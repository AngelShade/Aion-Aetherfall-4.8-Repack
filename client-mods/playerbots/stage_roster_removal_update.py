"""Bounded roster archive and confirmation update on the latest actual override."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotRoster':{'list'},
 shared.PREFIX+'services/playerbot/PlayerBotPresets':{'remove'},
 shared.PREFIX+'services/playerbot/PlayerBotPresets$Store':{'load'},
 shared.PREFIX+'services/PlayerBotHttpService':{'action'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotRosterRemoval'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();classes=a.classes.resolve();out=a.output.resolve();server=shared.ROOT/'target-deploy/game-server'
 command=classes/'playercommands/Bot.class';command.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/'cache/classes/playercommands/Bot.class',command)
 base.stage(classes,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 shutil.copy2(shared.ROOT/'game-server/config/playerbots/media/bots.js',out/'config/playerbots/media/bots.js')
 manifest=out/'manifest.json';d=json.loads(manifest.read_text());d['scope']='Account-owned Temporary Bot roster archive with native ID custody and inline removal confirmation'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 manifest.write_text(json.dumps(d,indent=2))
