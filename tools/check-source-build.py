"""Run bounded world-free companion regression entrypoints against only normal builder outputs."""
import argparse,json,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TESTS={'PlayerBotClericCheck':87,'PlayerBotSpiritmasterCheck':59,'PlayerBotSorcererCheck':54,'PlayerBotItemUseCheck':147,
 'PlayerBotRecallCheck':165,'PlayerBotTravelFormationCheck':679,'PlayerBotCastExecutionCheck':72,
 'PlayerBotTradeCheck':56,'PlayerBotEngineCheck':99,'PlayerBotStrategyCompositionCheck':49,
 'PlayerBotOffenseIntegrationCheck':44,'PlayerBotFollowSpeedCheck':6,'PlayerBotNavigationTrailCheck':17,
 'PlayerBotGroundNavigationCheck':57,'PlayerBotFormationCheck':314}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 build=a.build.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
 libs=[f for f in sorted((ROOT/'target-deploy/game-server/libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar','commons-4.8-SNAPSHOT.jar','playerbot-recruitment-fix.jar'}]
 cp=';'.join(map(str,[build/'checks',build/'game-server/game-server-4.8-SNAPSHOT.jar',build/'commons/commons-4.8-SNAPSHOT.jar',*libs]))
 reports={}
 for test,count in TESTS.items():
  cwd=ROOT if test in {'PlayerBotClericCheck','PlayerBotSpiritmasterCheck','PlayerBotSorcererCheck'} else build/'checks'
  result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test],cwd=cwd,capture_output=True,text=True)
  (out/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
  assert result.returncode==0,(test,result.stderr)
  reports[test]=next(line for line in result.stdout.splitlines() if line.startswith('OK: '+str(count)+' '))
 (out/'checks.json').write_text(json.dumps(reports,indent=2));print('OK: all '+str(len(reports))+' source-builder regression suites pass; no world/DB/native casts')

if __name__=='__main__':main()
