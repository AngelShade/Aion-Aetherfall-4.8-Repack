"""Stage only three authorized class edits and removal of AFK classes."""
import copy,hashlib,json,struct,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output/market-custody';SERVER=ROOT/'target-deploy/game-server'
CHANGES={'com/aionemu/gameserver/services/CentralMarketService.class':{'snapshot'},'com/aionemu/gameserver/GameServer.class':{'main'},'com/aionemu/gameserver/configs/Config.class':{'<clinit>','getClasses'}}
REMOVED={'com/aionemu/gameserver/services/AfkKeepAliveService.class','com/aionemu/gameserver/services/AfkKeepAliveService$SingletonHolder.class','com/aionemu/gameserver/configs/main/AfkKeepAliveConfig.class'}
def sha(data):return hashlib.sha256(data).hexdigest()
def members(data):
    offset=10;index=1;constants={};count=struct.unpack_from('>H',data,8)[0];sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index<count:
        tag=data[offset];offset+=1
        if tag==1:
            n=struct.unpack_from('>H',data,offset)[0];offset+=2;constants[index]=data[offset:offset+n];offset+=n
        else:offset+=sizes[tag]
        index+=2 if tag in (5,6) else 1
    offset+=6;n=struct.unpack_from('>H',data,offset)[0];offset+=2+n*2
    def read():
        nonlocal offset
        n=struct.unpack_from('>H',data,offset)[0];offset+=2;out={}
        for _ in range(n):
            start=offset;_,name,desc,attrs=struct.unpack_from('>HHHH',data,offset);offset+=8
            for _ in range(attrs):length=struct.unpack_from('>I',data,offset+2)[0];offset+=6+length
            out[(constants[name].decode(),constants[desc].decode())]=data[start:offset]
        return out
    fields=read();methods=read();return fields,methods,constants
def main():
    live=SERVER/'libs/game-server-4.8-SNAPSHOT.jar';target=OUT/live.name;changed={k:(OUT/'patched'/k).read_bytes() for k in CHANGES};original=live.read_bytes()
    with zipfile.ZipFile(live) as old:
        assert REMOVED<=set(old.namelist())
        for name,allowed in CHANGES.items():
            f,m,c=members(old.read(name));nf,nm,nc=members(changed[name]);assert f==nf and m.keys()==nm.keys(),name
            edits={k[0] for k in m if m[k]!=nm[k]};assert edits==allowed,(name,edits)
            assert all(nc.get(i)==value for i,value in c.items()),'Existing constant pool changed'
            if name.endswith('CentralMarketService.class'):
                predicates=[value.decode() for value in nc.values() if value.startswith(b" AND (o.state IN ('OPEN','QUEUED') OR EXISTS")];assert len(predicates)==1
                # javac's concat recipe appends the runtime filter as U+0001.
                predicate=predicates[0];assert predicate.count('\x01')<=1
                if predicate.endswith('\x01'):predicate=predicate[:-1]
                assert '\x01' not in predicate
                (OUT/'order-visibility.sql').write_text(predicate,encoding='utf-8')
            print('PASS:',name,'only',sorted(edits),'changed; other method bytes and fields identical')
        with zipfile.ZipFile(target,'w') as new:
            new.comment=old.comment
            for entry in old.infolist():
                if entry.filename not in REMOVED:new.writestr(copy.copy(entry),changed.get(entry.filename,old.read(entry)))
        with zipfile.ZipFile(target) as new:
            assert set(new.namelist())==set(old.namelist())-REMOVED
            for name in new.namelist():assert new.read(name)==changed.get(name,old.read(name)),name
        preserved=len(old.namelist())-len(CHANGES)-len(REMOVED)
    (OUT/'manifest.json').write_text(json.dumps(dict(serverRoot=str(SERVER),originalJar=sha(original),installedJar=sha(target.read_bytes()),changedClasses={k:sha(v) for k,v in changed.items()},removedClasses=sorted(REMOVED),preservedJarEntries=preserved),indent=2),encoding='utf-8')
    print('PASS: staged JAR;',preserved,'unrelated entries identical; AFK classes removed')
if __name__=='__main__':main()
