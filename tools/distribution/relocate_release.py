"""Move a prepared local release workspace into its maintained source repository."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def relocate(source, destination, receipt, resume=False):
    repo = Path(__file__).resolve().parents[2]
    source = source.resolve(strict=True)
    destination = destination.resolve()
    receipt = receipt.resolve()
    if destination != repo/'releases' or (destination.exists() and not resume):
        raise ValueError('Expected the unoccupied releases directory inside this repository')
    if not (repo/'.git').exists() or source.drive.casefold() != destination.drive.casefold():
        raise ValueError('Expected an existing Git repository and a same-volume move')
    if receipt.exists() or receipt.is_relative_to(source) or receipt.is_relative_to(destination):
        raise ValueError('Use a fresh external receipt directory')
    def current(relative):
        old = source/relative
        return old if old.exists() else destination/relative

    layout = json.loads(current('Maintenance/release-layout.json').read_text())
    support = layout['setupSupport']
    settings_rel = Path(support)/'settings.json'
    settings = json.loads(current(settings_rel).read_text())
    base_rel = Path(layout['baseRelease'])/'Aetherfall-0.86.7z'
    before = current(base_rel).stat()
    setup_sha = digest(current('Aetherfall Setup.exe'))
    # No copy fallback: large payloads and the existing installed game move once.
    def merge_move(old, new):
        if not old.resolve().is_relative_to(source) or not new.resolve().is_relative_to(destination):
            raise ValueError('Move path escapes the explicit source/destination')
        if old.is_symlink() or new.is_symlink():
            raise ValueError('Unexpected link in move paths')
        if not new.exists():
            old.rename(new)
            return
        if not old.is_dir() or not new.is_dir():
            raise ValueError('Both locations contain the same file; refusing overwrite: '+str(old))
        for child in list(old.iterdir()):
            merge_move(child, new/child.name)
        old.rmdir()  # Only an empty, explicitly bounded source directory.

    merge_move(source, destination)
    receipt.mkdir(parents=True)
    changed = []

    def write(relative, data):
        path = destination/relative
        if not path.resolve().is_relative_to(destination):
            raise ValueError('Unexpected metadata path')
        backup = receipt/relative
        old_sha = None
        if path.is_file():
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, backup)
            old_sha = digest(path)
        path.write_bytes(data)
        changed.append(dict(path=str(relative), before=old_sha, after=digest(path)))

    for key in ['InstalledPath', 'InstallDirectory', 'DownloadDirectory']:
        value = settings.get(key)
        if isinstance(value, str):
            settings[key] = value.replace(str(source), str(destination)).replace(source.as_posix(), destination.as_posix())
    write(settings_rel, json.dumps(settings, indent=2).encode())
    installed = settings.get('InstalledPath', '')
    if installed:
        for name in ['Server/database/my.ini', 'Server/database/data/my.ini']:
            ini = Path(installed)/name
            if ini.is_file() and ini.resolve().is_relative_to(destination):
                original = ini.read_text(encoding='utf-8-sig')
                updated = original.replace(str(source), str(destination)).replace(source.as_posix(), destination.as_posix())
                if updated != original:
                    write(ini.relative_to(destination), updated.encode())
    layout.update(sourceRepository='..', publisher='../tools/distribution/publish_update.py')
    write(Path('Maintenance/release-layout.json'), json.dumps(layout, indent=2).encode())
    prefix = '@echo off\r\nsetlocal\r\ncd /d "%~dp0"\r\n'
    command = 'python "%~dp0..\\tools\\distribution\\publish_update.py" --dist "%~dp0." --prepared'
    write(Path('Prepare Patch.cmd'), (prefix+command+' --no-upload\r\npause\r\n').encode())
    write(Path('Publish Update.cmd'), (prefix+command+'\r\npause\r\n').encode())
    scripts = destination/'Maintenance/Scripts'
    if scripts.exists():
        scripts.rename(destination/'Maintenance/History/Standalone-Scripts')
    readme = (destination/'README.txt').read_text()
    readme += '''
REPOSITORY CONNECTION

This workspace is releases/ inside the Aion Aetherfall source repository.
Prepare Patch and Publish Update run ../tools/distribution/publish_update.py
directly, so source changes to the publisher apply immediately. Historical
standalone script copies are kept under Maintenance/History only.

The repository tracks the source and release tools. Generated archives, patches,
settings and your installed game in releases/ stay local and are ignored by Git.
After normal build/install and repack refresh, Prepare Patch reads the current
repack version and FILES.json automatically. Publish Update releases that version
when you choose to run it; commits and pushes do not publish releases.
'''
    write(Path('README.txt'), readme.encode())
    after = (destination/base_rel).stat()
    assert (before.st_ino, before.st_size, before.st_mtime_ns) == (after.st_ino, after.st_size, after.st_mtime_ns)
    assert digest(destination/'Aetherfall Setup.exe') == setup_sha
    result = dict(status='moved', source=str(source), destination=str(destination),
                  repository=str(repo), changedMetadata=changed, baseArchiveSameFile=True,
                  installedGameCopied=False, setupSha256=setup_sha, uploaded=False)
    (receipt/'receipt.json').write_text(json.dumps(result, indent=2))
    print('Release workspace:', destination)
    print('Commands now use the maintained repository scripts directly.')
    print('Base archive retained as the same file; no game copy or upload.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    parser.add_argument('--receipt', required=True, type=Path)
    parser.add_argument('--resume', action='store_true', help='Finish a partial same-volume move without overwriting files')
    args = parser.parse_args()
    relocate(args.source, args.destination, args.receipt, args.resume)
