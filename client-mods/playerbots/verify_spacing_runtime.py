"""Compare selected currently loaded spacing methods against the reviewed disk override."""
import argparse,json
from pathlib import Path
from stage_companion_update import methods,method_name,ROOT,PREFIX
from stage_spacing_update import base
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--capture',type=Path,required=True);a=p.parse_args()
installed=ROOT/'target-deploy/game-server/libs/playerbot-recruitment-fix.jar';evidence=[]
scopes=dict(base.SCOPES);scopes[PREFIX+'services/playerbot/PlayerBotSpacing']={'validate','values','configure','snapshot','attack','nativeDistance','formation','observe','canRetreat','retreat','close'}
for name,wanted in scopes.items():
 if not wanted:continue
 loaded=methods(a.capture/'effective-loaded.jar',name);disk=methods(installed,name)
 selected={k:v for k,v in disk.items() if method_name(k) in wanted or any(method_name(k).startswith('lambda$'+w+'$') for w in wanted)}
 assert wanted<={method_name(k) for k in selected},name
 for key,value in selected.items():
  assert loaded.get(key)==value,(name,key)
  evidence.append(dict(className=name,method=key))
(a.capture/'method-comparison.json').write_text(json.dumps(evidence,indent=2))
print('OK:',len(evidence),'selected effective loaded methods/lambdas match the reviewed installed override')
