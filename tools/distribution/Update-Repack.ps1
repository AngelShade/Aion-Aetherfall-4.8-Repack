param(
    [string]$ClientPath,
    [string]$PackagePath,
    [string]$PatchFile,
    [string]$UpdateUrl
)
$ErrorActionPreference = 'Stop'
trap { Write-Output ('UPDATE ERROR: ' + $_.Exception.Message); exit 1 }

if (-not ('AetherfallUpdate.UpdateTransfer' -as [type])) {
    Add-Type -Path (Join-Path $PSScriptRoot 'UpdateTransfer.cs') -ReferencedAssemblies 'System.dll','System.Web.Extensions.dll'
}

function Write-UpdateProgress([string]$Phase, [string]$Unit, [Int64]$Done, [Int64]$Total, [int]$Item, [int]$Items, [string]$Current, [bool]$Force = $false) {
    [AetherfallUpdate.UpdateTransfer]::Report($Phase, $Unit, $Done, $Total, $Item, $Items, $Current, 0, $Force)
}

function Read-Json([string]$Path) {
    return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Get-Sha256([string]$Path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($Path)
    try { return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
}

# Hash the installed release shipped for a path, from its FILES.json text; '' when absent.
function Get-ShippedHash([string]$IndexText, [string]$Rel) {
    $quoted = '"' + $Rel + '"'
    $at = $IndexText.IndexOf($quoted, [StringComparison]::OrdinalIgnoreCase)
    while ($at -ge 0) {
        $start = $IndexText.LastIndexOf('{', $at)
        $end = $IndexText.IndexOf('}', $at)
        if ($start -ge 0 -and $end -gt $at) {
            $entry = $IndexText.Substring($start, $end - $start + 1)
            if ($entry -match ('"path"\s*:\s*' + [regex]::Escape($quoted)) -and $entry -match '"sha256"\s*:\s*"([0-9a-fA-F]{64})"') {
                return $Matches[1].ToLowerInvariant()
            }
        }
        $at = $IndexText.IndexOf($quoted, $at + 1, [StringComparison]::OrdinalIgnoreCase)
    }
    return ''
}

# Keep the player's .properties lines and append keys that only the update has.
function Merge-Properties([string]$Current, [string]$Incoming) {
    $bytes = [IO.File]::ReadAllBytes($Current)
    $bom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $text = [IO.File]::ReadAllText($Current)
    $pattern = '^\s*([^#!\s][^=]*?)\s*='
    $keys = @{}
    foreach ($line in ($text -split "`r?`n")) { if ($line -match $pattern) { $keys[$Matches[1]] = $true } }
    $added = New-Object System.Collections.Generic.List[string]
    foreach ($line in ([IO.File]::ReadAllText($Incoming) -split "`r?`n")) {
        if ($line -match $pattern -and -not $keys.ContainsKey($Matches[1])) { $added.Add($line.Trim()); $keys[$Matches[1]] = $true }
    }
    if ($added.Count -eq 0) { return $false }
    $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    if ($text.Length -gt 0 -and -not $text.EndsWith("`n")) { $text += $nl }
    $text += $nl + '# Added by the Aetherfall update; your existing values are kept.' + $nl + ($added -join $nl) + $nl
    [IO.File]::WriteAllText($Current, $text, (New-Object Text.UTF8Encoding $bom))
    return $true
}

function Assert-Https([string]$Value) {
    $uri = $null
    if ($Value -match '\s' -or -not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https' -or $uri.UserInfo) {
        throw ('Expected a direct HTTPS URL without credentials: ' + $Value)
    }
    return $Value
}

function Assert-PatchPath([string]$Rel) {
    if (-not $Rel -or $Rel -match '(^|[\\/])\.\.([\\/]|$)|[:\x00-\x1f]' -or [IO.Path]::IsPathRooted($Rel) -or
        $Rel.Replace('\', '/').StartsWith('Server/database/data', [StringComparison]::OrdinalIgnoreCase)) {
        throw ('Unsafe path in patch manifest: ' + $Rel)
    }
}

function Receive-VerifiedFile([string]$Url, [string]$Dest, [Int64]$Bytes, [string]$Hash,
                              [string]$Phase = 'manifest', [Int64]$Done = 0, [Int64]$Total = 0, [int]$Item = 1, [int]$Items = 1) {
    if ($Hash -notmatch '^[0-9a-fA-F]{64}$' -or $Bytes -lt 0) { throw 'Invalid update checksum or size.' }
    if ((Test-Path -LiteralPath $Dest) -and (Get-Item -LiteralPath $Dest).Length -eq $Bytes -and (Get-Sha256 $Dest) -eq $Hash) { return }
    $urlValue = Assert-Https $Url
    if ($Total -eq 0) { $Total = $Bytes }
    [AetherfallUpdate.UpdateTransfer]::Download($urlValue, $Dest, $Bytes, $Hash, $Phase, $Done, $Total, $Item, $Items)
}

# This only stages content. The same verified install/backup/settings path handles both formats.
function Stage-ChunkedPatch($Patch, [string]$Stage, [string]$ManifestDirectory) {
    $assetMap = @{}
    foreach ($asset in $Patch.assets) {
        $name = [string]$asset.file
        if ($name -notmatch '^Aetherfall-Chunk-[0-9a-f]{64}\.zip$' -or $assetMap.ContainsKey($name) -or
            [Int64]$asset.bytes -le 0 -or [Int64]$asset.bytes -ge 2147483648 -or [string]$asset.sha256 -notmatch '^[0-9a-f]{64}$') {
            throw 'Invalid chunk asset in update manifest.'
        }
        $assetMap[$name] = $asset
    }
    $assetCache = Join-Path $updatesDir 'Chunks'
    New-Item -ItemType Directory -Path $assetCache -Force | Out-Null
    $ready = @{}
    $seen = @{}
    $needed = @{}
    $plans = New-Object System.Collections.Generic.List[object]
    $sha = [Security.Cryptography.SHA256]::Create()
    $downloaded = [Int64]0
    $reused = 0
    $checked = 0
    $fileCount = @($Patch.files).Count
    Write-UpdateProgress 'checking' 'files' 0 $fileCount 0 $fileCount '' $true
    try {
        # Plan all missing packages first, so transfer totals exclude reusable data.
        foreach ($file in $Patch.files) {
            $rel = [string]$file.path
            Assert-PatchPath $rel
            if ($seen.ContainsKey($rel)) { throw ('Duplicate update path: ' + $rel) }
            $seen[$rel] = $true
            $total = [Int64]0
            foreach ($part in $file.parts) {
                if ([Int64]$part.bytes -le 0 -or [Int64]$part.bytes -gt 33554432 -or
                    [string]$part.sha256 -notmatch '^[0-9a-f]{64}$' -or -not $assetMap.ContainsKey([string]$part.asset)) {
                    throw ('Invalid file chunk: ' + $rel)
                }
                $total += [Int64]$part.bytes
            }
            if ($total -ne [Int64]$file.bytes -or [string]$file.sha256 -notmatch '^[0-9a-f]{64}$') { throw ('Invalid chunked file: ' + $rel) }
            Write-UpdateProgress 'checking' 'files' $checked $fileCount ($checked + 1) $fileCount $rel
            $live = Join-Path $package $rel
            $matches = $false
            if ((Test-Path -LiteralPath $live -PathType Leaf) -and (Get-Item -LiteralPath $live).Length -eq [Int64]$file.bytes) {
                $phase = if ([Int64]$file.bytes -ge 8388608) { 'checking-file' } else { '' }
                $hash = [AetherfallUpdate.UpdateTransfer]::Hash($live, $phase, 0, [Int64]$file.bytes, ($checked + 1), $fileCount, $rel)
                $matches = $hash -eq [string]$file.sha256
            }
            if ($matches) {
                $file | Add-Member -NotePropertyName unchanged -NotePropertyValue $true
                $reused++
            } else {
                $localParts = New-Object System.Collections.Generic.List[bool]
                $inputFile = if (Test-Path -LiteralPath $live -PathType Leaf) { [IO.File]::OpenRead($live) } else { $null }
                try {
                    $offset = [Int64]0
                    $partNumber = 0
                    foreach ($part in $file.parts) {
                        $partNumber++
                        $match = $false
                        if ($inputFile -and $inputFile.Length -ge $offset + [Int64]$part.bytes) {
                            $buffer = New-Object byte[] ([int]$part.bytes)
                            $inputFile.Position = $offset
                            $read = 0
                            while ($read -lt $buffer.Length) {
                                $n = $inputFile.Read($buffer, $read, $buffer.Length - $read)
                                if ($n -eq 0) { break }
                                $read += $n
                            }
                            $hash = [BitConverter]::ToString($sha.ComputeHash($buffer)).Replace('-', '').ToLowerInvariant()
                            $match = $read -eq $buffer.Length -and $hash -eq [string]$part.sha256
                        }
                        $localParts.Add($match)
                        if (-not $match) { $needed[[string]$part.asset] = $assetMap[[string]$part.asset] }
                        $offset += [Int64]$part.bytes
                        Write-UpdateProgress 'checking' 'files' $checked $fileCount ($checked + 1) $fileCount ($rel + ' (chunk ' + $partNumber + '/' + @($file.parts).Count + ')')
                    }
                } finally { if ($inputFile) { $inputFile.Dispose() } }
                $plans.Add([PSCustomObject]@{ file = $file; localParts = $localParts.ToArray() })
            }
            $checked++
            Write-UpdateProgress 'checking' 'files' $checked $fileCount $checked $fileCount $rel
        }
        Write-UpdateProgress 'checking' 'files' $fileCount $fileCount $fileCount $fileCount '' $true
        $pending = New-Object System.Collections.Generic.List[object]
        $networkBytes = [Int64]0
        $cacheChecked = 0
        foreach ($asset in ($needed.Values | Sort-Object file)) {
            $cached = Join-Path $assetCache ([string]$asset.file)
            $offline = Join-Path $ManifestDirectory ([string]$asset.file)
            if ((Test-Path -LiteralPath $offline -PathType Leaf) -and (Get-Item -LiteralPath $offline).Length -eq [Int64]$asset.bytes -and (Get-Sha256 $offline) -eq [string]$asset.sha256) {
                $ready[[string]$asset.file] = $offline
            } elseif ((Test-Path -LiteralPath $cached -PathType Leaf) -and (Get-Item -LiteralPath $cached).Length -eq [Int64]$asset.bytes -and (Get-Sha256 $cached) -eq [string]$asset.sha256) {
                $ready[[string]$asset.file] = $cached
            } else {
                $pending.Add($asset)
                $networkBytes += [Int64]$asset.bytes
            }
            $cacheChecked++
            Write-UpdateProgress 'cache' 'packages' $cacheChecked $needed.Count $cacheChecked $needed.Count ([string]$asset.file)
        }
        Write-Output ('Update plan: ' + $pending.Count + ' packages / ' + $networkBytes + ' bytes to download; ' + $reused + ' complete files reused.')
        $packageNumber = 0
        Write-UpdateProgress 'download' 'bytes' 0 $networkBytes 0 $pending.Count '' $true
        foreach ($asset in $pending) {
            $packageNumber++
            $cached = Join-Path $assetCache ([string]$asset.file)
            Receive-VerifiedFile ([string]$asset.url) $cached ([Int64]$asset.bytes) ([string]$asset.sha256) 'download' $downloaded $networkBytes $packageNumber $pending.Count
            $downloaded += [Int64]$asset.bytes
            $ready[[string]$asset.file] = $cached
        }
        Write-UpdateProgress 'download' 'bytes' $networkBytes $networkBytes $pending.Count $pending.Count '' $true
        $assembleBytes = [Int64]0
        foreach ($plan in $plans) { $assembleBytes += [Int64]$plan.file.bytes }
        $assembled = [Int64]0
        $fileNumber = 0
        Write-UpdateProgress 'assemble' 'bytes' 0 $assembleBytes 0 $plans.Count '' $true
        foreach ($plan in $plans) {
            $file = $plan.file
            $rel = [string]$file.path
            $fileNumber++
            $live = Join-Path $package $rel
            $dest = Join-Path $Stage ('payload/' + $rel)
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($dest)) -Force | Out-Null
            $inputFile = if (Test-Path -LiteralPath $live -PathType Leaf) { [IO.File]::OpenRead($live) } else { $null }
            $outputFile = [IO.File]::Create($dest)
            try {
                $offset = [Int64]0
                $partNumber = 0
                foreach ($part in $file.parts) {
                    $buffer = New-Object byte[] ([int]$part.bytes)
                    if ($plan.localParts[$partNumber]) {
                        if (-not $inputFile -or $inputFile.Length -lt $offset + $buffer.Length) { throw ('Installed file changed during update: ' + $rel) }
                        $inputFile.Position = $offset
                        $stream = $inputFile
                    } else {
                        $archive = [IO.Compression.ZipFile]::OpenRead($ready[[string]$part.asset])
                        $entry = $archive.GetEntry([string]$part.sha256)
                        if (-not $entry -or $entry.Length -ne $buffer.Length) { $archive.Dispose(); throw ('Missing or invalid update chunk: ' + $rel) }
                        $stream = $entry.Open()
                    }
                    try {
                        $read = 0
                        while ($read -lt $buffer.Length) {
                            $n = $stream.Read($buffer, $read, $buffer.Length - $read)
                            if ($n -eq 0) { throw ('Truncated update chunk: ' + $rel) }
                            $read += $n
                        }
                    } finally {
                        if (-not $plan.localParts[$partNumber]) { $stream.Dispose(); $archive.Dispose() }
                    }
                    $hash = [BitConverter]::ToString($sha.ComputeHash($buffer)).Replace('-', '').ToLowerInvariant()
                    if ($hash -ne [string]$part.sha256) { throw ('Update chunk checksum mismatch: ' + $rel) }
                    $outputFile.Write($buffer, 0, $buffer.Length)
                    $offset += $buffer.Length
                    $assembled += $buffer.Length
                    $partNumber++
                    Write-UpdateProgress 'assemble' 'bytes' $assembled $assembleBytes $fileNumber $plans.Count $rel
                }
            } finally {
                $outputFile.Dispose()
                if ($inputFile) { $inputFile.Dispose() }
            }
        }
        Write-UpdateProgress 'assemble' 'bytes' $assembleBytes $assembleBytes $plans.Count $plans.Count '' $true
    } finally { $sha.Dispose() }
    Write-Output ('Update download: ' + $downloaded + ' bytes; reused ' + $reused + ' complete installed files. Verified chunks are cached for retries.')
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

# Cached patches are an offline option only; an online channel is authoritative.
$localPatches = @(Get-ChildItem -LiteralPath $package -Filter 'Aetherfall-Patch-*' -File -ErrorAction SilentlyContinue | Where-Object { $_.Extension -in '.zip', '.json' })
$updatesDir = Join-Path $package 'Updates'
if (Test-Path -LiteralPath $updatesDir) {
    $localPatches += @(Get-ChildItem -LiteralPath $updatesDir -Filter 'Aetherfall-Patch-*' -File -ErrorAction SilentlyContinue | Where-Object { $_.Extension -in '.zip', '.json' })
}
if (-not $PatchFile -and -not $UpdateUrl -and $localPatches.Count -gt 0) {
    $latestLocal = $localPatches | Where-Object {
        $candidate = $null
        $_.BaseName -match '^Aetherfall-Patch-(.+)$' -and
            [Version]::TryParse($Matches[1], [ref]$candidate) -and $candidate -gt [Version]$currentVersion
    } | Sort-Object { [Version]($_.BaseName -replace '^Aetherfall-Patch-', '') } | Select-Object -Last 1
    if ($latestLocal) {
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
    if ([Version]([string]$remoteManifest.version) -le [Version]$currentVersion) {
        Write-Output ('Aion Aetherfall ' + $currentVersion + ' is already up to date.')
        exit 0
    }
    New-Item -ItemType Directory -Path $updatesDir -Force | Out-Null
    $patchName = if ($remoteManifest.patchFormat -eq 'chunked-v1') { 'Aetherfall-Patch-' + [string]$remoteManifest.version + '.json' } else { 'Aetherfall-Patch-' + [string]$remoteManifest.version + '.zip' }
    $PatchFile = Join-Path $updatesDir $patchName
    $transferPhase = if ($remoteManifest.patchFormat -eq 'chunked-v1') { 'manifest' } else { 'download' }
    Receive-VerifiedFile ([string]$remoteManifest.patchUrl) $PatchFile ([Int64]$remoteManifest.patchBytes) ([string]$remoteManifest.patchSha256) $transferPhase
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
    $chunked = [IO.Path]::GetExtension($PatchFile) -ieq '.json'
    if ($chunked) {
        $manifestPath = $PatchFile
    } else {
        Write-UpdateProgress 'extract' 'files' 0 0 0 0 'Preparing the legacy patch archive.' $true
        [IO.Compression.ZipFile]::ExtractToDirectory($PatchFile, $stageDir)
        $manifestPath = Join-Path $stageDir 'patch-manifest.json'
    }
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw 'The patch archive is missing patch-manifest.json.'
    }
    $patch = Read-Json $manifestPath
    if (($chunked -and ($patch.schema -ne 2 -or $patch.format -ne 'chunked-v1')) -or (-not $chunked -and $patch.schema -ne 1) -or -not $patch.version) {
        throw 'Unsupported patch manifest schema.'
    }
    $targetVersion = [string]$patch.version
    if ($remoteManifest -and $targetVersion -ne [string]$remoteManifest.version) { throw 'Update channel and patch versions differ.' }
    if ([Version]$targetVersion -lt [Version]$currentVersion) {
        throw ('Refusing to downgrade Aion Aetherfall from ' + $currentVersion + ' to ' + $targetVersion + '.')
    }
    if ($targetVersion -eq $currentVersion) {
        Write-Output ('Aion Aetherfall ' + $currentVersion + ' is already installed.')
        exit 0
    }

    foreach ($rem in $patch.removed) { Assert-PatchPath ([string]$rem.path) }
    if ($chunked) { Stage-ChunkedPatch $patch $stageDir ([IO.Path]::GetDirectoryName($PatchFile)) }
    # Verify all staged files before touching the repack.
    $verifyFiles = @($patch.files | Where-Object { -not $_.unchanged })
    $verifyBytes = [Int64]0
    foreach ($file in $verifyFiles) { $verifyBytes += [Int64]$file.bytes }
    $verified = [Int64]0
    $verifyNumber = 0
    Write-UpdateProgress 'verify' 'bytes' 0 $verifyBytes 0 $verifyFiles.Count '' $true
    foreach ($file in $verifyFiles) {
        $rel = [string]$file.path
        Assert-PatchPath $rel
        $verifyNumber++
        $stagedFile = Join-Path $stageDir ('payload/' + $rel)
        if (-not (Test-Path -LiteralPath $stagedFile) -or (Get-Item -LiteralPath $stagedFile).Length -ne [Int64]$file.bytes -or [AetherfallUpdate.UpdateTransfer]::Hash($stagedFile, 'verify', $verified, $verifyBytes, $verifyNumber, $verifyFiles.Count, $rel) -ne ([string]$file.sha256).ToLowerInvariant()) {
            throw ('Staged file checksum mismatch in patch: ' + $rel)
        }
        $verified += [Int64]$file.bytes
    }
    Write-UpdateProgress 'verify' 'bytes' $verifyBytes $verifyBytes $verifyFiles.Count $verifyFiles.Count '' $true

    # Player-owned settings (patch rule list): a copy that differs from what the
    # installed release shipped was changed by the player and is kept.
    $preserve = @{}
    if ($patch.preserve) {
        foreach ($rule in $patch.preserve) { $preserve[([string]$rule.path).ToLowerInvariant()] = [string]$rule.mode }
    }
    $indexPath = Join-Path $package 'FILES.json'
    $installedIndex = if ($preserve.Count -gt 0 -and (Test-Path -LiteralPath $indexPath)) { [IO.File]::ReadAllText($indexPath) } else { '' }
    $kept = @()

    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    $written = @()
    $removedList = @()

    try {
        # Commit installed-version metadata after all payload files.
        $installFiles = @($verifyFiles | Sort-Object { if ($_.path -ieq 'release.json') { 2 } elseif ($_.path -ieq 'FILES.json') { 1 } else { 0 } })
        $installedBytes = [Int64]0
        $installNumber = 0
        Write-UpdateProgress 'install' 'bytes' 0 $verifyBytes 0 $installFiles.Count '' $true
        foreach ($file in $installFiles) {
            $rel = [string]$file.path
            $installNumber++
            $dest = Join-Path $package $rel
            $mode = $preserve[$rel.ToLowerInvariant()]
            if ($mode -and (Test-Path -LiteralPath $dest)) {
                $live = Get-Sha256 $dest
                if ($live -ne ([string]$file.sha256).ToLowerInvariant() -and $live -ne (Get-ShippedHash $installedIndex $rel)) {
                    if ($mode -eq 'properties') {
                        $bk = Join-Path $backupDir $rel
                        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($bk)) -Force | Out-Null
                        [AetherfallUpdate.UpdateTransfer]::Copy($dest, $bk, 'backup', 0, (Get-Item -LiteralPath $dest).Length, $installNumber, $installFiles.Count, $rel)
                        $written += $rel
                        [void](Merge-Properties $dest (Join-Path $stageDir ('payload/' + $rel)))
                    }
                    $kept += $rel
                    $installedBytes += [Int64]$file.bytes
                    Write-UpdateProgress 'install' 'bytes' $installedBytes $verifyBytes $installNumber $installFiles.Count $rel
                    continue
                }
            }
            if (Test-Path -LiteralPath $dest) {
                $bk = Join-Path $backupDir $rel
                New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($bk)) -Force | Out-Null
                [AetherfallUpdate.UpdateTransfer]::Copy($dest, $bk, 'backup', 0, (Get-Item -LiteralPath $dest).Length, $installNumber, $installFiles.Count, $rel)
            }
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($dest)) -Force | Out-Null
            $written += $rel
            if (($rel -ieq 'Client/Aetherfall Launcher.exe' -or $rel -ieq 'Aetherfall Hub.exe') -and (Test-Path -LiteralPath $dest)) {
                $oldExe = $dest + '.old'
                if (Test-Path -LiteralPath $oldExe) { Remove-Item -LiteralPath $oldExe -Force -ErrorAction SilentlyContinue }
                Move-Item -LiteralPath $dest -Destination $oldExe -Force
            }
            [AetherfallUpdate.UpdateTransfer]::Copy((Join-Path $stageDir ('payload/' + $rel)), $dest, 'install', $installedBytes, $verifyBytes, $installNumber, $installFiles.Count, $rel)
            if ((Get-Sha256 $dest) -ne ([string]$file.sha256).ToLowerInvariant()) {
                throw ('Installed file verification failed: ' + $rel)
            }
            $installedBytes += [Int64]$file.bytes
        }
        Write-UpdateProgress 'install' 'bytes' $verifyBytes $verifyBytes $installFiles.Count $installFiles.Count '' $true
        if ($patch.removed) {
            $removeNumber = 0
            $removeCount = @($patch.removed).Count
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
                $removeNumber++
                Write-UpdateProgress 'remove' 'files' $removeNumber $removeCount $removeNumber $removeCount $rel
            }
            Write-UpdateProgress 'remove' 'files' $removeCount $removeCount $removeCount $removeCount '' $true
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

    if ($kept.Count -gt 0) { Write-Output ('Kept your settings: ' + ($kept -join ', ')) }
    Write-Output ('Updated Aion Aetherfall from ' + $currentVersion + ' to ' + $targetVersion + ' (' + $written.Count + ' files updated, ' + $kept.Count + ' settings kept).')
} finally {
    if (Test-Path -LiteralPath $stageDir) {
        Remove-Item -LiteralPath $stageDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
