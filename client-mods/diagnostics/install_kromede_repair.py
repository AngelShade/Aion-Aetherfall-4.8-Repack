"""Install the bounded repair with preloading, strict hashes and effective rollback."""
import argparse,json,shutil,subprocess,sys,zipfile
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
from stage_companion_update import sha

def install(package,pid):
 server=ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text())
 assert m['feature']=='ai-reload-kromede-repair' and Path(m['deployment'])==server
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256'] and sha(package/'rollback.jar')==m['rollbackSha256']
 for e in m['files']:
  assert (sha(server/e['path']) if (server/e['path']).exists() else None)==e['original'],'Live baseline changed: '+e['path']
  assert sha(package/e['path'])==e['installed'],'Payload changed: '+e['path']
 backup=server/'backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir()
 for e in m['files']:
  if e['original'] is not None:
   p=backup/e['path'];p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],p);assert sha(p)==e['original']
 shutil.copy2(package/'manifest.json',backup/'manifest.json');shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar')
 definitions=backup/'definitions.jar';helpers=backup/'new-helpers.jar'
 with zipfile.ZipFile(definitions,'w') as defs:
  for e in m['incrementalChangedMethods']:defs.write(package/'classes'/e['path'],e['path'])
 with zipfile.ZipFile(package/'libs/playerbot-recruitment-fix.jar') as src,zipfile.ZipFile(helpers,'w') as new:
  for name in m['newClasses']:new.writestr(name,src.read(name))
 agent=ROOT/'target/kromede-repair/runtime-agent-v23.jar';name='KromedeRuntimeUpdateAgent23'
 def attach(args):subprocess.run(['java','-cp',str(agent.parent/'tools')+';'+str(server/'libs/*'),name,str(pid),str(agent),'|'.join(map(str,args))],check=True)
 attach(['prepare',backup/'effective-rollback.jar',m['rollbackSha256'],server/'libs/playerbot-recruitment-fix.jar',sha(server/'libs/playerbot-recruitment-fix.jar'),backup/'runtime-preflight.txt'])
 applied=False
 try:
  for e in m['files']:
   if e['original']!=e['installed']:
    dest=server/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(package/e['path'],dest)
  attach(['apply',definitions,sha(definitions),backup/'effective-rollback.jar',m['rollbackSha256'],helpers,sha(helpers),backup/'runtime-verification.txt']);applied=True
  assert all(sha(server/e['path'])==e['installed'] for e in m['files']) and sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
 except Exception:
  if applied:attach(['rollback',backup/'effective-rollback.jar',m['rollbackSha256'],backup/'runtime-rollback.txt'])
  for e in m['files']:
   dest=server/e['path']
   if e['original'] is None:dest.unlink(missing_ok=True)
   else:shutil.copy2(backup/e['path'],dest)
  (backup/'manifest.json').rename(backup/'failed-manifest.json');raise
 (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),files=m['files']),indent=2))
 print((backup/'runtime-verification.txt').read_text());print('OK: installed and persisted; receipt:',backup)

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);p.add_argument('--pid',type=int,required=True);a=p.parse_args();install(a.package.resolve(),a.pid)
