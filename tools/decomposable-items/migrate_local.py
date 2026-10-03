"""Migrate saved local legacy box definitions to PR #200's sets without changing their loot.

Run with a saved legacy XML and an output XML. The six disputed PR #211 IDs
are intentionally excluded: the imported PR #200 file supplies their rewards.
"""
import argparse
from collections import defaultdict
from decimal import Decimal
from pathlib import Path
import xml.etree.ElementTree as ET

CLASSES = 'WARRIOR GLADIATOR TEMPLAR SCOUT ASSASSIN RANGER MAGE SORCERER SPIRIT_MASTER PRIEST CLERIC CHANTER ENGINEER RIDER GUNNER ARTIST BARD'.split()
DISPUTED = {188052649, 188052918, 188053543, 188053544, 188053545, 188053634}


def migrate(box):
    assert box.get('selectable', 'false') == 'false', 'Selectable migration requires an explicit review'
    assert not box.findall('.//random_item'), 'Random-type migration requires an explicit review'
    groups = box.findall('items')
    assert all(not set(g.attrib) - {'chance'} for g in groups), 'Level-conditioned migration requires an explicit review'
    total = sum(Decimal(g.get('chance', '100')) for g in groups)
    assert total > 0
    contexts = defaultdict(list)
    for race in ['ELYOS', 'ASMODIANS']:
        for pc in CLASSES:
            alternatives = []
            for g in groups:
                eligible = [i for i in g.findall('item') if i.get('race', 'PC_ALL') in ['PC_ALL', race]
                            and (not i.get('player_classes') or pc in i.get('player_classes').split())]
                # Empty outcomes previously failed before consuming the parent.
                if not eligible:
                    continue
                reward = tuple((i.get('id'), i.get('min_count', '1'), i.get('max_count', '0')) for i in eligible)
                weight = Decimal(g.get('chance', '100')) / total * 100
                assert weight.quantize(Decimal('.01')) == weight, 'Weight rounding would change the original distribution'
                alternatives.append((format(weight.normalize(), 'f'), reward))
            if alternatives:
                contexts[tuple(alternatives)].append((race, pc))
    out = ET.Element('decomposable', item_id=box.get('item_id'), override='true')
    for alternatives, pairs in contexts.items():
        by_race = {r: [pc for rr, pc in pairs if rr == r] for r in ['ELYOS', 'ASMODIANS']}
        conditions = [('PC_ALL', by_race['ELYOS'])] if by_race['ELYOS'] == by_race['ASMODIANS'] else list(by_race.items())
        for race, classes in conditions:
            if not classes:
                continue
            attrs = {}
            if race != 'PC_ALL':
                attrs['race'] = race
            if classes != CLASSES:
                attrs['player_classes'] = ' '.join(classes)
            branch = ET.SubElement(out, 'set', **attrs)
            for weight, reward in alternatives:
                attrs = {} if weight == '100' else {'chance': weight}
                parent = ET.SubElement(branch, 'bundle', **attrs) if len(reward) > 1 else branch
                for ident, minimum, maximum in reward:
                    item_attrs = {'id': ident}
                    if minimum != '1':
                        item_attrs['count'] = minimum
                    if maximum != '0':
                        item_attrs['max_count'] = maximum
                    if len(reward) == 1:
                        item_attrs.update(attrs)
                    ET.SubElement(parent, 'item', **item_attrs)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('legacy', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    root = ET.Element('decomposable_items', {'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance',
                                           'xsi:noNamespaceSchemaLocation': 'decomposable_items.xsd'})
    root.append(ET.Comment(' Local gameplay/restored-box overrides; provenance differs from the imported retail tables. '))
    for box in ET.parse(args.legacy).getroot():
        if int(box.get('item_id')) not in DISPUTED:
            root.append(migrate(box))
    ET.indent(root, space='\t')
    args.output.write_bytes(b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(root, encoding='utf-8') + b'\n')
    print(f'OK: {len(root)-1} unrelated local definitions migrated; six PR #211 definitions supplied by PR #200.')


if __name__ == '__main__':
    main()
