"""Hash-guarded incremental native repair install/rollback; requires Aion closed."""
import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

DEV_ROOT=Path(os.environ.get('AION_DEV_ROOT','D:/Proiecte/Project Restructure/Aion Development Workspace')).resolve()
CLIENT=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA').resolve()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
def target(root,name):
    path=(root/name).resolve()
    if not path.is_relative_to(root) or path==root:raise ValueError('Path escapes installation')
    return path
def replace(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    temporary=path.with_name(path.name+'.window-queue.tmp')
    temporary.write_bytes(data);os.replace(temporary,path)
def closed():
    # Fail closed if enumeration fails. A missing Path is not shutdown evidence.
    command="$ErrorActionPreference='Stop'; try { $p=@(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe') }); if($p.Count){exit 2} } catch { exit 3 }"
    result=subprocess.run(['powershell.exe','-NoProfile','-Command',command],capture_output=True,text=True)
    if result.returncode==2:raise ValueError('Fully close every Aion client before native file replacement')
    if result.returncode:raise ValueError('Process inspection failed; installation refused')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True)
    p.add_argument('--verify-only',action='store_true');p.add_argument('--restore',type=Path)
    a=p.parse_args();package=a.package.resolve()
    if not package.is_relative_to(DEV_ROOT/'staging/output'):raise ValueError('Expected external staged package')
    m=json.loads((package/'manifest.json').read_text());root=Path(m['clientRoot']).resolve()
    prefixes={'custom-window-queue-v1':'window-queue','remember-login-return-v2':'remember-login-return','remember-login-reconnect-v3':'remember-login-return','marketplace-session-v1':'marketplace-session'}
    if m['feature'] not in prefixes or root!=CLIENT:raise ValueError('Wrong feature or installation')
    name=m['graphicsCompatibility']['backupName']
    if not re.fullmatch(r'service-menu-graphics-\d{8}-\d{6}-\d{6}',name):raise ValueError('Invalid graphics recovery path')
    backup='DXVK-backups/'+name
    allowed={'bin64/Game.dll','DXVK/installed.json','DXVK/graphics-menu/installed.json','DXVK/graphics-menu/package/manifest.json',
             'DXVK/graphics-menu/package/bin64/Game.dll','DXVK/graphics-menu/package/Data/ui/game/game.pak',
             backup+'/bin64/Game.dll',backup+'/Data/ui/game/game.pak',backup+'/cursor-base/bin64/Game.dll'}
    if m['graphicsCompatibility']['localized']:allowed.update({'DXVK/graphics-menu/package/L10N/enu/Data/data.pak',backup+'/L10N/enu/Data/data.pak'})
    if m['feature'] in ('remember-login-return-v2','remember-login-reconnect-v3'):allowed.add('bin64/AionRememberLogin.dll')
    if len(m['files'])!=len(allowed) or {e['path'] for e in m['files']}!=allowed:raise ValueError('Unexpected transaction scope')
    def preserve():
        for e in m['preservedFiles']:
            if sha(target(root,e['path']))!=e['sha256']:raise ValueError('Preserved resource changed: '+e['path'])
    preserve()
    archive_root=DEV_ROOT/'archives/client'
    if a.restore:
        receipt=a.restore.resolve()
        if not receipt.is_relative_to(archive_root) or json.loads((receipt/'manifest.json').read_text())!=m:raise ValueError('Wrong recovery receipt')
        for e in m['files']:
            if sha(target(root,e['path']))!=e['staged']:raise ValueError('Later installed change; restore refused')
            if e['original'] is not None and sha(target(receipt,e['path']))!=e['original']:raise ValueError('Recovery payload changed')
        if a.verify_only:print('OK: restore hashes verified; no files changed');return
        closed()
        for e in reversed(m['files']):
            path=target(root,e['path'])
            if e['original'] is None:path.unlink()
            else:replace(path,target(receipt,e['path']).read_bytes())
        preserve();assert all(sha(target(root,e['path']))==e['original'] for e in m['files'])
        print('OK: restored original transaction hashes');return
    for e in m['files']:
        if sha(target(root,e['path']))!=e['original'] or sha(target(package,e['path']))!=e['staged']:raise ValueError('File changed since staging: '+e['path'])
    if a.verify_only:print('OK: current/staged/preserved hashes pass; no installed files changed');return
    closed()
    receipt=archive_root/(prefixes[m['feature']]+'-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));receipt.mkdir(parents=True)
    (receipt/'manifest.json').write_text(json.dumps(m,indent=2))
    for e in m['files']:
        if e['original'] is not None:
            saved=target(receipt,e['path']);saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target(root,e['path']),saved)
            if sha(saved)!=e['original']:raise ValueError('Recovery copy verification failed')
    written=[]
    try:
        closed()
        for e in m['files']:
            if sha(target(root,e['path']))!=e['original']:raise ValueError('Concurrent client change')
            replace(target(root,e['path']),target(package,e['path']).read_bytes());written.append(e)
            assert sha(target(root,e['path']))==e['staged']
        preserve()
    except BaseException:
        for e in reversed(written):
            path=target(root,e['path'])
            if e['original'] is None:path.unlink()
            else:replace(path,target(receipt,e['path']).read_bytes())
        raise
    print('OK: installed',m['feature'],'and synchronized graphics/cursor restore records; receipt:',receipt)
if __name__=='__main__':main()
