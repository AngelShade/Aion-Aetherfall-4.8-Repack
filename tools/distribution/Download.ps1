param([ValidateSet('Auto','Torrent','Http')][string]$Mode, [string]$Destination)
$ErrorActionPreference = 'Stop'
$root = if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'release-manifest.json')) { $PSScriptRoot } else { (Get-Item -LiteralPath $PSScriptRoot).Parent.FullName }
$lock = $null
function Read-Json([string]$Path) { return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json) }
function Assert-Https([string]$Value) {
    $uri = $null
    if ($Value -match '\s' -or -not [Uri]::TryCreate($Value,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -ne 'https' -or $uri.UserInfo) {
        throw 'A mirror must be a direct HTTPS URL without credentials or line breaks.'
    }
    return $Value
}
function Local-Path([string]$Base,[string]$Value) {
    if ([IO.Path]::IsPathRooted($Value)) { return [IO.Path]::GetFullPath($Value) }
    return [IO.Path]::GetFullPath((Join-Path $Base $Value))
}
function Check-Space([string]$Path,[Int64]$Needed) {
    $drive = New-Object IO.DriveInfo ([IO.Path]::GetPathRoot($Path))
    if ($drive.DriveFormat -eq 'FAT32') { throw 'Use NTFS or exFAT: extracted game files may exceed FAT32 limits.' }
    if ($drive.AvailableFreeSpace -lt $Needed) {
        throw ('Not enough space on {0}: allow {1:N1} GiB more for download and extraction.' -f $drive.Name,($Needed/1GB))
    }
}
try {
    $lock = [IO.File]::Open((Join-Path $root '.download.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $manifest = Read-Json (Join-Path $root 'release-manifest.json')
    $settings = Read-Json (Join-Path $root 'settings.json')
    if ($manifest.schema -ne 1 -or $manifest.torrentFile -notmatch '^Aetherfall-[0-9.]+\.torrent$' -or $manifest.installFolder -notmatch '^Aetherfall-[0-9.]+$') {
        throw 'Unsupported release manifest.'
    }
    foreach ($file in $manifest.files) {
        if ($file.name -notmatch '^Aetherfall-[0-9.]+\.7z(?:\.[0-9]{3,})?$' -or $file.bytes -le 0 -or $file.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid release file record.' }
    }
    $aria = Join-Path $root 'bin/aria2c.exe'
    $sevenzip = Join-Path $root 'bin/7za.exe'
    $tools = Read-Json (Join-Path $root 'bin/SHA256.json')
    foreach ($tool in @($aria,$sevenzip)) {
        $expected = $tools.([IO.Path]::GetFileName($tool))
        if ((Get-FileHash -LiteralPath $tool -Algorithm SHA256).Hash -ne $expected) { throw ('Downloader tool checksum mismatch: '+$tool) }
    }
    $torrent = Join-Path $root $manifest.torrentFile
    if ((Get-FileHash -LiteralPath $torrent -Algorithm SHA256).Hash -ne $manifest.torrentSha256) { throw 'Torrent checksum mismatch.' }
    $mirrors = Read-Json (Join-Path $root 'mirrors.json')
    $common = @('--no-conf=true','--continue=true','--auto-file-renaming=false','--allow-overwrite=false',
        '--file-allocation=none','--check-integrity=true','--max-connection-per-server=16','--split=16',
        '--min-split-size=4M','--max-concurrent-downloads=3','--summary-interval=1','--console-log-level=warn',
        '--max-tries=8','--retry-wait=5','--connect-timeout=20','--timeout=60','--check-certificate=true')
    if ($settings.MirrorsUrl) {
        $url = Assert-Https ([string]$settings.MirrorsUrl)
        $live = Join-Path $root 'mirrors-live.json'
        & $aria '--no-conf=true' '--auto-file-renaming=false' '--allow-overwrite=true' '--follow-torrent=false' '--follow-metalink=false' '--max-tries=2' '--timeout=20' '--max-file-not-found=1' ('--dir='+$root) '--out=mirrors-live.json' $url
        if ($LASTEXITCODE -eq 0) {
            if ((Get-Item -LiteralPath $live).Length -gt 1MB) { throw 'Live mirror list is unexpectedly large.' }
            $candidate = Read-Json $live
            if ($candidate.schema -ne 1 -or $candidate.release -ne $manifest.version) { throw 'Live mirror list targets a different release.' }
            $mirrors = $candidate
        } else { Write-Host 'Live mirror list is unavailable; using bundled mirrors and torrent.' }
    }
    if ($mirrors.schema -ne 1 -or $mirrors.release -ne $manifest.version) { throw 'Mirror list targets a different release.' }
    $webseeds = @($mirrors.WebSeeds | ForEach-Object { Assert-Https ([string]$_) })
    $httpComplete = $true
    foreach ($file in $manifest.files) {
        $urls = @($mirrors.Files.($file.name))
        if ($urls.Count -eq 0 -or -not $urls[0]) { $httpComplete = $false }
        foreach ($url in $urls) { if ($url) { $null = Assert-Https ([string]$url) } }
    }
    if (-not $Mode) { $Mode = [string]$settings.Mode }
    $requestedMode = $Mode
    if ($Mode -eq 'Auto') {
        if ($httpComplete) { $Mode = 'Http' }
        else { $Mode = 'Torrent' }
    }
    if ($Mode -notin @('Torrent','Http')) { throw 'Choose Auto, Torrent or Http in settings.json.' }
    if ($Mode -eq 'Http' -and -not $httpComplete) { throw 'Add direct HTTPS URLs for every archive part before selecting HTTP mode.' }
    $downloadRoot = Local-Path $root ([string]$settings.DownloadDirectory)
    $installParent = if ($Destination) { Local-Path $root $Destination } else { Local-Path $root ([string]$settings.InstallDirectory) }
    $parts = if ($manifest.torrentFolder) { Join-Path $downloadRoot $manifest.torrentFolder } else { $downloadRoot }
    $install = Join-Path $installParent $manifest.installFolder
    $missing = [Int64]0
    foreach ($file in $manifest.files) {
        $path = Join-Path $parts $file.name
        $existing = if (Test-Path -LiteralPath $path) { (Get-Item -LiteralPath $path).Length } else { 0 }
        $missing += [Math]::Max([Int64]0,([Int64]$file.bytes-$existing))
    }
    New-Item -ItemType Directory -Path $downloadRoot,$installParent -Force | Out-Null
    if ([IO.Path]::GetPathRoot($downloadRoot) -eq [IO.Path]::GetPathRoot($installParent)) {
        Check-Space $downloadRoot ($missing+[Int64]$manifest.installedBytes+2GB)
    } else {
        Check-Space $downloadRoot ($missing+1GB)
        Check-Space $installParent ([Int64]$manifest.installedBytes+1GB)
    }
    Write-Host ('AION AETHERFALL '+$manifest.version) -ForegroundColor Cyan
    Write-Host 'Closing this window keeps downloaded data. Start it again to resume.'
    if (@($mirrors.SharePages).Count -gt 0) {
        Write-Host ('Backup browser mirrors: ' + (@($mirrors.SharePages) -join ' | ')) -ForegroundColor Yellow
    }
    Write-Host ('Download method: '+$Mode)
    if ($Mode -eq 'Http') {
        New-Item -ItemType Directory -Path $parts -Force | Out-Null
        $lines = New-Object 'Collections.Generic.List[string]'
        foreach ($file in $manifest.files) {
            $lines.Add((@($mirrors.Files.($file.name)) -join "`t"))
            $lines.Add('  out='+$file.name)
            $lines.Add('  checksum=sha-256='+$file.sha256)
        }
        $inputFile = Join-Path $root 'http-downloads.txt'
        [IO.File]::WriteAllLines($inputFile,$lines,(New-Object Text.UTF8Encoding($false)))
        $arguments = $common + @('--follow-torrent=false','--follow-metalink=false',('--dir='+$parts),('--input-file='+$inputFile))
        & $aria @arguments
        if ($LASTEXITCODE -ne 0 -and $requestedMode -eq 'Auto') {
            Write-Host 'HTTP mirror busy or interrupted; switching automatically to BitTorrent P2P...' -ForegroundColor Yellow
            $Mode = 'Torrent'
        }
    }
    if ($Mode -eq 'Torrent') {
        Write-Host 'Torrent peers upload and download with each other. Keep an initial seed online.'
        $dhtFile = Join-Path $root 'dht.dat'
        if (-not (Test-Path -LiteralPath $dhtFile)) {
            $dhtBytes = New-Object byte[] 56
            $dhtBytes[0] = 0xA1; $dhtBytes[1] = 0xA2; $dhtBytes[2] = 0x02; $dhtBytes[7] = 0x03
            $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            $nodeId = New-Object byte[] 20; $rng.GetBytes($nodeId); $rng.Dispose()
            [Array]::Copy($nodeId, 0, $dhtBytes, 24, 20)
            [IO.File]::WriteAllBytes($dhtFile, $dhtBytes)
        }
        $arguments = $common + @('--enable-dht=true',('--dht-file-path='+$dhtFile),'--dht-entry-point=router.bittorrent.com:6881',
            '--enable-peer-exchange=true','--bt-enable-lpd=true','--seed-time=0','--max-overall-upload-limit=1M',
            ('--dir='+$downloadRoot),('--torrent-file='+$torrent)) + $webseeds
        & $aria @arguments
    }
    if ($LASTEXITCODE -ne 0) { throw 'Download paused or failed. Start this downloader again to resume.' }
    $count = 0
    foreach ($file in $manifest.files) {
        $path = Join-Path $parts $file.name
        if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -ne $file.bytes -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256) { throw ('Archive checksum mismatch: '+$file.name) }
        $count++; Write-Progress -Activity 'Checking downloaded archive parts' -Status ($count.ToString()+' / '+$manifest.files.Count) -PercentComplete (100*$count/$manifest.files.Count)
    }
    Write-Progress -Activity 'Checking downloaded archive parts' -Completed
    $marker = Join-Path $install '.aetherfall-installing.json'
    if (Test-Path -LiteralPath $install) {
        if (-not (Test-Path -LiteralPath $marker)) { throw ('The install folder already exists. Choose a new InstallDirectory in settings.json: '+$install) }
        if ((Read-Json $marker).infoHash -ne $manifest.infoHash) { throw 'The unfinished installation belongs to a different release.' }
    } else {
        New-Item -ItemType Directory -Path $install | Out-Null
        @{infoHash=$manifest.infoHash} | ConvertTo-Json | Set-Content -LiteralPath $marker -Encoding UTF8
    }
    Write-Host 'All archive parts verified. Extracting the repack...'
    & $sevenzip 'x' (Join-Path $parts $manifest.files[0].name) ('-o'+$install) '-y' '-aoa'
    if ($LASTEXITCODE -ne 0) { throw 'Extraction stopped. Keep the parts and start this downloader again.' }
    if ((Get-FileHash -LiteralPath (Join-Path $install 'FILES.json') -Algorithm SHA256).Hash -ne $manifest.packageFilesIndexSha256) { throw 'Extracted release inventory differs from the packed release.' }
    $updHelper = if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'Update-Repack.ps1')) { Join-Path $PSScriptRoot 'Update-Repack.ps1' } else { Join-Path $root 'bin/Update-Repack.ps1' }
    if (Test-Path -LiteralPath $updHelper) {
        Copy-Item -LiteralPath $updHelper -Destination (Join-Path $install 'Control/Update-Repack.ps1') -Force
    }
    Remove-Item -LiteralPath $marker
    Write-Host ('Ready: '+$install) -ForegroundColor Green
    Write-Host 'Open README.txt there for database, server and graphical launcher instructions.'
    Write-Host 'Archive parts are kept for resuming or seeding. You may remove them manually after installation.'
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally { if ($lock) { $lock.Dispose() } }
