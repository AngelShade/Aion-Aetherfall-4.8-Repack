"""Record a pinned PR snapshot and independently downloaded item tables.

Input is output/pr211-review, populated during the read-only GitHub/Codex/client
research. This records evidence; it does not claim publisher-certified 4.8 odds.
"""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/pr211-review'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    pr = read(OUT / 'pr200.json')
    jobs = read(OUT / 'contents-downloads.json')
    client = read(OUT / 'client48-evidence.json')
    manifest = {
        'researched': '2026-10-03',
        'upstream': {
            'pr': 'https://github.com/beyond-aion/aion-server/pull/200',
            'commit': pr['head']['sha'],
            'base': pr['base']['sha'],
            'data_sources_claimed_by_author': ['4.9 client disassembly XML structure',
                                              '4.6 and 5.8 retail server chances and counts matched by cName'],
            'limitations': 'Original retail server datasets and exact historical 4.8 odds were not independently acquired. '
                           'US Codex tables are secondary corroboration, not NCsoft certification. '
                           'Midsummer tooltip is generic and does not establish the one-shard pool.'
        },
        'files': {}, 'boxes': [], 'client48': client,
        'packet_review': 'https://github.com/beyond-aion/aion-server/pull/200#issuecomment-5658985753'
    }
    for name in ['decomposable_items.xml', 'custom_decomposable_items.xml']:
        path = OUT / 'pr200/game-server/data/static_data/decomposable_items' / name
        manifest['files'][name] = {
            'sha256': digest(path),
            'url': f"https://github.com/beyond-aion/aion-server/blob/{pr['head']['sha']}/game-server/data/static_data/decomposable_items/{name}"
        }
    for ident in sorted({job['id'] for job in jobs}):
        box = {'id': ident, 'name': client['strings'][client['items'][str(ident)]['desc']],
               'metadata48': f'https://aioncodex.com/48/item/{ident}/',
               'contents_page': f'https://aioncodex.com/us/item/{ident}/', 'groups': []}
        for job in jobs:
            if job['id'] != ident:
                continue
            path = OUT / job['file']
            rows = read(path)['aaData']
            box['groups'].append({'url': job['url'], 'sha256': digest(path), 'branch_chance': '100',
                                  'items': [{'id': row[0], 'quantity': row[4], 'chance': row[5].rstrip('%')} for row in rows]})
        box['finding'] = ('Angel Wings pool is corroborated; duplicate of PR200.' if ident == 188052918 else
                          'PR211 pool is unsupported and contradicted by the retrieved tables.' if ident in [188052649, 188053634] else
                          "Correct egg identity; retrieved quantity is 200, not PR211's 50.")
        if ident == 188053634:
            box['finding'] += ' Historical multi-item pool remains unresolved.'
        manifest['boxes'].append(box)
    Path(__file__).with_name('sources.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('OK: pinned hashes, nine independent contents tables and installed client evidence recorded.')


if __name__ == '__main__':
    main()
