"""Guarded original-only delivery of The Unbroken Field's prepared resources."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    temporary = path.with_name(path.name + '.writing-on-wall.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def closed():
    query = """$ErrorActionPreference='Stop'
$processes = @(Get-CimInstance Win32_Process -ErrorAction Stop)
$affected = @($processes | Where-Object {
    $_.Name -match '^(aion\\.bin|aion\\.exe|Aetherfall Launcher\\.exe)$' -or
    ($_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -match 'com\\.aionemu\\.gameserver')
} | Select-Object ProcessId,Name)
[pscustomobject]@{checkedAt=[DateTime]::UtcNow.ToString('o');enumerationSucceeded=$true;affected=$affected} | ConvertTo-Json -Compress
"""
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', query], capture_output=True, text=True, check=True)
    evidence = json.loads(result.stdout)
    assert evidence['enumerationSucceeded'] and not evidence['affected'], 'Close the original client and GameServer normally before delivery'
    return evidence


def install(stage, backup):
    manifest = json.loads((stage / 'manifest.json').read_text())
    assert manifest['feature'] == 'the-unbroken-field' and manifest['originalOnly'] and not manifest['installed']
    client = Path(manifest['client']).resolve(strict=True)
    server = (ROOT / 'target-deploy/game-server').resolve(strict=True)
    assert not backup.exists() and not backup.is_relative_to(ROOT) and not backup.is_relative_to(client)
    for name, expected in manifest['sourceHashes'].items():
        assert sha(ROOT / name) == expected, 'Maintained input changed: ' + name
    for name, expected in manifest['liveInputs'].items():
        path = Path(name)
        assert (sha(path) if path.exists() else None) == expected, 'Installed input changed: ' + name
    files = manifest['files']
    assert len(files) == 122 and len({str(Path(e['target']).resolve()).casefold() for e in files}) == len(files)
    for entry in files:
        target = Path(entry['target']).resolve()
        assert target.is_relative_to(client) or target.is_relative_to(server), 'Unexpected destination'
        assert sha(stage / entry['stage']) == entry['after']
        assert (sha(target) if target.exists() else None) == entry['before'], 'Target changed: ' + str(target)
    closure = closed()
    backup.mkdir(parents=True)
    receipt_path = backup / 'receipt.json'
    preserved = {str(server / 'libs/game-server-4.8-SNAPSHOT.jar'): sha(server / 'libs/game-server-4.8-SNAPSHOT.jar')}
    targets = {str(Path(e['target'])).casefold() for e in files}
    for name, expected in manifest['liveInputs'].items():
        if name.casefold() not in targets:
            preserved[name] = expected
    records = []
    for index, entry in enumerate(files):
        record = dict(entry, backup=None)
        if entry['before'] is not None:
            saved = backup / 'files' / str(index)
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(entry['target'], saved)
            assert sha(saved) == entry['before'], 'Backup verification failed'
            record['backup'] = str(saved)
        records.append(record)
    receipt = dict(feature=manifest['feature'], status='prepared', originalOnly=True, questIds=manifest['questIds'],
                   build=manifest['build'], stage=str(stage), files=records, closure=closure, preserved=preserved,
                   sourceHashes=manifest['sourceHashes'], deliveryScriptSha256=sha(Path(__file__)),
                   validation=manifest['validation'], componentsStarted=False, coreChanged=False,
                   serverJarChanged=False, repackChanged=False, sqlExecuted=False, gameplayVerified=False)
    save(receipt_path, receipt)
    receipt['closureBeforeCopy'] = closed()
    copied = []
    try:
        for record in records:
            target = Path(record['target'])
            assert (sha(target) if target.exists() else None) == record['before'], 'Target changed during preparation'
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(stage / record['stage'], target)
            assert sha(target) == record['after'], 'Copied hash mismatch: ' + str(target)
            copied.append(record)
        spec = importlib.util.spec_from_file_location('standing_client_checks', ROOT / 'client-mods/aetherfall-core/install.py')
        checks = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(checks)
        receipt['clientChecks'] = checks.checks(client, backup)
        assert all(check['rc'] == 0 for check in receipt['clientChecks'].values()), 'Client compatibility check failed'
        assert receipt['clientChecks']['custom-quests']['output'] == 'Custom quests unchanged (17).', 'Quest loader performed an unexpected second merge'
        assert all(sha(Path(record['target'])) == record['after'] for record in records), 'A check changed a delivered resource'
        assert all((sha(Path(name)) if Path(name).exists() else None) == expected for name, expected in preserved.items()), 'Preservation hash changed'
        receipt.update(status='installed', installedAt=datetime.now(timezone.utc).isoformat(), copiedHashesVerified=len(records))
        save(receipt_path, receipt)
    except Exception as error:
        receipt.update(status='failed', error=str(error), copiedTargets=[record['target'] for record in copied])
        save(receipt_path, receipt)
        closed()
        for record in reversed(copied):
            target = Path(record['target'])
            assert sha(target) == record['after'], 'Rollback stopped on an unexpected newer file: ' + str(target)
            if record['backup']:
                shutil.copy2(record['backup'], target)
                assert sha(target) == record['before']
            else:
                target.unlink()
        receipt['status'] = 'rolled-back'
        save(receipt_path, receipt)
        raise
    inventory_path = ROOT / 'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text())
    by_name = {entry['path'].casefold(): entry for entry in inventory['clientFiles']}
    for record in records:
        target = Path(record['target'])
        if not target.is_relative_to(client):
            continue
        name = target.relative_to(client).as_posix()
        entry = by_name.get(name.casefold())
        if entry is None:
            entry = dict(path=name)
            inventory['clientFiles'].append(entry)
        entry['sha256'] = record['after']
    summary = dict(installedAt=receipt['installedAt'], originalOnly=True, receipt=str(receipt_path),
                   build=manifest['build'], questIds=manifest['questIds'], title='The Unbroken Field',
                   prerequisite=1198, startNpc='Spatalos', installedFiles=len(records), coreChanged=False,
                   serverJarChanged=False, existing3999RecoveryRestored=True,
                   verification='Normal isolated Maven assembly; 16 handlers compiled against installed APIs; native quest schema; actual existing loader active/restore merges; native record/compressed-member preservation; full CIM closure; 122 copied hashes; standing client compatibility checks',
                   gameplayAcceptance='Owner: full chain, markers/links, shared objects, partial-count relog, abandon/reaccept, reward claims and final closure pending')
    inventory['lastUnbrokenFieldDelivery'] = summary
    for key in ('clientReceipts', 'serverReceipts'):
        if str(receipt_path) not in inventory[key]:
            inventory[key].append(str(receipt_path))
    inventory['serverJarSha256'] = preserved[str(server / 'libs/game-server-4.8-SNAPSHOT.jar')]
    save(inventory_path, inventory)
    manifest.update(installed=True, receipt=str(receipt_path))
    save(stage / 'manifest.json', manifest)
    print('Installed The Unbroken Field: 16 quests, 122 copied hashes verified; existing server core and client mods preserved. Receipt: ' + str(receipt_path))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    args = parser.parse_args()
    install(args.stage.resolve(strict=True), args.backup.resolve())
