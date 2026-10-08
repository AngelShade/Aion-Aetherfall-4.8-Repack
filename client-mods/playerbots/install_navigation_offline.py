"""Guarded generic navigation installation. Does not start GameServer/client or modify geodata."""
import argparse,json,shutil
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,sha,validate_output
from remove_follow_recovery_offline import stopped

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();validate_output(package);server=ROOT/'target-deploy/game-server'
 manifest=json.loads((package/'manifest.json').read_text())
 assert manifest['feature']=='playerbot-general-ground-navigation' and manifest['scope'].startswith(('PB-REPAIR-NAV-002:','PB-REPAIR-NAV-003:')) and Path(manifest['deployment'])==server
 assert [e['path'] for e in manifest['files'] if e['original']!=e['installed']]==['libs/playerbot-recruitment-fix.jar']
 stopped()
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==manifest['baseJarSha256']
 assert sha(server/'data/geo/models.mesh')==manifest['unchangedGeometrySha256']
 for e in manifest['files']:
  assert (server/e['path']).resolve().is_relative_to(server.resolve()) and (package/e['path']).resolve().is_relative_to(package)
  assert sha(server/e['path'])==e['original'] and sha(package/e['path'])==e['installed'],e['path']
 assert sha(package/'rollback.jar')==manifest['rollbackSha256']
 preferences={str(p.relative_to(server/'config/playerbots')):sha(p) for p in (server/'config/playerbots').rglob('*') if p.is_file()}
 backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True)
 for e in manifest['files']:
  dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest);assert sha(dest)==e['original']
 shutil.copytree(server/'config/playerbots',backup/'preferences-before');shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar')
 stopped()
 try:
  for e in manifest['files']:
   if e['original']!=e['installed']:shutil.copy2(package/e['path'],server/e['path'])
  assert all(sha(server/e['path'])==e['installed'] for e in manifest['files'])
  assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==manifest['baseJarSha256']
  assert sha(server/'data/geo/models.mesh')==manifest['unchangedGeometrySha256']
  assert preferences=={str(p.relative_to(server/'config/playerbots')):sha(p) for p in (server/'config/playerbots').rglob('*') if p.is_file()}
 except Exception:
  for e in manifest['files']:
   if e['original']!=e['installed']:shutil.copy2(backup/e['path'],server/e['path'])
  (backup/'failed-manifest.json').write_text(json.dumps(manifest,indent=2));raise
 (backup/'manifest.json').write_text(json.dumps(manifest,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='server/client stopped; generic bot navigation; unchanged geodata',files=manifest['files'],baseJarSha256=manifest['baseJarSha256'],preferencesPreserved=len(preferences)),indent=2))
 print('OK: generic navigation installed stopped; prior mods, geodata and settings preserved:',backup)
if __name__=='__main__':main()
