param(
    [Parameter(Mandatory=$true)][string]$ClientPath,
    [Parameter(Mandatory=$true)][string]$PreparedPath,
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
$botClient = (Resolve-Path -LiteralPath $ClientPath).Path
$botPrepared = (Resolve-Path -LiteralPath $PreparedPath).Path
if (!$VerifyOnly -and (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue)) { throw 'Fully close Aion before installing companions.' }
& python (Join-Path $PSScriptRoot 'verify_package.py') --client $botClient --staged $botPrepared
if ($LASTEXITCODE -ne 0) { throw 'Companion package verification failed.' }
$botManifest = Get-Content -Raw -LiteralPath (Join-Path $botPrepared 'manifest.json') | ConvertFrom-Json
if ($VerifyOnly) { Write-Output 'OK: companion package verified; no installation.'; return }
$botBackup = Join-Path $botClient ('TransmogMenu-backups/playerbots-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $botBackup | Out-Null
foreach ($botEntry in $botManifest.files) {
    if ($null -eq $botEntry.original) { continue }
    $botSaved = Join-Path $botBackup $botEntry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $botSaved) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $botClient $botEntry.path) -Destination $botSaved
    if ((Get-FileHash -LiteralPath $botSaved).Hash -ne $botEntry.original) { throw 'Companion backup verification failed.' }
}
Copy-Item -LiteralPath (Join-Path $botPrepared 'manifest.json') -Destination (Join-Path $botBackup 'manifest.json')
try {
    foreach ($botEntry in $botManifest.files) {
        New-Item -ItemType Directory -Path (Split-Path -Parent (Join-Path $botClient $botEntry.path)) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $botPrepared $botEntry.path) -Destination (Join-Path $botClient $botEntry.path)
        if ((Get-FileHash -LiteralPath (Join-Path $botClient $botEntry.path)).Hash -ne $botEntry.staged) { throw 'Installed companion hash mismatch.' }
    }
    foreach ($botEntry in $botManifest.preservedFiles) {
        if ((Get-FileHash -LiteralPath (Join-Path $botClient $botEntry.path)).Hash -ne $botEntry.sha256) { throw ('Preserved client changed: ' + $botEntry.path) }
    }
    & java (Join-Path $PSScriptRoot '../season-pass/VerifySignatures.java') $botClient $botClient
    if ($LASTEXITCODE -ne 0) { throw 'Installed addon signatures failed.' }
    if ($botManifest.graphicsCompatibility) {
        & (Join-Path $botClient 'DXVK/graphics-menu/ApplyGraphicsMenu.ps1') -ClientPath $botClient -VerifyOnly
        & (Join-Path $botClient 'DXVK/cursor-fix/InstallNativeCursorPatch.ps1') -ClientPath $botClient
    }
} catch {
    $botInstallError = $_
    foreach ($botEntry in $botManifest.files) {
        $botInstalled = Join-Path $botClient $botEntry.path
        if ($null -ne $botEntry.original) { Copy-Item -LiteralPath (Join-Path $botBackup $botEntry.path) -Destination $botInstalled }
        elseif (Test-Path -LiteralPath $botInstalled) { Remove-Item -LiteralPath $botInstalled }
    }
    throw $botInstallError
}
@{feature='player-companions'; installedAt=(Get-Date).ToString('o'); backup=$botBackup; files=$botManifest.files; preservedFiles=$botManifest.preservedFiles} |
    ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $botBackup 'installed.json') -Encoding utf8
Write-Output "OK: $($botManifest.files.Count) companion/recovery files installed; stock model key, existing modifications and graphics/cursor recovery preserved. Backup: $botBackup"
