param(
    [string]$ClientPath,
    [string]$PackagePath,
    [string]$PatchFile,
    [string]$UpdateUrl
)
$ErrorActionPreference = 'Stop'

function Read-Json([string]$Path) {
    return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Assert-Https([string]$Value) {
    $uri = $null
    if ($Value -match '\s' -or -not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https' -or $uri.UserInfo) {
        throw ('Expected a direct HTTPS URL without credentials: ' + $Value)
    }
    return $Value
}

if ($PackagePath) {
    $package = [IO.Path]::GetFullPath($PackagePath)
} elseif ($ClientPath) {
    $package = (Get-Item -LiteralPath ([IO.Path]::GetFullPath($ClientPath))).Parent.FullName
} elseif (Test-Path -LiteralPath (Join-Path (Get-Item -LiteralPath $PSScriptRoot).Parent.FullName 'REPACK.json')) {
    $package = (Get-Item -LiteralPath $PSScriptRoot).Parent.FullName
} elseif (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'settings.json')) {
    $settings = Read-Json (Join-Path $PSScriptRoot 'settings.json')
    $manifest = Read-Json (Join-Path $PSScriptRoot 'release-manifest.json')
    $installParent = if ([IO.Path]::IsPathRooted([string]$settings.InstallDirectory)) { [IO.Path]::GetFullPath([string]$settings.InstallDirectory) } else { [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ([string]$settings.InstallDirectory))) }
    $package = Join-Path $installParent ([string]$manifest.installFolder)
} else {
    $package = (Get-Item -LiteralPath $PSScriptRoot).Parent.FullName
}

if (-not (Test-Path -LiteralPath (Join-Path $package 'REPACK.json'))) {
    throw ('Could not find an installed Aetherfall repack at: ' + $package)
}

$releasePath = Join-Path $package 'release.json'
$currentRelease = Read-Json $releasePath
$currentVersion = [string]$currentRelease.Version

$updateConfigPath = Join-Path $package 'Client/Mods/Launcher/update.json'
$updateConfig = if (Test-Path -LiteralPath $updateConfigPath) { Read-Json $updateConfigPath } else { [PSCustomObject]@{ UpdateUrl = ''; PatchDirectory = '' } }
if (-not $UpdateUrl -and $updateConfig.UpdateUrl) {
    $UpdateUrl = [string]$updateConfig.UpdateUrl
}

# Check for a local Aetherfall-Patch-*.zip in the repack root or Updates folder first.
$localPatches = @(Get-ChildItem -LiteralPath $package -Filter 'Aetherfall-Patch-*.zip' -File -ErrorAction SilentlyContinue)
$updatesDir = Join-Path $package 'Updates'
if (Test-Path -LiteralPath $updatesDir) {
    $localPatches += @(Get-ChildItem -LiteralPath $updatesDir -Filter 'Aetherfall-Patch-*.zip' -File -ErrorAction SilentlyContinue)
}
if (-not $PatchFile -and $localPatches.Count -gt 0) {
    $latestLocal = $localPatches | Sort-Object Name | Select-Object -Last 1
    if ($latestLocal.Name -notmatch ('^Aetherfall-Patch-' + [Regex]::Escape($currentVersion) + '\.zip$')) {
        $PatchFile = $latestLocal.FullName
    }
}

$remoteManifest = $null
if (-not $PatchFile) {
    if (-not $UpdateUrl) {
        Write-Output ('Aion Aetherfall ' + $currentVersion + ' is installed. Place an Aetherfall-Patch-<version>.zip in the repack folder or set UpdateUrl in Client\Mods\Launcher\update.json.')
        exit 0
    }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $validUrl = Assert-Https $UpdateUrl
    $wc = New-Object Net.WebClient
    $wc.Encoding = [Text.Encoding]::UTF8
    try {
        $rawJson = $wc.DownloadString($validUrl)
        $remoteManifest = $rawJson | ConvertFrom-Json
    } finally {
        $wc.Dispose()
    }
    if (-not $remoteManifest -or $remoteManifest.schema -ne 1 -or -not $remoteManifest.version) {
        throw 'Invalid remote update manifest.'
    }
    if ([string]$remoteManifest.version -eq $currentVersion) {
        Write-Output ('Aion Aetherfall ' + $currentVersion + ' is already up to date.')
        exit 0
    }
    New-Item -ItemType Directory -Path $updatesDir -Force | Out-Null
    $patchName = 'Aetherfall-Patch-' + [string]$remoteManifest.version + '.zip'
    $PatchFile = Join-Path $updatesDir $patchName
    $patchUrl = Assert-Https ([string]$remoteManifest.patchUrl)
    if (-not (Test-Path -LiteralPath $PatchFile) -or (Get-FileHash -LiteralPath $PatchFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne ([string]$remoteManifest.patchSha256).ToLowerInvariant()) {
        $wc2 = New-Object Net.WebClient
        try {
            $wc2.DownloadFile($patchUrl, $PatchFile)
        } finally {
            $wc2.Dispose()
        }
    }
    if ((Get-FileHash -LiteralPath $PatchFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne ([string]$remoteManifest.patchSha256).ToLowerInvariant()) {
        Remove-Item -LiteralPath $PatchFile -Force -ErrorAction SilentlyContinue
        throw 'Downloaded patch ZIP failed SHA-256 verification.'
    }
}

if (-not (Test-Path -LiteralPath $PatchFile)) {
    throw ('Patch file not found: ' + $PatchFile)
}

# Ensure Aion and repack servers are closed before modifying files.
if (@(Get-Process -Name 'aion.bin', 'aion' -ErrorAction SilentlyContinue).Count -gt 0) {
    throw 'Close Aion before applying a repack update.'
}
$serverJava = Join-Path $package 'Server\runtime\java'
$runningJava = @(Get-Process -Name 'java', 'javaw' -ErrorAction SilentlyContinue | Where-Object {
    try { $_.Path -and $_.Path.StartsWith($serverJava, [StringComparison]::OrdinalIgnoreCase) } catch { $false }
})
if ($runningJava.Count -gt 0) {
    throw 'Stop the repack servers (GameServer / LoginServer / ChatServer) before applying an update.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$stageDir = Join-Path $package ('Control\.update-stage-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
$backupDir = Join-Path $package ('Control\UpdateBackups\backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))

try {
    [IO.Compression.ZipFile]::ExtractToDirectory($PatchFile, $stageDir)
    $manifestPath = Join-Path $stageDir 'patch-manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw 'The patch archive is missing patch-manifest.json.'
    }
    $patch = Read-Json $manifestPath
    if ($patch.schema -ne 1 -or -not $patch.version) {
        throw 'Unsupported patch manifest schema.'
    }
    $targetVersion = [string]$patch.version
    if ($targetVersion -eq $currentVersion) {
        Write-Output ('Aion Aetherfall ' + $currentVersion + ' is already installed.')
        exit 0
    }

    # Verify all staged files before touching the repack.
    foreach ($file in $patch.files) {
        $rel = [string]$file.path
        if ($rel.Contains('..') -or [IO.Path]::IsPathRooted($rel) -or $rel.StartsWith('Server/database/data', [StringComparison]::OrdinalIgnoreCase)) {
            throw ('Unsafe path in patch manifest: ' + $rel)
        }
        $stagedFile = Join-Path $stageDir ('payload/' + $rel)
        if (-not (Test-Path -LiteralPath $stagedFile) -or (Get-Item -LiteralPath $stagedFile).Length -ne [Int64]$file.bytes -or (Get-FileHash -LiteralPath $stagedFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne ([string]$file.sha256).ToLowerInvariant()) {
            throw ('Staged file checksum mismatch in patch: ' + $rel)
        }
    }

    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    $written = @()
    $removedList = @()

    try {
        foreach ($file in $patch.files) {
            $rel = [string]$file.path
            $dest = Join-Path $package $rel
            if (Test-Path -LiteralPath $dest) {
                $bk = Join-Path $backupDir $rel
                New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($bk)) -Force | Out-Null
                Copy-Item -LiteralPath $dest -Destination $bk -Force
            }
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($dest)) -Force | Out-Null
            if ($rel -ieq 'Client/Aetherfall Launcher.exe' -and (Test-Path -LiteralPath $dest)) {
                $oldExe = $dest + '.old'
                if (Test-Path -LiteralPath $oldExe) { Remove-Item -LiteralPath $oldExe -Force -ErrorAction SilentlyContinue }
                Move-Item -LiteralPath $dest -Destination $oldExe -Force
            }
            Copy-Item -LiteralPath (Join-Path $stageDir ('payload/' + $rel)) -Destination $dest -Force
            $written += $rel
            if ((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash.ToLowerInvariant() -ne ([string]$file.sha256).ToLowerInvariant()) {
                throw ('Installed file verification failed: ' + $rel)
            }
        }
        if ($patch.removed) {
            foreach ($rem in $patch.removed) {
                $rel = [string]$rem.path
                $dest = Join-Path $package $rel
                if (Test-Path -LiteralPath $dest) {
                    $bk = Join-Path $backupDir $rel
                    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($bk)) -Force | Out-Null
                    Copy-Item -LiteralPath $dest -Destination $bk -Force
                    Remove-Item -LiteralPath $dest -Force
                    $removedList += $rel
                }
            }
        }
    } catch {
        foreach ($rel in ($written + $removedList)) {
            $dest = Join-Path $package $rel
            $bk = Join-Path $backupDir $rel
            if (Test-Path -LiteralPath $bk) {
                Copy-Item -LiteralPath $bk -Destination $dest -Force
            } elseif (Test-Path -LiteralPath $dest) {
                Remove-Item -LiteralPath $dest -Force -ErrorAction SilentlyContinue
            }
        }
        throw
    }

    Write-Output ('Updated Aion Aetherfall from ' + $currentVersion + ' to ' + $targetVersion + ' (' + $written.Count + ' files updated).')
} finally {
    if (Test-Path -LiteralPath $stageDir) {
        Remove-Item -LiteralPath $stageDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
