"""Verify source-selected item-use methods, actual native checks and cumulative preservation."""
import argparse,json,subprocess,zipfile
from pathlib import Path
import stage_companion_update as shared
from verify_runtime_linkage import verify

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True)
 p.add_argument('--classes',type=Path,required=True);p.add_argument('--checks',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();shared.validate_output(package);server=shared.ROOT/'target-deploy/game-server'
 m=json.loads((package/'manifest.json').read_text());payload=package/'libs/playerbot-recruitment-fix.jar';baseline=server/'libs/playerbot-recruitment-fix.jar'
 assert m['feature']=='playerbot-item-task-cast-completion' and m['offlineOnly']
 selected={e['path']:set(e['methods']) for e in m['incrementalChangedMethods']};assert len(selected)==6 and sum(map(len,selected.values()))==8
 for e in m['files']:assert shared.sha(server/e['path'])==e['original'] and shared.sha(package/e['path'])==e['installed'],e['path']
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
  assert new.testzip() is None and set(old.namelist()).issubset(new.namelist())
  retained=[n for n in old.namelist() if n not in selected];assert all(old.read(n)==new.read(n) for n in retained)
  for n,allowed in selected.items():
   before=shared.methods(baseline,n[:-6]);after=shared.methods(payload,n[:-6]);source=shared.methods(a.classes,n[:-6])
   assert before.keys()==after.keys() and {k for k in before if before[k]!=after[k]}==allowed,n
   assert all(after[k]==source[k] for k in allowed),n
  assert all(new.read(n)==(a.classes/n).read_bytes() for n in m['newClasses'])
 tick=shared.methods(payload,shared.PREFIX+'services/playerbot/PlayerBotSession')['synchronized boolean tick();']
 assert any('PlayerBotItemUse.pause' in line for line in tick)
 assert not any('hasTask:' in line or 'PlayerBotAppearance' in line for line in tick)
 helper=shared.methods(payload,shared.PREFIX+'services/playerbot/PlayerBotItemUse')
 pause=helper['static boolean pause(com.aionemu.gameserver.model.gameobjects.player.Player, boolean);']
 assert any('hasScheduledTask' in line for line in pause) and any('cancelUseItem' in line for line in pause)
 assert not any('notifyMoveObservers' in line or 'cancelCurrentSkill' in line for line in pause)
 linkage=verify(payload,package/'effective-linkage');logs={}
 cp=';'.join(map(str,[a.checks,payload,server/'libs/*']))
 for test,count in [('PlayerBotItemUseCheck',147),('PlayerBotRecallCheck',165),('PlayerBotTravelFormationCheck',679),('PlayerBotCastExecutionCheck',72),('PlayerBotTradeCheck',None),('PlayerBotEngineCheck',99),('PlayerBotStrategyCompositionCheck',49),('PlayerBotOffenseIntegrationCheck',44),('PlayerBotFollowSpeedCheck',6),('PlayerBotNavigationTrailCheck',17),('PlayerBotGroundNavigationCheck',57),('PlayerBotFormationCheck',314)]:
  result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test],cwd=a.checks,capture_output=True,text=True)
  (package/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8');assert result.returncode==0,test+': '+result.stderr
  if count:assert 'OK: '+str(count)+' ' in result.stdout,test
  logs[test]=next(l for l in result.stdout.splitlines() if l.startswith('OK: ') and (count is None or l.startswith('OK: '+str(count)+' ')))
 report=dict(payloadSha256=shared.sha(payload),baselineSha256=shared.sha(baseline),itemUseChecks=147,
             changedDefinitions=6,changedMethods=8,activeItemTaskOnly=True,unselectedMethodsPreserved=True,
             retainedJarEntries=len(retained),runtimeLinkage=linkage,effectiveRegressions=logs,
             installed=False,gameAcceptance='pending user testing; reported cancellation path reproduced offline')
 (package/'verification.json').write_text(json.dumps(report,indent=2));print('OK: 147 native task/cast checks; cumulative preservation, linkage and effective regressions verified')

if __name__=='__main__':main()
