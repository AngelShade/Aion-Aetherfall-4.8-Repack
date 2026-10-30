"""Prepare native Stigma profile controls and matching graphics/cursor recovery."""
import argparse
import copy
import hashlib
import json
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

from patch_binary import patch, verify_access, BUTTON, GATES, CLICK_CAVE, SLOT_CAVE, LIFECYCLE_RESETS

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, binary_xml, encode_binary_xml
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
from prepare_client import rebuild, parts
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
from graphics_compat import prepare_incremental


def sha(data):
    return hashlib.sha256(data).hexdigest()


TEXT_KEY = 'STR_AETHERFALL_STIGMA_PROFILE_UPGRADE'
TOOLTIP_KEY = 'STR_AETHERFALL_STIGMA_PROFILE_UPGRADE_HELP'


def strings(data):
    root = binary_xml(data)
    for number, name, body in [(990100040, TEXT_KEY, 'Upgrade'),
                               (990100041, TOOLTIP_KEY, 'Open Stigma Upgrade')]:
        matches = [e for e in root if e.findtext('name') == name or e.findtext('id') == str(number)]
        if matches:
            if len(matches) != 1 or any(matches[0].findtext(k) != v for k, v in
                                        [('id', str(number)), ('name', name), ('body', body)]):
                raise ValueError('Stigma localization entry collision: ' + name)
            continue
        row = ET.SubElement(root, 'string')
        for key, value in [('id', str(number)), ('name', name), ('body', body)]:
            ET.SubElement(row, key).text = value
    return encode_binary_xml(root)


def layout(data):
    root = binary_xml(data)
    page = root.find(".//Widget[@name='tabpage_stigma']")
    help_button = root.find(".//Widget[@name='btn_stigma_help']")
    if root.get('type') != 'dlg_player_info' or page is None or help_button is None:
        raise ValueError('Expected native profile Stigma tab and Help control')
    existing = page.find("Widget[@name='" + BUTTON + "']")
    before = copy.deepcopy(root)
    if existing is not None:
        if (existing.get('frame') != '263,321,132,24'
                or (existing.get('text'), existing.get('tooltip')) not in
                [('Upgrade', 'Open Stigma Upgrade'), (TEXT_KEY, TOOLTIP_KEY)]):
            raise ValueError('Installed Stigma control differs')
        existing.set('text', TEXT_KEY)
        existing.set('tooltip', TOOLTIP_KEY)
        before_page = before.find(".//Widget[@name='tabpage_stigma']")
        before_page.remove(before_page.find("Widget[@name='" + BUTTON + "']"))
    else:
        ET.SubElement(page, 'Widget', name=BUTTON, type='button', preset='v5_button',
                      font='ui_default', frame='263,321,132,24', v_pos_align='bottom',
                      text=TEXT_KEY, tooltip=TOOLTIP_KEY)
    replacement = encode_binary_xml(root)
    check = binary_xml(replacement)
    new_page = check.find(".//Widget[@name='tabpage_stigma']")
    new_page.remove(new_page.find("Widget[@name='" + BUTTON + "']"))
    if ET.tostring(check) != ET.tostring(before):
        raise ValueError('An unrelated profile widget changed')
    return replacement


def archive(path, localized):
    raw = path.read_bytes()
    name = 'ui/game/player_info_dialog.xml' if localized else 'player_info_dialog.xml'
    with read_pak(path) as z:
        infos = {e.filename: e for e in z.infolist()}
        changes = {name: layout(z.read(name))}
        if localized:
            changes['strings/client_strings_ui.xml'] = strings(z.read('strings/client_strings_ui.xml'))
        result, _ = rebuild(raw, changes, infos)
    before, _, _, version = parts(raw)
    after, _, _, updated_version = parts(result)
    if version != updated_version or list(before) != list(after):
        raise ValueError('Profile archive structure changed')
    for member in before:
        if member not in changes and before[member][0] != after[member][0]:
            raise ValueError('Unrelated compressed archive member changed: ' + member)
    return result


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
    staged_state = output / 'Mods/GraphicsMenu/installed.json'
    state = json.loads(staged_state.read_text())
    package_path = output / 'Mods/GraphicsMenu/package/manifest.json'
    package = json.loads(package_path.read_text())
    backup = output / 'Mods/Backups/Renderer' / compatibility['backupName']
    for image in [dll, (output / 'Mods/GraphicsMenu/package/bin64/Game.dll').read_bytes(),
                  (backup / 'bin64/Game.dll').read_bytes(),
                  (backup / 'cursor-base/bin64/Game.dll').read_bytes()]:
        verify_access(image)

    def stage(relative, data):
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    stage('bin64/Game.dll', dll)
    for rel in ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']:
        active = archive(client / rel, rel.startswith('L10N/'))
        baseline = archive(backup / rel, rel.startswith('L10N/'))
        stage(rel, active)
        stage('Mods/GraphicsMenu/package/' + rel, active)
        stage(backup.relative_to(output).as_posix() + '/' + rel, baseline)
        entry = next(e for e in state['files'] if e['path'] == rel)
        entry.update(original=sha(baseline), installed=sha(active))
    package['files'] = copy.deepcopy(state['files'])
    stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    stage('Mods/StigmaProfile/mod.json', json.dumps(dict(
        Title='Stigma profile controls', Listed=True,
        Description='Equip and remove Stigmas from Profile. Upgrade opens the native Stigma charging window.',
        Files=['bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']), indent=2).encode())
    stage('Mods/StigmaProfile/README.md', (HERE / 'README.md').read_bytes())
    inventory = json.loads((client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    changed_active = ['bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']
    for entries in inventory['features'].values():
        for entry in entries:
            rel = entry.get('path', '')
            match = next((p for p in changed_active if p.casefold() == rel.casefold()), None)
            if match and entry.get('installedSha256'):
                entry.update(installedSha256=sha((output / match).read_bytes()), bytes=(output / match).stat().st_size)
    inventory['features']['StigmaProfile'] = [dict(path=rel, installedSha256=sha((output / rel).read_bytes()),
                                                 bytes=(output / rel).stat().st_size) for rel in changed_active]
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    records = []
    for path in sorted(output.rglob('*')):
        if not path.is_file():
            continue
        rel = path.relative_to(output).as_posix()
        old = client / rel
        records.append(dict(path=rel, original=sha(old.read_bytes()) if old.exists() else None,
                            staged=sha(path.read_bytes())))
    manifest = dict(feature='stigma-profile', clientRoot=str(client), files=records,
                    graphicsCompatibility=compatibility, serverChanged=False, repackChanged=False,
                    nativeGates=[dict(rva=hex(r), purpose=p) for r, _, p in GATES],
                    lifecycleResets=[dict(rva=hex(r), purpose=p) for r, p in LIFECYCLE_RESETS],
                    bridges=[hex(CLICK_CAVE), hex(SLOT_CAVE)],
                    sourceHashes={str(p): sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file()},
                    preservedFiles=[dict(path=rel, sha256=sha((client / rel).read_bytes())) for rel in
                                    ['Pub.key', 'Addon.key', 'bin64/crysystem.dll', 'bin32/bin32.pak.sig',
                                     'Data/func_pet/func_pet.pak.sig', 'Plugin/RelicCalc/RelicCalc.pak.sig',
                                     'bin64/AionRememberLogin.dll', 'bin64/AionIconBridge.dll', 'bin64/AionLootRoll.dll']],
                    gameplayAcceptance='Pending normal launch and player equip/remove/upgrade checks')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(records), 'affected files; original client remains unchanged:', output)


if __name__ == '__main__':
    main()
