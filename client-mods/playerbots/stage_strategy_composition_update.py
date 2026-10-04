"""PB-PORT-004: bounded engine/state/continuation update over the latest installation."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotEngine':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','markClosing','applyPreferences','order','attackSelectedTarget'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotStrategyComposition','PlayerBotArbitration'}

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();server=shared.ROOT/'target-deploy/game-server'
 base.stage(a.classes.resolve(),out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';manifest=json.loads(path.read_text(encoding='utf-8'))
 manifest['scope']='State strategy composition and fresh action continuers: PB-PORT-004'
 for entry in manifest['files']:entry['installed']=shared.sha(out/entry['path'])
 path.write_text(json.dumps(manifest,indent=2),encoding='utf-8')
