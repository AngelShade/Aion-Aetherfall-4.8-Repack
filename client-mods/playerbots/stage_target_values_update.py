"""Bounded role-specific target values on the latest cumulative installation."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotSession':{'chooseTarget'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotTargetValues'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Native role target values: range/health, loose enemy recovery, weak hate and other-tank protection';f.write_text(json.dumps(d,indent=2))
