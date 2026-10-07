"""Bounded runtime repair using the current cumulative override and deployed scripts."""
import argparse, copy, json, shutil, subprocess, sys, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared
PREFIX=shared.PREFIX
SCOPES={PREFIX+'ai/AIEngine':{'init','reload','registerAI','newAI','validateScripts'},
        PREFIX+'services/CronJobService$IdianDepthPortalSpawner':{'run'},'admincommands/Reload':{'execute'},
        'instance/FireTempleInstance':{'onInstanceCreate'}}

def stage(classes,out):
 shared.validate_output(out)
 server=ROOT/'target-deploy/game-server'
 latest=sorted((server/'backups').glob('playerbots-recruitment-*/manifest.json'))[-1]
 receipt=json.loads(latest.read_text())
 expected={e['path']:e['installed'] for e in receipt['files']}
 for ui in sorted((server/'backups').glob('playerbots-ui-*/installed.json')):
  if ui.parent.name.removeprefix('playerbots-ui-')>latest.parent.name.removeprefix('playerbots-recruitment-'):
   for e in json.loads(ui.read_text())['files']:
    assert e['path'].startswith('config/playerbots/media/')
    expected[e['path']]=e['installed']
 assert all(shared.sha(server/rel)==digest for rel,digest in expected.items()),'Installed receipt baseline changed'
 # Preserve all files recorded by the last cumulative receipt, including newer UI receipts.
 files={e['path']:shared.sha(server/e['path']) for e in receipt['files']}
 assert shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==receipt['baseJarSha256']
 out.mkdir(parents=True,exist_ok=False);baseline=out/'baseline';baseline.mkdir();plan=[];review=[]
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as override,zipfile.ZipFile(server/'libs/game-server-4.8-SNAPSHOT.jar') as base:
  for name,allowed in SCOPES.items():
   rel=name+'.class';target=baseline/rel;target.parent.mkdir(parents=True,exist_ok=True)
   target.write_bytes((server/'cache/classes'/rel).read_bytes() if name.startswith(('admincommands/','instance/')) else override.read(rel) if rel in override.namelist() else base.read(rel))
   before=shared.methods(baseline,name);after=shared.methods(classes,name)
   changed=[k for k in before.keys()&after.keys() if before[k]!=after[k]]
   assert all(shared.method_name(k) in allowed for k in changed),changed
   assert not after.keys()-before.keys(),f'New runtime methods: {name}'
   if changed:plan.append(name+'\t'+','.join(sorted({shared.method_name(k) for k in changed})))
   review.append(dict(path=rel,methods=changed))
 (out/'methods.tsv').write_text('\n'.join(plan))
 tools=out/'tools';tools.mkdir()
 subprocess.run(['javac','-d',str(tools),str(shared.java_tool('StagePlayerBotCompanionPatch.java'))],check=True)
 staged=out/'classes'
 subprocess.run(['java','-cp',str(tools)+';'+str(classes)+';'+str(server/'libs/*'),'StagePlayerBotCompanionPatch',str(baseline),str(classes),str(staged),str(out/'methods.tsv')],check=True)
 for e in review:
  name=e['path'][:-6];before=shared.methods(baseline,name);after=shared.methods(staged,name)
  assert before.keys()==after.keys() and {k for k in before if before[k]!=after[k]}==set(e['methods'])
 new=[]
 for source in (classes/(PREFIX+'ai')).glob('AIRegistryReload*.class'):
  rel=source.relative_to(classes);target=staged/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
  with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old:
   if rel.as_posix() not in old.namelist():new.append(rel.as_posix())
   else:assert shared.methods(server/'libs/playerbot-recruitment-fix.jar',rel.as_posix()[:-6])==shared.methods(classes,rel.as_posix()[:-6]),'Existing helper changes require explicit runtime SCOPES'
 jarpath='libs/playerbot-recruitment-fix.jar';(out/'libs').mkdir()
 replacements={p.relative_to(staged).as_posix():p.read_bytes() for p in staged.rglob('*.class') if not p.relative_to(staged).as_posix().startswith(('admincommands/','instance/'))}
 with zipfile.ZipFile(server/jarpath) as old,zipfile.ZipFile(out/jarpath,'w') as merged:
  merged.comment=old.comment
  for e in old.infolist():merged.writestr(copy.copy(e),replacements.pop(e.filename,old.read(e)))
  for name,data in replacements.items():merged.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
 with zipfile.ZipFile(out/'rollback.jar','w') as rollback:
  for e in review:rollback.write(baseline/e['path'],e['path'])
 for rel in files:
  if rel==jarpath:continue
  target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/rel,target)
 # Compile the deployed Reload overlay, retaining its installed decomposable behavior.
 rel='data/handlers/admincommands/Reload.java';files[rel]=shared.sha(server/rel);target=out/rel;target.parent.mkdir(parents=True,exist_ok=True)
 shutil.copy2(ROOT/'target/kromede-repair/source/Reload.java',target)
 rel='cache/classes/admincommands/Reload.class';files[rel]=shared.sha(server/rel);target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(staged/'admincommands/Reload.class',target)
 rel='cache/classes/instance/FireTempleInstance.class';files[rel]=shared.sha(server/rel);target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(staged/'instance/FireTempleInstance.class',target)
 for source in (ROOT/'game-server/data/handlers/ai/instance/fireTemple').glob('*.java'):
  rel='data/handlers/ai/instance/fireTemple/'+source.name;files[rel]=shared.sha(server/rel) if (server/rel).exists() else None;target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
 # NPC mappings and 50/50 instance roll were already on disk; retain exact hashes.
 for rel in ['data/static_data/npcs/npc_templates.xml','data/handlers/instance/FireTempleInstance.java']:
  files[rel]=shared.sha(server/rel);target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/rel,target)
 manifest=dict(feature='ai-reload-kromede-repair',deployment=str(server),baseJarSha256=receipt['baseJarSha256'],previousReceipt=str(latest.relative_to(server)),scope='atomic async AI reload, resilient portal rotations and Kromede encounter',
  incrementalChangedMethods=review,changedMethods=receipt['changedMethods']+[e['path']+': '+k for e in review for k in e['methods']],newClasses=new,rollbackSha256=shared.sha(out/'rollback.jar'),
  files=[dict(path=rel,original=digest,installed=shared.sha(out/rel)) for rel,digest in files.items()])
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
 with zipfile.ZipFile(server/jarpath) as old,zipfile.ZipFile(out/jarpath) as newjar:
  changed={e['path'] for e in review}
  preserved=[n for n in old.namelist() if n not in changed]
  assert all(old.read(n)==newjar.read(n) for n in preserved)
 print('OK: all',len(preserved),'unrelated override entries retained; only reviewed installed methods changed:',out)

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();stage(a.classes.resolve(),a.output.resolve())
