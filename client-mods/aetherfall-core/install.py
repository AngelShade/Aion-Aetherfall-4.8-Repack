"""Install a staged AetherfallCore build on the original client (guarded, reversible).

Requires a successful full CIM process enumeration showing Aion and the client
launcher closed. Every live file must still match the hash the stage was built
from. Each target is copied to a fresh external backup first; staged files are
then copied and their hashes verified. Afterwards the three standing checks run:
Graphics menu -VerifyOnly, launcher --custom-quests-sync and
--aetherfall-interface. A receipt with all hashes and check outputs is written
to the backup folder; restore by copying backup/files back with Aion closed.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def closed():
    # The restricted Get-Process view can omit a running elevated Aion process.
    # CIM must succeed; an inaccessible full process list is never closure proof.
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command',
        "$ErrorActionPreference='Stop'; Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe','Aetherfall Launcher.exe') } | Select-Object -ExpandProperty Name"],
        check=True, capture_output=True, text=True)
    if result.stdout.strip():
        raise SystemExit('Close Aion and its launcher normally first: ' + result.stdout.strip())


def checks(client, work):
    out = {}
    # The current desktop shell's module path may omit Windows PowerShell's
    # Get-FileHash. Use the available current PowerShell for this existing guard.
    graphics_shell = shutil.which('pwsh.exe') or 'powershell.exe'
    result = subprocess.run([graphics_shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                             str(client / 'Mods/GraphicsMenu/Install-GraphicsMenu.ps1'), '-ClientPath', str(client), '-VerifyOnly'],
                            capture_output=True, text=True)
    out['graphics'] = dict(rc=result.returncode, output=(result.stdout + result.stderr).strip())
    for name, flag in [('custom-quests', '--custom-quests-sync'), ('interface', '--aetherfall-interface')]:
        report = work / (name + '.txt')
        result = subprocess.run([str(client / 'Aetherfall Launcher.exe'), flag, '--client', str(client), '--report', str(report)])
        out[name] = dict(rc=result.returncode, output=report.read_text(encoding='utf-8-sig', errors='replace').strip() if report.exists() else '')
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage', type=Path, required=True)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--backup', type=Path, required=True, help='fresh folder outside the client and source')
    a = p.parse_args()
    stage, client, backup = a.stage.resolve(), a.client.resolve(), a.backup.resolve()
    manifest = json.loads((stage / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('feature') != 'aetherfall-core' or Path(manifest['clientRoot']).resolve() != client:
        raise SystemExit('This stage belongs to another client or feature')
    if backup.exists() or backup.is_relative_to(client):
        raise SystemExit('Use a fresh external backup folder')
    closed()
    for rel, digest in manifest.get('inputGuards', {}).items():
        if sha(client / rel) != digest:
            raise SystemExit('Native quest metadata input changed; rebuild: ' + rel)
    for f in manifest['files']:
        live = client / f['path']
        current = sha(live) if live.exists() else None
        if current != f['original']:
            raise SystemExit(f"{f['path']} changed since the stage was built; rebuild")
        if sha(stage / f['path']) != f['installed']:
            raise SystemExit(f"staged {f['path']} does not match its manifest")
    backup.mkdir(parents=True)
    for f in manifest['files']:
        live = client / f['path']
        if live.exists():
            target = backup / 'files' / f['path']
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(live, target)
            if sha(target) != f['original']:
                raise SystemExit('backup verification failed: ' + f['path'])
    closed()  # re-check immediately before replacing binaries
    copied = {}
    for f in manifest['files']:
        target = client / f['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(stage / f['path'], target)
        copied[f['path']] = sha(target)
        if copied[f['path']] != f['installed']:
            raise SystemExit(f"copy verification failed: {f['path']}; restore from {backup / 'files'}")
    results = checks(client, backup)
    receipt = dict(change='AetherfallCore Lua bridge and HUD data layer', installedAt=datetime.now(timezone.utc).isoformat(),
                   client=str(client), stage=str(stage), files=manifest['files'], copied=copied,
                   newFiles=[f['path'] for f in manifest['files'] if f['original'] is None], checks=results,
                   restore='With Aion closed: copy backup/files over the client and delete the newFiles.')
    (backup / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(results, indent=2))
    print('receipt:', backup / 'receipt.json')
    if any(r['rc'] != 0 for r in results.values()):
        raise SystemExit('a post-install check failed; see the receipt')


if __name__ == '__main__':
    main()
