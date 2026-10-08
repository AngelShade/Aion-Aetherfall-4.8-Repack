"""Stage the window queue repair from the current cumulative installation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
from window_queue_patch import patch
from graphics_compat import prepare_incremental

DEV_ROOT=Path(os.environ.get('AION_DEV_ROOT', 'D:/Proiecte/Project Restructure/Aion Development Workspace'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--inventory',type=Path,required=True)
    a=p.parse_args();root=a.client.resolve();out=a.output.resolve()
    if not out.is_relative_to(DEV_ROOT.resolve()) or out.exists():raise ValueError('Use fresh external staging output')
    inventory=json.loads(a.inventory.read_text())
    if Path(inventory['clientRoot']).resolve()!=root or not all(inventory['checks'].values()):raise ValueError('Current client inventory must pass')
    for entry in inventory['clientFiles']:
        if sha(root/entry['path'])!=entry['sha256']:raise ValueError('Client changed after inventory: '+entry['path'])
    current=root/'bin64/Game.dll'
    updated,meta=patch(current.read_bytes(),(root/'bin64/game.dll.orig').read_bytes())
    out.mkdir(parents=True)
    updated,graphics=prepare_incremental(root,out,updated)
    if not graphics:raise ValueError('Expected installed Graphics/cursor compatibility')
    path=out/'bin64/Game.dll';path.parent.mkdir(parents=True);path.write_bytes(updated)
    entries=[dict(path=f.relative_to(out).as_posix(),original=sha(root/f.relative_to(out)),staged=sha(f)) for f in sorted(out.rglob('*')) if f.is_file()]
    changed={e['path'].lower() for e in entries}
    # Preserve every current inventoried client feature file, plus pet/signing
    # archives even when an inventory version does not list those explicitly.
    names={'bin32/bin32.pak','Data/func_pet/func_pet.pak','Plugin/RelicCalc/RelicCalc.pak',
           'Pub.key','Addon.key','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig',
           'Plugin/RelicCalc/RelicCalc.pak.sig','Aion Start.bat','bin64/Awesomium.dll'}
    hashes=inventory.get('clientFiles',inventory.get('files',{}))
    if isinstance(hashes,dict):names.update(hashes)
    elif isinstance(hashes,list):names.update(e['path'] for e in hashes)
    names.update(['bin64/CrySystem.dll','bin64/AionIconBridge.dll','bin64/AionIconBridge.index',
                  'bin64/AionMarketShortcut.dll','Data/Items/Items.pak','Data/ui/ui.pak',
                  'Data/ui/game/game.pak','L10N/enu/Data/data.pak'])
    preserved=[dict(path=n,sha256=sha(root/n)) for n in sorted(names) if n.lower() not in changed and (root/n).is_file()]
    manifest=dict(feature='custom-window-queue-v1',clientRoot=str(root),graphicsCompatibility=graphics,
                  hook=meta,files=entries,preservedFiles=preserved,status='staged; real-game acceptance pending')
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print('OK: staged',len(entries),'files;',len(preserved),'preserved resources; client untouched')

if __name__=='__main__':main()
