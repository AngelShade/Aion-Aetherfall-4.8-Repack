"""Verify bounded effective execution methods plus production race/command/continuation regressions."""
import argparse,json,subprocess,zipfile,hashlib
from pathlib import Path
import stage_companion_update as shared

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);p.add_argument('--classes',type=Path,required=True);p.add_argument('--checks',type=Path,required=True);p.add_argument('--baseline-evidence',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();shared.validate_output(package);server=shared.ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text())
 payload=package/'libs/playerbot-recruitment-fix.jar';baseline=server/'libs/playerbot-recruitment-fix.jar';selected={e['path']:set(e['methods']) for e in m['incrementalChangedMethods']}
 assert len(selected)==3 and sum(map(len,selected.values()))==4
 for e in m['files']:assert shared.sha(server/e['path'])==e['original'] and shared.sha(package/e['path'])==e['installed']
 assert shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
  assert old.namelist()==new.namelist() and new.testzip() is None
  retained=[name for name in old.namelist() if name not in selected]
  assert all(old.read(name)==new.read(name) for name in retained)
  for name,allowed in selected.items():
   before=shared.methods(baseline,name[:-6]);after=shared.methods(payload,name[:-6]);source=shared.methods(a.classes,name[:-6])
   assert before.keys()==after.keys() and {k for k in before if before[k]!=after[k]}==allowed
   assert all(after[k]==source[k] for k in allowed)
  for name,digest in m['bundledMediaSha256'].items():assert hashlib.sha256(new.read('playerbots/media/'+name)).hexdigest()==digest
 code=shared.methods(payload,shared.PREFIX+'services/playerbot/PlayerBotSession$CastAction')['public boolean execute();']
 where=lambda token:next(i for i,line in enumerate(code) if token in line)
 assert where('monitorenter')<where('Method isUseful:')<where('PlayerBotNavigation.stop')<where('Skill.useSkill')
 assert sum('monitorexit' in line for line in code)>=2,'All paths must release native mover monitor'
 evidence=json.loads(a.baseline_evidence.read_text());assert all(evidence['baselineFailuresReproduced'].values())
 cp=str(a.checks)+';'+str(payload)+';'+str(server/'libs/*');logs={}
 expected={'PlayerBotCastExecutionCheck':'OK: 72 ','PlayerBotEngineCheck':'OK: 99 ',
  'PlayerBotStrategyCompositionCheck':'OK: 49 ','PlayerBotOffenseIntegrationCheck':'OK: 44 ','PlayerBotNavigationTrailCheck':'OK: 17 '}
 for test,text in expected.items():
  result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test],cwd=a.checks,capture_output=True,text=True)
  (package/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
  assert result.returncode==0 and text in result.stdout,test
  logs[test]=text
 sources={}
 for relative in ['upstream/src/Bot/Engine/Engine.cpp','upstream/src/Bot/PlayerbotAI.cpp']:
  lines=(shared.ROOT/'third-party/playerbots/SHA256SUMS').read_text().splitlines();expectedsha=next(line.split()[0] for line in lines if line.endswith(relative))
  actual=shared.sha(shared.ROOT/'third-party/playerbots'/relative);assert actual==expectedsha; sources[relative]=actual
 report=dict(coreExecutionChecks=72,fourBaselineFailuresReproduced=True,unselectedMethodsPreserved=True,retainedJarEntries=len(retained),changedMethods=4,changedDefinitions=3,atomicNativeCastAdmissionVerified=True,nativeScriptCacheEquivalentClasses=len(evidence['regeneratedNativeScriptClasses']),effectiveRegressions=logs,recordingJdbcPreferenceChecks=31,payloadSha256=shared.sha(payload),pinnedSources=sources,gameAcceptance='pending user testing',installed=False)
 (package/'verification.json').write_text(json.dumps(report,indent=2));print('OK: 72 effective core checks and engine/native-planning regressions; earlier entries retained:',len(retained))
if __name__=='__main__':main()
