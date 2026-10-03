"""Test backup, hash guards and restoration against disposable fixture files."""
from pathlib import Path
import hashlib
import json
import sys
import uuid
import install

root=Path(__file__).resolve().parents[2]/'output/browser-flash'/('install-fixture-'+uuid.uuid4().hex)
client=root/'client';staged=root/'staged'
for directory in [client/'bin64',staged/'bin64']:directory.mkdir(parents=True,exist_ok=False)
original=b'fixture original Game.dll';patched=b'fixture instrumented Game.dll';probe=b'fixture probe.dll';awesomium=b'fixture browser.dll';unrelated=b'fixture unrelated patch.dll'
def digest(data):return hashlib.sha256(data).hexdigest()
for name,data in [('Game.dll',original),('Awesomium.dll',awesomium),('Other.dll',unrelated)]:(client/'bin64'/name).write_bytes(data)
for name,data in [('Game.dll',patched),('AionBrowserProbe.dll',probe)]:(staged/'bin64'/name).write_bytes(data)
manifest=dict(kind='fixture',client=str(client),originalGameSha256=digest(original),awesomiumSha256=digest(awesomium),files={'Game.dll':digest(patched),'AionBrowserProbe.dll':digest(probe)})
tracking='DXVK/graphics-menu/graphics-state.json';baseline='DXVK/graphics-menu/probe-original.dll'
old_tracking=b'fixture original graphics tracking';new_tracking=b'fixture updated graphics tracking';new_baseline=b'fixture diagnostic-aware baseline'
for base,name,data in [(client,tracking,old_tracking),(staged,tracking,new_tracking),(staged,baseline,new_baseline)]:
    target=base/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
manifest['graphicsTrackingFiles']=[dict(path=tracking,original=digest(old_tracking),installed=digest(new_tracking)),dict(path=baseline,original=None,installed=digest(new_baseline))]
(staged/'manifest.json').write_text(json.dumps(manifest),encoding='utf-8')
# Only this disposable fixture skips the separately tested live-client guard.
install.closed=lambda:None
def run(*args):
    sys.argv=['install.py','--staged',str(staged),*args];install.main()
run()
backup=next((client/'BrowserFrame-backups').iterdir())
assert (backup/'Game.dll').read_bytes()==original
assert (client/'bin64/Game.dll').read_bytes()==patched
assert (client/'bin64/Other.dll').read_bytes()==unrelated
assert (client/tracking).read_bytes()==new_tracking
assert (client/baseline).read_bytes()==new_baseline
try:run()
except ValueError:pass
else:raise AssertionError('Repeat installation should be refused')
(client/'bin64/Game.dll').write_bytes(b'fixture later modification')
try:run('--restore',str(backup))
except ValueError:pass
else:raise AssertionError('Restoration must refuse later client changes')
assert (client/'bin64/Game.dll').read_bytes()==b'fixture later modification'
(client/'bin64/Game.dll').write_bytes(patched);run('--restore',str(backup))
assert (client/'bin64/Game.dll').read_bytes()==original
assert not (client/'bin64/AionBrowserProbe.dll').exists()
assert (client/'bin64/Other.dll').read_bytes()==unrelated
assert (client/tracking).read_bytes()==old_tracking
assert not (client/baseline).exists()
print('PASS: disposable install/backup/restore; unrelated DLL preserved; duplicate and changed-client guards refuse without overwrite')
