"""Bundle reserved broker identities and their guarded pre-start repair for fresh repacks."""
from pathlib import Path

SOURCE = Path(__file__).resolve().parent / 'repack'


def fresh_database_verification(start_database):
    """Accept only the six bundled system sellers, with no personal characters."""
    old = "(Invoke-DatabaseQuery 'SELECT COUNT(*) FROM aetherfall_gs.players') -ne '0'"
    sellers = "((id BETWEEN 999901 AND 999903 AND race='ELYOS' AND account_id=-900001 AND account_name='aetherfall_market_elyos') OR (id BETWEEN 999904 AND 999906 AND race='ASMODIANS' AND account_id=-900002 AND account_name='aetherfall_market_asmodians')) AND deletion_date IS NULL"
    new = '(Invoke-DatabaseQuery "SELECT COUNT(*) FROM aetherfall_gs.players WHERE ' + sellers + '") -ne \'6\' -or\n        (Invoke-DatabaseQuery \'SELECT COUNT(*) FROM aetherfall_gs.players\') -ne \'6\''
    if new in start_database:
        return start_database
    if start_database.count(old) != 1:
        raise ValueError('Unexpected fresh database verification; preserve it for review')
    return start_database.replace(old, new).replace('freshCharacters=0', 'freshCharacters=0; systemBrokerSellers=6')


def payloads(package, schema=None, bootstrap=None, start=None):
    incoming = (package / 'Server/database/schema/aetherfall_gs.sql').read_text(encoding='utf-8-sig') if schema is None else schema
    schema = incoming
    bootstrap = (package / 'Server/database/bootstrap.sql').read_text(encoding='utf-8-sig') if bootstrap is None else bootstrap
    start = (package / 'Control/Start-Servers.ps1').read_text(encoding='utf-8-sig') if start is None else start
    seed = (SOURCE / 'broker_sellers_initial.sql').read_text(encoding='utf-8')
    marker = '-- Reserved offline broker sellers for the standalone Aetherfall repack.'
    if marker in schema:
        if schema.count(marker) != 1:
            raise ValueError('Expected one owned broker seed block at the end of the GS schema')
        schema = schema[:schema.index(marker)] + seed
    else:
        schema += '\n' + seed
    if bootstrap.count(incoming) != 1:
        raise ValueError('Fresh bootstrap must contain exactly one matching GS schema')
    bootstrap = bootstrap.replace(incoming, schema, 1)
    call = "& (Join-Path $PSScriptRoot 'Initialize-BrokerSellers.ps1')"
    if call not in start:
        previous = "& (Join-Path $PSScriptRoot 'Initialize-PlayerbotPopulation.ps1')"
        if start.count(previous) != 1:
            raise ValueError('Expected native population bootstrap before market initialization')
        start = start.replace(previous, previous + '\n' + call, 1)
    return {
        'Server/database/schema/aetherfall_gs.sql': schema.encode('utf-8'),
        'Server/database/bootstrap.sql': bootstrap.encode('utf-8'),
        'Server/database/schema/broker_sellers_initial.sql': seed.encode('utf-8'),
        'Control/Initialize-BrokerSellers.ps1': (SOURCE / 'Initialize-BrokerSellers.ps1').read_bytes(),
        'Control/Start-Servers.ps1': start.encode('utf-8'),
        'Control/Start-Database.ps1': fresh_database_verification((package / 'Control/Start-Database.ps1').read_text(encoding='utf-8-sig')).encode('utf-8'),
    }
