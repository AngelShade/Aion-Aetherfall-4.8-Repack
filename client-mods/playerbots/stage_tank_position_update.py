"""Bounded tank hold repair; preserve all latest deployed methods and media."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotCoordination':{'tankFacing','spread'},'playercommands/Bot':set()}
base.HELPERS=set()
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();base.stage(a.classes.resolve(),out)
 shutil.copy2(shared.ROOT/'target-deploy/game-server/data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='PB-REPAIR-TANK-001: stationary tank hold instead of recursive boss-facing pulls and active-tank targeted-cast spread'
 for entry in d['files']:entry['installed']=shared.sha(out/entry['path'])
 path.write_text(json.dumps(d,indent=2))
