"""PB-REPAIR-ENGINE-001: repair native cast/movement admission and idempotent engine controls."""
import argparse,json,shutil,zipfile
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={
 shared.PREFIX+'controllers/movement/PlayerBotMoveController':{'moveStep'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'order','applyPreferences'},
 shared.PREFIX+'services/playerbot/PlayerBotSession$CastAction':{'execute'},
 'playercommands/Bot':set(),
}
base.HELPERS=set()

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--baseline-evidence',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();shared.validate_output(out);server=shared.ROOT/'target-deploy/game-server'
 prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  src=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else a.classes/(name+'.class')
  dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dest)
 base.stage(prepared,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 m=json.loads((out/'manifest.json').read_text());prior=json.loads(Path(m['previousReceipt']).read_text())
 evidence=json.loads(a.baseline_evidence.read_text());assert all(evidence['baselineFailuresReproduced'].values())
 refreshed={e['path']:e for e in evidence['regeneratedNativeScriptClasses']}
 known={e['path'] for e in m['files']}
 for entry in prior['files']:
  rel=entry['path'];assert (server/rel).resolve().is_relative_to(server.resolve())
  if rel in known:continue
  actual=shared.sha(server/rel)
  if actual!=entry['installed']:
   verified=refreshed[rel]
   assert verified['actual']==actual and verified['recorded']==entry['installed'] and verified['identicalEffectiveMethodsAndSchema']
  dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/rel,dest)
  m['files'].append(dict(path=rel,original=actual,installed=actual))
 for entry in m['files']:entry['installed']=shared.sha(out/entry['path'])
 assert [e['path'] for e in m['files'] if e['original']!=e['installed']]==['libs/playerbot-recruitment-fix.jar']
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(out/'libs/playerbot-recruitment-fix.jar') as new:
  selected={e['path'] for e in m['incrementalChangedMethods']};retained=[name for name in old.namelist() if name not in selected]
  assert new.namelist()==old.namelist() and all(old.read(name)==new.read(name) for name in retained)
  m['retainedJarEntries']=len(retained)
 m.update(feature='playerbot-core-cast-execution',scope='PB-REPAIR-ENGINE-001: committed casts exclude stale mover tasks; atomic cast admission and idempotent commands/preferences',unchangedGeometrySha256=shared.sha(server/'data/geo/models.mesh'),baselineNativeScriptRefresh=evidence['regeneratedNativeScriptClasses'])
 assert len(m['incrementalChangedMethods'])==3 and sum(len(e['methods']) for e in m['incrementalChangedMethods'])==4
 (out/'manifest.json').write_text(json.dumps(m,indent=2))
 print('OK: four core methods staged; earlier JAR entries retained:',len(retained))
if __name__=='__main__':main()
