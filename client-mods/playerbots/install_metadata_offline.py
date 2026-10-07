"""Guarded offline metadata schema/import plus bounded cumulative JAR install; never starts any service."""
import argparse,json,shutil,subprocess
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,sha,validate_output

def stopped():
 cmd="$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -and $_.CommandLine -like '*com.aionemu.gameserver.GameServer*') -or $_.Name -match '^(aion|aionbin|Game)\\.(exe|bin)$' }).Count"
 r=subprocess.run(['pwsh','-NoProfile','-Command',cmd],check=True,capture_output=True,text=True)
 assert r.stdout.strip()=='0','GameServer/client running; keep them off for metadata installation'

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args();package=a.package.resolve();validate_output(package)
 server=ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text());assert m['scope'].startswith('PB-SCOPE-012A:') and Path(m['deployment'])==server
 stopped()
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
 for e in m['files']:assert sha(server/e['path'])==e['original'] and sha(package/e['path'])==e['installed'],e['path']
 schema=package/'playerbot_metadata.sql';tool=package/'tools/PlayerBotMetadataMigration.class'
 assert sha(schema)==m['metadataSchemaSha256'] and sha(tool)==m['migrationToolSha256']
 diagnostics=DEV_ROOT/'diagnostics/playerbots-metadata-20261007';diagnostics.mkdir(parents=True,exist_ok=True)
 def migrate(mode,report):
  cp=str(package/'tools')+';'+str(package/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/*')
  with (diagnostics/('migration-'+mode+'-output.txt')).open('w') as log:
   subprocess.run(['java','-cp',cp,'PlayerBotMetadataMigration',mode,str(server),str(schema),str(report)],check=True,stdout=log,stderr=subprocess.STDOUT)
 migrate('verify',diagnostics/'migration-preflight.txt') # no deployment if database is unavailable
 stopped()
 backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True)
 for e in m['files']:
  dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest);assert sha(dest)==e['original']
 shutil.copytree(server/'config/playerbots',backup/'preferences-before');shutil.copy2(schema,backup/schema.name);shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar')
 report=backup/'migration.txt'
 try:
  for e in m['files']:
   if e['original']!=e['installed']:shutil.copy2(package/e['path'],server/e['path'])
  migrate('apply',report)
 except Exception:
  if Path(str(report)+'.commit-attempted').exists():
   (backup/'held-manifest.json').write_text(json.dumps(m,indent=2));raise RuntimeError('Import commit uncertain; retain installed DAO, keep server stopped and verify database custody before rollback')
  for e in m['files']:
   if e['original']!=e['installed']:shutil.copy2(backup/e['path'],server/e['path'])
  (backup/'failed-manifest.json').write_text(json.dumps(m,indent=2));raise
 assert all(sha(server/e['path'])==e['installed'] for e in m['files'])
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
 # Publish the successful recovery receipt only after committed import and disk preservation.
 (backup/'manifest.json').write_text(json.dumps(m,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='server/client stopped; database imported',files=m['files'],baseJarSha256=m['baseJarSha256']),indent=2))
 print('OK: native metadata schema/import and one bounded cumulative installation; recovery receipt:',backup)
if __name__=='__main__':main()
