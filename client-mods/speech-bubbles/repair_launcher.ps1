param([Parameter(Mandatory=$true)][string]$PreparedPath,[Parameter(Mandatory=$true)][string]$BackupPath)
$ErrorActionPreference='Stop'
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$backup=(Resolve-Path -LiteralPath $BackupPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
$original=Get-Content -Raw -LiteralPath (Join-Path $backup 'manifest.json') | ConvertFrom-Json
$client=(Resolve-Path -LiteralPath $manifest.clientRoot).Path
if($client -ne $original.clientRoot -or $original.graphicsCompatibility -or -not $backup.StartsWith((Join-Path $client 'SpeechBubbles-backups\'),[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected speech backup.'}
if(Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue){throw 'Fully close Aion before launcher repair.'}
. (Join-Path $PSScriptRoot 'GraphicsCompatibility.ps1')
$extra=@(Get-SpeechGraphicsPaths $manifest)
$main=@('bin64/Game.dll','bin64/AionSpeechBubbles.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak')
$expected=$main+$extra
if($extra.Count -eq 0 -or $manifest.files.Count -ne $expected.Count -or (Compare-Object ($manifest.files.path|Sort-Object) ($expected|Sort-Object))){throw 'Unexpected repair file list.'}
if($original.files.Count -ne 4 -or (Compare-Object ($original.files.path|Sort-Object) ($main|Sort-Object))){throw 'Unexpected original receipt.'}
foreach($e in $manifest.files){
 $target=Join-Path $client $e.path
 if((Get-FileHash -LiteralPath (Join-Path $prepared $e.path)).Hash -ne $e.installed){throw 'Prepared repair changed.'}
 if($main -contains $e.path){
  $old=@($original.files|Where-Object {$_.path -eq $e.path})
  if($old.Count -ne 1 -or $old[0].original -ne $e.original -or $old[0].installed -ne $e.installed -or (Get-FileHash -LiteralPath $target).Hash -ne $e.installed){throw 'Recorded speech installation changed.'}
 } elseif($null -eq $e.original){if(Test-Path -LiteralPath $target){throw 'New compatibility baseline already exists.'}}
 elseif((Get-FileHash -LiteralPath $target).Hash -ne $e.original){throw 'Launcher tracking changed since preparation.'}
}
$entries=@($manifest.files|Where-Object {$extra -contains $_.path})
foreach($e in $entries){
 if($null -eq $e.original){continue}
 $saved=Join-Path $backup $e.path
 if(Test-Path -LiteralPath $saved){throw 'Repair backup target already exists.'}
 New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $client $e.path) -Destination $saved
 if((Get-FileHash -LiteralPath $saved).Hash -ne $e.original){throw 'Repair backup verification failed.'}
}
$receiptBytes=[IO.File]::ReadAllBytes((Join-Path $backup 'manifest.json'))
Copy-Item -LiteralPath (Join-Path $backup 'manifest.json') -Destination (Join-Path $backup 'manifest.before-launcher-repair.json')
try{
 foreach($e in $entries){
  $target=Join-Path $client $e.path
  New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $prepared $e.path) -Destination $target
  if((Get-FileHash -LiteralPath $target).Hash -ne $e.installed){throw 'Compatibility install verification failed.'}
 }
 Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
}catch{
 foreach($e in $entries){
  $target=Join-Path $client $e.path
  if($null -eq $e.original){if(Test-Path -LiteralPath $target){Remove-Item -LiteralPath $target}}
  else{Copy-Item -LiteralPath (Join-Path $backup $e.path) -Destination $target}
 }
 [IO.File]::WriteAllBytes((Join-Path $backup 'manifest.json'),$receiptBytes)
 throw
}
Write-Output 'Graphics/cursor launch tracking repaired; live DLL and UI bytes unchanged. Speech restore also restores the prior tracking.'
