param(
    [Parameter(Mandatory=$true)][string]$ClientPath,
    [string]$PackagePath,
    [switch]$VerifyOnly,
    [switch]$Restore,
    [string]$PackageId,
    [string]$BackupRoot
)
# Installs, verifies or restores an upscaled texture package built by build.py.
# Archives change only while Aion is closed, after verified backups.
# Batches stack: each package keeps its own state file; restore the newest batch first.
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$modRoot = Join-Path $client 'Mods/TextureUpscale'
$legacyState = Join-Path $modRoot 'installed.json'
if (Test-Path -LiteralPath $legacyState) {
    $legacy = Get-Content -LiteralPath $legacyState -Raw | ConvertFrom-Json
    Move-Item -LiteralPath $legacyState -Destination (Join-Path $modRoot ('installed-' + $legacy.packageId + '.json'))
}
function State-Path([string]$Id) { Join-Path $modRoot ('installed-' + $Id + '.json') }
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Assert-ClientClosed {
    if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Exit Aion normally before changing world textures.' }
}
function Replace-Archive([string]$Source, [string]$Target) {
    $temporary = $Target + '.textureupscale-new'
    Copy-Item -LiteralPath $Source -Destination $temporary -Force
    Move-Item -LiteralPath $temporary -Destination $Target -Force
}

if ($Restore) {
    if ($PackageId -notmatch '^[a-f0-9]{16}$') { throw 'Pass -PackageId of the batch to restore (newest first).' }
    $statePath = State-Path $PackageId
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($state.rollbackAvailable -eq $false) {
        throw 'These textures are included in the repack baseline. Per-batch rollback is unavailable. Use the backup created when an update is applied to recover that update.'
    }
    if ($state.restored) { 'Texture upscale already restored.'; return }
    Assert-ClientClosed
    foreach ($entry in $state.files) {
        $current = Join-Path $client $entry.path
        if ((Hash $current) -ne $entry.staged) { throw "Archive changed after this batch was installed (restore later batches first): $($entry.path). No files restored." }
        if ((Hash (Join-Path $state.backupRoot $entry.path)) -ne $entry.original) { throw "Backup verification failed: $($entry.path). No files restored." }
    }
    foreach ($entry in $state.graphicsFiles) {
        if ((Hash (Join-Path $client $entry.path)) -ne $entry.staged -or
            (Hash (Join-Path $state.backupRoot $entry.path)) -ne $entry.original) {
            throw "Shared graphics package changed after this batch: $($entry.path). No files restored."
        }
    }
    foreach ($entry in $state.files) { Replace-Archive (Join-Path $state.backupRoot $entry.path) (Join-Path $client $entry.path) }
    foreach ($entry in $state.graphicsFiles) { Replace-Archive (Join-Path $state.backupRoot $entry.path) (Join-Path $client $entry.path) }
    $state.restored = $true
    $state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding UTF8
    "Restored $($state.files.Count) archives from $($state.backupRoot)."; return
}

$package = (Resolve-Path -LiteralPath $PackagePath).Path
$manifest = Get-Content -LiteralPath (Join-Path $package 'manifest.json') -Raw | ConvertFrom-Json
if ($manifest.schema -ne 1 -or $manifest.packageId -notmatch '^[a-f0-9]{16}$' -or $manifest.files.Count -lt 1) { throw 'Unsupported texture package.' }
if ($manifest.clientRoot -ne $client) { throw 'Package belongs to another client.' }
$statePath = State-Path $manifest.packageId
# Archives a later batch rebuilt on top of this one count as installed for verification.
$later = @{}
foreach ($file in @(Get-ChildItem -LiteralPath $modRoot -Filter 'installed-*.json' -ErrorAction SilentlyContinue)) {
    $other = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
    if ($other.packageId -eq $manifest.packageId -or $other.restored) { continue }
    foreach ($f in $other.files) { $later[$f.path + '|' + $f.original] = $f.staged }
}
function Carried-Hash([string]$Path, [string]$Expected, [string]$Actual) {
    $seen = @{}
    while ($later.ContainsKey($Path + '|' + $Expected) -and -not $seen.ContainsKey($Expected)) {
        $seen[$Expected] = $true
        $Expected = $later[$Path + '|' + $Expected]
        if ($Expected -eq $Actual) { return $true }
    }
    return $false
}
$original = 0; $installed = 0; $superseded = 0
foreach ($entry in $manifest.files) {
    if ($entry.path -notmatch '^Levels/[A-Za-z0-9_]+/[A-Za-z0-9_]+\.pak$') { throw "Unsupported archive path: $($entry.path)" }
    if ((Hash (Join-Path $package $entry.path)) -ne $entry.staged) { throw "Package checksum failed: $($entry.path)" }
    $hash = Hash (Join-Path $client $entry.path)
    if ($hash -eq $entry.original) { $original++ } elseif ($hash -eq $entry.staged) { $installed++ }
    elseif (Carried-Hash $entry.path $entry.staged $hash) { $superseded++ }
    else { throw "Archive changed after the package was built: $($entry.path). Rebuild the package. No files changed." }
}
if ($VerifyOnly) { "Verified $($manifest.files.Count) texture archives: $original original / $installed upscaled / $superseded carried into a later batch."; return }
if ($installed + $superseded -eq $manifest.files.Count) { 'Texture upscale already installed.'; return }
if ($original -ne $manifest.files.Count) { throw 'Mixed archive states detected. No files changed.' }
# Shared terrain archives must keep the launcher's verified graphics package in sync.
# Preserve the original graphics manifest and package archives in this batch's backup
# and restore them together, using the same guards as the live archives.
$graphicsPlan = @()
$graphicsManifest = $null
$graphicsManifestPath = $null
$graphicsQueuePath = Join-Path $client 'Mods/GraphicsOverhaul/queued.json'
if (Test-Path -LiteralPath $graphicsQueuePath) {
    $queue = Get-Content -LiteralPath $graphicsQueuePath -Raw | ConvertFrom-Json
    if ($queue.packageId -notmatch '^[a-f0-9]{16}$') { throw 'Invalid graphics package ID.' }
    $graphicsPackage = 'Mods/GraphicsOverhaul/packages/' + $queue.packageId
    $graphicsManifestPath = $graphicsPackage + '/manifest.json'
    $graphicsManifest = Get-Content -LiteralPath (Join-Path $client $graphicsManifestPath) -Raw | ConvertFrom-Json
    foreach ($file in $manifest.files) {
        $shared = @($graphicsManifest.files | Where-Object { $_.path -eq $file.path })
        if ($shared.Count -eq 0) { continue }
        $graphicsState = Get-Content -LiteralPath (Join-Path $client 'Mods/GraphicsOverhaul/installed.json') -Raw | ConvertFrom-Json
        if ($graphicsManifest.schema -ne 1 -or $graphicsManifest.clientRoot -ne $client -or
            $graphicsState.packageId -ne $queue.packageId -or $graphicsState.restored -or $shared.Count -ne 1 -or
            $shared[0].staged -ne $file.original -or
            (Hash (Join-Path $client ($graphicsPackage + '/' + $file.path))) -ne $file.original) {
            throw "Shared graphics package baseline differs: $($file.path). No files changed."
        }
        $graphicsPlan += [ordered]@{path=($graphicsPackage + '/' + $file.path); original=$file.original; staged=$file.staged; source=$file.path}
        $memberCount = $shared[0].changedEntries.Count + $shared[0].unchangedEntries
        $shared[0].staged = $file.staged
        $shared[0].changedEntries = @(@($shared[0].changedEntries) + @($file.changedEntries) | Sort-Object -Unique)
        $shared[0].unchangedEntries = $memberCount - $shared[0].changedEntries.Count
    }
}
Assert-ClientClosed
if ($BackupRoot) {
    $backup = [IO.Path]::GetFullPath($BackupRoot)
    if ($backup -eq $client -or $backup.StartsWith($client + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Use an external backup folder outside the client.'
    }
    if (Test-Path -LiteralPath $backup) { throw 'Use a new external backup folder.' }
} else {
    $backup = Join-Path $client ('Mods/Backups/TextureUpscale/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
}
foreach ($entry in $manifest.files) {
    $target = Join-Path $backup $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $client $entry.path) -Destination $target
    if ((Hash $target) -ne $entry.original) { throw 'Backup verification failed. No archives changed.' }
}
$graphicsFiles = @()
foreach ($entry in $graphicsPlan) {
    $target = Join-Path $backup $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $client $entry.path) -Destination $target
    if ((Hash $target) -ne $entry.original) { throw 'Graphics package backup verification failed. No archives changed.' }
    $graphicsFiles += [ordered]@{path=$entry.path; original=$entry.original; staged=$entry.staged}
}
if ($graphicsPlan.Count) {
    $target = Join-Path $backup $graphicsManifestPath
    Copy-Item -LiteralPath (Join-Path $client $graphicsManifestPath) -Destination $target
    $priorHash = Hash (Join-Path $client $graphicsManifestPath)
    if ((Hash $target) -ne $priorHash) { throw 'Graphics manifest backup verification failed. No archives changed.' }
    $updatedGraphicsManifest = Join-Path $backup 'graphics-manifest-updated.json'
    [IO.File]::WriteAllText($updatedGraphicsManifest, ($graphicsManifest | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))
    $graphicsFiles += [ordered]@{path=$graphicsManifestPath; original=$priorHash; staged=(Hash $updatedGraphicsManifest)}
}
$completed = @()
try {
    foreach ($entry in $manifest.files) {
        $completed += $entry
        $target = Join-Path $client $entry.path
        Replace-Archive (Join-Path $package $entry.path) $target
        if ((Hash $target) -ne $entry.staged) { throw "Installed checksum failed: $($entry.path)" }
    }
    foreach ($entry in $graphicsPlan) {
        $completed += $entry
        $target = Join-Path $client $entry.path
        Replace-Archive (Join-Path $package $entry.source) $target
        if ((Hash $target) -ne $entry.staged) { throw "Graphics package checksum failed: $($entry.path)" }
    }
    if ($graphicsPlan.Count) {
        $completed += [ordered]@{path=$graphicsManifestPath}
        Replace-Archive $updatedGraphicsManifest (Join-Path $client $graphicsManifestPath)
        if ((Hash (Join-Path $client $graphicsManifestPath)) -ne $graphicsFiles[-1].staged) { throw 'Graphics manifest checksum failed.' }
    }
} catch {
    $failure = $_
    foreach ($entry in $completed) { Replace-Archive (Join-Path $backup $entry.path) (Join-Path $client $entry.path) }
    throw $failure
}
New-Item -ItemType Directory -Path $modRoot -Force | Out-Null
[ordered]@{packageId=$manifest.packageId; zones=$manifest.zones; backupRoot=$backup; restored=$false; appliedAt=(Get-Date -Format 'o')
    files=@($manifest.files | ForEach-Object { [ordered]@{path=$_.path; original=$_.original; staged=$_.staged} }); graphicsFiles=$graphicsFiles} |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding UTF8
"Installed $($manifest.files.Count) upscaled texture archives. Verified backups: $backup"
