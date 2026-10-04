"""Verify tank methods and unchanged movement/attack/hazard authorities actually loaded."""
import argparse,json
from pathlib import Path
from stage_companion_update import methods,method_name,ROOT,PREFIX
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--capture',type=Path,required=True);a=p.parse_args()
installed=str(ROOT/'target-deploy/game-server/libs/playerbot-recruitment-fix.jar')+';'+str(ROOT/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar')
scopes={'PlayerBotCoordination':{'tankFacing','spread','away'},'PlayerBotSession':{'tick'},'PlayerBotSession$ReachAction':{'isUseful','isPossible','execute'},'PlayerBotSession$CastAction':{'isUseful','isPossible','prerequisites','execute'},'PlayerBotNavigation':{'approach','move','escapeHazards','stop'},'PlayerBotHazards':{'visible','risk','escape','safePath'}}
evidence=[]
for short,wanted in scopes.items():
 name=PREFIX+'services/playerbot/'+short;loaded=methods(a.capture/'effective-loaded.jar',name);disk=methods(installed,name)
 selected={k:v for k,v in disk.items() if method_name(k) in wanted or any(method_name(k).startswith('lambda$'+w+'$') for w in wanted)}
 assert wanted<={method_name(k) for k in selected},short
 for key,value in selected.items():
  assert loaded.get(key)==value,(name,key)
  evidence.append(dict(className=name,method=key))
(a.capture/'method-comparison.json').write_text(json.dumps(evidence,indent=2))
print('OK:',len(evidence),'selected effective tank/movement/attack/hazard methods and lambdas match installed bytecode')
