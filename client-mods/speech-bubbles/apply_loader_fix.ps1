param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
$client=(Resolve-Path -LiteralPath $manifest.clientRoot).Path
if(Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue){throw 'Close the crashed Aion client before repair.'}
if($manifest.speechReceipt -notmatch '^SpeechBubbles-backups/\d{8}-\d{6}-\d{3}/manifest\.json$'){throw 'Invalid speech receipt path.'}
$receipt=Get-Content -Raw -LiteralPath (Join-Path $client $manifest.speechReceipt) | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'GraphicsCompatibility.ps1')
$compat=@(Get-SpeechGraphicsPaths $receipt)
$games=@($compat|Where-Object {$_ -like '*bin64/Game.dll'})
$expected=@('bin64/Game.dll','DXVK/graphics-menu/package/manifest.json','DXVK/graphics-menu/installed.json','DXVK/installed.json',$manifest.speechReceipt)+$games
if($manifest.files.Count -ne 8 -or (Compare-Object ($manifest.files.path|Sort-Object) ($expected|Sort-Object))){throw 'Unexpected loader repair targets.'}
foreach($e in $manifest.files){
 if((Get-FileHash -LiteralPath (Join-Path $client $e.path)).Hash -ne $e.original -or (Get-FileHash -LiteralPath (Join-Path $prepared $e.path)).Hash -ne $e.installed){throw 'Client or prepared repair changed.'}
}
$backup=Join-Path $client ('SpeechBubbles-backups/loader-fix-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
foreach($e in $manifest.files){
 $saved=Join-Path $backup $e.path
 New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $client $e.path) -Destination $saved
 if((Get-FileHash -LiteralPath $saved).Hash -ne $e.original){throw 'Loader repair backup failed.'}
}
Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
try{
 foreach($e in $manifest.files){
  Copy-Item -LiteralPath (Join-Path $prepared $e.path) -Destination (Join-Path $client $e.path)
  if((Get-FileHash -LiteralPath (Join-Path $client $e.path)).Hash -ne $e.installed){throw 'Loader repair verification failed.'}
 }
}catch{
 foreach($e in $manifest.files){Copy-Item -LiteralPath (Join-Path $backup $e.path) -Destination (Join-Path $client $e.path)}
 throw
}
Write-Output "Installed IAT loader permission fix. Verified recovery backup: $backup"
