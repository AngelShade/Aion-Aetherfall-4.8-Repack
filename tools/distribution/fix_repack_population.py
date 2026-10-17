"""Apply the population bootstrap packaging correction to the uninitialized full repack."""
import argparse
import hashlib
import json
import shutil
import re
import zipfile
from datetime import datetime
from pathlib import Path
from repack_population import payloads


def digest(data):
    return hashlib.sha256(data).hexdigest()


def apply(package, backup, build=None, notes=None):
    package, backup = package.resolve(strict=True), backup.resolve()
    if backup.exists() or backup.is_relative_to(package) or (package/'Server/database/data').exists():
        raise ValueError('Use the uninitialized full repack and a fresh external backup')
    repo = Path(__file__).resolve().parents[2]
    release = json.loads((repo/'release.json').read_text())
    changes = payloads(package)
    if build:
        build = build.resolve(strict=True)
        manifest = json.loads((build/'build-manifest.json').read_text())
        for component in ['game-server','login-server','chat-server']:
            archive_path = build/component/(component+'.zip')
            relative_archive = archive_path.relative_to(build).as_posix()
            if digest(archive_path.read_bytes()) != manifest['artifacts'][relative_archive]:
                raise ValueError('Normal builder assembly checksum mismatch: '+component)
            with zipfile.ZipFile(archive_path) as archive:
                libraries = [name for name in archive.namelist() if name.startswith(component+'/libs/') and name.endswith('.jar')]
                if not libraries:
                    raise ValueError('Normal builder assembly has no libraries: '+component)
                for name in libraries:
                    if len(Path(name).parts) != 3:
                        raise ValueError('Unexpected library path')
                    changes['Server/'+name] = archive.read(name)
    changes['release.json'] = (repo/'release.json').read_bytes()
    changes['Client/Mods/release.json'] = changes['release.json']
    identity = json.loads((package/'REPACK.json').read_text())
    identity.update(release=release['Version'].split('.')[-1].zfill(3), updatedAt=datetime.now().astimezone().isoformat(),
                    populationBootstrap='Native initial empty state; guarded existing-install repair before server startup')
    if build:
        identity['gameServerSha256'] = digest(changes['Server/game-server/libs/game-server-4.8-SNAPSHOT.jar'])
    changes['REPACK.json'] = json.dumps(identity, indent=2).encode()
    readme = (package/'README.txt').read_text()
    readme = re.sub(r'REPACK \d+\.\d+', 'REPACK '+release['Version'], readme, count=1)
    if '\nWORLD BOT STARTUP REPAIR\n' not in readme:
        readme += '\nWORLD BOT STARTUP REPAIR\nFresh databases initialize the native population state. Existing empty installs\nare repaired before the servers start; accounts and characters are preserved.\nExisting bot populations and legacy imports are never overwritten.\n'
    if notes:
        readme += '\n'+notes.read_text(encoding='utf-8-sig').rstrip()+'\n'
    changes['README.txt'] = readme.encode()
    changes = {relative:data for relative,data in changes.items()
               if not (package/relative).is_file() or digest((package/relative).read_bytes()) != digest(data)}
    index = json.loads((package/'FILES.json').read_text())
    entries = {entry['path'].casefold(): entry for entry in index['files']}
    for relative, data in changes.items():
        entry = entries.get(relative.casefold())
        if entry is None:
            entry = dict(path=relative, mutable=False)
            index['files'].append(entry)
        entry.update(sha256=digest(data), bytes=len(data))
    changes['FILES.json'] = json.dumps(index, separators=(',', ':')).encode()
    backup.mkdir(parents=True)
    written, records = [], []
    try:
        for relative, data in changes.items():
            path, saved = package/relative, backup/relative
            if not path.resolve().is_relative_to(package):
                raise ValueError('Unexpected package path')
            before = None
            if path.is_file():
                saved.parent.mkdir(parents=True, exist_ok=True)
                before = digest(path.read_bytes())
                shutil.copy2(path, saved)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            written.append(relative)
            assert digest(path.read_bytes()) == digest(data)
            records.append(dict(path=relative, before=before, sha256=digest(data), bytes=len(data)))
    except BaseException:
        for relative in reversed(written):
            saved, path = backup/relative, package/relative
            if saved.is_file():
                shutil.copy2(saved, path)
            else:
                path.unlink()
        raise
    (backup/'receipt.json').write_text(json.dumps(dict(status='complete', version=release['Version'],
        package=str(package), files=records, databaseStarted=False, sqlExecuted=False,
        jarChanged=bool(build), normalBuild=str(build) if build else None,
        originalDevelopmentInstallationChanged=False), indent=2))
    print('Repack '+release['Version']+': '+str(len(records))+' copied hashes verified; no SQL or game process started.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    parser.add_argument('--build', type=Path, help='Exact normal all-component builder output for a repack server update')
    parser.add_argument('--notes', type=Path, help='Player-facing release notes to include in the repack README')
    args = parser.parse_args()
    apply(args.package, args.backup, args.build, args.notes)
