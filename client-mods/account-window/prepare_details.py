"""Prepare native selected-boon details, short hover names and row selection."""
import argparse
import copy
import html
import json
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import prepare as account
sys.path.insert(0, str(HERE.parent / 'account-boons'))
import build as boons
from graphics_compat import prepare_incremental
import details_binary

KEY = 'STR_AETHERFALL_ACCOUNT_DETAILS'
ACTIVE_KEY = 'STR_AETHERFALL_ACCOUNT_ACTIVE_BOONS'
TITLE_KEY = 'STR_AETHERFALL_ACCOUNT_BOON_DETAILS'
# Same installed benefits and thresholds; only their presentation changes.
DETAILS = {
    1: ['Requirement: Season Pass Premium, purchased with Kinah.', 'Duration: Until the season ends.', '',
        'Benefits', '+30% XP from hunting, groups, quests, gathering and crafting.',
        '+30% gathering and crafting skill XP.', 'Central Market: receive 84.5% of each sale instead of 65%.',
        'Wardrobe: unlock appearances without a ticket.'],
    2: ['Requirement: Season Pass Advanced Premium.', 'Duration: Until the season ends.', '',
        'Benefits', '+20% item drops.', 'Energy of Repose: full refill after 24 hours offline instead of 48.',
        'Central Market warehouse: +2,000 VT capacity.'],
    3: ['Account Fame is shared by your characters. Open Account Window to see your current points and reward steps.', '',
        'Earning Fame', 'Combat: character levels from level 10. Levels 55+ count double; level 65 counts five times.',
        'Life: gathering and crafting skills from 100; one Fame per five skill points.',
        'Legend: one Fame per ten completed quests, plus five per title.', '',
        'Daily Kinah mail', '100 Kinah per Fame point, with separate daily limits:',
        'Combat: 500,000 Kinah. Life: 250,000. Legend: 250,000.', '',
        'Combat Fame rewards: bonus Abyss Points', '200 Fame: +10%. 500: +20%. 1,000: +30%.',
        '2,000 Fame: +40%. 3,000: +50%.', '',
        'Life Fame rewards: crafting / gathering skill XP', '50 Fame: +50% / +0%.',
        '100 Fame: +100% / +100%.', '200 Fame: +200% / +100%.',
        '400 Fame: +300% / +200%.', '800 Fame: +500% / +200%.', '',
        'Legend Fame rewards: Energy of Repose XP bonus', '50 Fame: 50%. 100: 60%. 200: 70%.',
        '300 Fame: 80%. 400: 100%.', '', 'Total Fame rewards',
        '1,000 Fame: Central Market sales return 5% more.', '2,500 Fame: +10% item drops.'],
    4: ['Permanent account bonuses earned through story and quest progress on any character.', '',
        'Chapters', 'Complete the Ascension ceremony.', 'Enter the Abyss.', 'Complete The Unfinished Entry.',
        'Complete 300 quests.', 'Complete 800 quests.', '', 'Benefits for each chapter',
        '+3% maximum HP and MP.', '+2% physical attack.', '+20 magic boost.',
        '+3% physical defence.', '+10 magic resist.'],
    5: ['Requirement: A level 65 Daeva on your account.', '',
        'Receive a coffer by mail for every month of account age.',
        'Rewards include expansion tickets, enchantment stones, crafting charms, mounts and more.',
        'The timer shows when the next coffer is due.'],
}


def boon_strings(payload):
    root = account.binary_xml(boons.strings(payload))
    for pid, lines in DETAILS.items():
        key = f'STR_F2P_PACK_PACK_{pid}'
        body = '<font color="#a6d9ff">' + html.escape(boons.TEXT[key], quote=False) + '</font><br><br>'
        body += '<br>'.join(html.escape(line, quote=False) for line in lines)
        next(e for e in root if e.findtext('name') == key + '_DESC').find('body').text = body
    return account.encode_binary_xml(root)


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
    bg.attrib.pop('text', None)
    bg.attrib.pop('font', None)
    title.set('text', ACTIVE_KEY)
    page.find("Widget[@name='bm_effect_list_title']").set('text', TITLE_KEY)
    page.find("Widget[@name='bm_list']").set('exclusive', 'True')
    if view is None:
        view = ET.Element('Widget', flag='vscroll', frame='11,206,378,100', name='bm_effect_list',
                          padding='0,0', scroll_type='auto', text_color='0.8078,0.6863,0.4235,1.0',
                          text_offset='10,10', type='htmlview', valign='top')
        page.insert(list(page).index(bg) + 1, view)
    # Compare unaffected widgets, including the Account Window button, exactly.
    affected = {'bm_effect_list', 'bm_effect_list_bg', 'bm_list_title', 'bm_effect_list_title', 'bm_list'}
    def unaffected(tree):
        tree = copy.deepcopy(tree)
        p = tree.find(".//Widget[@name='tabpage_account_info']")
        for node in list(p):
            if node.get('name') in affected:
                p.remove(node)
        return ET.tostring(tree)
    result = account.encode_binary_xml(root)
    if unaffected(account.binary_xml(result)) != unaffected(before):
        raise ValueError('Unexpected profile layout change')
    return result


def strings(payload):
    root = account.binary_xml(payload)
    for number, key, body in [(990100062, KEY, 'Select an account boon to read its details.'),
                              (990100063, ACTIVE_KEY, 'Active boons'),
                              (990100064, TITLE_KEY, 'Boon details')]:
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
            result['strings/client_strings_bm.xml'] = boon_strings(z.read('strings/client_strings_bm.xml'))
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
    output.mkdir(parents=True)
    dll = details_binary.patch((client / 'bin64/Game.dll').read_bytes())
    _, compatibility = prepare_incremental(client, output, dll)
    if not compatibility:
        raise ValueError('Expected native Graphics/cursor recovery')
    state = json.loads((output / 'Mods/GraphicsMenu/installed.json').read_text())
    manifest = json.loads((output / 'Mods/GraphicsMenu/package/manifest.json').read_text())
    baseline = output / 'Mods/Backups/Renderer' / compatibility['backupName']
    def stage(rel, data):
        target = output / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    stage('bin64/Game.dll', dll)
    resources = ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']
    changed = ['bin64/Game.dll'] + resources
    for rel in resources:
        entry = next(e for e in state['files'] if e['path'] == rel)
        raw, saved = (client / rel).read_bytes(), (baseline / rel).read_bytes()
        if (account.sha(raw) != entry['installed'] or account.sha(saved) != entry['original'] or
                account.sha((client / 'Mods/GraphicsMenu/package' / rel).read_bytes()) != entry['installed']):
            raise ValueError('Live/package/restore hashes disagree: ' + rel)
        active, restore = archive(raw, rel.startswith('L10N')), archive(saved, rel.startswith('L10N'))
        stage(rel, active)
        stage('Mods/GraphicsMenu/package/' + rel, active)
        stage(baseline.relative_to(output).as_posix() + '/' + rel, restore)
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
            old = client / rel
            records.append(dict(path=rel, original=account.sha(old.read_bytes()) if old.exists() else None,
                                staged=account.sha(path.read_bytes())))
    preserved = ['bin64/AetherfallCore.dll', 'Plugin/RelicCalc/RelicCalc.pak', 'Addon.key', 'Pub.key',
                 'Plugin/RelicCalc/RelicCalc.pak.sig', 'Data/func_pet/func_pet.pak.sig', 'bin32/bin32.pak.sig',
                 'Data/BM/BM.pak', 'Data/USA/BM/BM.pak']
    result = dict(feature='account-details', clientRoot=str(client), files=records, graphicsCompatibility=compatibility,
                  sourceHashes={str(p): account.sha(p.read_bytes()) for p in [Path(__file__), HERE / 'prepare.py',
                      HERE / 'details_binary.py', HERE.parent / 'account-boons/build.py']},
                  preservedFiles=[dict(path=rel, sha256=account.sha((client / rel).read_bytes())) for rel in preserved],
                  gameplayAcceptance='Pending: P > Account initial/changed boon selection, native highlight, scrolling details, short hover names and Account Window')
    (output / 'manifest.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print('Prepared', len(records), 'files; both profile layouts and English descriptions verified; unrelated compressed members preserved:', output)


if __name__ == '__main__':
    main()
