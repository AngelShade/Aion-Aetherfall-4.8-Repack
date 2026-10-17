param([Parameter(Mandatory=$true)][string]$PreparedPath)
$ErrorActionPreference='Stop'
$prepared=(Resolve-Path -LiteralPath $PreparedPath).Path
$manifest=Get-Content -Raw -LiteralPath (Join-Path $prepared 'manifest.json') | ConvertFrom-Json
if($manifest.feature -ne 'companion-journey-hud'){throw 'Wrong shortcut package.'}
$client=(Resolve-Path -LiteralPath $manifest.clientRoot).Path
$archiveRoot='D:\Proiecte\Project Restructure\Aion Development Workspace\archives\client'
foreach($process in @(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^(aion|aionbin)\.(exe|bin)$'})){
 if(-not $process.ExecutablePath){throw 'Cannot verify Aion process path.'}
 if($process.ExecutablePath.StartsWith($client+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Close Aion normally before installing HUD shortcuts.'}
}
if((Get-FileHash -LiteralPath (Join-Path $client 'Pub.key')).Hash -ne $manifest.sourceKey){throw 'Stock model key changed.'}
$paths=@{}
foreach($e in $manifest.files){
 if($e.path -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$))' -or $paths.ContainsKey($e.path)){throw 'Invalid replacement path.'}
 $paths[$e.path]=$true
 $target=[IO.Path]::GetFullPath((Join-Path $client $e.path))
 if(-not $target.StartsWith($client+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Replacement outside client.'}
 if($e.path -notmatch '^(bin64/AionMarketShortcut\.dll|Addon\.key|bin32/bin32\.pak\.sig|Data/func_pet/func_pet\.pak\.sig|Plugin/RelicCalc/RelicCalc\.pak(\.sig)?|Data/ui/ui\.pak|Textures/ui/ui\.pak|L10N/enu/Data/data\.pak|Data/ui/game_hud_s[12]/game_hud_s[12]\.pak|Mods/GraphicsMenu/(installed\.json|package/(manifest\.json|L10N/enu/Data/data\.pak))|Mods/Backups/Renderer/service-menu-graphics-\d{8}-\d{6}-\d{6}/L10N/enu/Data/data\.pak)$'){throw "Unexpected replacement: $($e.path)"}
 if((Get-FileHash -LiteralPath (Join-Path $prepared $e.path)).Hash -ne $e.installed){throw 'Prepared file changed.'}
 if($null -eq $e.original){if(Test-Path -LiteralPath $target){throw 'Added file already exists.'}}
 elseif((Get-FileHash -LiteralPath $target).Hash -ne $e.original){throw "Client changed: $($e.path)"}
}
foreach($e in $manifest.preservedFiles){if((Get-FileHash -LiteralPath (Join-Path $client $e.path)).Hash -ne $e.sha256){throw "Preserved file changed: $($e.path)"}}
foreach($e in $manifest.sourceHashes.PSObject.Properties){if((Get-FileHash -LiteralPath $e.Name).Hash -ne $e.Value){throw 'Maintained source changed after verification.'}}
python (Join-Path $PSScriptRoot 'verify_package.py') $prepared
if($LASTEXITCODE -ne 0){throw 'Complete package verification failed.'}
$backup=Join-Path $archiveRoot ('service-shortcuts-'+(Get-Date -Format 'yyyyMMdd-HHmmss-ffffff'))
if(-not [IO.Path]::GetFullPath($backup).StartsWith([IO.Path]::GetFullPath($archiveRoot)+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected backup directory.'}
New-Item -ItemType Directory -Path $backup | Out-Null
foreach($e in $manifest.files){
 if($null -eq $e.original){continue}
 $saved=Join-Path $backup $e.path
 New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $client $e.path) -Destination $saved
 if((Get-FileHash -LiteralPath $saved).Hash -ne $e.original){throw 'Backup verification failed.'}
}
Copy-Item -LiteralPath (Join-Path $prepared 'manifest.json') -Destination (Join-Path $backup 'manifest.json')
try{
 foreach($e in $manifest.files){
  $target=Join-Path $client $e.path
  Copy-Item -LiteralPath (Join-Path $prepared $e.path) -Destination $target
  if((Get-FileHash -LiteralPath $target).Hash -ne $e.installed){throw 'Installed file verification failed.'}
 }
 foreach($e in $manifest.preservedFiles){if((Get-FileHash -LiteralPath (Join-Path $client $e.path)).Hash -ne $e.sha256){throw 'Preserved mod changed during installation.'}}
 python (Join-Path $PSScriptRoot 'verify_package.py') $prepared --installed
 if($LASTEXITCODE -ne 0){throw 'Independent installed package verification failed.'}
 @{installedAt=(Get-Date -Format o);package=$prepared;feature=$manifest.feature} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'installed.json')
}catch{
 foreach($e in $manifest.files){
  $target=Join-Path $client $e.path
  if($null -eq $e.original){Remove-Item -LiteralPath $target -ErrorAction SilentlyContinue}
  else{Copy-Item -LiteralPath (Join-Path $backup $e.path) -Destination $target}
 }
 throw
}
Write-Output "OK: Companion/Journey native HUD shortcuts installed; verified recovery receipt: $backup"
