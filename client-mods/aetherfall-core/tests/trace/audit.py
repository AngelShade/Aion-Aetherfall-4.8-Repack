"""Inventory exact retained trace deltas; this report does not approve them."""
import argparse
from collections import Counter, defaultdict
import gzip
import json
from pathlib import Path
import re
from urllib.parse import unquote

from policy import records

MUTATIONS = frozenset(('pin','tippin','backdrop','backdropclear','backdropprune','gaps',
                      'move','setshown','movable','setcamerasetting','settext','widgetshown','command'))


def decode_save(name, data):
    assert len(data.encode('utf-8')) <= 32768, 'oversize save ' + name
    if data.startswith('AFUI1 '):
        assert re.fullmatch(r'AFUI1 \d+\n.*\nEND\n', data, re.S), 'invalid AFUI1 framing ' + name
        return {'format':'AFUI1','records':data.splitlines()[1:-1]}
    if data.startswith('AFUI2 '):
        match = re.fullmatch(r'(AFUI2 INDEX|AFUI2 \d+ (?:character|account))\n(.*)\nCHECK ([0-9a-f]{4})\nEND\n', data, re.S)
        if match:
            body = match[2]
            checksum = 0
            for byte in body.encode('utf-8'):
                checksum = (checksum*31+byte)%65521
            assert f'{checksum:04x}' == match[3], 'invalid save checksum ' + name
            rows = [[unquote(field) for field in line.split('|')] for line in body.splitlines()]
            return {'format':match[1],'records':rows}
    # The owner's export-file action uses a share envelope, not disk records.
    if name == 'afui-export.cfg':
        value = data.strip()
        match = re.fullmatch(r'(AFUI[12])!(\d+)!(.*)!([0-9a-f]{4})', value, re.S)
        assert match and len(match[3]) == int(match[2]), 'invalid sharing envelope'
        checksum = 0
        for byte in match[3].encode('utf-8'):
            checksum = (checksum*31+byte)%65521
        assert f'{checksum:04x}' == match[4], 'invalid share checksum'
        return {'format':match[1]+' share','records':match[3].split(';')}
    raise AssertionError('unrecognized saved file ' + name)


def inventory(path):
    counts, effects, inputs, saves = Counter(), defaultdict(list), [], []
    for record in records(path):
        scenario, kind, name, args, results = record
        counts[kind+':'+name] += 1
        if kind == 'input':
            inputs.append(record)
        if kind == 'bridge' and name in MUTATIONS:
            effects[scenario].append(record)
        if kind == 'bridge' and name == 'save':
            saves.append({'scenario':scenario,'name':args[0],'result':results,
                          'decoded':decode_save(args[0],args[1])})
    return counts, effects, inputs, saves


def audit(reference, current):
    old, new = inventory(reference), inventory(current)
    deltas = {api:{'reference':old[0][api],'current':new[0][api]}
              for api in sorted(old[0].keys() | new[0].keys()) if old[0][api] != new[0][api]}
    effects = {}
    for scenario in old[1].keys() | new[1].keys():
        before, after = old[1][scenario], new[1][scenario]
        if before != after:
            first = next((i for i,(a,b) in enumerate(zip(before,after)) if a != b), min(len(before),len(after)))
            effects[scenario] = {'referenceCount':len(before),'currentCount':len(after),
                                 'firstReference':before[first:first+1],'firstCurrent':after[first:first+1]}
    return {'approved':False,'inputStreamIdentical':old[2] == new[2],
            'retainedApiCountDeltas':deltas,'mutationDifferences':effects,
            'referenceSaves':old[3],'currentSaves':new[3]}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reference',type=Path)
    parser.add_argument('current',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    result=audit(args.reference,args.current)
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('Original input stream identical:',result['inputStreamIdentical'])
    print('Retained API count deltas:',json.dumps(result['retainedApiCountDeltas']))
    print('Scenarios with native mutation differences:',json.dumps(result['mutationDifferences']))
    print('Validated saved envelopes:',len(result['referenceSaves']),'->',len(result['currentSaves']))
    print('Unapproved audit report:',args.output)


if __name__=='__main__':
    main()
