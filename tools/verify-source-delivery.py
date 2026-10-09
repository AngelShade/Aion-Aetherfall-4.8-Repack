"""Verify normal Maven outputs against the effective installed feature baseline; never rewrite JARs."""
import argparse,concurrent.futures,hashlib,json,subprocess,sys,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared

ALLOWED={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','snapshot','markClosing'},
 shared.PREFIX+'services/playerbot/PlayerBotOffense':{'routine'},
 shared.PREFIX+'services/playerbot/PlayerBotGearPolicy':{'eligible'},
 # Previous source-only appearance integrations are now guarded by a disabled
 # source feature flag. No native gameplay is enabled by this release.
 shared.PREFIX+'services/playerbot/PlayerBotAppearance':{'enabled','configure','tick','inventory'},
 shared.PREFIX+'services/PlayerBotHttpService':{'action'},
}

def main():
 if not (ROOT/'target-deploy/game-server/libs/playerbot-recruitment-fix.jar').is_file():
  raise RuntimeError('Historical override comparison is retired. Use tools/release-game-server.py --scope <ID> for current complete release verification.')
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 build=a.build.resolve();out=a.output.resolve();shared.validate_output(build);shared.validate_output(out);out.mkdir(parents=True,exist_ok=True)
 server=ROOT/'target-deploy/game-server';game=build/'game-server/game-server-4.8-SNAPSHOT.jar';commons=build/'commons/commons-4.8-SNAPSHOT.jar'
 assert game.exists() and commons.exists()
 old=server/'libs/playerbot-recruitment-fix.jar'
 with zipfile.ZipFile(old) as archive:classes=sorted(n[:-6] for n in archive.namelist() if n.endswith('.class'))
 with zipfile.ZipFile(game) as archive:
  assert archive.testzip() is None
  built=set(archive.namelist())
  assert 'com/aionemu/gameserver/services/playerbot/PlayerBotSpiritmaster.class' in built
  for name in ['bots.html','bots.css','bots.js']:
   assert archive.read('playerbots/media/'+name)==(ROOT/'game-server/config/playerbots/media'/name).read_bytes(),name

 def compare(name):
  before=shared.methods(old,name)
  if name+'.class' not in built:
   assert name.endswith('PlayerBotQuestRoutes$1'),name
   return dict(className=name,obsoleteDefinition=True)
  after=shared.methods(game,name)
  changed={shared.method_name(k) for k in before.keys()&after.keys() if before[k]!=after[k]}
  missing=before.keys()-after.keys()
  assert all(k.startswith('private ') and 'lambda$' in k for k in missing),(name,missing)
  assert changed.issubset(ALLOWED.get(name,set())),(name,changed)
  return dict(className=name,changedMethods=sorted(changed),retiredUnusedSynthetics=sorted(missing))
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:comparisons=list(pool.map(compare,classes))
 # Audit the full builder output without any old game/override JAR fallback.
 verifier=out/'linkage';verifier.mkdir(exist_ok=True)
 subprocess.run(['javac','-d',str(verifier),str(ROOT/'client-mods/playerbots/java/PlayerBotLinkageCheck.java')],check=True,capture_output=True,text=True)
 libs=[f for f in sorted((server/'libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar','commons-4.8-SNAPSHOT.jar','playerbot-recruitment-fix.jar'}]
 cp=';'.join(map(str,[verifier,game,commons,*libs,server/'cache/classes']))
 result=subprocess.run(['java','-Xverify:all','-cp',cp,'PlayerBotLinkageCheck',str(game)],check=True,cwd=out,capture_output=True,text=True)
 (out/'linkage.txt').write_text(result.stdout+result.stderr)
 inventory=json.loads((ROOT/'docs/INSTALLED_MODS.json').read_text());assert all(inventory['checks'].values())
 prior=shared.receipt_paths(server,'playerbots-recruitment-*/manifest.json')[-1]
 receipt=json.loads(prior.read_text());assert all(shared.sha(server/e['path'])==e['installed'] for e in receipt['files'])
 manifest=dict(feature='playerbots-source-build-spiritmaster',scope='PB-PORT-005B / PB-BUILD-001',deployment=str(server),previousReceipt=str(prior),
  builder='normal Maven reactor via tools/build-components.ps1; output JARs unchanged',
  files=[dict(path='libs/game-server-4.8-SNAPSHOT.jar',source=str(game),original=shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar'),installed=shared.sha(game)),
         dict(path='libs/commons-4.8-SNAPSHOT.jar',source=str(commons),original=shared.sha(server/'libs/commons-4.8-SNAPSHOT.jar'),installed=shared.sha(commons))],
  retiredOverrideSha256=shared.sha(old),launcherOriginalSha256=shared.sha(server/'start.bat'),baselineFiles=receipt['files'],
  clientFiles=inventory['clientFiles'],geometrySha256=shared.sha(server/'data/geo/models.mesh'),
  configFiles={str(f.relative_to(server)):shared.sha(f) for f in (server/'config').rglob('*') if f.is_file()},
  classComparison=comparisons,fullRuntimeLinkage=result.stdout.strip(),appearanceEnabled=False,gameAcceptance='pending user testing')
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2));print('OK: source build preserves '+str(len(classes))+' deployed definitions; '+result.stdout.strip())

if __name__=='__main__':main()
