param([string]$ClientPath = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Close Aion before restoring its renderer.' }
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$modRoot = Join-Path $clientRoot 'DXVK'
$statePath = Join-Path $modRoot 'installed.json'
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'RendererCommon.ps1')
if ($state.clientRoot -ne $clientRoot -or $state.files.Count -ne 3) { throw 'Unexpected DXVK restore record.' }
$backup = (Resolve-Path -LiteralPath $state.backupRoot).Path
$expectedRoot = (Join-Path $clientRoot 'DXVK-backups').TrimEnd('\') + '\'
if (-not $backup.StartsWith($expectedRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected DXVK backup path.' }
foreach ($entry in $state.files) {
    if (@('bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf') -cnotcontains $entry.path) { throw 'Unexpected restore target.' }
    $target = Resolve-RendererFile $clientRoot $entry.path
    if ((Get-FileHash -LiteralPath $target).Hash -ne $entry.installed) { throw "Later changes detected: $($entry.path). Restore stopped." }
    if ($null -ne $entry.original -and (Get-FileHash -LiteralPath (Join-Path $backup $entry.path)).Hash -ne $entry.original) {
        throw 'Original backup hash failed.'
    }
}
# Remove the menu first so its backup returns Game.dll to the cursor-patched
# build before the full renderer uninstall restores the earlier native build.
$menuState = Join-Path $modRoot 'graphics-menu/installed.json'
$nativePatches = @($state.nativeCursorPatch.files | Where-Object { $null -ne $_ })
foreach ($entry in $nativePatches) {
    if (@('bin64/XRenderD3D9.dll','bin64/Game.dll') -cnotcontains $entry.path) { throw 'Unexpected native cursor restore target.' }
    $saved = (Resolve-Path -LiteralPath $entry.backupPath).Path
    if (-not $saved.StartsWith($expectedRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected native cursor backup path.' }
    if ((Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -ne $entry.installed) { throw 'Later native client changes detected; restore stopped.' }
    if ((Get-FileHash -LiteralPath $saved).Hash -ne $entry.original) { throw 'Native cursor backup checksum failed.' }
}
if (Test-Path -LiteralPath $menuState) {
    & (Join-Path $modRoot 'graphics-menu/RestoreGraphicsMenu.ps1') -ClientPath $clientRoot
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
}
& (Join-Path $modRoot 'QualityProfile.ps1') -ClientPath $clientRoot -Restore
foreach ($entry in $nativePatches) {
    Copy-Item -LiteralPath $entry.backupPath -Destination (Join-Path $clientRoot $entry.path) -Force
}
foreach ($entry in $state.files) {
    $target = Resolve-RendererFile $clientRoot $entry.path
    if ($null -eq $entry.original) { Remove-Item -LiteralPath $target }
    else {
        Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination (Join-Path $clientRoot $entry.path) -Force
        if ($target -ne (Join-Path $clientRoot $entry.path)) { Remove-Item -LiteralPath $target }
    }
}
$launcherPath = Join-Path $clientRoot 'Aion Start.bat'
$launcher = [IO.File]::ReadAllText($launcherPath)
$launcher = [regex]::Replace($launcher,'(?ms)^rem BEGIN DXVK QUALITY\r?\n.*?^rem END DXVK QUALITY\r?\n?','')
$launcher = [regex]::Replace($launcher,'(?ms)^rem BEGIN RENDERER MENU\r?\n.*?^rem END RENDERER MENU\r?\n?','')
[IO.File]::WriteAllText($launcherPath,$launcher,[Text.Encoding]::ASCII)
$state | Add-Member -NotePropertyName restoredAt -NotePropertyValue (Get-Date -Format 'o') -Force
$state | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statePath -Encoding UTF8
'DXVK and its native cursor patch removed; previous managed graphics settings restored.'
