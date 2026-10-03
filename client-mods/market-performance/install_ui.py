"""Deploy validated market/shop browser assets and only two JAR asset constants."""
import argparse,hashlib,json,os,shutil,tempfile,zipfile
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=['config/central-market/media/'+n for n in ['market.html','market.js','market.css']]+['config/ingameshop/media/'+n for n in ['marketplace.js','marketplace.css']]
JAR='libs/game-server-4.8-SNAPSHOT.jar'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def replace(path,data):
    fd,name=tempfile.mkstemp(prefix='market-ui-',suffix='.tmp',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as stream:stream.write(data)
        os.replace(name,path)
    finally:
        if Path(name).exists():Path(name).unlink()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,default=ROOT/'target-deploy/game-server');p.add_argument('--versions',type=Path,default=ROOT/'output/market-performance/versions');a=p.parse_args();server=a.server.resolve();versions=a.versions.resolve()
    if not server.is_relative_to(ROOT) or not versions.is_relative_to(ROOT):raise ValueError('Expected server and staging within workspace')
    m=json.loads((versions/'manifest.json').read_text(encoding='utf-8'))
    if sha(server/JAR)!=m['originalJar'] or sha(versions/Path(JAR).name)!=m['installedJar']:raise ValueError('JAR changed since staging')
    with zipfile.ZipFile(server/JAR) as old,zipfile.ZipFile(versions/Path(JAR).name) as new:
        assert old.namelist()==new.namelist()
        for name in old.namelist():
            if name!=m['classPath']:assert old.read(name)==new.read(name),name
    # Hash all configuration/data, excluding the five authorized assets.
    preserved={str(f.relative_to(server)).replace('\\','/'):sha(f) for directory in ['config','data/static_data'] for f in (server/directory).rglob('*') if f.is_file() and str(f.relative_to(server)).replace('\\','/') not in ASSETS}
    sources={name:ROOT/'game-server'/name for name in ASSETS};sources[JAR]=versions/Path(JAR).name
    files=[dict(path=name,original=sha(server/name),installed=sha(source)) for name,source in sources.items()]
    backup=server/'backups'/('market-performance-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True,exist_ok=False)
    for e in files:
        dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest);assert sha(dest)==e['original']
    receipt=dict(feature='market-shop-performance',serverRoot=str(server),files=files,preservedFiles=preserved,jarVerification=m)
    (backup/'manifest.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');applied=[]
    try:
        for e in files:
            if sha(server/e['path'])!=e['original']:raise ValueError('Live file changed: '+e['path'])
            replace(server/e['path'],sources[e['path']].read_bytes());applied.append(e);assert sha(server/e['path'])==e['installed']
        for name,digest in preserved.items():assert sha(server/name)==digest,name
    except Exception:
        for e in reversed(applied):
            if sha(server/e['path'])==e['installed']:replace(server/e['path'],(backup/e['path']).read_bytes())
        raise
    (versions.parent/'installed-receipt.json').write_text(json.dumps(dict(backup=str(backup),**receipt),indent=2),encoding='utf-8')
    print('OK: five browser assets and two JAR constants installed;',m['preservedJarEntries'],'other JAR entries and all configuration/data preserved. Backup:',backup)
if __name__=='__main__':main()
