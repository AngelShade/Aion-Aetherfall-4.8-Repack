"""Verify the effective bounded captain/gunner update without creating native actors."""
import argparse,hashlib,json,subprocess,zipfile
from pathlib import Path
import stage_companion_update as shared
from stage_steel_rake_completion_update import DATA,SCRIPTS,patch_data

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for key in ('package','classes','scripts','checks'):p.add_argument('--'+key,type=Path,required=True)
 p.add_argument('--installed-receipt',type=Path)
 a=p.parse_args();package=a.package.resolve();shared.validate_output(package)
 server=shared.ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text())
 payload=package/'libs/playerbot-recruitment-fix.jar';baseline=(a.installed_receipt or server)/'libs/playerbot-recruitment-fix.jar'
 if a.installed_receipt:
  assert json.loads((a.installed_receipt/'manifest.json').read_text())==m
 selected={e['path']:set(e['methods']) for e in m['incrementalChangedMethods']}
 assert len(selected)==2 and sum(map(len,selected.values()))==3
 for e in m['files']:
  live=server/e['path'];assert (shared.sha(live) if live.is_file() else None)==e['installed' if a.installed_receipt else 'original'],e['path']
  if a.installed_receipt and e['original'] is not None:assert shared.sha(a.installed_receipt/e['path'])==e['original']
  assert shared.sha(package/e['path'])==e['installed'],e['path']
 assert shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
 assert shared.sha(server/'data/geo/models.mesh')==m['unchangedGeometrySha256']
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
  assert new.testzip() is None
  assert set(new.namelist())-set(old.namelist())=={shared.PREFIX+'services/playerbot/PlayerBotSteelRakeCaptainTactics.class'}
  retained=[n for n in old.namelist() if n not in selected]
  assert all(old.read(n)==new.read(n) for n in retained)
  for name,allowed in selected.items():
   before=shared.methods(baseline,name[:-6]);after=shared.methods(payload,name[:-6]);source=shared.methods(a.classes,name[:-6])
   assert before.keys()==after.keys() and {k for k in before if before[k]!=after[k]}==allowed
   assert all(after[k]==source[k] for k in allowed)
  for name,digest in m['bundledMediaSha256'].items():assert hashlib.sha256(new.read('playerbots/media/'+name)).hexdigest()==digest
  session=shared.methods(payload,shared.PREFIX+'services/playerbot/PlayerBotSession')
  assert not any('PlayerBotAppearance' in line for lines in session.values() for line in lines)
 for rel in DATA:assert (package/rel).read_bytes()==patch_data(rel,((a.installed_receipt or server)/rel).read_bytes()),rel
 for name in SCRIPTS:
  rel='data/handlers/ai/instance/rakes/'+name+'.java'
  assert (package/rel).read_bytes()==(shared.ROOT/'game-server'/rel).read_bytes()
  compiled=list((a.scripts/'ai/instance/rakes').glob(name+'*.class'))
  assert compiled
  for file in compiled:
   if file.stem==name or file.stem.startswith(name+'$'):
    assert file.read_bytes()==(package/'cache/classes/ai/instance/rakes'/file.name).read_bytes()
 # Preserve each original TODO beside its implementation; capture absence remains explicit in documentation.
 for name in ('BrassEyeGroggetAI','ChiefGunnerKoakoaAI'):
  rel='game-server/data/handlers/ai/instance/rakes/'+name+'.java'
  original=subprocess.check_output(['git','show','HEAD:'+rel],cwd=shared.ROOT,text=True)
  source=(shared.ROOT/rel).read_text()
  comments=[line.strip() for line in original.splitlines() if any(s in line.lower() for s in ('todo','to do','need snif'))]
  assert all(comment in source for comment in comments)
 captain=shared.methods(package/'cache/classes','ai/instance/rakes/BrassEyeGroggetAI')
 assert not any('Method think:' in line for line in captain['private void resume();']), 'Native endCast must own post-cast scheduling'
 gunner=shared.methods(package/'cache/classes','ai/instance/rakes/ChiefGunnerKoakoaAI')
 pause=gunner['protected synchronized void handleIndividualSpawnedSummons(com.aionemu.gameserver.model.templates.ai.Percentage);']
 assert all(any(token in line for line in pause) for token in ('AIState.IDLE','AISubState.NONE','cancelCurrentSkill')), 'Queued native attacks/casts must be paused'
 # Existing cast/order/control regressions are run against this payload, preserving the concurrent engine fix.
 regression=shared.DEV_ROOT/'staging/target/playerbots-cast-execution-20261008/checks'
 effective=server/'libs/playerbot-recruitment-fix.jar' if a.installed_receipt else payload
 cp=str(a.checks)+';'+str(regression)+';'+str(effective)+';'+str(server/'libs/*')
 tests={'PlayerBotSteelRakeCheck':28,'PlayerBotSteelRakeCaptainTacticsCheck':16,'PlayerBotGroundNavigationCheck':57,
  'PlayerBotNavigationTrailCheck':17,'PlayerBotCastExecutionCheck':72,'PlayerBotEngineCheck':99,
  'PlayerBotStrategyCompositionCheck':49,'PlayerBotOffenseIntegrationCheck':44}
 for test,count in tests.items():
  r=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test],capture_output=True,text=True,cwd=a.checks)
  (package/(test+'.txt')).write_text(r.stdout+r.stderr,encoding='utf-8')
  assert r.returncode==0 and f'OK: {count} ' in r.stdout,test
 scriptcp=str(a.scripts)+';'+str(effective)+';'+str(server/'libs/*')+';'+str(server/'cache/classes')
 for test,count in {'SteelRakeCaptainPlanCheck':86,'SteelRakeTasksCheck':11}.items():
  r=subprocess.run(['java','-Xverify:all','-cp',scriptcp,'ai.instance.rakes.'+test],capture_output=True,text=True,cwd=a.checks)
  (package/(test+'.txt')).write_text(r.stdout+r.stderr,encoding='utf-8')
  assert r.returncode==0 and f'OK: {count} ' in r.stdout,test
  tests[test]=count
 report=dict(scope='PB-SCOPE-007A-R1',effectiveChangedMethods=3,retainedJarEntries=len(retained),
  originalTodoCommentsPreserved=7,staticDataEditsBounded=True,uninstalledAppearanceExcluded=True,offlineChecks=tests,
  gameplayAcceptance='pending user testing',retailCapture='unavailable; display offsets, extra wave composition and amplifier cadence are native-asset adaptations',
  payloadSha256=shared.sha(payload),installed=bool(a.installed_receipt))
 (package/('verification-installed.json' if a.installed_receipt else 'verification.json')).write_text(json.dumps(report,indent=2))
 print('OK: captain/gunner completion and effective regressions:',sum(tests.values()),'checks;',len(retained),'unrelated JAR entries preserved')
if __name__=='__main__':main()
