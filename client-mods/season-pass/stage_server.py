"""Create a reviewable server-only deployment bundle without changing a running server."""
import hashlib
import json
import shutil
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from incremental_server import compose

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output/season-pass/server-v9'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    if OUT.exists():raise ValueError('Use a fresh staging directory')
    patch=compose(ROOT,OUT)
    shutil.copytree(ROOT/'game-server/config/season-pass',OUT/'config/season-pass')
    deployed=ROOT/'target-deploy/game-server'
    boxes_rel='data/static_data/decomposable_items/decomposable_items.xml'
    restored={188052187,188052555,188053068,188053975,188053976}|set(range(188053979,188053990))
    new_boxes={int(e.get('item_id')):e for e in ET.parse(ROOT/'game-server'/boxes_rel).getroot()}
    old_boxes={int(e.get('item_id')):e for e in ET.parse(deployed/boxes_rel).getroot()}
    assert new_boxes.keys()==old_boxes.keys(),'Box ID set changed since deployment'
    for id,old in old_boxes.items():
        if id in restored:
            assert len(new_boxes[id])>0
            assert len(old)==0 or ET.tostring(old)==ET.tostring(new_boxes[id]),'Later deployed restoration exists: '+str(id)
        else:assert ET.tostring(old)==ET.tostring(new_boxes[id]),'Unrelated deployed box differs: '+str(id)
    box_target=OUT/boxes_rel;box_target.parent.mkdir(parents=True);shutil.copyfile(ROOT/'game-server'/boxes_rel,box_target)
    dependencies=[]
    for rel in ['data/static_data/items/item_templates.xml','data/static_data/pets/pets.xml']:
        source_data=ROOT/'game-server'/rel
        assert sha(source_data)==sha(deployed/rel),'Deployment reward data differs: '+rel
        dependencies.append(dict(path=rel,sha256=sha(source_data)))
    files=[dict(path=f.relative_to(OUT).as_posix(),sha256=sha(f),original=sha(deployed/f.relative_to(OUT)) if (deployed/f.relative_to(OUT)).is_file() else None) for f in sorted(OUT.rglob('*')) if f.is_file()]
    (OUT/'manifest.json').write_text(json.dumps(dict(feature='daeva-season-pass',deployment=str(deployed),files=files,dependencies=dependencies,incrementalJar=patch),indent=2))
    shutil.make_archive(str(OUT),'zip',OUT)
    print('OK: server bundle staged with verified compiled pass classes:',OUT.with_suffix('.zip'))
if __name__=='__main__':main()
