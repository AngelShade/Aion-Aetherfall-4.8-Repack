param([Parameter(Mandatory=$true)][string]$ClientPath,[switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$root = Join-Path $client 'DXVK/graphics-menu'
$manifest = Get-Content -LiteralPath (Join-Path $root 'package/manifest.json') -Raw | ConvertFrom-Json
$statePath = Join-Path $root 'installed.json'
$state = $null
if (Test-Path -LiteralPath $statePath) { $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json }
if ($state -and $state.restoredAt) { throw 'Graphics menu was restored; re-stage it before reinstalling.' }
$allowed = @('bin64/Game.dll','Data/ui/game/game.pak','bin64/AionGraphicsMenu.dll','L10N/enu/Data/data.pak')
if ($manifest.files.Count -notin @(3,4) -or $manifest.files[0].path -ne 'bin64/Game.dll' -or @($manifest.files.path | Select-Object -Unique).Count -ne $manifest.files.Count) { throw 'Unexpected graphics menu manifest.' }
if ($state -and $state.clientRoot -ne $client) { throw 'Graphics menu state belongs to another client.' }
$pending = @()
foreach ($entry in $manifest.files) {
    if ($allowed -cnotcontains $entry.path) { throw 'Unexpected graphics menu target.' }
    if ((Get-FileHash -LiteralPath (Join-Path $root ('package/' + $entry.path))).Hash -ne $entry.installed) { throw 'Graphics menu payload changed.' }
    $target = Join-Path $client $entry.path
    $expected = $entry.original
    $existing = @($state.files | Where-Object { $_.path -eq $entry.path })
    if ($existing.Count -gt 0) {
        if ($existing.Count -ne 1 -or $existing[0].installed -ne $entry.installed -or $existing[0].original -ne $entry.original) { throw 'Existing menu payload changed; use a reviewed revision.' }
        $expected = $entry.installed
    } else { $pending += $entry }
    if ($null -eq $expected) {
        if (Test-Path -LiteralPath $target) { throw 'Graphics menu DLL already exists outside this installation.' }
    } elseif ((Get-FileHash -LiteralPath $target).Hash -ne $expected) { throw "Later client changes detected: $($entry.path). Menu installation stopped." }
}
if ($pending.Count -eq 0) { 'Graphics menu is already installed and verified.'; return }
if ($VerifyOnly) { 'Graphics menu queued; current client and payload hashes verified.'; return }
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Exit Aion normally before applying its Graphics menu.' }
$dxvkPath = Join-Path $client 'DXVK/installed.json'
$dxvk = Get-Content -LiteralPath $dxvkPath -Raw | ConvertFrom-Json
$game = @($dxvk.nativeCursorPatch.files | Where-Object { $_.path -eq 'bin64/Game.dll' })
$expectedGame = $manifest.files[0].original
if ($state) { $expectedGame = $manifest.files[0].installed }
if ($game.Count -ne 1 -or $game[0].installed -ne $expectedGame) { throw 'Expected the working native cursor patch before graphics menu installation.' }
$backup = Join-Path $client ('DXVK-backups/graphics-menu-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
if ($state) {
    $backup = (Resolve-Path -LiteralPath $state.backupRoot).Path
    $expectedRoot = (Join-Path $client 'DXVK-backups').TrimEnd('\') + '\'
    if (-not $backup.StartsWith($expectedRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid menu backup path.' }
}
New-Item -ItemType Directory -Path $backup -Force | Out-Null
foreach ($entry in $pending) {
    if ($null -eq $entry.original) { continue }
    $saved = Join-Path $backup $entry.path
    New-Item -ItemType Directory -Path (Split-Path $saved -Parent) -Force | Out-Null
    if (-not (Test-Path -LiteralPath $saved)) { Copy-Item -LiteralPath (Join-Path $client $entry.path) -Destination $saved }
    if ((Get-FileHash -LiteralPath $saved).Hash -ne $entry.original) { throw 'Menu backup checksum mismatch.' }
}
$dxvkBefore = [IO.File]::ReadAllBytes($dxvkPath)
$stateBefore = $null
if ($state) { $stateBefore = [IO.File]::ReadAllBytes($statePath) }
if (-not $state) { Copy-Item -LiteralPath $dxvkPath -Destination (Join-Path $backup 'dxvk-installed.json') }
try {
    foreach ($entry in $pending) { Copy-Item -LiteralPath (Join-Path $root ('package/' + $entry.path)) -Destination (Join-Path $client $entry.path) -Force }
    $game[0].installed = $manifest.files[0].installed
    $dxvk | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath $dxvkPath -Encoding UTF8
    @{clientRoot=$client;backupRoot=$backup;files=$manifest.files;installedAt=(Get-Date -Format 'o')} |
        ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $statePath -Encoding UTF8
} catch {
    foreach ($entry in $pending) {
        $target = Join-Path $client $entry.path
        if ($null -eq $entry.original) { Remove-Item -LiteralPath $target -ErrorAction SilentlyContinue }
        else { Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination $target -Force }
    }
    [IO.File]::WriteAllBytes($dxvkPath,$dxvkBefore)
    if ($stateBefore) { [IO.File]::WriteAllBytes($statePath,$stateBefore) }
    else { Remove-Item -LiteralPath $statePath -ErrorAction SilentlyContinue }
    throw
}
'Native Graphics menu installed; cursor and existing client modifications preserved.'
