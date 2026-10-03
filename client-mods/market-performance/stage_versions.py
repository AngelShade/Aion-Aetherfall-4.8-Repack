"""Patch exactly two UTF8 asset version constants in the deployed service class."""
import argparse,copy,hashlib,json,struct,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
CLASS='com/aionemu/gameserver/services/MarketplaceService.class'
def sha(data):return hashlib.sha256(data).hexdigest()
def patch(source):
    if source[:4]!=bytes.fromhex('cafebabe'):raise ValueError('Expected class')
    size=struct.unpack_from('>H',source,8)[0];offset=10;index=1;result=bytearray(source[:10]);changes=[]
    sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index<size:
        tag=source[offset];offset+=1;result.append(tag)
        if tag==1:
            n=struct.unpack_from('>H',source,offset)[0];offset+=2;old=source[offset:offset+n];offset+=n;new=old
            for prior,after in [(b'marketplace.css?v=14',b'marketplace.css?v=15'),(b'marketplace.js?v=1\'',b'marketplace.js?v=2\'')]:
                if prior in new:
                    if new.count(prior)!=1:raise ValueError('Duplicate asset version constant')
                    new=new.replace(prior,after);changes.append(prior.decode())
            result+=struct.pack('>H',len(new))+new
        else:
            n=sizes[tag];result+=source[offset:offset+n];offset+=n
            if tag in (5,6):index+=1
        index+=1
    result+=source[offset:]
    assert changes==['marketplace.css?v=14',"marketplace.js?v=1'"],changes
    assert len(result)==len(source)
    # All non-constant-pool bytes, including every method body, are identical.
    assert result[offset:]==source[offset:]
    return bytes(result)
def main():
    p=argparse.ArgumentParser();p.add_argument('--server',type=Path,default=ROOT/'target-deploy/game-server');p.add_argument('--output',type=Path,default=ROOT/'output/market-performance/versions');a=p.parse_args();server=a.server.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
    jar=server/'libs/game-server-4.8-SNAPSHOT.jar';original=jar.read_bytes()
    with zipfile.ZipFile(jar) as archive:
        source=archive.read(CLASS);changed=patch(source);newjar=out/jar.name
        with zipfile.ZipFile(newjar,'w') as target:
            target.comment=archive.comment
            for entry in archive.infolist():target.writestr(copy.copy(entry),changed if entry.filename==CLASS else archive.read(entry))
        with zipfile.ZipFile(newjar) as verified:
            assert archive.namelist()==verified.namelist()
            for name in archive.namelist():assert verified.read(name)==(changed if name==CLASS else archive.read(name)),name
        count=len(archive.namelist())-1
    output=out/'classes'/CLASS;output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(changed)
    (out/'manifest.json').write_text(json.dumps(dict(serverRoot=str(server),originalJar=sha(original),installedJar=sha(newjar.read_bytes()),originalClass=sha(source),installedClass=sha(changed),preservedJarEntries=count,classPath=CLASS),indent=2),encoding='utf-8')
    print('OK: only CSS/JS version constants changed;',count,'other JAR entries and all method bodies preserved')
if __name__=='__main__':main()
