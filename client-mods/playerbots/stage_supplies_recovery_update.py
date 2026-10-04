"""Native build supplies and selected/party revive-summon on the cumulative baseline."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotConsumables':{'candidates','needed','use'},
 shared.PREFIX+'services/playerbot/PlayerBotTravel':{'summon','summonAll'},
 shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'close'},
 shared.PREFIX+'services/PlayerBotHttpService':{'action'},
 'playercommands/Bot':{'<init>','execute'},
}
base.HELPERS={'PlayerBotSupplies','PlayerBotRecovery'}
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out)
 # The small visible browser labels belong to this update.
 path=out/'config/playerbots/media/bots.js';s=path.read_text();s=s.replace("name === 'all' ? 'Summon party' : 'Summon companion'", "name === 'all' ? 'Revive / summon selected or party' : 'Revive / summon companion'");s=s.replace("'Recovery supplies'", "'Recovery and build supplies'");path.write_text(s)
 f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Build-aware native consumables and Temporary supplies; automatic wipe recovery and selected/party revive-summon'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 f.write_text(json.dumps(d,indent=2))
