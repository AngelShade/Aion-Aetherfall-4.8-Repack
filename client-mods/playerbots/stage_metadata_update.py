"""PB-SCOPE-012A: Aion DAO-backed care/gear metadata and shared native checkpoints."""
import argparse,json,shutil,zipfile,subprocess
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotQuestSync$State':{'<init>','save'},
 shared.PREFIX+'services/playerbot/PlayerBotGearPolicy$State':{'<init>','save'},
 shared.PREFIX+'services/playerbot/PlayerBotPersistence':{'save'},
 shared.PREFIX+'services/playerbot/PlayerBotService':{'dismiss'},
 shared.PREFIX+'services/playerbot/PlayerBotTradeStore':{'commit'},
 shared.PREFIX+'services/playerbot/PlayerBotSupplyCatalog':{'record'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotMetadata'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 server=shared.ROOT/'target-deploy/game-server';out=a.output.resolve();classes=a.classes.resolve();base.stage(classes,out)
 # New DAO is outside the playerbot helper folder. Append only its exact compiled entries.
 path=out/'libs/playerbot-recruitment-fix.jar';new=[]
 with zipfile.ZipFile(path,'a') as jar:
  for source in (classes/(shared.PREFIX+'dao')).glob('PlayerBotMetadataDAO*.class'):
   rel=source.relative_to(classes).as_posix();assert rel not in jar.namelist();jar.write(source,rel);new.append(rel)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 d=json.loads((out/'manifest.json').read_text());d['scope']='PB-SCOPE-012A: native database metadata cache and atomic inventory/progress checkpoints'
 d['newClasses']=sorted(d['newClasses']+new)
 shutil.copy2(shared.ROOT/'game-server/sql/playerbot_metadata.sql',out/'playerbot_metadata.sql')
 subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',str(path)+';'+str(server/'libs/*'),'-d',str(out/'tools'),str(shared.ROOT/'client-mods/playerbots/tooling/PlayerBotMetadataMigration.java')],check=True)
 d['metadataSchemaSha256']=shared.sha(out/'playerbot_metadata.sql');d['migrationToolSha256']=shared.sha(out/'tools/PlayerBotMetadataMigration.class')
 for e in d['files']:e['installed']=shared.sha(out/e['path'])
 (out/'manifest.json').write_text(json.dumps(d,indent=2))
