"""PB-CUSTOM-APPEARANCE-001: keep companion outfit appearance separate from combat gear."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'snapshot','tick','markClosing'},
 shared.PREFIX+'services/playerbot/PlayerBotGearPolicy':{'eligible'},
 shared.PREFIX+'services/PlayerBotHttpService':{'action'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotAppearance'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 server=shared.ROOT/'target-deploy/game-server';out=a.output.resolve();base.stage(a.classes.resolve(),out)
 # Overlay only the new controls on the latest deployed media; unrelated UI survives.
 installed=(server/'config/playerbots/media/bots.js').read_text()
 source=(shared.ROOT/'game-server/config/playerbots/media/bots.js').read_text()
 start=source.index('    if (item.equipped && item.appearanceSet)');end=source.index('    if (!item.equipped && item.slots.length)',start)
 anchor='    if (!item.equipped && item.slots.length)'
 assert installed.count(anchor)==1 and 'item.appearanceTargets' not in installed
 installed=installed.replace(anchor,source[start:end]+anchor)
 hint='Items belong to this companion. Choose an available slot to equip an item.'
 installed=installed.replace(hint,'Equip combat armor for stats. Keep outfits in the cube and choose Use as transmog to apply their look. Equip is still available for normal equipment.')
 (out/'config/playerbots/media/bots.js').write_text(installed,encoding='utf-8')
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';d=json.loads(path.read_text());d['scope']='PB-CUSTOM-APPEARANCE-001: owned companion transmog with combat stats, persistent slot choices, native appearance broadcast and costume auto-gear exclusion'
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 path.write_text(json.dumps(d,indent=2))
