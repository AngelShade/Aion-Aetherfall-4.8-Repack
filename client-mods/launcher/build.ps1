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
    ('/resource:'+(Join-Path $PSScriptRoot 'LanguageTheme.xaml')+',Aetherfall.LanguageTheme.xaml'),
    ('/resource:'+(Join-Path $PSScriptRoot 'LanguageSelector.xaml')+',Aetherfall.LanguageSelector.xaml'),
    ('/resource:'+(Join-Path $PSScriptRoot '../native-icon-bridge/ui_art.json')+',Aetherfall.CompanionArt.json'),
    ('/reference:'+(Join-Path $framework 'WPF/PresentationFramework.dll')),
    ('/reference:'+(Join-Path $framework 'WPF/PresentationCore.dll')),
    ('/reference:'+(Join-Path $framework 'WPF/WindowsBase.dll')),
    ('/reference:'+(Join-Path $framework 'System.Xaml.dll')),
    ('/reference:'+(Join-Path $framework 'System.Web.Extensions.dll')),
    ('/reference:'+(Join-Path $framework 'System.Xml.Linq.dll')),
    ('/reference:'+(Join-Path $framework 'System.IO.Compression.dll')),
    ('/reference:'+(Join-Path $framework 'System.IO.Compression.FileSystem.dll')))
if($IconPath){$arguments+=('/win32icon:'+([IO.Path]::GetFullPath($IconPath)))}
$arguments+=(Join-Path $PSScriptRoot 'Launcher.cs')
$arguments+=(Join-Path $PSScriptRoot 'StartupPresentation.cs')
$arguments+=(Join-Path $PSScriptRoot 'CustomQuests.cs')
$arguments+=(Join-Path $PSScriptRoot 'AetherfallInterface.cs')
$arguments+=(Join-Path $PSScriptRoot 'CompanionArt.cs')
$arguments+=(Join-Path $PSScriptRoot 'Languages.cs')
$arguments+=(Join-Path $PSScriptRoot 'LanguageDownloads.cs')
$arguments+=(Join-Path $PSScriptRoot 'LanguagePage.cs')
$arguments+=(Join-Path $PSScriptRoot 'LanguageSelection.cs')
$arguments+=(Join-Path $PSScriptRoot 'LanguageSelector.cs')
$arguments+=(Join-Path $PSScriptRoot '../../tools/distribution/UpdateTransfer.cs')
$arguments+=(Join-Path $PSScriptRoot 'Mods.cs')
$arguments+=(Join-Path $PSScriptRoot 'ModsWindow.cs')
$arguments+=(Join-Path $PSScriptRoot 'QuestPakKeys.cs')
$arguments+=(Join-Path $PSScriptRoot 'AssemblyInfo.cs')
$sourceFiles=@($arguments | Where-Object { $_ -match '\.cs$' })+@(
    (Join-Path $PSScriptRoot 'MainWindow.xaml'),(Join-Path $PSScriptRoot 'app.manifest'),
    (Join-Path $PSScriptRoot 'LanguageTheme.xaml'),(Join-Path $PSScriptRoot 'LanguageSelector.xaml'),
    (Join-Path $PSScriptRoot '../native-icon-bridge/ui_art.json'),$PSCommandPath)
$beforeHashes=@{}
foreach($source in $sourceFiles){$beforeHashes[[IO.Path]::GetFullPath($source)]=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()}
& $compiler @arguments
if($LASTEXITCODE -ne 0){throw 'Launcher compilation failed.'}
foreach($source in $sourceFiles){if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $beforeHashes[[IO.Path]::GetFullPath($source)]){throw ('Launcher source changed during build: '+$source)}}
$assets=Join-Path $outputRoot 'Mods/Launcher/Assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'assets/atreia.png'),(Join-Path $PSScriptRoot 'assets/frame.png'),(Join-Path $PSScriptRoot 'assets/aetherfall-logo.png'),(Join-Path $PSScriptRoot 'assets/aion-native-atlas.png'),(Join-Path $PSScriptRoot 'assets/logo-source.json') -Destination $assets
'Built graphical launcher: '+(Join-Path $outputRoot 'Aetherfall Launcher.exe')

Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../../release.json') -Destination (Join-Path $outputRoot 'Mods/release.json')
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$sourceHashes=@{}
foreach($source in $beforeHashes.Keys){$sourceHashes[$source.Substring($repoRoot.Length+1).Replace('\','/')]=$beforeHashes[$source]}
@{schema=1;inputs=$sourceHashes;launcherSha256=(Get-FileHash -LiteralPath (Join-Path $outputRoot 'Aetherfall Launcher.exe') -Algorithm SHA256).Hash.ToLowerInvariant()} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputRoot 'launcher-build.json') -Encoding UTF8
