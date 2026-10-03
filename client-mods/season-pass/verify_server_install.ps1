param([string]$BundlePath='output/season-pass/server-v9')
$ErrorActionPreference='Stop'
$source=(Resolve-Path -LiteralPath $BundlePath).Path
$original=Get-Content -Raw -LiteralPath (Join-Path $source 'manifest.json') | ConvertFrom-Json
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$root=Join-Path $workspace ('output/season-pass/server-install-check-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $root | Out-Null
function Assert-Fixture([string]$Path) {
 $resolved=[IO.Path]::GetFullPath($Path)
 if(-not $resolved.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Test path outside disposable fixture.'}
}
function New-Fixture([string]$Name) {
 $prepared=Join-Path $root ($Name+'/prepared');$deployment=Join-Path $root ($Name+'/deployment')
 foreach($dir in @($prepared,$deployment)){Assert-Fixture $dir;New-Item -ItemType Directory -Path $dir -Force | Out-Null}
 foreach($entry in $original.files){
  $staged=Join-Path $prepared $entry.path;Assert-Fixture $staged
  New-Item -ItemType Directory -Path (Split-Path -Parent $staged) -Force | Out-Null
  Microsoft.PowerShell.Management\Copy-Item -LiteralPath (Join-Path $source $entry.path) -Destination $staged
  if($null -ne $entry.original){
   $target=Join-Path $deployment $entry.path;Assert-Fixture $target
   New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
   Microsoft.PowerShell.Management\Copy-Item -LiteralPath (Join-Path $original.deployment $entry.path) -Destination $target
  }
 }
 foreach($entry in $original.dependencies){
  $target=Join-Path $deployment $entry.path;Assert-Fixture $target
  New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
  Microsoft.PowerShell.Management\Copy-Item -LiteralPath (Join-Path $original.deployment $entry.path) -Destination $target
 }
 $manifest=$original | ConvertTo-Json -Depth 10 | ConvertFrom-Json
 $manifest.deployment=$deployment
 $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $prepared 'manifest.json') -Encoding UTF8
 return @{Prepared=$prepared;Deployment=$deployment}
}
# The port guard is replaced only in this isolated fixture scope; no server is stopped.
function netstat { }
$success=New-Fixture 'success'
& (Join-Path $PSScriptRoot 'install_server.ps1') -PreparedPath $success.Prepared
foreach($entry in $original.files){if((Get-FileHash -LiteralPath (Join-Path $success.Deployment $entry.path)).Hash -ne $entry.sha256){throw 'Installed fixture hash mismatch.'}}
$backup=Get-ChildItem -LiteralPath (Join-Path $success.Deployment 'backups') -Directory | Select-Object -First 1
foreach($entry in $original.files){if($null -ne $entry.original -and (Get-FileHash -LiteralPath (Join-Path $backup.FullName $entry.path)).Hash -ne $entry.original){throw 'Backup fixture hash mismatch.'}}
$dependency=Join-Path $success.Deployment $original.dependencies[0].path
Add-Content -LiteralPath $dependency -Value ''
$rejected=$false
try{& (Join-Path $PSScriptRoot 'install_server.ps1') -PreparedPath $success.Prepared}catch{if($_.Exception.Message -notlike 'Native reward data changed*'){throw};$rejected=$true}
if(-not $rejected){throw 'Changed dependency was accepted.'}
Microsoft.PowerShell.Management\Copy-Item -LiteralPath (Join-Path $original.deployment $original.dependencies[0].path) -Destination $dependency
$rejected=$false
try{& (Join-Path $PSScriptRoot 'install_server.ps1') -PreparedPath $success.Prepared}catch{if($_.Exception.Message -notin @('Later server configuration exists.','Deployed server changed since staging.')){throw};$rejected=$true}
if(-not $rejected){throw 'Later installation was overwritten.'}
$failure=New-Fixture 'failure'
$global:SeasonPassFixtureFailureTarget=[IO.Path]::GetFullPath((Join-Path $failure.Deployment 'libs/game-server-4.8-SNAPSHOT.jar'))
$global:SeasonPassFixtureInjected=$false
function Copy-Item {
 param([string]$LiteralPath,[string]$Destination)
 Assert-Fixture $Destination
 if(-not $global:SeasonPassFixtureInjected -and [IO.Path]::GetFullPath($Destination) -eq $global:SeasonPassFixtureFailureTarget){$global:SeasonPassFixtureInjected=$true;throw 'Intentional fixture copy failure.'}
 Microsoft.PowerShell.Management\Copy-Item -LiteralPath $LiteralPath -Destination $Destination
}
$failed=$false
try{& (Join-Path $PSScriptRoot 'install_server.ps1') -PreparedPath $failure.Prepared}catch{if($_.Exception.Message -ne 'Intentional fixture copy failure.'){throw};$failed=$true}
if(-not $failed){throw 'Copy failure was not exercised.'}
foreach($entry in $original.files){
 $target=Join-Path $failure.Deployment $entry.path;Assert-Fixture $target
 if($null -eq $entry.original){if(Test-Path -LiteralPath $target){throw 'Failed installation left a new file.'}}
 elseif((Get-FileHash -LiteralPath $target).Hash -ne $entry.original){throw 'Rollback did not restore original fixture.'}
}
Write-Output 'OK: server install, original-data backup, dependency guard, later-file guard and failure rollback verified on disposable copies.'
