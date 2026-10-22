param(
    [Parameter(Mandatory=$true)][string]$ClientPath,
    [ValidateSet('Enable','Disable','Ensure','Verify')][string]$Action='Ensure',
    [string]$ArchivePath,
    [string]$StandardCacheRoot
)
$ErrorActionPreference='Stop'
if ($PSVersionTable.PSEdition -eq 'Desktop') { $env:PSModulePath = (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/Modules') + ';' + $env:PSModulePath }
$client=(Resolve-Path -LiteralPath $ClientPath).Path
$package=[IO.Directory]::GetParent($client).FullName
$mod=Join-Path $client 'Mods/HDTextures'
$contractPath=Join-Path $mod 'component.json'
$statePath=Join-Path $mod 'installed.json'
$utf8=New-Object Text.UTF8Encoding($false)
function Read-Json([string]$Path) { Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json }
function Json-Bytes($Value) { $utf8.GetBytes(($Value | ConvertTo-Json -Depth 40)) }
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Within([string]$Root,[string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or ($Relative -split '[\\/]') -contains '..') { throw 'Invalid HD texture path.' }
    $full=[IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (-not $full.StartsWith($Root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'HD texture path escapes its folder.' }
    return $full
}
function Assert-Closed {
    foreach ($process in @(Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue)) {
        try { $path=$process.Path } catch { throw 'Cannot verify that Aion is closed. Close Aion normally and retry.' }
        if (-not $path) { throw 'Cannot verify the Aion process path. Close Aion normally and retry.' }
        if ($path.StartsWith($client.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Close this Aion client normally before changing textures.' }
    }
}
function File-Stamp([string]$Path) {
    $file=Get-Item -LiteralPath $Path
    return @{bytes=$file.Length; ticks=$file.LastWriteTimeUtc.Ticks}
}
if (-not (Test-Path -LiteralPath (Join-Path $package 'REPACK.json')) -or -not (Test-Path -LiteralPath (Join-Path $client 'bin64/aion.bin'))) { throw 'Select the Client folder inside an Aetherfall repack.' }
$contract=Read-Json $contractPath
if ($contract.schema -ne 1 -or $contract.id -ne 'aetherfall-world-hd' -or $contract.files.Count -lt 1) { throw 'Unsupported HD texture component.' }
$coreRelease=Read-Json (Join-Path $package 'release.json')
if ([Version]$coreRelease.Version -lt [Version]$contract.minimumCoreVersion) { throw 'Update the repack before installing these HD textures.' }
$owned=@{}
foreach ($entry in $contract.files) {
    if ($entry.path -notmatch '^Levels/[A-Za-z0-9_]+/[A-Za-z0-9_]+\.pak$' -or $owned.ContainsKey($entry.path)) { throw 'Invalid or duplicate texture archive.' }
    $owned[$entry.path]=$entry
}
foreach ($shared in $contract.shared) {
    if (-not $owned.ContainsKey($shared.path) -or $shared.packagePath -notmatch '^Mods/GraphicsOverhaul/packages/[a-f0-9]{16}/Levels/[A-Za-z0-9_]+/[A-Za-z0-9_]+\.pak$') { throw 'Invalid shared graphics archive.' }
}
$state=if(Test-Path -LiteralPath $statePath){Read-Json $statePath}else{$null}
if (($Action -eq 'Ensure' -or $Action -eq 'Verify') -and (-not $state -or -not $state.enabled)) { 'Standard world textures are active.';return }
if ($state -and $state.enabled -and $state.assetVersion -ne $contract.assetVersion) { throw 'This HD pack is incompatible with the current core update. Restore standard textures or install the matching HD pack.' }
if ($Action -eq 'Disable' -and (-not $state -or -not $state.enabled)) { 'Standard textures are already active.';return }
if ($Action -eq 'Enable' -or $Action -eq 'Disable') { Assert-Closed }
$stamps=@{};if($state -and $state.stamps){foreach($property in $state.stamps.PSObject.Properties){$stamps[$property.Name]=$property.Value}}
$actual=@{}
function Verify-Current([string]$Relative,$Entry,[bool]$AllowStandard,[bool]$Fast) {
    $path=Within $client $Relative;$stamp=File-Stamp $path;$saved=$stamps[$Relative]
    if($Fast -and $saved -and $saved.bytes -eq $stamp.bytes -and $saved.ticks -eq $stamp.ticks){$hash=$Entry.hdSha256}
    else{$hash=Hash $path}
    if($hash -ne $Entry.hdSha256 -and (-not $AllowStandard -or $hash -ne $Entry.standardSha256)){throw "Texture archive differs from this pack: $Relative. No textures were changed."}
    $actual[$Relative]=$hash
}
foreach($entry in $contract.files){Verify-Current $entry.path $entry ($Action -eq 'Enable') ($Action -eq 'Ensure')}
foreach($shared in $contract.shared){Verify-Current $shared.packagePath $owned[$shared.path] ($Action -eq 'Enable') ($Action -eq 'Ensure')}
$graphicsPath=Within $client $contract.graphicsManifest
$graphics=Read-Json $graphicsPath
if($graphics.packageId -ne $contract.graphicsPackageId){throw 'The graphics package does not match this HD pack.'}
foreach($shared in $contract.shared){
    $matches=@($graphics.files | Where-Object {$_.path -eq $shared.path})
    if($matches.Count -ne 1 -or $matches[0].original -ne $shared.standardEntry.original -or ($matches[0].staged -ne $owned[$shared.path].standardSha256 -and $matches[0].staged -ne $owned[$shared.path].hdSha256)){throw 'Shared graphics data changed. Install a compatible HD pack before proceeding.'}
}
if($Action -eq 'Verify'){'HD texture archives and shared graphics data verified.';return}
$targetHD=$Action -ne 'Disable';$repair=$false
for($i=0;$i -lt $graphics.files.Count;$i++){
    $shared=@($contract.shared | Where-Object {$_.path -eq $graphics.files[$i].path})
    if($shared.Count){
        $expected=if($targetHD){$shared[0].hdEntry}else{$shared[0].standardEntry}
        if($graphics.files[$i].staged -ne $expected.staged){$graphics.files[$i]=$expected;$repair=$true}
    }
}
$graphics.clientRoot=$client
if($Action -eq 'Ensure' -and -not $repair){'HD texture selection verified.';return}
if($repair){Assert-Closed}
$cache=if($state -and $state.cacheRoot){[string]$state.cacheRoot}elseif($StandardCacheRoot){[IO.Path]::GetFullPath($StandardCacheRoot)}else{Join-Path $mod ('standard-cache/'+$contract.assetVersion)}
if(-not [IO.Path]::IsPathRooted($cache)){$cache=Within $client $cache}
$transactionRoot=Join-Path $mod ('.transactions/'+[Guid]::NewGuid().ToString('N'))
if(-not $transactionRoot.StartsWith((Join-Path $mod '.transactions').TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid transaction folder.'}
New-Item -ItemType Directory -Path $transactionRoot -Force | Out-Null
$changed=New-Object 'System.Collections.Generic.List[object]'
$keepRecovery=$false
$documents=@{}
foreach($path in @($graphicsPath,$statePath,(Join-Path $mod 'mod.json'))){$documents[$path]=if(Test-Path -LiteralPath $path){[IO.File]::ReadAllBytes($path)}else{$null}}
function Replace-Archive([string]$Relative,[string]$Source,[string]$Desired,[string]$AssetPath) {
    $target=Within $client $Relative
    if($actual[$Relative] -eq $Desired){return}
    $restore=Within $cache $AssetPath
    if($actual[$Relative] -ne $owned[$AssetPath].standardSha256){$restore=Within $transactionRoot ('before/'+$Relative);New-Item -ItemType Directory -Path (Split-Path -Parent $restore) -Force | Out-Null;Copy-Item -LiteralPath $target -Destination $restore; if((Hash $restore) -ne $actual[$Relative]){throw 'Rollback snapshot failed.'}}
    $changed.Add(@{target=$target;source=$restore;sha256=$actual[$Relative]})
    $temporary=$target+'.hd-new'
    try{Copy-Item -LiteralPath $Source -Destination $temporary -Force;if((Hash $temporary) -ne $Desired){throw 'Texture copy verification failed.'};Move-Item -LiteralPath $temporary -Destination $target -Force}
    finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}}
    $actual[$Relative]=$Desired
}
try {
    if($Action -eq 'Enable'){
        if(-not $ArchivePath){throw 'Select the downloaded HD ZIP first.'}
        'Checking the downloaded HD pack...'
        if((Hash $ArchivePath) -ne $contract.archiveSha256){throw 'The HD ZIP checksum does not match. Download the complete matching pack.'}
        $reserve=[int64]$contract.hdBytes*2+[int64]$contract.standardBytes+536870912
        $drive=New-Object IO.DriveInfo ([IO.Path]::GetPathRoot($client));if($drive.AvailableFreeSpace -lt $reserve){throw ('Free at least '+[Math]::Ceiling($reserve/1GB)+' GiB on the game drive before installing HD textures.')}
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip=[IO.Compression.ZipFile]::OpenRead($ArchivePath)
        try {
            $header=$zip.GetEntry('hd-pack-manifest.json');if(-not $header){throw 'This is not an Aetherfall HD texture pack.'}
            $reader=New-Object IO.StreamReader($header.Open());try{$metadata=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
            if($metadata.assetVersion -ne $contract.assetVersion -or $metadata.id -ne $contract.id){throw 'HD pack version mismatch.'}
            $prepared=0
            foreach($entry in $contract.files){
                $member=$zip.GetEntry('payload/'+$entry.path);if(-not $member -or $member.Length -ne $entry.hdBytes){throw 'HD ZIP is missing a required texture archive.'}
                $output=Within $transactionRoot ('payload/'+$entry.path);New-Item -ItemType Directory -Path (Split-Path -Parent $output) -Force | Out-Null
                $input=$member.Open();$stream=[IO.File]::Create($output);try{$input.CopyTo($stream)}finally{$stream.Dispose();$input.Dispose()}
                if((Hash $output) -ne $entry.hdSha256){throw 'HD texture checksum failed.'}
                $prepared++;"Preparing HD textures: $prepared/$($contract.files.Count)"
            }
        } finally {$zip.Dispose()}
        foreach($entry in $contract.files){
            $baseline=Within $cache $entry.path
            if(-not (Test-Path -LiteralPath $baseline)){
                if($actual[$entry.path] -ne $entry.standardSha256){throw 'Standard texture recovery is unavailable for this existing HD installation.'}
                New-Item -ItemType Directory -Path (Split-Path -Parent $baseline) -Force | Out-Null;Copy-Item -LiteralPath (Within $client $entry.path) -Destination $baseline
            }
            if((Hash $baseline) -ne $entry.standardSha256){throw 'Standard texture cache checksum failed.'}
        }
    }
    if($Action -eq 'Disable'){
        foreach($entry in $contract.files){if((Hash (Within $cache $entry.path)) -ne $entry.standardSha256){throw 'Standard texture recovery checksum failed. No textures changed.'}}
    }
    if($Action -eq 'Enable' -or $Action -eq 'Disable'){
        $count=0
        foreach($entry in $contract.files){
            $source=if($targetHD){Within $transactionRoot ('payload/'+$entry.path)}else{Within $cache $entry.path};$expected=if($targetHD){$entry.hdSha256}else{$entry.standardSha256}
            Replace-Archive $entry.path $source $expected $entry.path;$count++;Write-Progress 'Aetherfall world textures' $entry.path -PercentComplete ([int](100*$count/$contract.files.Count))
            if($targetHD){"Installing HD textures: $count/$($contract.files.Count)"}else{"Restoring standard textures: $count/$($contract.files.Count)"}
        }
        foreach($shared in $contract.shared){$entry=$owned[$shared.path];$source=Within $client $entry.path;$expected=if($targetHD){$entry.hdSha256}else{$entry.standardSha256};Replace-Archive $shared.packagePath $source $expected $entry.path}
    }
    if($repair){[IO.File]::WriteAllBytes($graphicsPath,(Json-Bytes $graphics))}
    $saved=@{}
    foreach($relative in $actual.Keys){$saved[$relative]=File-Stamp (Within $client $relative)}
    $cacheValue=if($cache.StartsWith($client.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){$cache.Substring($client.Length+1)}else{$cache}
    $archiveValue=if($Action -eq 'Enable'){[IO.Path]::GetFullPath($ArchivePath)}elseif($state -and $state.archivePath){[string]$state.archivePath}else{''}
    $newState=@{schema=1;assetVersion=$contract.assetVersion;enabled=$targetHD;cacheRoot=$cacheValue;stamps=$saved;archivePath=$archiveValue}
    [IO.File]::WriteAllBytes($statePath,(Json-Bytes $newState))
    $info=Join-Path $mod 'mod.json'
    if($targetHD){[IO.File]::WriteAllBytes($info,(Json-Bytes @{Title='HD world textures';Description='Optional 2x world textures. Switch them in Aetherfall Hub under Mods & tools.';Listed=$true;Files=@()}))}
    elseif(Test-Path -LiteralPath $info){Remove-Item -LiteralPath $info -Force}
} catch {
    $failure=$_
    try {
        for($i=$changed.Count-1;$i -ge 0;$i--){$item=$changed[$i];Copy-Item -LiteralPath $item.source -Destination $item.target -Force;if((Hash $item.target) -ne $item.sha256){throw 'Texture rollback checksum failed.'}}
        foreach($path in $documents.Keys){if($null -ne $documents[$path]){[IO.File]::WriteAllBytes($path,$documents[$path])}elseif(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path -Force}}
    } catch {
        $keepRecovery=$true
        throw "Texture rollback needs recovery. Keep this folder and report the error: $transactionRoot. $($_.Exception.Message)"
    }
    throw $failure
} finally {
    if(-not $keepRecovery -and $transactionRoot.StartsWith((Join-Path $mod '.transactions').TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $transactionRoot)){Remove-Item -LiteralPath $transactionRoot -Recurse -Force}
    Write-Progress 'Aetherfall world textures' 'Complete' -Completed
}
if($targetHD){'HD world textures enabled.'}else{'Standard world textures restored.'}
