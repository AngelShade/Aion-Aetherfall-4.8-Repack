param([Parameter(Mandatory=$true)][string]$ClientPath)
$ErrorActionPreference = 'Stop'
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$statePath = Join-Path $clientRoot 'DXVK/installed.json'
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'RendererCommon.ps1')
if ($state.clientRoot -ne $clientRoot -or $state.restoredAt) { throw 'Expected an active DXVK installation.' }
foreach ($entry in $state.files) {
    if (@('bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf') -cnotcontains $entry.path) { throw 'Unexpected installed renderer file path.' }
    if ((Get-FileHash -LiteralPath (Resolve-RendererFile $clientRoot $entry.path)).Hash -ne $entry.installed) { throw 'Installed DXVK files changed.' }
}
if (Test-Path -LiteralPath (Join-Path $clientRoot 'bin64/game.dll.patched')) { throw 'A pending Game.dll update must be resolved before applying the cursor patch.' }
if ($state.nativeCursorPatch) {
    foreach ($entry in $state.nativeCursorPatch.files) {
        if (@('bin64/XRenderD3D9.dll','bin64/Game.dll') -cnotcontains $entry.path -or
            (Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -ne $entry.installed) {
            throw 'Installed native cursor patch changed; startup stopped.'
        }
    }
    'Native UI cursor patch is already installed.'
    return
}
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Exit Aion normally before patching its cursor.' }
$patches = @(
    @{path='bin64/XRenderD3D9.dll';hash='c8dafe7fddc5cf364c6bf15c258b6e37fe4f7edb9d405c605285af7311205b2a';offset=0x1202D7;bytes=[byte[]](0x7C,0x0C)},
    @{path='bin64/Game.dll';hash='97deff11c2cc2827eeed24613668a39661dd86c3d646774080bcf1ce782c9b4b';offset=0x551C96;bytes=[byte[]](0x78,0x18)}
)
$pending = @()
foreach ($patch in $patches) {
    $target = Join-Path $clientRoot $patch.path
    if ((Get-FileHash -LiteralPath $target).Hash -ne $patch.hash) { throw "Unsupported native client build: $($patch.path)" }
    $bytes = [IO.File]::ReadAllBytes($target)
    if ($bytes[$patch.offset] -ne $patch.bytes[0] -or $bytes[$patch.offset+1] -ne $patch.bytes[1]) { throw 'Cursor instruction check failed.' }
    # Bypass only the two stereo-depth gates inside the cursor functions.
    # The cursor-mode, cursor-object and visibility checks remain in place.
    $bytes[$patch.offset] = 0x90
    $bytes[$patch.offset+1] = 0x90
    $pending += @{path=$patch.path;original=$patch.hash;after=$bytes;offset=$patch.offset}
}
$backup = Join-Path $clientRoot ('DXVK-backups/native-cursor-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$savedFiles = @('bin64/XRenderD3D9.dll','bin64/Game.dll','DXVK/QualityProfile.ps1','DXVK/Restore.ps1','DXVK/installed.json','system.cfg','SystemOptionGraphics.cfg')
foreach ($path in $savedFiles) {
    $saved = Join-Path $backup $path
    New-Item -ItemType Directory -Path (Split-Path $saved -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $clientRoot $path) -Destination $saved
    if ((Get-FileHash -LiteralPath $saved).Hash -ne (Get-FileHash -LiteralPath (Join-Path $clientRoot $path)).Hash) { throw 'Native cursor backup check failed.' }
}
try {
    # The installed earlier helper also removes the unsuccessful depth override.
    & (Join-Path $clientRoot 'DXVK/QualityProfile.ps1') -ClientPath $clientRoot -Restore
    $installedPatches = @()
    foreach ($patch in $pending) {
        $target = Join-Path $clientRoot $patch.path
        [IO.File]::WriteAllBytes($target,$patch.after)
        $installedPatches += @{path=$patch.path;original=$patch.original;installed=(Get-FileHash -LiteralPath $target).Hash;backupPath=(Join-Path $backup $patch.path);offset=$patch.offset;replacement='9090'}
    }
    foreach ($helper in @('QualityProfile.ps1','Restore.ps1')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $helper) -Destination (Join-Path $clientRoot ('DXVK/' + $helper)) -Force
    }
    & (Join-Path $clientRoot 'DXVK/QualityProfile.ps1') -ClientPath $clientRoot
    $state | Add-Member -NotePropertyName nativeCursorPatch -NotePropertyValue @{
        appliedAt=(Get-Date -Format 'o');architecture='x64';files=$installedPatches;visualConfirmation='Pending player check'
    } -Force
    $state | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $statePath -Encoding UTF8
} catch {
    $failure = $_
    foreach ($path in $savedFiles) { Copy-Item -LiteralPath (Join-Path $backup $path) -Destination (Join-Path $clientRoot $path) -Force }
    throw $failure
}
"Native UI cursor patch installed (four instruction bytes). Backup: $backup"
