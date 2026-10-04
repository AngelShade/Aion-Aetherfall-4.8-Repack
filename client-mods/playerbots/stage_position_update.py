"""Preserve the cumulative installation while repairing tank formation and ranged movement."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotSession$CastAction':{'isPossible','prerequisites','execute'},
 shared.PREFIX+'services/playerbot/PlayerBotSession$ReachAction':{'isUseful'},
 shared.PREFIX+'services/playerbot/PlayerBotNavigation':{'approach'},
 shared.PREFIX+'services/playerbot/PlayerBotFormationLayout':{'point','destination'},
 shared.PREFIX+'services/playerbot/PlayerBotFormation':{'destination'},
 shared.PREFIX+'services/playerbot/PlayerBotCombatPosition':{'desired'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotCombatPosition'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 server=shared.ROOT/'target-deploy/game-server';out=a.output.resolve();base.stage(a.classes.resolve(),out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='Role-aware front tank formation and native spell-distance movement'
 for entry in d['files']:entry['installed']=shared.sha(out/entry['path'])
 path.write_text(json.dumps(d,indent=2))
