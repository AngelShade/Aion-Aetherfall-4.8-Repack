$ErrorActionPreference='Stop'
$source=(Join-Path $env:USERPROFILE 'Downloads\aion-4.8-na\Aion 4.8 NA')
$fixture=Join-Path $PSScriptRoot ('test-workspace/localized-' + [guid]::NewGuid().ToString('N'))
$root=Join-Path $fixture 'DXVK/graphics-menu'
New-Item -ItemType Directory -Path $root -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'build/graphics-menu') -Destination (Join-Path $root 'package') -Recurse
$state=Get-Content -LiteralPath (Join-Path $source 'DXVK/graphics-menu/installed.json') -Raw | ConvertFrom-Json
$sourceBackup=$state.backupRoot
$state.clientRoot=$fixture
$state.backupRoot=Join-Path $fixture 'DXVK-backups/menu-original'
foreach ($entry in $state.files) {
    $target=Join-Path $fixture $entry.path
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $source $entry.path) -Destination $target
    if ($null -ne $entry.original) {
        $saved=Join-Path $state.backupRoot $entry.path
        New-Item -ItemType Directory -Path (Split-Path $saved -Parent) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $sourceBackup $entry.path) -Destination $saved
    }
}
$localized=Join-Path $fixture 'L10N/enu/Data/data.pak'
New-Item -ItemType Directory -Path (Split-Path $localized -Parent) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'L10N/enu/Data/data.pak') -Destination $localized
$state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $root 'installed.json') -Encoding UTF8
$dxvk=Get-Content -LiteralPath (Join-Path $source 'DXVK/installed.json') -Raw | ConvertFrom-Json
$dxvk.clientRoot=$fixture
$dxvk | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath (Join-Path $fixture 'DXVK/installed.json') -Encoding UTF8
[IO.File]::WriteAllText((Join-Path $fixture 'Aion Start.bat'),'rem later edit')
$global:LocalizedTestRunning=$true
function Get-Process { param($Name,$ErrorAction) if ($global:LocalizedTestRunning) { [pscustomobject]@{Name='aion.bin'} } }
function Assert([bool]$Value,[string]$Message){if(-not $Value){throw $Message}}
& (Join-Path $PSScriptRoot 'ApplyGraphicsMenu.ps1') -ClientPath $fixture -VerifyOnly
$failed=$false;try{& (Join-Path $PSScriptRoot 'ApplyGraphicsMenu.ps1') -ClientPath $fixture}catch{$failed=$true}
Assert $failed 'Running localization replacement was allowed'
$gameHash=(Get-FileHash -LiteralPath (Join-Path $fixture 'bin64/Game.dll')).Hash
$global:LocalizedTestRunning=$false
& (Join-Path $PSScriptRoot 'ApplyGraphicsMenu.ps1') -ClientPath $fixture
& (Join-Path $PSScriptRoot 'ApplyGraphicsMenu.ps1') -ClientPath $fixture
$installed=Get-Content -LiteralPath (Join-Path $root 'installed.json') -Raw | ConvertFrom-Json
Assert ($installed.files.Count -eq 4) 'Localized file missing from restore record'
Assert ((Get-FileHash -LiteralPath (Join-Path $fixture 'bin64/Game.dll')).Hash -eq $gameHash) 'Localized revision changed native cursor/other hooks'
$entry=$installed.files | Where-Object {$_.path -eq 'L10N/enu/Data/data.pak'}
Assert ((Get-FileHash -LiteralPath $localized).Hash -eq $entry.installed) 'Localized archive not installed'
& (Join-Path $PSScriptRoot 'RestoreGraphicsMenu.ps1') -ClientPath $fixture
Assert ((Get-FileHash -LiteralPath $localized).Hash -eq $entry.original) 'Localized archive restore did not preserve exact earlier content'
Assert ([IO.File]::ReadAllText((Join-Path $fixture 'Aion Start.bat')) -eq 'rem later edit') 'Later launcher edit lost'
'PASS incremental English layout install, running guard, idempotence, unchanged native patch, exact archive restoration.'
