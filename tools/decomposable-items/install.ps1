param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
if($manifest.feature -ne 'decomposable-pr200-backport'){throw 'Wrong decomposable bundle.'}
$deployment=(Resolve-Path -LiteralPath $manifest.deployment).Path
if(@(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -match 'com\.aionemu\.gameserver\.GameServer' }).Count -gt 0){
 throw 'Stop GameServer gracefully before replacing its code and data together.'
}
function Assert-Target([string]$Relative) {
 if($Relative -notmatch '^(libs/game-server-4\.8-SNAPSHOT\.jar|data/static_data/static_data\.xml|data/static_data/items/item_templates\.xml|data/static_data/decomposable_items/(decomposable_items\.xsd|decomposable_items\.xml|custom_decomposable_items\.xml|local_decomposable_items\.xml)|data/handlers/(admincommands/Reload|playercommands/Decompose)\.java)$'){
  throw 'Unexpected decomposable bundle path.'
 }
 $absolute=[IO.Path]::GetFullPath((Join-Path $deployment $Relative))
 if(-not $absolute.StartsWith($deployment+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target outside deployment.'}
 return $absolute
}
foreach($dependency in $manifest.dependencies){
 if($dependency.path -ne 'data/static_data/items/item_templates.xsd'){throw 'Unexpected schema dependency.'}
 if((Get-FileHash -LiteralPath (Join-Path $deployment $dependency.path)).Hash -ne $dependency.sha256){throw 'Schema dependency changed; regenerate the bundle.'}
}
foreach($entry in $manifest.files){
 $target=Assert-Target $entry.path
 if((Get-FileHash -LiteralPath (Join-Path $prepared $entry.path)).Hash -ne $entry.sha256){throw 'Prepared bundle changed.'}
 if($null -eq $entry.original){
  if(Test-Path -LiteralPath $target){throw 'A later deployed file exists; regenerate the bundle.'}
 }elseif((Get-FileHash -LiteralPath $target).Hash -ne $entry.original){throw 'Deployment changed; regenerate the bundle.'}
}
$backup=Join-Path $deployment ('backups/decomposable-pr200-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach($entry in $manifest.files){if($null -ne $entry.original){
 $saved=Join-Path $backup $entry.path
 New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Assert-Target $entry.path) -Destination $saved
 if((Get-FileHash -LiteralPath $saved).Hash -ne $entry.original){throw 'Backup verification failed.'}
}}
Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
try{
 foreach($entry in $manifest.files){
  $target=Assert-Target $entry.path
  New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $prepared $entry.path) -Destination $target
  if((Get-FileHash -LiteralPath $target).Hash -ne $entry.sha256){throw 'Installed hash mismatch.'}
 }
}catch{
 foreach($entry in $manifest.files){
  $target=Assert-Target $entry.path
  if($null -ne $entry.original){Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination $target}
  elseif(Test-Path -LiteralPath $target){Remove-Item -LiteralPath $target}
 }
 throw
}
Write-Output "OK: decomposable code/data installed together. Backup: $backup. Start GameServer normally."
