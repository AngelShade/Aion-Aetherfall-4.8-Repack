"""Install only the verified browse JAR and three assets with rollback and preservation guards."""
import json,shutil,hashlib
from datetime import datetime
from pathlib import Path
from install_ui import replace
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'output/market-browse';SERVER=ROOT/'target-deploy/game-server'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    m=json.loads((OUT/'manifest.json').read_text());jar='libs/game-server-4.8-SNAPSHOT.jar'
    assert sha(SERVER/jar)==m['originalJar'] and sha(OUT/Path(jar).name)==m['installedJar'],'JAR changed since staging'
    assets=['config/central-market/media/'+n for n in ['market.html','market.js','market.css']]
    sources={n:ROOT/'game-server'/n for n in assets};sources[jar]=OUT/Path(jar).name
    files=[dict(path=n,original=sha(SERVER/n),installed=sha(s)) for n,s in sources.items()]
    preserved={str(p.relative_to(SERVER)).replace('\\','/'):sha(p) for d in ['config','data/static_data'] for p in (SERVER/d).rglob('*') if p.is_file() and str(p.relative_to(SERVER)).replace('\\','/') not in assets}
    backup=SERVER/'backups'/('market-browse-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True,exist_ok=False)
    for e in files:
        p=backup/e['path'];p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(SERVER/e['path'],p);assert sha(p)==e['original']
    receipt=dict(backup=str(backup),files=files,preservedFiles=preserved,jarVerification=m);(backup/'manifest.json').write_text(json.dumps(receipt,indent=2));applied=[]
    try:
        for e in files:
            dest=SERVER/e['path'];assert sha(dest)==e['original'];replace(dest,sources[e['path']].read_bytes());applied.append(e);assert sha(dest)==e['installed']
        for n,digest in preserved.items():assert sha(SERVER/n)==digest,n
    except Exception:
        for e in reversed(applied):
            if sha(SERVER/e['path'])==e['installed']:replace(SERVER/e['path'],(backup/e['path']).read_bytes())
        raise
    (OUT/'installed-receipt.json').write_text(json.dumps(receipt,indent=2));print('PASS: browse installed; all other config/data unchanged. Backup:',backup)
if __name__=='__main__':main()
