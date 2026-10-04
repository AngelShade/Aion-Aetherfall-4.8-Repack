"""Bounded companion inventory custody guard; preserve every other deployed method."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'dao/InventoryDAO':{'storeCompanionInventory'},'playercommands/Bot':set()}
base.HELPERS=set()
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Companion transaction checks native item ownership/template/storage before any delete, insert or update';f.write_text(json.dumps(d,indent=2))
