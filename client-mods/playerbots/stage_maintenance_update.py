"""Bounded level-up maintenance, native stat weights and reward-pack separation."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotService':{'recruit'},shared.PREFIX+'controllers/PlayerController':{'onLevelChange'},shared.PREFIX+'services/playerbot/PlayerBotEquipment':{'score','weight'},shared.PREFIX+'services/playerbot/PlayerBotTemporary':{'tick','choice'},'playercommands/Bot':set()}
base.HELPERS=set()
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Temporary Bot native level-up stats and class promotion; account reward packs excluded; stable class/role stat weights';f.write_text(json.dumps(d,indent=2))
