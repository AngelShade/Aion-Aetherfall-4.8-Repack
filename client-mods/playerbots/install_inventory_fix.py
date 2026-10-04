"""Incrementally repair panel inventory serialization on the effective installed override."""
import argparse
import copy
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'client-mods/season-pass'))
from incremental_server import methods

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--pid');parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    output=args.output.resolve();server=ROOT/'target-deploy/game-server';relative='libs/playerbot-recruitment-fix.jar'
    current=server/relative;base=server/'libs/game-server-4.8-SNAPSHOT.jar';start=server/'start.bat'
    assert b'-cp "libs/playerbot-recruitment-fix.jar;libs/*"' in start.read_bytes(),'Override classpath changed'
    if not args.apply:
        previous=sorted((server/'backups').glob('playerbots-recruitment-*/manifest.json'))[-1]
        receipt=json.loads(previous.read_text())
        assert sha(base)==receipt['baseJarSha256'] and all(sha(server/e['path'])==e['installed'] for e in receipt['files']),'Effective installed baseline changed'
        path='com/aionemu/gameserver/services/playerbot/PlayerBotSession'
        before=methods(current,path);after=methods(args.classes.resolve(),path)
        assert before.keys()==after.keys(),'Class schema changed'
        changed=[key for key in before if before[key]!=after[key]]
        assert len(changed)==1 and ' lambda$snapshot$0(' in changed[0],'Changes exceed the reviewed inventory mapper'
        output.mkdir(parents=True,exist_ok=False);payload=output/relative;payload.parent.mkdir()
        with zipfile.ZipFile(current) as original,zipfile.ZipFile(payload,'w') as merged:
            assert set(original.namelist())=={path+'.class',path.replace('PlayerBotSession','PlayerBotService')+'.class'}
            merged.comment=original.comment
            for entry in original.infolist():
                merged.writestr(copy.copy(entry),(args.classes/(path+'.class')).read_bytes() if entry.filename==path+'.class' else original.read(entry))
        shutil.copy2(start,output/'start.bat')
        manifest=dict(feature='playerbot-inventory-slot-serialization',deployment=str(server),baseJarSha256=sha(base),
            previousReceipt=str(previous.relative_to(server)),incrementalChangedMethods=changed,
            changedMethods=receipt['changedMethods']+changed,
            files=[dict(path=rel,original=sha(server/rel),installed=sha(output/rel)) for rel in [relative,'start.bat']])
        (output/'manifest.json').write_text(json.dumps(manifest,indent=2))
        print('OK: staged one-method repair on latest override; all service methods, recruitment changes and launcher retained.');return
    manifest=json.loads((output/'manifest.json').read_text())
    assert args.pid and manifest['feature']=='playerbot-inventory-slot-serialization' and Path(manifest['deployment'])==server
    assert sha(base)==manifest['baseJarSha256'] and {e['path'] for e in manifest['files']}=={relative,'start.bat'}
    for e in manifest['files']:
        assert sha(server/e['path'])==e['original'],'Live baseline changed'
        assert sha(output/e['path'])==e['installed'],'Reviewed payload changed'
    backup=server/'backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir()
    for e in manifest['files']:
        saved=backup/e['path'];saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],saved);assert sha(saved)==e['original']
    shutil.copy2(output/'manifest.json',backup/'manifest.json')
    agent=ROOT/'target/playerbots-inventory-check/patch-agent.jar'
    original=backup/relative
    argument='|'.join(['apply',str(current),manifest['files'][0]['installed'],str(original),manifest['files'][0]['original'],str(backup/'runtime-verification.txt')])
    try:
        # Only the override is replaced; the base JAR, client and launcher stay byte-identical.
        shutil.copy2(output/relative,current);assert sha(current)==manifest['files'][0]['installed']
        subprocess.run(['java','-cp',str(agent.parent/'tools')+';'+str(server/'libs/*'),'PlayerBotInventoryPatchAgent',args.pid,str(agent),argument],check=True)
        assert sha(base)==manifest['baseJarSha256'] and sha(start)==manifest['files'][1]['original']
    except Exception:
        shutil.copy2(original,current)
        raise
    (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),files=manifest['files'],baseJarSha256=manifest['baseJarSha256']),indent=2))
    print((backup/'runtime-verification.txt').read_text());print('OK: inventory mapper repaired live and persisted. Backup:',backup)

if __name__=='__main__':main()
