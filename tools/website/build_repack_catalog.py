"""Build the website's static instance atlas and validate its native references."""
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET


def build_catalog(repo: Path, app: Path):
    native = repo / 'game-server'
    references = json.loads((repo / 'website/resources/content/repack-reference.json').read_text(encoding='utf-8'))
    inputs = ['data/static_data/world_maps.xml', 'data/static_data/instance_cooltimes/instance_cooltimes.xml',
              'src/com/aionemu/gameserver/model/PlayerClass.java',
              'src/com/aionemu/gameserver/services/playerbot/PlayerBotRaidPolicy.java']
    classes = (native / inputs[2]).read_text(encoding='utf-8')
    for entry in references['classes']:
        match = re.search(r'\b'+re.escape(entry['key'])+r'\((\d+),\s*\d+,\s*([A-Z_]+),', classes)
        if not match or int(match[1]) != entry['id'] or match[2] != entry['family_key']:
            raise ValueError('Class reference no longer matches native source: '+entry['key'])
    if len({entry['key'] for entry in references['classes']}) != 11:
        raise ValueError('Expected eleven unique Aion 4.8 advanced classes')
    for entry in references['commands']:
        source = entry['source']
        if source not in inputs:
            inputs.append(source)
        if entry['evidence'] not in (native / source).read_text(encoding='utf-8'):
            raise ValueError('Command reference no longer matches native help: '+entry['syntax'])
    policy = (native / inputs[3]).read_text(encoding='utf-8')
    if '24 : 6' not in policy:
        raise ValueError('Recheck native party capacity before shipping the party planner')
    maps = {int(row.get('id')): row for row in ET.parse(native / inputs[0]).getroot()}
    instances = []
    omitted = []
    for row in ET.parse(native / inputs[1]).getroot().findall('instance_cooltime'):
        world = int(row.get('worldId'))
        if world not in maps:
            raise ValueError('Instance is missing a native map: '+str(world))
        if not maps[world].get('name'):
            omitted.append({'id': world, 'reason': 'No named destination in native map data'})
            continue
        factions = {}
        for race, suffix in [('ELYOS', 'light'), ('ASMODIANS', 'dark')]:
            if row.get('race') not in ['PC_ALL', race]:
                continue
            def number(name):
                value = row.findtext(name+'_'+suffix)
                return int(value) if value is not None and int(value) > 0 else None
            factions[race] = {'min_level': number('enter_min_level'), 'max_level': number('enter_max_level'),
                              'members': number('max_member')}
        instances.append({'id': world, 'name': maps[world].get('name'), 'factions': factions})
    if len({row['id'] for row in instances}) != len(instances):
        raise ValueError('Duplicate instance IDs require explicit handling')
    result = {'schema': 1, 'version': 'Aion 4.8 NA', 'instances': sorted(instances, key=lambda row: row['name'].casefold()),
              'omitted': omitted,
              'inputs': {name: hashlib.sha256((native/name).read_bytes()).hexdigest() for name in inputs},
              'referenceSha256': hashlib.sha256((repo/'website/resources/content/repack-reference.json').read_bytes()).hexdigest()}
    (app/'resources/content/repack-atlas.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    return result
