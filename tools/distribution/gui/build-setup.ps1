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
    ('/resource:' + (Join-Path $launcherDir 'LanguageTheme.xaml') + ',Aetherfall.LanguageTheme.xaml'),
    ('/resource:' + (Join-Path $launcherDir 'LanguageSelector.xaml') + ',Aetherfall.LanguageSelector.xaml'),
    ('/resource:' + (Join-Path $PSScriptRoot '../Update-Repack.ps1') + ',Aetherfall.Update-Repack.ps1'),
    ('/resource:' + (Join-Path $PSScriptRoot '../UpdateTransfer.cs') + ',Aetherfall.UpdateTransfer.cs'),
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
    ('/reference:' + (Join-Path $framework 'System.IO.Compression.dll'))
    ('/reference:' + (Join-Path $framework 'System.IO.Compression.FileSystem.dll'))
)
$arguments += ('/win32icon:' + ([IO.Path]::GetFullPath($IconPath)))
$arguments += (Join-Path $PSScriptRoot 'SetupApp.cs')
$arguments += (Join-Path $PSScriptRoot 'ServerControl.cs')
$arguments += (Join-Path $PSScriptRoot 'OriginalInstallation.cs')
$arguments += (Join-Path $PSScriptRoot 'HubMods.cs')
$arguments += (Join-Path $PSScriptRoot 'HubLanguages.cs')
$arguments += (Join-Path $launcherDir 'LanguageSelection.cs')
$arguments += (Join-Path $launcherDir 'LanguageSelector.cs')
$arguments += (Join-Path $launcherDir 'LanguageDownloads.cs')
$arguments += (Join-Path $PSScriptRoot '../UpdateTransfer.cs')
$sourceFiles=@($arguments | Where-Object { $_ -match '\.cs$' -and !$_.StartsWith('/') })+@(
    $arguments | Where-Object { $_.StartsWith('/resource:') } | ForEach-Object { $_.Substring(10).Split(',')[0] }
)+@(
    (Join-Path $PSScriptRoot 'SetupWindow.xaml'),(Join-Path $launcherDir 'LanguageTheme.xaml'),
    (Join-Path $launcherDir 'LanguageSelector.xaml'),(Join-Path $PSScriptRoot '../Update-Repack.ps1'),
    (Join-Path $launcherDir 'app.manifest'),([IO.Path]::GetFullPath($IconPath)),$PSCommandPath)
$buildInputs=@{}
foreach($source in $sourceFiles){$buildInputs[[IO.Path]::GetFullPath($source)]=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()}
& $compiler @arguments
if ($LASTEXITCODE -ne 0) {
    throw 'Aetherfall Setup.exe compilation failed.'
}
Write-Output ('Built Aetherfall Setup Hub: ' + $outExe)

foreach($source in $buildInputs.Keys){if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $buildInputs[$source]){throw ('Hub source changed during build: '+$source)}}
@{schema=1;inputs=$buildInputs;hubSha256=(Get-FileHash -LiteralPath $outExe -Algorithm SHA256).Hash.ToLowerInvariant()} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath ($outExe+'.build.json') -Encoding UTF8
