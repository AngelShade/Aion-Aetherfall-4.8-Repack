"""Guarded original-client HUD delivery with an external backup and copy receipt."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
from build_plugin import COHORT, HERE, PRESERVE, ROOT, sha


def closed():
    # The restricted Get-Process view can omit a running elevated Aion process.
    # CIM must succeed; an inaccessible full process list is never closure proof.
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command',
        "$ErrorActionPreference='Stop'; Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -match '^(aion\\.bin|aion\\.exe|Aetherfall Launcher\\.exe)$' } | Select-Object -ExpandProperty Name"],
        check=True, capture_output=True, text=True)
    if result.stdout.strip():
        raise RuntimeError('Close Aion and its launcher normally first: '+result.stdout.strip())


def compatibility(client, work):
    """Run the installed launcher's real feature loaders on an external copy."""
    mirror = work/'compatibility-client'
    mirror.mkdir()
    interface = json.loads((client/'Mods/AetherfallUI/manifest.json').read_text(encoding='utf-8-sig'))
    graphics = json.loads((client/'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
    paths = set(interface['archives']) | set(interface['files']) | {'Aetherfall Launcher.exe', 'Data/Quest/Quest.pak', 'L10N/enu/data/data.pak'}
    paths |= {'Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json'}
    paths |= {entry['path'] for entry in graphics['files']}
    paths |= {'Mods/GraphicsMenu/package/'+entry['path'] for entry in graphics['files']}
    for rel in paths:
        target = mirror/rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(client/rel, target)
    for rel in ['Mods/CustomQuests', 'Mods/AetherfallUI']:
        shutil.copytree(client/rel, mirror/rel)
    protected = {rel: sha(mirror/rel) for rel in paths if rel.lower().endswith(('.pak', '.dll', '.exe'))}
    results = {}
    for name, flag in [('custom-quests', '--custom-quests-sync'), ('interface', '--aetherfall-interface')]:
        report = work/(name+'.txt')
        subprocess.run([str(mirror/'Aetherfall Launcher.exe'), flag, '--client', str(mirror), '--report', str(report)], check=True)
        results[name] = report.read_text(encoding='utf-8-sig')
        for rel, expected in protected.items():
            if sha(mirror/rel) != expected:
                raise RuntimeError(f'Existing {name} requires reconciliation: {rel} changed in the verification copy')
    # VerifyOnly is the installed Graphics menu's read-only ownership guard.
    result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(client/'Mods/GraphicsMenu/Install-GraphicsMenu.ps1'),
                             '-ClientPath', str(client), '-VerifyOnly'], check=True, capture_output=True, text=True)
    results['graphics'] = result.stdout.strip()
    (work/'compatibility.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    return results


def install(stage, client, backup, resume=False):
    manifest = json.loads((stage/'manifest.json').read_text(encoding='utf-8'))
    if manifest['feature'] != 'aetherfall-hud' or Path(manifest['clientRoot']).resolve() != client:
        raise ValueError('This HUD build belongs to another client')
    if [e['path'] for e in manifest['files']] != COHORT or set(manifest['preserved']) != set(PRESERVE):
        raise ValueError('Unexpected HUD delivery cohort')
    if (backup.exists() and not resume) or client == backup or client in backup.parents or ROOT == backup or ROOT in backup.parents:
        raise ValueError('Use a fresh external backup folder')
    if resume and ((backup/'receipt.json').exists() or json.loads((backup/'manifest.json').read_text()) != manifest):
        raise ValueError('Resume requires the same uninstalled, backed-up package')
    for name, expected in manifest['sources'].items():
        if sha(HERE/name) != expected:
            raise ValueError('Maintained source changed; rebuild before delivery: '+name)

    def baseline():
        for e in manifest['files']:
            if sha(client/e['path']) != e['original'] or sha(stage/e['path']) != e['staged']:
                raise ValueError('Client or package drift: '+e['path'])
        for rel, expected in manifest['preserved'].items():
            if sha(client/rel) != expected:
                raise ValueError('Preserved client dependency changed: '+rel)

    closed()
    baseline()
    backup.mkdir(parents=True, exist_ok=resume)
    if not resume:
        shutil.copy2(stage/'manifest.json', backup/'manifest.json')
    for e in manifest['files']:
        target = backup/'files'/e['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        if not resume:
            shutil.copy2(client/e['path'], target)
        if sha(target) != e['original']:
            raise RuntimeError('Backup mismatch: '+e['path'])
    checks = json.loads((backup/'compatibility.json').read_text()) if resume else compatibility(client, backup)
    closed()
    baseline()
    # Refresh this feature's inventory from the actual installation before copying.
    inventory_path = ROOT/'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text(encoding='utf-8-sig'))
    live = {e['path'].lower(): e['original'] for e in manifest['files']}
    live.update({p.lower(): value for p, value in manifest['preserved'].items()})
    for entry in inventory['clientFiles']:
        if entry['path'].lower() in live:
            entry['sha256'] = live[entry['path'].lower()]
    inventory_path.write_text(json.dumps(inventory, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    try:
        for e in manifest['files']:
            shutil.copy2(stage/e['path'], client/e['path'])
            if sha(client/e['path']) != e['staged']:
                raise RuntimeError('Installed hash mismatch: '+e['path'])
        subprocess.run(['java', str(HERE.parent/'season-pass/VerifySignatures.java'), str(client), str(client)], check=True)
        for rel, expected in manifest['preserved'].items():
            if sha(client/rel) != expected:
                raise RuntimeError('Preserved file changed: '+rel)
    except Exception:
        closed()
        for e in manifest['files']:
            shutil.copy2(backup/'files'/e['path'], client/e['path'])
        raise
    receipt = dict(feature='aetherfall-hud', installedAt=datetime.now(timezone.utc).isoformat(),
                   clientRoot=str(client), build=str(stage), backup=str(backup/'files'),
                   files=manifest['files'], preserved=manifest['preserved'], compatibility=checks,
                   acceptance='Pending owner client launch, movers/relog, frames, cast, pet, Additional Functions and companion checks',
                   rollback='With the client and launcher closed, copy the entire files/ cohort back over the original client.')
    (backup/'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    inventory = json.loads(inventory_path.read_text(encoding='utf-8-sig'))
    changed = {e['path'].lower(): e['staged'] for e in manifest['files']}
    for entry in inventory['clientFiles']:
        if entry['path'].lower() in changed:
            entry['sha256'] = changed[entry['path'].lower()]
    inventory['lastAetherfallHUDDelivery'] = dict(date='2026-10-19', receipt=str(backup/'receipt.json'),
        build=str(stage), source='client-mods/aetherfall-ui', files=changed, compatibility=checks,
        limits=manifest['limitations'], gameplayAcceptance=receipt['acceptance'], repackChanged=False)
    inventory.setdefault('clientReceipts', []).append(str(backup/'receipt.json'))
    inventory_path.write_text(json.dumps(inventory, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    print(json.dumps(checks, indent=2))
    print('Installed five copied/hash-verified files; original model key, native binaries and UI/quest archives preserved.')
    print('Receipt:', backup/'receipt.json')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage', type=Path, required=True)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--backup', type=Path, required=True)
    p.add_argument('--resume', action='store_true', help='Reuse a matching external backup and completed compatibility checks')
    a = p.parse_args()
    install(a.stage.resolve(), a.client.resolve(), a.backup.resolve(), a.resume)
