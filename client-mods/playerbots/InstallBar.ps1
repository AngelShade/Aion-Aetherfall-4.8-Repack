param(
    [Parameter(Mandatory=$true)][string]$ClientPath,
    [Parameter(Mandatory=$true)][string]$PreparedPath,
    [switch]$VerifyOnly
)
$ErrorActionPreference='Stop'
$barClient=(Resolve-Path -LiteralPath $ClientPath).Path
$barPackage=(Resolve-Path -LiteralPath $PreparedPath).Path
if (!$VerifyOnly -and (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue)) { throw 'Close Aion before replacing the command bar files.' }
& python (Join-Path $PSScriptRoot 'verify_bar.py') --client $barClient --staged $barPackage
if ($LASTEXITCODE -ne 0) { throw 'Command bar verification failed.' }
$barManifest=Get-Content -Raw -LiteralPath (Join-Path $barPackage 'manifest.json') | ConvertFrom-Json
if ($VerifyOnly) { Write-Output 'OK: command bar verified; no installation.';return }
$barBackup=Join-Path $barClient ('TransmogMenu-backups/playerbot-bar-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $barBackup | Out-Null
foreach ($barEntry in $barManifest.files) {
    $barSaved=Join-Path $barBackup $barEntry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $barSaved) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $barClient $barEntry.path) -Destination $barSaved
    if ((Get-FileHash -LiteralPath $barSaved).Hash -ne $barEntry.original) { throw 'Command bar backup mismatch.' }
}
Copy-Item -LiteralPath (Join-Path $barPackage 'manifest.json') -Destination (Join-Path $barBackup 'manifest.json')
try {
    foreach ($barEntry in $barManifest.files) {
        Copy-Item -LiteralPath (Join-Path $barPackage $barEntry.path) -Destination (Join-Path $barClient $barEntry.path)
        if ((Get-FileHash -LiteralPath (Join-Path $barClient $barEntry.path)).Hash -ne $barEntry.staged) { throw 'Installed command bar hash mismatch.' }
    }
    & java (Join-Path $PSScriptRoot '../season-pass/VerifySignatures.java') $barClient $barClient
    if ($LASTEXITCODE -ne 0) { throw 'Installed addon signatures failed.' }
    & (Join-Path $barClient 'DXVK/graphics-menu/ApplyGraphicsMenu.ps1') -ClientPath $barClient -VerifyOnly
    & (Join-Path $barClient 'DXVK/cursor-fix/InstallNativeCursorPatch.ps1') -ClientPath $barClient
    foreach ($barEntry in $barManifest.preservedFiles) {
        if ((Get-FileHash -LiteralPath (Join-Path $barClient $barEntry.path)).Hash -ne $barEntry.sha256) { throw ('Preserved client changed: '+$barEntry.path) }
    }
} catch {
    $barFailure=$_
    foreach ($barEntry in $barManifest.files) { Copy-Item -LiteralPath (Join-Path $barBackup $barEntry.path) -Destination (Join-Path $barClient $barEntry.path) }
    throw $barFailure
}
@{feature='playerbot-party-bar';installedAt=(Get-Date).ToString('o');backup=$barBackup;files=$barManifest.files;preservedFiles=$barManifest.preservedFiles} |
    ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $barBackup 'installed.json') -Encoding utf8
Write-Output "OK: native companion command bar installed; all existing menus, keys, graphics/cursor recovery and server fixes preserved. Backup: $barBackup"
