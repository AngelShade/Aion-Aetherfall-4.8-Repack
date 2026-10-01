"""Stage only menu icon references and archive signatures for the English client."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

from patch_plugin_key import patch_plugin_key


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_icons(root, settings):
    from fire_temple_probe import binary_xml, read_pak
    icons = [entry['icon'] for entry in settings['serverCommands']] + [settings['cashShop']['icon']]
    if len(set(icons)) != len(icons) or any(not re.fullmatch('[A-Za-z0-9_]+', icon) for icon in icons):
        raise ValueError('Menu icons must be distinct native skin names')
    with read_pak(root / 'Textures/ui/ui.pak') as textures:
        headers = {name.lower(): textures.read(name)[:128] for name in textures.namelist()
                   if name.lower().startswith('v5_common')}
    for relative, entry in [('Data/ui/ui.pak', 'UI_Preload.xml'),
                            ('L10N/enu/data/data.pak', 'ui/ui_preload.xml')]:
        with read_pak(root / relative) as archive:
            payload = archive.read(entry)
        tree = binary_xml(payload) if payload[0] == 128 else ET.fromstring(payload)
        skins = {skin.get('name'): skin for skin in tree.iter('Skin')}
        for icon in icons:
            if icon not in skins:
                raise ValueError(f'Missing native menu skin {icon} in {relative}')
            skin = skins[icon]
            texture = skin.get('texture').rsplit('/', 1)[-1] + '.dds'
            if texture.lower() not in headers:
                raise ValueError(f'Missing texture for {icon}')
            # DDS dimensions are stored before the pixel format, even for compressed textures.
            height, width = struct.unpack_from('<II', headers[texture.lower()], 12)
            x, y, w, h = map(int, skin.get('src_image').split(','))
            if min(x, y) < 0 or min(w, h) <= 0 or x + w > width or y + h > height:
                raise ValueError(f'Icon rectangle exceeds texture bounds: {icon}')
    print('All four distinct icon skins and texture rectangles verified in base and English UI.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--codec-directory', type=Path, required=True)
    parser.add_argument('--client-path', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.codec_directory.resolve()))
    from fire_temple_probe import read_pak
    from patch_client_world import encode_pak
    root, output = args.client_path.resolve(), args.output.resolve()
    if output.exists() or root == output or root in output.parents:
        raise ValueError('Use a new staging directory outside the client')
    if (root / 'Plugin/TransmogMenu').exists():
        raise ValueError('Legacy addon present; use the complete menu builder')
    engine = (root / 'bin64/crysystem.dll').read_bytes()
    if patch_plugin_key(engine) != engine:
        raise ValueError('Archive-v2 signing repair must already be installed')
    settings = json.loads(Path(__file__).with_name('menus.json').read_text(encoding='utf-8-sig'))
    validate_icons(root, settings)
    with read_pak(root / 'Plugin/RelicCalc/RelicCalc.pak') as archive:
        content = {name: archive.read(name) for name in archive.namelist()}
    original = content['PrivateMenus.lua']
    updated = original
    for entry in settings['serverCommands']:
        old = ('{label = ' + json.dumps(entry['label']) + ', command = ' + json.dumps(entry['command']) + '}').encode()
        new = old[:-1] + (', icon = ' + json.dumps(entry['icon']) + '}').encode()
        if updated.count(old) != 1:
            raise ValueError(f'Installed menu configuration differs: {entry["command"]}')
        updated = updated.replace(old, new)
    # Preserve the installed browser functions, URLs, layout seeds, and line endings exactly.
    for old, new in [
        (b'RegisterMenu(entry.label, SLASH_PRIVATEWAREHOUSE1, "v5_start_menu_relic_up")',
         b'RegisterMenu(entry.label, SLASH_PRIVATEWAREHOUSE1, entry.icon)'),
        (b'RegisterMenu(entry.label, "/say ." .. entry.command, "v5_start_menu_relic_up")',
         b'RegisterMenu(entry.label, "/say ." .. entry.command, entry.icon)'),
        (b'RegisterMenu(PRIVATE_CASH_SHOP_LABEL, SLASH_PRIVATECASHSHOP1, "v5_start_menu_relic_up")',
         ('RegisterMenu(PRIVATE_CASH_SHOP_LABEL, SLASH_PRIVATECASHSHOP1, ' + json.dumps(settings['cashShop']['icon']) + ')').encode()),
    ]:
        if updated.count(old) != 1:
            raise ValueError('Unexpected installed menu registration')
        updated = updated.replace(old, new)
    content['PrivateMenus.lua'] = updated
    packed = io.BytesIO()
    with zipfile.ZipFile(packed, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, payload in content.items():
            archive.writestr(name, payload)
    staged = output / 'Plugin/RelicCalc/RelicCalc.pak'
    staged.parent.mkdir(parents=True)
    staged.write_bytes(encode_pak(packed.getvalue()))
    with read_pak(staged) as archive:
        assert archive.testzip() is None
        assert archive.namelist() == list(content)
        for name, payload in content.items():
            assert archive.read(name) == payload, name
    subprocess.run([str(args.java), str(Path(__file__).with_name('SignClientPackages.java')),
                    str(root), str(output)], check=True)
    if digest(output / 'Pub.key') != digest(root / 'Pub.key'):
        raise ValueError('Installed model key differs from the stock key')
    (output / 'Pub.key').unlink()  # Identical stock key stays installed, untouched.
    files = [{'path': path.relative_to(output).as_posix(),
              'original': digest(root / path.relative_to(output)), 'staged': digest(path)}
             for path in sorted(output.rglob('*')) if path.is_file()]
    preserved = ['Pub.key', 'bin64/game.dll', 'bin64/crysystem.dll', 'bin32/bin32.pak',
                 'Data/func_pet/func_pet.pak', 'Data/ui/game/game.pak', 'L10N/enu/data/data.pak']
    manifest = {'clientRoot': str(root), 'files': files, 'legacyAddon': [], 'retiredFiles': [],
                'signatureIsolation': 'archive-v2', 'menuIconsOnly': True,
                'preservedFiles': [{'path': name, 'sha256': digest(root / name)} for name in preserved]}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(f'Prepared {len(files)} replacements; only PrivateMenus.lua changed inside RelicCalc.pak.')


if __name__ == '__main__':
    main()
