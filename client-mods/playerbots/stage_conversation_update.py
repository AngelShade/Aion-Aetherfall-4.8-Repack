"""Bounded native intermediate quest conversations."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'controllers/NpcController':{'onDialogSelect'},shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'close'},shared.PREFIX+'services/playerbot/PlayerBotQuestMetadata':{'ready','rewardNpcIds'},shared.PREFIX+'services/playerbot/PlayerBotQuestJournal':{'describe'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotQuestConversations'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='native intermediate quest conversations, per-objective contacts and successful owner conversation witnesses';f.write_text(json.dumps(d,indent=2))
