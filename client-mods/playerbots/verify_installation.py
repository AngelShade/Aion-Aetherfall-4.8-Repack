"""Verify installed companion server/client hashes, backups, archive preservation and live public routes."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import zipfile
import prepare

def verify(client_backup,server_backup):
    client_manifest=json.loads((client_backup/'manifest.json').read_text(encoding='utf-8'))
    client=Path(client_manifest['clientRoot']);server_manifest=json.loads((server_backup/'manifest.json').read_text(encoding='utf-8'))
    server=Path(server_manifest['deployment'])
    for root,backup,manifest,hash_key in [(client,client_backup,client_manifest,'staged'),(server,server_backup,server_manifest,'sha256')]:
        for entry in manifest['files']:
            assert prepare.sha((root/entry['path']).read_bytes())==entry[hash_key],'Installed hash mismatch: '+entry['path']
            if entry['original'] is not None:assert prepare.sha((backup/entry['path']).read_bytes())==entry['original'],'Backup mismatch: '+entry['path']
    for entry in client_manifest['preservedFiles']:
        assert prepare.sha((client/entry['path']).read_bytes())==entry['sha256'],'Preserved client changed: '+entry['path']
    dll='bin64/Game.dll'
    assert (client/dll).read_bytes()==prepare.patch((client_backup/dll).read_bytes())
    pak='Plugin/RelicCalc/RelicCalc.pak';old=prepare.read_pak(client_backup/pak);new=prepare.read_pak(client/pak)
    assert new.namelist()==old.namelist() and new.testzip() is None
    for name in old.namelist():assert new.read(name)==(prepare.menu(old.read(name)) if name=='PrivateMenus.lua' else old.read(name)),name
    jar='libs/game-server-4.8-SNAPSHOT.jar';preserved=0
    with zipfile.ZipFile(server_backup/jar) as old,zipfile.ZipFile(server/jar) as new:
        assert new.testzip() is None
        for name in old.namelist():
            if name not in server_manifest['replacedClasses']:
                assert old.read(name)==new.read(name),'Unrelated deployed JAR entry changed: '+name;preserved+=1
    assert preserved==server_manifest['preservedJarEntries']
    subprocess.run(['java',str(prepare.HERE.parent/'season-pass/VerifySignatures.java'),str(client),str(client)],check=True)
    graphics=json.loads((client/'DXVK/graphics-menu/installed.json').read_text(encoding='utf-8-sig'))
    package=json.loads((client/'DXVK/graphics-menu/package/manifest.json').read_text(encoding='utf-8-sig'))
    assert graphics['files']==package['files'] and not graphics.get('restoredAt'),'Graphics recovery state differs'
    for entry in graphics['files']:
        assert prepare.sha((client/entry['path']).read_bytes())==entry['installed'],'Graphics tracking is stale: '+entry['path']
        assert prepare.sha((client/'DXVK/graphics-menu/package'/entry['path']).read_bytes())==entry['installed'],'Cached graphics payload differs'
        if entry['original'] is not None:
            assert prepare.sha((Path(graphics['backupRoot'])/entry['path']).read_bytes())==entry['original'],'Graphics restore baseline differs'
    dxvk=json.loads((client/'DXVK/installed.json').read_text(encoding='utf-8-sig'))
    for entry in dxvk['nativeCursorPatch']['files']:
        assert prepare.sha((client/entry['path']).read_bytes())==entry['installed'].lower(),'Cursor tracking is stale'
        assert prepare.sha(Path(entry['backupPath']).read_bytes())==entry['original'].lower(),'Cursor restore baseline differs'
    # Both recovery DLLs must retain the exact companion browser caves.
    saved=(client_backup/dll).read_bytes();updated=prepare.patch(saved)
    game_record=next(e for e in dxvk['nativeCursorPatch']['files'] if e['path']==dll)
    for baseline in [Path(graphics['backupRoot'])/dll,Path(game_record['backupPath'])]:
        data=baseline.read_bytes()
        for start,end in prepare.RANGES:assert data[start:end]==updated[start:end],'Graphics/cursor removal would remove companion routes'
    environment=dict(os.environ)
    environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(client/'DXVK/graphics-menu/ApplyGraphicsMenu.ps1'),'-ClientPath',str(client),'-VerifyOnly'],check=True,env=environment)
    routes=[]
    for path,status in [('/market/companions',200),('/market/companions/media/bots.js',200),('/market/companions/media/bots.css',200),('/market/companions/state',403)]:
        try: response=urllib.request.urlopen('http://127.0.0.1:8091'+path,timeout=10)
        except urllib.error.HTTPError as error:response=error
        with response:
            assert response.code==status,'Unexpected live status: '+path
            body=response.read()
            if status==200:
                filename='bots.html' if path=='/market/companions' else path.rsplit('/',1)[1]
                assert body==(server/'config/playerbots/media'/filename).read_bytes(),'Live companion media differs from installed file'
            else:assert 'Log in' in json.loads(body)['error']
            routes.append(dict(path=path,status=status))
    request=urllib.request.Request('http://127.0.0.1:8091/market/companions/action',data=b'action=recruit&name=Unavailable',method='POST')
    try: response=urllib.request.urlopen(request,timeout=10)
    except urllib.error.HTTPError as error:response=error
    with response:assert response.code==403,'Unauthenticated live companion mutation was not refused'
    report=dict(client=str(client),server=str(server),clientBackup=str(client_backup),serverBackup=str(server_backup),preservedJarEntries=preserved,liveRoutes=routes,anonymousMutationStatus=403,graphicsCursorRecovery='verified',gameplayAcceptance='pending')
    (server_backup/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('OK: installed companion hashes/backups, stock assets, three addon signatures, graphics/cursor startup and restore baselines,',preserved,'unrelated JAR entries and five live HTTP checks verified. In-game acceptance remains pending.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--client-backup',type=Path,required=True);parser.add_argument('--server-backup',type=Path,required=True);args=parser.parse_args()
    try:verify(args.client_backup.resolve(),args.server_backup.resolve())
    except Exception as error:print('FAIL:',error);raise SystemExit(1)
