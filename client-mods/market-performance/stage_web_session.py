"""Preserve the installed server and all unrelated methods while refreshing web sessions."""
import copy,json,hashlib,zipfile
from pathlib import Path
from stage_cleanup import members
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'output/reconnect-fix';SERVER=ROOT/'target-deploy/game-server'
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
    jar=SERVER/'libs/game-server-4.8-SNAPSHOT.jar';name='com/aionemu/gameserver/network/aion/clientpackets/CM_ENTER_WORLD.class';helper='com/aionemu/gameserver/services/player/WebSessionService.class'
    edits={name:(OUT/'patched'/name).read_bytes(),helper:(OUT/'compiled'/helper).read_bytes()}
    with zipfile.ZipFile(jar) as old:
        f,m,c=members(old.read(name));nf,nm,nc=members(edits[name]);assert f==nf and m.keys()==nm.keys()
        assert {k[0] for k in m if m[k]!=nm[k]}=={'runImpl'}
        assert all(nc.get(i)==v for i,v in c.items()),'Existing constants changed'
        assert helper not in old.namelist()
        with zipfile.ZipFile(OUT/jar.name,'w') as new:
            new.comment=old.comment
            for e in old.infolist():new.writestr(copy.copy(e),edits.get(e.filename,old.read(e)))
            new.writestr(helper,edits[helper])
        with zipfile.ZipFile(OUT/jar.name) as new:
            assert set(new.namelist())==set(old.namelist())|{helper}
            for n in old.namelist():assert new.read(n)==edits.get(n,old.read(n)),n
        preserved=len(old.namelist())-1
    (OUT/'manifest.json').write_text(json.dumps(dict(originalJar=sha(jar.read_bytes()),installedJar=sha((OUT/jar.name).read_bytes()),changedClasses={k:sha(v) for k,v in edits.items()},preservedJarEntries=preserved),indent=2))
    print('PASS:',preserved,'other JAR entries and unedited method bytes identical')
if __name__=='__main__':main()
