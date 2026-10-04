"""Bounded class armor and build-aware weapon/stat selection repair."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotGearPolicy':{'eligible','allowedWeapon'},shared.PREFIX+'services/playerbot/PlayerBotGenerated':{'eligible','equipStarterSet','addEquipped'},shared.PREFIX+'services/playerbot/PlayerBotEquipment':{'weight'},shared.PREFIX+'services/playerbot/PlayerBotGenerationOptions':{'initialize'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotBuildRules'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='class mastery armor filtering, role-specific weapons and threat/healing/caster equipment scoring';f.write_text(json.dumps(d,indent=2))
