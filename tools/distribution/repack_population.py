"""Fresh repack population initialization; never import personal bot data."""
from pathlib import Path

SOURCE = Path(__file__).resolve().parent/'repack'


def payloads(package, schema=None, bootstrap=None):
    schema_path = package/'Server/database/schema/aetherfall_gs.sql'
    original = schema_path.read_text(encoding='utf-8-sig')
    schema = original if schema is None else schema
    incoming = schema
    bootstrap = (package/'Server/database/bootstrap.sql').read_text(encoding='utf-8-sig') if bootstrap is None else bootstrap
    seed = (SOURCE/'playerbot_population_initial.sql').read_text()
    marker = '-- Initial native state for a new repack with no legacy population to import.'
    if marker not in schema:
        schema += '\n'+seed
    # Fresh bootstrap embeds exactly one GS schema before switching databases.
    if bootstrap.count(incoming) != 1:
        raise ValueError('Fresh bootstrap must contain exactly one matching GS schema')
    bootstrap = bootstrap.replace(incoming, schema, 1)
    start = (package/'Control/Start-Servers.ps1').read_text(encoding='utf-8-sig')
    if "'Initialize-PlayerbotPopulation.ps1'" not in start:
        if start.count('Assert-OwnDatabase\n') != 1:
            raise ValueError('Expected the native repack server-start ownership check')
        start = start.replace('Assert-OwnDatabase\n', "Assert-OwnDatabase\n& (Join-Path $PSScriptRoot 'Initialize-PlayerbotPopulation.ps1')\n", 1)
    return {
        'Server/database/schema/aetherfall_gs.sql': schema.encode(),
        'Server/database/bootstrap.sql': bootstrap.encode(),
        'Server/database/schema/playerbot_population_initial.sql': seed.encode(),
        'Control/Initialize-PlayerbotPopulation.ps1': (SOURCE/'Initialize-PlayerbotPopulation.ps1').read_bytes(),
        'Control/Start-Servers.ps1': start.encode(),
    }
