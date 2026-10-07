"""Verify only the evidenced gear-save method changes; preserve every cumulative entry and settings file."""
import argparse,json,zipfile,subprocess
from pathlib import Path
import stage_companion_update as shared
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True);a=p.parse_args();out=a.package.resolve()
server=shared.ROOT/'target-deploy/game-server';baseline=server/'libs/playerbot-recruitment-fix.jar';payload=out/'libs/playerbot-recruitment-fix.jar'
m=json.loads((out/'manifest.json').read_text());rel=shared.PREFIX+'services/playerbot/PlayerBotGearPolicy$State.class';name=rel[:-6]
assert m['newClasses']==[] and len(m['incrementalChangedMethods'])==1
assert m['incrementalChangedMethods'][0]['path']==rel and m['incrementalChangedMethods'][0]['methods']==['void save();']
with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
 assert set(old.namelist())==set(new.namelist()) and new.testzip() is None
 preserved=[n for n in old.namelist() if n!=rel and old.read(n)==new.read(n)]
 assert len(preserved)==len(old.namelist())-1
 before=shared.methods(baseline,name);after=shared.methods(payload,name)
 assert before.keys()==after.keys() and [k for k in before if before[k]!=after[k]]==['void save();']
 assert any('PlayerBotSettingsFiles.replace' in x for x in after['void save();'])
 care=shared.methods(payload,shared.PREFIX+'services/playerbot/PlayerBotQuestSync$State')
 assert any('PlayerBotSettingsFiles.replace' in x for x in care['void save();'])
for e in m['files']:
 assert shared.sha(server/e['path'])==e['original'] and shared.sha(out/e['path'])==e['installed']
 if e['path']!='libs/playerbot-recruitment-fix.jar':assert e['original']==e['installed']
report=dict(preservedEntries=len(preserved),changedMethods=1,newClasses=0,careRetryRetained=True,overrideSha256=shared.sha(payload),gameplayAcceptance='pending user testing')
(out/'verification.json').write_text(json.dumps(report,indent=2));print('OK:',report)
