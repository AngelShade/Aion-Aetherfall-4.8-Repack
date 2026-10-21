param(
    [Parameter(Mandatory=$true)][string]$ClientPath,
    [string]$PackagePath,
    [switch]$VerifyOnly,
    [switch]$Restore,
    [string]$PackageId
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
    if ($state.restored) { 'Texture upscale already restored.'; return }
    Assert-ClientClosed
    foreach ($entry in $state.files) {
        $current = Join-Path $client $entry.path
        if ((Hash $current) -ne $entry.staged) { throw "Archive changed after this batch was installed (restore later batches first): $($entry.path). No files restored." }
        if ((Hash (Join-Path $state.backupRoot $entry.path)) -ne $entry.original) { throw "Backup verification failed: $($entry.path). No files restored." }
    }
    foreach ($entry in $state.files) { Replace-Archive (Join-Path $state.backupRoot $entry.path) (Join-Path $client $entry.path) }
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
$original = 0; $installed = 0; $superseded = 0
foreach ($entry in $manifest.files) {
    if ($entry.path -notmatch '^Levels/[A-Za-z0-9_]+/[A-Za-z0-9_]+\.pak$') { throw "Unsupported archive path: $($entry.path)" }
    if ((Hash (Join-Path $package $entry.path)) -ne $entry.staged) { throw "Package checksum failed: $($entry.path)" }
    $hash = Hash (Join-Path $client $entry.path)
    if ($hash -eq $entry.original) { $original++ } elseif ($hash -eq $entry.staged) { $installed++ }
    elseif ($later[$entry.path + '|' + $entry.staged] -eq $hash) { $superseded++ }
    else { throw "Archive changed after the package was built: $($entry.path). Rebuild the package. No files changed." }
}
if ($VerifyOnly) { "Verified $($manifest.files.Count) texture archives: $original original / $installed upscaled / $superseded carried into a later batch."; return }
if ($installed + $superseded -eq $manifest.files.Count) { 'Texture upscale already installed.'; return }
if ($original -ne $manifest.files.Count) { throw 'Mixed archive states detected. No files changed.' }
Assert-ClientClosed
$backup = Join-Path $client ('Mods/Backups/TextureUpscale/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
foreach ($entry in $manifest.files) {
    $target = Join-Path $backup $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $client $entry.path) -Destination $target
    if ((Hash $target) -ne $entry.original) { throw 'Backup verification failed. No archives changed.' }
}
$completed = @()
try {
    foreach ($entry in $manifest.files) {
        $completed += $entry
        $target = Join-Path $client $entry.path
        Replace-Archive (Join-Path $package $entry.path) $target
        if ((Hash $target) -ne $entry.staged) { throw "Installed checksum failed: $($entry.path)" }
    }
} catch {
    $failure = $_
    foreach ($entry in $completed) { Replace-Archive (Join-Path $backup $entry.path) (Join-Path $client $entry.path) }
    throw $failure
}
New-Item -ItemType Directory -Path $modRoot -Force | Out-Null
[ordered]@{packageId=$manifest.packageId; zones=$manifest.zones; backupRoot=$backup; restored=$false; appliedAt=(Get-Date -Format 'o')
    files=@($manifest.files | ForEach-Object { [ordered]@{path=$_.path; original=$_.original; staged=$_.staged} })} |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding UTF8
"Installed $($manifest.files.Count) upscaled texture archives. Verified backups: $backup"
