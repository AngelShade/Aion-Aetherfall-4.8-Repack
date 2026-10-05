"""PB-REPAIR-SETTINGS-001: bounded Windows care-file replacement retries."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotQuestSync$State':{'save'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotSettingsFiles'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();out.parent.mkdir(parents=True,exist_ok=True);base.stage(a.classes.resolve(),out)
 shutil.copy2(shared.ROOT/'target-deploy/game-server/data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='PB-REPAIR-SETTINGS-001: short Windows sharing-violation retries for care settings; preserve atomic replacement and persistent error visibility'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 path.write_text(json.dumps(d,indent=2))
