"""Read-only inventory of terrain surfaces, environment presets and shared VFX."""
import sys, json, io, struct, collections
from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/'expanded-warehouse'))
from codec import read_pak,binary_xml
client=Path(sys.argv[1]); out=ROOT/'research'; out.mkdir(exist_ok=True)
result={'zones':[], 'textures':[], 'effects':[]}; previews=[]
for p in sorted(client.glob('Levels/*/Level.pak')):
    zone={'zone':p.parent.name,'archive':p.relative_to(client).as_posix(),'surfaces':[], 'environments':[]}
    with read_pak(p) as z:
        for n in z.namelist():
            if n.lower() not in ('leveldata.xml','mission_mission0.xml','materials.xml'): continue
            data=z.read(n)
            try: tree=binary_xml(data) if data[0]==128 else ET.fromstring(data)
            except Exception as e: zone.setdefault('errors',[]).append(str(e)); continue
            if n.lower()=='leveldata.xml':
                for node in tree.iter():
                    if node.tag.lower()=='surfacetype': zone['surfaces'].append(dict(node.attrib))
            if n.lower()=='mission_mission0.xml':
                zone['environments']=[{node.tag:dict(node.attrib) for node in env} for env in tree.iter('Environment')]
    result['zones'].append(zone)
for p in sorted(client.glob('Levels/*/*.pak')):
    if p.stem.lower()!=p.parent.name.lower(): continue
    with read_pak(p) as z:
        for n in z.namelist():
            if not n.lower().startswith('detail/') or not n.lower().endswith('.dds'): continue
            b=z.read(n); row={'archive':p.relative_to(client).as_posix(),'entry':n,'sha256':__import__('hashlib').sha256(b).hexdigest()}
            if b[:4]!=b'DDS ': continue
            row.update(size=list(struct.unpack_from('<II',b,12))[::-1],format=b[84:88].decode('ascii',errors='replace'))
            result['textures'].append(row)
            if len(previews)<150 and any(x in n.lower() for x in ['snow','sand','lava','ice','mud','rock','grass','dirt','field','road']):
                try:
                    im=Image.open(io.BytesIO(b)).convert('RGB'); im.thumbnail((120,100)); previews.append((p.parent.name+'/'+n,im))
                except Exception: pass
for relative in ['effects/effects_Textures.pak','Textures/Animated/Water/Water.pak','Textures/Detail/Detail.pak']:
    with read_pak(client/relative) as z:
        for n in z.namelist():
            if not n.lower().endswith('.dds'): continue
            b=z.read(n)
            if b[:4]!=b'DDS ': continue
            result['effects'].append({'archive':relative,'entry':n,'size':list(struct.unpack_from('<II',b,12))[::-1],'format':b[84:88].decode('ascii',errors='replace')})
(out/'global-inventory.json').write_text(json.dumps(result,indent=2))
sheet=Image.new('RGB',(1000,((len(previews)+7)//8)*130),(25,27,32)); draw=ImageDraw.Draw(sheet)
for i,(name,im) in enumerate(previews):
    x=i%8*125;y=i//8*130;sheet.paste(im,(x,y));draw.text((x,y+101),name[:22],fill='white')
sheet.save(out/'terrain-inventory.png')
hashes=collections.Counter(x['sha256'] for x in result['textures'])
print(json.dumps({'zones':len(result['zones']),'terrainTextures':len(result['textures']),'uniqueTerrainTextures':len(hashes),'effects':len(result['effects']),'surfaceExamples':[(x['zone'],x['surfaces'][:2]) for x in result['zones'][:4]],'topDuplicates':hashes.most_common(15)},indent=2))
