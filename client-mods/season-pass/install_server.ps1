param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$m=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
if($m.feature -ne 'daeva-season-pass'){throw 'Wrong server bundle.'}
$deployment=(Resolve-Path -LiteralPath $m.deployment).Path
$ports=@(netstat -ano -p tcp | Select-String ':7777\s+.*LISTENING')
if($ports.Count -gt 0){throw 'Stop GameServer gracefully with zero online players before installing the server bundle.'}
foreach($e in $m.dependencies){
 if($e.path -notmatch '^data/static_data/(items/item_templates|decomposable_items/decomposable_items|pets/pets)\.xml$'){throw 'Unexpected reward data dependency.'}
 if((Get-FileHash -LiteralPath (Join-Path $deployment $e.path)).Hash -ne $e.sha256){throw 'Native reward data changed since catalog verification. Revalidate and stage a fresh bundle.'}
}
foreach($e in $m.files){
 if($e.path -notmatch '^(libs/game-server-4\.8-SNAPSHOT\.jar|data/static_data/decomposable_items/decomposable_items\.xml|config/season-pass/(season\.properties|missions\.tsv|rewards\.tsv|schema\.sql|media/(pass\.(html|css|js)|(ascendant-dawn|aether-frame|aether-crest|ascendant-crest|pass-panel)\.png)))$'){throw 'Unexpected server bundle path.'}
 $target=[IO.Path]::GetFullPath((Join-Path $deployment $e.path))
 if(-not $target.StartsWith($deployment+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target outside deployment.'}
 if((Get-FileHash -LiteralPath (Join-Path $prepared $e.path)).Hash -ne $e.sha256){throw 'Server bundle changed.'}
 if($null -eq $e.original){if(Test-Path -LiteralPath $target){throw 'Later server configuration exists.'}}
 elseif((Get-FileHash -LiteralPath $target).Hash -ne $e.original){throw 'Deployed server changed since staging.'}
}
$backup=Join-Path $deployment ('backups/season-pass-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach($e in $m.files){if($null -ne $e.original){
 $saved=Join-Path $backup $e.path;New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $deployment $e.path) -Destination $saved
 if((Get-FileHash -LiteralPath $saved).Hash -ne $e.original){throw 'Backup verification failed.'}
}}
Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
try{
 foreach($e in $m.files){
  $target=Join-Path $deployment $e.path;New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $prepared $e.path) -Destination $target
  if((Get-FileHash -LiteralPath $target).Hash -ne $e.sha256){throw 'Server installation verification failed.'}
 }
}catch{
 foreach($e in $m.files){$target=Join-Path $deployment $e.path
  if($null -ne $e.original){Copy-Item -LiteralPath (Join-Path $backup $e.path) -Destination $target}
  elseif(Test-Path -LiteralPath $target){Remove-Item -LiteralPath $target}
 }
 throw
}
Write-Output "OK: server bundle installed. Backup: $backup. Start GameServer normally to initialize Season Pass."
