param([Parameter(Mandatory=$true)][string]$ClientPath,[string]$PackagePath=(Join-Path $PSScriptRoot 'build/graphics-menu'))
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$root = Join-Path $client 'DXVK/graphics-menu'
$statePath = Join-Path $root 'installed.json'
if (Test-Path -LiteralPath $statePath) {
    & (Join-Path $root 'ApplyGraphicsMenu.ps1') -ClientPath $client -VerifyOnly
    return
}
$manifest = Get-Content -LiteralPath (Join-Path $PackagePath 'manifest.json') -Raw | ConvertFrom-Json
foreach ($entry in $manifest.files) {
    if (@('bin64/Game.dll','Data/ui/game/game.pak','bin64/AionGraphicsMenu.dll','L10N/enu/Data/data.pak') -cnotcontains $entry.path) { throw 'Unexpected menu payload target.' }
    if ((Get-FileHash -LiteralPath (Join-Path $PackagePath $entry.path)).Hash -ne $entry.installed) { throw 'Menu package checksum failed.' }
    $target = Join-Path $client $entry.path
    if ($null -eq $entry.original) {
        if (Test-Path -LiteralPath $target) { throw 'Menu helper already exists outside this installation.' }
    } elseif ((Get-FileHash -LiteralPath $target).Hash -ne $entry.original) { throw 'Current client changed since menu package preparation.' }
}
$launcherPath = Join-Path $client 'Aion Start.bat'
$launcher = [IO.File]::ReadAllText($launcherPath)
if ($launcher -notmatch '(?m)^rem BEGIN DXVK QUALITY\r?$') { throw 'DXVK launcher integration missing.' }
if ($launcher -match '(?m)^rem BEGIN RENDERER MENU\r?$') { throw 'Renderer menu already queued. Inspect the existing package first.' }
$backup = Join-Path $client ('DXVK-backups/menu-launcher-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $backup 'Aion Start.bat')
New-Item -ItemType Directory -Path $root -Force | Out-Null
Copy-Item -LiteralPath $PackagePath -Destination (Join-Path $root 'package') -Recurse
foreach ($name in @('ApplyGraphicsMenu.ps1','RestoreGraphicsMenu.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $root $name) -Force }
foreach ($name in @('RendererCommon.ps1','SelectRenderer.ps1','VerifyActive.ps1','Restore.ps1')) {
    $target = Join-Path $client ('DXVK/' + $name)
    if (Test-Path -LiteralPath $target) { Copy-Item -LiteralPath $target -Destination (Join-Path $backup $name) }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $target -Force
}
if (Test-Path -LiteralPath (Join-Path $client 'DXVK/cursor-fix')) {
    foreach ($name in @('InstallNativeCursorPatch.ps1','RendererCommon.ps1')) {
        $target = Join-Path $client ('DXVK/cursor-fix/' + $name)
        if (Test-Path -LiteralPath $target) { Copy-Item -LiteralPath $target -Destination (Join-Path $backup ('cursor-' + $name)) }
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $target -Force
    }
}
$ini = Join-Path $client 'DXVK/renderer.ini'
if (-not (Test-Path -LiteralPath $ini)) { [IO.File]::WriteAllText($ini,"[Renderer]`r`nVulkan=1`r`nActiveVulkan=1`r`n",[Text.Encoding]::ASCII) }
$block = @'
rem BEGIN RENDERER MENU
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%PSModulePath%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0DXVK\graphics-menu\ApplyGraphicsMenu.ps1" -ClientPath "%~dp0."
if errorlevel 1 (
    echo Graphics menu verification failed. Startup stopped to preserve the client.
    pause
    exit /b 1
)
rem END RENDERER MENU
'@
$select = @'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0DXVK\SelectRenderer.ps1" -ClientPath "%~dp0."
if errorlevel 1 (
    echo Renderer selection failed. Startup stopped to preserve the client.
    pause
    exit /b 1
)
call "%~dp0DXVK\renderer-env.bat"
'@
$block = ($block -replace '\r?\n',"`r`n") + "`r`n"
$select = ($select -replace '\r?\n',"`r`n") + "`r`n"
$launcher = $launcher.Replace("rem BEGIN DXVK QUALITY",$block + 'rem BEGIN DXVK QUALITY')
$marker = [regex]::Match($launcher,'(?m)^if exist "%~dp0DXVK\\cursor-fix\\InstallNativeCursorPatch\.ps1"').Index
if ($marker -eq 0) { throw 'Expected the installed native cursor startup block.' }
$launcher = $launcher.Insert($marker,$select)
# Native environment/art upgrades apply under either backend. Only the private
# Vulkan effects environment is conditional on this launch's renderer choice.
$start = $launcher.IndexOf('if defined VK_LAYER_PATH (')
$end = $launcher.IndexOf('rem END GLOBAL GRAPHICS')
if ($start -ge 0 -and $end -gt $start) {
    $launcher = $launcher.Insert($end,")`r`n").Insert($start,"if not `"%AION_USE_VULKAN%`"==`"0`" (`r`n")
}
[IO.File]::WriteAllText($launcherPath,$launcher,[Text.Encoding]::ASCII)
@{clientRoot=$client;launcherBackup=$backup;status='Queued for next normal launch'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'queued.json') -Encoding UTF8
& (Join-Path $root 'ApplyGraphicsMenu.ps1') -ClientPath $client -VerifyOnly
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { 'Graphics menu queued. Exit Aion normally, then use Aion Start.bat.' }
else { & (Join-Path $root 'ApplyGraphicsMenu.ps1') -ClientPath $client }
