"""Read matching 4.8 client tribes and report pre-base world-spawn overlaps."""
from pathlib import Path
import sys,xml.etree.ElementTree as ET,json
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'client-mods/expanded-warehouse'))
from codec import read_pak,binary_xml
client=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
out=ROOT/'target/base-camps';out.mkdir(exist_ok=True,parents=True)
with read_pak(client/'Data/Npcs/Npcs.pak') as pak:
    data=pak.read('npc_tribe_relation.xml');tribes=binary_xml(data)
    selected=[ET.tostring(t,encoding='unicode') for t in tribes if 'ldf_v_' in t.get('Tribe','').lower()]
    print('\n'.join(selected));(out/'client-camp-tribes.json').write_text(json.dumps(selected,indent=2))
    wanted=set()
    for p in (ROOT/'target-deploy/game-server/data/static_data/spawns/Bases').glob('*.xml'):
        if p.name[:9] in {'210020000','210040000','220020000','220040000'}:
            wanted.update(int(s.get('npc_id')) for s in ET.parse(p).findall('.//spawn'))
    rows=[]
    for name in ['client_npcs_monster.xml','client_npcs_npc.xml']:
        payload=pak.read(name);npcs=binary_xml(payload)
        for n in npcs:
            if int(n.findtext('id','0')) in wanted:rows.append({c.tag:c.text for c in n if c.tag in {'id','name','tribe','tribe_type','ai','srange','sangle','race','mesh'}})
    (out/'client-camp-npcs.json').write_text(json.dumps(rows,indent=2))
root=ROOT/'target-deploy/game-server/data/static_data/spawns';overlaps=[]
npcs={n.get('npc_id'):n.attrib for n in ET.parse(ROOT/'target-deploy/game-server/data/static_data/npcs/npc_templates.xml').getroot()}
for p in (root/'Bases').glob('*.xml'):
    if p.name[:9] not in {'210020000','210040000','220020000','220040000'}:continue
    for b in ET.parse(p).findall('.//base_spawn'):
        flag=next(s for s in b.findall('./occupier_template/spawn') if s.get('handler')=='FLAG').find('spot')
        for s in ET.parse(root/'Npcs'/p.name).findall('.//spawn'):
            for spot in s.findall('spot'):
                distance=((float(spot.get('x'))-float(flag.get('x')))**2+(float(spot.get('y'))-float(flag.get('y')))**2)**.5
                if distance<12:
                    row=dict(file=p.name,base=b.get('id'),npc=s.get('npc_id'),spot=spot.attrib,distance=distance,template=npcs.get(s.get('npc_id')))
                    overlaps.append(row);print('OVERLAP',row)
(out/'world-overlaps.json').write_text(json.dumps(overlaps,indent=2))
