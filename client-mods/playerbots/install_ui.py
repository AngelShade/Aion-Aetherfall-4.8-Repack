"""Install only reviewed companion browser media, preserving client and server code."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
FILES = ['config/playerbots/media/bots.' + ext for ext in ('html', 'css', 'js')]

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def verify_preserved(manifest):
    for entry in manifest['preservedFiles']:
        assert sha(Path(entry['path'])) == entry['sha256'], 'Preserved baseline changed: ' + entry['path']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--verify-live', action='store_true', help='Require byte-exact HTTP responses from the running server')
    args = parser.parse_args()
    package = args.package.resolve()
    server = ROOT / 'target-deploy/game-server'
    if args.prepare:
        assert not package.exists(), 'Use a fresh staging directory'
        inventory = json.loads((ROOT/'docs/INSTALLED_MODS.json').read_text())
        assert all(inventory['checks'].values())
        preserved = [dict(path=str(Path(inventory['clientRoot'])/e['path']), sha256=e['sha256']) for e in inventory['clientFiles']]
        anchor = Path(inventory['clientRoot'])/'PlayerBotBar.ini'
        if anchor.exists():
            preserved.append(dict(path=str(anchor), sha256=sha(anchor)))
        for relative in ['libs/game-server-4.8-SNAPSHOT.jar', 'libs/playerbot-recruitment-fix.jar', 'start.bat', 'config/main/playerbots.properties']:
            preserved.append(dict(path=str(server/relative), sha256=sha(server/relative)))
        package.mkdir(parents=True)
        files = []
        for relative in FILES:
            destination = package/relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT/'game-server'/relative, destination)
            files.append(dict(path=relative, original=sha(server/relative), installed=sha(destination)))
        manifest = dict(feature='playerbot-window-ui', revision='tabs-master-detail-1', deployment=str(server), preparedAt=datetime.now().isoformat(), files=files, preservedFiles=preserved)
        (package/'manifest.json').write_text(json.dumps(manifest, indent=2))
        verify_preserved(manifest)
        print('OK: three-file UI package prepared:', package)
        return
    manifest = json.loads((package/'manifest.json').read_text())
    assert manifest['feature'] == 'playerbot-window-ui' and Path(manifest['deployment']) == server
    assert [e['path'] for e in manifest['files']] == FILES
    verify_preserved(manifest)
    for entry in manifest['files']:
        assert sha(server/entry['path']) == entry['original'], 'Installed UI changed since preparation'
        assert sha(package/entry['path']) == entry['installed'], 'Reviewed package changed'
    backup = server/'backups'/('playerbots-ui-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir()
    for entry in manifest['files']:
        destination = backup/entry['path']
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(server/entry['path'], destination)
        assert sha(destination) == entry['original']
    shutil.copy2(package/'manifest.json', backup/'manifest.json')
    try:
        for entry in manifest['files']:
            shutil.copy2(package/entry['path'], server/entry['path'])
            assert sha(server/entry['path']) == entry['installed']
        verify_preserved(manifest)
        if args.verify_live:
            for entry in manifest['files']:
                path = '/market/companions' if entry['path'].endswith('.html') else '/market/companions/media/'+Path(entry['path']).name
                with urllib.request.urlopen('http://127.0.0.1:8091'+path, timeout=10) as response:
                    assert response.status == 200 and response.read() == (server/entry['path']).read_bytes(), 'Live HTTP media mismatch'
    except Exception:
        for entry in manifest['files']:
            shutil.copy2(backup/entry['path'], server/entry['path'])
        (backup/'manifest.json').rename(backup/'failed-manifest.json')
        raise
    (backup/'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(), files=manifest['files']), indent=2))
    (backup/'verification.json').write_text(json.dumps(dict(result='OK', files=3, preservedFiles=len(manifest['preservedFiles']), publicRoutes='byte-exact' if args.verify_live else 'pending server startup', gameplayAcceptance='pending in-game'), indent=2))
    print('OK: three companion UI files installed; client, server JARs, override and launcher preserved. Backup:', backup)

if __name__ == '__main__':
    main()
