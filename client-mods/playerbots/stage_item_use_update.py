"""PB-REPAIR-ENGINE-002: native active item-task and committed cast correction."""
import argparse,json,shutil
from pathlib import Path
import stage_companion_update as shared
import stage_formation_update as base

SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotCare':{'available','observe'},
 shared.PREFIX+'services/playerbot/PlayerBotGearPolicy':{'available'},
 shared.PREFIX+'services/playerbot/PlayerBotSupplies':{'use'},
 shared.PREFIX+'services/playerbot/PlayerBotSummonPolicy':{'busy'},
 shared.PREFIX+'services/playerbot/PlayerBotTrade':{'equip','tick'},
 'playercommands/Bot':set(),
}

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();shared.validate_output(out);server=shared.ROOT/'target-deploy/game-server'
 base.SCOPES=SCOPES;base.HELPERS={'PlayerBotItemUse'}
 prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in SCOPES:
  entry=name+'.class';source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else a.classes/entry
  dest=prepared/entry;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 entry=shared.PREFIX+'services/playerbot/PlayerBotItemUse.class'
 dest=prepared/entry;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(a.classes/entry,dest)
 base.stage(prepared,out)
 m=json.loads((out/'manifest.json').read_text());previous=Path(m['previousReceipt'])
 if not previous.is_absolute():previous=server/previous
 prior=json.loads(previous.read_text());known={e['path'] for e in m['files']}
 for e in prior['files']:
  if e['path'] in known:continue
  assert shared.sha(server/e['path'])==e['installed']
  dest=out/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest)
  m['files'].append(dict(path=e['path'],original=e['installed'],installed=e['installed']))
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 for e in m['files']:e['installed']=shared.sha(out/e['path'])
 assert [e['path'] for e in m['files'] if e['original']!=e['installed']]==['libs/playerbot-recruitment-fix.jar']
 m.update(feature='playerbot-item-task-cast-completion',scope='PB-REPAIR-ENGINE-002',offlineOnly=True,
          unchangedGeometrySha256=shared.sha(server/'data/geo/models.mesh'))
 shutil.copy2(shared.ROOT/'docs/INSTALLED_MODS.json',out/'inventory-before.json')
 (out/'manifest.json').write_text(json.dumps(m,indent=2));print('OK: source-compiled item task correction staged with cumulative preservation')

if __name__=='__main__':main()
