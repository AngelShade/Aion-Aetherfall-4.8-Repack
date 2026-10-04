"""Verify post-install mod files, preferences and cumulative tank package preservation."""
import argparse,json,zipfile
from pathlib import Path
from stage_companion_update import ROOT,sha
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--receipt',type=Path,required=True);a=p.parse_args()
work=ROOT/'target/playerbots-tank-position';server=ROOT/'target-deploy/game-server';backup=a.receipt.resolve()
before=json.loads((work/'inventory-before.json').read_text());after=json.loads((ROOT/'docs/INSTALLED_MODS.json').read_text())
assert before['clientFiles']==after['clientFiles'],'Client modification changed'
assert before['serverJarSha256']==after['serverJarSha256'],'Base JAR changed'
assert before['serverLauncherSha256']==after['serverLauncherSha256'],'Launcher changed'
assert all(after['checks'].values()),'Installed mod check failed'
manifest=json.loads((backup/'manifest.json').read_text());assert manifest['previousReceipt'].replace('\\','/').endswith('playerbots-recruitment-20261004-224922-627052/manifest.json')
assert all(sha(server/e['path'])==e['installed'] for e in manifest['files'])
selected={e['path'] for e in manifest['incrementalChangedMethods']};assert len(selected)==1
with zipfile.ZipFile(backup/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as new:
 assert new.testzip() is None
 assert set(new.namelist())==set(old.namelist())|selected
 preserved=0
 for name in old.namelist():
  if name not in selected:assert old.read(name)==new.read(name),name;preserved+=1
def settings(root):return {p.relative_to(root).as_posix():p for p in root.rglob('*') if p.is_file() and p.suffix in {'.properties','.json'}}
old=settings(backup/'preferences-before');new=settings(server/'config/playerbots');assert old.keys()==new.keys(),'Preference file set changed'
growth=[]
for rel,path in old.items():
 if path.read_bytes()==new[rel].read_bytes():continue
 assert rel.startswith('gear-character-') and path.suffix=='.properties',rel
 def props(p):return dict(line.split('=',1) for line in p.read_text().splitlines() if line and not line.startswith(('#','!')))
 a,b=props(path),props(new[rel]);assert a.keys()==b.keys(),rel
 assert all(a[k]==b[k] for k in a if k!='generated'),rel
 assert set(a.get('generated','').split(','))<=set(b.get('generated','').split(',')),rel
 growth.append(rel)
report=dict(receipt=str(backup.relative_to(server)),preservedEntries=preserved,clientHashes=len(after['clientFiles']),modChecks=len(after['checks']),settingsFiles=len(old),normalProvenanceGrowth=growth,serverReceipts=len(after['serverReceipts']),clientReceipts=len(after['clientReceipts']),baseAndLauncherPreserved=True)
(work/'preservation.json').write_text(json.dumps(report,indent=2));print('OK:',json.dumps(report))
