param([Parameter(Mandatory=$true)][string]$ClientPath,[Parameter(Mandatory=$true)][string]$PackagePath)
. (Join-Path $PSScriptRoot 'GlobalCommon.ps1')
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$package = (Resolve-Path -LiteralPath $PackagePath).Path
$manifest = Read-GlobalManifest $package
if ($manifest.clientRoot -ne $client) { throw 'Package belongs to another client.' }
$sources = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'postfx-sources.json') -Raw | ConvertFrom-Json
foreach ($pin in $sources.payload.PSObject.Properties) {
    if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot ('research/' + $pin.Name))).Hash -ne $pin.Value) { throw 'Pinned ReShade payload checksum failed.' }
}
if (-not (Test-Path -LiteralPath (Join-Path $client 'bin64/aion.bin'))) { throw 'Expected client not found.' }
$launcherPath = Join-Path $client 'Aion Start.bat'
$launcher = [IO.File]::ReadAllText($launcherPath)
$pattern = '(?im)^start\s+""\s+"bin64\\aion\.bin"[^\r\n]*'
if ([regex]::Matches($launcher,$pattern).Count -ne 1) { throw 'Unexpected launcher architecture or layout.' }
foreach ($entry in $manifest.files) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash
    if ($hash -ne $entry.original -and $hash -ne $entry.staged) { throw "Changed client archive: $($entry.path). No files changed." }
}
$root = Join-Path $client 'GraphicsOverhaul'
$queuePath = Join-Path $root 'queued.json'
if (Test-Path -LiteralPath $queuePath) {
    $previousQueue = Get-Content -LiteralPath $queuePath -Raw | ConvertFrom-Json
    if ($previousQueue.packageId -ne $manifest.packageId) { throw 'Restore the previous broad package before installing another.' }
    # Preserve a player's edited ReShade settings on a repeat install.
    & (Join-Path $root 'ApplyGlobal.ps1') -ClientPath $client -PackagePath (Join-Path $root ('packages/' + $manifest.packageId)) -VerifyOnly
    'This graphics package is already prepared.'; return
}
$staged = Join-Path $root ('packages/' + $manifest.packageId)
New-Item -ItemType Directory -Path $staged -Force | Out-Null
foreach ($entry in $manifest.files) {
    $target = Join-Path $staged $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $package $entry.path) -Destination $target
}
Copy-Item -LiteralPath (Join-Path $package 'manifest.json') -Destination (Join-Path $staged 'manifest.json')
$null = Read-GlobalManifest $staged
foreach ($name in @('GlobalCommon.ps1','ApplyGlobal.ps1','RestoreGlobal.ps1','VerifyGlobal.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $root $name) -Force
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'postfx-sources.json') -Destination (Join-Path $root 'postfx-sources.json')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'postfx') -Destination (Join-Path $root 'PostFX') -Recurse
foreach ($architecture in @('64','32')) {
    $layer = Join-Path $root ('vulkan' + $architecture)
    New-Item -ItemType Directory -Path $layer -Force | Out-Null
    foreach ($ext in @('dll','json')) {
        $source = Join-Path $PSScriptRoot ('research/ReShade' + $architecture + '.' + $ext)
        $target = Join-Path $layer ('ReShade' + $architecture + '.' + $ext)
        Copy-Item -LiteralPath $source -Destination $target
        if ((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $source).Hash) { throw 'Vulkan layer copy checksum failed.' }
    }
}
$fxRoot = Join-Path $root 'PostFX'
New-Item -ItemType Directory -Path (Join-Path $fxRoot 'Cache') -Force | Out-Null
$ini = @"
[GENERAL]
EffectSearchPaths=$fxRoot\Shaders
TextureSearchPaths=$fxRoot\Textures
PresetPath=$fxRoot\AionEnhanced.ini
IntermediateCachePath=$fxRoot\Cache
PerformanceMode=1
SkipLoadingDisabledEffects=1
[INPUT]
KeyOverlay=36,0,0,0
KeyEffects=145,0,0,0
KeyScreenshot=44,0,0,0
InputProcessing=2
[OVERLAY]
TutorialProgress=4
ShowFPS=0
ShowFrameTime=0
[SCREENSHOT]
SavePath=$client\Screenshot
FileFormat=1
SaveBeforeShot=1
"@
[IO.File]::WriteAllText((Join-Path $fxRoot 'ReShade.ini'),$ini,[Text.Encoding]::UTF8)
$oldBlock = [regex]::Match($launcher,'(?ms)^rem BEGIN VISUAL OVERHAUL\r?\n.*?^rem END VISUAL OVERHAUL\r?\n?').Value
$launcher = [regex]::Replace($launcher,'(?ms)^rem BEGIN VISUAL OVERHAUL\r?\n.*?^rem END VISUAL OVERHAUL\r?\n?','')
$block = @'
rem BEGIN GLOBAL GRAPHICS
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0GraphicsOverhaul\ApplyGlobal.ps1" -ClientPath "%~dp0." -PackagePath "%~dp0GraphicsOverhaul\packages\PACKAGE_ID"
if errorlevel 1 (
    echo Graphics package verification failed. See GraphicsOverhaul\RestoreGlobal.ps1.
    pause
    exit /b 1
)
if not "%AION_USE_VULKAN%"=="0" (
if defined VK_LAYER_PATH (
    set "VK_LAYER_PATH=%~dp0GraphicsOverhaul\vulkan64;%VK_LAYER_PATH%"
) else (
    set "VK_LAYER_PATH=%~dp0GraphicsOverhaul\vulkan64"
)
if defined VK_INSTANCE_LAYERS (
    set "VK_INSTANCE_LAYERS=VK_LAYER_reshade;%VK_INSTANCE_LAYERS%"
) else (
    set "VK_INSTANCE_LAYERS=VK_LAYER_reshade"
)
set "RESHADE_BASE_PATH_OVERRIDE=%~dp0GraphicsOverhaul\PostFX"
)
rem END GLOBAL GRAPHICS
'@
$block = ($block.Replace('PACKAGE_ID',$manifest.packageId) -replace '\r?\n',"`r`n") + "`r`n"
$match = [regex]::Match($launcher,$pattern)
$launcher = $launcher.Insert($match.Index,$block)
Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $root 'launcher-before-global.bat')
@{packageId=$manifest.packageId; clientRoot=$client; previousVisualBlock=$oldBlock; status='Prepared for next normal launch'} |
    ConvertTo-Json | Set-Content -LiteralPath $queuePath -Encoding UTF8
[IO.File]::WriteAllText($launcherPath,$launcher,[Text.Encoding]::ASCII)
& (Join-Path $root 'ApplyGlobal.ps1') -ClientPath $client -PackagePath $staged -VerifyOnly
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { 'Queued for the next normal launch. Exit Aion normally and use Aion Start.bat.' }
else { & (Join-Path $root 'ApplyGlobal.ps1') -ClientPath $client -PackagePath $staged }
