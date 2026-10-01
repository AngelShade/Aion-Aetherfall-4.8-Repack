$ErrorActionPreference = 'Stop'
$source = (Join-Path $env:USERPROFILE 'Downloads\aion-4.8-na\Aion 4.8 NA')
$sourceMenu = Get-Content -LiteralPath (Join-Path $source 'DXVK/graphics-menu/installed.json') -Raw | ConvertFrom-Json
$sourceQueue = Get-Content -LiteralPath (Join-Path $source 'DXVK/graphics-menu/queued.json') -Raw | ConvertFrom-Json
$fixture = Join-Path $PSScriptRoot ('test-workspace/menu-' + [guid]::NewGuid().ToString('N'))
foreach ($path in @('bin64/Game.dll','bin64/XRenderD3D9.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak','bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf','system.cfg','SystemOptionGraphics.cfg','Aion Start.bat','DXVK/installed.json')) {
    $target = Join-Path $fixture $path
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    $from = Join-Path $source $path
    if ($path -in @('bin64/Game.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak')) { $from = Join-Path $sourceMenu.backupRoot $path }
    if ($path -eq 'Aion Start.bat') { $from = Join-Path $sourceQueue.launcherBackup 'Aion Start.bat' }
    Copy-Item -LiteralPath $from -Destination $target
}
New-Item -ItemType Directory -Path (Join-Path $fixture 'DXVK/cursor-fix') -Force | Out-Null
$record = Get-Content -LiteralPath (Join-Path $fixture 'DXVK/installed.json') -Raw | ConvertFrom-Json
$record.clientRoot = $fixture
($record.nativeCursorPatch.files | Where-Object {$_.path -eq 'bin64/Game.dll'}).installed = (Get-FileHash -LiteralPath (Join-Path $fixture 'bin64/Game.dll')).Hash
$record | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath (Join-Path $fixture 'DXVK/installed.json') -Encoding UTF8
$global:MenuTestRunning = $true
function Get-Process { param($Name,$ErrorAction) if ($global:MenuTestRunning) { [pscustomobject]@{Name='aion.bin'} } }
function Assert([bool]$Value,[string]$Message) { if (-not $Value) { throw $Message } }
function Reject([scriptblock]$Action,[string]$Message) { $failed=$false;try { & $Action } catch { $failed=$true };Assert $failed $Message }
$gameBefore = (Get-FileHash -LiteralPath (Join-Path $fixture 'bin64/Game.dll')).Hash
$uiBefore = (Get-FileHash -LiteralPath (Join-Path $fixture 'Data/ui/game/game.pak')).Hash
& (Join-Path $PSScriptRoot 'InstallGraphicsMenu.ps1') -ClientPath $fixture
Assert ((Get-FileHash -LiteralPath (Join-Path $fixture 'bin64/Game.dll')).Hash -eq $gameBefore) 'Queue changed running Game.dll'
Reject { & (Join-Path $fixture 'DXVK/graphics-menu/ApplyGraphicsMenu.ps1') -ClientPath $fixture } 'Running menu apply was allowed'
Reject { & (Join-Path $fixture 'DXVK/SelectRenderer.ps1') -ClientPath $fixture } 'Running renderer switch was allowed'
$global:MenuTestRunning = $false
& (Join-Path $fixture 'DXVK/graphics-menu/ApplyGraphicsMenu.ps1') -ClientPath $fixture
& (Join-Path $fixture 'DXVK/graphics-menu/ApplyGraphicsMenu.ps1') -ClientPath $fixture
$launcher = [IO.File]::ReadAllText((Join-Path $fixture 'Aion Start.bat'))
Assert ($launcher.IndexOf('SelectRenderer.ps1') -lt $launcher.IndexOf('InstallNativeCursorPatch.ps1')) 'Renderer selection must happen before cursor validation'
Assert ($launcher.IndexOf('ApplyGraphicsMenu.ps1') -lt $launcher.IndexOf('SelectRenderer.ps1')) 'Menu tracking must update before renderer validation'
Assert ($launcher.Contains('if not "%AION_USE_VULKAN%"=="0" (')) 'Post effects must be conditional on renderer'
& (Join-Path $fixture 'DXVK/SelectRenderer.ps1') -ClientPath $fixture
[IO.File]::WriteAllText((Join-Path $fixture 'DXVK/renderer.ini'),"[Renderer]`r`nVulkan=0`r`nActiveVulkan=1`r`n")
& (Join-Path $fixture 'DXVK/SelectRenderer.ps1') -ClientPath $fixture
& (Join-Path $fixture 'DXVK/SelectRenderer.ps1') -ClientPath $fixture
foreach ($arch in @('32','64')) {
    Assert (-not (Test-Path -LiteralPath (Join-Path $fixture "bin$arch/d3d9.dll"))) 'Vulkan wrapper still active when disabled'
    Assert (Test-Path -LiteralPath (Join-Path $fixture "bin$arch/d3d9.dxvk-disabled.dll")) 'Parked wrapper missing'
}
& (Join-Path $fixture 'DXVK/cursor-fix/InstallNativeCursorPatch.ps1') -ClientPath $fixture
Assert ([IO.File]::ReadAllText((Join-Path $fixture 'DXVK/renderer-env.bat')).Contains('AION_USE_VULKAN=0')) 'Launcher did not disable Vulkan layer'
$parked = Join-Path $fixture 'bin32/d3d9.dxvk-disabled.dll'
$original = [IO.File]::ReadAllBytes($parked)
[IO.File]::WriteAllBytes($parked,[byte[]]($original + [byte]0))
[IO.File]::WriteAllText((Join-Path $fixture 'DXVK/renderer.ini'),"[Renderer]`r`nVulkan=1`r`nActiveVulkan=0`r`n")
Reject { & (Join-Path $fixture 'DXVK/SelectRenderer.ps1') -ClientPath $fixture } 'Tampered wrapper switch was allowed'
Assert (Test-Path -LiteralPath (Join-Path $fixture 'bin64/d3d9.dxvk-disabled.dll')) 'Rejected switch partly changed architecture'
[IO.File]::WriteAllBytes($parked,$original)
& (Join-Path $fixture 'DXVK/SelectRenderer.ps1') -ClientPath $fixture
foreach ($arch in @('32','64')) { Assert (Test-Path -LiteralPath (Join-Path $fixture "bin$arch/d3d9.dll")) 'Vulkan wrapper failed to return' }
$gamePath = Join-Path $fixture 'bin64/Game.dll'
$gamePatched = [IO.File]::ReadAllBytes($gamePath)
$uiPatched = (Get-FileHash -LiteralPath (Join-Path $fixture 'Data/ui/game/game.pak')).Hash
[IO.File]::WriteAllBytes($gamePath,[byte[]]($gamePatched + [byte]0))
Reject { & (Join-Path $fixture 'DXVK/graphics-menu/RestoreGraphicsMenu.ps1') -ClientPath $fixture } 'Tampered menu restore was allowed'
Assert ((Get-FileHash -LiteralPath (Join-Path $fixture 'Data/ui/game/game.pak')).Hash -eq $uiPatched) 'Rejected restore changed UI'
[IO.File]::WriteAllBytes($gamePath,$gamePatched)
[IO.File]::AppendAllText((Join-Path $fixture 'Aion Start.bat'),"rem later unrelated setting`r`n")
& (Join-Path $fixture 'DXVK/graphics-menu/RestoreGraphicsMenu.ps1') -ClientPath $fixture
Assert ((Get-FileHash -LiteralPath $gamePath).Hash -eq $gameBefore) 'Menu restore changed existing native mods/cursor'
Assert ((Get-FileHash -LiteralPath (Join-Path $fixture 'Data/ui/game/game.pak')).Hash -eq $uiBefore) 'UI restore did not restore exact archive'
$launcher = [IO.File]::ReadAllText((Join-Path $fixture 'Aion Start.bat'))
Assert ($launcher.Contains('rem later unrelated setting') -and $launcher.Contains('SelectRenderer.ps1')) 'Restore lost later settings or renderer selector'
'PASS menu queue/install/idempotence, running guards, both renderer switches, native cursor compatibility, tamper rejection, exact restore, later edits preserved.'
