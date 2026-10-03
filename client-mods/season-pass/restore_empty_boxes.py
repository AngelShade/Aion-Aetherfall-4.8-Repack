"""Restore only sixteen documented empty native box IDs, without changing others.

This preserves exact items, quantities, class/faction conditions and published
weights. Use --apply to write; otherwise verify the checked-in definitions.
"""
import argparse
from decimal import Decimal
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'game-server/data/static_data/decomposable_items/local_decomposable_items.xml'
SOURCES=Path(__file__).with_name('box-content-sources.json')

def definitions():
    manifest=json.loads(SOURCES.read_text(encoding='utf-8'));result={}
    for box in manifest['boxes']:
        groups=box['groups'];out=ET.Element('decomposable',item_id=str(box['id']),override='true')
        for group in groups:
            attrs={}
            if group['race']!='PC_ALL':attrs['race']=group['race']
            if group['player_class']:attrs['player_classes']=group['player_class']
            dest=ET.SubElement(out,'set',**attrs)
            assert sum(Decimal(i['chance']) for i in group['items'])==100
            for item in group['items']:
                attrs=dict(id=str(item['id']),chance=item['chance'])
                if item['quantity']!=1:attrs['count']=str(item['quantity'])
                ET.SubElement(dest,'item',**attrs)
        ET.indent(out,space='\t',level=1)
        result[box['id']]='\t'+ET.tostring(out,encoding='unicode')
    assert len(result)==16
    return result

def signature(box):
    # Ignore formatting/default attributes and the order of mutually exclusive sets.
    result=[]
    for group in box.findall('set'):
        condition=(group.get('race','PC_ALL'),tuple(sorted(group.get('player_classes','').split())))
        rewards=tuple(sorted((i.get('id'),int(i.get('count','1')),Decimal(i.get('chance','100'))) for i in group.findall('item')))
        assert len(rewards)==len(group),'Restored boxes must contain single-item alternatives'
        result.append((condition,rewards))
    return sorted(result)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    original=DATA.read_bytes();text=original.decode('utf-8');newline='\r\n' if b'\r\n' in original else '\n'
    untouched_before=ET.fromstring(original)
    expected=definitions()
    for item,block in expected.items():
        pattern=rf'\t<decomposable item_id="{item}"(?:[ \t]+[a-z_]+="[^"]*")*[ \t]*(?:/>|>.*?</decomposable>)(?:<!--[^\r\n]*-->)?'
        matches=list(re.finditer(pattern,text,re.S));assert len(matches)==1,f'Unique entry required: {item}'
        current=matches[0].group();node=ET.fromstring(current.split('<!--')[0])
        name=re.search(r'<!-- (.*?) -->',current)
        rendered=block+(f'<!-- {name.group(1)} -->' if name else '')
        rendered=rendered.replace('\n',newline)
        if signature(node)==signature(ET.fromstring(block)):
            continue
        assert args.apply,f'Unrestored definition: {item}'
        assert len(node)==0 and node.get('selectable','false')=='false',f'Refusing to overwrite existing nonempty box: {item}'
        text=text[:matches[0].start()]+rendered+text[matches[0].end():]
    after=ET.fromstring(text)
    prior={int(e.get('item_id')):ET.tostring(e) for e in untouched_before if int(e.get('item_id')) not in expected}
    remaining={int(e.get('item_id')):ET.tostring(e) for e in after if int(e.get('item_id')) not in expected}
    assert prior==remaining,'Unrelated box definitions changed'
    if args.apply:DATA.write_bytes(text.encode('utf-8'))
    print('OK: 16 original box IDs restored/verified; exact published weights and quantities; class/faction conditions retained; unrelated boxes preserved.')

if __name__=='__main__':
    try:main()
    except Exception as e:print('FAIL:',e);raise
