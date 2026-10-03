"""Add read-only discovery while preserving all other installed classes/method bytes."""
import copy,json,hashlib,zipfile
from pathlib import Path
from stage_cleanup import members
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'output/market-browse';SERVER=ROOT/'target-deploy/game-server'
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
    jar=SERVER/'libs/game-server-4.8-SNAPSHOT.jar';name='com/aionemu/gameserver/services/CentralMarketService.class'
    edits={name:(OUT/'patched'/name).read_bytes()}
    for p in (OUT/'compiled/com/aionemu/gameserver/services').glob('CentralMarketBrowse*.class'):edits[str(p.relative_to(OUT/'compiled')).replace('\\','/')]=p.read_bytes()
    with zipfile.ZipFile(jar) as old:
        f,m,c=members(old.read(name));nf,nm,nc=members(edits[name]);assert f==nf and m.keys()==nm.keys()
        assert {k[0] for k in m if m[k]!=nm[k]}=={'catalogView','templateView'}
        key=next(k for k in m if k[0]=='templateView');assert m[key][2:]==nm[key][2:] and int.from_bytes(m[key][:2],'big')&~2==int.from_bytes(nm[key][:2],'big'),'templateView body changed'
        assert all(nc.get(i)==v for i,v in c.items()),'Old constants changed'
        assert all(n not in old.namelist() for n in edits if n!=name)
        with zipfile.ZipFile(OUT/jar.name,'w') as new:
            new.comment=old.comment
            for e in old.infolist():new.writestr(copy.copy(e),edits.get(e.filename,old.read(e)))
            for n,b in edits.items():
                if n!=name:new.writestr(n,b)
        with zipfile.ZipFile(OUT/jar.name) as new:
            assert set(new.namelist())==set(old.namelist())|set(edits)
            for n in old.namelist():assert new.read(n)==edits.get(n,old.read(n)),n
            for n,b in edits.items():assert new.read(n)==b,n
        preserved=len(old.namelist())-1
    (OUT/'manifest.json').write_text(json.dumps(dict(originalJar=sha(jar.read_bytes()),installedJar=sha((OUT/jar.name).read_bytes()),changedClasses={k:sha(v) for k,v in edits.items()},preservedJarEntries=preserved),indent=2))
    print('PASS:',preserved,'other JAR entries and unedited methods identical')
if __name__=='__main__':main()
