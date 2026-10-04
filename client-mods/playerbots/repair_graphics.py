"""Rebase graphics/cursor recovery over a verified installed companion patch.

Only graphics tracking, its cached package, and new recovery baselines change.
Live game binaries, archives, signing keys, launcher and preferences stay intact.
"""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

import prepare
from graphics_compat import prepare_incremental


def stage(client, receipt, output):
    if output.exists() or output.is_relative_to(client):
        raise ValueError('Use a new staging directory outside the client')
    dll = (client / 'bin64/Game.dll').read_bytes()
    result, compatibility = prepare_incremental(client, output, dll, receipt)
    if result != dll or not compatibility:
        raise ValueError('Expected installed Graphics menu and unchanged live DLL')
    files = [dict(path=p.relative_to(output).as_posix(),
                  original=prepare.sha((client/p.relative_to(output)).read_bytes()) if (client/p.relative_to(output)).is_file() else None,
                  staged=prepare.sha(p.read_bytes())) for p in sorted(output.rglob('*')) if p.is_file()]
    manifest = dict(feature='player-companions-graphics-recovery', clientRoot=str(client),
                    companionReceipt=str(receipt), graphicsCompatibility=compatibility,
                    compactBrowserTitles=True, companionRoutes=True, files=files)
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    return manifest


def apply(client, output):
    manifest = json.loads((output/'manifest.json').read_text())
    if manifest['feature'] != 'player-companions-graphics-recovery' or Path(manifest['clientRoot']).resolve() != client:
        raise ValueError('Recovery package belongs to another client')
    environment=dict(os.environ)
    environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    process = subprocess.run(['powershell.exe', '-NoProfile', '-Command',
        "$ErrorActionPreference='Stop'; if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { exit 1 }"], capture_output=True,env=environment)
    if process.returncode:
        raise ValueError('Close Aion normally before repairing recovery records')
    backup = client/'DXVK-backups'/('companions-tracking-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir()
    untouched = ['bin64/Game.dll', 'Aion Start.bat', 'Addon.key'] + prepare.PRESERVED + [
        'Plugin/RelicCalc/RelicCalc.pak', 'Plugin/RelicCalc/RelicCalc.pak.sig',
        'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig']
    preserved = {rel:prepare.sha((client/rel).read_bytes()) for rel in untouched if (client/rel).is_file()}
    for entry in manifest['files']:
        rel = entry['path']; destination = (client/rel).resolve()
        allowed = rel.startswith('DXVK/graphics-menu/') or rel == 'DXVK/installed.json' or rel.startswith('DXVK-backups/'+manifest['graphicsCompatibility']['backupName']+'/')
        if not destination.is_relative_to(client) or not allowed:
            raise ValueError('Unexpected recovery target: '+rel)
        actual = prepare.sha(destination.read_bytes()) if destination.is_file() else None
        if actual != entry['original'] or prepare.sha((output/rel).read_bytes()) != entry['staged']:
            raise ValueError('Recovery package or client changed: '+rel)
        if actual is not None:
            saved=backup/rel; saved.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(destination,saved)
            if prepare.sha(saved.read_bytes()) != actual: raise ValueError('Recovery backup failed: '+rel)
    shutil.copy2(output/'manifest.json',backup/'manifest.json')
    done=[]
    try:
        for entry in manifest['files']:
            target=client/entry['path'];target.parent.mkdir(parents=True,exist_ok=True)
            done.append(entry)
            temporary=target.with_name(target.name+'.companions-recovery.tmp')
            shutil.copy2(output/entry['path'],temporary);os.replace(temporary,target)
            if prepare.sha(target.read_bytes()) != entry['staged']:raise ValueError('Installed recovery checksum failed')
        for script,extra in [('DXVK/graphics-menu/ApplyGraphicsMenu.ps1',['-VerifyOnly']),('DXVK/cursor-fix/InstallNativeCursorPatch.ps1',[])]:
            subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(client/script),'-ClientPath',str(client),*extra],check=True,env=environment)
        for rel,expected in preserved.items():
            if prepare.sha((client/rel).read_bytes()) != expected:raise ValueError('Live mod changed: '+rel)
    except Exception:
        for entry in reversed(done):
            target=client/entry['path']
            if entry['original'] is None:target.unlink(missing_ok=True)
            else:shutil.copy2(backup/entry['path'],target)
        raise
    (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),preservedFiles=preserved,files=manifest['files']),indent=2))
    print('OK: graphics/cursor startup guards pass; live mods unchanged. Recovery backup:',backup)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True);parser.add_argument('--receipt',type=Path)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    try:
        client=args.client.resolve();output=args.output.resolve()
        if args.receipt:stage(client,args.receipt.resolve(),output)
        if args.apply:apply(client,output)
    except Exception as error:
        print('FAIL:',error);raise SystemExit(1)
