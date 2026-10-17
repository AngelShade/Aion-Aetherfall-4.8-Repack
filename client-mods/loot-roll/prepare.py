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
from patch_binary import patch


def sha(data):
    return hashlib.sha256(data).hexdigest()


def layout(data):
    root = binary_xml(data)
    if root.get('type') != 'dlg_loot_roll' or root.find("Widget[@name='greed']") is not None:
        raise ValueError('Unexpected native loot-roll layout')
    root.attrib.update(frame='99,190,334,108', title_height='18', title_frame='7,1,290,16', title_offset='0,0')
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


def archive(path, localized):
    changes = {'ui/game/loot_roll_dialog.xml' if localized else 'loot_roll_dialog.xml': layout}
    if localized:
        changes['strings/client_strings_ui.xml'] = strings
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

    stage('bin64/Game.dll', patch((client / 'bin64/Game.dll').read_bytes())[0])
    for relative in ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']:
        stage(relative, archive(client / relative, relative.startswith('L10N/')))
    work = output.parent / (output.name + '-compile')
    work.mkdir()
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
        baseline = Path(state['backupRoot']) / relative
        if sha(baseline.read_bytes()) != entry['original'] or sha((client / relative).read_bytes()) != entry['installed']:
            raise ValueError('Graphics recovery baseline differs: ' + relative)
        data = patch(baseline.read_bytes())[0] if relative.endswith('.dll') else archive(baseline, relative.startswith('L10N/'))
        stage(baseline.relative_to(client).as_posix(), data)
        entry.update(original=sha(data), installed=sha(files[relative]))
        stage('Mods/GraphicsMenu/package/' + relative, files[relative])
    manifest['files'] = state['files']
    stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(manifest, indent=2).encode())
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
    record = dict(feature='native-loot-roll', clientRoot=str(client),
                  files=[dict(path=rel, original=sha((client / rel).read_bytes()) if (client / rel).exists() else None, installed=sha(data)) for rel, data in files.items()],
                  sourceHashes={str(p): sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file()}, testsRun=False, gameStarted=False)
    (output / 'manifest.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print('Built/staged', len(files), 'files; live client unchanged.')


if __name__ == '__main__':
    main()
