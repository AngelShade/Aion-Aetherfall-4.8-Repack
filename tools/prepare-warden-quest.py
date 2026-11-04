"""Prepare and validate quest 14997 against the original client. Never installs or edits client PAKs."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT/'client-mods/expanded-warehouse'))
from codec import read_pak, binary_xml


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def exercise_installer(client, out, package):
    """Run the maintained installer on a copy; every original remains read-only."""
    staged = out/'client-copy'
    graphics = json.loads((client/'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
    interface = json.loads((client/'Mods/AetherfallUI/manifest.json').read_text(encoding='utf-8-sig'))
    files = {'Aetherfall Launcher.exe', 'Data/Quest/Quest.pak'}
    files.update(entry['path'] for entry in graphics['files'])
    files.update(interface['archives'])
    files.update(interface['files'])
    for folder in ['Mods/CustomQuests', 'Mods/GraphicsMenu', 'Mods/AetherfallUI']:
        files.update(p.relative_to(client).as_posix() for p in (client/folder).rglob('*') if p.is_file())
    originals = {relative: sha(client/relative) for relative in sorted(files)}
    for relative in originals:
        target = staged/relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(client/relative, target)
        assert sha(target) == originals[relative], relative
    # Only the disposable copy's machine paths change. The installer, manifests,
    # launcher and quest sources retain their real guards and authored bytes.
    graphics_state = staged/'Mods/GraphicsMenu/installed.json'
    state = json.loads(graphics_state.read_text(encoding='utf-8-sig'))
    state['clientRoot'] = str(staged)
    graphics_state.write_text(json.dumps(state, indent=2), encoding='utf-8')
    (staged/'Mods/CustomQuests/settings.json').write_text(json.dumps({'BackupRoot': str(out/'merge-backups')}), encoding='utf-8')
    marker = dict(kind='custom-quest-installer-scratch', originalClient=str(client),
                  files={relative: sha(staged/relative) for relative in originals})
    (staged/'.aetherfall-quest-stage.json').write_text(json.dumps(marker, indent=2), encoding='utf-8')
    package_hashes = {p.name: sha(p) for p in package.iterdir() if p.is_file()}
    help_relative = 'Mods/CustomQuests/Quests/14996-HelpRequest'
    help_before = {p.name: sha(p) for p in (staged/help_relative).iterdir() if p.is_file()}
    owned_before = json.loads((staged/'Mods/CustomQuests/installed.json').read_text(encoding='utf-8-sig'))['Owned']
    command = [sys.executable, str(ROOT/'client-mods/custom-quests/add_package.py'), '--client', str(staged),
               '--package', str(package), '--backup', str(out/'installer-backup'), '--staged-client']
    result = subprocess.run(command, capture_output=True, text=True)
    (out/'installer-check.log').write_text(result.stdout+result.stderr, encoding='utf-8')
    unchanged = originals == {relative: sha(client/relative) for relative in originals}
    evidence = dict(command=command, exitCode=result.returncode, originalFilesUnchanged=unchanged,
                    originalFiles=originals, copiedClient=str(staged), originalInstalled=False)
    if result.returncode == 0:
        target = staged/'Mods/CustomQuests/Quests'/package.name
        evidence['packageUnchanged'] = package_hashes == {p.name: sha(p) for p in target.iterdir() if p.is_file()}
        evidence['helpPackageUnchanged'] = help_before == {p.name: sha(p) for p in (staged/help_relative).iterdir() if p.is_file()}
        owned_after = json.loads((staged/'Mods/CustomQuests/installed.json').read_text(encoding='utf-8-sig'))['Owned']
        evidence['existingQuestRecordsUnchanged'] = all(owned_after.get(key) == value for key, value in owned_before.items())
        evidence['existingQuestRecordsChecked'] = len(owned_before)
        receipt = json.loads((out/'installer-backup/receipt.json').read_text())
        evidence['installerChecks'] = receipt['checks']
        assert evidence['packageUnchanged'] and evidence['helpPackageUnchanged'] and evidence['existingQuestRecordsUnchanged']
    (out/'installer-check.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    if not unchanged:
        raise SystemExit('Original client changed during the staged exercise; review originalFiles in installer-check.json')
    if result.returncode:
        raise SystemExit('Staged add_package.py check failed; see '+str(out/'installer-check.log'))


def xml(data):
    return binary_xml(data) if data.startswith(b'\x80') else ET.fromstring(data)


def nearby_segments(beats):
    cells = {}
    for row in beats.read_text().splitlines():
        if not row or row.startswith('#'):
            continue
        cols = row.split('\t')
        points = [tuple(map(float, v.split(','))) for v in cols[9].split(';')]
        for a, b in zip(points, points[1:]):
            for x in range(math.floor((min(a[0], b[0])-100)/64), math.floor((max(a[0], b[0])+100)/64)+1):
                for y in range(math.floor((min(a[1], b[1])-100)/64), math.floor((max(a[1], b[1])+100)/64)+1):
                    cells.setdefault((int(cols[1]), x, y), []).append((a, b))
    return cells


def distance(p, a, b):
    d = tuple(v-u for u, v in zip(a, b))
    length = sum(v*v for v in d)
    t = max(0, min(1, sum((v-u)*w for u, v, w in zip(a, p, d))/length)) if length else 0
    return math.sqrt(sum((v-u-t*w)**2 for u, v, w in zip(a, p, d)))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--refresh-source', action='store_true', help='refresh maintained quest tracker rows from verified client cNames')
    p.add_argument('--exercise-installer', action='store_true', help='exercise maintained add_package.py on a disposable copy with real launcher and Graphics/Interface guards')
    a = p.parse_args()
    client, out = a.client.resolve(strict=True), a.output.resolve()
    if out.exists() or out.is_relative_to(ROOT) or out.is_relative_to(client):
        raise SystemExit('Use a fresh external stage')
    sources = {rel: sha(client/rel) for rel in ['Data/Npcs/Npcs.pak', 'Data/Quest/Quest.pak', 'L10N/enu/Data/data.pak']}
    original_quest = xml(read_pak(client/'Data/Quest/Quest.pak').read('quest.xml'))
    if any(n.findtext('id') == '14997' for n in original_quest):
        raise SystemExit('Quest 14997 is already installed; review that package before preparing a replacement')
    r = xml(read_pak(client/'Data/Npcs/Npcs.pak').read('client_npcs_monster.xml'))
    client_names = {int(n.findtext('id')): n.findtext('name') for n in r if n.findtext('id') and n.findtext('name')}
    cells = nearby_segments(ROOT/'game-server/config/playerbots/warden-beats.tsv')
    maps = {k[0] for k in cells}
    templates = {int(n.get('npc_id')): n for n in ET.parse(ROOT/'game-server/data/static_data/npcs/npc_templates.xml').getroot()}
    selected = set()
    for path in (ROOT/'game-server/data/static_data/spawns/Npcs').glob('*.xml'):
        if not any(path.name.startswith(str(m)+'_') for m in maps):
            continue
        for spawn_map in ET.parse(path).getroot():
            world = int(spawn_map.get('map_id', '0'))
            for spawn in spawn_map:
                ident = int(spawn.get('npc_id', '0'))
                n = templates.get(ident)
                if n is None or n.get('type') != 'MONSTER' or ident not in client_names:
                    continue
                for spot in spawn.findall('spot'):
                    point = tuple(float(spot.get(axis)) for axis in ['x', 'y', 'z'])
                    if any(distance(point, left, right) <= 100 for left, right in cells.get((world, math.floor(point[0]/64), math.floor(point[1]/64)), [])):
                        selected.add(ident)
                        break
    if not selected:
        raise SystemExit('No verified native monsters along the prepared beats')
    names = sorted({client_names[n] for n in selected})
    csv = ''.join('14997,Progress(SECTION_0==0; SECTION_1<15),,killedByUser,,1,'+n+'\n' for n in names)
    maintained = ROOT/'client-mods/custom-quests/Quests/14997-RoadBounty'
    if a.refresh_source:
        (maintained/'quest_script_monster.csv').write_text(csv, encoding='utf-8')
    elif (maintained/'quest_script_monster.csv').read_text() != csv:
        raise SystemExit('Tracker source differs from the beat/client closure; use --refresh-source after review')
    for name in ['quest.xml', 'registration.xml', 'strings.xml', 'dialog.html']:
        ET.parse(maintained/name)
    assert json.loads((maintained/'quest.json').read_text())['Id'] == 14997
    # Keep the established Help Request byte-identical in source and in the installed client.
    help_source = ROOT/'client-mods/custom-quests/Quests/14996-HelpRequest'
    help_hashes = {p.name: sha(p) for p in help_source.iterdir() if p.is_file()}
    out.mkdir(parents=True)
    package = out/'14997-RoadBounty'
    shutil.copytree(maintained, package)
    saved = ['L10N/enu/Data/data.pak', 'Data/Quest/Quest.pak', 'Mods/GraphicsMenu/package/L10N/enu/Data/data.pak',
             'Mods/CustomQuests/installed.json', 'Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json']
    targets = {rel: sha(client/rel) if (client/rel).is_file() else None for rel in saved}
    receipt = dict(client=str(client), package=str(package), nativeSources=sources,
        beatSource=sha(ROOT/'game-server/config/playerbots/warden-beats.tsv'), originals=targets,
        packageFiles={p.name: sha(p) for p in package.iterdir()}, nativeMonsterIds=sorted(selected), trackerNames=len(names),
        unchangedHelpSource=help_hashes, installed=False,
        installer='python client-mods/custom-quests/add_package.py --client "'+str(client)+'" --package "'+str(package)+'" --backup <fresh-external-backup>',
        reversal='With Aion and its launcher closed normally, restore every add_package.py backup/files byte and remove only the new 14997-RoadBounty package directory. Verify the recorded original hashes.')
    (out/'client-stage.json').write_text(json.dumps(receipt, indent=2))
    assert sources == {rel: sha(client/rel) for rel in sources}
    assert help_hashes == {p.name: sha(p) for p in help_source.iterdir() if p.is_file()}
    if a.exercise_installer:
        exercise_installer(client, out, package)
    print('Prepared only:', out, '; native tracker names:', len(names))


if __name__ == '__main__':
    main()
