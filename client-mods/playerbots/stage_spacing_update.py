"""Bounded owner-controlled ranged spacing and finite retreat continuation."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','snapshot'},
 shared.PREFIX+'services/playerbot/PlayerBotFormationLayout':{'destination'},
 shared.PREFIX+'services/playerbot/PlayerBotFormation':{'close'},
 shared.PREFIX+'services/playerbot/PlayerBotCombatPosition':{'desired','tooClose','allowSpellApproach'},
 shared.PREFIX+'services/PlayerBotHttpService':{'action'},
 shared.PREFIX+'services/playerbot/PlayerBotSpacing':{'formation'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotSpacing'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 server=shared.ROOT/'target-deploy/game-server';out=a.output.resolve();base.stage(a.classes.resolve(),out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 for ext in ['js','css']:
  rel='config/playerbots/media/bots.'+ext;shutil.copy2(shared.ROOT/'game-server'/rel,out/rel)
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='Owner-controlled ranged spacing and one bounded retreat per engagement'
 for entry in d['files']:entry['installed']=shared.sha(out/entry['path'])
 path.write_text(json.dumps(d,indent=2))
