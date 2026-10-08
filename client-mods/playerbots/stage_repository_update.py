"""PB-SCOPE-012C: native presets/archive markers and bundled current companion UI."""
import argparse,json,shutil,zipfile,subprocess
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotPresets$Store':{'load','write'},
 shared.PREFIX+'services/playerbot/PlayerBotRosterRemoval':{'removed','archive','remove'},
 shared.PREFIX+'services/PlayerBotHttpService':{'handle'},
 shared.PREFIX+'services/playerbot/PlayerBotTemporary':{'persistCreation'},
 shared.PREFIX+'services/playerbot/PlayerBotPersistence':{'save'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotRepository','PlayerBotMedia','PlayerBotCreationMetadata'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();shared.validate_output(out)
 server=shared.ROOT/'target-deploy/game-server';prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else a.classes/(name+'.class');dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 for helper in base.HELPERS:
  source=a.classes/(shared.PREFIX+'services/playerbot/'+helper+'.class');dest=prepared/source.relative_to(a.classes);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 base.stage(prepared,out);m=json.loads((out/'manifest.json').read_text());added=[];media={}
 with zipfile.ZipFile(out/'libs/playerbot-recruitment-fix.jar','a') as jar:
  for source in (a.classes/(shared.PREFIX+'dao')).glob('PlayerBotRepositoryDAO*.class'):
   rel=source.relative_to(a.classes).as_posix();assert rel not in jar.namelist();jar.write(source,rel);added.append(rel)
  for name in ['bots.html','bots.css','bots.js']:
   source=server/'config/playerbots/media'/name;rel='playerbots/media/'+name;assert rel not in jar.namelist();jar.write(source,rel);added.append(rel);media[name]=shared.sha(source)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 shutil.copy2(shared.ROOT/'game-server/sql/playerbot_repository.sql',out/'playerbot_repository.sql')
 cp=str(out/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/*')
 subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(out/'tools'),str(shared.ROOT/'client-mods/playerbots/tooling/PlayerBotRepositoryMigration.java')],check=True)
 m.update(feature='playerbot-native-repository',scope='PB-SCOPE-012C: native saved-party and removed-roster repository; bundle exact installed companion media',newClasses=sorted(m['newClasses']+[x for x in added if x.endswith('.class')]),bundledMediaSha256=media,repositorySchemaSha256=shared.sha(out/'playerbot_repository.sql'),migrationToolSha256=shared.sha(out/'tools/PlayerBotRepositoryMigration.class'))
 for e in m['files']:e['installed']=shared.sha(out/e['path'])
 assert [e['path'] for e in m['files'] if e['original']!=e['installed']]==['libs/playerbot-recruitment-fix.jar']
 (out/'manifest.json').write_text(json.dumps(m,indent=2))
