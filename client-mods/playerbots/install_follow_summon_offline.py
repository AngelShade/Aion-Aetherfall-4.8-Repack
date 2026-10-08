"""Install reviewed follow/summon controls with both processes off, hash guards and rollback."""
import argparse,json,shutil
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,CLIENT_ROOT,sha,validate_output
from remove_follow_recovery_offline import stopped

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();validate_output(package);server=ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text())
 proof=json.loads((package/'verification.json').read_text());inventory=json.loads((package/'inventory-before.json').read_text())
 assert m['feature']=='playerbot-follow-summon-controls' and m['offlineOnly'] and Path(m['deployment'])==server
 assert proof['formationSummonChecks']==679 and proof['unselectedMethodsPreserved'] and proof['movementServiceLockLookupAbsent'] and proof['payloadSha256']==sha(package/'libs/playerbot-recruitment-fix.jar') and proof['configSha256']==sha(package/'config/main/playerbots.properties')
 stopped()
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256'] and sha(server/'data/geo/models.mesh')==m['unchangedGeometrySha256']
 assert all(sha(CLIENT_ROOT/e['path'])==e['sha256'] for e in inventory['clientFiles'])
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
  assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256'] and sha(server/'data/geo/models.mesh')==m['unchangedGeometrySha256']
  assert all(sha(CLIENT_ROOT/e['path'])==e['sha256'] for e in inventory['clientFiles'])
  assert settings=={str(f.relative_to(server/'config/playerbots')):sha(f) for f in (server/'config/playerbots').rglob('*') if f.is_file()}
 except Exception:
  for e in m['files']:
   if e['original']!=e['installed']:shutil.copy2(backup/e['path'],server/e['path'])
  (backup/'failed-manifest.json').write_text(json.dumps(m,indent=2));raise
 (backup/'manifest.json').write_text(json.dumps(m,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='server/client stopped; follow/summon config cold load',files=m['files'],baseJarSha256=m['baseJarSha256']),indent=2))
 print('OK: follow/summon installed offline; prior mods/alt builds preserved; receipt:',backup)
if __name__=='__main__':main()
