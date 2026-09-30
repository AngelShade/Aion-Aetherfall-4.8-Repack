"""Prepare a signed local-client modification; never write to the client here."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import zipfile
import re
import xml.etree.ElementTree as ET
from patch_game_dll import build_dll


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


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

    root = args.client_path.resolve()
    output = args.output.resolve()
    if output == root or root in output.parents:
        raise ValueError('Preparation output must be outside the client')
    if output.exists():
        raise ValueError('Use a new output directory to avoid stale staged files')
    source = root / 'Plugin/RelicCalc/RelicCalc.pak'
    mod_root = Path(__file__).resolve().parent
    settings = json.loads((mod_root / 'menus.json').read_text(encoding='utf-8-sig'))
    commands = [entry['command'] for entry in settings['serverCommands']]
    if not commands or len(set(commands)) != len(commands) or any(not re.fullmatch('[a-z][a-z0-9]{0,31}', c) for c in commands):
        raise ValueError('Server command aliases must be unique lowercase letters/numbers, at most 32 characters')
    for entry in settings['serverCommands']:
        if not re.fullmatch('[A-Za-z0-9 ]{1,40}', entry['label']):
            raise ValueError('Use plain menu labels of at most 40 characters')
    with read_pak(source) as archive:
        content = {name: archive.read(name) for name in archive.namelist()}
    original = content['RelicCalc.lua']
    anchor = b'\tRegisterMenu(GetAionStr("STR_RELICCALC_TITLE"), lastCommand, "v5_start_menu_relic_up");'
    legacy = b'\tRegisterMenu("Transmog", "/say .transmog", "v5_start_menu_relic_up");\r\n'
    insertion = b'\tPrivateMenus_Register();\r\n'
    base_lua = original.replace(legacy, b'').replace(insertion, b'')
    if base_lua.count(anchor) != 1:
        raise ValueError('Expected RelicCalc menu registration exactly once')
    content['RelicCalc.lua'] = base_lua.replace(anchor, insertion + anchor)
    content['CashShop.xml'] = (mod_root / 'CashShop.xml').read_text(encoding='utf-8-sig').replace('UTF-8', 'UTF-16').replace('\n', '\r\n').encode('utf-16')
    ET.fromstring(content['CashShop.xml'])
    content['Warehouse.xml'] = (mod_root / 'Warehouse.xml').read_text(encoding='utf-8-sig').replace('UTF-8', 'UTF-16').replace('\n', '\r\n').encode('utf-16')
    ET.fromstring(content['Warehouse.xml'])
    # JSON-quoted ASCII values are valid Lua string literals for these labels and URL.
    config = 'PRIVATE_SERVER_MENUS = {\n' + ''.join(
        '    {label = ' + json.dumps(e['label']) + ', command = ' + json.dumps(e['command']) + '},\n'
        for e in settings['serverCommands']) + '};\n'
    config += 'PRIVATE_CASH_SHOP_URL = ' + json.dumps(settings['cashShop']['url']) + ';\n'
    config += 'PRIVATE_CASH_SHOP_LABEL = ' + json.dumps(settings['cashShop']['label']) + ';\n'
    config += 'PRIVATE_CENTRAL_MARKET_URL = ' + json.dumps(settings['centralMarket']['url']) + ';\n'
    content['PrivateMenus.lua'] = (config + (mod_root / 'PrivateMenus.lua').read_text(encoding='utf-8-sig')).replace('\n', '\r\n').encode('utf-8')
    toc = content['RelicCalc.toc'].decode('utf-8').replace('\r', '').splitlines()
    toc = [line for line in toc if line not in ('CashShop.xml', 'Warehouse.xml', 'PrivateMenus.lua')]
    toc += ['CashShop.xml', 'Warehouse.xml', 'PrivateMenus.lua']
    content['RelicCalc.toc'] = ('\r\n'.join(toc) + '\r\n').encode('utf-8')
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, payload in content.items():
            info = zipfile.ZipInfo(name, (2026, 9, 29, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, payload)
    patched = output / 'Plugin/RelicCalc/RelicCalc.pak'
    patched.parent.mkdir(parents=True)
    patched.write_bytes(encode_pak(data.getvalue()))
    with read_pak(patched) as archive:
        assert archive.testzip() is None
        assert archive.namelist() == list(content)
        for name, payload in content.items():
            assert archive.read(name) == payload, name
    signer = Path(__file__).with_name('SignClientPackages.java')
    subprocess.run([str(args.java), str(signer), str(root), str(output)], check=True)
    from patch_plugin_key import patch_plugin_key
    cry_system = output / 'bin64/crysystem.dll'
    cry_system.parent.mkdir(parents=True, exist_ok=True)
    cry_system.write_bytes(patch_plugin_key((root / 'bin64/crysystem.dll').read_bytes()))
    patched_dll = output / 'bin64/game.dll'
    patched_dll.parent.mkdir(parents=True, exist_ok=True)
    dll = build_dll(root / 'bin64/game.dll.orig', commands, [settings['cashShop']['url'], settings['centralMarket']['url']])
    inventory = settings.get('inventory')
    if inventory:
        from unified_inventory import BASE_SLOTS, SLOTS, patch_inventory_dll, prepare_inventory_archive
        if inventory != {'slots': SLOTS, 'columns': 12, 'baseSlots': BASE_SLOTS}:
            raise ValueError('Unified inventory requires 180 base slots, 279 maximum cells, and 12 columns')
        prepare_inventory_archive(root, output)
        # Localized UI templates take precedence over the base UI archive.
        if (root / 'L10N/enu/data/data.pak').exists():
            prepare_inventory_archive(root, output, 'L10N/enu/data/data.pak', 'ui/game/')
        dll = patch_inventory_dll(dll)
        from inventory_search import patch_search_dll
        dll = patch_search_dll(dll)
    patched_dll.write_bytes(dll)
    replacements = []
    for staged in sorted(output.rglob('*')):
        if staged.is_file():
            relative = staged.relative_to(output).as_posix()
            replacements.append({'path': relative, 'original': digest(root / relative) if (root / relative).exists() else None, 'staged': digest(staged)})
    previous_addon = root / 'Plugin/TransmogMenu'
    legacy = [{'path': f.relative_to(previous_addon).as_posix(), 'sha256': digest(f)}
              for f in sorted(previous_addon.rglob('*')) if f.is_file()]
    retired = [{'path': 'bin64/game.dll.patched', 'sha256': digest(root / 'bin64/game.dll.patched')}] if (root / 'bin64/game.dll.patched').exists() else []
    manifest = {'clientRoot': str(root), 'files': replacements, 'legacyAddon': legacy, 'retiredFiles': retired,
                'inventorySlots': SLOTS if inventory else 0, 'signatureIsolation': 'archive-v2'}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(f'Prepared {len(replacements)} replacements in {output}; client untouched.')
    print('Prepared configured server menu entries and an embedded Cash Shop window before Relic Appraiser.')


if __name__ == '__main__':
    main()
