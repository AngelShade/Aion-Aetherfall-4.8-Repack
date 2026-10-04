"""PB-PORT-005A: bounded native Sorcerer single-target strategy integration."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','priority'},
 shared.PREFIX+'services/playerbot/PlayerBotOffense':{'routine'},
 shared.PREFIX+'services/playerbot/PlayerBotCombatBuffs':{'useful'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotSorcerer'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 server=shared.ROOT/'target-deploy/game-server';out=a.output.resolve();base.stage(a.classes.resolve(),out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='PB-PORT-005A Sorcerer native single-target chains, upkeep, fillers, mana and offensive boosts'
 for entry in d['files']:entry['installed']=shared.sha(out/entry['path'])
 path.write_text(json.dumps(d,indent=2))
