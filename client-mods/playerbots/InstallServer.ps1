param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$botPrepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$botManifest=Get-Content -Raw -LiteralPath (Join-Path $botPrepared 'manifest.json') | ConvertFrom-Json
if ($botManifest.feature -ne 'player-companions-server') { throw 'Wrong server payload.' }
$botDeployment=(Resolve-Path -LiteralPath $botManifest.deployment).Path
if ($botDeployment -ne [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../target-deploy/game-server'))) { throw 'Unexpected server deployment.' }
$botSocket=[Net.Sockets.TcpClient]::new()
try { $botSocket.Connect('127.0.0.1',7777); throw 'GameServer must finish graceful shutdown before installation.' }
catch [Net.Sockets.SocketException] { } finally { $botSocket.Dispose() }
foreach ($botEntry in $botManifest.files) {
    if ($botEntry.path -notmatch '^(libs/game-server-4\.8-SNAPSHOT\.jar|data/handlers/playercommands/Bot\.java|config/administration/commands\.properties|config/main/playerbots\.properties|sql/playerbots\.sql|config/playerbots/media/bots\.(html|css|js))$') { throw 'Unexpected companion installation path.' }
    $botTarget=[IO.Path]::GetFullPath((Join-Path $botDeployment $botEntry.path))
    if (!$botTarget.StartsWith($botDeployment+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Target outside deployment.' }
    if ((Get-FileHash -LiteralPath (Join-Path $botPrepared $botEntry.path)).Hash -ne $botEntry.sha256) { throw 'Prepared companion payload changed.' }
    if ($null -eq $botEntry.original) { if (Test-Path -LiteralPath $botTarget) { throw 'Later server file exists.' } }
    elseif ((Get-FileHash -LiteralPath $botTarget).Hash -ne $botEntry.original) { throw 'Installed server changed since staging.' }
}
$botBackup=Join-Path $botDeployment ('backups/playerbots-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $botBackup | Out-Null
foreach ($botEntry in $botManifest.files) {
    if ($null -ne $botEntry.original) {
        $botSaved=Join-Path $botBackup $botEntry.path;New-Item -ItemType Directory -Path (Split-Path -Parent $botSaved) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $botDeployment $botEntry.path) -Destination $botSaved
        if ((Get-FileHash -LiteralPath $botSaved).Hash -ne $botEntry.original) { throw 'Server backup verification failed.' }
    }
}
Copy-Item -LiteralPath (Join-Path $botPrepared 'manifest.json') -Destination (Join-Path $botBackup 'manifest.json')
try {
    foreach ($botEntry in $botManifest.files) {
        $botTarget=Join-Path $botDeployment $botEntry.path;New-Item -ItemType Directory -Path (Split-Path -Parent $botTarget) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $botPrepared $botEntry.path) -Destination $botTarget
        if ((Get-FileHash -LiteralPath $botTarget).Hash -ne $botEntry.sha256) { throw 'Installed companion server hash mismatch.' }
    }
} catch {
    $botInstallError=$_
    foreach ($botEntry in $botManifest.files) {
        $botTarget=Join-Path $botDeployment $botEntry.path
        if ($null -ne $botEntry.original) { Copy-Item -LiteralPath (Join-Path $botBackup $botEntry.path) -Destination $botTarget }
        elseif (Test-Path -LiteralPath $botTarget) { Remove-Item -LiteralPath $botTarget }
    }
    throw $botInstallError
}
@{feature='player-companions-server';installedAt=(Get-Date).ToString('o');backup=$botBackup;files=$botManifest.files;preservedJarEntries=$botManifest.preservedJarEntries} |
    ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $botBackup 'installed.json') -Encoding utf8
Write-Output "OK: companion server installed and hash verified. Backup: $botBackup"
