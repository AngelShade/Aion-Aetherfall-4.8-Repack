"""Install bounded defensive class decisions against the actual cumulative override."""
import argparse,json
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSkills':{'classify'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'recipient','priority'},
 shared.PREFIX+'services/playerbot/PlayerBotSession$CastAction':{'isUseful','execute'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotDefense'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out);f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Pinned proactive defenses and native pursuer root/snare; preserve panic, party, alt and pull safety';f.write_text(json.dumps(d,indent=2))
