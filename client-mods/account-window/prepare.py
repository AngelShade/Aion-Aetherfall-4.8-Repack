"""Prepare the Aetherfall Account window client package and matching graphics/cursor recovery.

Changes, all preserving every unrelated byte and archive member:
- bin64/Game.dll: /market/account login route and the Profile > Account button bridge (account_binary.py)
- Data/ui/game/game.pak and L10N/enu/Data/data.pak: btn_account_window on the Account tab, English label
- Plugin/RelicCalc/RelicCalc.pak (+ signatures): /account and /boons open the window in the Market dialog
"""
import argparse
import copy
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
from account_binary import patch, ACCOUNT_URL, BUTTON, BROWSER_EXT, CLICK_EXT  # noqa: E402
from codec import read_pak, binary_xml, encode_binary_xml  # noqa: E402
from prepare_client import rebuild, parts  # noqa: E402
from graphics_compat import prepare_incremental  # noqa: E402

TEXT_KEY, TOOLTIP_KEY = 'STR_AETHERFALL_ACCOUNT_WINDOW', 'STR_AETHERFALL_ACCOUNT_WINDOW_HELP'
STRINGS = [(990100060, TEXT_KEY, 'Account Window'), (990100061, TOOLTIP_KEY, 'Open your Aetherfall Account: boons, renown and characters')]
FRAME = '263,321,132,24'
RELIC = 'Plugin/RelicCalc/RelicCalc.pak'
LUA_FUNCTION = '''function PrivateAccount_Open()
    -- Reuse the verified, viewport-sized native browser dialog, like the Season Pass.
    PrivateWarehouse:Show();
    PrivateWarehouse:SetText("Aetherfall Account");
    PrivateWarehouse:SetRect(0, 0, 1280, 960);
    PrivateWarehouseBrowser:LoadUrlWithWebAuth(PRIVATE_ACCOUNT_URL);
end
'''
LUA_REGISTER = ('    SlashCmdList["PRIVATEACCOUNT"] = PrivateAccount_Open;\n    SLASH_PRIVATEACCOUNT1 = "/account";\n'
                '    SLASH_PRIVATEACCOUNT2 = "/boons";\n')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def strings(data):
    root = binary_xml(data)
    for number, name, body in STRINGS:
        matches = [e for e in root if e.findtext('name') == name or e.findtext('id') == str(number)]
        if matches:
            if len(matches) != 1 or any(matches[0].findtext(k) != v for k, v in [('id', str(number)), ('name', name), ('body', body)]):
                raise ValueError('Account localization entry collision: ' + name)
            continue
        row = ET.SubElement(root, 'string')
        for key, value in [('id', str(number)), ('name', name), ('body', body)]:
            ET.SubElement(row, key).text = value
    return encode_binary_xml(root)


def layout(data):
    root = binary_xml(data)
    page = root.find(".//Widget[@name='tabpage_account_info']")
    if root.get('type') != 'dlg_player_info' or page is None or page.find("Widget[@name='bm_effect_list_bg']") is None:
        raise ValueError('Expected the native Profile Account tab')
    before = copy.deepcopy(root)
    existing = page.find("Widget[@name='" + BUTTON + "']")
    if existing is not None:
        if (existing.get('frame'), existing.get('text'), existing.get('tooltip')) != (FRAME, TEXT_KEY, TOOLTIP_KEY):
            raise ValueError('Installed Account button differs')
        return data
    ET.SubElement(page, 'Widget', name=BUTTON, type='button', preset='v5_button', font='ui_default', frame=FRAME,
                  v_pos_align='bottom', text=TEXT_KEY, tooltip=TOOLTIP_KEY)
    replacement = encode_binary_xml(root)
    check = binary_xml(replacement)
    new_page = check.find(".//Widget[@name='tabpage_account_info']")
    new_page.remove(new_page.find("Widget[@name='" + BUTTON + "']"))
    if ET.tostring(check) != ET.tostring(before):
        raise ValueError('An unrelated profile widget changed')
    return replacement


def rebuild_members(raw, changes_for):
    with read_pak_bytes(raw) as z:
        infos = {e.filename: e for e in z.infolist()}
        changes = changes_for(z)
    result, _ = rebuild(raw, changes, infos)
    before, _, _, version = parts(raw)
    after, _, _, updated = parts(result)
    if version != updated or list(before) != list(after):
        raise ValueError('Archive structure changed')
    for member in before:
        if member not in changes and before[member][0] != after[member][0]:
            raise ValueError('Unrelated compressed archive member changed: ' + member)
    return result


def read_pak_bytes(raw):
    import tempfile, os
    fd, path = tempfile.mkstemp(suffix='.pak')
    try:
        os.write(fd, raw)
        os.close(fd)
        return read_pak(path)
    finally:
        os.remove(path)


def profile_archive(raw, localized):
    name = 'ui/game/player_info_dialog.xml' if localized else 'player_info_dialog.xml'
    def changes(z):
        out = {name: layout(z.read(name))}
        if localized:
            out['strings/client_strings_ui.xml'] = strings(z.read('strings/client_strings_ui.xml'))
        return out
    return rebuild_members(raw, changes)


def menus(data):
    text = data.decode('utf-8-sig')
    newline = '\r\n' if '\r\n' in text else '\n'
    text = text.replace('\r\n', '\n')
    anchor = 'function PrivateMenus_Register()\n'
    if 'PRIVATEACCOUNT' in text:
        if ('PRIVATE_ACCOUNT_URL = ' + json.dumps(ACCOUNT_URL) + ';') not in text:
            raise ValueError('Installed account menu differs')
        return data
    if text.count(anchor) != 1 or 'PrivateWarehouseBrowser:LoadUrlWithWebAuth(PRIVATE_SEASON_PASS_URL)' not in text:
        raise ValueError('Unexpected Aetherfall menu baseline')
    text = 'PRIVATE_ACCOUNT_URL = ' + json.dumps(ACCOUNT_URL) + ';\n' + LUA_FUNCTION + '\n' + text.replace(anchor, anchor + LUA_REGISTER)
    out = text.replace('\n', newline).encode('utf-8')
    return (b'\xef\xbb\xbf' + out) if data.startswith(b'\xef\xbb\xbf') else out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external preparation folder')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('This delivery is scoped to the original development client')
    output.mkdir(parents=True)
    dll = patch((client / 'bin64/Game.dll').read_bytes())
    _, compatibility = prepare_incremental(client, output, dll)
    if not compatibility:
        raise ValueError('Expected installed native graphics/cursor recovery')
    state_path = output / 'Mods/GraphicsMenu/installed.json'
    state = json.loads(state_path.read_text())
    package_path = output / 'Mods/GraphicsMenu/package/manifest.json'
    package = json.loads(package_path.read_text())
    backup = output / 'Mods/Backups/Renderer' / compatibility['backupName']

    def stage(relative, data):
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    stage('bin64/Game.dll', dll)
    for rel in ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']:
        localized = rel.startswith('L10N/')
        active = profile_archive((client / rel).read_bytes(), localized)
        baseline = profile_archive((backup / rel).read_bytes(), localized)
        stage(rel, active)
        stage('Mods/GraphicsMenu/package/' + rel, active)
        stage(backup.relative_to(output).as_posix() + '/' + rel, baseline)
        entry = next(e for e in state['files'] if e['path'] == rel)
        entry.update(original=sha(baseline), installed=sha(active))
    package['files'] = copy.deepcopy(state['files'])
    stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())

    relic_raw = (client / RELIC).read_bytes()
    relic = rebuild_members(relic_raw, lambda z: {'PrivateMenus.lua': menus(z.read('PrivateMenus.lua'))})
    stage(RELIC, relic)
    subprocess.run(['java', str(HERE.parent / 'transmog-menu/SignClientPackages.java'), str(client), str(output)], check=True)
    (output / 'Pub.key').unlink(missing_ok=True)  # the signer verifies the stock key; it is never installed

    stage('Mods/AccountWindow/mod.json', json.dumps(dict(
        Title='Aetherfall Account window', Listed=True,
        Description='Profile > Account > Account Window (or /account) opens your earned boons, renown and characters.',
        Files=['bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak', RELIC]), indent=2).encode())
    stage('Mods/AccountWindow/README.md', (HERE / 'README.md').read_bytes())
    changed_active = ['bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak', RELIC]
    inventory = json.loads((client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    for entries in inventory['features'].values():
        for entry in entries:
            match = next((p for p in changed_active if p.casefold() == entry.get('path', '').casefold()), None)
            if match and entry.get('installedSha256'):
                entry.update(installedSha256=sha((output / match).read_bytes()), bytes=(output / match).stat().st_size)
    inventory['features']['AccountWindow'] = [dict(path=rel, installedSha256=sha((output / rel).read_bytes()),
                                                 bytes=(output / rel).stat().st_size) for rel in changed_active]
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    records = []
    for path in sorted(output.rglob('*')):
        if path.is_file():
            rel = path.relative_to(output).as_posix()
            old = client / rel
            records.append(dict(path=rel, original=sha(old.read_bytes()) if old.exists() else None, staged=sha(path.read_bytes())))
    manifest = dict(feature='account-window', clientRoot=str(client), files=records, graphicsCompatibility=compatibility,
                    serverChanged=False, repackChanged=False, bridges=[hex(BROWSER_EXT), hex(CLICK_EXT)], route=ACCOUNT_URL,
                    sourceHashes={str(p): sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file()},
                    preservedFiles=[dict(path=rel, sha256=sha((client / rel).read_bytes())) for rel in
                                    ['Pub.key', 'bin64/crysystem.dll', 'bin64/AionRememberLogin.dll', 'bin64/AionIconBridge.dll',
                                     'bin64/AionLootRoll.dll', 'bin64/AionMarketShortcut.dll', 'bin64/AionGraphicsMenu.dll']
                                    if (client / rel).is_file()],
                    gameplayAcceptance='Pending: Profile > Account > Account Window and /account open the window')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(records), 'affected files; original client unchanged:', output)


if __name__ == '__main__':
    main()
