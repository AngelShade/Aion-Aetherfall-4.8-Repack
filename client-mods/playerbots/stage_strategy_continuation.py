"""Bounded offensive decisions, native target strategies and owner-centred quest routes."""
import argparse,json,shutil,zipfile
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'priority','tick','markClosing'},
 shared.PREFIX+'services/playerbot/PlayerBotSession$CastAction':{'isUseful'},
 shared.PREFIX+'services/playerbot/PlayerBotTargetValues':{'choose'},
 shared.PREFIX+'services/playerbot/PlayerBotHazards':{'visible','lambda$visible$0'},
 shared.PREFIX+'services/playerbot/PlayerBotOffense':{'finisher','useful'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestRoutes':{'leash'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotOffense','PlayerBotTargetStrategies','PlayerBotQuestRoutes'}
# Once installed, use bounded helper method transplants too. Retain original
# cached switch classes and every unselected member, including debug attributes.
with zipfile.ZipFile(shared.ROOT/'target-deploy/game-server/libs/playerbot-recruitment-fix.jar') as installed:
 for helper in list(base.HELPERS):
  if shared.PREFIX+'services/playerbot/'+helper+'.class' in installed.namelist():
   base.HELPERS.remove(helper)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 classes=a.classes.resolve();out=a.output.resolve();server=shared.ROOT/'target-deploy/game-server'
 command=classes/'playercommands/Bot.class';command.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/'cache/classes/playercommands/Bot.class',command)
 base.stage(classes,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Native rune/DoT combat decisions, caster/combo target values, owner-centred quest route planning and Kromede trap anticipation'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 f.write_text(json.dumps(d,indent=2))
