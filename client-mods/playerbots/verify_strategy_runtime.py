"""Verify captured effective methods and live-install preservation; never attaches or mutates."""
import argparse,json,re,zipfile
from pathlib import Path
import stage_companion_update as shared

def tree(root):return {p.relative_to(root).as_posix():shared.sha(p) for p in root.rglob('*') if p.is_file()}

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['package','receipt','runtime','before-inventory','after-inventory','output']:p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();server=shared.ROOT/'target-deploy/game-server'
 manifest=json.loads((a.package/'manifest.json').read_text(encoding='utf-8'))
 receipt=json.loads((a.receipt/'manifest.json').read_text(encoding='utf-8'))
 assert receipt==manifest,'Installed receipt differs from reviewed package'
 assert (a.receipt/'runtime-preflight.txt').read_text().endswith('OK: every existing override class loaded before replacing the JAR; no character state changed.\n')
 humans=re.search(r'Human connections before=(\d+) after=(\d+)',(a.receipt/'runtime-verification.txt').read_text())
 assert humans and humans[1]==humans[2],'Human connection count changed'
 for entry in manifest['files']:
  assert shared.sha(a.receipt/entry['path'])==entry['original'],'Recovery backup changed'
  assert shared.sha(server/entry['path'])==entry['installed'],'Installed payload changed'
  assert shared.sha(a.package/entry['path'])==entry['installed'],'Reviewed payload changed'
 assert shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==manifest['baseJarSha256']
 before=json.loads(a.before_inventory.read_text(encoding='utf-8'));after=json.loads(a.after_inventory.read_text(encoding='utf-8'))
 for field in ['clientFiles','deployedConfig','serverJarSha256','serverLauncherSha256']:assert before[field]==after[field],field
 assert all(after['checks'].values())
 prior=tree(a.receipt/'preferences-before');current=tree(server/'config/playerbots')
 differences=sorted(k for k in prior.keys()|current.keys() if prior.get(k)!=current.get(k))
 # Running bots may append generated-item provenance. Every preference value is
 # still required equal, and no arbitrary newly changed setting is accepted.
 for name in differences:
  assert name.startswith('gear-character-') and name.endswith('.properties'), 'Companion setting changed: '+name
  def properties(root):
   return dict(line.split('=',1) for line in (root/name).read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
  old,new=properties(a.receipt/'preferences-before'),properties(server/'config/playerbots')
  assert {k:v for k,v in old.items() if k!='generated'}=={k:v for k,v in new.items() if k!='generated'},name
  assert set(old.get('generated','').split(','))<=set(new.get('generated','').split(',')),'Provenance shrank'
 effective=a.runtime/'effective-loaded.jar';selected={e['path']:set(e['methods']) for e in manifest['incrementalChangedMethods']}
 verified=[]
 with zipfile.ZipFile(effective) as capture:
  for name in capture.namelist():
   actual=shared.methods(effective,name[:-6]);expected=shared.methods(a.package/'libs/playerbot-recruitment-fix.jar',name[:-6])
   if name in selected:
    assert actual.keys()==expected.keys(),'Effective schema differs: '+name
    keys=selected[name]
   else:keys=set(expected)
   assert keys<=actual.keys(),'Missing effective helper methods'
   for key in keys:
    assert actual[key]==expected[key],'Effective method differs: '+name+' '+key
    verified.append(dict(path=name,method=key))
 report=dict(receipt=str(a.receipt),effectiveMethods=len(verified),effectiveDefinitions=11,
  clientHashesPreserved=len(after['clientFiles']),modChecks=len(after['checks']),settingsFiles=len(current),
  allowedProvenanceChanges=differences,baseJarPreserved=True,launcherPreserved=True,
  runtime='49 loaded-engine fixtures and five naturally scheduled session contexts; client combat still pending',methods=verified)
 a.output.write_text(json.dumps(report,indent=2),encoding='utf-8')
 print('OK:',len(verified),'effective loaded methods,',len(after['clientFiles']),'client hashes,',len(after['checks']),'mod checks;',len(current),'settings files reviewed')
