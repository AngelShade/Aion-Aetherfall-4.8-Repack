"""PB-REPAIR-SETTINGS-002: extend installed Windows sharing-denial recovery to gear provenance."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotGearPolicy$State':{'save'},'playercommands/Bot':set()}
base.HELPERS=set()
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();out.parent.mkdir(parents=True,exist_ok=True);base.stage(a.classes.resolve(),out)
 shutil.copy2(shared.ROOT/'target-deploy/game-server/data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='PB-REPAIR-SETTINGS-002: gear settings use installed bounded sharing-denial retries; atomic persistence and permanent errors retained'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 path.write_text(json.dumps(d,indent=2))
