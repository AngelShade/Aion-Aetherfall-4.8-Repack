"""Install only enemy utility decisions against the current cumulative override."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSkills':{'classify'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'recipient','priority','petOrderPriority'},
 shared.PREFIX+'services/playerbot/PlayerBotSession$CastAction':{'isUseful'},
 shared.PREFIX+'services/playerbot/PlayerBotEnemyUtility':{'interruptPriority'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotEnemyUtility'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Pinned class enemy utility: legal native offensive purges and reachable party-engaged caster interrupts';f.write_text(json.dumps(d,indent=2))
