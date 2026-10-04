"""Bounded tank enmity update, retaining existing combat and party methods."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotSession':{'chooseTarget','priority','lambda$chooseTarget$0','lambda$chooseTarget$1','lambda$chooseTarget$2'},shared.PREFIX+'services/playerbot/PlayerBotThreat':{'hold'},shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'close'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotTank'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='native tank threat ability priorities, loose-party-target recovery and bounded DPS opening pause';f.write_text(json.dumps(d,indent=2))

