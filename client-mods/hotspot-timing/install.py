"""Verify and deliver a prepared hotspot timer cohort to a closed client."""
import argparse,json,shutil,subprocess
from datetime import datetime,timezone
from pathlib import Path
from prepare import ROOT,sha,patch,compatibility_paths

def digest(path):
    return sha(path.read_bytes()) if path.is_file() else None

def within(root,relative):
    result=(root/relative).resolve()
    if result==root or root not in result.parents:
        raise ValueError('Payload path escapes its root')
    return result

def closed(client):
    command="Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe') } | Select-Object ProcessId,ExecutablePath | ConvertTo-Json -Compress"
    result=subprocess.run(['powershell.exe','-NoProfile','-Command',command],capture_output=True,text=True,check=True)
    processes=json.loads(result.stdout) if result.stdout.strip() else []
    if isinstance(processes,dict):processes=[processes]
    for process in processes:
        executable=process.get('ExecutablePath')
        if not executable or client in Path(executable).resolve().parents:
            raise ValueError('Selected client is running or its process path cannot be verified; close normally')

def deliver(client,prepared,backup,verify_only=False):
    client=client.resolve();prepared=prepared.resolve();backup=backup.resolve()
    manifest=json.loads((prepared/'manifest.json').read_text(encoding='utf-8'))
    if manifest['feature'] not in ('native-hotspot-timing','native-hotspot-server-timing') or Path(manifest['clientRoot']).resolve()!=client:
        raise ValueError('Wrong prepared client cohort')
    allowed={'bin64/Game.dll'}|compatibility_paths(manifest['compatibility'])
    if 'statue' in manifest:
        expected_archives={'Data/NPCs/NPCs.pak','Data/FlightPath/FlightPath.pak','Data/World/World.pak','Data/Strings/Strings.pak','L10N/enu/Data/data.pak'}
        if set(manifest['statue']['archives'])!=expected_archives:raise ValueError('Unexpected statue resource cohort')
        allowed|=expected_archives
    files=manifest['files']
    if len({f['path'] for f in files})!=len(files):raise ValueError('Duplicate payload entry')
    for source,expected in manifest['sourceHashes'].items():
        if digest(Path(source))!=expected:raise ValueError('Maintained source changed since preparation')
    for entry in files:
        rel=entry['path']
        if rel not in allowed:raise ValueError('Unexpected compatibility payload')
        if digest(within(prepared,rel))!=entry['installed']:raise ValueError('Prepared file changed: '+rel)
        if digest(within(client,rel))!=entry['original']:raise ValueError('Installed file changed: '+rel)
    for entry in manifest['preserved']:
        if digest(within(client,entry['path']))!=entry['sha256']:raise ValueError('Preserved feature changed')
    original=(client/'bin64/Game.dll').read_bytes()
    if manifest['feature']=='native-hotspot-server-timing':
        from prepare_dynamic import build_patch
        expected,changes=build_patch(original);proof=manifest['nativeProof']
    else:
        expected,changes=patch(original,manifest['castMilliseconds']);proof=manifest['patches']
    if sha(original)!=manifest['originalGameSha256'] or sha(expected)!=manifest['gameSha256'] or changes!=proof:
        raise ValueError('Native timer proof differs from preparation')
    if digest(prepared/'bin64/Game.dll')!=sha(expected):raise ValueError('Native payload does not match exact patch')
    if verify_only:
        print('PASS: native timer proof, current source, affected compatibility cohort and',len(manifest['preserved']),'preserved feature files; no client writes.')
        return
    if backup.exists() or client==backup or client in backup.parents or ROOT==backup or ROOT in backup.parents:
        raise ValueError('Use a fresh external backup directory')
    closed(client)
    backup.mkdir(parents=True)
    shutil.copy2(prepared/'manifest.json',backup/'prepared-manifest.json')
    # Back up every existing affected file before any client mutation.
    for entry in files:
        if entry['original'] is not None:
            target=within(backup/'before',entry['path']);target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(within(client,entry['path']),target)
            if digest(target)!=entry['original']:raise ValueError('Backup hash mismatch')
    closed(client)
    copied=[]
    try:
        for entry in files:
            target=within(client,entry['path']);target.parent.mkdir(parents=True,exist_ok=True)
            copied.append(entry)
            shutil.copy2(within(prepared,entry['path']),target)
            if digest(target)!=entry['installed']:raise ValueError('Copied file hash mismatch')
        for entry in manifest['preserved']:
            if digest(within(client,entry['path']))!=entry['sha256']:raise ValueError('Preserved feature changed during delivery')
    except Exception:
        # Retain newly created recovery images; restore only existing affected files.
        for entry in reversed(copied):
            if entry['original'] is not None:
                shutil.copy2(within(backup/'before',entry['path']),within(client,entry['path']))
                if digest(within(client,entry['path']))!=entry['original']:raise RuntimeError('Rollback hash mismatch')
        raise
    manifest.update(installedAt=datetime.now(timezone.utc).isoformat(),receipt=str(backup/'receipt.json'),
                    installerSha256=digest(Path(__file__)),serverChanged=False,repackChanged=False,copiedHashesVerified=True)
    (backup/'receipt.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('Installed original-client hotspot timer:',manifest['feature'],'; all',len(files),'copied hashes and',len(manifest['preserved']),'preserved feature files verified. Receipt:',backup/'receipt.json')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True);p.add_argument('--prepared',type=Path,required=True)
    p.add_argument('--backup',type=Path,required=True);p.add_argument('--verify-only',action='store_true')
    a=p.parse_args();deliver(a.client,a.prepared,a.backup,a.verify_only)
