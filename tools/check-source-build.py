"""Run bounded world-free companion regression entrypoints against only normal builder outputs."""
import argparse,json,subprocess,hashlib,re,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TESTS={'PlayerBotMetadataCheck':35,'PlayerBotRepositoryCheck':24,'PlayerBotPopulationCheck':83,'PlayerBotWorldCheck':102,'PlayerBotWorldTradeCheck':90,'PlayerBotWorldProgressCheck':32,'PlayerBotAssassinCheck':123,'PlayerBotGladiatorCheck':66,'PlayerBotTemplarCheck':71,'PlayerBotChanterCheck':89,'PlayerBotClericCheck':87,'PlayerBotSpiritmasterCheck':59,'PlayerBotSorcererCheck':54,'PlayerBotItemUseCheck':147,
 'PlayerBotRecallCheck':165,'PlayerBotTravelFormationCheck':679,'PlayerBotCastExecutionCheck':72,
 'PlayerBotTradeCheck':56,'PlayerBotEngineCheck':99,'PlayerBotStrategyCompositionCheck':49,
 'PlayerBotOffenseIntegrationCheck':44,'PlayerBotFollowSpeedCheck':6,'PlayerBotNavigationTrailCheck':17,
 'PlayerBotGroundNavigationCheck':70,'PlayerBotCollisionPerformanceCheck':448,'PlayerBotFormationCheck':314,'PlayerBotWorldPerformanceCheck':2994,'PlayerBotShutdownCheck':16,'PlayerBotWorldRepairCheck':24,'PlayerBotSharedRepairCheck':141,'PlayerBotPopulationPipelineCheck':13,'PlayerBotPopulationRotationCheck':537,'PlayerBotPlayerZoneCheck':144}

TESTS.update({'PlayerBotRosterBatchCheck':25, 'PlayerBotCheckpointCreationCheck':22, 'PlayerBotRaidCheck':1221})
TESTS['PlayerBotPopulationScaleCheck']=64
TESTS['PlayerBotPopulationRepositoryCheck']=69
TESTS['PlayerBotPopulationPipelineCheck']=21
TESTS['PlayerBotPopulationLifecycleCheck']=176
TESTS['PlayerBotPopulationLifecycleServiceCheck']=43
TESTS['PlayerBotSchedulerCpuCheck']=62
TESTS['PlayerBotCpuPlanningCheck']=43
TESTS['PlayerBotTravelAdmissionCheck']=24
TESTS['PlayerBotTerrainThroughputCheck']=24
TESTS['PlayerBotWarehouseCheck']=117
TESTS['PlayerBotMailCheck']=87
TESTS['PlayerBotFieldActivityCheck']=534
TESTS['PlayerBotLegionStorageCheck']=98
TESTS['PlayerBotLegionInvitationCheck']=157
TESTS['PlayerBotNamesCheck']=46
TESTS['PlayerBotPopulationFocusCheck']=8067
TESTS['PlayerBotPendingFocusCheck']=20
TESTS['PlayerBotPopulationMixCheck']=17
TESTS['PlayerBotActivityRegionCheck']=13
TESTS['PlayerBotResponsivenessCheck']=6013
TESTS['PlayerBotPopulationAdmissionCheck']=39024
TESTS['PlayerBotExecutionCheck']=39
TESTS['PlayerBotAuditCoverageCheck']=32
TESTS['PlayerBotConversationCheck']=18
TESTS['PlayerBotQuestObjectivesCheck']=35
TESTS['PlayerBotIdleCastingCheck']=114
TESTS['PlayerBotChanterCheck']=99
TESTS['PlayerBotFieldCombatCheck']=59
TESTS['PlayerBotNameLookupCheck']=53
TESTS['PlayerBotQuestCapabilityCheck']=40
TESTS['PlayerBotLegionPresenceCheck']=44
TESTS['PlayerBotAllianceControlCheck']=82
TESTS['PlayerBotDecisionIsolationCheck']=32
TESTS['PlayerBotDecisionWorkersCheck']=8313
TESTS['PlayerBotSupportOrderingCheck']=1019
TESTS['PlayerBotActiveWorkerCheck']=3166
TESTS['PlayerBotNpcPatrolCheck']=13
TESTS['PlayerBotRouteCadenceCheck']=70
TESTS['PlayerBotRouteContinuationCheck']=238
TESTS['PlayerBotNavigationHandoffCheck']=19
TESTS['PlayerBotNavigationBudgetCheck']=12800
TESTS['PlayerBotNavigationConfigCheck']=28
TESTS['PlayerBotConfigLocationCheck']=10
TESTS['PlayerBotPartialRouteCheck']=187
TESTS['PlayerBotEarlyRoutePrefetchCheck']=11
TESTS['PlayerBotTownPolicyCheck']=134
TESTS['PlayerBotTownServiceIntegrationCheck']=8
TESTS['PlayerBotPostKillTravelCheck']=61
TESTS['PlayerBotHealingPlanningCheck']=126
TESTS['PlayerBotUtilityPlanningCheck']=38
TESTS['PlayerBotNonCombatBuffCheck']=47
TESTS['PlayerBotDecisionNavigationCheck']=14
TESTS['PlayerBotNativeRouteYieldCheck']=83
TESTS['PlayerBotCapitalAreaCheck']=116
TESTS['PlayerBotCampDistributionCheck']=3020
TESTS['PlayerBotNavigationPendingCheck']=23
TESTS['PlayerBotFailedTravelCheck']=21
TESTS['PlayerBotCampLandingCheck']=27
TESTS['PlayerBotNavigationSearchCheck']=1229
TESTS['PlayerBotCombatGearCheck']=29
TESTS['PlayerBotCustodyCheck']=94
TESTS['PlayerBotItemViewCheck']=29
TESTS['PlayerBotSupportCooperationCheck']=56
TESTS['PlayerBotForcedFollowCheck']=20
TESTS['PlayerBotWeaponStanceCheck']=169
TESTS['PlayerBotHeightTraversalCheck']=84
TESTS['PlayerBotTargetVisibilityCheck']=1845
TESTS['PlayerBotQuestSkillCounterCheck']=311
TESTS['PlayerBotQuestItemOfferCheck']=15
TESTS['PlayerBotQuestExchangeCheck']=48
TESTS['PlayerBotQuestCraftingCheck']=267
TESTS['PlayerBotQuestMaterialsCheck']=723
TESTS['PlayerBotNativeInteractionCheck']=24
TESTS['PlayerBotQuestFeatureConfigCheck']=429
TESTS['PlayerBotQuestPrerequisitesCheck']=170
TESTS['PlayerBotNativeQuestItemChainCheck']=168
TESTS['PlayerBotNativeCraftingTaskCheck']=57
TESTS['PlayerBotNativeGatheringTaskCheck']=102

QUEST_TESTS={'EltnenLifeStoneDiaryCheck':1881,'KaligaQuestCreditCheck':424,'KaligaEncounterCheck':68,'NativeQuestActionsCheck':4377}
QUEST_TESTS.update({'NativeSielQuestCheck':866,'NativeDispatchQuestCheck':552,'NativeDaevanionQuestCheck':2341,
 'NativeCountedHuntCheck':847,'NativeRaeQuestCheck':481,'NativeReportCircuitCheck':196,
 'NativeExpertQualificationCheck':430,'NativeTappingQuestCheck':70,'NativeMasterQualificationCheck':370})
QUEST_TESTS['NativeMasterFinalQualificationCheck']=252
QUEST_TESTS['NativeStoryRewardChoicesCheck']=328
QUEST_TESTS['NativeStoryFollowUpCheck']=58
TESTS['PlayerBotStoryRewardIntentCheck']=19
TESTS['PlayerBotNativeXmlQuestCheck']=80
TESTS['PlayerBotQuestObjectCommitmentCheck']=71
TESTS['PlayerBotQuestTransformationCheck']=80
TESTS['PlayerBotQuestItemTargetCheck']=50
QUEST_TESTS['NativeKillObjectFamilyCheck']=490

def reviewed_native_fixtures():
 catalogue=ROOT/'tools/native-quest-fixtures.json'
 names=json.loads(catalogue.read_text())['handlers']
 assert len(names)==len(set(names)) and names, 'Ambiguous native fixture catalogue'
 assert all(name.startswith('game-server/data/handlers/quest/') and '..' not in Path(name).parts and '\\' not in name for name in names), 'Invalid native fixture path'
 return [ROOT/name for name in names]

def quest_fixtures():
 return [ROOT/'game-server/test/com/aionemu/gameserver/questEngine'/(name+'.java') for name in QUEST_TESTS]+[ROOT/'game-server/data/handlers'/name for name in ['quest/eltnen/_1035RefreshingtheSprings.java','quest/eltnen/_1038TheShadowsCommand.java','quest/heiron/_18602NightmareinShiningArmor.java','instance/KromedesTrialInstance.java','ai/GeneralNpcAI.java','ai/AggressiveNpcAI.java','ai/instance/kromedesTrial/KaligaAI.java',
  'quest/poeta/_1001TheKerubThreat.java','quest/poeta/_1002RequestoftheElim.java','quest/poeta/_1003IllegalLogging.java','quest/poeta/_1004NeutralizingOdium.java','quest/poeta/_1005BarringtheGate.java',
  'quest/ishalgen/_2001ThinkingAhead.java','quest/ishalgen/_2003TreasureOfTheDeceased.java','quest/ishalgen/_2004ACharmedCube.java','quest/ishalgen/_2005TeachingaLesson.java','quest/ishalgen/_2006HitThemWhereitHurts.java','quest/ishalgen/_2007WheresRaeThisTime.java','ai/ActionItemNpcAI.java','ai/quests/QuestItemNpcAI.java']]

def sha(path):
 with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def fixtures():
 return sorted(set(sorted((ROOT/'game-server/test/com/aionemu/gameserver/services/playerbot').glob('*.java'))+[ROOT/'game-server/test/com/aionemu/gameserver/ShutdownHookCheck.java']+quest_fixtures()+reviewed_native_fixtures()))

def binding(build):
 libs=[f for f in sorted((ROOT/'target-deploy/game-server/libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar','commons-4.8-SNAPSHOT.jar','playerbot-recruitment-fix.jar'}]
 paths=[Path(__file__),ROOT/'tools/check-database-shutdown.py',ROOT/'tools/java/DatabaseShutdownCheck.java',ROOT/'tools/check-player-skill-cancellation.py',ROOT/'tools/java/PlayerSkillCancellationCheck.java',*[ROOT/'tools'/name for name in ['build-manifest.py','test-source-release-methods.py','deliver-source-component.py','prepare-source-release.py','document-playerbot-config.py','release-game-server.py','verify-source-delivery.py','quest-handler-inventory.py','check-source-static-data.py','check-source-browser.py','check-playerbot-ui-behavior.py']],build/'build-manifest.json',build/'game-server/game-server-4.8-SNAPSHOT.jar',build/'commons/commons-4.8-SNAPSHOT.jar',*fixtures(),*libs]
 from importlib import import_module
 restoration=import_module('check-npc-restoration')
 paths.extend([ROOT/'tools/check-npc-restoration.py',Path(restoration.shared.__file__),restoration.BASELINE,ROOT/'tools/native-quest-fixtures.json',ROOT/'tools/QuestHandlerActionInventory.java',ROOT/'tools/test-quest-action-inventory.py',ROOT/'tools/java/PlayerBotMaterialCatalogBuildCheck.java',ROOT/'tools/configure-playerbot-level-spread.py',ROOT/'tools/playerbot-config-paths.py',ROOT/'tools/move-playerbot-configuration.py'])
 return {str(p.resolve()):sha(p) for p in paths}

def validate(build,out):
 from importlib import import_module
 assert json.loads((out/'npc-restoration.json').read_text())==import_module('check-npc-restoration').verify(build),'NPC restoration evidence changed'
 evidence=json.loads((out/'evidence.json').read_text())
 assert evidence['version']==1 and evidence['inputs']==binding(build),'Regression inputs/artifacts changed'
 expected=TESTS|QUEST_TESTS|{'ShutdownHookCheck':14,'DatabaseShutdownCheck':17,'PlayerSkillCancellationCheck':41}
 assert set(evidence['suites'])==set(expected),'Missing/extra regression suites'
 checks=json.loads((out/'checks.json').read_text())
 assert set(checks)==set(expected),'Missing suite result'
 classes={str(p.relative_to(build/'checks')):sha(p) for p in sorted((build/'checks').rglob('*.class'))}
 assert classes and evidence['classes']==classes,'Compiled regression fixtures changed'
 tool=evidence['tooling'];assert tool['exitCode']==0 and tool['count']>0 and sha(out/'tooling.txt')==tool['reportSha256'],'Failed/changed tooling checks'
 assert 'OK' in (out/'tooling.txt').read_text().splitlines(),'Missing tooling success'
 assert int(re.search(r'Ran (\d+) tests', (out/'tooling.txt').read_text()).group(1))==tool['count'],'Wrong tooling count'
 assert sha(out/'compile.txt')==evidence['compileSha256'],'Fixture compilation evidence changed'
 for name,count in expected.items():
  suite=evidence['suites'][name]
  assert suite['exitCode']==0 and suite['count']==count,'Failed/wrong-count suite: '+name
  assert sha(out/(name+'.txt'))==suite['reportSha256'],'Regression report changed: '+name
  assert checks[name]==suite['result'] and checks[name].startswith('OK: '+str(count)+' '),'Wrong suite result: '+name
  assert checks[name] in (out/(name+'.txt')).read_text().splitlines(),'Missing successful result: '+name
 database=json.loads((out/'database-shutdown/evidence.json').read_text())
 assert database['mode']=='normal-builder-artifact' and database['count']==17,'Database shutdown must verify actual normal artifacts'
 for jar in [build/'game-server/game-server-4.8-SNAPSHOT.jar',build/'commons/commons-4.8-SNAPSHOT.jar']:
  assert database['inputs'].get(str(jar))==sha(jar),'Database shutdown artifact binding changed'
 assert all(sha(Path(path))==digest for path,digest in database['inputs'].items()),'Database shutdown inputs changed'
 assert database['reportSha256']==sha(out/'database-shutdown/checks.txt') and database['compileSha256']==sha(out/'database-shutdown/compile.txt'),'Database shutdown reports changed'
 assert database['abiSha256']==sha(out/'database-shutdown/abi.json'),'Database shutdown native descriptor evidence changed'
 assert database['classes']=={str(p.relative_to(out/'database-shutdown/classes')):sha(p) for p in sorted((out/'database-shutdown/classes').rglob('*.class'))},'Database shutdown fixture changed'
 assert database['classes'] and not any(name.replace('\\','/') in {'com/aionemu/commons/database/DatabaseFactory.class','com/aionemu/gameserver/ShutdownHook.class','com/aionemu/gameserver/utils/ThreadPoolManager.class'} for name in database['classes']),'Database shutdown fixture must not replace repaired JAR classes'
 cancellation=json.loads((out/'player-skill-cancellation/evidence.json').read_text())
 assert cancellation['mode']=='normal-builder-artifact' and cancellation['count']==41,'Player cancellation must verify actual normal artifacts'
 for jar in [build/'game-server/game-server-4.8-SNAPSHOT.jar',build/'commons/commons-4.8-SNAPSHOT.jar']:
  assert cancellation['inputs'].get(str(jar))==sha(jar),'Player cancellation artifact binding changed'
 assert all(sha(Path(path))==digest for path,digest in cancellation['inputs'].items()),'Player cancellation inputs changed'
 assert cancellation['result']==checks['PlayerSkillCancellationCheck'],'Player cancellation suite result changed'
 assert cancellation['checksSha256']==sha(out/'player-skill-cancellation/checks.txt') and cancellation['compileSha256']==sha(out/'player-skill-cancellation/compile.txt'),'Player cancellation reports changed'
 assert cancellation['abiSha256']==sha(out/'player-skill-cancellation/abi.json'),'Player cancellation native descriptor evidence changed'
 assert cancellation['classes']=={str(p.relative_to(out/'player-skill-cancellation/classes')):sha(p) for p in sorted((out/'player-skill-cancellation/classes').rglob('*.class'))},'Player cancellation fixture changed'
 assert cancellation['classes'] and not any(name.replace('\\','/')=='com/aionemu/gameserver/controllers/PlayerController.class' for name in cancellation['classes']),'Player cancellation fixture must not replace repaired JAR class'
 return evidence

def main():
 if not __debug__:raise RuntimeError('Regression validation cannot run with Python optimization enabled')
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 build=a.build.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
 # Never leave a previous successful report usable after a failed rerun.
 (out/'evidence.json').unlink(missing_ok=True)
 before=binding(build)
 from importlib import import_module
 (out/'npc-restoration.json').write_text(json.dumps(import_module('check-npc-restoration').verify(build),indent=2))
 tooling=subprocess.run([sys.executable,str(ROOT/'tools/test-source-release-methods.py')],cwd=ROOT,capture_output=True,text=True)
 (out/'tooling.txt').write_text(tooling.stdout+tooling.stderr)
 assert tooling.returncode==0,tooling.stderr
 tool_count=int(re.search(r'Ran (\d+) tests',tooling.stderr).group(1))
 libs=[f for f in sorted((ROOT/'target-deploy/game-server/libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar','commons-4.8-SNAPSHOT.jar','playerbot-recruitment-fix.jar'}]
 cp=';'.join(map(str,[build/'checks',build/'game-server/game-server-4.8-SNAPSHOT.jar',build/'commons/commons-4.8-SNAPSHOT.jar',*libs]))
 (build/'checks').mkdir(exist_ok=True)
 # Recompilation cannot run a stale class whose fixture source was removed.
 for compiled in (build/'checks').rglob('*.class'):compiled.unlink()
 argfile=out/'javac.args'
 argfile.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in fixtures()))
 compile_result=subprocess.run(['javac','-cp',cp,'-d',str(build/'checks'),'@'+str(argfile)],cwd=ROOT,capture_output=True,text=True)
 (out/'compile.txt').write_text(compile_result.stdout+compile_result.stderr)
 assert compile_result.returncode==0,compile_result.stderr
 reports={};suites={}
 for test,count in TESTS.items():
  cwd=ROOT if test in {'PlayerBotWorldProgressCheck','PlayerBotAssassinCheck','PlayerBotGladiatorCheck','PlayerBotTemplarCheck','PlayerBotChanterCheck','PlayerBotClericCheck','PlayerBotSpiritmasterCheck','PlayerBotSorcererCheck',
   'PlayerBotQuestCraftingCheck','PlayerBotQuestPrerequisitesCheck','PlayerBotNativeCraftingTaskCheck','PlayerBotNativeXmlQuestCheck'} else build/'checks'
  args=[str(ROOT/'game-server')] if test in {'PlayerBotRepositoryCheck','PlayerBotPlayerZoneCheck','PlayerBotAuditCoverageCheck','PlayerBotIdleCastingCheck','PlayerBotCombatGearCheck','PlayerBotTargetVisibilityCheck','PlayerBotStoryRewardIntentCheck','PlayerBotQuestTransformationCheck','PlayerBotQuestItemTargetCheck','PlayerBotNpcPatrolNativeSpawnCheck','PlayerBotNpcPatrolAdmissionCheck','PlayerBotNativeRouteYieldCheck','PlayerBotCapitalAreaCheck','PlayerBotConfigLocationCheck','PlayerBotCampDistributionCheck','PlayerBotNonCombatBuffCheck','PlayerBotTownPolicyCheck'} else []
  result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test,*args],cwd=cwd,capture_output=True,text=True)
  (out/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
  assert result.returncode==0,(test,result.stderr)
  reports[test]=next(line for line in result.stdout.splitlines() if line.startswith('OK: '+str(count)+' '))
  suites[test]=dict(exitCode=result.returncode,count=count,result=reports[test],reportSha256=sha(out/(test+'.txt')))
 for test,count in QUEST_TESTS.items():
  args=[str(ROOT/'game-server')] if test=='KaligaEncounterCheck' or test.startswith('Native') else []
  result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.questEngine.'+test,*args],cwd=build/'checks',capture_output=True,text=True)
  (out/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
  assert result.returncode==0,(test,result.stderr)
  reports[test]=next(line for line in result.stdout.splitlines() if line.startswith('OK: '+str(count)+' '))
  suites[test]=dict(exitCode=result.returncode,count=count,result=reports[test],reportSha256=sha(out/(test+'.txt')))
 result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.ShutdownHookCheck'],cwd=build/'checks',capture_output=True,text=True)
 (out/'ShutdownHookCheck.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
 assert result.returncode==0,result.stderr
 reports['ShutdownHookCheck']=next(line for line in result.stdout.splitlines() if line.startswith('OK: 14 '))
 suites['ShutdownHookCheck']=dict(exitCode=result.returncode,count=14,result=reports['ShutdownHookCheck'],reportSha256=sha(out/'ShutdownHookCheck.txt'))
 database=subprocess.run([sys.executable,str(ROOT/'tools/check-database-shutdown.py'),'--build',str(build),'--output',str(out/'database-shutdown')],cwd=ROOT,capture_output=True,text=True)
 (out/'DatabaseShutdownCheck.txt').write_text(database.stdout+database.stderr,encoding='utf-8')
 assert database.returncode==0,database.stdout+database.stderr
 reports['DatabaseShutdownCheck']=next(line for line in database.stdout.splitlines() if line.startswith('OK: 17 '))
 suites['DatabaseShutdownCheck']=dict(exitCode=0,count=17,result=reports['DatabaseShutdownCheck'],reportSha256=sha(out/'DatabaseShutdownCheck.txt'))
 cancellation=subprocess.run([sys.executable,str(ROOT/'tools/check-player-skill-cancellation.py'),'--build',str(build),'--output',str(out/'player-skill-cancellation')],cwd=ROOT,capture_output=True,text=True)
 (out/'PlayerSkillCancellationCheck.txt').write_text(cancellation.stdout+cancellation.stderr,encoding='utf-8')
 assert cancellation.returncode==0,cancellation.stdout+cancellation.stderr
 reports['PlayerSkillCancellationCheck']=next(line for line in cancellation.stdout.splitlines() if line.startswith('OK: 41 '))
 suites['PlayerSkillCancellationCheck']=dict(exitCode=0,count=41,result=reports['PlayerSkillCancellationCheck'],reportSha256=sha(out/'PlayerSkillCancellationCheck.txt'))
 assert before==binding(build),'Regression inputs changed while checks ran'
 (out/'checks.json').write_text(json.dumps(reports,indent=2))
 (out/'evidence.json').write_text(json.dumps(dict(version=1,inputs=before,suites=suites,tooling=dict(exitCode=0,count=tool_count,reportSha256=sha(out/'tooling.txt')),compileSha256=sha(out/'compile.txt'),classes={str(p.relative_to(build/'checks')):sha(p) for p in sorted((build/'checks').rglob('*.class'))}),indent=2))
 validate(build,out)
 print('OK: all '+str(len(reports))+' artifact-bound source-builder regression suites pass; no world/DB/native casts')

if __name__=='__main__':main()
