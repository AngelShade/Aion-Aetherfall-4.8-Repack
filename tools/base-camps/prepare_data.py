"""Prepare exact camp data repairs from the native terrain audit, without installing."""
import csv,json,re,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];SERVER=ROOT/'target-deploy/game-server';OUT=ROOT/'target/base-camps/data'
rows=[r for r in csv.DictReader((ROOT/'target/base-camps/before.tsv').open(),delimiter='\t') if r['kind']=='SPAWN']
fixes=[];files={}
for folder in ['Bases','Npcs']:
    for filename in ['210020000_Eltnen.xml','210040000_Heiron.xml','220020000_Morheim.xml','220040000_Beluslan.xml']:
        rel='data/static_data/spawns/'+folder+'/'+filename;p=SERVER/rel;original=p.read_bytes();text=original.decode('utf-8');base=None;owner=None;npc=None;handler=None;lines=[]
        for line in text.splitlines(keepends=True):
            m=re.search(r'<base_spawn id="(\d+)"',line)
            if m:base=m[1]
            m=re.search(r'<occupier_template occupier="([^"]+)"',line)
            if m:owner=m[1]
            m=re.search(r'<spawn npc_id="(\d+)"([^>]*)>',line)
            if m:
                npc=m[1];h=re.search(r'handler="([^"]+)"',m[2]);handler=h[1] if h else None
            if '<spot ' in line:
                attr=dict(re.findall(r'(\w+)="([^"]+)"',line))
                if folder=='Bases':
                    matched=[r for r in rows if r['world']==filename[:9] and r['base']==base and r['occupier']==owner and r['handler']==handler and r['npc']==npc and abs(float(r['x'])-float(attr['x']))<.001 and abs(float(r['y'])-float(attr['y']))<.001 and abs(float(r['z'])-float(attr['z']))<.001]
                    assert len(matched)==1,(filename,base,npc,attr,matched)
                    r=matched[0]
                    if abs(float(r['delta']))>.4:
                        assert abs(float(r['delta']))<10 and float(r['ground'])==float(r['ground'])
                        line=line.replace('z="'+attr['z']+'"','z="'+r['ground']+'"')
                        fixes.append(dict(kind='height',world=int(r['world']),base=int(base),occupier=owner,handler=handler,npc=int(npc),x=float(attr['x']),y=float(attr['y']),oldZ=float(attr['z']),newZ=float(r['ground'])))
                else:
                    overlaps=json.loads((ROOT/'target/base-camps/world-overlaps.json').read_text())
                    found=[r for r in overlaps if r['file']==filename and r['npc']==npc and r['spot']==attr]
                    if found:
                        r=found[0];assert r['template']['ai']=='aggressive' and r['template'].get('type','MONSTER')=='MONSTER'
                        fixes.append(dict(kind='remove-world-spot',world=int(filename[:9]),base=int(r['base']),npc=int(npc),x=float(attr['x']),y=float(attr['y']),oldZ=float(attr['z'])))
                        continue
            lines.append(line)
        # Keep all authored roles/routes, timings, quests and non-overlapping world spots.
        payload=''.join(lines).encode('utf-8');target=OUT/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(payload)
        if payload!=original:files[rel]=dict(original=hashlib.sha256(original).hexdigest(),installed=hashlib.sha256(payload).hexdigest())
(OUT/'changes.json').write_text(json.dumps(dict(changes=fixes,files=files),indent=2))
print('OK: prepared',sum(r['kind']=='height' for r in fixes),'height repairs and',sum(r['kind']=='remove-world-spot' for r in fixes),'obsolete central world spots;',len(files),'changed XML files. No deployed files changed.')
