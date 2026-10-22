"""Prepare the bounded original-English-client NPC restoration; never installs or starts the game."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import runpy
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
FEATURE = Path(__file__).resolve().parent
NATIVE = runpy.run_path(str(ROOT / 'client-mods/quest-red-journal/prepare.py'))
read_pak = NATIVE['read_pak']
binary_xml = NATIVE['binary_xml']
encode_binary_xml = NATIVE['encode_binary_xml']
encode_html = NATIVE['encode_html']
decode_html = NATIVE['decode_html']
extend_archive = NATIVE['extend_archive']
parts = NATIVE['parts']


def sha(data):
    return hashlib.sha256(data).hexdigest()


def field(node, key, value):
    child = node.find(key)
    if child is None:
        child = ET.SubElement(node, key)
    child.text = str(value)


def edit_records(data, expected, change):
    tree = binary_xml(data)
    before = [ET.tostring(e) for e in tree]
    changed = set()
    for index, node in enumerate(tree):
        identity = node.findtext('id')
        if identity in expected:
            change(node)
            changed.add(identity)
        else:
            assert ET.tostring(node) == before[index]
    assert changed == set(expected), (changed, expected)
    encoded = encode_binary_xml(tree)
    assert ET.tostring(binary_xml(encoded)) == ET.tostring(tree)
    return encoded, len(tree) - len(changed)


def npc_dialog(text, filename, paragraphs, *, buy=None, remove_trade=False):
    tree = ET.fromstring(decode_html(text, filename))
    for page in tree.findall('HtmlPage'):
        if page.get('name') not in ('select_quest', 'select1'):
            continue
        body = page.find('Contents/html/body')
        if body is not None:
            for line in paragraphs:
                ET.SubElement(body, 'p').text = line
        funcs = page.find('npcfuncs')
        if funcs is not None:
            trade = funcs.find('trade_in')
            if trade is not None:
                if buy is not None:
                    trade.tag = 'trade_buy'
                    trade.text = buy
                elif remove_trade:
                    funcs.remove(trade)
    result = encode_html('<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(tree, encoding='unicode'), filename)
    assert ET.tostring(ET.fromstring(decode_html(result, filename))) == ET.tostring(tree)
    return result


def prepare(client, output, reuse=None):
    assert not output.exists() and ROOT not in output.parents and client not in output.parents
    output.mkdir(parents=True)
    catalog = json.loads((FEATURE / 'catalog.json').read_text())
    stock = catalog['stock']
    manifest = {'feature': 'npc-restoration', 'originalOnly': True, 'installed': False,
                'gameplayVerified': False, 'files': [], 'validation': {}}
    previous = json.loads((reuse / 'manifest.json').read_text()) if reuse else None
    if previous:
        assert previous['sourceHashes']['catalog.json'] == sha((FEATURE / 'catalog.json').read_bytes())
    for relative in ('Data/Npcs/Npcs.pak', 'Data/Items/Items.pak', 'Data/Quest/Quest.pak', 'L10N/ENU/data/data.pak'):
        source_path = client / relative
        original = source_path.read_bytes()
        # A dialogue-only repair can reuse the already verified, unaffected data archives.
        if previous and not relative.startswith('L10N/'):
            entry = next(e for e in previous['files'] if e['path'] == relative)
            data = (reuse / relative).read_bytes()
            assert sha(original) == entry['before'] and sha(data) == entry['after']
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            manifest['files'].append(copy.deepcopy(entry))
            manifest['validation'][relative] = copy.deepcopy(previous['validation'][relative])
            print('Reused verified ' + relative, flush=True)
            continue
        changes = {}
        checks = {}
        with read_pak(source_path) as source:
            if relative == 'Data/Npcs/Npcs.pak':
                goods = binary_xml(source.read('client_npc_goodslist.xml'))
                assert not set(stock) & {n.findtext('id') for n in goods}, 'Custom goods ID already in use'
                native_descs = {n.findtext('desc') for n in goods}
                for sid, entry in stock.items():
                    assert entry['description'] in native_descs
                    node = ET.SubElement(goods, 'client_npc_goodslist')
                    for key, value in [('id', sid), ('name', entry['name']), ('desc', entry['description']), ('use_category', entry['category'])]:
                        field(node, key, value)
                    listing = ET.SubElement(node, 'goods_list')
                    for item in entry['items']:
                        field(ET.SubElement(listing, 'data'), 'item', item['clientName'])
                changes['client_npc_goodslist.xml'] = encode_binary_xml(goods)
                assert ET.tostring(binary_xml(changes['client_npc_goodslist.xml'])) == ET.tostring(goods)
                def npc(node):
                    nid = node.findtext('id')
                    if nid in catalog['merchants']:
                        kind, tab_field = ('abyss_trade_info', 'atab') if nid == '800417' else ('extra_currency_trade_info', 'etab')
                        assert node.find(kind) is None
                        tabs = ET.SubElement(ET.SubElement(node, kind), 'tab_list')
                        for sid in catalog['merchants'][nid]:
                            field(ET.SubElement(tabs, 'data'), tab_field, stock[sid]['name'])
                    else:
                        actual = 'LDF5b_Dv14_Exchange_Cera' if nid == '801494' else 'LDF5b_Lv14_Exchange_Cera'
                        field(node, 'quest_ai_name', actual)
                changes['client_npcs_npc.xml'], checks['otherNpcRecordsPreserved'] = edit_records(
                    source.read('client_npcs_npc.xml'), {'800371', '800372', '800417', '801494', '801495'}, npc)
            elif relative == 'Data/Items/Items.pak':
                armor = {str(item['id']): item for item in stock['60011']['items']}
                def price(node):
                    item = armor[node.findtext('id')]
                    assert node.findtext('name').lower() == item['clientName'].lower()
                    assert node.find('extra_currency_item') is None and node.find('abyss_item') is None
                    field(node, 'extra_currency_item', 'tiamat_coin_01')
                    field(node, 'extra_currency_item_count', item['price']['count'])
                changes['client_items_armor.xml'], checks['otherArmorRecordsPreserved'] = edit_records(
                    source.read('client_items_armor.xml'), armor, price)
            elif relative == 'Data/Quest/Quest.pak':
                def quest(node):
                    info = catalog['quests'][node.findtext('id')]
                    assert node.findtext('minlevel_permitted') == '99'
                    assert node.findtext('collect_item1') == 'coin_piece_01 10'
                    field(node, 'minlevel_permitted', info['minLevel'])
                    field(node, 'category2', info['zone'])
                changes['quest.xml'], checks['otherQuestRecordsPreserved'] = edit_records(
                    source.read('quest.xml'), catalog['quests'], quest)
            else:
                descriptions = {
                    'weapon': ('Browse Protectorate weapons', ['Protectorate weapons cost 12 Protectorate Coins each.']),
                    'armor': ('Browse Protectorate Recruit armor', ['Protectorate Recruit armor costs 8 coins for the chest, 6 for the legs, and 4 each for gloves, shoulders and boots.']),
                    'abyssarmor': ('Browse captured Abyss armor', ['Captured level-60 armor is issued for your faction. Payment uses the usual Abyss Points and Mithril Medals.'])}
                for suffix, (label, lines) in descriptions.items():
                    name = 'dialogs/idtiamat/idtiamat_drakan_' + suffix + '_rw.html'
                    changes[name] = npc_dialog(source.read(name), Path(name).name,
                        lines + ["Only those in Drakan form may trade. Use Vershaka's Statue nearby."], buy=label)
                outcomes = ['Each attempt consumes 10 Fragmented Ceramium. There is a 33% chance of one Ceramium Medal and a 67% chance of one Rusted Medal. No Kinah is charged.']
                for side in ('d', 'l'):
                    for variant in ('8', '14'):
                        name = 'dialogs/ldf5b/ldf5b_' + side + 'v' + variant + '_exchange_cera.html'
                        changes[name] = npc_dialog(source.read(name), Path(name).name, outcomes, remove_trade=variant == '8')
                for qid, person in [('13360', 'Bekasis'), ('23360', 'Inharet')]:
                    name = 'dialogs/' + ('10000_19999' if qid == '13360' else '20000_29999') + '/quest_q' + qid + '.html'
                    tree = ET.fromstring(decode_html(source.read(name), Path(name).name))
                    for page in tree.findall('HtmlPage'):
                        body = page.find('Contents/html/body')
                        if body is None:
                            continue
                        if page.get('name') in ('select1', 'select_quest_reward1'):
                            for child in list(body):
                                body.remove(child)
                            ET.SubElement(body, 'p').text = person + ' can attempt to reform your Fragmented Ceramium into a medal.'
                            ET.SubElement(body, 'p').text = outcomes[0]
                        elif page.get('name') == 'quest_complete':
                            for child in list(body):
                                body.remove(child)
                            ET.SubElement(body, 'p').text = 'You brought ten Fragmented Ceramium to ' + person + ' for a medal-morphing attempt. You may gather more fragments and try again.'
                    changes[name] = encode_html('<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(tree, encoding='unicode'), Path(name).name)
                def rename(node):
                    body = node.find('body')
                    assert body is not None
                    body.text = re.sub(r'\s*\(unused\)', '', body.text, flags=re.I)
                strings = binary_xml(source.read('strings/client_strings_npc.xml'))
                before = [ET.tostring(n) for n in strings]
                count = 0
                for index, node in enumerate(strings):
                    if node.findtext('name') in {'STR_LDF5b_Dv8_Exchange_Cera', 'STR_LDF5b_Lv8_Exchange_Cera'}:
                        rename(node)
                        count += 1
                    else:
                        assert ET.tostring(node) == before[index]
                assert count == 2
                changes['strings/client_strings_npc.xml'] = encode_binary_xml(strings)
                checks['otherNpcNamesPreserved'] = len(strings) - count
            patched = extend_archive(original, changes, {})
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(patched)
            with read_pak(target) as verified:
                assert verified.namelist() == source.namelist()
                for name in changes:
                    assert verified.read(name) == changes[name], name
            old, _, _, version = parts(original)
            new, _, _, new_version = parts(patched)
            assert version == new_version and set(old) == set(new)
            for name in old:
                if name not in changes:
                    assert old[name][0] == new[name][0], 'Unrelated compressed member changed: ' + name
            checks['unchangedCompressedMembersPreserved'] = len(old) - len(changes)
            manifest['files'].append({'path': relative, 'before': sha(original), 'after': sha(patched), 'members': list(changes)})
            manifest['validation'][relative] = checks
            print('Prepared ' + relative + ': ' + str(len(changes)) + ' affected members', flush=True)
    manifest['sourceHashes'] = {p.name: sha(p.read_bytes()) for p in (FEATURE / 'prepare.py', FEATURE / 'catalog.json')}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--reuse', type=Path, help='Reuse unaffected NPC/item/quest archives after a dialogue-only change')
    args = parser.parse_args()
    prepare(args.client.resolve(strict=True), args.output.resolve(), args.reuse.resolve(strict=True) if args.reuse else None)
