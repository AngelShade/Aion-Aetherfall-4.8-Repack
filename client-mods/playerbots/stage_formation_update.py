"""Preserve the latest installation while transplanting only formation methods."""
import argparse,json,shutil
from pathlib import Path
import stage_companion_update as shared
SCOPES={
 shared.PREFIX+'controllers/movement/PlayerBotMoveController':{'startMovingToDestination','moveStep','abortMove'},
 shared.PREFIX+'services/playerbot/PlayerBotNavigation':{'follow','move'},
 shared.PREFIX+'services/playerbot/PlayerBotFlight':{'move'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'close'},
 'playercommands/Bot':set(),
}
HELPERS={'PlayerBotFormation'}
def stage(classes,out):
 shared.validate_output(out)
 scratch=out.parent/(out.name+'-inputs');scratch.mkdir(exist_ok=False)
 for name in SCOPES:
  dest=scratch/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(classes/(name+'.class'),dest)
 for path in (classes/(shared.PREFIX+'services/playerbot')).glob('*.class'):
  if path.stem.split('$')[0] in HELPERS:
   dest=scratch/path.relative_to(classes);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
 original=shared.methods
 server=shared.ROOT/'target-deploy/game-server';baseline=str(server/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/game-server-4.8-SNAPSHOT.jar')
 def reviewed(path,name):
  actual=original(path,name)
  if Path(path)==scratch:
   before=original(server/'cache/classes' if name=='playercommands/Bot' else baseline,name)
   for key in before:
    method=shared.method_name(key)
    if method not in SCOPES[name] and not (name.endswith('PlayerBotSession') and method.startswith('lambda$tick$')):actual[key]=before[key]
  return actual
 shared.methods=reviewed;shared.SCOPES=SCOPES;shared.HELPERS=HELPERS;shared.stage(scratch,out)
 for name in ['bots.html','bots.css','bots.js']:shutil.copy2(server/'config/playerbots/media'/name,out/'config/playerbots/media'/name)
 manifest=json.loads((out/'manifest.json').read_text());manifest['scope']='continuous movement clock and stable tactical follow formation'
 for entry in manifest['files']:entry['installed']=shared.sha(out/entry['path'])
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();stage(a.classes.resolve(),a.output.resolve())
