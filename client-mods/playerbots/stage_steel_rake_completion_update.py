"""PB-SCOPE-007A-R1: implement the original captain/gunner requirements without replacing other mods."""
import argparse,json,shutil,zipfile,re
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSteelRake':{'adds','attackable'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'validEnemy'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotSteelRakeCaptainTactics'}
SCRIPTS=('BrassEyeGroggetAI','ChiefGunnerKoakoaAI','SteelRakeTasks','SteelRakeCaptainPlan','SteelRakeStigmaAmplifierAI')
DATA=('data/static_data/npcs/npc_templates.xml','data/static_data/npc_skills/npc_skills.xml','data/static_data/spawns/Instances/300100000_Steel Rake.xml')

def patch_data(rel,original):
 s=original.decode('utf-8')
 if rel==DATA[0]:
  a=s.index('<npc_template npc_id="281180"');b=s.index('</npc_template>',a);block=s[a:b]
  assert block.count('ai="general"')==1;block=block.replace('ai="general"','ai="steel_rake_stigma_amplifier"')
 elif rel==DATA[1]:
  a=s.index('<npc_skills npc_ids="215081">');b=s.index('</npc_skills>',a);block=s[a:b]
  for id in (18190,18192,18195,18203):
   old=f'id="{id}" lv="37" prob="25"';assert block.count(old)==1;block=block.replace(old,old.replace('prob="25"','prob="0"'))
 else:
  a=s.index('<spawn npc_id="281180">');b=s.index('</spawn>',a);block=s[a:b]
  old='x="403.184" y="510.165" z="1076.36" aerial_spawn="true"';assert block.count(old)==1;block=block.replace(old,'x="403.184" y="510.165" z="1071.736"')
 return (s[:a]+block+s[b:]).encode('utf-8')

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for flag in ('classes','scripts','output'):p.add_argument('--'+flag,type=Path,required=True)
 a=p.parse_args();out=a.output.resolve();shared.validate_output(out);server=shared.ROOT/'target-deploy/game-server'
 prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else a.classes/(name+'.class')
  dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 rel=shared.PREFIX+'services/playerbot/PlayerBotSteelRakeCaptainTactics.class';dest=prepared/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(a.classes/rel,dest)
 base.stage(prepared,out);shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 m=json.loads((out/'manifest.json').read_text());m.update(feature='playerbot-steel-rake',scope='PB-SCOPE-007A-R1: original tower, floor-correct summons, staircase, amplifier, root/capture/blast and gunner pause requirements implemented',unchangedGeometrySha256=shared.sha(server/'data/geo/models.mesh'))
 entries={e['path']:e for e in m['files']}
 def payload(rel,content):
  dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(content)
  entries[rel]=dict(path=rel,original=shared.sha(server/rel) if (server/rel).is_file() else None,installed=shared.sha(dest))
 for name in SCRIPTS:
  payload('data/handlers/ai/instance/rakes/'+name+'.java',(shared.ROOT/'game-server/data/handlers/ai/instance/rakes'/(name+'.java')).read_bytes())
  for f in (a.scripts/'ai/instance/rakes').glob(name+'*.class'):
   if f.stem!=name and not f.stem.startswith(name+'$'):continue
   payload('cache/classes/ai/instance/rakes/'+f.name,f.read_bytes())
 for rel in DATA:payload(rel,patch_data(rel,(server/rel).read_bytes()))
 # Retain every earlier native script file in the cumulative receipt and hash guards.
 prior=json.loads(Path(m['previousReceipt']).read_text())
 for e in prior['files']:
  if e['path'] not in entries:payload(e['path'],(server/e['path']).read_bytes())
 m['files']=list(entries.values())
 for e in m['files']:e['installed']=shared.sha(out/e['path'])
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(out/'libs/playerbot-recruitment-fix.jar') as new:
  changed={e['path'] for e in m['incrementalChangedMethods']};retained=[n for n in old.namelist() if n not in changed]
  assert all(old.read(n)==new.read(n) for n in retained);m['retainedJarEntries']=len(retained)
 (out/'manifest.json').write_text(json.dumps(m,indent=2));print('OK: original requirement completion staged; unrelated JAR entries retained:',len(retained))
if __name__=='__main__':main()
