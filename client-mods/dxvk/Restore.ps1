param([string]$ClientPath = ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))))
$ErrorActionPreference = 'Stop'
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Close Aion before restoring its renderer.' }
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$modRoot = Join-Path $clientRoot 'Mods/Renderer'
$statePath = Join-Path $modRoot 'installed.json'
. (Join-Path $modRoot 'ClientOwnership.ps1')
$state = Read-OwnershipJson $statePath
. (Join-Path $PSScriptRoot 'RendererCommon.ps1')
if ($state.clientRoot -ne $clientRoot -or $state.files.Count -ne 3) { throw 'Unexpected DXVK restore record.' }
$backup = [IO.Path]::GetFullPath($state.backupRoot)
$expectedRoot = (Join-Path $clientRoot 'Mods/Backups/Renderer').TrimEnd('\') + '\'
if (@($state.files | Where-Object { $null -ne $_.original }).Count -gt 0 -and -not $backup.StartsWith($expectedRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected DXVK backup path.' }
foreach ($entry in $state.files) {
    if (@('bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf') -cnotcontains $entry.path) { throw 'Unexpected restore target.' }
    $target = Resolve-RendererFile $clientRoot $entry.path
    if ((Get-FileHash -LiteralPath $target).Hash -ne $entry.installed) { throw "Later changes detected: $($entry.path). Restore stopped." }
    if ($null -ne $entry.original -and (Get-FileHash -LiteralPath (Join-Path $backup $entry.path)).Hash -ne $entry.original) {
        throw 'Original backup hash failed.'
    }
}
# Both native mods restore only their owned parts; no shared Game.dll baseline.
$menuState = Join-Path $clientRoot 'Mods/GraphicsMenu/installed.json'
if ($state.nativeCursorPatch -and -not $state.nativeCursorPatch.restoredAt) { [AetherfallOwnership.Parts]::VerifyCursor($clientRoot) }
if ((Test-Path -LiteralPath $menuState) -and -not (Read-OwnershipJson $menuState).restoredAt) { Confirm-GraphicsOwnership $clientRoot }
if (Test-Path -LiteralPath $menuState) {
    & (Join-Path $clientRoot 'Mods/GraphicsMenu/RestoreGraphicsMenu.ps1') -ClientPath $clientRoot
    $state = Read-OwnershipJson $statePath
}
& (Join-Path $modRoot 'QualityProfile.ps1') -ClientPath $clientRoot -Restore
if ($state.nativeCursorPatch -and -not $state.nativeCursorPatch.restoredAt) {
    [AetherfallOwnership.Parts]::Cursor($clientRoot,$true)
    $state.PSObject.Properties.Remove('nativeCursorPatch')
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
if (Test-Path -LiteralPath $launcherPath) {
    $launcher = [IO.File]::ReadAllText($launcherPath)
    $launcher = [regex]::Replace($launcher,'(?ms)^rem BEGIN DXVK QUALITY\r?\n.*?^rem END DXVK QUALITY\r?\n?','')
    $launcher = [regex]::Replace($launcher,'(?ms)^rem BEGIN RENDERER MENU\r?\n.*?^rem END RENDERER MENU\r?\n?','')
    [IO.File]::WriteAllText($launcherPath,$launcher,[Text.Encoding]::ASCII)
}
$state | Add-Member -NotePropertyName restoredAt -NotePropertyValue (Get-Date -Format 'o') -Force
Write-OwnershipJson $statePath $state
'DXVK and its native cursor patch removed; previous managed graphics settings restored.'
