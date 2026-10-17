"""Stage a native Wardrobe shortcut immediately right of the installed Journey button."""
import argparse
import copy
import hashlib
import io
import json
import subprocess
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, binary_xml, encode_binary_xml, encode_pak

BUTTON = 'wardrobe_menu_button'
TOOLTIP = 'STR_PRIVATE_WARDROBE_HUD'
NATIVE_HUD_WIDTHS = {'v5_hud_start_s1': 165, 'v5_hud_start_s2': 323}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def library(data):
    tree = binary_xml(data)
    native = tree.find(".//Skin[@name='v5_npc_func_skinchange']")
    category = tree.find(".//Category[@name='version5']")
    if native is None or category is None:
        raise ValueError('Native Wardrobe artwork or version5 skin category missing')
    existing = tree.find(".//Preset[@name='" + BUTTON + "']")
    if existing is None:
        preset = ET.SubElement(category, 'Preset', name=BUTTON, type='button')
        for state in ('up', 'over', 'down'):
            ET.SubElement(preset, 'SkinRef', main_state='0', name=native.get('name'), sub_state=state)
    return encode_binary_xml(tree)


def hud(data):
    tree = binary_xml(data)
    journey = tree.find("Widget[@name='journey_menu_button']")
    if tree.get('name') != 'start_dialog' or journey is None:
        raise ValueError('Expected the installed Journey shortcut')
    x, y, width, height = map(int, journey.get('frame').split(','))
    button = tree.find("Widget[@name='" + BUTTON + "']")
    if button is None:
        button = ET.SubElement(tree, 'Widget', name=BUTTON, type='button')
    button.attrib.update(frame=f'{x + 40},{y},{width},{height}', flag='visible', preset=BUTTON, tooltip=TOOLTIP)
    frame = list(map(int, tree.get('frame').split(',')))
    # The root preset includes the painted flight-control ring. Resizing the
    # window stretches that artwork without moving the native flight control.
    # Keep the native canvas; Wardrobe uses the open space beside Journey.
    native_width = NATIVE_HUD_WIDTHS.get(tree.get('preset'))
    if native_width is None or frame[2] not in (native_width, max(native_width, x + 40 + width)):
        raise ValueError('Unexpected native HUD width')
    frame[2] = native_width
    if y < 0 or y + height > frame[3]:
        raise ValueError('Wardrobe shortcut outside HUD height')
    tree.set('frame', ','.join(map(str, frame)))
    return encode_binary_xml(tree)


def strings(data):
    tree = binary_xml(data)
    existing = [e for e in tree if e.findtext('name') == TOOLTIP]
    if len(existing) > 1:
        raise ValueError('Duplicate Wardrobe tooltip')
    if existing:
        existing[0].find('body').text = 'Wardrobe'
    else:
        if any(e.findtext('id') == '990100005' for e in tree):
            raise ValueError('Wardrobe tooltip ID is already used')
        string = ET.SubElement(tree, 'string')
        ET.SubElement(string, 'id').text = '990100005'
        ET.SubElement(string, 'name').text = TOOLTIP
        ET.SubElement(string, 'body').text = 'Wardrobe'
    return encode_binary_xml(tree)


def archive(path, transforms):
    stream = io.BytesIO()
    with read_pak(path) as source:
        replacements = {name: function(source.read(name)) for name, function in transforms.items()}
        with zipfile.ZipFile(stream, 'w') as output:
            for entry in source.infolist():
                output.writestr(copy.copy(entry), replacements.get(entry.filename, source.read(entry.filename)))
    return bytes(encode_pak(stream.getvalue()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a new external staging folder')
    output.mkdir(parents=True)
    records = []

    def stage(relative, data):
        source, destination = client / relative, output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        records.append(dict(path=relative, before=sha(source.read_bytes()), after=sha(data)))
        print('Prepared:', relative, flush=True)

    vcvars = Path(r'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat')
    native = output / 'native'
    native.mkdir()
    source = HERE.parent / 'market-shortcut/market_shortcut.cpp'
    command = native / 'build.cmd'
    product = native / 'AionMarketShortcut.dll'
    command.write_text('@echo off\ncall "' + str(vcvars) + '" >nul\nif errorlevel 1 exit /b %errorlevel%\n' +
        'cl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"' + str(native / 'shortcut.obj') + '" "' + str(source) +
        '" /link /OUT:"' + str(product) + '" /IMPLIB:"' + str(native / 'shortcut.lib') + '"\n')
    build = subprocess.run(f'cmd.exe /d /s /c ""{command}""', capture_output=True, text=True)
    (native / 'build.log').write_text(build.stdout + build.stderr)
    if build.returncode:
        raise RuntimeError('Native build failed: ' + str(native / 'build.log'))
    stage('bin64/AionMarketShortcut.dll', product.read_bytes())
    stage('Data/ui/ui.pak', archive(client / 'Data/ui/ui.pak', {'UI_Preload.xml': library}))
    for style in (1, 2):
        relative = f'Data/ui/game_hud_s{style}/game_hud_s{style}.pak'
        stage(relative, archive(client / relative, {'start_dialog.xml': hud}))
    localized = {'ui/ui_preload.xml': library, 'strings/client_strings_ui.xml': strings,
                 'ui/game_hud_s1/start_dialog.xml': hud, 'ui/game_hud_s2/start_dialog.xml': hud}
    stage('L10N/enu/Data/data.pak', archive(client / 'L10N/enu/Data/data.pak', localized))

    graphics = client / 'Mods/GraphicsMenu'
    state = json.loads((graphics / 'installed.json').read_text(encoding='utf-8-sig'))
    package = json.loads((graphics / 'package/manifest.json').read_text(encoding='utf-8-sig'))
    if state['files'] != package['files']:
        raise ValueError('Graphics recovery metadata differs')
    locale = next(e for e in state['files'] if e['path'].casefold() == 'l10n/enu/data/data.pak')
    baseline = (Path(state['backupRoot']) / locale['path']).resolve(strict=True)
    if not baseline.is_relative_to(client) or sha(baseline.read_bytes()) != locale['original']:
        raise ValueError('Graphics restore baseline differs')
    active = next(e for e in records if e['path'] == 'L10N/enu/Data/data.pak')
    if active['before'] != locale['installed']:
        raise ValueError('Current locale differs from its recovery record')
    stage(baseline.relative_to(client).as_posix(), archive(baseline, localized))
    locale.update(original=records[-1]['after'], installed=active['after'])
    package['files'] = copy.deepcopy(state['files'])
    for relative, value in [('installed.json', state), ('package/manifest.json', package)]:
        stage((graphics / relative).relative_to(client).as_posix(), json.dumps(value, indent=2).encode())
    stage((graphics / 'package/L10N/enu/Data/data.pak').relative_to(client).as_posix(),
          (output / 'L10N/enu/Data/data.pak').read_bytes())
    preserved = ['bin64/Game.dll', 'bin64/crysystem.dll', 'bin64/AionIconBridge.dll',
                 'bin64/AionGraphicsMenu.dll', 'bin64/AionRememberLogin.dll', 'bin64/AionSpeechBubbles.dll',
                 'bin64/XRenderD3D9.dll', 'Plugin/RelicCalc/RelicCalc.pak', 'Addon.key', 'Pub.key',
                 'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig', 'Plugin/RelicCalc/RelicCalc.pak.sig']
    manifest = dict(feature='wardrobe-hud', clientRoot=str(client), files=records,
        preserved=[dict(path=p, sha256=sha((client / p).read_bytes())) for p in preserved],
        sourceHashes={str(p): sha(p.read_bytes()) for p in [Path(__file__), source]},
        ordinaryTestsRun=False, startup=False, scope='Wardrobe shortcut immediately right of Journey; existing native icon and command')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    print('READY: native build and nine staged file copies; no installed files changed.', flush=True)


if __name__ == '__main__':
    main()
