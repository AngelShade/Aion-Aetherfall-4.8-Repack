"""Install a two-class override and persist launcher order; preserve the base JAR."""
import argparse
import hashlib
import json
from datetime import datetime
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
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--classes',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--pid');parser.add_argument('--apply',action='store_true')
    args=parser.parse_args();classes=args.classes.resolve();output=args.output.resolve();server=ROOT/'target-deploy/game-server'
    base=server/'libs/game-server-4.8-SNAPSHOT.jar';start=server/'start.bat';relative='libs/playerbot-recruitment-fix.jar'
    if not args.apply:
        output.mkdir(parents=True,exist_ok=False)
        allowed={'PlayerBotService':{'ensureOwner','allowsTarget','relocate','canEnter'},'PlayerBotSession':{'tick','inPvp'}}
        jar=output/relative;jar.parent.mkdir()
        review=[]
        with zipfile.ZipFile(jar,'w',compression=zipfile.ZIP_DEFLATED) as package:
            for name,permitted in allowed.items():
                path='com/aionemu/gameserver/services/playerbot/'+name
                before=methods(base,path);after=methods(classes,path)
                assert before.keys()==after.keys(),'Class schema changed'
                changed=[key for key in before if before[key]!=after[key]]
                assert len(changed)==len(permitted) and all(any((' '+method+'(') in key for method in permitted) for key in changed),'Unreviewed class change'
                package.writestr(path+'.class',(classes/(path+'.class')).read_bytes());review+=changed
        data=start.read_bytes();old=b'-cp "libs/*"';new=b'-cp "libs/playerbot-recruitment-fix.jar;libs/*"'
        assert data.count(old)==1 and b'playerbot-recruitment-fix.jar' not in data,'Later launcher implementation detected'
        (output/'start.bat').write_bytes(data.replace(old,new))
        manifest=dict(feature='playerbot-unrestricted-locations',deployment=str(server),baseJarSha256=sha(base),changedMethods=review,
                      files=[dict(path=rel,original=sha(server/rel) if (server/rel).exists() else None,installed=sha(output/rel)) for rel in [relative,'start.bat']])
        (output/'manifest.json').write_text(json.dumps(manifest,indent=2));print('OK: two-class override staged; only six methods differ; base JAR unchanged.');return
    manifest=json.loads((output/'manifest.json').read_text())
    assert Path(manifest['deployment'])==server and manifest['feature']=='playerbot-unrestricted-locations' and sha(base)==manifest['baseJarSha256']
    assert args.pid and {e['path'] for e in manifest['files']}=={relative,'start.bat'}
    for e in manifest['files']:
        assert (sha(server/e['path']) if (server/e['path']).exists() else None)==e['original'],'Live baseline changed'
        assert sha(output/e['path'])==e['installed'],'Payload changed'
    backup=server/'backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir()
    for e in manifest['files']:
        if e['original'] is not None:
            saved=backup/e['path'];saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],saved);assert sha(saved)==e['original']
    shutil.copy2(output/'manifest.json',backup/'manifest.json')
    try:
        for e in manifest['files']:shutil.copy2(output/e['path'],server/e['path']);assert sha(server/e['path'])==e['installed']
        agent=ROOT/'target/playerbots-recruitment-check/patch-agent.jar';agent_classes=agent.parent/'agent-classes'
        subprocess.run(['java','-cp',str(agent_classes)+';'+str(server/'libs/*'),'PlayerBotRecruitmentPatchAgent',args.pid,str(agent),str(server/relative),manifest['files'][0]['installed'],str(backup/'runtime-verification.txt')],check=True)
        assert sha(base)==manifest['baseJarSha256'],'Base JAR changed during patch'
    except Exception:
        for e in manifest['files']:
            target=server/e['path']
            if e['original'] is None:target.unlink(missing_ok=True)
            else:shutil.copy2(backup/e['path'],target)
        raise
    (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),files=manifest['files'],baseJarSha256=manifest['baseJarSha256']),indent=2))
    print((backup/'runtime-verification.txt').read_text());print('OK: live recruitment fix installed and persisted for start.bat; base JAR and client untouched. Backup:',backup)

if __name__=='__main__':main()
