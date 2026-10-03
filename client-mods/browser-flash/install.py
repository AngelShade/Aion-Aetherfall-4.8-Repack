"""Install/restore only the verified passive diagnostic, with Aion closed."""
import argparse
import ctypes as c
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def closed():
    class Entry(c.Structure):
        _fields_=[('size',c.c_ulong),('usage',c.c_ulong),('pid',c.c_ulong),('heap',c.c_size_t),('module',c.c_ulong),('threads',c.c_ulong),('parent',c.c_ulong),('priority',c.c_long),('flags',c.c_ulong),('exe',c.c_wchar*260)]
    k=c.WinDLL('kernel32',use_last_error=True)
    k.CreateToolhelp32Snapshot.argtypes=[c.c_ulong,c.c_ulong];k.CreateToolhelp32Snapshot.restype=c.c_void_p
    k.Process32FirstW.argtypes=[c.c_void_p,c.POINTER(Entry)];k.Process32NextW.argtypes=k.Process32FirstW.argtypes;k.CloseHandle.argtypes=[c.c_void_p]
    handle=k.CreateToolhelp32Snapshot(2,0)
    if handle in (None,c.c_void_p(-1).value):raise c.WinError(c.get_last_error())
    try:
        entry=Entry();entry.size=c.sizeof(entry);ok=k.Process32FirstW(handle,c.byref(entry))
        while ok:
            if entry.exe.lower() in ['aion.bin','aion.exe']:raise ValueError('Close Aion normally before replacing loaded DLLs')
            ok=k.Process32NextW(handle,c.byref(entry))
    finally:k.CloseHandle(handle)

def replace(path,data):
    fd,name=tempfile.mkstemp(prefix='browser-probe-',suffix='.tmp',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as f:f.write(data)
        os.replace(name,path)
    finally:
        if Path(name).exists():Path(name).unlink()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--staged',type=Path,required=True);p.add_argument('--restore',type=Path);args=p.parse_args()
    staged=args.staged.resolve();manifest=json.loads((staged/'manifest.json').read_text(encoding='utf-8'));client=Path(manifest['client']).resolve();binpath=client/'bin64'
    closed()
    if args.restore:
        backup=args.restore.resolve()
        if not backup.is_relative_to(client/'BrowserFrame-backups'):raise ValueError('Backup outside diagnostic backup directory')
        receipt=json.loads((backup/'receipt.json').read_text(encoding='utf-8'))
        if receipt['originalGameSha256']!=manifest['originalGameSha256'] or sha(backup/'Game.dll')!=manifest['originalGameSha256']:raise ValueError('Unexpected backup')
        for name,digest in manifest['files'].items():
            if sha(binpath/name)!=digest:raise ValueError('Client changed after diagnostic installation: '+name)
        entries=receipt.get('graphicsTrackingFiles',[])
        tracking_backup=Path(receipt.get('graphicsTrackingBackup',str(backup))).resolve()
        if not tracking_backup.is_relative_to(client/'BrowserFrame-backups'):raise ValueError('Tracking backup outside diagnostic backups')
        for e in entries:
            live=client/e['path']
            if not live.resolve().is_relative_to(client):raise ValueError('Tracking target outside client')
            if sha(live)!=e['installed']:raise ValueError('Graphics tracking changed after diagnostic installation')
            if e['original'] is not None and sha(tracking_backup/e['path'])!=e['original']:raise ValueError('Tracking backup changed')
        replace(binpath/'Game.dll',(backup/'Game.dll').read_bytes())
        for e in reversed(entries):
            live=client/e['path']
            if e['original'] is None:live.unlink()
            else:replace(live,(tracking_backup/e['path']).read_bytes())
        (binpath/'AionBrowserProbe.dll').unlink()
        print('OK: diagnostic removed; exact original installed Game.dll restored');return
    if sha(binpath/'Game.dll')!=manifest['originalGameSha256'] or sha(binpath/'Awesomium.dll')!=manifest['awesomiumSha256']:raise ValueError('Client changed since staging; rebuild the diagnostic first')
    if (binpath/'AionBrowserProbe.dll').exists():raise ValueError('Existing diagnostic DLL; restore it first')
    for name,digest in manifest['files'].items():
        if name not in ['Game.dll','AionBrowserProbe.dll'] or sha(staged/'bin64'/name)!=digest:raise ValueError('Invalid staged diagnostic: '+name)
    entries=manifest.get('graphicsTrackingFiles',[])
    for e in entries:
        live=client/e['path']
        if not live.resolve().is_relative_to(client):raise ValueError('Tracking target outside client')
        current=sha(live) if live.exists() else None
        if current!=e['original'] or sha(staged/e['path'])!=e['installed']:raise ValueError('Graphics tracking input changed')
    preserved={f.name:sha(f) for f in binpath.glob('*.dll') if f.name.lower()!='game.dll'}
    backup=client/'BrowserFrame-backups'/datetime.now().strftime('%Y%m%d-%H%M%S-%f');backup.mkdir(parents=True,exist_ok=False)
    shutil.copy2(binpath/'Game.dll',backup/'Game.dll');assert sha(backup/'Game.dll')==manifest['originalGameSha256']
    for e in entries:
        if e['original'] is not None:
            target=backup/e['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(client/e['path'],target)
    (backup/'receipt.json').write_text(json.dumps(dict(**manifest,preservedDlls=preserved),indent=2),encoding='utf-8')
    applied=[]
    try:
        # Install the new dependency before pointing Game.dll at it.
        replace(binpath/'AionBrowserProbe.dll',(staged/'bin64/AionBrowserProbe.dll').read_bytes())
        closed();assert sha(binpath/'Game.dll')==manifest['originalGameSha256']
        replace(binpath/'Game.dll',(staged/'bin64/Game.dll').read_bytes())
        for e in entries:
            live=client/e['path'];live.parent.mkdir(parents=True,exist_ok=True);replace(live,(staged/e['path']).read_bytes());applied.append(e)
        for name,digest in manifest['files'].items():assert sha(binpath/name)==digest
        for e in entries:assert sha(client/e['path'])==e['installed']
        for name,digest in preserved.items():assert sha(binpath/name)==digest,name
    except Exception:
        for e in reversed(applied):
            live=client/e['path']
            if sha(live)!=e['installed']:continue
            if e['original'] is None:live.unlink()
            else:replace(live,(backup/e['path']).read_bytes())
        if sha(binpath/'Game.dll')==manifest['files']['Game.dll']:replace(binpath/'Game.dll',(backup/'Game.dll').read_bytes())
        if (binpath/'AionBrowserProbe.dll').exists() and sha(binpath/'AionBrowserProbe.dll')==manifest['files']['AionBrowserProbe.dll']:(binpath/'AionBrowserProbe.dll').unlink()
        raise
    print('OK: passive diagnostic installed; other client DLLs preserved. Backup:',backup)
if __name__=='__main__':main()
