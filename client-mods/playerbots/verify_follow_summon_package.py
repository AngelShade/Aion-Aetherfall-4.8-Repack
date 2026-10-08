"""Verify effective follow/summon production paths and preservation before offline install."""
import argparse,hashlib,json,re,subprocess,zipfile
from pathlib import Path
import stage_companion_update as shared

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);p.add_argument('--classes',type=Path,required=True);p.add_argument('--checks',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();shared.validate_output(package);server=shared.ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text())
 assert m['offlineOnly'] and m['feature']=='playerbot-follow-summon-controls'
 payload=package/'libs/playerbot-recruitment-fix.jar';baseline=server/'libs/playerbot-recruitment-fix.jar';selected={e['path']:set(e['methods']) for e in m['incrementalChangedMethods']}
 if m.get('refinementOf'):assert len(selected)==1 and sum(map(len,selected.values()))==3
 else:assert len(selected)==6 and sum(map(len,selected.values()))==14
 for e in m['files']:assert shared.sha(server/e['path'])==e['original'] and shared.sha(package/e['path'])==e['installed'],e['path']
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
  assert new.testzip() is None and set(old.namelist()).issubset(new.namelist())
  retained=[name for name in old.namelist() if name not in selected and name not in m.get('offlineReplacedHelpers',[])]
  assert all(old.read(name)==new.read(name) for name in retained)
  for name,allowed in selected.items():
   before=shared.methods(baseline,name[:-6]);after=shared.methods(payload,name[:-6]);source=shared.methods(a.classes,name[:-6])
   assert before.keys()==after.keys() and {k for k in before if before[k]!=after[k]}==allowed,name
   assert all(after[k]==source[k] for k in allowed),name
  for name in m['newClasses']:assert new.read(name)==(a.classes/name).read_bytes(),name
  for name in m.get('offlineReplacedHelpers',[]):
   assert new.read(name)==(a.classes/name).read_bytes(),name
   before=shared.methods(baseline,name[:-6]);after=shared.methods(payload,name[:-6])
   assert set(before).issubset(after)
   assert {shared.method_name(k) for k in set(after)-set(before)}=={'remember','goal','forget'}
   assert {shared.method_name(k) for k in before if before[k]!=after[k]}=={'refresh','{};'},'Unreviewed existing helper method changed'
  for name,digest in m['bundledMediaSha256'].items():assert hashlib.sha256(new.read('playerbots/media/'+name)).hexdigest()==digest
 configclass=shared.PREFIX+'configs/main/PlayerBotConfig'
 # Adding one native @Property field is explicitly cold-load-only; all old
 # configuration methods and fields remain, and native processing is tested.
 assert shared.methods(server/'libs/game-server-4.8-SNAPSHOT.jar',configclass)==shared.methods(payload,configclass)
 config=(package/'config/main/playerbots.properties').read_text();assert 'gameserver.playerbots.enable = true' in config and 'gameserver.playerbots.summon.enable = true' in config
 for line in (server/'config/main/playerbots.properties').read_text().splitlines():
  if '=' in line and not line.lstrip().startswith('#') and not line.startswith('gameserver.playerbots.enable'):
   key,value=map(str.strip,line.split('=',1));assert re.search(r'(?m)^'+re.escape(key)+r'\s*=\s*'+re.escape(value)+r'\s*$',config),key
 # No movement refresh is admitted before the existing cast/stale callback guard.
 movement=shared.methods(payload,shared.PREFIX+'controllers/movement/PlayerBotMoveController')['private void moveStep();']
 where=lambda token:next(i for i,line in enumerate(movement) if token in line)
 assert where('Player.isCasting')<where('PlayerBotFollowIntent.refresh')<where('PlayerBotFollowSpeed.update')
 for name,key in [(shared.PREFIX+'services/playerbot/PlayerBotFollowIntent','public static void refresh(com.aionemu.gameserver.model.gameobjects.player.Player);'),(shared.PREFIX+'services/playerbot/PlayerBotFollowIntent','static com.aionemu.gameserver.services.playerbot.PlayerBotNavigation$Point goal(com.aionemu.gameserver.model.gameobjects.player.Player);'),(shared.PREFIX+'services/playerbot/PlayerBotFormation','public static double speedMultiplier(com.aionemu.gameserver.model.gameobjects.player.Player, float, float, float);')]:
  code=shared.methods(payload,name)[key]
  assert not any(token in line for token in ['PlayerBotFormation.destination','PlayerBotFormationLayout','PlayerBotService','PlayerBotCombatPosition'] for line in code),'Movement tick reaches service/session formation locks'
 recovery=shared.methods(payload,shared.PREFIX+'services/playerbot/PlayerBotRecovery')['static void tick(com.aionemu.gameserver.services.playerbot.PlayerBotSession);']
 assert any('PlayerBotSummonPolicy.regroup' in line for line in recovery) and not any('PlayerBotTravel.summonAll' in line for line in recovery)
 cp=str(a.checks)+';'+str(payload)+';'+str(server/'libs/*');logs={}
 tests={'PlayerBotTravelFormationCheck':'OK: 679 ', 'PlayerBotCastExecutionCheck':'OK: 72 ', 'PlayerBotEngineCheck':'OK: 99 ',
  'PlayerBotStrategyCompositionCheck':'OK: 49 ', 'PlayerBotOffenseIntegrationCheck':'OK: 44 ', 'PlayerBotFollowSpeedCheck':'OK: 6 ',
  'PlayerBotNavigationTrailCheck':'OK: 17 ', 'PlayerBotGroundNavigationCheck':'OK: 57 ', 'PlayerBotFormationCheck':'OK: '}
 for test,expected in tests.items():
  result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test],cwd=a.checks,capture_output=True,text=True)
  (package/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8');assert result.returncode==0 and expected in result.stdout,test+': '+result.stderr
  logs[test]=next(line for line in result.stdout.splitlines() if line.startswith('OK:') and (test!='PlayerBotEngineCheck' or '99 ' in line))
 sources={};lines=(shared.ROOT/'third-party/playerbots/SHA256SUMS').read_text().splitlines()
 for relative in ['upstream/src/Ai/Base/Actions/FollowActions.cpp','upstream/src/Ai/Base/Actions/MovementActions.cpp']:
  expected=next(line.split()[0] for line in lines if line.endswith(relative));actual=shared.sha(shared.ROOT/'third-party/playerbots'/relative);assert actual==expected;sources[relative]=actual
 inventory=json.loads((package/'inventory-before.json').read_text())
 assert all(shared.sha(shared.CLIENT_ROOT/e['path'])==e['sha256'] for e in inventory['clientFiles'])
 report=dict(formationSummonChecks=679,changedMethods=sum(map(len,selected.values())),changedDefinitions=len(selected),retainedJarEntries=len(retained),unselectedMethodsPreserved=True,movementServiceLockLookupAbsent=True,offlineOnly=True,payloadSha256=shared.sha(payload),configSha256=shared.sha(package/'config/main/playerbots.properties'),effectiveRegressions=logs,pinnedSources=sources,clientHashesPreserved=len(inventory['clientFiles']),gameAcceptance='pending user testing',installed=False)
 (package/'verification.json').write_text(json.dumps(report,indent=2));print('OK: effective follow/summon and core regressions; retained earlier JAR entries:',len(retained))
if __name__=='__main__':main()
