. (Join-Path $PSScriptRoot 'Database-Common.ps1')
if (-not (Test-Path -LiteralPath (Join-Path $RepackRoot 'REPACK.json'))) { throw 'Missing repack identity.' }
if (Test-DatabasePort) {
    Assert-OwnDatabase
    if (-not (Test-Path -LiteralPath (Join-Path $DatabaseRoot 'ready.json'))) { throw 'This repack database is running, but its initial setup is incomplete. See setup-errors.log; no reset was attempted.' }
    'This repack database is already running.'
    exit 0
}
# An interrupted first setup is moved aside, never deleted, so the next start can finish the job.
function Move-UnfinishedDatabase([string]$Path) {
    $aside = (Split-Path -Leaf $Path) + '.failed-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    Move-Item -LiteralPath $Path -Destination (Join-Path $DatabaseRoot $aside)
    "Kept the unfinished database setup as Server/database/$aside; starting a new setup."
}
if (-not (Test-Path -LiteralPath (Join-Path $DatabaseData 'mysql'))) {
    if ((Test-Path -LiteralPath $DatabaseData) -and @(Get-ChildItem -LiteralPath $DatabaseData -Force).Count -gt 0) {
        # ready.json proves setup finished once: this folder may hold real accounts and characters.
        if (Test-Path -LiteralPath (Join-Path $DatabaseRoot 'ready.json')) { throw 'The database finished setup earlier, but its mysql system folder is now missing. Nothing was changed. Restore Server/database/data from a backup, and check whether antivirus removed files.' }
        Move-UnfinishedDatabase $DatabaseData
    }
    # Setup builds in data.init and only becomes data once it succeeds.
    $building = Join-Path $DatabaseRoot 'data.init'
    if (Test-Path -LiteralPath $building) { Move-UnfinishedDatabase $building }
    $installer = Join-Path $DatabaseRoot 'runtime/bin/mariadb-install-db.exe'
    $installLog = Join-Path $DatabaseRoot 'install-errors.log'
    $install = Start-Process -FilePath $installer -ArgumentList @(('"--datadir='+$building+'"'),('"--password='+$Credentials.rootPassword+'"'),"--port=$($Credentials.port)",'--silent') -WorkingDirectory $DatabaseRoot -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $DatabaseRoot 'install.log') -RedirectStandardError $installLog
    if ($install.ExitCode -ne 0) {
        $reason = @(Get-Content -LiteralPath (Join-Path $DatabaseRoot 'install.log'),$installLog -ErrorAction SilentlyContinue | Where-Object { $_.Trim() }) | Select-Object -Last 1
        throw "Portable database initialization failed with exit code $($install.ExitCode): $reason See Server/database/install-errors.log, check antivirus and folder permissions, then start again. Existing files were preserved."
    }
    if (Test-Path -LiteralPath $DatabaseData) { Remove-Item -LiteralPath $DatabaseData }
    Move-Item -LiteralPath $building -Destination $DatabaseData
}
$base = (Join-Path $DatabaseRoot 'runtime').Replace('\','/')
$data = $DatabaseData.Replace('\','/')
$ini = "[mysqld]`r`nbasedir=$base`r`ndatadir=$data`r`nport=$($Credentials.port)`r`nbind-address=127.0.0.1`r`ncharacter-set-server=utf8mb4`r`ncollation-server=utf8mb4_general_ci`r`nmax_allowed_packet=64M`r`ninnodb_buffer_pool_size=128M`r`npid-file=$data/aetherfall.pid`r`nlog-error=$data/database-error.log`r`n"
$iniPath = Join-Path $DatabaseRoot 'my.ini'
$ini += "default-time-zone=+00:00`r`n"
Write-DatabaseFile $iniPath $ini
Write-ClientOptions
$server = Join-Path $DatabaseRoot 'runtime/bin/mysqld.exe'
$process = Start-Process -FilePath $server -ArgumentList @(('--defaults-file="'+$iniPath+'"'),'--console') -WorkingDirectory $DatabaseRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $DatabaseRoot 'console.log') -RedirectStandardError (Join-Path $DatabaseRoot 'console-error.log')
# mysqld stays hidden so closing this window can never stop it; the window only shows its log.
Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'Watch-Database.ps1')+'"'),'-DatabaseProcessId',$process.Id) -WindowStyle Normal
$deadline = [DateTime]::UtcNow.AddSeconds(90)
while (-not (Test-DatabasePort)) {
    $process.Refresh()
    if ($process.HasExited) { throw 'Database exited during startup. Read Server/database/console-error.log and data/database-error.log.' }
    if ([DateTime]::UtcNow -gt $deadline) { throw 'Database startup timed out. Its files were preserved; inspect its logs.' }
    Start-Sleep -Milliseconds 500
}
Assert-OwnDatabase
$ready = Join-Path $DatabaseRoot 'ready.json'
if (-not (Test-Path -LiteralPath $ready)) {
    $import = Start-Process -FilePath $DatabaseClient -ArgumentList @(('--defaults-extra-file="'+$ClientOptions+'"'),'--binary-mode','--default-character-set=utf8mb4') -WorkingDirectory $DatabaseRoot -WindowStyle Hidden -Wait -PassThru -RedirectStandardInput (Join-Path $DatabaseRoot 'bootstrap.sql') -RedirectStandardOutput (Join-Path $DatabaseRoot 'setup.log') -RedirectStandardError (Join-Path $DatabaseRoot 'setup-errors.log')
    if ($import.ExitCode -ne 0) { throw 'Initial schema import failed. See setup-errors.log. No database reset or deletion was attempted.' }
    if ((Invoke-DatabaseQuery 'SELECT COUNT(*) FROM aetherfall_ls.account_data') -ne '0' -or
        (Invoke-DatabaseQuery "SELECT COUNT(*) FROM aetherfall_gs.players WHERE ((id BETWEEN 999901 AND 999903 AND race='ELYOS' AND account_id=-900001 AND account_name='aetherfall_market_elyos') OR (id BETWEEN 999904 AND 999906 AND race='ASMODIANS' AND account_id=-900002 AND account_name='aetherfall_market_asmodians')) AND deletion_date IS NULL") -ne '6' -or
        (Invoke-DatabaseQuery 'SELECT COUNT(*) FROM aetherfall_gs.players') -ne '6' -or
        (Invoke-DatabaseQuery 'SELECT COUNT(*) FROM aetherfall_ls.gameservers WHERE id=1') -ne '1') {
        throw 'Fresh database verification failed; initial files were preserved.'
    }
    @{ version=1; initializedAt=(Get-Date -Format o); databaseNames=@('aetherfall_gs','aetherfall_ls','aetherfall_cs'); freshAccounts=0; freshCharacters=0; systemBrokerSellers=6 } | ConvertTo-Json | Set-Content -LiteralPath $ready -Encoding UTF8
}
"Database ready on 127.0.0.1:$($Credentials.port), PID $($process.Id). No Windows service was installed."
'You can now start the servers. Use 4 - Stop Database.cmd after closing the servers normally.'
