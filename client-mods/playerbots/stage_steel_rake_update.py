"""PB-SCOPE-007A: native Steel Rake encounters and companion tactics on current installation."""
import argparse,json,shutil,zipfile
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','chooseTarget','markClosing'},
 shared.PREFIX+'services/playerbot/PlayerBotHazards':{'lambda$visible$0'},
 shared.PREFIX+'services/playerbot/PlayerBotEncounters':{'allowsAttack'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotSteelRake'}
SCRIPTS=('EngineerLahulahuAI','ChiefGunnerKoakoaAI','BrassEyeGroggetAI','GoldenEyeMantutuAI','FeedingMantutuAI','TamerAnikikiAI','SteelRakeTasks')

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for flag in ('classes','session-classes','scripts','output'):p.add_argument('--'+flag,type=Path,required=True)
 a=p.parse_args();out=a.output.resolve();shared.validate_output(out)
 server=shared.ROOT/'target-deploy/game-server';prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else (a.session_classes if name.endswith('PlayerBotSession') else a.classes)/(name+'.class')
  dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 for source in (a.classes/(shared.PREFIX+'services/playerbot')).glob('PlayerBotSteelRake*.class'):
  dest=prepared/source.relative_to(a.classes);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 base.stage(prepared,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 m=json.loads((out/'manifest.json').read_text());m.update(feature='playerbot-steel-rake',scope='PB-SCOPE-007A: native Steel Rake encounter lifecycle and visible companion tactics; no movement workaround',unchangedGeometrySha256=shared.sha(server/'data/geo/models.mesh'))
 for name in SCRIPTS:
  for rel,source in [('data/handlers/ai/instance/rakes/'+name+'.java',shared.ROOT/'game-server/data/handlers/ai/instance/rakes'/(name+'.java')),('cache/classes/ai/instance/rakes/'+name+'.class',a.scripts/'ai/instance/rakes'/(name+'.class'))]:
   dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
   m['files'].append(dict(path=rel,original=shared.sha(server/rel) if (server/rel).exists() else None,installed=shared.sha(dest)))
 for e in m['files']:e['installed']=shared.sha(out/e['path'])
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(out/'libs/playerbot-recruitment-fix.jar') as new:
  changed={e['path'] for e in m['incrementalChangedMethods']};retained=[n for n in old.namelist() if n not in changed]
  assert all(old.read(n)==new.read(n) for n in retained),'Unrelated cumulative entry changed'
  m['retainedJarEntries']=len(retained)
 (out/'manifest.json').write_text(json.dumps(m,indent=2))
 print('OK: reviewed core and native handler package; retained JAR entries:',len(retained))
if __name__=='__main__':main()
