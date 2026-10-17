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

$arguments = @(
    '/nologo', '/target:winexe', '/platform:x64', '/optimize+', '/debug-',
    ('/out:' + $outExe),
    ('/win32manifest:' + (Join-Path $launcherDir 'app.manifest')),
    ('/resource:' + (Join-Path $PSScriptRoot 'SetupWindow.xaml') + ',Aetherfall.SetupWindow.xaml'),
    ('/resource:' + (Join-Path $assetsDir 'atreia.png') + ',Aetherfall.atreia.png'),
    ('/resource:' + (Join-Path $assetsDir 'frame.png') + ',Aetherfall.frame.png'),
    ('/resource:' + (Join-Path $assetsDir 'aetherfall-logo.png') + ',Aetherfall.aetherfall-logo.png'),
    ('/resource:' + (Join-Path $assetsDir 'aion-native-atlas.png') + ',Aetherfall.aion-native-atlas.png'),
    ('/reference:' + (Join-Path $framework 'WPF/PresentationFramework.dll')),
    ('/reference:' + (Join-Path $framework 'WPF/PresentationCore.dll')),
    ('/reference:' + (Join-Path $framework 'WPF/WindowsBase.dll')),
    ('/reference:' + (Join-Path $framework 'System.Xaml.dll')),
    ('/reference:' + (Join-Path $framework 'System.Web.Extensions.dll'))
)
if ($IconPath -and (Test-Path -LiteralPath $IconPath)) {
    $arguments += ('/win32icon:' + ([IO.Path]::GetFullPath($IconPath)))
}
$arguments += (Join-Path $PSScriptRoot 'SetupApp.cs')
& $compiler @arguments
if ($LASTEXITCODE -ne 0) {
    throw 'Aetherfall Setup.exe compilation failed.'
}
Write-Output ('Built Aetherfall Setup Hub: ' + $outExe)
