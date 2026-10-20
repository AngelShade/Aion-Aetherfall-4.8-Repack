param(
    [Parameter(Mandatory=$true)][string]$ClientPath,
    [Parameter(Mandatory=$true)][string]$PreparedPath,
    [Parameter(Mandatory=$true)][string]$BackupPath,
    [switch]$VerifyOnly
)
# Install a prepare_wardrobe_layout.py package: back up, replace, verify, roll back on failure.
$ErrorActionPreference='Stop'
$client=(Resolve-Path -LiteralPath $ClientPath).Path
$package=(Resolve-Path -LiteralPath $PreparedPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $package 'manifest.json') | ConvertFrom-Json
foreach ($entry in $manifest.files) {
    if ((Get-FileHash -LiteralPath (Join-Path $package $entry.path)).Hash -ne $entry.staged) { throw ('Staged file changed: '+$entry.path) }
    if ((Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash -ne $entry.original) { throw ('Installed file changed since staging: '+$entry.path) }
}
if ($VerifyOnly) { Write-Output 'OK: Wardrobe layout package verified; no installation.';return }
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Close Aion before replacing the Wardrobe files.' }
if (Test-Path -LiteralPath $BackupPath) { throw 'Backup directory already exists.' }
New-Item -ItemType Directory -Path $BackupPath | Out-Null
foreach ($entry in $manifest.files) {
    $saved=Join-Path $BackupPath $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $client $entry.path) -Destination $saved
    if ((Get-FileHash -LiteralPath $saved).Hash -ne $entry.original) { throw 'Wardrobe backup mismatch.' }
}
Copy-Item -LiteralPath (Join-Path $package 'manifest.json') -Destination (Join-Path $BackupPath 'manifest.json')
try {
    foreach ($entry in $manifest.files) {
        Copy-Item -LiteralPath (Join-Path $package $entry.path) -Destination (Join-Path $client $entry.path)
        if ((Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash -ne $entry.staged) { throw 'Installed Wardrobe hash mismatch.' }
    }
    & java (Join-Path $PSScriptRoot '../season-pass/VerifySignatures.java') $client $client
    if ($LASTEXITCODE -ne 0) { throw 'Installed addon signatures failed.' }
    & (Join-Path $client 'Mods/GraphicsMenu/Install-GraphicsMenu.ps1') -ClientPath $client -VerifyOnly
    foreach ($entry in $manifest.preservedFiles) {
        if ((Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash -ne $entry.sha256) { throw ('Preserved client file changed: '+$entry.path) }
    }
} catch {
    $failure=$_
    foreach ($entry in $manifest.files) { Copy-Item -LiteralPath (Join-Path $BackupPath $entry.path) -Destination (Join-Path $client $entry.path) }
    throw $failure
}
@{feature=$manifest.feature;revision=$manifest.revision;installedAt=(Get-Date).ToString('o');client=$client;backup=$BackupPath;files=$manifest.files} |
    ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $BackupPath 'receipt.json') -Encoding utf8
Write-Output "OK: Wardrobe layout installed; other addon entries and client files preserved. Backup: $BackupPath"
