"""Back up and atomically deploy the live Season Pass browser and render assets."""
import argparse,hashlib,json,os,shutil,tempfile
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ARTWORK=['aether-frame.png','aether-crest.png','ascendant-crest.png','pass-panel.png']
FILES=['pass.css','pass.js','pass.html']+ARTWORK
PRESERVED=['libs/game-server-4.8-SNAPSHOT.jar','config/season-pass/season.properties','config/season-pass/rewards.tsv','config/season-pass/missions.tsv','config/season-pass/schema.sql','data/static_data/decomposable_items/decomposable_items.xml']
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def replace(path,data):
    descriptor,name=tempfile.mkstemp(prefix='pass-ui-',suffix='.tmp',dir=path.parent)
    try:
        with os.fdopen(descriptor,'wb') as stream:stream.write(data)
        os.replace(name,path)
    finally:
        if Path(name).exists():Path(name).unlink()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--server',type=Path,default=ROOT/'target-deploy/game-server');args=parser.parse_args();server=args.server.resolve()
    if not server.is_relative_to(ROOT) or not (server/'config/season-pass/media/pass.html').is_file():raise ValueError('Expected a deployed server within this workspace')
    source=ROOT/'game-server/config/season-pass/media';destination=server/'config/season-pass/media'
    def source_path(name):return source/'optimized'/name if name in ARTWORK else source/name
    preserved={name:sha(server/name) for name in PRESERVED}
    files=[dict(path='config/season-pass/media/'+name,original=sha(destination/name),installed=sha(source_path(name))) for name in FILES]
    backup=server/'backups'/('season-pass-ui-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True,exist_ok=False)
    for name in FILES:shutil.copy2(destination/name,backup/name)
    for e in files:assert sha(backup/Path(e['path']).name)==e['original']
    receipt=dict(feature='season-pass-ui-v5',serverRoot=str(server),files=files,preservedFiles=preserved,originalArtwork={name:sha(source/name) for name in ARTWORK})
    (backup/'manifest.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    try:
        for e in files:
            name=Path(e['path']).name
            if sha(destination/name)!=e['original']:raise ValueError('Live asset changed before install: '+name)
            replace(destination/name,source_path(name).read_bytes());assert sha(destination/name)==e['installed']
        for name,digest in preserved.items():assert sha(server/name)==digest,name
        for name,digest in receipt['originalArtwork'].items():assert sha(source/name)==digest,name
    except Exception:
        for e in files:
            name=Path(e['path']).name
            if sha(destination/name)==e['installed']:replace(destination/name,(backup/name).read_bytes())
        raise
    print('OK: Season Pass browser and four optimized artwork files installed; source artwork, reward data, rules and server JAR unchanged. Backup:',backup)
if __name__=='__main__':main()
