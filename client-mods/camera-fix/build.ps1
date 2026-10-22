param([Parameter(Mandatory = $true)][string]$OutputRoot)
$ErrorActionPreference = 'Stop'
# Upstream commit behind the installed version.dll 1.5.1 (September 2026).
$commit = '023102583a3f7c6d2877accb4c41069bea489d04'
$out = [IO.Path]::GetFullPath($OutputRoot)
if (Test-Path -LiteralPath $out) { throw 'Choose a fresh OutputRoot.' }
$src = Join-Path $out 'aion-version-dll'
git clone -q https://github.com/beyond-aion/aion-version-dll.git $src
if ($LASTEXITCODE) { throw 'Clone failed.' }
git -C $src checkout -q $commit
if ($LASTEXITCODE) { throw 'Checkout failed.' }
git -C $src apply --ignore-whitespace (Join-Path $PSScriptRoot 'camera_fix_mode.patch')
if ($LASTEXITCODE) { throw 'Patch did not apply.' }

# packages.config expects Detours 4.0.1 under src/packages.
$detours = Join-Path $src 'src/packages/Detours.4.0.1'
New-Item -ItemType Directory -Force -Path $detours | Out-Null
$nupkg = Join-Path $src 'src/packages/detours.4.0.1.zip'
Invoke-WebRequest -UseBasicParsing -Uri 'https://www.nuget.org/api/v2/package/Detours/4.0.1' -OutFile $nupkg
Expand-Archive -LiteralPath $nupkg -DestinationPath $detours

$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
$msbuild = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -find 'MSBuild\**\Bin\amd64\MSBuild.exe' | Select-Object -First 1
if (-not $msbuild) { throw 'MSBuild with the C++ tools was not found.' }
# The project targets v145; VS 2022 (v143) builds the same exports.
$toolset = if ($env:CAMERA_FIX_TOOLSET) { $env:CAMERA_FIX_TOOLSET } else { 'v143' }
& $msbuild (Join-Path $src 'src/AionVersionDll/AionVersionDll.vcxproj') /p:Configuration=Release /p:Platform=x64 /p:PlatformToolset=$toolset /p:TrackFileAccess=false /m /v:m /nologo
if ($LASTEXITCODE) { throw 'Build failed.' }
$dll = Join-Path $out 'version.dll'
Copy-Item -LiteralPath (Join-Path $src 'src/AionVersionDll/x64/Release/version.dll') -Destination $dll
"Built $dll sha256 $((Get-FileHash -LiteralPath $dll).Hash.ToLower())"
