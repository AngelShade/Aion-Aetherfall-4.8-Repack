"""Build and stage the native stacked Need/Greed/Pass interface; never starts Aion."""
import argparse
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, binary_xml, encode_binary_xml, encode_pak
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
from prepare_client import rebuild, parts
from patch_binary import patch

PANEL_HEIGHT = 128  # 18px title + 99px content + native frame clearance


def sha(data):
    return hashlib.sha256(data).hexdigest()


def layout(data, installed=False):
    root = binary_xml(data)
    existing = root.find("Widget[@name='greed']")
    if root.get('type') != 'dlg_loot_roll' or (existing is not None) != installed:
        raise ValueError('Unexpected native loot-roll layout')
    flags = [f for f in root.get('flag', '').split(';') if f and f not in ('movable', 'not_movable')]
    if 'title' not in flags:
        raise ValueError('Expected the native loot-roll title bar')
    root.set('flag', ';'.join(flags + ['movable']))
    if installed:
        if root.get('frame') not in ('99,190,334,108', f'99,190,334,{PANEL_HEIGHT}'):
            raise ValueError('Unexpected installed loot-roll dimensions')
        if existing.get('frame') != '117,49,100,24' or root.find("Widget[@name='your_choice']") is None:
            raise ValueError('Unexpected installed loot-roll controls')
        # A layout repair changes only the outer height and movement flag. Text, buttons,
        # localization, client widget metadata and item tooltip behavior survive.
        root.set('frame', f'99,190,334,{PANEL_HEIGHT}')
        return encode_binary_xml(root)
    root.attrib.update(frame=f'99,190,334,{PANEL_HEIGHT}', title_height='18', title_frame='7,1,290,16', title_offset='0,0')
    frames = {'lootitem': '8,4,318,44', 'remain': '8,76,318,16',
              'remain_gauge': '8,94,318,5', 'roll': '8,49,100,24', 'abandon': '226,49,100,24'}
    for name, frame in frames.items():
        widget = root.find("Widget[@name='" + name + "']")
        if widget is None:
            raise ValueError('Missing native roll widget: ' + name)
        widget.set('frame', frame)
    ET.SubElement(root, 'Widget', name='greed', type='button', preset='v5_button',
                  frame='117,49,100,24', font='ui_default', text='STR_AETHERFALL_LOOT_GREED',
                  tooltip='STR_AETHERFALL_LOOT_GREED_HELP')
    root.find("Widget[@name='roll']").set('tooltip', 'STR_AETHERFALL_LOOT_NEED_HELP')
    root.find("Widget[@name='abandon']").set('tooltip', 'STR_AETHERFALL_LOOT_PASS_HELP')
    ET.SubElement(root, 'Widget', name='your_choice', type='static', font='ui_default',
                  frame='8,51,318,22', flag='transparent', text_color='0.88,0.78,0.56,1.0')
    return encode_binary_xml(root)


def strings(data):
    root = binary_xml(data)
    entries = [('GREED', 'Greed'), ('NEED_HELP', 'Need: roll for an item your character can use. Need rolls take priority over Greed.'),
               ('GREED_HELP', 'Greed: roll if nobody needs this item.'), ('PASS_HELP', 'Pass: leave this item for the other eligible players.')]
    for index, (suffix, body) in enumerate(entries):
        name = 'STR_AETHERFALL_LOOT_' + suffix
        if any(e.findtext('name') == name or e.findtext('id') == str(990100020 + index) for e in root):
            raise ValueError('Loot string collision')
        row = ET.SubElement(root, 'string')
        for key, value in [('id', str(990100020 + index)), ('name', name), ('body', body)]:
            ET.SubElement(row, key).text = value
    return encode_binary_xml(root)


def archive(path, localized, layout_only=False):
    changes = {'ui/game/loot_roll_dialog.xml' if localized else 'loot_roll_dialog.xml':
               lambda data: layout(data, installed=layout_only)}
    if localized and not layout_only:
        changes['strings/client_strings_ui.xml'] = strings
    if layout_only:
        original = path.read_bytes()
        with read_pak(path) as source:
            infos = {entry.filename: entry for entry in source.infolist()}
            if not set(changes).issubset(infos):
                raise ValueError('Native loot resources missing')
            replacements = {name: transform(source.read(name)) for name, transform in changes.items()}
            updated, _ = rebuild(original, replacements, infos)
        # Preserve the exact compressed bytes of every other archive member.
        before, _, _, version = parts(original)
        after, _, _, updated_version = parts(updated)
        if version != updated_version or list(before) != list(after):
            raise ValueError('Loot archive structure changed')
        for name in before:
            if name not in changes and before[name][0] != after[name][0]:
                raise ValueError('Unrelated loot archive member changed: ' + name)
        return updated
    output = io.BytesIO()
    with read_pak(path) as source, zipfile.ZipFile(output, 'w') as target:
        if not set(changes).issubset(source.namelist()):
            raise ValueError('Native loot resources missing')
        for entry in source.infolist():
            data = source.read(entry)
            target.writestr(copy.copy(entry), changes[entry.filename](data) if entry.filename in changes else data)
    return encode_pak(output.getvalue())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--layout-only', action='store_true', help='Repair installed loot layouts/bridge without modifying Game.dll or cursor recovery')
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external output directory')
    output.mkdir(parents=True)
    files = {}

    def stage(relative, data):
        if relative in files and files[relative] != data:
            raise ValueError('Conflicting staged payload')
        files[relative] = data
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    if not args.layout_only:
        stage('bin64/Game.dll', patch((client / 'bin64/Game.dll').read_bytes())[0])
    for relative in ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']:
        stage(relative, archive(client / relative, relative.startswith('L10N/'), args.layout_only))
    work = output.parent / (output.name + '-compile')
    work.mkdir()
    (output / 'bin64').mkdir(exist_ok=True)
    vcvars = Path(os.environ.get('ProgramFiles(x86)', r'C:\Program Files (x86)')) / 'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    script = work / 'build.cmd'
    script.write_text(f'@echo off\ncall "{vcvars}" >nul\ncl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"{work / "loot.obj"}" "{HERE / "loot_roll.cpp"}" /link /OUT:"{output / "bin64/AionLootRoll.dll"}" /IMPLIB:"{work / "loot.lib"}" user32.lib\n')
    subprocess.run('cmd.exe /d /s /c \"\"' + str(script) + '\"\"', check=True)
    stage('bin64/AionLootRoll.dll', (output / 'bin64/AionLootRoll.dll').read_bytes())

    # Keep the new native feature in both active packages and their existing
    # graphics/cursor restore baselines. No old package can remove it on restore.
    state = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text(encoding='utf-8-sig'))
    manifest = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
    if state['files'] != manifest['files']:
        raise ValueError('Graphics recovery metadata differs')
    for entry in state['files']:
        relative = entry['path']
        if relative not in files:
            continue
        backup_root = Path(state['backupRoot'])
        if not backup_root.is_relative_to(client) and args.layout_only:
            # The original client may have been moved intact. Rebase only the
            # known Mods/Backups suffix and verify its recorded payload hashes.
            suffix = backup_root.parts
            matches = [i for i in range(len(suffix)-1) if suffix[i:i+2] == ('Mods', 'Backups')]
            if len(matches) != 1:
                raise ValueError('Recovery root is outside the selected client')
            backup_root = client.joinpath(*suffix[matches[0]:])
        if not backup_root.resolve().is_relative_to(client):
            raise ValueError('Recovery root is outside the selected client')
        if args.layout_only:
            state['backupRoot'] = str(backup_root)
            state['clientRoot'] = str(client)
        baseline = backup_root / relative
        if sha(baseline.read_bytes()) != entry['original'] or sha((client / relative).read_bytes()) != entry['installed']:
            raise ValueError('Graphics recovery baseline differs: ' + relative)
        data = patch(baseline.read_bytes())[0] if relative.endswith('.dll') else archive(baseline, relative.startswith('L10N/'), args.layout_only)
        stage(baseline.relative_to(client).as_posix(), data)
        entry.update(original=sha(data), installed=sha(files[relative]))
        stage('Mods/GraphicsMenu/package/' + relative, files[relative])
    manifest['files'] = state['files']
    stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(manifest, indent=2).encode())
    if not args.layout_only:
        cursor = json.loads((client / 'Mods/Renderer/installed.json').read_text(encoding='utf-8-sig'))
        entry = next(e for e in cursor['nativeCursorPatch']['files'] if e['path'] == 'bin64/Game.dll')
        baseline = Path(entry['backupPath'])
        if sha(baseline.read_bytes()) != entry['original'] or sha((client / entry['path']).read_bytes()) != entry['installed']:
            raise ValueError('Cursor baseline differs')
        data = patch(baseline.read_bytes())[0]
        stage(baseline.relative_to(client).as_posix(), data)
        entry.update(original=sha(data), installed=sha(files['bin64/Game.dll']))
        stage('Mods/Renderer/installed.json', json.dumps(cursor, indent=2).encode())
        stage('Mods/LootRoll/mod.json', json.dumps(dict(Title='Loot rolls', Description='Compact stacked Need / Greed / Pass rolls with native item icons, tooltips and countdowns.', Listed=True, Files=['bin64/AionLootRoll.dll']), indent=2).encode())
    record = dict(feature='native-loot-roll-layout' if args.layout_only else 'native-loot-roll', clientRoot=str(client),
                  files=[dict(path=rel, original=sha((client / rel).read_bytes()) if (client / rel).exists() else None, installed=sha(data)) for rel, data in files.items()],
                  sourceHashes={str(p): sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file()}, testsRun=False, gameStarted=False,
                  preserved=[dict(path='bin64/Game.dll', sha256=sha((client / 'bin64/Game.dll').read_bytes()))] if args.layout_only else [])
    (output / 'manifest.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print('Built/staged', len(files), 'files; live client unchanged.')


if __name__ == '__main__':
    main()
