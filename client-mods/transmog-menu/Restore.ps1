param(
    [Parameter(Mandatory = $true)][string]$ClientPath,
    [Parameter(Mandatory = $true)][string]$BackupPath
)
$ErrorActionPreference = 'Stop'
if (Get-Process -Name 'aion.bin' -ErrorAction SilentlyContinue) { throw 'Fully close Aion before restoring.' }
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$backup = (Resolve-Path -LiteralPath $BackupPath).Path
$backupRoot = Join-Path $clientRoot 'TransmogMenu-backups'
if (-not $backup.StartsWith($backupRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Backup must be inside this client backup directory.' }
$manifest = Get-Content -Raw -LiteralPath (Join-Path $backup 'manifest.json') | ConvertFrom-Json
if ($manifest.clientRoot -ne $clientRoot) { throw 'Backup belongs to a different client.' }
$expected = @('bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig', 'Plugin/RelicCalc/RelicCalc.pak', 'Plugin/RelicCalc/RelicCalc.pak.sig', 'Pub.key')
if (@($manifest.files).Count -ne $expected.Count -or (Compare-Object ($manifest.files.path | Sort-Object) ($expected | Sort-Object))) { throw 'Unexpected backup file list.' }
foreach ($entry in $manifest.files) {
    if ((Get-FileHash -LiteralPath (Join-Path $backup $entry.path)).Hash -ne $entry.original) { throw "Backup mismatch: $($entry.path)" }
    $current = (Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash
    if ($current -ne $entry.staged -and $current -ne $entry.original) { throw "Client file changed after installation: $($entry.path)" }
}
$legacySaved = [IO.Path]::GetFullPath((Join-Path $backup 'legacy-TransmogMenu'))
$legacy = [IO.Path]::GetFullPath((Join-Path $clientRoot 'Plugin\TransmogMenu'))
if (-not $legacy.StartsWith($clientRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or
    -not $legacySaved.StartsWith($backup + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid legacy addon restore paths.' }
if ((Test-Path -LiteralPath $legacySaved) -and (Test-Path -LiteralPath $legacy)) { throw 'A new TransmogMenu directory exists; preserve it before restoring.' }
foreach ($entry in $manifest.files | Sort-Object { $_.path -eq 'Pub.key' }) {
    Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination (Join-Path $clientRoot $entry.path)
    if ((Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -ne $entry.original) { throw 'Restore verification failed.' }
}
if (Test-Path -LiteralPath $legacySaved) { Move-Item -LiteralPath $legacySaved -Destination $legacy }
Write-Output 'Restored and verified the original client key, signatures, and Relic Appraiser.'
