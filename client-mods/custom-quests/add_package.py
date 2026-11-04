"""Add one quest package to an installed client without stopping Play.

The launcher merges a new quest into English data.pak, whose exact hash the Graphics
menu records, so Play would otherwise stop at the Graphics step. With Aion closed this
copies the package, runs the launcher's normal merge, moves the Graphics record and its
package copy to the merged archive, and runs the standing guards. Any failure restores
every saved file. A receipt with paths and hashes is written to the backup folder.
For offline verification, --staged-client accepts only a receipt-guarded TEMP copy
without an Aion executable. It exercises the same merge and compatibility guards;
normal client installation always requires an accessible closure check.
"""
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ENGLISH='L10N/enu/Data/data.pak'
QUESTS='Data/Quest/Quest.pak'
RECORDS=('Mods/GraphicsMenu/installed.json','Mods/GraphicsMenu/package/manifest.json')
PACKAGE_COPY='Mods/GraphicsMenu/package/'+ENGLISH
STATE='Mods/CustomQuests/installed.json'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def staged_client(client):
    # This explicit verification mode can only write a disposable TEMP copy that
    # cannot run Aion. Real installations always retain the closure requirement.
    if not client.is_relative_to(Path(tempfile.gettempdir()).resolve()):
        raise SystemExit('--staged-client requires a disposable TEMP client copy')
    marker=client/'.aetherfall-quest-stage.json'
    if not marker.is_file():
        raise SystemExit('The staged client receipt is missing')
    state=json.loads(marker.read_text(encoding='utf-8'))
    if state.get('kind')!='custom-quest-installer-scratch' or Path(state['originalClient']).resolve()==client:
        raise SystemExit('Invalid staged client receipt')
    if any(path.name.lower() in ('aion.exe','aion.bin') for path in client.rglob('*') if path.is_file()):
        raise SystemExit('A runnable Aion client cannot use --staged-client')
    for name,expected in state['files'].items():
        target=(client/name).resolve(strict=True)
        if not target.is_relative_to(client) or sha(target)!=expected:
            raise SystemExit('Staged client input changed: '+name)


def closed():
    query="$ErrorActionPreference='Stop'; Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe','Aetherfall Launcher.exe') } | Select-Object -ExpandProperty Name"
    result=subprocess.run(['powershell.exe','-NoProfile','-Command',query],capture_output=True,text=True,check=True)
    if result.stdout.strip():
        raise SystemExit('Close Aion and its launcher normally first: '+result.stdout.strip())


def launcher(client,work,flag):
    report=work/(flag.strip('-')+'.txt')
    code=subprocess.run([str(client/'Aetherfall Launcher.exe'),flag,'--client',str(client),'--report',str(report)]).returncode
    return code,report.read_text(encoding='utf-8-sig',errors='replace').strip() if report.exists() else ''


def graphics(client):
    shell=shutil.which('pwsh.exe') or 'powershell.exe'
    result=subprocess.run([shell,'-NoProfile','-ExecutionPolicy','Bypass','-File',str(client/'Mods/GraphicsMenu/Install-GraphicsMenu.ps1'),'-ClientPath',str(client),'-VerifyOnly'],capture_output=True,text=True)
    return result.returncode,(result.stdout+result.stderr).strip()


def rebase(client,before,after):
    # Hashes are plain ASCII in these records, and either case may be stored.
    for name in RECORDS:
        path=client/name
        data=path.read_bytes()
        for old,new in ((before,after),(before.upper(),after.upper())):
            data=data.replace(old.encode(),new.encode())
        if after.encode() not in data.lower():
            raise RuntimeError('The Graphics record does not hold data.pak: '+name)
        path.write_bytes(data)
    shutil.copy2(client/ENGLISH,client/PACKAGE_COPY)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True)
    p.add_argument('--package',type=Path,required=True)
    p.add_argument('--backup',type=Path,required=True,help='fresh folder outside the client and source')
    p.add_argument('--replace',action='store_true',help='update this existing package with verified backup and rollback')
    p.add_argument('--staged-client',action='store_true',help='verify on a receipt-guarded disposable TEMP copy without runnable Aion files; never for a real client')
    a=p.parse_args()
    client,package,backup=a.client.resolve(strict=True),a.package.resolve(strict=True),a.backup.resolve()
    if backup.exists() or backup.is_relative_to(client) or backup.is_relative_to(Path(__file__).resolve().parents[2]):
        raise SystemExit('Use a fresh external backup folder')
    target=client/'Mods/CustomQuests/Quests'/package.name
    package_root=(client/'Mods/CustomQuests/Quests').resolve()
    if not package_root.is_relative_to(client) or not target.resolve().is_relative_to(package_root):
        raise SystemExit('The package target escapes CustomQuests/Quests')
    replacing=target.is_dir()
    if target.exists() and not a.replace:
        raise SystemExit('This package is already in the client')
    if a.replace and not replacing:
        raise SystemExit('--replace requires an existing package directory')
    if a.staged_client: staged_client(client)
    else: closed()
    saved=(ENGLISH,QUESTS,PACKAGE_COPY,STATE)+RECORDS+tuple(path.relative_to(client).as_posix() for path in target.rglob('*') if path.is_file())
    added=[(target/path.relative_to(package)).relative_to(client).as_posix() for path in package.rglob('*') if path.is_file() and not (target/path.relative_to(package)).exists()]
    before={name:sha(client/name) for name in saved}
    if before[ENGLISH]!=before[PACKAGE_COPY] or graphics(client)[0]:
        raise SystemExit('The Graphics record does not match this client; fix that first')
    backup.mkdir(parents=True)
    for name in saved:
        copy=backup/'files'/name
        copy.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(client/name,copy)
        if sha(copy)!=before[name]:
            raise SystemExit('Backup verification failed: '+name)
    checks={}
    try:
        if not a.staged_client: closed()
        shutil.copytree(package,target,dirs_exist_ok=replacing)
        checks['merge']=launcher(client,backup,'--custom-quests-sync')
        if checks['merge'][0]:
            raise RuntimeError(checks['merge'][1])
        merged=sha(client/ENGLISH)
        if merged==before[ENGLISH]:
            raise RuntimeError('The merge did not change data.pak')
        rebase(client,before[ENGLISH],merged)
        checks['graphics']=graphics(client)
        checks['unchanged']=launcher(client,backup,'--custom-quests-sync')
        checks['interface']=launcher(client,backup,'--aetherfall-interface')
        if any(code for code,_ in checks.values()) or 'unchanged' not in checks['unchanged'][1]:
            raise RuntimeError('A guard failed: '+json.dumps(checks))
    except Exception as error:
        if replacing:
            for name in added:
                if (client/name).is_file():
                    (client/name).unlink()
        else:
            resolved=target.resolve()
            if not resolved.is_relative_to(package_root) or resolved==package_root:
                raise SystemExit('Refusing rollback outside this package directory')
            shutil.rmtree(resolved,ignore_errors=True)
        for name in saved:
            shutil.copy2(backup/'files'/name,client/name)
        if {name:sha(client/name) for name in saved}!=before:
            raise SystemExit(f'Install failed and restore did not verify; copy {backup/"files"} by hand. {error}')
        raise SystemExit(f'Install failed; every file was restored. {error}')
    receipt=dict(change='Updated custom quest package' if replacing else 'Added custom quest package',package=package.name,installedAt=datetime.now(timezone.utc).isoformat(),client=str(client),stagedClient=a.staged_client,
        before=before,after={name:sha(client/name) for name in saved},newFiles=added,checks=checks,
        restore='With Aion closed: copy backup/files over the client; remove only the listed newFiles.' if replacing else 'With Aion closed: copy backup/files over the client and delete the new package folder.')
    (backup/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    print(json.dumps(checks,indent=2))
    print('receipt:',backup/'receipt.json')


if __name__=='__main__':
    main()
