param(
    [ValidateSet('Open','Start','Browse','Initialize','Admin','Configure')][string]$Action='Open',
    [switch]$CheckOnly,
    [switch]$AdminPanel,
    [int]$LauncherId=0
)
$ErrorActionPreference='Stop'
$websiteRoot=[IO.Path]::GetFullPath($PSScriptRoot)
$component=Get-Content -LiteralPath (Join-Path $websiteRoot 'component.json') -Raw | ConvertFrom-Json
$localRoot=Join-Path $websiteRoot 'local'
New-Item -ItemType Directory -Path $localRoot -Force | Out-Null
$instancePath=Join-Path $localRoot 'instance.id'
function Get-WebsiteArchiveHash([string]$Path) {
    $stream=[IO.File]::OpenRead($Path)
    $sha=[Security.Cryptography.SHA256]::Create()
    try {return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLowerInvariant()}
    finally {$sha.Dispose();$stream.Dispose()}
}
function Get-WebsitePhpArguments([string]$RuntimeRoot) {
    $cacheRoot=Join-Path $localRoot 'state\framework\opcache'
    New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
    $cacheId=[IO.File]::ReadAllText($instancePath).Trim()
    return @('-c',(Join-Path $RuntimeRoot 'php.ini'),'-d',('extension_dir='+ (Join-Path $RuntimeRoot 'ext')),
        '-d',('opcache.cache_id='+$cacheId),'-d',('opcache.file_cache='+$cacheRoot),'-d','opcache.file_cache_fallback=1')
}
function Get-WebsiteUrl {
    $settingsPath=Join-Path $localRoot 'settings.env'
    if(!(Test-Path -LiteralPath $settingsPath)){return 'http://127.0.0.1:8090'}
    $settings=Get-Content -LiteralPath $settingsPath -Raw
    $match=[regex]::Match($settings,'(?m)^APP_URL=http://127\.0\.0\.1:(\d+)\s*$')
    if(!$match.Success){throw 'APP_URL must use http://127.0.0.1:PORT for this local website.'}
    $port=[int]$match.Groups[1].Value
    if($port -lt 1024 -or $port -gt 65535){throw 'Choose a website port between 1024 and 65535.'}
    return 'http://127.0.0.1:'+$port
}
function Test-WebsiteReady([string]$Url) {
    if(!(Test-Path -LiteralPath $instancePath)){return $false}
    $instance=[IO.File]::ReadAllText($instancePath).Trim()
    $response=$null
    try {
        $request=[Net.HttpWebRequest]::Create($Url+'/up')
        $request.Proxy=$null
        $request.AllowAutoRedirect=$false
        $request.Timeout=750
        $response=$request.GetResponse()
        return ([int]$response.StatusCode -eq 200 -and $response.Headers['X-Aetherfall-Website'] -eq $instance)
    } catch {return $false} finally {if($response){$response.Close()}}
}
function Open-WebsiteWhenReady {
    $deadline=[DateTime]::UtcNow.AddSeconds(120)
    while([DateTime]::UtcNow -lt $deadline){
        $url=Get-WebsiteUrl
        if(Test-WebsiteReady $url){
            if($AdminPanel){
                $runtime=Join-Path $localRoot ('runtime-'+([string]$component.runtime.sha256).Substring(0,16))
                $app=Join-Path $localRoot ('app-'+([string]$component.app.sha256).Substring(0,16))
                if(!(Test-Path -LiteralPath (Join-Path $app '.complete'))){throw 'Close the running website and open Website.cmd to finish the update first.'}
                $env:AETHERFALL_WEBSITE_ROOT=$websiteRoot
                $env:PHP_INI_SCAN_DIR=''
                Push-Location -LiteralPath $app
                try {
                    $ownerArguments=Get-WebsitePhpArguments $runtime
                    $ownerLink=& (Join-Path $runtime 'php.exe') @ownerArguments portable.php aetherfall:website-owner-link --no-interaction
                    if($LASTEXITCODE -ne 0){throw 'The control panel could not open. Use Website Tools.cmd to create a website administrator.'}
                    $url=([string]$ownerLink).Trim()
                    if($url -notmatch '^http://127\.0\.0\.1:\d+/owner-access/[a-f0-9]{64}$'){throw 'The local administrator link was not valid.'}
                } finally {Pop-Location}
            }
            if($CheckOnly){Write-Output ('Ready: '+$url)}else{Start-Process -FilePath $url}
            return
        }
        if($LauncherId -gt 0 -and !(Get-Process -Id $LauncherId -ErrorAction SilentlyContinue)){
            throw 'Website startup stopped before it was ready. Review the website console.'
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'The website did not become ready. Review the website console and local/browser-open.err.log.'
}
if($Action -eq 'Browse'){Open-WebsiteWhenReady;return}
if($Action -eq 'Open' -and (Test-WebsiteReady (Get-WebsiteUrl))){
    Open-WebsiteWhenReady
    return
}
try {
    $guard=New-Object IO.FileStream((Join-Path $localRoot 'website.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
} catch [IO.IOException] {
    if($Action -eq 'Open'){Open-WebsiteWhenReady;return}
    throw 'The website is already running. Close its website console before using the maintenance tools.'
}
try {
    if(!(Test-Path -LiteralPath $instancePath)){
        [IO.File]::WriteAllText($instancePath,[Guid]::NewGuid().ToString())
    }
    $env:AETHERFALL_WEBSITE_INSTANCE=[IO.File]::ReadAllText($instancePath).Trim()
    foreach($kind in @('runtime','app')){
        $archive=Join-Path $websiteRoot ($kind+'.zip')
        $expected=[string]$component.$kind.sha256
        if((Get-WebsiteArchiveHash $archive) -ne $expected){throw ('Website '+$kind+' archive checksum failed.')}
        $destination=Join-Path $localRoot ($kind+'-'+$expected.Substring(0,16))
        if(!(Test-Path -LiteralPath (Join-Path $destination '.complete'))){
            if(Test-Path -LiteralPath $destination){throw ('An incomplete website extraction exists. Preserve it and extract into a fresh folder: '+$destination)}
            Write-Output ('Preparing the portable website '+$kind+'...')
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            [IO.Compression.ZipFile]::ExtractToDirectory($archive,$destination)
            [IO.File]::WriteAllText((Join-Path $destination '.complete'),$expected)
        }
        if($kind -eq 'runtime'){$runtimeRoot=$destination}else{$applicationRoot=$destination}
    }
    $php=Join-Path $runtimeRoot 'php.exe'
    $env:AETHERFALL_WEBSITE_ROOT=$websiteRoot
    $env:PHP_INI_SCAN_DIR=''
    $arguments=Get-WebsitePhpArguments $runtimeRoot
    Push-Location -LiteralPath $applicationRoot
    try {
        if($Action -eq 'Configure'){
            & $php @arguments portable.php --version
            Start-Process notepad.exe -ArgumentList ('"'+(Join-Path $localRoot 'settings.env')+'"')
            return
        }
        if(!(Test-Path -LiteralPath (Join-Path $localRoot 'cms-initialized.json'))){
            & $php @arguments portable.php migrate --force --no-interaction
            if($LASTEXITCODE -ne 0){throw 'Website CMS migration failed.'}
            & $php @arguments portable.php shield:generate --all --panel=admin --option=permissions --no-interaction
            if($LASTEXITCODE -ne 0){throw 'Website permission initialization failed.'}
            & $php @arguments portable.php db:seed --force --no-interaction
            if($LASTEXITCODE -ne 0){throw 'Website role initialization failed.'}
            & $php @arguments portable.php aetherfall:website-defaults
            if($LASTEXITCODE -ne 0){throw 'Website settings initialization failed.'}
            [IO.File]::WriteAllText((Join-Path $localRoot 'cms-initialized.json'),('{"schema":1,"installedAt":"'+[DateTime]::UtcNow.ToString('o')+'"}'))
            Write-Output 'Standalone website CMS initialized.'
        } else {
            & $php @arguments portable.php migrate --force --no-interaction
            if($LASTEXITCODE -ne 0){throw 'Website CMS migration failed.'}
            & $php @arguments portable.php aetherfall:website-defaults
            if($LASTEXITCODE -ne 0){throw 'Website settings initialization failed.'}
        }
        $contentMarker=Join-Path $localRoot 'aetherfall-content-v1.json'
        if(!(Test-Path -LiteralPath $contentMarker)){
            & $php @arguments portable.php aetherfall:website-content --no-interaction
            if($LASTEXITCODE -ne 0){throw 'The Aetherfall website content could not be prepared.'}
            [IO.File]::WriteAllText($contentMarker,('{"installedAt":"'+[DateTime]::UtcNow.ToString('o')+'"}'))
        }
        if($Action -eq 'Admin'){
            & $php @arguments portable.php aetherfall:website-admin
            if($LASTEXITCODE -ne 0){throw 'Website administrator was not changed.'}
            return
        }
        if($Action -eq 'Initialize'){
            & $php @arguments portable.php aetherfall:website-native
            if($LASTEXITCODE -ne 0){throw 'Native initialization did not finish. Check this website connection settings.'}
            return
        }
        $url=Get-WebsiteUrl
        $port=([Uri]$url).Port
        if($Action -eq 'Open'){
            $browseArguments='-NoProfile -ExecutionPolicy Bypass -File "'+$PSCommandPath+'" -Action Browse -LauncherId '+$PID
            if($CheckOnly){$browseArguments+=' -CheckOnly'}
            if($AdminPanel){$browseArguments+=' -AdminPanel'}
            Start-Process -FilePath powershell.exe -ArgumentList $browseArguments -WindowStyle Hidden -WorkingDirectory $websiteRoot -RedirectStandardOutput (Join-Path $localRoot 'browser-open.log') -RedirectStandardError (Join-Path $localRoot 'browser-open.err.log') | Out-Null
        }
        Write-Output ('Website: '+$url+'. Admin: /admin')
        Write-Output 'Keep this window open. Press Ctrl+C to stop only the website.'
        & $php @arguments -S ('127.0.0.1:'+$port) -t public portable.php
        if($LASTEXITCODE -ne 0){throw 'The website stopped. Check whether another application is using its port.'}
    } finally {Pop-Location}
} finally {$guard.Dispose()}
