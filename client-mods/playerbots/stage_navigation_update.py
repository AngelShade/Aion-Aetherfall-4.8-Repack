"""PB-REPAIR-NAV-002: generic bot door/ground routing; preserve effective installed methods."""
import argparse,json,shutil,subprocess,zipfile
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'controllers/movement/PlayerBotMoveController':{'moveStep'},
 shared.PREFIX+'services/playerbot/PlayerBotNavigation':{'move','approach','follow'},
 shared.PREFIX+'services/playerbot/PlayerBotPathfinder':{'find'},
 shared.PREFIX+'world/geo/GeoService':{'findGroundMovementCollision'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotGroundNavigation','PlayerBotNavigationTrail'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();shared.validate_output(out)
 server=shared.ROOT/'target-deploy/game-server';classes=a.classes.resolve()
 prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else classes/(name+'.class')
  dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as installed:
  for source in (classes/(shared.PREFIX+'services/playerbot')).glob('*.class'):
   if source.stem.split('$')[0] not in base.HELPERS:continue
   rel=source.relative_to(classes).as_posix()
   if rel in installed.namelist():
    if installed.read(rel)!=source.read_bytes():
     # Maven and focused javac builds may differ only in debug attributes. Keep
     # installed bytes when methods and full declared schema are unchanged.
     name=rel.removesuffix('.class')
     def schema(cp):
      dump=subprocess.check_output(['javap','-p','-s','-constants','-cp',str(cp),name.replace('/','.')],text=True)
      return '\n'.join(line for line in dump.splitlines() if not line.startswith('Compiled from '))
     assert shared.methods(server/'libs/playerbot-recruitment-fix.jar',name)==shared.methods(classes,name) and schema(server/'libs/playerbot-recruitment-fix.jar')==schema(classes),'Existing helper changes need explicit runtime SCOPES: '+rel
    dest=prepared/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(installed.read(rel))
   else:
    dest=prepared/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 base.stage(prepared,out)
 manifest=json.loads((out/'manifest.json').read_text())
 manifest['feature']='playerbot-general-ground-navigation'
 manifest['scope']='PB-REPAIR-NAV-002: follow consumes reached breadcrumb prefixes behind dynamic doors; retain generic ground/door movement in any world/instance/combat state'
 manifest['unchangedGeometrySha256']=shared.sha(server/'data/geo/models.mesh')
 # Keep the installed command source too; this repair owns no command/UI changes.
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 for e in manifest['files']:e['installed']=shared.sha(out/e['path'])
 assert [e['path'] for e in manifest['files'] if e['original']!=e['installed']] in ([],['libs/playerbot-recruitment-fix.jar'])
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
