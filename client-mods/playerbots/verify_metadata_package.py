"""Offline effective metadata integration, schema preservation, and actual DAO/cache checks."""
import argparse,json,subprocess,zipfile,hashlib
from pathlib import Path
import stage_companion_update as shared

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);p.add_argument('--classes',type=Path,required=True);p.add_argument('--baseline',type=Path);p.add_argument('--installed',action='store_true');a=p.parse_args()
 package=a.package.resolve();shared.validate_output(package);server=shared.ROOT/'target-deploy/game-server';m=json.loads((package/'manifest.json').read_text())
 payload=package/'libs/playerbot-recruitment-fix.jar';baseline=a.baseline.resolve() if a.baseline else server/'libs/playerbot-recruitment-fix.jar';selected={r['path']:set(r['methods']) for r in m['incrementalChangedMethods']}
 assert shared.sha(baseline)==next(e['original'] for e in m['files'] if e['path']=='libs/playerbot-recruitment-fix.jar')
 assert shared.sha(package/'playerbot_metadata.sql')==m['metadataSchemaSha256'] and shared.sha(package/'tools/PlayerBotMetadataMigration.class')==m['migrationToolSha256']
 assert sum(map(len,selected.values()))==8 and len(selected)==6
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
  assert set(new.namelist())==set(old.namelist())|set(m['newClasses']) and new.testzip() is None
  preserved=0
  for name in old.namelist():
   if name not in selected:assert old.read(name)==new.read(name),name;preserved+=1
 for name,allowed in selected.items():
  before=shared.methods(baseline,name[:-6]);after=shared.methods(payload,name[:-6]);source=shared.methods(a.classes,name[:-6])
  assert before.keys()==after.keys(),name
  assert {k for k in before if before[k]!=after[k]}==allowed,name
  assert all(after[k]==source[k] for k in allowed),name
 for e in m['files']:
  assert shared.sha(server/e['path'])==e['installed' if a.installed else 'original'] and shared.sha(package/e['path'])==e['installed'],e['path']
  if e['path']!='libs/playerbot-recruitment-fix.jar':assert e['original']==e['installed']
 for short in ['PlayerBotQuestSync$State','PlayerBotGearPolicy$State']:
  code=shared.methods(payload,shared.PREFIX+'services/playerbot/'+short)['void save();']
  assert any('PlayerBotMetadata.save' in x for x in code)
  assert not any('java/nio/file/Files.' in x or 'DatabaseFactory.' in x or 'PlayerBotSettingsFiles.' in x for x in code)
 cp=str(payload)+';'+str(server/'libs/*')
 for short,method in [('PlayerBotPersistence','save'),('PlayerBotTradeStore','commit')]:
  codes=shared.methods(payload,shared.PREFIX+'services/playerbot/'+short)
  code=next(v for k,v in codes.items() if shared.method_name(k)==method)
  where=lambda s:next(i for i,x in enumerate(code) if s in x)
  assert where('InventoryDAO.storeCompanionInventory')<where('PlayerBotMetadata.store')<where('java/sql/Connection.commit')<where('PlayerBotMetadata.committed')<where('InventoryDAO.companionInventoryCommitted')
  assert any('InventoryDAO.markCompanionInventoryDirty' in x for x in code),'Native rollback dirty marking lost'
 checks=package/'effective-checks';checks.mkdir(exist_ok=True)
 source=shared.ROOT/'game-server/test/com/aionemu/gameserver/services/playerbot/PlayerBotMetadataCheck.java'
 subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(checks),str(source)],check=True)
 result=subprocess.check_output(['java','-Xverify:all','-cp',str(checks)+';'+cp,'com.aionemu.gameserver.services.playerbot.PlayerBotMetadataCheck'],cwd=checks,text=True,stderr=subprocess.STDOUT)
 (package/'effective-checks.txt').write_text(result,encoding='utf-8');assert 'OK: 35 ' in result
 report=dict(preservedEntries=preserved,changedMethods=8,existingClasses=6,newClasses=len(m['newClasses']),effectiveChecks=35,sharedCheckpointOrderVerified=True,careGearSaveHasNoIO=True,installed=a.installed,databaseMigration='see successful installation receipt and committed-row check' if a.installed else 'pending native migration',nativeClientAcceptance='pending user testing')
 (package/'verification.json').write_text(json.dumps(report,indent=2));print('OK:',report)
if __name__=='__main__':main()
