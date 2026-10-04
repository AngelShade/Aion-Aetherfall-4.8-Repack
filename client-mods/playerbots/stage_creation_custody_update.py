"""Bounded native item allocation custody repair on the current installed override."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/item/ItemFactory':{'newItem'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotItemIds'}
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 classes=a.classes.resolve();out=a.output.resolve();server=shared.ROOT/'target-deploy/game-server'
 command=classes/'playercommands/Bot.class';command.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/'cache/classes/playercommands/Bot.class',command)
 base.stage(classes,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Prevent native item allocation from reusing persisted inventory or character IDs'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 f.write_text(json.dumps(d,indent=2))
