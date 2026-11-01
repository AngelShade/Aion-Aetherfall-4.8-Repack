"""Add one quest package to an installed client without stopping Play.

The launcher merges a new quest into English data.pak, whose exact hash the Graphics
menu records, so Play would otherwise stop at the Graphics step. With Aion closed this
copies the package, runs the launcher's normal merge, moves the Graphics record and its
package copy to the merged archive, and runs the standing guards. Any failure restores
every saved file. A receipt with paths and hashes is written to the backup folder.
"""
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ENGLISH='L10N/enu/Data/data.pak'
QUESTS='Data/Quest/Quest.pak'
RECORDS=('Mods/GraphicsMenu/installed.json','Mods/GraphicsMenu/package/manifest.json')
PACKAGE_COPY='Mods/GraphicsMenu/package/'+ENGLISH
STATE='Mods/CustomQuests/installed.json'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


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
    a=p.parse_args()
    client,package,backup=a.client.resolve(strict=True),a.package.resolve(strict=True),a.backup.resolve()
    if backup.exists() or backup.is_relative_to(client):
        raise SystemExit('Use a fresh external backup folder')
    target=client/'Mods/CustomQuests/Quests'/package.name
    if target.exists():
        raise SystemExit('This package is already in the client')
    closed()
    saved=(ENGLISH,QUESTS,PACKAGE_COPY,STATE)+RECORDS
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
        closed()
        shutil.copytree(package,target)
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
        shutil.rmtree(target,ignore_errors=True)
        for name in saved:
            shutil.copy2(backup/'files'/name,client/name)
        if {name:sha(client/name) for name in saved}!=before:
            raise SystemExit(f'Install failed and restore did not verify; copy {backup/"files"} by hand. {error}')
        raise SystemExit(f'Install failed; every file was restored. {error}')
    receipt=dict(change='Added custom quest package',package=package.name,installedAt=datetime.now(timezone.utc).isoformat(),client=str(client),
        before=before,after={name:sha(client/name) for name in saved},newFiles=[str(path.relative_to(client)) for path in target.rglob('*') if path.is_file()],checks=checks,
        restore='With Aion closed: copy backup/files over the client and delete the new package folder.')
    (backup/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    print(json.dumps(checks,indent=2))
    print('receipt:',backup/'receipt.json')


if __name__=='__main__':
    main()
