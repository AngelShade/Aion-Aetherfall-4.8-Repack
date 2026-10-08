"""Verify effective summon/early-recall paths and preservation against actual installation."""
import argparse,json,subprocess,zipfile
from pathlib import Path
import stage_companion_update as shared
from verify_runtime_linkage import verify

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True)
 p.add_argument('--classes',type=Path,required=True);p.add_argument('--checks',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();shared.validate_output(package);server=shared.ROOT/'target-deploy/game-server'
 m=json.loads((package/'manifest.json').read_text());payload=package/'libs/playerbot-recruitment-fix.jar';baseline=server/'libs/playerbot-recruitment-fix.jar'
 assert m['feature']=='playerbot-summon-distance-recall' and m['offlineOnly']
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
 def code(name,key):return shared.methods(payload,shared.PREFIX+name)[key]
 tick=code('services/playerbot/PlayerBotSession','synchronized boolean tick();')
 where=lambda c,token:next(i for i,l in enumerate(c) if token in l)
 early=where(tick,'PlayerBotRecall.automatic')
 for token in ['PlayerBotTrade.tick','PlayerBotTransfers.follow','PlayerBotMoveController.hasFailed','PlayerBotRecovery.tick','PlayerBotRevival.ready']:
  assert early<where(tick,token),token
 assert not any('PlayerBotAppearance' in l for l in tick)
 recall=shared.methods(payload,shared.PREFIX+'services/playerbot/PlayerBotRecall')
 transition=next(v for k,v in recall.items() if 'lambda$recall$' in k)
 for token in ['ExchangeService.cancelExchange','cancelCurrentSkill','cancelUseItem','notifyMoveObservers','DropService.closeDropList','PlayerBotSession.order','PlayerBotService.relocate','PlayerBotQuestSync.returnToOwner']:
  where(transition,token)
 assert where(transition,'PlayerBotSession.order')<where(transition,'PlayerBotService.relocate')
 automatic=recall['static boolean automatic(com.aionemu.gameserver.services.playerbot.PlayerBotSession);']
 assert not any(token in l for token in ['isCasting','isTrading','isLooting','canPerformMove','isInCombat','PlayerBotSummonPolicy.request','PlayerBotPartyBehavior'] for l in automatic)
 mover=code('controllers/movement/PlayerBotMoveController','public synchronized void abortMove();')
 assert where(mover,'PlayerBotRecall.recalling')<where(mover,'Field failed:Z')
 linkage=verify(payload,package/'effective-linkage')
 cp=';'.join(map(str,[a.checks,payload,server/'libs/*']));logs={};recallCount=0
 for test,count in [('PlayerBotRecallCheck',None),('PlayerBotTravelFormationCheck',679),('PlayerBotCastExecutionCheck',72),('PlayerBotEngineCheck',99),('PlayerBotStrategyCompositionCheck',49),('PlayerBotOffenseIntegrationCheck',44),('PlayerBotFollowSpeedCheck',6),('PlayerBotNavigationTrailCheck',17),('PlayerBotGroundNavigationCheck',57),('PlayerBotFormationCheck',314)]:
  result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test],cwd=a.checks,capture_output=True,text=True)
  (package/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8');assert result.returncode==0,test+': '+result.stderr
  if count:assert 'OK: '+str(count)+' ' in result.stdout,test
  line=next(l for l in result.stdout.splitlines() if l.startswith('OK: ') and (count is None or l.startswith('OK: '+str(count)+' ')));logs[test]=line
  if test=='PlayerBotRecallCheck':recallCount=int(line.split()[1]);assert recallCount>=160
 report=dict(payloadSha256=shared.sha(payload),baselineSha256=shared.sha(baseline),recallChecks=recallCount,changedDefinitions=6,changedMethods=8,
             earlyRecallBeforeActions=True,unselectedMethodsPreserved=True,retainedJarEntries=len(retained),runtimeLinkage=linkage,effectiveRegressions=logs,
             automaticThresholdMetres=60,installed=False,gameAcceptance='pending user testing')
 (package/'verification.json').write_text(json.dumps(report,indent=2));print('OK: effective early recall, cancellable summon, native guard regression and prior checks:',recallCount)

if __name__=='__main__':main()
