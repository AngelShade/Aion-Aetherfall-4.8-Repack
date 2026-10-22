param(
    [Parameter(Mandatory = $true)][string]$ClientPath,
    [Parameter(Mandatory = $true)][string]$Backup
)
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$receipt = Get-Content -LiteralPath (Join-Path $Backup 'receipt.json') -Raw | ConvertFrom-Json
$target = Join-Path $client 'bin64/version.dll'
$ini = Join-Path $client 'bin64/mods.ini'
if (Get-Process -Name 'aion.bin', 'aion' -ErrorAction SilentlyContinue) { throw 'Close Aion before restoring version.dll.' }
if ((Get-FileHash -LiteralPath $target).Hash.ToLower() -ne $receipt.installed) { throw 'version.dll changed after the camera fix install; stop.' }
Copy-Item -LiteralPath (Join-Path $Backup 'bin64/version.dll') -Destination $target
if ((Get-FileHash -LiteralPath $target).Hash.ToLower() -ne $receipt.previous) { throw 'Restored version.dll hash mismatch.' }
$savedIni = Join-Path $Backup 'bin64/mods.ini'
if (Test-Path -LiteralPath $savedIni) { Copy-Item -LiteralPath $savedIni -Destination $ini } else { Remove-Item -LiteralPath $ini -Force -ErrorAction SilentlyContinue }
"Restored version.dll $($receipt.previous)"
