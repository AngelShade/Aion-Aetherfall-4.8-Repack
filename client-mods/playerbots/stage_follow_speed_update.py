"""Synchronize native client interpolation with authoritative follow movement."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'controllers/movement/PlayerBotMoveController':{'startMovingToDestination','moveStep','abortMove'},shared.PREFIX+'model/stats/container/CreatureGameStats':{'getMovementSpeedFloat'},shared.PREFIX+'services/playerbot/PlayerBotFormation':{'speedMultiplier'},shared.PREFIX+'services/playerbot/PlayerBotNavigation':{'move'},shared.PREFIX+'services/playerbot/PlayerBotFlight':{'move'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotFollowSpeed'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='native server/client follow-speed agreement and continuous destination segments; human/combat speed preserved';f.write_text(json.dumps(d,indent=2))
