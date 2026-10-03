"""Exhaustively check restored native box outcomes against saved public tables.

Also writes small production-loader fixtures copied from actual authoritative
XML. It never opens boxes on a live character or changes the deployed server.
"""
from collections import Counter
from decimal import Decimal
from pathlib import Path
import json
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
CLASSES=['GLADIATOR','TEMPLAR','ASSASSIN','RANGER','SORCERER','SPIRIT_MASTER','CLERIC','CHANTER','GUNNER','RIDER','BARD']
CLASS_IDS=[1,2,4,5,7,8,10,11,14,13,16]
checks=0
def check(ok,label):
    global checks
    checks+=1
    if not ok:raise AssertionError(label)

def main():
    subprocess.run([sys.executable,str(HERE/'restore_empty_boxes.py')],check=True)
    sources=json.loads((HERE/'box-content-sources.json').read_text(encoding='utf-8'))['boxes']
    items={int(e.get('id')):e for e in ET.parse(ROOT/'game-server/data/static_data/items/item_templates.xml').getroot()}
    folder=ROOT/'game-server/data/static_data/decomposable_items'
    boxes={};overrides={}
    for path in sorted(folder.glob('*.xml')):
        for entry in ET.parse(path).getroot():
            target=overrides if entry.get('override','false')=='true' else boxes
            ident=int(entry.get('item_id'));check(ident not in target,'no duplicate regular/override definition')
            target[ident]=entry
    boxes.update(overrides)
    client={int(row.split('\t')[0]) for row in (ROOT/'game-server/config/central-market/media/icon_sources.tsv').read_text().splitlines() if row.split('\t')[0].isdigit()}
    fixture_ids={r['id'] for r in sources}|set(range(188053750,188053772))
    for source in sources:
        box=boxes[source['id']]
        check(box.get('selectable','false')=='false','native random box stays random')
        check(items[source['id']].find('actions/decompose') is not None,'existing native open action')
        check(source['id'] in client,'native box artwork')
        roles=CLASSES if source['id'] in [188053975,188053976] or source['id']<188053979 else [CLASSES[source['id']-188053979]]
        for race in ['ELYOS','ASMODIANS']:
            for pc in roles:
                expected=Counter()
                for group in source['groups']:
                    if group['race'] not in ['PC_ALL',race] or group['player_class'] not in [None,pc]:continue
                    for i in group['items']:
                        if i['race'] in ['PC_ALL',race]:expected[(i['id'],i['quantity'])]+=Decimal(i['chance'])
                actual=Counter()
                applicable=[g for g in box.findall('set') if g.get('race','PC_ALL') in ['PC_ALL',race]
                    and (not g.get('player_classes') or pc in g.get('player_classes').split())]
                check(len(applicable)==1,f"one eligible branch: {source['id']} {pc} {race}")
                for group in applicable:
                    check(group.get('chance','100')=='100','branch always fires')
                    for i in group.findall('item'):
                        id=int(i.get('id'));qty=int(i.get('count','1'))
                        check('max_count' not in i.attrib,'published restored quantities are fixed')
                        check(id in items and id in client,'all contents exist in matching server/client')
                        actual[(id,qty)]+=Decimal(i.get('chance','100'))
                        if source['id']>=188053979:
                            t=items[id];stigma=t.find('stigma')
                            check(stigma is not None and stigma.get('chargeable')=='true','native 4.8 chargeable stigma')
                            check(int(t.get('restrict').split()[CLASS_IDS[CLASSES.index(pc)]])>0,'usable by named class')
                check(actual==expected,f"exact source IDs, quantities and weights for {source['id']} {pc} {race}")
                check(sum(actual.values())==100,'every eligible distribution sums to 100 percent')
    # Fixture imports the restored boxes and their existing native nested bundles.
    decomp=ET.Element('decomposable_items');needed=set(fixture_ids)
    for id in sorted(fixture_ids):
        decomp.append(boxes[id])
        needed.update(int(i.get('id')) for i in boxes[id].findall('.//item'))
    templates=ET.Element('item_templates')
    for id in sorted(needed):templates.append(items[id])
    out=ROOT/'output/season-pass/box-check';out.mkdir(exist_ok=True)
    ET.ElementTree(decomp).write(out/'boxes.xml',encoding='utf-8',xml_declaration=True)
    ET.ElementTree(templates).write(out/'items.xml',encoding='utf-8',xml_declaration=True)
    print(f'OK: {checks} restored-box checks: exact published outcomes, both factions, eleven advanced classes, quantities, native icons and chargeable stigmas.')

if __name__=='__main__':
    try:main()
    except Exception as e:print('FAIL:',e);raise
