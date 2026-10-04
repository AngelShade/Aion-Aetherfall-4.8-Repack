"""Temporary Bot factory, maintenance and owned-alt isolation; bounded native methods."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
import zipfile
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotService':{'generate','recruit'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','snapshot','setAutoGear'},
 shared.PREFIX+'services/playerbot/PlayerBotGearPolicy':{'eligible','configure','generatedTemplate'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestSync':{'setCare'},
 shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'close'},
 shared.PREFIX+'services/playerbot/PlayerBotPersistence':{'saveProgress'},
 shared.PREFIX+'services/StigmaService':{'notifyEquipAction','onPlayerLogin','getPossibleStigmaCount','getPossibleAdvancedStigmaCount'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotTemporary'}
with zipfile.ZipFile(shared.ROOT/'target-deploy/game-server/libs/playerbot-recruitment-fix.jar') as installed:
 if shared.PREFIX+'services/playerbot/PlayerBotTemporary.class' in installed.namelist():
  base.SCOPES[shared.PREFIX+'services/playerbot/PlayerBotTemporary']={'gear','stigmaScore'}
  base.HELPERS=set()
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();base.stage(a.classes.resolve(),out)
 for name in ['bots.html','bots.css','bots.js']:shutil.copy2(shared.ROOT/'game-server/config/playerbots/media'/name,out/'config/playerbots/media'/name)
 f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Temporary Bot native gear/skill/Stigma builds, owner-level maintenance, ten-level tiers and owned-alt isolation'
 for entry in d['files']:entry['installed']=shared.sha(out/entry['path'])
 f.write_text(json.dumps(d,indent=2))
