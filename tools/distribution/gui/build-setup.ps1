param(
    [Parameter(Mandatory=$true)][string]$OutputExePath,
    [string]$IconPath
)
$ErrorActionPreference = 'Stop'
$outExe = [IO.Path]::GetFullPath($OutputExePath)
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($outExe)) -Force | Out-Null

$framework = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319'
$compiler = Join-Path $framework 'csc.exe'
$launcherDir = Join-Path $PSScriptRoot '../../../client-mods/launcher'
$assetsDir = Join-Path $launcherDir 'assets'
if (!$IconPath) { $IconPath = Join-Path $assetsDir 'aetherfall-hub.ico' }
if (!(Test-Path -LiteralPath $IconPath -PathType Leaf)) { throw "Hub icon is missing: $IconPath" }

$arguments = @(
    '/nologo', '/target:winexe', '/platform:x64', '/optimize+', '/debug-',
    ('/out:' + $outExe),
    ('/win32manifest:' + (Join-Path $launcherDir 'app.manifest')),
    ('/resource:' + (Join-Path $PSScriptRoot 'SetupWindow.xaml') + ',Aetherfall.SetupWindow.xaml'),
    ('/resource:' + (Join-Path $PSScriptRoot '../Update-Repack.ps1') + ',Aetherfall.Update-Repack.ps1'),
    ('/resource:' + (Join-Path $assetsDir 'atreia.png') + ',Aetherfall.atreia.png'),
    ('/resource:' + (Join-Path $assetsDir 'frame.png') + ',Aetherfall.frame.png'),
    ('/resource:' + (Join-Path $assetsDir 'aetherfall-logo.png') + ',Aetherfall.aetherfall-logo.png'),
    ('/resource:' + (Join-Path $assetsDir 'aion-native-atlas.png') + ',Aetherfall.aion-native-atlas.png'),
    ('/resource:' + ([IO.Path]::GetFullPath($IconPath)) + ',Aetherfall.HubIcon.ico'),
    ('/reference:' + (Join-Path $framework 'WPF/PresentationFramework.dll')),
    ('/reference:' + (Join-Path $framework 'WPF/PresentationCore.dll')),
    ('/reference:' + (Join-Path $framework 'WPF/WindowsBase.dll')),
    ('/reference:' + (Join-Path $framework 'System.Xaml.dll')),
    ('/reference:' + (Join-Path $framework 'System.Web.Extensions.dll'))
    ('/reference:' + (Join-Path $framework 'System.Management.dll'))
)
$arguments += ('/win32icon:' + ([IO.Path]::GetFullPath($IconPath)))
$arguments += (Join-Path $PSScriptRoot 'SetupApp.cs')
$arguments += (Join-Path $PSScriptRoot 'ServerControl.cs')
$arguments += (Join-Path $PSScriptRoot 'OriginalInstallation.cs')
& $compiler @arguments
if ($LASTEXITCODE -ne 0) {
    throw 'Aetherfall Setup.exe compilation failed.'
}
Write-Output ('Built Aetherfall Setup Hub: ' + $outExe)
