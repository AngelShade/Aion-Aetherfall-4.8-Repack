# Exercise queue/apply/rollback on a synthetic client, never on the real client.
$ErrorActionPreference = 'Stop'
$testRoot = Join-Path $PSScriptRoot ('test-workspace/' + [guid]::NewGuid().ToString('N'))
$clientRoot = Join-Path $testRoot 'client'
$packageRoot = Join-Path $testRoot 'package'
New-Item -ItemType Directory -Path (Join-Path $clientRoot 'bin64') -Force | Out-Null
New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
[IO.File]::WriteAllBytes((Join-Path $clientRoot 'bin64/aion.bin'), [byte[]]@(0))
$originalLauncher = "@echo off`r`npowershell.exe -File `"%~dp0Enable-FreeFlight.ps1`"`r`nstart `"`" `"bin64\aion.bin`" -ip:127.0.0.1 -port:2106 -loginex`r`n"
[IO.File]::WriteAllText((Join-Path $clientRoot 'Aion Start.bat'), $originalLauncher, [Text.Encoding]::ASCII)
$paths = @('Levels/lf1/lf1.pak','Levels/lc1/lc1.pak','Levels/lf1/Level.pak',
    'Levels/lc1/Level.pak','Levels/common/Mesh_Textures_026.pak','effects/effects_Textures.pak')
$files = @()
foreach ($path in $paths) {
    $originalPath = Join-Path $clientRoot $path
    $stagedPath = Join-Path $packageRoot $path
    New-Item -ItemType Directory -Path (Split-Path $originalPath -Parent) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path $stagedPath -Parent) -Force | Out-Null
    [IO.File]::WriteAllText($originalPath, 'original ' + $path)
    [IO.File]::WriteAllText($stagedPath, 'replacement ' + $path)
    $files += @{path=$path; original=(Get-FileHash -LiteralPath $originalPath).Hash.ToLower();
        staged=(Get-FileHash -LiteralPath $stagedPath).Hash.ToLower()}
}
@{clientRoot=$clientRoot;packageId='0123456789abcdef';files=$files} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $packageRoot 'manifest.json')
$global:VisualPackTestAionRunning = $true
function Get-Process { param($Name,$ErrorAction) if ($global:VisualPackTestAionRunning) { [pscustomobject]@{Name='aion.bin'} } }
function Assert([bool]$Condition,[string]$Message) { if (-not $Condition) { throw $Message } }
& (Join-Path $PSScriptRoot 'Install.ps1') -ClientPath $clientRoot -PackagePath $packageRoot
$modRoot = Join-Path $clientRoot 'VisualOverhaul'
$stagedRoot = Join-Path $modRoot 'packages/0123456789abcdef'
foreach ($entry in $files) {
    Assert ((Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -eq $entry.original) 'Queue changed a running client archive'
}
$launcher = [IO.File]::ReadAllText((Join-Path $clientRoot 'Aion Start.bat'))
Assert ($launcher.Contains('Enable-FreeFlight.ps1')) 'Flight launcher integration was lost'
Assert ($launcher.IndexOf('BEGIN VISUAL OVERHAUL') -lt $launcher.IndexOf('start ""')) 'Apply block is after launch'
$rejected = $false
try { & (Join-Path $modRoot 'ApplyPackage.ps1') -ClientPath $clientRoot -PackagePath $stagedRoot } catch { $rejected = $true }
Assert $rejected 'Running-client apply was allowed'
$global:VisualPackTestAionRunning = $false
& (Join-Path $modRoot 'ApplyPackage.ps1') -ClientPath $clientRoot -PackagePath $stagedRoot
$stateBefore = [IO.File]::ReadAllText((Join-Path $modRoot 'installed.json'))
& (Join-Path $modRoot 'ApplyPackage.ps1') -ClientPath $clientRoot -PackagePath $stagedRoot
Assert ($stateBefore -ceq [IO.File]::ReadAllText((Join-Path $modRoot 'installed.json'))) 'Idempotent apply changed backup state'
# A later, unrelated launcher addition must survive restore.
[IO.File]::AppendAllText((Join-Path $clientRoot 'Aion Start.bat'), "rem later unrelated edit`r`n")
$first = Join-Path $clientRoot $files[0].path
[IO.File]::WriteAllText($first, 'later unrelated archive edit')
$rejected = $false
try { & (Join-Path $modRoot 'Restore.ps1') -ClientPath $clientRoot } catch { $rejected = $true }
Assert $rejected 'Restore overwrote a later archive edit'
foreach ($entry in $files | Select-Object -Skip 1) {
    Assert ((Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -eq $entry.staged) 'Failed restore partially changed archives'
}
Copy-Item -LiteralPath (Join-Path $stagedRoot $files[0].path) -Destination $first -Force
& (Join-Path $modRoot 'Restore.ps1') -ClientPath $clientRoot
foreach ($entry in $files) {
    Assert ((Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -eq $entry.original) 'Original archive not restored'
}
Assert ([IO.File]::ReadAllText((Join-Path $clientRoot 'Aion Start.bat')) -ceq ($originalLauncher + "rem later unrelated edit`r`n")) 'Restore changed unrelated launcher content'
Write-Output 'PASS: running-client queue/guard, backup, apply, idempotence, edit conflict protection, exact restore and launcher preservation.'
