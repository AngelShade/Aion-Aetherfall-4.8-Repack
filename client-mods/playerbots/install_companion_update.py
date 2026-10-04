"""Install reviewed companion override/media and atomically update the running server."""
import argparse,hashlib,json,shutil,subprocess,zipfile
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--package',type=Path,required=True);parser.add_argument('--pid',required=True)
    parser.add_argument('--agent',type=Path,default=ROOT/'target/playerbots-companion-check/update-agent.jar');parser.add_argument('--agent-class',default='PlayerBotCompanionUpdateAgent');parser.add_argument('--preload-override',action='store_true');args=parser.parse_args()
    package=args.package.resolve();server=ROOT/'target-deploy/game-server';manifest=json.loads((package/'manifest.json').read_text())
    assert manifest['feature']=='playerbot-companion-flight-quests-travel-care' and Path(manifest['deployment'])==server
    assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==manifest['baseJarSha256']
    assert sha(package/'rollback.jar')==manifest['rollbackSha256']
    for entry in manifest['files']:
        assert sha(server/entry['path'])==entry['original'],'Live baseline changed: '+entry['path']
        assert sha(package/entry['path'])==entry['installed'],'Reviewed payload changed: '+entry['path']
    backup=server/'backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir()
    for entry in manifest['files']:
        target=backup/entry['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/entry['path'],target);assert sha(target)==entry['original']
    shutil.copytree(server/'config/playerbots',backup/'preferences-before')
    shutil.copy2(package/'manifest.json',backup/'manifest.json');shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar')
    helpers=backup/'new-companion-helpers.jar';definitions=backup/'reviewed-definitions.jar'
    with zipfile.ZipFile(package/'libs/playerbot-recruitment-fix.jar') as source,zipfile.ZipFile(helpers,'w') as new,zipfile.ZipFile(definitions,'w') as changes:
        for name in manifest['newClasses']:new.writestr(name,source.read(name))
        for entry in manifest['incrementalChangedMethods']:
            name=entry['path'];data=(package/'cache/classes'/name).read_bytes() if name.startswith('playercommands/') else source.read(name)
            changes.writestr(name,data)
    agent=args.agent.resolve()
    argument='|'.join(['generation' if manifest.get('generationFix') else 'apply',str(definitions),sha(definitions),str(backup/'effective-rollback.jar'),manifest['rollbackSha256'],str(helpers),sha(helpers),str(backup/'runtime-verification.txt')])
    applied=False
    try:
        if args.preload_override:
            original=next(e['original'] for e in manifest['files'] if e['path']=='libs/playerbot-recruitment-fix.jar')
            prepare='|'.join(['prepare',str(backup/'effective-rollback.jar'),manifest['rollbackSha256'],str(server/'libs/playerbot-recruitment-fix.jar'),original,str(backup/'runtime-preflight.txt')])
            subprocess.run(['java','-cp',str(agent.parent/'tools')+';'+str(server/'libs/*'),args.agent_class,args.pid,str(agent),prepare],check=True)
        for entry in manifest['files']:
            if entry['original']!=entry['installed']:shutil.copy2(package/entry['path'],server/entry['path'])
        subprocess.run(['java','-cp',str(agent.parent/'tools')+';'+str(server/'libs/*'),args.agent_class,args.pid,str(agent),argument],check=True)
        applied=True
        assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==manifest['baseJarSha256']
        assert all(sha(server/e['path'])==e['installed'] for e in manifest['files'])
    except Exception:
        if applied:
            rollback='|'.join(['rollback',str(backup/'effective-rollback.jar'),manifest['rollbackSha256'],str(backup/'runtime-rollback.txt')])
            subprocess.run(['java','-cp',str(agent.parent/'tools')+';'+str(server/'libs/*'),args.agent_class,args.pid,str(agent),rollback],check=True)
        for entry in manifest['files']:shutil.copy2(backup/entry['path'],server/entry['path'])
        (backup/'manifest.json').rename(backup/'failed-manifest.json')
        raise
    (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),files=manifest['files'],baseJarSha256=manifest['baseJarSha256']),indent=2))
    print((backup/'runtime-verification.txt').read_text());print('OK: companion update installed live and persisted. Backup:',backup)
if __name__=='__main__':main()
