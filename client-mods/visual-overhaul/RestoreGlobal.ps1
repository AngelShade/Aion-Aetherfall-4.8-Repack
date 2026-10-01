param([string]$ClientPath=(Split-Path -Parent $PSScriptRoot))
. (Join-Path $PSScriptRoot 'GlobalCommon.ps1')
Assert-ClientClosed
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$root = Join-Path $client 'GraphicsOverhaul'
$queue = Get-Content -LiteralPath (Join-Path $root 'queued.json') -Raw | ConvertFrom-Json
if ($queue.clientRoot -ne $client) { throw 'Restore record belongs to another client.' }
$manifest = Read-GlobalManifest (Join-Path $root ('packages/' + $queue.packageId))
$statePath = Join-Path $root 'installed.json'
if (Test-Path -LiteralPath $statePath) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($state.packageId -ne $queue.packageId -or $state.clientRoot -ne $client) { throw 'Restore state mismatch.' }
    $backup = (Resolve-Path -LiteralPath $state.backupRoot).Path
    $prefix = (Join-Path $client 'GraphicsOverhaul-backups').TrimEnd('\') + '\'
    if (-not $backup.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected backup directory.' }
    foreach ($entry in $manifest.files) {
        $hash = (Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash
        if ($hash -ne $entry.staged -and $hash -ne $entry.original) { throw "Later archive edit detected: $($entry.path). Restore stopped before changing any files." }
        if ((Get-FileHash -LiteralPath (Join-Path $backup $entry.path)).Hash -ne $entry.original) { throw 'Backup hash failed.' }
    }
    foreach ($entry in $manifest.files) {
        Copy-GlobalArchive (Join-Path $backup $entry.path) (Join-Path $client $entry.path)
        if ((Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash -ne $entry.original) { throw 'Restored archive checksum failed.' }
    }
    $state.restored = $true
    $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
}
$launcherPath = Join-Path $client 'Aion Start.bat'
$launcher = [IO.File]::ReadAllText($launcherPath)
$launcher = [regex]::Replace($launcher,'(?ms)^rem BEGIN GLOBAL GRAPHICS\r?\n.*?^rem END GLOBAL GRAPHICS\r?\n?','')
if ($queue.previousVisualBlock -and $launcher -notmatch 'rem BEGIN VISUAL OVERHAUL') {
    $match = [regex]::Match($launcher,'(?im)^start\s+""\s+"bin64\\aion\.bin"[^\r\n]*')
    if (-not $match.Success) { throw 'Game start command not found. Archives restored; restore the launcher manually.' }
    $launcher = $launcher.Insert($match.Index,$queue.previousVisualBlock)
}
[IO.File]::WriteAllText($launcherPath,$launcher,[Text.Encoding]::ASCII)
$queue.status = 'Restored'
$queue | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'queued.json') -Encoding UTF8
'Previous artwork restored; Vulkan effects removed from startup. DXVK and the cursor fix remain active.'
