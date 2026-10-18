"""Repair moved-client graphics metadata without replacing game assets.

Explicit old/new roots and matching installed/package hashes are required.
Run while Aion is closed; the launcher may remain open between launch attempts.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def repair(client, previous, backup, apply=False):
    client = client.resolve(strict=True)
    previous = str(previous).rstrip('\\/')
    if str(client).casefold() == previous.casefold():
        raise ValueError('Expected different old and current client roots')
    root = client / 'Mods/GraphicsOverhaul'
    queue_path = root / 'queued.json'
    state_path = root / 'installed.json'
    queue, state = read_json(queue_path), read_json(state_path)
    package_id = queue['packageId']
    if not re.fullmatch('[a-f0-9]{16}', package_id):
        raise ValueError('Invalid package ID')
    package = root / 'packages' / package_id
    manifest_path = package / 'manifest.json'
    manifest = read_json(manifest_path)
    if manifest['schema'] != 1 or manifest['packageId'] != package_id:
        raise ValueError('Package manifest mismatch')
    if state['packageId'] != package_id or state.get('restored'):
        raise ValueError('Expected the currently installed graphics package')
    for record in (queue, state, manifest):
        if record['clientRoot'].casefold() != previous.casefold():
            raise ValueError('Graphics records do not match the explicit previous root')
    seen = set()
    if not manifest['files']:
        raise ValueError('Empty package')
    for entry in manifest['files']:
        relative = entry['path']
        if (not re.fullmatch(r'(Levels/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\.pak|effects/effects_Textures\.pak)', relative)
                or relative.casefold() in seen):
            raise ValueError('Unexpected or duplicate archive path')
        seen.add(relative.casefold())
        expected = entry['staged']
        if not re.fullmatch('[a-f0-9]{64}', expected):
            raise ValueError('Invalid archive checksum')
        if digest(client / relative) != expected or digest(package / relative) != expected:
            raise ValueError('Installed/package archive mismatch: ' + relative)
    pending = {}
    for path, record in ((queue_path, queue), (state_path, state), (manifest_path, manifest)):
        record['clientRoot'] = str(client)
        pending[path] = (json.dumps(record, indent=2) + '\n').encode('utf-8')
    # Preserve recovery evidence. Only rebind a backup if it actually moved here.
    old_backup = state.get('backupRoot', '')
    if old_backup.casefold().startswith((previous + '\\').casefold()):
        relocated = client / old_backup[len(previous) + 1:]
        if relocated.is_dir():
            state['backupRoot'] = str(relocated)
            pending[state_path] = (json.dumps(state, indent=2) + '\n').encode('utf-8')
    ini_path = root / 'PostFX/ReShade.ini'
    ini = ini_path.read_text(encoding='utf-8-sig')
    keys = {'EffectSearchPaths', 'TextureSearchPaths', 'IntermediateCachePath', 'SavePath', 'PresetPath'}
    lines = []
    for line in ini.splitlines(keepends=True):
        key, separator, value = line.partition('=')
        if separator and key in keys and value.casefold().startswith((previous + '\\').casefold()):
            line = key + '=' + str(client) + value[len(previous):]
        lines.append(line)
    pending[ini_path] = ''.join(lines).encode('utf-8')
    # Refresh only existing records for the four metadata files, if registered.
    inventory_path = client / 'Mods/InstalledMods.json'
    inventory = read_json(inventory_path)
    inventory_changed = False
    for entries in inventory.get('features', {}).values():
        for entry in entries:
            target = client / entry['path']
            if target in pending:
                entry['installedSha256'] = hashlib.sha256(pending[target]).hexdigest()
                entry['bytes'] = len(pending[target])
                inventory_changed = True
    if inventory_changed:
        pending[inventory_path] = (json.dumps(inventory, indent=2) + '\n').encode('utf-8')
    originals = {path: path.read_bytes() for path in pending}
    preserved = client / 'bin64/Game.dll'
    native_hash = digest(preserved)
    receipt = {'clientRoot': str(client), 'previousRoot': previous,
               'verifiedInstalledArchives': len(seen), 'packageId': package_id,
               'gameDllSha256': native_hash, 'files': [],
               'checkedAt': datetime.now(timezone.utc).isoformat()}
    if apply:
        backup = backup.resolve()
        if backup.is_relative_to(client):
            raise ValueError('Backup must be outside the client')
        subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command',
                        "$ErrorActionPreference='Stop'; "
                        "$running=@(Get-CimInstance Win32_Process -ErrorAction Stop | "
                        "Where-Object { $_.Name -in @('aion.bin','aion.exe') }); "
                        "if ($running.Count -gt 0) { throw 'Exit Aion normally before repairing its graphics paths.' }"],
                       check=True, capture_output=True, text=True)
        backup.mkdir(parents=True, exist_ok=False)
        for path in pending:
            if path.read_bytes() != originals[path]:
                raise ValueError('Metadata changed during preflight')
            saved = backup / path.relative_to(client)
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, saved)
        try:
            for path, data in pending.items():
                path.write_bytes(data)
                if path.read_bytes() != data:
                    raise ValueError('Copied metadata mismatch')
                receipt['files'].append({'path': path.relative_to(client).as_posix(),
                                         'before': hashlib.sha256(originals[path]).hexdigest(),
                                         'after': hashlib.sha256(data).hexdigest()})
            if digest(preserved) != native_hash:
                raise ValueError('Native client changed during repair')
        except Exception:
            for path, data in originals.items():
                path.write_bytes(data)
            raise
        (backup / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--previous-root', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    repair(args.client, args.previous_root, args.backup, args.apply)
