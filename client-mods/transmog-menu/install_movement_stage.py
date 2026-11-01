"""Install a prepare_lfg_movement.py stage into the original client, with a backup and hash guards."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    args = parser.parse_args()
    stage, backup = args.stage.resolve(strict=True), args.backup.resolve()
    manifest = json.loads((stage / 'manifest.json').read_text(encoding='utf-8'))
    client = Path(manifest['clientRoot']).resolve(strict=True)
    if not manifest.get('feature', '').endswith('window-movement') or (client.parent / 'REPACK.json').exists():
        raise SystemExit('Expected an original-client window movement stage')
    if backup.exists() or backup.is_relative_to(client) or backup.is_relative_to(stage):
        raise SystemExit('Use a fresh external backup folder')
    # CIM must succeed: tasklist can omit an elevated client, and a failed query is never closure proof.
    running = subprocess.run(['powershell.exe', '-NoProfile', '-Command',
        "$ErrorActionPreference='Stop'; Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe') } | ForEach-Object Name"],
        check=True, capture_output=True, text=True).stdout.strip()
    if running:
        raise SystemExit('Close the client first: ' + running)
    for record in manifest['preservedFiles']:
        if sha(client / record['path']) != record['sha256']:
            raise SystemExit(record['path'] + ' changed since the stage was built; rebuild')
    for record in manifest['files']:
        live = client / record['path']
        if (sha(live) if live.exists() else None) != record['original']:
            raise SystemExit(record['path'] + ' changed since the stage was built; rebuild')
        if sha(stage / record['path']) != record['installed']:
            raise SystemExit(record['path'] + ' stage copy does not match its manifest')
    for record in manifest['files']:
        live = client / record['path']
        saved = backup / record['path']
        if live.exists():
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(live, saved)
    for record in manifest['files']:
        live = client / record['path']
        live.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(stage / record['path'], live)
        if sha(live) != record['installed']:
            raise SystemExit(record['path'] + ' did not install; restore from ' + str(backup))
    (backup / 'receipt.json').write_text(json.dumps(dict(feature=manifest['feature'], files=manifest['files'],
        restore='With the client closed, copy this folder over the client root.'), indent=2) + '\n')
    print('INSTALLED', len(manifest['files']), 'files; backup', backup)


if __name__ == '__main__':
    main()
