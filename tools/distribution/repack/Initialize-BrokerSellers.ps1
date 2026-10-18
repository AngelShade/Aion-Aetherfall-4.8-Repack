$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Database-Common.ps1')
Assert-OwnDatabase

$validQuery = "SELECT COUNT(*) FROM aetherfall_gs.players WHERE deletion_date IS NULL AND ((id BETWEEN 999901 AND 999903 AND race='ELYOS') OR (id BETWEEN 999904 AND 999906 AND race='ASMODIANS'))"
# Existing working sellers, including older customized sellers, are never replaced.
if ([int](Invoke-DatabaseQuery $validQuery) -eq 6) { return }

$connections = Invoke-DatabaseQuery "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER='aetherfall'"
if ([int]$connections -gt 0) {
    throw 'Close GameServer, LoginServer and ChatServer normally before initializing missing broker sellers. Keep the repack database running.'
}
$occupied = [int](Invoke-DatabaseQuery 'SELECT COUNT(*) FROM aetherfall_gs.players WHERE id BETWEEN 999901 AND 999906')
$conflicts = Invoke-DatabaseQuery "SELECT COUNT(*) FROM aetherfall_gs.players WHERE (id BETWEEN 999901 AND 999903 AND (race<>'ELYOS' OR account_id<>-900001 OR account_name<>'aetherfall_market_elyos' OR deletion_date IS NOT NULL)) OR (id BETWEEN 999904 AND 999906 AND (race<>'ASMODIANS' OR account_id<>-900002 OR account_name<>'aetherfall_market_asmodians' OR deletion_date IS NOT NULL))"
if ([int]$conflicts -gt 0) { throw 'Reserved broker IDs contain conflicting characters. They were preserved; resolve this conflict before initializing market sellers.' }

$sql = [IO.File]::ReadAllText((Join-Path $DatabaseRoot 'schema/broker_sellers_initial.sql'))
try {
    $null = Invoke-DatabaseQuery ('USE aetherfall_gs; ' + $sql)
} catch {
    throw ('Broker seller initialization failed; conflicting identities are never overwritten. ' + $_.Exception.Message)
}
if ([int](Invoke-DatabaseQuery $validQuery) -ne 6) { throw 'Broker seller initialization could not be verified. Keep the servers closed.' }
('Initialized ' + (6 - $occupied) + ' missing reserved broker sellers. Existing accounts, characters and market listings were retained.')
