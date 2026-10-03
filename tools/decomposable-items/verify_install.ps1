param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$source=(Resolve-Path -LiteralPath $PreparedPath).Path
$original=Get-Content -Raw -LiteralPath (Join-Path $source 'manifest.json') | ConvertFrom-Json
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$root=Join-Path $workspace ('output/pr211-review/install-check-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $root | Out-Null
function Assert-Fixture([string]$Path){
 if(-not [IO.Path]::GetFullPath($Path).StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Outside disposable fixture.'}
}
function New-Fixture([string]$Name){
 $prepared=Join-Path $root ($Name+'/prepared');$deployment=Join-Path $root ($Name+'/deployment')
 foreach($folder in @($prepared,$deployment)){Assert-Fixture $folder;New-Item -ItemType Directory -Path $folder -Force | Out-Null}
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
# Only disposable fixtures bypass the live-process guard; no process is stopped.
function Get-CimInstance { }
$success=New-Fixture 'success'
& (Join-Path $PSScriptRoot 'install.ps1') -PreparedPath $success.Prepared
foreach($entry in $original.files){if((Get-FileHash -LiteralPath (Join-Path $success.Deployment $entry.path)).Hash -ne $entry.sha256){throw 'Fixture installation mismatch.'}}
$backup=Get-ChildItem -LiteralPath (Join-Path $success.Deployment 'backups') -Directory | Select-Object -First 1
foreach($entry in $original.files){if($null -ne $entry.original -and (Get-FileHash -LiteralPath (Join-Path $backup.FullName $entry.path)).Hash -ne $entry.original){throw 'Fixture backup mismatch.'}}
$rejected=$false
try{& (Join-Path $PSScriptRoot 'install.ps1') -PreparedPath $success.Prepared}catch{if($_.Exception.Message -notmatch 'Deployment changed|later deployed file'){throw};$rejected=$true}
if(-not $rejected){throw 'Later installation was overwritten.'}
$failure=New-Fixture 'failure'
$global:DecomposableFixtureFailure=[IO.Path]::GetFullPath((Join-Path $failure.Deployment 'data/static_data/decomposable_items/local_decomposable_items.xml'))
$global:DecomposableFailureInjected=$false
function Copy-Item {
 param([string]$LiteralPath,[string]$Destination)
 Assert-Fixture $Destination
 if(-not $global:DecomposableFailureInjected -and [IO.Path]::GetFullPath($Destination) -eq $global:DecomposableFixtureFailure){
  $global:DecomposableFailureInjected=$true;throw 'Intentional fixture failure.'
 }
 Microsoft.PowerShell.Management\Copy-Item -LiteralPath $LiteralPath -Destination $Destination
}
$failed=$false
try{& (Join-Path $PSScriptRoot 'install.ps1') -PreparedPath $failure.Prepared}catch{if($_.Exception.Message -ne 'Intentional fixture failure.'){throw};$failed=$true}
if(-not $failed){throw 'Rollback was not exercised.'}
foreach($entry in $original.files){
 $target=Join-Path $failure.Deployment $entry.path;Assert-Fixture $target
 if($null -eq $entry.original){if(Test-Path -LiteralPath $target){throw 'Rollback left a new file.'}}
 elseif((Get-FileHash -LiteralPath $target).Hash -ne $entry.original){throw 'Rollback failed to restore original.'}
}
Write-Output 'OK: installation, backups, later-file guard and mid-install failure rollback passed on disposable copies.'
