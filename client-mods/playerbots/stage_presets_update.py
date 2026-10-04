"""Install saved-bot checkpoints and mixed party presets on the cumulative baseline."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/PlayerBotHttpService':{'snapshot','action'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotPresets'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out)
 for name in ['bots.html','bots.css','bots.js']:shutil.copy2(shared.ROOT/'game-server/config/playerbots/media'/name,out/'config/playerbots/media'/name)
 f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Account-owned saved Temporary Bot checkpoints and mixed alt/Temporary party presets; native recruitment and persistence'
 for entry in d['files']:entry['installed']=shared.sha(out/entry['path'])
 f.write_text(json.dumps(d,indent=2))
