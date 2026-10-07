"""Check the actual staged transmog definitions, preservation, and offline native regressions."""
import argparse,json,subprocess,zipfile
from pathlib import Path
import stage_companion_update as shared

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);p.add_argument('--classes',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();classes=a.classes.resolve();m=json.loads((package/'manifest.json').read_text())
 server=shared.ROOT/'target-deploy/game-server';baseline=server/'libs/playerbot-recruitment-fix.jar';payload=package/'libs/playerbot-recruitment-fix.jar'
 entry=next(e for e in m['files'] if e['path']=='libs/playerbot-recruitment-fix.jar')
 assert shared.sha(baseline)==entry['original']
 assert all(shared.sha(package/e['path'])==e['installed'] and shared.sha(server/e['path'])==e['original'] for e in m['files'])
 assert all(e['original']==e['installed'] for e in m['files'] if e['path'] not in {'libs/playerbot-recruitment-fix.jar','config/playerbots/media/bots.js'})
 selected={e['path']:set(e['methods']) for e in m['incrementalChangedMethods']};assert sum(map(len,selected.values()))==5
 preserved=0
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
  assert new.testzip() is None and set(new.namelist())==set(old.namelist())|set(m['newClasses'])|set(selected)
  for name in old.namelist():
   if name not in selected:assert old.read(name)==new.read(name),name;preserved+=1
  for name in m['newClasses']:assert new.read(name)==(classes/name).read_bytes(),name
 for name,allowed in selected.items():
  old=shared.methods(str(baseline)+';'+str(server/'libs/game-server-4.8-SNAPSHOT.jar'),name[:-6]);new=shared.methods(payload,name[:-6]);source=shared.methods(classes,name[:-6])
  assert old.keys()==new.keys(),name
  assert {k for k in old if old[k]!=new[k]}==allowed,name
  assert all(new[k]==source[k] for k in allowed),name
 checks=package/'effective-checks';checks.mkdir(exist_ok=True);cp=str(payload)+';'+str(server/'libs/*')
 test=shared.ROOT/'game-server/test/com/aionemu/gameserver/services/playerbot/PlayerBotAppearanceCheck.java'
 subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(checks),str(test)],check=True)
 subprocess.run(['java','-Xverify:all','-cp',str(checks)+';'+cp,'com.aionemu.gameserver.services.playerbot.PlayerBotAppearanceCheck'],check=True)
 result=dict(preservedEntries=preserved,changedMethods=5,newClasses=len(m['newClasses']),effectiveAppearanceChecks=16,installed=False,nativeClientAcceptance='pending user testing')
 (package/'verification.json').write_text(json.dumps(result,indent=2));print('OK:',result)
if __name__=='__main__':main()
