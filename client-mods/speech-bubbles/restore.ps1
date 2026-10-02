param([Parameter(Mandatory=$true)][string]$BackupPath)
$ErrorActionPreference='Stop'
$backup=(Resolve-Path -LiteralPath $BackupPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $backup 'manifest.json') | ConvertFrom-Json
$clientRoot=(Resolve-Path -LiteralPath $manifest.clientRoot).Path
$expected=@('bin64/Game.dll','bin64/AionSpeechBubbles.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak')
. (Join-Path $PSScriptRoot 'GraphicsCompatibility.ps1')
if (Test-SpeechClientRunning $clientRoot) { throw 'Fully close Aion before restoring.' }
$expected+=@(Get-SpeechGraphicsPaths $manifest)
if($manifest.artworkArchives){
 $art=@('Data/ui/ui.pak','Textures/ui/ui.pak')
 if(Compare-Object ($manifest.artworkArchives|Sort-Object) ($art|Sort-Object)){throw 'Unexpected artwork archives.'}
 $expected+=$art
}
if (@($manifest.files).Count -ne $expected.Count -or (Compare-Object ($manifest.files.path | Sort-Object) ($expected | Sort-Object))) { throw 'Unexpected restore list.' }
foreach($entry in $manifest.files) {
 $target=Join-Path $clientRoot $entry.path
 if ((Get-FileHash -LiteralPath $target).Hash -ne $entry.installed) { throw "Later client changes detected: $($entry.path). Preserve them before restoring." }
 if ($null -ne $entry.original -and (Get-FileHash -LiteralPath (Join-Path $backup $entry.path)).Hash -ne $entry.original) { throw 'Backup hash mismatch.' }
}
foreach($entry in $manifest.files | Sort-Object { $_.path -ne 'bin64/Game.dll' }) {
 $target=Join-Path $clientRoot $entry.path
 if ($null -eq $entry.original) { Remove-Item -LiteralPath $target }
 else {
  Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination $target
  if((Get-FileHash -LiteralPath $target).Hash -ne $entry.original){throw 'Restored hash mismatch.'}
 }
}
Write-Output 'Restored the recorded client files.'
