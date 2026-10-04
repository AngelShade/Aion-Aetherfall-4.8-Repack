"""Bounded full-party transfer update on the latest live cumulative override."""
import argparse
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},shared.PREFIX+'services/playerbot/PlayerBotService':{'relocate'},shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'close'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotTransfers'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();base.stage(a.classes.resolve(),a.output.resolve())
