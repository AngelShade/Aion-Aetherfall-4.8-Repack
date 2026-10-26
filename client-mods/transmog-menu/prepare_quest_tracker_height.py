"""Stage the quest tracker's layout height so the tracker can be dragged vertically.

The stock quest_indicator_dialog frame is 960 high, the whole 1280x960 layout
height. The game derives the live height from that frame inside its own SetRect,
so at UI scale 1.125 on a 1080 screen the tracker is exactly screen height and
the game's on-screen clamp holds it at y=0. Its content ends at 830: the main
quest list (grid_container 0..495) and the temporary-quest list (grid_temp_quest
495..830). Only the root frame height changes; every widget and other archive
member stays byte-identical. Active, Graphics package and restore copies of both
the base and English archives are staged together, as for the Find Group window.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
NAME = 'quest_indicator_dialog.xml'
HEIGHT = 830  # bottom of grid_temp_quest (495 + 335)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def archive(path, localized, codec, rebuild, parts):
    name = 'ui/game/' + NAME if localized else NAME
    raw = path.read_bytes()
    with codec.read_pak(path) as source:
        infos = {i.filename: i for i in source.infolist()}
        if len(infos) != len(source.infolist()):
            raise ValueError('Duplicate UI archive entry')
        root = codec.binary_xml(source.read(name))
        frame = [int(v) for v in root.get('frame', '').split(',')]
        children = {c.get('name'): [int(v) for v in c.get('frame', '').split(',')] for c in root if c.tag == 'Widget'}
        if (root.get('name') != 'quest_indicator_dialog' or root.get('type') != 'dlg_quest_indicator'
                or len(frame) != 4 or frame[3] not in (960, HEIGHT)
                or children.get('grid_container') != [0, 0, 230, 495] or children.get('grid_temp_quest') != [0, 495, 230, 335]):
            raise ValueError('Unexpected quest tracker layout: ' + str(path))
        expected = copy.deepcopy(root)
        expected.set('frame', ','.join(str(v) for v in frame[:3] + [HEIGHT]))
        payload = codec.encode_binary_xml(expected)
        if ET.tostring(codec.binary_xml(payload)) != ET.tostring(expected):
            raise ValueError('Quest tracker layout did not round-trip')
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
    parser.add_argument('--stage', type=Path, required=True, help='existing aetherfall-core stage to add the layout to')
    parser.add_argument('--client-mods', type=Path, default=HERE.parent)
    args = parser.parse_args()
    mods = args.client_mods.resolve(strict=True)
    for folder in ('expanded-warehouse', 'bundle-opening', 'transmog-menu'):
        sys.path.insert(0, str(mods / folder))
    import codec
    from prepare_client import rebuild, parts
    from graphics_compat import owned_renderer_backup
    client, stage = args.client.resolve(strict=True), args.stage.resolve(strict=True)
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('Original client only')
    manifest = json.loads((stage / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('feature') != 'aetherfall-core':
        raise ValueError('Expected an aetherfall-core stage')
    state = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text(encoding='utf-8-sig'))
    package = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
    if state.get('restoredAt') or state['files'] != package['files']:
        raise ValueError('Active Graphics menu records disagree')
    baseline = owned_renderer_backup(client, state['backupRoot'])
    skin = json.loads((client / 'Mods/AetherfallUI/manifest.json').read_text(encoding='utf-8-sig'))
    for name, record in skin['archives'].items():
        if any(n.lower().endswith(NAME) for n in record['entries']):
            raise ValueError('The quest tracker is skin-owned; update its interface payloads together')
    staged = {f['path']: f for f in manifest['files']}

    def stage_file(relative, data):
        target = stage / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        live = client / relative
        record = staged.get(relative) or dict(path=relative, original=sha(live) if live.exists() else None)
        record['installed'] = sha(target)
        if relative not in staged:
            manifest['files'].append(record); staged[relative] = record

    affected = ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']
    for rel in affected:
        record = next(r for r in state['files'] if r['path'] == rel)
        saved = baseline / rel
        if (sha(client / rel) != record['installed'] or sha(saved) != record['original'] or
                sha(client / 'Mods/GraphicsMenu/package' / rel) != record['installed']):
            raise ValueError('Graphics active/package/restore hashes disagree: ' + rel)
        localized = rel.startswith('L10N/')
        active = archive(client / rel, localized, codec, rebuild, parts)
        restore = archive(saved, localized, codec, rebuild, parts)
        stage_file(rel, active)
        stage_file('Mods/GraphicsMenu/package/' + rel, active)
        stage_file(baseline.relative_to(client).as_posix() + '/' + rel, restore)
        record.update(installed=hashlib.sha256(active).hexdigest(), original=hashlib.sha256(restore).hexdigest())
    package['files'] = state['files']
    stage_file('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage_file('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    # Build on the inventory the Core stage already prepared, if any.
    inventory_path = stage / 'Mods/InstalledMods.json'
    inventory = json.loads((inventory_path if inventory_path.exists() else client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    for records in inventory['features'].values():
        for record in records:
            if record.get('path') in affected:
                target = stage / record['path']
                record.update(installedSha256=sha(target), bytes=target.stat().st_size)
    inventory['features']['QuestTrackerHeight'] = [dict(path=rel, installedSha256=sha(stage / rel),
        bytes=(stage / rel).stat().st_size) for rel in affected]
    stage_file('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    manifest.setdefault('layoutChanges', []).append(dict(feature='quest-tracker-height', member=NAME, height=HEIGHT,
        source=sha(Path(__file__))))
    (stage / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Added the quest tracker layout height to', stage, '; live client untouched.')


if __name__ == '__main__':
    main()
