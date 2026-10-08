"""Install the reviewed encounter package only while GameServer/client are stopped."""
import argparse,json,shutil
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,sha,validate_output
from remove_follow_recovery_offline import stopped

def digest(path):return sha(path) if path.is_file() else None
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();validate_output(package);server=ROOT/'target-deploy/game-server'
 m=json.loads((package/'manifest.json').read_text());assert m['feature']=='playerbot-steel-rake' and Path(m['deployment'])==server
 allowed={'libs/playerbot-recruitment-fix.jar','data/handlers/playercommands/Bot.java','cache/classes/playercommands/Bot.class','start.bat'}
 for e in m['files']:
  rel=e['path'];assert rel in allowed or rel.startswith(('data/handlers/ai/instance/rakes/','cache/classes/ai/instance/rakes/'))
  assert (server/rel).resolve().is_relative_to(server.resolve()) and (package/rel).resolve().is_relative_to(package)
  assert digest(server/rel)==e['original'] and sha(package/rel)==e['installed'],rel
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256'] and sha(server/'data/geo/models.mesh')==m['unchangedGeometrySha256']
 assert sha(package/'rollback.jar')==m['rollbackSha256'];stopped()
 preferences={str(p.relative_to(server/'config/playerbots')):sha(p) for p in (server/'config/playerbots').rglob('*') if p.is_file()}
 backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True)
 for e in m['files']:
  if e['original'] is not None:
   dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest);assert sha(dest)==e['original']
 shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar');stopped()
 try:
  for e in m['files']:
   assert digest(server/e['path'])==e['original'],e['path']
   if e['original']!=e['installed']:
    dest=server/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(package/e['path'],dest)
  assert all(sha(server/e['path'])==e['installed'] for e in m['files'])
  assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256'] and sha(server/'data/geo/models.mesh')==m['unchangedGeometrySha256']
  assert preferences=={str(p.relative_to(server/'config/playerbots')):sha(p) for p in (server/'config/playerbots').rglob('*') if p.is_file()}
 except Exception:
  for e in m['files']:
   if e['original']!=e['installed']:
    if e['original'] is None:
     if digest(server/e['path'])==e['installed']:(server/e['path']).unlink()
    else:shutil.copy2(backup/e['path'],server/e['path'])
  (backup/'failed-manifest.json').write_text(json.dumps(m,indent=2));raise
 (backup/'manifest.json').write_text(json.dumps(m,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='offline; no server/client lifecycle action',files=m['files'],preferencesPreserved=len(preferences)),indent=2))
 print('OK: encounter package installed offline:',backup)
if __name__=='__main__':main()
