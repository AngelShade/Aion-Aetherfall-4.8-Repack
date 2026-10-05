"""PB-SCOPE-003A: native owner gift exchange and explicitly donated upgrade selection."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/ExchangeService':{'validateParticipants','addItem','addKinah','lockExchange','confirmExchange','cancelExchange'},
 shared.PREFIX+'network/aion/clientpackets/CM_EXCHANGE_REQUEST':{'runImpl'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','markClosing','equip'},
 shared.PREFIX+'services/playerbot/PlayerBotTrade':{'equip','tick'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotTrade','PlayerBotTradeStore'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 server=shared.ROOT/'target-deploy/game-server';out=a.output.resolve();base.stage(a.classes.resolve(),out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='PB-SCOPE-003A owner gifts through native trade window, guarded durable custody and donated equipment only'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 path.write_text(json.dumps(d,indent=2))
