$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Database-Common.ps1')
Assert-OwnDatabase

# A valid imported population belongs to the native repository. Never reset it.
$state = Invoke-DatabaseQuery 'SELECT COUNT(*) FROM aetherfall_gs.playerbot_population_state'
if ([int]$state -ne 0) {
    $valid = Invoke-DatabaseQuery "SELECT COUNT(*) FROM aetherfall_gs.playerbot_population_state WHERE id=1 AND version=1 AND revision>=0 AND import_digest REGEXP '^[0-9a-f]{64}$'"
    if ([int]$state -ne 1 -or [int]$valid -ne 1) { throw 'Population state is invalid. Existing bot data was preserved; an offline migration is required.' }
    return
}

$legacyFolder = Join-Path $RepackRoot 'Server/game-server/config/playerbots'
foreach ($legacy in @('world-bots.properties','world-server-population.json','world-population-events.json','world-population.json')) {
    if (Test-Path -LiteralPath (Join-Path $legacyFolder $legacy)) { throw 'Legacy population files exist. They need a native offline import; no empty population was created.' }
}
$connections = Invoke-DatabaseQuery "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER='aetherfall'"
if ([int]$connections -gt 0) { throw 'Close GameServer, LoginServer and ChatServer normally, then start them again to initialize the missing repack population state. Keep the database running.' }
$tables = @('playerbot_population_accounts','playerbot_population_members','playerbot_world_residents','playerbot_population_events','playerbot_roster')
foreach ($table in $tables) {
    if ([int](Invoke-DatabaseQuery ('SELECT COUNT(*) FROM aetherfall_gs.'+$table)) -ne 0) { throw ('Existing bot data in '+$table+' needs a native offline migration; it was preserved.') }
}
$nativeBots = Invoke-DatabaseQuery 'SELECT COUNT(*) FROM aetherfall_gs.players WHERE account_id BETWEEN -1023999 AND -1000000'
if ([int]$nativeBots -ne 0) { throw 'Native world-bot characters already exist; no empty population was created.' }

$sqlPath = Join-Path $DatabaseRoot 'schema/playerbot_population_initial.sql'
$sql = [IO.File]::ReadAllText($sqlPath)
$result = Invoke-DatabaseQuery ('USE aetherfall_gs; START TRANSACTION; '+$sql+' SELECT ROW_COUNT(); COMMIT;')
if ([int]$result -ne 1) { throw 'Population initialization was not applied; existing data was preserved.' }
$verified = Invoke-DatabaseQuery "SELECT COUNT(*) FROM aetherfall_gs.playerbot_population_state WHERE id=1 AND version=1 AND target=0 AND target_until=0 AND revision=0 AND import_digest='ce010d7da5cf425bb206e2912423e8d0d688911b422e138a07b408d5925c8bc1'"
if ([int]$verified -ne 1) { throw 'Population initialization could not be verified. Keep game servers stopped.' }
'Initialized the missing fresh-repack population state. World bots can start normally.'
