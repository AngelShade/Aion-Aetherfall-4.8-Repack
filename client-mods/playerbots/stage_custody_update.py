"""PB-REPAIR-INV-001: transplant only the pending-write custody transaction method."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'dao/InventoryDAO':{'storeCompanionInventory'},'playercommands/Bot':set()}
base.HELPERS=set()
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 server=shared.ROOT/'target-deploy/game-server';out=a.output.resolve();base.stage(a.classes.resolve(),out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='PB-REPAIR-INV-001: ignore committed deletion records; retain all pending-write custody guards'
 for entry in d['files']:entry['installed']=shared.sha(out/entry['path'])
 assert len(d['incrementalChangedMethods'])==1 and len(d['incrementalChangedMethods'][0]['methods'])==1
 path.write_text(json.dumps(d,indent=2))
