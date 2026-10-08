"""Install the reviewed core execution correction offline, preserving cumulative native handlers/media."""
import argparse,json,shutil
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,sha,validate_output
from remove_follow_recovery_offline import stopped

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();validate_output(package);server=ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text())
 assert m['feature']=='playerbot-core-cast-execution' and m['scope'].startswith('PB-REPAIR-ENGINE-001:') and Path(m['deployment'])==server
 proof=json.loads((package/'verification.json').read_text());assert proof['coreExecutionChecks']==72 and proof['fourBaselineFailuresReproduced'] and proof['unselectedMethodsPreserved'] and proof['payloadSha256']==sha(package/'libs/playerbot-recruitment-fix.jar')
 stopped()
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256'] and sha(server/'data/geo/models.mesh')==m['unchangedGeometrySha256']
 for e in m['files']:
  assert (server/e['path']).resolve().is_relative_to(server.resolve()) and (package/e['path']).resolve().is_relative_to(package)
  assert sha(server/e['path'])==e['original'] and sha(package/e['path'])==e['installed'],e['path']
 assert sha(package/'rollback.jar')==m['rollbackSha256']
 settings={str(f.relative_to(server/'config/playerbots')):sha(f) for f in (server/'config/playerbots').rglob('*') if f.is_file()}
 backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));validate_output(backup);backup.mkdir(parents=True)
 for e in m['files']:
  dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest);assert sha(dest)==e['original']
 shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar');shutil.copy2(package/'verification.json',backup/'verification.json')
 stopped()
 try:
  for e in m['files']:
   if e['original']!=e['installed']:shutil.copy2(package/e['path'],server/e['path'])
  assert all(sha(server/e['path'])==e['installed'] for e in m['files'])
  assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
  assert sha(server/'data/geo/models.mesh')==m['unchangedGeometrySha256']
  assert settings=={str(f.relative_to(server/'config/playerbots')):sha(f) for f in (server/'config/playerbots').rglob('*') if f.is_file()}
 except Exception:
  for e in m['files']:
   if e['original']!=e['installed']:shutil.copy2(backup/e['path'],server/e['path'])
  (backup/'failed-manifest.json').write_text(json.dumps(m,indent=2));raise
 (backup/'manifest.json').write_text(json.dumps(m,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='server/client stopped; core cast execution repair',files=m['files'],baseJarSha256=m['baseJarSha256']),indent=2))
 print('OK: core execution installed offline; cumulative mods/owned-alt builds retained; receipt:',backup)
if __name__=='__main__':main()
