"""Install the validated cumulative gear override only while GameServer is stopped."""
import argparse,json,shutil,subprocess
from datetime import datetime
from pathlib import Path
from install_companion_update import ROOT,sha
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--package',type=Path,required=True);args=parser.parse_args()
    package=args.package.resolve();server=ROOT/'target-deploy/game-server';manifest=json.loads((package/'manifest.json').read_text())
    command="@(Get-CimInstance Win32_Process -Filter \"name='java.exe'\" | Where-Object { $_.CommandLine -like '*com.aionemu.gameserver.GameServer*' }).Count"
    result=subprocess.run(['pwsh','-NoProfile','-Command',command],check=True,capture_output=True,text=True)
    assert result.stdout.strip()=='0','GameServer is running; use the live installer'
    assert Path(manifest['deployment'])==server and manifest.get('scope','').startswith('equipment acquisition')
    assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==manifest['baseJarSha256']
    for entry in manifest['files']:
        assert sha(server/entry['path'])==entry['original'],'Live baseline changed: '+entry['path']
        assert sha(package/entry['path'])==entry['installed'],'Reviewed payload changed: '+entry['path']
    backup=server/'backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir()
    for entry in manifest['files']:
        target=backup/entry['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/entry['path'],target);assert sha(target)==entry['original']
    shutil.copytree(server/'config/playerbots',backup/'preferences-before');shutil.copy2(package/'manifest.json',backup/'manifest.json');shutil.copy2(package/'rollback.jar',backup/'effective-rollback.jar')
    try:
        for entry in manifest['files']:
            if entry['original']!=entry['installed']:shutil.copy2(package/entry['path'],server/entry['path'])
        assert all(sha(server/e['path'])==e['installed'] for e in manifest['files'])
        assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==manifest['baseJarSha256']
    except Exception:
        for entry in manifest['files']:shutil.copy2(backup/entry['path'],server/entry['path'])
        (backup/'manifest.json').rename(backup/'failed-manifest.json');raise
    (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(),mode='server stopped',files=manifest['files'],baseJarSha256=manifest['baseJarSha256']),indent=2))
    (backup/'runtime-verification.txt').write_text('OK: gear override and command installed while GameServer was stopped; all receipts and preserved files verified.\nStartup/native panel validation pending.\n')
    print('OK: equipment update installed on disk; base JAR, launcher, latest generation fix, media and client preserved. Backup:',backup)
if __name__=='__main__':main()
