param([Parameter(Mandatory=$true)][string]$OutputPath,[string]$IconPath)
$ErrorActionPreference='Stop'
$outputRoot=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$framework=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319'
$compiler=Join-Path $framework 'csc.exe'
$arguments=@('/nologo','/target:winexe','/platform:x64','/optimize+','/debug-',
    ('/out:'+(Join-Path $outputRoot 'Aetherfall Launcher.exe')),
    ('/win32manifest:'+(Join-Path $PSScriptRoot 'app.manifest')),
    ('/resource:'+(Join-Path $PSScriptRoot 'MainWindow.xaml')+',Aetherfall.MainWindow.xaml'),
    ('/reference:'+(Join-Path $framework 'WPF/PresentationFramework.dll')),
    ('/reference:'+(Join-Path $framework 'WPF/PresentationCore.dll')),
    ('/reference:'+(Join-Path $framework 'WPF/WindowsBase.dll')),
    ('/reference:'+(Join-Path $framework 'System.Xaml.dll')),
    ('/reference:'+(Join-Path $framework 'System.Web.Extensions.dll')))
if($IconPath){$arguments+=('/win32icon:'+([IO.Path]::GetFullPath($IconPath)))}
$arguments+=(Join-Path $PSScriptRoot 'Launcher.cs')
$arguments+=(Join-Path $PSScriptRoot 'StartupPresentation.cs')
$arguments+=(Join-Path $PSScriptRoot 'AssemblyInfo.cs')
& $compiler @arguments
if($LASTEXITCODE -ne 0){throw 'Launcher compilation failed.'}
$assets=Join-Path $outputRoot 'Mods/Launcher/Assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'assets/atreia.png'),(Join-Path $PSScriptRoot 'assets/frame.png'),(Join-Path $PSScriptRoot 'assets/aetherfall-logo.png'),(Join-Path $PSScriptRoot 'assets/aion-native-atlas.png'),(Join-Path $PSScriptRoot 'assets/logo-source.json') -Destination $assets
'Built graphical launcher: '+(Join-Path $outputRoot 'Aetherfall Launcher.exe')

Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../../release.json') -Destination (Join-Path $outputRoot 'Mods/release.json')
