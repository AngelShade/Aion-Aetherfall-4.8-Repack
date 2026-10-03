$ErrorActionPreference = 'Stop'
$fixture = Join-Path $PSScriptRoot ('test-workspace/multi-client-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixture 'DXVK') -Force | Out-Null
foreach ($name in @('SelectRenderer.ps1','RendererCommon.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $fixture ('DXVK/' + $name))
}
function Assert([bool]$Condition,[string]$Message) { if (-not $Condition) { throw $Message } }
function Reject([scriptblock]$Action,[string]$Message) {
    $failed = $false
    try { & $Action } catch { $failed = $true }
    Assert $failed $Message
}
$global:MultiClientRunning = $false
function Get-Process { param($Name,$ErrorAction) if ($global:MultiClientRunning) { [pscustomobject]@{Name='aion.bin'} } }
$files = @()
foreach ($path in @('bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf','bin64/Game.dll','bin64/XRenderD3D9.dll')) {
    $target = Join-Path $fixture $path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    [IO.File]::WriteAllText($target,$path)
    $files += @{path=$path;installed=(Get-FileHash -LiteralPath $target).Hash}
}
$statePath = Join-Path $fixture 'DXVK/installed.json'
$state = @{clientRoot=$fixture;files=@($files | Where-Object {$_.path -in @('bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf')});nativeCursorPatch=@{files=@($files | Where-Object {$_.path -in @('bin64/Game.dll','bin64/XRenderD3D9.dll')})}}
$state | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $statePath
$selector = Join-Path $fixture 'DXVK/SelectRenderer.ps1'
& $selector -ClientPath $fixture
$global:MultiClientRunning = $true
$before = Get-ChildItem -LiteralPath $fixture -Recurse -File | ForEach-Object { "$($_.FullName)|$((Get-FileHash -LiteralPath $_.FullName).Hash)|$($_.LastWriteTimeUtc.Ticks)" }
& $selector -ClientPath $fixture
& (Join-Path $PSScriptRoot 'InstallNativeCursorPatch.ps1') -ClientPath $fixture
$after = Get-ChildItem -LiteralPath $fixture -Recurse -File | ForEach-Object { "$($_.FullName)|$((Get-FileHash -LiteralPath $_.FullName).Hash)|$($_.LastWriteTimeUtc.Ticks)" }
Assert (($before -join "`n") -ceq ($after -join "`n")) 'Additional launch rewrote installed renderer/cursor files'
$ini = Join-Path $fixture 'DXVK/renderer.ini'
[IO.File]::WriteAllText($ini,"[Renderer]`r`nVulkan=0`r`nActiveVulkan=1`r`n")
Reject { & $selector -ClientPath $fixture } 'Running renderer switch was allowed'
Assert (Test-Path -LiteralPath (Join-Path $fixture 'bin64/d3d9.dll')) 'Rejected switch changed DLL placement'
$global:MultiClientRunning = $false
& $selector -ClientPath $fixture
$global:MultiClientRunning = $true
& $selector -ClientPath $fixture
Assert (Test-Path -LiteralPath (Join-Path $fixture 'bin64/d3d9.dxvk-disabled.dll')) 'Direct3D additional launch reenabled Vulkan'
[IO.File]::AppendAllText((Join-Path $fixture 'bin32/d3d9.dxvk-disabled.dll'),'tampered')
Reject { & $selector -ClientPath $fixture } 'Running launch accepted a tampered renderer'
$state.Remove('nativeCursorPatch')
$state | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $statePath
# Restore the renderer fixture so the missing cursor guard is tested independently.
[IO.File]::WriteAllText((Join-Path $fixture 'bin32/d3d9.dxvk-disabled.dll'),'bin32/d3d9.dll')
Reject { & (Join-Path $PSScriptRoot 'InstallNativeCursorPatch.ps1') -ClientPath $fixture } 'Running client allowed a new cursor patch'
$latin1 = [Text.Encoding]::GetEncoding(28591)
function Encode([string]$Line) { $latin1.GetString([byte[]]@($latin1.GetBytes($Line) | ForEach-Object { $_ -bxor 255 })) }
$config = Join-Path $fixture 'system.cfg'
$enabledFlight = '-- [System-Configuration Ver1.0]' + "`r`n" + (Encode 'g_freefly = "1"') + "`r`n"
[IO.File]::WriteAllBytes($config,$latin1.GetBytes($enabledFlight))
$flight = Join-Path $PSScriptRoot '../free-flight/Enable-FreeFlight.ps1'
$configTime = (Get-Item -LiteralPath $config).LastWriteTimeUtc.Ticks
& $flight -ClientPath $fixture
Assert ((Get-Item -LiteralPath $config).LastWriteTimeUtc.Ticks -eq $configTime) 'Running free flight verification rewrote config'
& (Join-Path $PSScriptRoot 'QualityProfile.ps1') -ClientPath $fixture -Startup
Assert ((Get-Item -LiteralPath $config).LastWriteTimeUtc.Ticks -eq $configTime) 'Additional launch changed graphics settings'
Reject { & (Join-Path $PSScriptRoot 'QualityProfile.ps1') -ClientPath $fixture } 'Explicit graphics apply accepted a running client'
Reject { & (Join-Path $PSScriptRoot 'QualityProfile.ps1') -ClientPath $fixture -Startup -Restore } 'Startup flag bypassed restore protection'
[IO.File]::WriteAllBytes($config,$latin1.GetBytes($enabledFlight.Replace((Encode 'g_freefly = "1"'),(Encode 'g_freefly = "0"'))))
$flightBefore = (Get-FileHash -LiteralPath $config).Hash
Reject { & $flight -ClientPath $fixture } 'Running client allowed flight config mutation'
Assert ((Get-FileHash -LiteralPath $config).Hash -eq $flightBefore) 'Rejected flight change wrote config'
$overhaul = Join-Path $fixture 'GraphicsOverhaul'
$package = Join-Path $overhaul 'package'
New-Item -ItemType Directory -Path (Join-Path $package 'Levels/LF1') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $fixture 'Levels/LF1') -Force | Out-Null
foreach ($name in @('ApplyGlobal.ps1','GlobalCommon.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot ('../visual-overhaul/' + $name)) -Destination (Join-Path $overhaul $name)
}
$archive = Join-Path $fixture 'Levels/LF1/level.pak'
[IO.File]::WriteAllText($archive,'original')
$original = (Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $package 'Levels/LF1/level.pak'),'installed')
$installed = (Get-FileHash -LiteralPath (Join-Path $package 'Levels/LF1/level.pak')).Hash.ToLowerInvariant()
$packageId = '0123456789abcdef'
@{schema=1;packageId=$packageId;clientRoot=$fixture;files=@(@{path='Levels/LF1/level.pak';original=$original;staged=$installed})} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $package 'manifest.json')
@{packageId=$packageId;restored=$false} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $overhaul 'installed.json')
$apply = Join-Path $overhaul 'ApplyGlobal.ps1'
Reject { & $apply -ClientPath $fixture -PackagePath $package } 'Running client allowed a new graphics package'
Copy-Item -LiteralPath (Join-Path $package 'Levels/LF1/level.pak') -Destination $archive
$archiveTime = (Get-Item -LiteralPath $archive).LastWriteTimeUtc.Ticks
& $apply -ClientPath $fixture -PackagePath $package
Assert ((Get-Item -LiteralPath $archive).LastWriteTimeUtc.Ticks -eq $archiveTime) 'Installed graphics verification rewrote archive'
'OK: additional Vulkan/Direct3D launches are read-only; renderer switches, tampering, new patches and config mutations remain guarded.'
