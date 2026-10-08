"""Record compiler-regenerated native script bytes only after matching reviewed methods/schema."""
import argparse,json,shutil,subprocess,copy
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,sha,methods,receipt_paths,validate_output

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--evidence',type=Path,required=True);a=p.parse_args()
 evidence=json.loads(a.evidence.read_text());server=ROOT/'target-deploy/game-server';prior=receipt_paths(server,'playerbots-recruitment-*/manifest.json')[-1];m=json.loads(prior.read_text())
 refresh={r['path']:r for r in evidence['regeneratedNativeScriptClasses']};assert refresh
 assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==m['baseJarSha256']
 for entry in m['files']:
  rel=entry['path'];assert (server/rel).resolve().is_relative_to(server.resolve())
  actual=sha(server/rel)
  if actual==entry['installed']:continue
  proof=refresh[rel];assert rel.startswith('cache/classes/ai/instance/rakes/') and rel.endswith('.class')
  assert proof['actual']==actual and proof['recorded']==entry['installed']
  name=rel.removeprefix('cache/classes/')[:-6];reference=Path(proof['referenceClasspath'])
  assert sha(reference/(name+'.class'))==entry['installed']
  assert methods(reference,name)==methods(server/'cache/classes',name)
  schema=lambda path:subprocess.check_output(['javap','-p','-s','-constants','-cp',str(path),name.replace('/','.')],text=True).splitlines()[1:]
  assert schema(reference)==schema(server/'cache/classes')
 backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));validate_output(backup);backup.mkdir(parents=True)
 observed=copy.deepcopy(m);observed.update(previousReceipt=str(prior),scope='Verified native script compiler cache refresh; no code/files deployed',incrementalChangedMethods=[],newClasses=[],baselineNativeScriptRefresh=list(refresh.values()))
 for entry in observed['files']:
  path=server/entry['path'];digest=sha(path);dest=backup/entry['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest);assert sha(dest)==digest
  entry.update(original=digest,installed=digest)
 (backup/'manifest.json').write_text(json.dumps(observed,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(observedAt=datetime.now().isoformat(),mode='read-only native compiler cache reconciliation; zero deployed replacements',files=observed['files']),indent=2))
 print('OK: reviewed native script methods/schema unchanged; refreshed recovery evidence only:',backup)
if __name__=='__main__':main()
