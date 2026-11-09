"""Install a staged Broker Swap module (guarded, reversible).

Uses the same guards as aetherfall-core/install.py: Aion and its launcher must be closed,
every live file must still match the hash the stage was built from, each target is backed up
to a fresh external folder before copying, copies are hash-verified, and the standing Graphics
menu, custom quest and Aetherfall Interface checks run afterwards. Restore by copying
backup/files back with Aion closed.
"""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import shutil

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('core_install', HERE.parent / 'aetherfall-core/install.py')
core = importlib.util.module_from_spec(spec)
spec.loader.exec_module(core)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage', type=Path, required=True)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--backup', type=Path, required=True, help='fresh folder outside the client and source')
    a = p.parse_args()
    stage, client, backup = a.stage.resolve(), a.client.resolve(), a.backup.resolve()
    manifest = json.loads((stage / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('feature') != 'broker-swap' or Path(manifest['clientRoot']).resolve() != client:
        raise SystemExit('This stage belongs to another client or feature')
    if backup.exists() or backup.is_relative_to(client):
        raise SystemExit('Use a fresh external backup folder')
    core.closed()
    for f in manifest['files']:
        live = client / f['path']
        if (core.sha(live) if live.exists() else None) != f['original']:
            raise SystemExit(f"{f['path']} changed since the stage was built; rebuild")
        if core.sha(stage / f['path']) != f['installed']:
            raise SystemExit(f"staged {f['path']} does not match its manifest")
    backup.mkdir(parents=True)
    for f in manifest['files']:
        live = client / f['path']
        if live.exists():
            target = backup / 'files' / f['path']
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(live, target)
            if core.sha(target) != f['original']:
                raise SystemExit('backup verification failed: ' + f['path'])
    core.closed()
    copied = {}
    for f in manifest['files']:
        target = client / f['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(stage / f['path'], target)
        copied[f['path']] = core.sha(target)
        if copied[f['path']] != f['installed']:
            raise SystemExit(f"copy verification failed: {f['path']}; restore from {backup / 'files'}")
    results = core.checks(client, backup)
    receipt = dict(change='Broker Swap module (HUD Broker button follows the server Broker state; Central Market opens on request)',
                   installedAt=datetime.now(timezone.utc).isoformat(), client=str(client), stage=str(stage),
                   files=manifest['files'], archiveChanges=manifest.get('archiveChanges'), copied=copied,
                   newFiles=[f['path'] for f in manifest['files'] if f['original'] is None], checks=results,
                   restore='With Aion closed: copy backup/files over the client and delete the newFiles.')
    (backup / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(results, indent=2))
    print('receipt:', backup / 'receipt.json')
    if any(r['rc'] != 0 for r in results.values()):
        raise SystemExit('a post-install check failed; see the receipt')


if __name__ == '__main__':
    main()
