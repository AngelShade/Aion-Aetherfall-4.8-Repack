param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
$clientRoot=(Resolve-Path -LiteralPath $manifest.clientRoot).Path
$expected=@('bin64/Game.dll','bin64/AionSpeechBubbles.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak')
. (Join-Path $PSScriptRoot 'GraphicsCompatibility.ps1')
if (Test-SpeechClientRunning $clientRoot) { throw 'Fully close Aion before installation.' }
$expected+=@(Get-SpeechGraphicsPaths $manifest)
if($manifest.artworkArchives){
 $art=@('Data/ui/ui.pak','Textures/ui/ui.pak')
 if(Compare-Object ($manifest.artworkArchives|Sort-Object) ($art|Sort-Object)){throw 'Unexpected artwork archives.'}
 $expected+=$art
}
if (@($manifest.files).Count -ne $expected.Count -or (Compare-Object ($manifest.files.path | Sort-Object) ($expected | Sort-Object))) { throw 'Unexpected replacement list.' }
foreach($entry in $manifest.files) {
 $target=Join-Path $clientRoot $entry.path
 if ((Get-FileHash -LiteralPath (Join-Path $prepared $entry.path)).Hash -ne $entry.installed) { throw 'Prepared file changed.' }
 if ($null -eq $entry.original) {
  if (Test-Path -LiteralPath $target) { throw 'Added DLL already exists; preserve it first.' }
 } elseif ((Get-FileHash -LiteralPath $target).Hash -ne $entry.original) { throw "Client changed since preparation: $($entry.path)" }
}
$backup=Join-Path $clientRoot ('SpeechBubbles-backups\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach($entry in $manifest.files) {
 if ($null -eq $entry.original) { continue }
 $saved=Join-Path $backup $entry.path
 New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $clientRoot $entry.path) -Destination $saved
 if ((Get-FileHash -LiteralPath $saved).Hash -ne $entry.original) { throw 'Backup verification failed.' }
}
Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
try {
 # Install dependencies and UI first, then the DLL that imports the extension.
 foreach($entry in $manifest.files | Sort-Object { $_.path -eq 'bin64/Game.dll' }) {
  New-Item -ItemType Directory -Path (Split-Path -Parent (Join-Path $clientRoot $entry.path)) -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $prepared $entry.path) -Destination (Join-Path $clientRoot $entry.path)
  if ((Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -ne $entry.installed) { throw 'Installed file verification failed.' }
 }
} catch {
 foreach($entry in $manifest.files) {
  $target=Join-Path $clientRoot $entry.path
  if ($null -eq $entry.original) { if(Test-Path -LiteralPath $target){Remove-Item -LiteralPath $target} }
  else { Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination $target }
 }
 throw
}
Write-Output "Speech bubbles installed; verified backup: $backup"
