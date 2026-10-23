"""Stage native Find Group (Shift+V) title dragging without changing binaries."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
from codec import binary_xml, encode_binary_xml, read_pak
from prepare_client import rebuild, parts
from graphics_compat import owned_renderer_backup
from movable_windows import patch_layout

NAME = 'party_match_dialog.xml'
TYPE = 'dlg_party_match'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def archive(path, localized):
    name = 'ui/game/' + NAME if localized else NAME
    raw = path.read_bytes()
    with read_pak(path) as source:
        infos = {i.filename: i for i in source.infolist()}
        if len(infos) != len(source.infolist()):
            raise ValueError('Duplicate UI archive entry')
        root = binary_xml(source.read(name))
        if root.get('name') != 'party_match_dialog' or root.get('type') != TYPE:
            raise ValueError('Unexpected Find Group layout')
        expected = copy.deepcopy(root)
        expected.attrib.pop('align_type', None)
        flags = [s for s in expected.get('flag', '').split(';') if s and s not in ('movable', 'not_movable')]
        expected.set('flag', ';'.join(flags + ['movable']))
        payload = encode_binary_xml(patch_layout(root, TYPE))
        if ET.tostring(binary_xml(payload)) != ET.tostring(expected):
            raise ValueError('Unexpected Find Group widget change')
        updated, _ = rebuild(raw, {name: payload}, infos)
    before, _, _, version = parts(raw)
    after, _, _, updated_version = parts(updated)
    if version != updated_version or list(before) != list(after):
        raise ValueError('UI archive structure changed')
    for entry in before:
        if entry != name and before[entry][0] != after[entry][0]:
            raise ValueError('Unrelated compressed UI member changed: ' + entry)
    return updated


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external output folder')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('Original client only')
    state = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text(encoding='utf-8-sig'))
    package = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
    if state.get('restoredAt') or state['files'] != package['files']:
        raise ValueError('Active Graphics menu records disagree')
    baseline = owned_renderer_backup(client, state['backupRoot'])
    skin = json.loads((client / 'Mods/AetherfallUI/manifest.json').read_text(encoding='utf-8-sig'))
    for name, record in skin['archives'].items():
        if name.lower() in ('data/ui/game/game.pak', 'l10n/enu/data/data.pak'):
            if any(n.lower().endswith(NAME) for n in record['entries']):
                raise ValueError('Find Group is now skin-owned; update its state payloads together')
    files = []
    def stage(relative, data):
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        original = client / relative
        files.append(dict(path=relative, original=sha(original) if original.exists() else None, installed=sha(target)))
    affected = ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']
    for rel in affected:
        record = next(r for r in state['files'] if r['path'] == rel)
        saved = baseline / rel
        if (sha(client / rel) != record['installed'] or sha(saved) != record['original'] or
                sha(client / 'Mods/GraphicsMenu/package' / rel) != record['installed']):
            raise ValueError('Graphics active/package/restore hashes disagree: ' + rel)
        localized = rel.startswith('L10N/')
        active, restore = archive(client / rel, localized), archive(saved, localized)
        stage(rel, active)
        stage('Mods/GraphicsMenu/package/' + rel, active)
        stage(baseline.relative_to(client).as_posix() + '/' + rel, restore)
        record.update(installed=hashlib.sha256(active).hexdigest(), original=hashlib.sha256(restore).hexdigest())
    package['files'] = state['files']
    stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    inventory = json.loads((client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    for records in inventory['features'].values():
        for record in records:
            if record.get('path') in affected:
                target = output / record['path']
                record.update(installedSha256=sha(target), bytes=target.stat().st_size)
    inventory['features']['LfgWindowMovement'] = [dict(path=rel, installedSha256=sha(output / rel),
        bytes=(output / rel).stat().st_size) for rel in affected]
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    preserved = ['bin64/Game.dll', 'bin64/AetherfallCore.dll', 'bin64/AionLootRoll.dll',
        'Plugin/RelicCalc/RelicCalc.pak', 'Plugin/RelicCalc/RelicCalc.pak.sig', 'Addon.key', 'Pub.key',
        'bin32/bin32.pak', 'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak', 'Data/func_pet/func_pet.pak.sig',
        'bin64/AionGraphicsMenu.dll', 'bin64/CrySystem.dll', 'Mods/Renderer/installed.json',
        'Mods/AetherfallUI/manifest.json', 'Mods/AetherfallUI/installed.json']
    manifest = dict(feature='lfg-window-movement', clientRoot=str(client), files=files,
        sourceHashes={str(p): sha(p) for p in [Path(__file__), HERE / 'movable_windows.py']},
        preservedFiles=[dict(path=n, sha256=sha(client / n)) for n in preserved if (client / n).is_file()],
        gameplayAcceptance='Pending owner: Shift+V title drag, /moveui Save/Cancel, close/reopen and relog')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(files), 'files; only Find Group movement/alignment changed; live client untouched.')


if __name__ == '__main__':
    main()
