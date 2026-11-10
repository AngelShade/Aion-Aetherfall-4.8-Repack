"""Exact input-boundary deltas and cumulative native-state invariants.

Candidate ledgers are unapproved. Comparison accepts a ledger only after its
review flag and reasons are set explicitly; every changed block must match both
record hashes, its input marker and its complete specific-call count deltas.
"""
from collections import Counter
import gzip
import hashlib
from itertools import zip_longest
import json

from policy import records, widget_name


def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False)


def digest(rows):
    return hashlib.sha256(('\n'.join(map(encoded, rows))+'\n').encode()).hexdigest()


def blocks(path):
    marker, body = ['@load'], []
    for record in records(path):
        if record[1] == 'input':
            yield marker, body
            marker, body = record, []
        else:
            body.append(record)
    yield marker, body


class NativeState:
    def __init__(self):
        self.values = {}
        self.commands = []

    def consume(self, rows):
        for record in rows:
            _, kind, name, args, result = record
            if kind == 'widget' and name.startswith('SetWidget'):
                target = widget_name(record)
                if target:
                    field = [target, name]
                    if name == 'SetWidgetAttr': field.append(args[2])
                    self.values['widget:' + encoded(field)] = args[2:]
            if kind != 'bridge' or result == [False]:
                continue
            if name == 'pin':
                ident, x = args[:2]
                if ident == 0:
                    self.values = {key:value for key,value in self.values.items() if not key.startswith('pin:')}
                else:
                    key = 'pin:' + encoded([ident,args[7]])
                    if x is None:
                        self.values.pop(key, None)
                    else:
                        self.values[key] = args[1:]
            elif name == 'backdrop':
                key = 'backdrop:' + encoded([args[0],*args[2:4]])
                # Multiplier 1 releases a retained selection in the real bridge.
                if args[1] == 1:
                    self.values.pop(key, None)
                else:
                    self.values[key] = args[1:]
            elif name == 'backdropclear':
                self.values = {key:value for key,value in self.values.items() if not key.startswith('backdrop:')}
            elif name == 'tippin':
                self.values['tooltip'] = args
            elif name in ('move','setshown','movable','gaps','setcamerasetting'):
                self.values[name+':'+encoded(args[:1])] = args[1:]
            elif name in ('settext','widgetshown'):
                self.values[name+':'+encoded(args[:2])] = args[2:]
            elif name == 'command':
                self.commands.append(args)

    def differences(self, other):
        result = {key:{'reference':self.values.get(key),'current':other.values.get(key)}
                  for key in sorted(self.values.keys() | other.values.keys())
                  if self.values.get(key) != other.values.get(key)}
        if self.commands != other.commands:
            result['commands'] = {'reference':self.commands,'current':other.commands}
        return result


def specific_deltas(before, after):
    old = Counter(encoded(row[1:]) for row in before)
    new = Counter(encoded(row[1:]) for row in after)
    return [{'record':json.loads(row),'reference':old[row],'current':new[row]}
            for row in sorted(old.keys() | new.keys()) if old[row] != new[row]]


def candidate(reference, current):
    states = NativeState(), NativeState()
    changed, inputs, mismatched = [], 0, []
    for index, pair in enumerate(zip_longest(blocks(reference), blocks(current))):
        before, after = pair
        if before is None or after is None or before[0] != after[0]:
            raise AssertionError('Original input stream differs at boundary ' + str(index))
        inputs += 1
        states[0].consume(before[1]); states[1].consume(after[1])
        native = states[0].differences(states[1])
        if native:
            mismatched.append({'boundary':index,'input':before[0],'nativeState':native})
        if before[1] != after[1]:
            changed.append({'boundary':index,'input':before[0],
                            'referenceSha256':digest(before[1]),'currentSha256':digest(after[1]),
                            'referenceRecords':len(before[1]),'currentRecords':len(after[1]),
                            'nativeState':native,'specificCallDeltas':specific_deltas(before[1],after[1]),
                            'reason':'UNREVIEWED'})
    return {'reviewed':False,'inputBoundaries':inputs,'changedBlocks':changed,
            'nativeStateMismatches':mismatched}


def compare_reviewed(reference, current, fixture):
    if not fixture.exists():
        print('No reviewed migration ledger:', fixture)
        return False
    opener = gzip.open if fixture.suffix == '.gz' else open
    with opener(fixture, 'rt', encoding='utf-8') as stream:
        expected = json.load(stream)
    if not expected.get('reviewed') or any(entry.get('reason') in (None,'','UNREVIEWED') for entry in expected['changedBlocks']):
        print('Migration ledger has not been explicitly reviewed')
        return False
    actual = candidate(reference,current)
    if actual['nativeStateMismatches']:
        print('Native state differs after an original input boundary')
        return False
    if actual['inputBoundaries'] != expected['inputBoundaries']:
        print('Input-boundary count differs from reviewed migration')
        return False
    old = {entry['boundary']:entry for entry in expected['changedBlocks']}
    new = {entry['boundary']:entry for entry in actual['changedBlocks']}
    if old.keys() != new.keys():
        print('Changed input blocks differ from reviewed migration')
        return False
    for index in old:
        reviewed, observed = dict(old[index]), dict(new[index])
        reviewed.pop('reason'); observed.pop('reason')
        if reviewed != observed:
            print('First unreviewed migration difference at boundary',index,observed['input'])
            return False
    # A reviewed state difference must be local to a specific changed block;
    # subsequent equal blocks may not silently retain a different native state.
    if expected.get('nativeStateMismatches',[]) != actual['nativeStateMismatches']:
        print('Cumulative native state differs from reviewed migration')
        return False
    print(f"PASS: {actual['inputBoundaries']} original boundaries; {len(new)} exact reviewed migration blocks; "
          f"{len(actual['nativeStateMismatches'])} explicit native-state differences")
    return True
