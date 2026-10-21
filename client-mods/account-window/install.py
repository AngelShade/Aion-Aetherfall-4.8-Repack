"""Install only a prepared original-client Account window package, with backup and rollback."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
from graphics_compat import compatibility_paths


def sha(path):
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepared', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    args = parser.parse_args()
    prepared, backup = args.prepared.resolve(strict=True), args.backup.resolve()
    manifest = json.loads((prepared / 'manifest.json').read_text())
    client = Path(manifest['clientRoot']).resolve(strict=True)
    if manifest.get('feature') != 'account-window' or (client.parent / 'REPACK.json').exists():
        raise ValueError('Expected the original-client Account window package')
    if backup.exists() or backup.is_relative_to(client) or backup.is_relative_to(prepared):
        raise ValueError('Use a fresh external backup directory')
    allowed = {'bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak', 'Plugin/RelicCalc/RelicCalc.pak',
               'Plugin/RelicCalc/RelicCalc.pak.sig', 'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig', 'Addon.key',
               'Mods/AccountWindow/mod.json', 'Mods/AccountWindow/README.md', 'Mods/InstalledMods.json'}
    allowed.update(compatibility_paths(manifest['graphicsCompatibility']))
    staged = {e['path'] for e in manifest['files']}
    if not staged <= allowed or len(staged) != len(manifest['files']):
        raise ValueError('Unexpected Account window package file list')
    query = "$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process -Filter \"Name='aion.bin' OR Name='aion.exe'\" | Select-Object ProcessId,ExecutablePath) | ConvertTo-Json -Compress"
    raw = subprocess.check_output(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', query], text=True).strip()
    processes = json.loads(raw) if raw else []
    if isinstance(processes, dict):
        processes = [processes]
    for process in processes:
        if not process.get('ExecutablePath'):
            raise RuntimeError('Cannot verify the client executable path; no files replaced')
        if Path(process['ExecutablePath']).resolve().is_relative_to(client):
            raise RuntimeError('Close the original Aion client normally before installation')
    for path, expected in manifest['sourceHashes'].items():
        if sha(Path(path)) != expected:
            raise ValueError('Account window source changed after preparation')
    for entry in manifest['files']:
        path = client / entry['path']
        if not path.resolve().is_relative_to(client) or sha(prepared / entry['path']) != entry['staged']:
            raise ValueError('Staged path/hash mismatch')
        before = sha(path) if path.exists() else None
        if before != entry['original']:
            raise ValueError('Client changed after preparation: ' + entry['path'])
    for entry in manifest['preservedFiles']:
        if sha(client / entry['path']) != entry['sha256']:
            raise ValueError('Preserved client feature changed: ' + entry['path'])
    backup.mkdir(parents=True)
    shutil.copy2(prepared / 'manifest.json', backup / 'manifest.json')
    for entry in manifest['files']:
        if entry['original'] is not None:
            saved = backup / entry['path']
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(client / entry['path'], saved)
            if sha(saved) != entry['original']:
                raise RuntimeError('Backup checksum mismatch')
    written = []
    try:
        for entry in manifest['files']:
            path = client / entry['path']
            path.parent.mkdir(parents=True, exist_ok=True)
            written.append(entry)
            shutil.copy2(prepared / entry['path'], path)
            if sha(path) != entry['staged']:
                raise RuntimeError('Copied checksum mismatch')
        for entry in manifest['preservedFiles']:
            if sha(client / entry['path']) != entry['sha256']:
                raise RuntimeError('Preserved client hash changed during installation')
    except BaseException:
        for entry in reversed(written):
            path = client / entry['path']
            if entry['original'] is None:
                if path.exists():
                    path.unlink()
            else:
                shutil.copy2(backup / entry['path'], path)
        raise
    receipt = dict(feature='account-window', clientRoot=str(client), installedAt=datetime.now().astimezone().isoformat(),
                   backup=str(backup), files=manifest['files'], preservedFiles=manifest['preservedFiles'],
                   originalClientClosureVerified=True, serverChanged=False, repackChanged=False,
                   installerSha256=sha(Path(__file__)), gameplayAcceptance=manifest['gameplayAcceptance'])
    (backup / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print('Installed and hash-verified', len(written), 'affected files. Receipt:', backup / 'receipt.json')


if __name__ == '__main__':
    main()
