"""Remove the rejected PB-REPAIR-FOLLOW-001 package using its exact hash-guarded receipt."""
import argparse
import copy
import json
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

from stage_companion_update import ROOT, DEV_ROOT, sha


def stopped():
    # The sandbox's Get-Process view can omit actual desktop processes. CIM must
    # succeed from the authorized host context; denial is never proof of shutdown.
    command = r"""$ErrorActionPreference='Stop'
$processes=@(Get-CimInstance Win32_Process)
$java=@($processes | Where-Object { $_.Name -match '^javaw?\.exe$' })
if (@($java | Where-Object { [string]::IsNullOrWhiteSpace($_.CommandLine) }).Count -gt 0) { throw 'Java command-line inspection incomplete' }
@($processes | Where-Object { ($_.Name -match '^javaw?\.exe$' -and $_.CommandLine -match 'com\.aionemu\.gameserver\.GameServer') -or $_.Name -match '^(aion|aionbin|game(?:client|server)?)\.(exe|bin)$' }).Count
"""
    result = subprocess.run(['pwsh', '-NoProfile', '-Command', command], check=True, capture_output=True, text=True)
    if result.stdout.strip() != '0':
        raise RuntimeError('Server/client running or state unconfirmed; removal requires both off.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    receipt = args.receipt.resolve()
    rejected = json.loads((receipt / 'manifest.json').read_text())
    server = ROOT / 'target-deploy/game-server'
    assert rejected['feature'] == 'playerbot-stuck-follower-recovery'
    assert Path(rejected['deployment']) == server
    prior = json.loads(Path(rejected['previousReceipt']).read_text())
    stopped()
    assert sha(server / 'libs/game-server-4.8-SNAPSHOT.jar') == rejected['baseJarSha256']
    for entry in rejected['files']:
        assert sha(server / entry['path']) == entry['installed'], 'Newer changes require incremental removal: ' + entry['path']
        assert sha(receipt / entry['path']) == entry['original'], 'Recovery backup changed: ' + entry['path']

    manifest = copy.deepcopy(prior)
    manifest.update(feature='playerbot-follow-recovery-removed', scope='PB-REPAIR-FOLLOW-001: removed by user request; restore pre-recovery navigation and unrelated UI', previousReceipt=str(receipt / 'manifest.json'), newClasses=[], removedClasses=rejected['newClasses'])
    manifest['files'] = [dict(path=e['path'], original=e['installed'], installed=e['original']) for e in rejected['files']]
    backup = DEV_ROOT / 'archives/server/game-server/backups' / ('playerbots-recruitment-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(parents=True)
    for e in manifest['files']:
        dest = backup / e['path']
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(server / e['path'], dest)
        assert sha(dest) == e['original']
    shutil.copytree(server / 'config/playerbots', backup / 'preferences-before')
    stopped()
    try:
        for e in manifest['files']:
            if e['original'] != e['installed']:
                shutil.copy2(receipt / e['path'], server / e['path'])
        assert all(sha(server / e['path']) == e['installed'] for e in manifest['files'])
    except Exception:
        for e in manifest['files']:
            if e['original'] != e['installed']:
                shutil.copy2(backup / e['path'], server / e['path'])
        (backup / 'failed-manifest.json').write_text(json.dumps(manifest, indent=2))
        raise
    (backup / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    (backup / 'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(), mode='server/client stopped; rejected recovery removed', files=manifest['files'], baseJarSha256=manifest['baseJarSha256']), indent=2))
    print('OK: rejected recovery and incidental UI change removed; prior baseline restored:', backup)


if __name__ == '__main__':
    main()
