"""PB-SCOPE-012B: complete the listed preference namespaces using native metadata checkpoints."""
import argparse,json,shutil,subprocess
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotMetadata':{'validate'},
 shared.PREFIX+'services/playerbot/PlayerBotPreferences':{'load','save'},
 shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior$State':{'<init>','save'},
 shared.PREFIX+'services/playerbot/PlayerBotSpacing':{'lambda$values$0','configure'},
 shared.PREFIX+'services/playerbot/PlayerBotFormationLayout':{'lambda$selected$0','configure'},
 shared.PREFIX+'services/playerbot/PlayerBotPartyCompletion':{'load','remember'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotMetadataConfiguration'}

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();shared.validate_output(out);server=shared.ROOT/'target-deploy/game-server'
 prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else a.classes/(name+'.class')
  dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 helper=a.classes/(shared.PREFIX+'services/playerbot/PlayerBotMetadataConfiguration.class')
 dest=prepared/helper.relative_to(a.classes);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(helper,dest)
 base.stage(prepared,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 shutil.copy2(shared.ROOT/'game-server/sql/playerbot_metadata.sql',out/'playerbot_metadata.sql')
 cp=str(out/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/*')
 subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(out/'tools'),str(shared.ROOT/'client-mods/playerbots/tooling/PlayerBotMetadataMigration.java')],check=True)
 m=json.loads((out/'manifest.json').read_text());m.update(feature='playerbot-metadata-preferences',scope='PB-SCOPE-012A: PB-SCOPE-012B continuation; native preferences/behavior/spacing/formation/reward-witness persistence; preserve care/gear and navigation',metadataSchemaSha256=shared.sha(out/'playerbot_metadata.sql'),migrationToolSha256=shared.sha(out/'tools/PlayerBotMetadataMigration.class'))
 for e in m['files']:e['installed']=shared.sha(out/e['path'])
 assert [e['path'] for e in m['files'] if e['original']!=e['installed']]==['libs/playerbot-recruitment-fix.jar']
 (out/'manifest.json').write_text(json.dumps(m,indent=2))
