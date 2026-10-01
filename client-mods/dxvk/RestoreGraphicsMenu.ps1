param([Parameter(Mandatory=$true)][string]$ClientPath)
$ErrorActionPreference = 'Stop'
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Exit Aion normally before restoring its Graphics menu.' }
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$statePath = Join-Path $client 'DXVK/graphics-menu/installed.json'
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if ($state.restoredAt) { 'Graphics menu already restored.'; return }
$backup = (Resolve-Path -LiteralPath $state.backupRoot).Path
$expectedRoot = (Join-Path $client 'DXVK-backups').TrimEnd('\') + '\'
if ($state.clientRoot -ne $client -or -not $backup.StartsWith($expectedRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid menu restore record.' }
foreach ($entry in $state.files) {
    if (@('bin64/Game.dll','Data/ui/game/game.pak','bin64/AionGraphicsMenu.dll','L10N/enu/Data/data.pak') -cnotcontains $entry.path) { throw 'Invalid menu restore target.' }
    if ((Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash -ne $entry.installed) { throw 'Later client changes detected; menu restore stopped.' }
    if ($null -ne $entry.original -and (Get-FileHash -LiteralPath (Join-Path $backup $entry.path)).Hash -ne $entry.original) { throw 'Menu backup checksum failed.' }
}
$dxvkPath = Join-Path $client 'DXVK/installed.json'
$dxvk = Get-Content -LiteralPath $dxvkPath -Raw | ConvertFrom-Json
$game = @($dxvk.nativeCursorPatch.files | Where-Object { $_.path -eq 'bin64/Game.dll' })
if ($game.Count -ne 1 -or $game[0].installed -ne $state.files[0].installed) { throw 'Cursor tracking changed; menu restore stopped.' }
foreach ($entry in $state.files) {
    $target = Join-Path $client $entry.path
    if ($null -eq $entry.original) { Remove-Item -LiteralPath $target }
    else { Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination $target -Force }
}
$game[0].installed = $state.files[0].original
$dxvk | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath $dxvkPath -Encoding UTF8
$launcherPath = Join-Path $client 'Aion Start.bat'
$launcher = [IO.File]::ReadAllText($launcherPath)
$launcher = [regex]::Replace($launcher,'(?ms)^rem BEGIN RENDERER MENU\r?\n.*?^rem END RENDERER MENU\r?\n?','')
[IO.File]::WriteAllText($launcherPath,$launcher,[Text.Encoding]::ASCII)
$state | Add-Member -NotePropertyName restoredAt -NotePropertyValue (Get-Date -Format 'o') -Force
$state | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $statePath -Encoding UTF8
'Graphics menu restored. The last selected renderer and native cursor remain in place.'
