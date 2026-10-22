"""Stage exact normal outputs and install a single original-only component with closure/hash guards."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / 'target-deploy/game-server'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest(path):
    return sha(path.read_bytes()) if path.exists() else None


def outside_records(data, tag, key, ids):
    pattern = rb'<' + tag.encode() + rb'\s+' + key.encode() + rb'="(?:' + b'|'.join(str(i).encode() for i in ids) + rb')"[^>]*>.*?</' + tag.encode() + rb'>'
    data = re.sub(pattern, b'', data, flags=re.S)
    data = re.sub(rb'<!--.*?-->', b'', data, flags=re.S)
    return re.sub(rb'>\s+<', b'><', data).strip()


def prepare_server(build, output):
    assert not output.exists() and not output.is_relative_to(ROOT)
    subprocess.run(['python', str(ROOT / 'tools/build-manifest.py'), 'verify', '--build', str(build)], check=True)
    normal = json.loads((build / 'build-manifest.json').read_text())
    catalog = json.loads((Path(__file__).parent / 'catalog.json').read_text())
    goods = ['data/static_data/goodslists/' + p.name for p in (ROOT / 'game-server/data/static_data/goodslists').glob('goodslists*.xml')]
    files = goods + ['data/static_data/npc_trade_list.xml', 'data/static_data/npcs/npc_templates.xml',
        'data/static_data/items/item_templates.xml', 'data/static_data/quest_data/quest_data.xml',
        'data/static_data/quest_script_data/ceramium_restoration.xml',
        'data/static_data/spawns/Instances/300510000_Tiamat_Stronghold.xml',
        'data/static_data/spawns/Npcs/Custom/Ceramium_Exchangers.xml', 'libs/game-server-4.8-SNAPSHOT.jar']
    manifest = {'feature': 'npc-restoration', 'component': 'server', 'build': str(build), 'originalOnly': True,
                'installed': False, 'gameplayVerified': False, 'files': [], 'preserved': {}}
    output.mkdir(parents=True)
    with zipfile.ZipFile(build / 'game-server/game-server.zip') as archive:
        for relative in files:
            data = archive.read('game-server/' + relative)
            live = SERVER / relative
            if relative.endswith('.jar'):
                with zipfile.ZipFile(live) as old, zipfile.ZipFile(build / 'game-server/game-server-4.8-SNAPSHOT.jar') as new:
                    a = {n: old.read(n) for n in old.namelist() if not n.startswith('META-INF/')}
                    b = {n: new.read(n) for n in new.namelist() if not n.startswith('META-INF/')}
                    changed = sorted(n for n in a.keys() & b.keys() if a[n] != b[n])
                    added, removed = sorted(b.keys() - a.keys()), sorted(a.keys() - b.keys())
                    allowed = re.compile(r'com/aionemu/gameserver/(?:network/aion/clientpackets/CM_DIALOG_SELECT|network/aion/serverpackets/SM_TRADELIST|services/(?:DialogService|TradeService|RestoredNpcService))(?:\$[^/]+)?\.class$')
                    assert all(allowed.fullmatch(n) for n in changed), 'Unrelated server class changes: ' + str(changed)
                    helper = 'com/aionemu/gameserver/services/RestoredNpcService.class'
                    assert set(added) <= {helper} and helper in b and not removed
                    manifest['jarDiff'] = {'changed': changed, 'added': added, 'removed': removed, 'otherPayloadsPreserved': len(a) - len(changed)}
            elif live.exists():
                if relative in goods:
                    tag, key, ids = 'list', 'id', catalog['stock']
                elif relative.endswith('npc_trade_list.xml'):
                    tag, key, ids = 'tradelist_template', 'npc_id', catalog['merchants']
                elif relative.endswith('npc_templates.xml'):
                    tag, key, ids = 'npc_template', 'npc_id', ['800371', '800372', '800417', '801494', '801495']
                elif relative.endswith('item_templates.xml'):
                    tag, key, ids = 'item_template', 'id', [i['id'] for i in catalog['stock']['60011']['items']]
                elif relative.endswith('quest_data.xml'):
                    tag, key, ids = 'quest', 'id', catalog['quests']
                else:
                    tag, key, ids = 'spawn', 'npc_id', ['800371', '800372', '800417']
                assert outside_records(data, tag, key, ids) == outside_records(live.read_bytes(), tag, key, ids), 'Unrelated runtime data differs: ' + relative
            else:
                assert relative.endswith(('ceramium_restoration.xml', 'Ceramium_Exchangers.xml'))
            if not relative.endswith('.jar'):
                assert sha(data) == normal['inputs']['game-server/' + relative]
            if digest(live) == sha(data):
                manifest['preserved'][relative] = sha(data)
                continue
            staged = output / relative
            staged.parent.mkdir(parents=True, exist_ok=True)
            staged.write_bytes(data)
            manifest['files'].append({'path': relative, 'destination': str(live), 'before': digest(live), 'after': sha(data)})
        for name in archive.namelist():
            if not name.startswith('game-server/libs/') or not name.endswith('.jar') or name.endswith('/game-server-4.8-SNAPSHOT.jar'):
                continue
            relative = name.removeprefix('game-server/')
            current = SERVER / relative
            if name.endswith('/commons-4.8-SNAPSHOT.jar'):
                import io
                with zipfile.ZipFile(current) as old, zipfile.ZipFile(io.BytesIO(archive.read(name))) as new:
                    payload = lambda z: {n: z.read(n) for n in z.namelist() if not n.startswith('META-INF/')}
                    assert payload(old) == payload(new), 'Commons payload changed; dependency reconciliation required'
            else:
                assert digest(current) == sha(archive.read(name)), 'Dependency upgrade requires reconciliation: ' + relative
            manifest['preserved'][relative] = digest(current)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Prepared exact normal server output; unrelated XML records, JAR payloads and dependencies preserved.')


def closure(component):
    query = "Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -match '^(javaw?|aion|aion_x64|aion_x86)(\\.exe|\\.bin)$' } | Select-Object Name,ExecutablePath,CommandLine | ConvertTo-Json -Compress"
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', query], capture_output=True, text=True, check=True)
    processes = json.loads(result.stdout) if result.stdout.strip() else []
    if isinstance(processes, dict):
        processes = [processes]
    for p in processes:
        name, command = p['Name'].lower(), p.get('CommandLine')
        if component == 'client' and name.startswith('aion'):
            raise RuntimeError('Close the Aion client normally before installation')
        if component == 'server' and name.startswith('java') and (not command or 'com.aionemu.gameserver.GameServer' in command):
            raise RuntimeError('GameServer is running or its closure cannot be verified')
    return processes


def install(stage, backup, component, client=None):
    assert not backup.exists() and not backup.is_relative_to(ROOT) and (client is None or not backup.is_relative_to(client))
    m = json.loads((stage / 'manifest.json').read_text())
    assert m['originalOnly'] and not m['installed']
    if component == 'server':
        assert m['component'] == 'server'
        subprocess.run(['python', str(ROOT / 'tools/build-manifest.py'), 'verify', '--build', m['build']], check=True)
    else:
        assert client is not None and m['feature'] == 'npc-restoration'
        for name, expected in m['sourceHashes'].items():
            assert digest(Path(__file__).parent / name) == expected
    for e in m['files']:
        destination = Path(e['destination']) if component == 'server' else client / e['path']
        assert digest(destination) == e['before'], 'Destination changed since preparation: ' + str(destination)
        assert digest(stage / e['path']) == e['after'], 'Prepared output changed'
    if component == 'server':
        for name, expected in m['preserved'].items():
            assert digest(SERVER / name) == expected, 'Runtime dependency changed'
    m['processSnapshot'] = closure(component)
    backup.mkdir(parents=True)
    for e in m['files']:
        destination = Path(e['destination']) if component == 'server' else client / e['path']
        if e['before'] is not None:
            target = backup / e['path']
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(destination, target)
            assert digest(target) == e['before']
    (backup / 'rollback-manifest.json').write_text(json.dumps(m, indent=2) + '\n')
    for e in m['files']:
        destination = Path(e['destination']) if component == 'server' else client / e['path']
        assert digest(destination) == e['before'], 'Destination changed during backup'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(stage / e['path'], destination)
        assert digest(destination) == e['after']
    m.update(installed=True, installedAt=datetime.now(timezone.utc).isoformat(), component=component,
             backup=str(backup), lifecyclePerformed=False)
    receipt = backup / 'receipt.json'
    receipt.write_text(json.dumps(m, indent=2) + '\n')
    (stage / 'manifest.json').write_text(json.dumps(m, indent=2) + '\n')
    inventory_path = ROOT / 'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text(encoding='utf-8-sig'))
    if component == 'client':
        by_path = {e['path'].casefold(): e for e in inventory['clientFiles']}
        for e in m['files']:
            if e['path'].casefold() in by_path:
                by_path[e['path'].casefold()]['sha256'] = e['after']
    key = 'lastNpcRestoration' + component.title() + 'Delivery'
    inventory[key] = {'installedAt': m['installedAt'], 'receipt': str(receipt), 'originalOnly': True,
        'files': len(m['files']), 'npcIds': [800371, 800372, 800417, 801494, 801495, 801537, 801538],
        'verification': 'Normal builder output, focused XML schemas/contracts, native record/compressed-member preservation, copied-file hashes',
        'gameplayAcceptance': 'Owner verifies shops, placement, Drakan admission, faction stock and both repeatable exchanges in game.'}
    inventory.setdefault(component + 'Receipts', []).append(str(receipt))
    inventory_path.write_text(json.dumps(inventory, indent=2) + '\n')
    print('Installed original ' + component + '; ' + str(len(m['files'])) + ' hashes verified. Receipt: ' + str(receipt))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='mode', required=True)
    prep = sub.add_parser('prepare-server')
    prep.add_argument('--build', type=Path, required=True)
    prep.add_argument('--output', type=Path, required=True)
    ins = sub.add_parser('install')
    ins.add_argument('--component', choices=['server', 'client'], required=True)
    ins.add_argument('--stage', type=Path, required=True)
    ins.add_argument('--backup', type=Path, required=True)
    ins.add_argument('--client', type=Path)
    a = p.parse_args()
    if a.mode == 'prepare-server':
        prepare_server(a.build.resolve(strict=True), a.output.resolve())
    else:
        install(a.stage.resolve(strict=True), a.backup.resolve(), a.component, a.client.resolve(strict=True) if a.client else None)
