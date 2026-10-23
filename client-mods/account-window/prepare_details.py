"""Prepare resource-only P > Account details; preserve native earned-pack admission.

The stock renderer at Game.dll RVA 0x8372e0 formats the retail effect table
(RVA 0x9eb250), not client_bm_pack.desc_long. Removing retail effects therefore
leaves it empty. Use the existing scrolling static box for a localized guide;
remove only the obsolete htmlview. Its native caller explicitly accepts null.
"""
import argparse
import copy
import json
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import prepare as account
sys.path.insert(0, str(HERE.parent / 'account-boons'))
import build as boons
from graphics_compat import owned_renderer_backup

KEY = 'STR_AETHERFALL_ACCOUNT_DETAILS'
ACTIVE_KEY = 'STR_AETHERFALL_ACCOUNT_ACTIVE_BOONS'
GUIDE = ('The list above shows earned boons only. This guide includes locked benefits. '
         'Open Account Window for your points, active steps, timers and unlock progress.\n\n' +
         '\n\n'.join(boons.TEXT[f'STR_F2P_PACK_PACK_{i}'] + '\n' + boons.TEXT[f'STR_F2P_PACK_PACK_{i}_DESC']
                     for i in range(1, 6)))


def layout(payload):
    root = account.binary_xml(payload)
    page = root.find(".//Widget[@name='tabpage_account_info']")
    if page is None:
        raise ValueError('Native Account page missing')
    before = copy.deepcopy(root)
    bg = page.find("Widget[@name='bm_effect_list_bg']")
    view = page.find("Widget[@name='bm_effect_list']")
    title = page.find("Widget[@name='bm_list_title']")
    if bg is None or title is None or bg.get('type') != 'static':
        raise ValueError('Unexpected Account details layout')
    bg.set('text', KEY)
    bg.set('font', 'ui_default')
    title.set('text', ACTIVE_KEY)
    if view is not None:
        page.remove(view)
    # Verify no other widget or attribute was changed.
    expected = copy.deepcopy(before)
    ep = expected.find(".//Widget[@name='tabpage_account_info']")
    ep.find("Widget[@name='bm_effect_list_bg']").set('text', KEY)
    ep.find("Widget[@name='bm_effect_list_bg']").set('font', 'ui_default')
    ep.find("Widget[@name='bm_list_title']").set('text', ACTIVE_KEY)
    ev = ep.find("Widget[@name='bm_effect_list']")
    if ev is not None:
        ep.remove(ev)
    result = account.encode_binary_xml(root)
    if ET.tostring(account.binary_xml(result)) != ET.tostring(expected):
        raise ValueError('Unexpected profile layout change')
    return result


def strings(payload):
    root = account.binary_xml(payload)
    for number, key, body in [(990100062, KEY, GUIDE), (990100063, ACTIVE_KEY, 'Active boons')]:
        matches = [e for e in root if e.findtext('name') == key or e.findtext('id') == str(number)]
        if matches:
            if len(matches) != 1 or matches[0].findtext('name') != key or matches[0].findtext('id') != str(number):
                raise ValueError('Account details localization collision')
            matches[0].find('body').text = body
        else:
            node = ET.SubElement(root, 'string')
            for tag, value in [('id', str(number)), ('name', key), ('body', body)]:
                ET.SubElement(node, tag).text = value
    return account.encode_binary_xml(root)


def archive(raw, localized):
    name = 'ui/game/player_info_dialog.xml' if localized else 'player_info_dialog.xml'
    def changes(z):
        result = {name: layout(z.read(name))}
        if localized:
            result['strings/client_strings_ui.xml'] = strings(z.read('strings/client_strings_ui.xml'))
            result['strings/client_strings_bm.xml'] = boons.strings(z.read('strings/client_strings_bm.xml'))
        return result
    return account.rebuild_members(raw, changes)


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
    manifest = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
    if state.get('restoredAt') or state['files'] != manifest['files']:
        raise ValueError('Graphics records disagree or are inactive')
    baseline = owned_renderer_backup(client, state['backupRoot'])
    def stage(rel, data):
        target = output / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    changed = ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']
    for rel in changed:
        entry = next(e for e in state['files'] if e['path'] == rel)
        raw, saved = (client / rel).read_bytes(), (baseline / rel).read_bytes()
        if (account.sha(raw) != entry['installed'] or account.sha(saved) != entry['original'] or
                account.sha((client / 'Mods/GraphicsMenu/package' / rel).read_bytes()) != entry['installed']):
            raise ValueError('Live/package/restore hashes disagree: ' + rel)
        active, restore = archive(raw, rel.startswith('L10N')), archive(saved, rel.startswith('L10N'))
        stage(rel, active)
        stage('Mods/GraphicsMenu/package/' + rel, active)
        stage(baseline.relative_to(client).as_posix() + '/' + rel, restore)
        for entries in (state['files'], manifest['files']):
            next(e for e in entries if e['path'] == rel).update(installed=account.sha(active), original=account.sha(restore))
    stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(manifest, indent=2).encode())
    inventory = json.loads((client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    for entries in inventory['features'].values():
        for entry in entries:
            if entry.get('path') in changed:
                target = output / entry['path']
                entry.update(installedSha256=account.sha(target.read_bytes()), bytes=target.stat().st_size)
    inventory['features']['AccountDetails'] = [dict(path=rel, installedSha256=account.sha((output / rel).read_bytes()),
                                                   bytes=(output / rel).stat().st_size) for rel in changed]
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    records = []
    for path in sorted(output.rglob('*')):
        if path.is_file():
            rel = path.relative_to(output).as_posix()
            records.append(dict(path=rel, original=account.sha((client / rel).read_bytes()), staged=account.sha(path.read_bytes())))
    preserved = ['bin64/Game.dll', 'Plugin/RelicCalc/RelicCalc.pak', 'Addon.key', 'Pub.key',
                 'Plugin/RelicCalc/RelicCalc.pak.sig', 'Data/func_pet/func_pet.pak.sig', 'bin32/bin32.pak.sig',
                 'Data/BM/BM.pak', 'Data/USA/BM/BM.pak']
    result = dict(feature='account-details', clientRoot=str(client), files=records, graphicsCompatibility=None,
                  sourceHashes={str(p): account.sha(p.read_bytes()) for p in [Path(__file__), HERE / 'prepare.py',
                      HERE.parent / 'account-boons/build.py']},
                  preservedFiles=[dict(path=rel, sha256=account.sha((client / rel).read_bytes())) for rel in preserved],
                  gameplayAcceptance='Pending: P > Account scrolling details, active list, hover descriptions and Account Window')
    (output / 'manifest.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print('Prepared', len(records), 'files; both profile layouts and English descriptions verified; unrelated compressed members preserved:', output)


if __name__ == '__main__':
    main()
