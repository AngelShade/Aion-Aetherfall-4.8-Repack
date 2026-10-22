"""Install a staged Aetherfall Interface build and the matching launcher into a client.

Refuses while Aion or the client launcher runs, or when the client's archives changed
since the stage was built. Backs up every file it may change to a fresh external
folder, copies the launcher and `Mods/AetherfallUI`, applies the requested state with
the launcher's own switch code, and writes a hash receipt.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

LAUNCHER = 'Aetherfall Launcher.exe'
MOD = 'Mods/AetherfallUI'


def sha(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as handle:
        for block in iter(lambda: handle.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def running(client):
    """Aion or this client's launcher; PowerShell gives executable paths."""
    script = "Get-Process | Where-Object { $_.ProcessName -match '^(aion|aion\\.bin|Aetherfall Launcher)$' } | ForEach-Object { $_.ProcessName + '|' + $_.Path }"
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', script], capture_output=True, text=True, check=True)
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    return [line for line in lines if not line.startswith('Aetherfall Launcher|') or str(client).lower() in line.lower()]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, required=True)
    parser.add_argument('--launcher', type=Path, required=True, help='launcher built by client-mods/launcher/build.ps1')
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True, help='fresh folder outside the client')
    parser.add_argument('--state', choices=['off', 'on', 'layout'], default='on')
    args = parser.parse_args()
    client, backup = args.client.resolve(), args.backup.resolve()
    staged_mod = args.stage.resolve() / MOD
    manifest = json.loads((staged_mod / 'manifest.json').read_text(encoding='utf-8'))
    if client in backup.parents or backup == client:
        raise SystemExit('backup must be outside the client')
    if backup.exists() and any(backup.iterdir()):
        raise SystemExit('backup must be a fresh, empty folder')
    busy = running(client)
    if busy:
        raise SystemExit('close Aion and the client launcher first: ' + '; '.join(busy))
    for rel, expected in manifest['sources'].items():
        if sha(client / rel) != expected:
            raise SystemExit(f'{rel} changed since the stage was built; rebuild the addon')
    for rel, entry in manifest['files'].items():
        if sha(client / rel) != entry['off']:
            raise SystemExit(f'{rel} changed since the stage was built; rebuild the addon')

    changed = [LAUNCHER] + list(manifest['archives']) + list(manifest['files'])
    backup.mkdir(parents=True)
    before = {}
    for rel in changed:
        target = backup / 'files' / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(client / rel, target)
        before[rel] = sha(client / rel)
        if sha(target) != before[rel]:
            raise SystemExit(f'backup verification failed: {rel}')
    if (client / MOD).exists():
        shutil.copytree(client / MOD, backup / 'files' / MOD)

    shutil.copy2(args.launcher, client / LAUNCHER)
    if sha(client / LAUNCHER) != sha(args.launcher):
        raise SystemExit('launcher copy verification failed')
    # HUD profiles live beside skin payloads. A skin rebuild must retain them.
    shutil.copytree(staged_mod, client / MOD, dirs_exist_ok=True)
    report = backup / 'apply.txt'
    result = subprocess.run([str(client / LAUNCHER), '--aetherfall-interface', '--client', str(client), '--state', args.state, '--report', str(report)])
    message = report.read_text(encoding='utf-8', errors='replace') if report.exists() else ''
    if result.returncode != 0:
        raise SystemExit(f'interface switch failed (rc {result.returncode}); restore from {backup}: {message}')

    after = {rel: sha(client / rel) for rel in changed}
    receipt = dict(change='Aetherfall Interface addon and launcher switch', installed_at=datetime.now(timezone.utc).isoformat(),
                   client=str(client), state=args.state, apply=message, version=manifest['version'],
                   launcher_sha256=sha(args.launcher), before=before, after=after,
                   mod_files={str(p.relative_to(client / MOD)).replace('\\', '/'): sha(p) for p in sorted((client / MOD).rglob('*')) if p.is_file()},
                   restore="Copy files/ back over the client with Aion closed, or switch the interface off in the launcher's Mods window.")
    (backup / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(message)
    print(f'receipt: {backup / "receipt.json"}')


if __name__ == '__main__':
    main()
