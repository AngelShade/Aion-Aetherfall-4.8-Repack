$ErrorActionPreference = 'Stop'
$fixture = Join-Path $PSScriptRoot ('test-workspace/global-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$client = Join-Path $fixture 'client'
$package = Join-Path $fixture 'package'
New-Item -ItemType Directory -Path (Join-Path $client 'bin64'),$package -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $client 'bin64/aion.bin'),'fixture; never executed')
$entries = @()
foreach ($relative in @('Levels/lf1/lf1.pak','Levels/lc1/Level.pak')) {
    $source = Join-Path $client $relative; $target = Join-Path $package $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $source),(Split-Path -Parent $target) -Force | Out-Null
    [IO.File]::WriteAllText($source,('previous regional pack ' + $relative))
    [IO.File]::WriteAllText($target,('new broad pack ' + $relative))
    $entries += @{path=$relative; original=(Get-FileHash -LiteralPath $source).Hash.ToLower(); staged=(Get-FileHash -LiteralPath $target).Hash.ToLower()}
}
@{schema=1;packageId='1234567890abcdef';clientRoot=$client;files=$entries} | ConvertTo-Json -Depth 6 |
    Set-Content -LiteralPath (Join-Path $package 'manifest.json') -Encoding UTF8
$launcher = "@echo off`r`nrem BEGIN DXVK QUALITY`r`nrem existing cursor setup`r`nrem END DXVK QUALITY`r`nrem BEGIN VISUAL OVERHAUL`r`nrem existing regional package`r`nrem END VISUAL OVERHAUL`r`nstart `"`" `"bin64\aion.bin`" -ip:127.0.0.1`r`n"
$launcherPath = Join-Path $client 'Aion Start.bat'
[IO.File]::WriteAllText($launcherPath,$launcher)
$global:fixtureRunning = $true
function Get-Process { param($Name,$ErrorAction) if ($global:fixtureRunning) { [pscustomobject]@{ProcessName='aion.bin'} } }
& (Join-Path $PSScriptRoot 'InstallGlobal.ps1') -ClientPath $client -PackagePath $package
if ((Get-FileHash -LiteralPath (Join-Path $client $entries[0].path)).Hash -ne $entries[0].original) { throw 'Queue changed a loaded archive.' }
$prepared = [IO.File]::ReadAllText($launcherPath)
if ($prepared -notmatch 'existing cursor setup' -or $prepared -notmatch 'VK_INSTANCE_LAYERS' -or $prepared -match 'BEGIN VISUAL OVERHAUL') { throw 'Launcher integration failed.' }
$staged = Join-Path $client 'GraphicsOverhaul/packages/1234567890abcdef'
$guarded = $false
try { & (Join-Path $client 'GraphicsOverhaul/ApplyGlobal.ps1') -ClientPath $client -PackagePath $staged } catch { $guarded = $_.Exception.Message -match 'Exit Aion' }
if (-not $guarded) { throw 'Running-client guard failed.' }
$global:fixtureRunning = $false
& (Join-Path $client 'GraphicsOverhaul/ApplyGlobal.ps1') -ClientPath $client -PackagePath $staged
& (Join-Path $client 'GraphicsOverhaul/ApplyGlobal.ps1') -ClientPath $client -PackagePath $staged
if (@(Get-ChildItem -LiteralPath (Join-Path $client 'GraphicsOverhaul-backups') -Directory).Count -ne 1) { throw 'Repeat application created another backup.' }
$ini = Join-Path $client 'GraphicsOverhaul/PostFX/ReShade.ini'
[IO.File]::AppendAllText($ini,"`r`n; later player settings`r`n")
& (Join-Path $PSScriptRoot 'InstallGlobal.ps1') -ClientPath $client -PackagePath $package
if ([IO.File]::ReadAllText($ini) -notmatch 'later player settings') { throw 'Repeat install lost later preset settings.' }
$bad = Join-Path $client $entries[1].path
[IO.File]::AppendAllText($bad,' later edit')
$rejected = $false
try { & (Join-Path $client 'GraphicsOverhaul/RestoreGlobal.ps1') -ClientPath $client } catch { $rejected = $_.Exception.Message -match 'Later archive edit' }
if (-not $rejected -or (Get-FileHash -LiteralPath (Join-Path $client $entries[0].path)).Hash -ne $entries[0].staged) { throw 'Restore preflight did not protect all later edits.' }
Copy-Item -LiteralPath (Join-Path $package $entries[1].path) -Destination $bad -Force
[IO.File]::AppendAllText($launcherPath,"rem later unrelated launcher edit`r`n")
& (Join-Path $client 'GraphicsOverhaul/RestoreGlobal.ps1') -ClientPath $client
foreach ($entry in $entries) { if ((Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash -ne $entry.original) { throw 'Exact restoration failed.' } }
$restored = [IO.File]::ReadAllText($launcherPath)
if ($restored -notmatch 'BEGIN VISUAL OVERHAUL' -or $restored -notmatch 'existing cursor setup' -or $restored -notmatch 'later unrelated launcher edit' -or $restored -match 'BEGIN GLOBAL GRAPHICS') { throw 'Launcher restoration failed.' }
'PASS: running-client queue/guard, previous regional pack handoff, repeat install/application, later archive edit protection, exact restoration and preserved cursor/launcher/preset edits.'
