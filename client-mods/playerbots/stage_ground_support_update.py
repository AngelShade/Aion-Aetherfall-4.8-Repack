"""PB-REPAIR-NAV-003: physical see-through floors in shared bot ground probes."""
import argparse,json,shutil,zipfile
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotGroundNavigation':{'walk'},'playercommands/Bot':set()}
base.HELPERS={'PlayerBotGroundSupport'}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();shared.validate_output(out);server=shared.ROOT/'target-deploy/game-server'
 prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else a.classes/(name+'.class')
  dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 rel=shared.PREFIX+'services/playerbot/PlayerBotGroundSupport.class';dest=prepared/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(a.classes/rel,dest)
 base.stage(prepared,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 m=json.loads((out/'manifest.json').read_text());m.update(feature='playerbot-general-ground-navigation',scope='PB-REPAIR-NAV-003: native physical see-through surfaces support walking in every world/instance; preserve slope/cliff/wall/door checks',unchangedGeometrySha256=shared.sha(server/'data/geo/models.mesh'))
 # Carry the freshly installed native encounter files into this cumulative receipt.
 prior=json.loads(Path(m['previousReceipt']).read_text())
 known={e['path'] for e in m['files']}
 for e in prior['files']:
  if e['path'] in known:continue
  rel=e['path'];assert shared.sha(server/rel)==e['installed'];dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/rel,dest)
  m['files'].append(dict(path=rel,original=e['installed'],installed=e['installed']))
 for e in m['files']:e['installed']=shared.sha(out/e['path'])
 assert [e['path'] for e in m['files'] if e['original']!=e['installed']]==['libs/playerbot-recruitment-fix.jar']
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(out/'libs/playerbot-recruitment-fix.jar') as new:
  changed={e['path'] for e in m['incrementalChangedMethods']};retained=[n for n in old.namelist() if n not in changed]
  assert all(old.read(n)==new.read(n) for n in retained),'Unrelated cumulative entries changed'
  m['retainedJarEntries']=len(retained)
 (out/'manifest.json').write_text(json.dumps(m,indent=2));print('OK: shared floor correction staged; unrelated JAR entries retained:',len(retained))
if __name__=='__main__':main()
