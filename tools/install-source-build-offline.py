"""Copy verified, unchanged normal builder outputs into the existing offline component runtime."""
import argparse,json,shutil,subprocess,sys
from datetime import datetime
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped

def main():
 if not (ROOT/'target-deploy/game-server/libs/playerbot-recruitment-fix.jar').is_file():
  raise RuntimeError('Historical override-to-source transition is complete. Use tools/release-game-server.py --scope <ID> --install for current delivery.')
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args()
 out=a.package.resolve();shared.validate_output(out)
 m=json.loads((out/'manifest.json').read_text());checks=json.loads((out/'checks.json').read_text())
 server=ROOT/'target-deploy/game-server';client=shared.CLIENT_ROOT
 assert m['feature']=='playerbots-source-build-spiritmaster' and Path(m['deployment'])==server
 assert checks['PlayerBotSpiritmasterCheck'].startswith('OK: 59 ') and len(checks)>=12
 override=server/'libs/playerbot-recruitment-fix.jar';launcher=server/'start.bat'
 originalLauncher=launcher.read_bytes();token=b'-cp "libs/playerbot-recruitment-fix.jar;libs/*"'
 assert originalLauncher.count(token)==1
 replacementLauncher=originalLauncher.replace(token,b'-cp "libs/*"')
 # Normal client exit can persist graphics preferences after staging. Accept
 # only these two paths after an independent complete installed-mod refresh.
 differences=[e for e in m['clientFiles'] if shared.sha(client/e['path'])!=e['sha256']]
 if differences:
  assert {e['path'] for e in differences}.issubset({'system.cfg','SystemOptionGraphics.cfg'})
  inventory=json.loads((ROOT/'docs/INSTALLED_MODS.json').read_text())
  assert all(inventory['checks'].values()) and inventory['serverJarSha256']==shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')
  assert all(shared.sha(client/e['path'])==e['sha256'] for e in inventory['clientFiles'])
  (out/'manifest-before-client-preference-refresh.json').write_text(json.dumps(m,indent=2))
  current={e['path']:e['sha256'] for e in inventory['clientFiles']}
  m['clientPreferenceRefresh']=[dict(path=e['path'],previous=e['sha256'],current=current[e['path']]) for e in differences]
  m['clientFiles']=inventory['clientFiles']
  (out/'manifest.json').write_text(json.dumps(m,indent=2))
 def guard():
  stopped()
  assert shared.sha(override)==m['retiredOverrideSha256'] and shared.sha(launcher)==m['launcherOriginalSha256']
  assert all(shared.sha(Path(e['source']))==e['installed'] and shared.sha(server/e['path'])==e['original'] for e in m['files'])
  assert all(shared.sha(server/e['path'])==e['installed'] for e in m['baselineFiles'])
  assert all(shared.sha(server/path)==sha for path,sha in m['configFiles'].items())
  assert shared.sha(server/'data/geo/models.mesh')==m['geometrySha256']
  assert all(shared.sha(client/e['path'])==e['sha256'] for e in m['clientFiles'])
 guard()
 backup=shared.DEV_ROOT/'archives/server/game-server/backups'/('playerbots-source-build-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
 backup.mkdir(parents=True,exist_ok=False)
 saved=list(m['files'])+[dict(path='start.bat',original=m['launcherOriginalSha256']),dict(path='libs/playerbot-recruitment-fix.jar',original=m['retiredOverrideSha256'])]
 for e in saved:
  dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest);assert shared.sha(dest)==e['original']
 guard()
 try:
  for e in m['files']:shutil.copy2(e['source'],server/e['path'])
  launcher.write_bytes(replacementLauncher)
  # Exact archived file retirement; no source/build classes are rewritten.
  override.unlink()
  assert all(shared.sha(server/e['path'])==e['installed'] for e in m['files']) and launcher.read_bytes()==replacementLauncher
  assert all(shared.sha(server/path)==sha for path,sha in m['configFiles'].items())
  assert shared.sha(server/'data/geo/models.mesh')==m['geometrySha256']
  assert all(shared.sha(client/e['path'])==e['sha256'] for e in m['clientFiles'])
  subprocess.run(['java','-Xverify:all','-cp',';'.join(map(str,[out/'linkage',server/'libs/*',server/'cache/classes'])),
    'PlayerBotLinkageCheck',str(server/'libs/game-server-4.8-SNAPSHOT.jar')],check=True,cwd=out,capture_output=True,text=True)
 except Exception:
  for e in saved:shutil.copy2(backup/e['path'],server/e['path'])
  (backup/'failed-manifest.json').write_text(json.dumps(m,indent=2));raise
 m['files'].append(dict(path='start.bat',original=m['launcherOriginalSha256'],installed=shared.sha(launcher)))
 m['guardFiles']=[e for e in m['baselineFiles'] if e['path'] not in {v['path'] for v in m['files']}|{'libs/playerbot-recruitment-fix.jar'}]
 m['retiredFiles']=['libs/playerbot-recruitment-fix.jar'];m['checks']=checks
 with __import__('zipfile').ZipFile(server/'libs/game-server-4.8-SNAPSHOT.jar') as jar:
  import hashlib
  m['bundledMediaSha256']={name:hashlib.sha256(jar.read('playerbots/media/'+name)).hexdigest() for name in ['bots.html','bots.css','bots.js']}
 (backup/'manifest.json').write_text(json.dumps(m,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='offline normal source builder output; no startup/attach/gameplay',files=m['files'],retiredFiles=m['retiredFiles']),indent=2))
 shutil.copy2(out/'checks.json',backup/'checks.json')
 print('OK: unchanged Maven Commons/GameServer outputs copied; old override archived/retired; receipt:',backup)

if __name__=='__main__':main()
