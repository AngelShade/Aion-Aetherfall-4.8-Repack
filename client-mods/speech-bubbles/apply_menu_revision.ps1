param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
$client=(Resolve-Path -LiteralPath $manifest.clientRoot).Path
if($manifest.speechReceipt -notmatch '^SpeechBubbles-backups/\d{8}-\d{6}-\d{3}/manifest\.json$'){throw 'Invalid speech receipt path.'}
$receipt=Get-Content -Raw -LiteralPath (Join-Path $client $manifest.speechReceipt) | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'GraphicsCompatibility.ps1')
if(Test-SpeechClientRunning $client){throw 'Close Aion normally before installing the native chat-menu revision.'}
$expected=@('bin64/Game.dll','bin64/AionSpeechBubbles.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak',$manifest.speechReceipt)+@(Get-SpeechGraphicsPaths $receipt)
if($manifest.artworkArchives){
 $art=@('Data/ui/ui.pak','Textures/ui/ui.pak')
 if(Compare-Object ($manifest.artworkArchives|Sort-Object) ($art|Sort-Object)){throw 'Unexpected artwork archives.'}
 $expected+=$art
 $originalRoot=Split-Path -Parent $manifest.speechReceipt
 $originals=@($art | ForEach-Object {($originalRoot.Replace('\','/')+'/'+$_)})
 foreach($rel in $manifest.newOriginals){if($rel -notin $originals){throw 'Unexpected artwork backup.'};$expected+=$rel}
}
if($manifest.files.Count -ne $expected.Count -or (Compare-Object ($manifest.files.path|Sort-Object) ($expected|Sort-Object))){throw 'Unexpected menu revision targets.'}
foreach($e in $manifest.files){
 $target=Join-Path $client $e.path
 if(($null -eq $e.original -and (Test-Path -LiteralPath $target)) -or ($null -ne $e.original -and (Get-FileHash -LiteralPath $target).Hash -ne $e.original) -or (Get-FileHash -LiteralPath (Join-Path $prepared $e.path)).Hash -ne $e.installed){throw "Client or prepared patch changed: $($e.path)"}
}
$backup=Join-Path $client ('SpeechBubbles-backups/menu-revision-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
foreach($e in $manifest.files){
 if($null -eq $e.original){continue}
 $saved=Join-Path $backup $e.path
 New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $client $e.path) -Destination $saved
 if((Get-FileHash -LiteralPath $saved).Hash -ne $e.original){throw 'Menu revision backup failed.'}
}
Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
try{
 foreach($e in $manifest.files | Sort-Object { $_.path -eq 'bin64/Game.dll' }){
  New-Item -ItemType Directory -Path (Split-Path -Parent (Join-Path $client $e.path)) -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $prepared $e.path) -Destination (Join-Path $client $e.path)
  if((Get-FileHash -LiteralPath (Join-Path $client $e.path)).Hash -ne $e.installed){throw 'Menu revision verification failed.'}
 }
}catch{
 foreach($e in $manifest.files){
  if($null -eq $e.original){Remove-Item -LiteralPath (Join-Path $client $e.path) -ErrorAction SilentlyContinue}
  else{Copy-Item -LiteralPath (Join-Path $backup $e.path) -Destination (Join-Path $client $e.path)}
 }
 throw
}
Write-Output "Installed native shared-character Chat Bubble menu. Verified recovery backup: $backup"
