"""Install verified storage/order fixes and remove the rejected AFK extension."""
import hashlib,json,shutil
from datetime import datetime
from pathlib import Path
from install_ui import replace
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'output/market-custody';SERVER=ROOT/'target-deploy/game-server'
ASSETS=['config/central-market/media/market.js','config/central-market/media/market.html'];JAR='libs/game-server-4.8-SNAPSHOT.jar';AFK='config/main/afk.properties'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    m=json.loads((OUT/'manifest.json').read_text());assert sha(SERVER/JAR)==m['originalJar'] and sha(OUT/Path(JAR).name)==m['installedJar'],'JAR changed since staging'
    sources={name:ROOT/'game-server'/name for name in ASSETS};sources[JAR]=OUT/Path(JAR).name
    files=[dict(path=name,original=sha(SERVER/name),installed=sha(source)) for name,source in sources.items()]+[dict(path=AFK,original=sha(SERVER/AFK),installed=None)]
    preserved={str(f.relative_to(SERVER)).replace('\\','/'):sha(f) for directory in ['config','data/static_data'] for f in (SERVER/directory).rglob('*') if f.is_file() and str(f.relative_to(SERVER)).replace('\\','/') not in ASSETS+[AFK]}
    backup=SERVER/'backups'/('market-storage-afk-removal-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True,exist_ok=False)
    for e in files:
        dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(SERVER/e['path'],dest);assert sha(dest)==e['original']
    receipt=dict(backup=str(backup),files=files,preservedFiles=preserved,jarVerification=m);(backup/'manifest.json').write_text(json.dumps(receipt,indent=2));applied=[]
    try:
        for e in files:
            dest=SERVER/e['path'];assert sha(dest)==e['original'],'Live file changed'
            if e['installed'] is None:dest.unlink()
            else:replace(dest,sources[e['path']].read_bytes());assert sha(dest)==e['installed']
            applied.append(e)
        for name,digest in preserved.items():assert sha(SERVER/name)==digest,name
    except Exception:
        for e in reversed(applied):
            dest=SERVER/e['path']
            matches = not dest.exists() if e['installed'] is None else sha(dest)==e['installed']
            if matches:replace(dest,(backup/e['path']).read_bytes())
        raise
    (OUT/'installed-receipt.json').write_text(json.dumps(receipt,indent=2))
    print('OK: storage/order fixes installed; AFK classes, startup/config hooks and option removed;',m['preservedJarEntries'],'other JAR entries and other config/data unchanged. Backup:',backup)
if __name__=='__main__':main()
