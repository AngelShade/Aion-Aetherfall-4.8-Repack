"""Verify imported source hashes, exact published box outcomes and local migration.

Historical truth is limited by the provenance recorded in sources.json. A pass
means the implementation matches that evidence, not that every 4.8 retail odd
has been certified. Optional --legacy checks a saved pre-migration box snapshot.
"""
import argparse
from collections import Counter
from fractions import Fraction
import hashlib
import itertools
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT / 'game-server/data/static_data/decomposable_items'
CLASSES = 'WARRIOR GLADIATOR TEMPLAR SCOUT ASSASSIN RANGER MAGE SORCERER SPIRIT_MASTER PRIEST CLERIC CHANTER ENGINEER RIDER GUNNER ARTIST BARD'.split()
checks = 0


def check(ok, message):
    global checks
    checks += 1
    if not ok:
        raise AssertionError(message)


def item_signature(item, legacy=False):
    minimum = int(item.get('min_count' if legacy else 'count', '1'))
    return int(item.get('id')), minimum, int(item.get('max_count', '0')) or minimum


def distribution(box, race, pc):
    result = Counter({(): Fraction(1)})
    for branch in box.findall('set'):
        if branch.get('race', 'PC_ALL') not in ['PC_ALL', race] or (branch.get('player_classes') and pc not in branch.get('player_classes').split()):
            continue
        outcomes = Counter()
        for alternative in branch:
            items = [alternative] if alternative.tag == 'item' else list(alternative)
            outcomes[tuple(sorted(item_signature(i) for i in items))] += Fraction(alternative.get('chance', '100')) / 100
        check(Fraction(branch.get('chance', '100')) == 100, 'audited local/six-box branches always fire')
        check(sum(outcomes.values()) == 1, 'audited alternative probabilities total exactly one')
        merged = Counter()
        for (prior, a), (reward, b) in itertools.product(result.items(), outcomes.items()):
            merged[tuple(sorted(prior + reward))] += a * b
        result = merged
    return result


def legacy_distribution(box, race, pc):
    groups = box.findall('items')
    total = sum(Fraction(g.get('chance', '100')) for g in groups)
    result = Counter()
    for group in groups:
        eligible = [i for i in group.findall('item') if i.get('race', 'PC_ALL') in ['PC_ALL', race]
                    and (not i.get('player_classes') or pc in i.get('player_classes').split())]
        result[tuple(sorted(item_signature(i, True) for i in eligible))] += Fraction(group.get('chance', '100')) / total
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy', type=Path)
    args = parser.parse_args()
    manifest = json.loads(Path(__file__).with_name('sources.json').read_text(encoding='utf-8'))
    for name, source in manifest['files'].items():
        check(hashlib.sha256((FOLDER / name).read_bytes()).hexdigest() == source['sha256'], 'exact pinned upstream file: ' + name)
    boxes, overrides = {}, {}
    for path in sorted(FOLDER.glob('*.xml')):
        for entry in ET.parse(path).getroot():
            target = overrides if entry.get('override', 'false') == 'true' else boxes
            ident = int(entry.get('item_id'))
            check(ident not in target, f'no duplicate definition {ident}')
            target[ident] = entry
    boxes.update(overrides)
    catalog = {int(e.get('id')): e for e in ET.parse(ROOT / 'game-server/data/static_data/items/item_templates.xml').getroot()}
    for ident, box in boxes.items():
        check(ident in catalog, f'parent item exists: {ident}')
        for item in box.findall('.//item'):
            check(int(item.get('id')) in catalog, f'reward exists: {ident} -> {item.get("id")}')
    disputed = {box['id'] for box in manifest['boxes']}
    for source in manifest['boxes']:
        box = boxes[source['id']]
        check(box.get('override', 'false') == 'false', 'reviewed table is not shadowed by old PR211 data')
        expected = Counter({(): Fraction(1)})
        for group in source['groups']:
            next_outcomes = Counter()
            for item in group['items']:
                next_outcomes[((item['id'], item['quantity'], item['quantity']),)] += Fraction(item['chance']) / 100
            combined = Counter()
            for (prior, a), (reward, b) in itertools.product(expected.items(), next_outcomes.items()):
                combined[tuple(sorted(prior + reward))] += a * b
            expected = combined
        for race in ['ELYOS', 'ASMODIANS']:
            for pc in CLASSES:
                check(distribution(box, race, pc) == expected, f'exact retrieved rewards/counts/weights: {source["id"]} {race} {pc}')
    if args.legacy:
        for old in ET.parse(args.legacy).getroot():
            ident = int(old.get('item_id'))
            if ident in disputed:
                continue
            check(boxes[ident].get('override') == 'true', 'local definition retains precedence')
            for race in ['ELYOS', 'ASMODIANS']:
                for pc in CLASSES:
                    check(distribution(boxes[ident], race, pc) == legacy_distribution(old, race, pc),
                          f'exact local loot preserved: {ident} {race} {pc}')
    print(f'OK: {checks} source/data/migration checks; six boxes match recorded evidence. Historical limitations remain in sources.json.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('FAIL:', error)
        raise
