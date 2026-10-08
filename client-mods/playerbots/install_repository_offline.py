"""Offline native preset/archive migration and exact UI bundling, followed by verified bounded file retirement."""
import argparse,json,shutil,subprocess,zipfile,hashlib
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,sha,validate_output
from remove_follow_recovery_offline import stopped
from retire_metadata_files_offline import unused_files

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args();package=a.package.resolve();validate_output(package)
 server=ROOT/'target-deploy/game-server';directory=(server/'config/playerbots').resolve();m=json.loads((package/'manifest.json').read_text())
 assert m['feature']=='playerbot-native-repository' and Path(m['deployment'])==server
 stopped();assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
 for e in m['files']:assert sha(server/e['path'])==e['original'] and sha(package/e['path'])==e['installed'],e['path']
 assert sha(package/'rollback.jar')==m['rollbackSha256']
 assert sha(package/'playerbot_repository.sql')==m['repositorySchemaSha256'] and sha(package/'tools/PlayerBotRepositoryMigration.class')==m['migrationToolSha256']
 before={str(f.relative_to(directory)).replace('\\','/'):sha(f) for f in directory.rglob('*') if f.is_file()}
 diag=DEV_ROOT/'diagnostics/playerbots-repository-20261007';diag.mkdir(parents=True,exist_ok=True)
 cp=str(package/'tools')+';'+str(package/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/*')
 def migration(mode,report):
  with (diag/('migration-'+mode+'.txt')).open('w') as log:
   subprocess.run(['java','-Xverify:all','-cp',cp,'PlayerBotRepositoryMigration',mode,str(server),str(package/'playerbot_repository.sql'),str(report)],check=True,stdout=log,stderr=subprocess.STDOUT)
 migration('verify',diag/'preflight.txt');stopped()
 assert before=={str(f.relative_to(directory)).replace('\\','/'):sha(f) for f in directory.rglob('*') if f.is_file()}
 backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True)
 for e in m['files']:
  dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest);assert sha(dest)==e['original']
 shutil.copytree(directory,backup/'preferences-before');shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar')
 report=backup/'migration.txt'
 stopped()
 try:
  for e in m['files']:
   if e['original']!=e['installed']:shutil.copy2(package/e['path'],server/e['path'])
  migration('apply',report)
 except Exception:
  if Path(str(report)+'.commit-attempted').exists():
   (backup/'held-manifest.json').write_text(json.dumps(m,indent=2));raise RuntimeError('Repository commit uncertain; keep stopped and verify database before rollback')
  for e in m['files']:
   if e['original']!=e['installed']:shutil.copy2(backup/e['path'],server/e['path'])
  raise
 assert all(sha(server/e['path'])==e['installed'] for e in m['files'])
 # Independent read-only connection proves durable documents before any removal.
 migration('retire',diag/'committed-files.txt');rows=[]
 for line in (diag/'committed-files.txt').read_text().splitlines():
  path,digest=line.split('|');target=(directory/path).resolve();assert target.is_relative_to(directory) and target.parent.name in {'saved-parties','removed'} and sha(target)==digest
  rows.append(dict(path=path,sha256=digest))
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as jar:
  for name,digest in m['bundledMediaSha256'].items():
   assert name in {'bots.html','bots.css','bots.js'} and hashlib.sha256(jar.read('playerbots/media/'+name)).hexdigest()==digest
   assert before['media/'+name]==digest;rows.append(dict(path='media/'+name,sha256=digest))
 assert {r['path'] for r in rows}==set(before),'Unexpected active settings; refuse blanket removal'
 # Record installation before cleanup. If retirement fails, native DB and bundled UI remain installed with legacy recovery files intact.
 m['retiredRuntimeFiles']=rows;m['files']=[e for e in m['files'] if not e['path'].startswith('config/playerbots/media/')]
 (backup/'manifest.json').write_text(json.dumps(m,indent=2));(backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='offline native repository import and UI bundling',files=m['files'],baseJarSha256=m['baseJarSha256']),indent=2))
 stopped();assert before=={str(f.relative_to(directory)).replace('\\','/'):sha(f) for f in directory.rglob('*') if f.is_file()}
 for row in rows:assert sha(backup/'preferences-before'/row['path'])==row['sha256']
 try:
  # Exclusively guard each immediate folder; retain every handle throughout deletion.
  from contextlib import ExitStack
  with ExitStack() as stack:
   for folder in ['saved-parties','removed','media']:
    children=[dict(path=Path(r['path']).name,sha256=r['sha256']) for r in rows if r['path'].startswith(folder+'/')]
    stack.enter_context(unused_files((directory/folder).resolve(),children))
   for row in rows:(directory/row['path']).unlink()
  for folder in ['saved-parties','removed','media']:(directory/folder).rmdir()
  assert not any(f.is_file() for f in directory.rglob('*'))
  assert all(sha(server/e['path'])==e['installed'] for e in m['files'])
 except Exception:
  for row in rows:
   target=directory/row['path'];target.parent.mkdir(parents=True,exist_ok=True)
   if not target.exists():shutil.copy2(backup/'preferences-before'/row['path'],target)
  raise
 (backup/'retired.json').write_text(json.dumps(dict(retired=len(rows),files=rows,archive=str(backup/'preferences-before'),emptyFoldersRemoved=['saved-parties','removed','media']),indent=2))
 print('OK: native repository installed; six JSON records and three exact bundled UI files retired; recovery:',backup)
if __name__=='__main__':main()
