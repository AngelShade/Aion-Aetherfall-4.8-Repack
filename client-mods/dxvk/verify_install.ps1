$ErrorActionPreference = 'Stop'
$testRoot = Join-Path $PSScriptRoot ('test-workspace/' + [guid]::NewGuid().ToString('N'))
$clientRoot = Join-Path $testRoot 'client'
New-Item -ItemType Directory -Path $clientRoot -Force | Out-Null
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
foreach ($patch in $manifest.compatibilityPatches) {
    $target = Join-Path $clientRoot $patch.path
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path (Join-Path $env:USERPROFILE 'Downloads\aion-4.8-na\Aion 4.8 NA') $patch.path) -Destination $target
}
$latin1 = [Text.Encoding]::GetEncoding(28591)
function Encode([string]$Line) { return $latin1.GetString([byte[]]@($latin1.GetBytes($Line) | ForEach-Object { $_ -bxor 255 })) }
$nativeBefore = '-- [System-Configuration Ver1.0]' + "`r`n" +
    (Encode 'g_freefly = "1"') + "`r`n" + (Encode 's_MasterVolume = "0.13"') + "`r`n" +
    (Encode 'g_cfg_video_MRT_SHADOW = "2"') + "`r`n"
[IO.File]::WriteAllBytes((Join-Path $clientRoot 'system.cfg'),$latin1.GetBytes($nativeBefore))
$graphicsBefore = "[AION]`r`nMG_SHADOW = `"2`"`r`nMG_GENERAL = `"-2`"`r`nUSE_MG = `"1`"`r`nFULLSCREEN_WIDTH = `"1920`"`r`n"
[IO.File]::WriteAllText((Join-Path $clientRoot 'SystemOptionGraphics.cfg'),$graphicsBefore,[Text.Encoding]::ASCII)
$launcherBefore = "@echo off`r`npowershell.exe -File `"%~dp0Enable-FreeFlight.ps1`"`r`nrem existing visual pack`r`nstart `"`" `"bin64\aion.bin`" -ip:127.0.0.1 -port:2106 -loginex`r`n"
[IO.File]::WriteAllText((Join-Path $clientRoot 'Aion Start.bat'),$launcherBefore,[Text.Encoding]::ASCII)
$global:DXVKTestAionRunning = $true
function Get-Process { param($Name,$ErrorAction) if ($global:DXVKTestAionRunning) { [pscustomobject]@{Name='aion.bin'} } }
function Assert([bool]$Condition,[string]$Message) { if (-not $Condition) { throw $Message } }
& (Join-Path $PSScriptRoot 'Install.ps1') -ClientPath $clientRoot
$installedLauncher = [IO.File]::ReadAllText((Join-Path $clientRoot 'Aion Start.bat'))
Assert ($installedLauncher.IndexOf('set "PSModulePath=') -lt $installedLauncher.IndexOf('Enable-FreeFlight.ps1')) 'PowerShell module path must be set before any launcher scripts'
Assert ($latin1.GetString([IO.File]::ReadAllBytes((Join-Path $clientRoot 'system.cfg'))) -ceq $nativeBefore) 'Running client settings changed'
$rejected = $false
try { & (Join-Path $clientRoot 'DXVK/QualityProfile.ps1') -ClientPath $clientRoot } catch { $rejected = $true }
Assert $rejected 'Running client settings guard failed'
$global:DXVKTestAionRunning = $false
& (Join-Path $clientRoot 'DXVK/QualityProfile.ps1') -ClientPath $clientRoot
$nativeAfter = $latin1.GetString([IO.File]::ReadAllBytes((Join-Path $clientRoot 'system.cfg')))
Assert ($nativeAfter.Contains((Encode 'g_freefly = "1"'))) 'Flight option lost'
Assert ($nativeAfter.Contains((Encode 's_MasterVolume = "0.13"'))) 'Volume changed'
Assert ($nativeAfter.Contains((Encode 'r_mrt_ssao = "1"'))) 'AO was not enabled'
Assert ($nativeAfter.Contains((Encode 'r_mrt_sharpen = "0.12"'))) 'Sharpening value incorrect'
Assert ($nativeAfter.Contains((Encode 'r_3dvision_cursor = "1"'))) 'UI cursor rendering was not enabled'
Assert ($nativeAfter.Contains((Encode 'g_cfg_video_MRT_SHADOW = "4"'))) 'Shadow setting incorrect'
& (Join-Path $clientRoot 'DXVK/QualityProfile.ps1') -ClientPath $clientRoot
Assert ($nativeAfter -ceq $latin1.GetString([IO.File]::ReadAllBytes((Join-Path $clientRoot 'system.cfg')))) 'Quality profile is not idempotent'
# Later sound and launcher changes must survive restoration.
$nativeAfter = $nativeAfter.Replace((Encode 's_MasterVolume = "0.13"'),(Encode 's_MasterVolume = "0.91"'))
[IO.File]::WriteAllBytes((Join-Path $clientRoot 'system.cfg'),$latin1.GetBytes($nativeAfter))
[IO.File]::AppendAllText((Join-Path $clientRoot 'Aion Start.bat'),"rem later unrelated edit`r`n")
& (Join-Path $clientRoot 'DXVK/Restore.ps1') -ClientPath $clientRoot
$expected = $nativeBefore.Replace((Encode 's_MasterVolume = "0.13"'),(Encode 's_MasterVolume = "0.91"'))
Assert ($latin1.GetString([IO.File]::ReadAllBytes((Join-Path $clientRoot 'system.cfg'))) -ceq $expected) 'Settings restore changed unrelated content'
Assert ([IO.File]::ReadAllText((Join-Path $clientRoot 'SystemOptionGraphics.cfg')) -ceq $graphicsBefore) 'Graphics settings restore incorrect'
Assert ([IO.File]::ReadAllText((Join-Path $clientRoot 'Aion Start.bat')) -ceq ($launcherBefore + "rem later unrelated edit`r`n")) 'Launcher restore changed unrelated content'
foreach ($entry in $manifest.files) { Assert (-not (Test-Path -LiteralPath (Join-Path $clientRoot $entry.path))) 'Added renderer file survived restore' }
'PASS: complete dual-client installation, running-session settings guard, encoded profile, idempotence and restore with later edits preserved.'
