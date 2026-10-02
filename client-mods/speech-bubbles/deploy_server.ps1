param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$runtime=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\target-deploy\game-server')).Path
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
$expected=@('libs/game-server-4.8-SNAPSHOT.jar','data/handlers/playercommands/Speechbubble.java','config/administration/commands.properties')
if(@($manifest.files).Count -ne 3 -or (Compare-Object ($manifest.files.path|Sort-Object) ($expected|Sort-Object))){throw 'Unexpected deployment list.'}
foreach($e in $manifest.files) {
 $p=Join-Path $runtime $e.path
 if((Get-FileHash -LiteralPath (Join-Path $prepared $e.path)).Hash -ne $e.installed){throw 'Staged hash mismatch.'}
 if($null -eq $e.original){if(Test-Path -LiteralPath $p){throw 'New command already exists.'}}
 elseif((Get-FileHash -LiteralPath $p).Hash -ne $e.original){throw 'Deployment changed; rebuild from the latest JAR.'}
}
$backup=Join-Path $runtime ('backups\speech-bubbles-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach($e in $manifest.files){
 if($null -eq $e.original){continue}
 $saved=Join-Path $backup $e.path
 New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $runtime $e.path) -Destination $saved
 if((Get-FileHash -LiteralPath $saved).Hash -ne $e.original){throw 'Backup hash mismatch.'}
}
Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
try {
 foreach($e in $manifest.files){
  Copy-Item -LiteralPath (Join-Path $prepared $e.path) -Destination (Join-Path $runtime $e.path)
  if((Get-FileHash -LiteralPath (Join-Path $runtime $e.path)).Hash -ne $e.installed){throw 'Deployment hash mismatch.'}
 }
} catch {
 foreach($e in $manifest.files){
  $target=Join-Path $runtime $e.path
  if($null -eq $e.original){if(Test-Path -LiteralPath $target){Remove-Item -LiteralPath $target}}
  else{Copy-Item -LiteralPath (Join-Path $backup $e.path) -Destination $target}
 }
 throw
}
Write-Output "Server deployment verified; backup: $backup"
