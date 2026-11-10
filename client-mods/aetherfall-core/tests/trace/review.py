"""Review and re-record the migration ledger after an intended behaviour change.

Compares a fresh trace of the current source with the frozen reference, lists
every input block that is new to the ledger or whose difference changed, with
its call-count deltas, and writes the ledger only with --accept REASON. Blocks
whose difference is unchanged keep their recorded reason. Native state must
still match at every original input boundary; that is never reviewable away.

    python review.py REFERENCE CURRENT LEDGER            list what changed
    python review.py REFERENCE CURRENT LEDGER --accept "why these blocks changed"
"""
import argparse
from collections import Counter
import gzip
import json
from pathlib import Path

from boundaries import candidate


def load(path):
    opener = gzip.open if path.suffix == '.gz' else open
    with opener(path, 'rt', encoding='utf-8') as stream:
        return json.load(stream)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reference', type=Path)
    parser.add_argument('current', type=Path)
    parser.add_argument('ledger', type=Path)
    parser.add_argument('--accept', help='reason recorded for every new or changed block')
    args = parser.parse_args()
    actual = candidate(args.reference, args.current)
    if actual['nativeStateMismatches']:
        raise SystemExit('Native state differs after %d input boundaries; this cannot be accepted.' % len(actual['nativeStateMismatches']))
    previous = load(args.ledger) if args.ledger.exists() else {'changedBlocks': []}
    known = {entry['boundary']: entry for entry in previous['changedBlocks']}
    fresh, kept, deltas = [], 0, Counter()
    for entry in actual['changedBlocks']:
        old = known.get(entry['boundary'])
        same = old is not None and {k: v for k, v in old.items() if k != 'reason'} == {k: v for k, v in entry.items() if k != 'reason'}
        if same:
            entry['reason'] = old['reason']; kept += 1
            continue
        before = {json.dumps(d['record']): d for d in (old or {}).get('specificCallDeltas', [])}
        for delta in entry['specificCallDeltas']:
            was = before.get(json.dumps(delta['record']))
            if was is None or (was['reference'], was['current']) != (delta['reference'], delta['current']):
                record = delta['record']
                key = (record[0], record[1], str(record[2][1])[:26] if record[0] == 'widget' and len(record[2]) > 1 else '')
                deltas[key] += delta['current'] - delta['reference'] - ((was['current'] - was['reference']) if was else 0)
        fresh.append(entry)
    gone = sorted(set(known) - {entry['boundary'] for entry in actual['changedBlocks']})
    print('%d input boundaries; %d changed blocks: %d unchanged since review, %d new or different, %d no longer differ'
          % (actual['inputBoundaries'], len(actual['changedBlocks']), kept, len(fresh), len(gone)))
    for key, count in sorted(deltas.items(), key=lambda item: -abs(item[1]))[:40]:
        print('  %+6d  %s %s %s' % (count, *key))
    inputs = Counter(json.dumps(entry['input'][:3]) for entry in fresh)
    for name, count in inputs.most_common(12):
        print('  input x%d: %s' % (count, name[:120]))
    if not args.accept:
        return
    for entry in fresh:
        entry['reason'] = args.accept
    actual['reviewed'] = True
    opener = gzip.open if args.ledger.suffix == '.gz' else open
    with opener(args.ledger, 'wt', encoding='utf-8') as stream:
        json.dump(actual, stream)
    print('ledger written:', args.ledger)


if __name__ == '__main__':
    main()
