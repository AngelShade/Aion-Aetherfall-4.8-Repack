"""Organize an existing release workspace by moving files on the same volume."""
import argparse
import hashlib
import json
import os
import shutil
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--root', type=Path, required=True)
parser.add_argument('--repack', type=Path, required=True)
parser.add_argument('--setup', type=Path, required=True)
parser.add_argument('--receipt', type=Path, required=True)
parser.add_argument('--keep-support-name', action='store_true', help='Keep the existing support/install directory at its current path')
args = parser.parse_args()
root = args.root.resolve(strict=True)
support_name = 'Aetherfall-Downloader' if args.keep_support_name else 'Setup'
support = root/support_name
repack = args.repack.resolve(strict=True)
receipt = args.receipt.resolve()
if (root/'Maintenance/release-layout.json').exists():
    raise ValueError('Already organized; use the existing entry points')
if not (root/'baseline-files-0.86.json').is_file() or not (repack/'REPACK.json').is_file():
    raise ValueError('Expected the existing base release and complete portable repack')
if receipt.is_relative_to(root) or receipt.exists():
    raise ValueError('Use a fresh external receipt')


def inside(relative):
    if args.keep_support_name and relative.startswith('Setup/'):
        relative = support_name+relative[len('Setup'):]
    path = root/relative
    if not path.resolve().is_relative_to(root):
        raise ValueError('Path escapes release folder: '+relative)
    return path


moves = []


def move(source, target):
    old, new = inside(source), inside(target)
    if not old.exists():
        return
    if old.is_symlink() or new.exists():
        raise ValueError('Unexpected source or occupied target: '+str(old))
    new.parent.mkdir(parents=True, exist_ok=True)
    old.rename(new)  # Same-volume rename, including large archives/installations.
    moves.append(dict(source=old.relative_to(root).as_posix(), target=new.relative_to(root).as_posix()))


version = json.loads((repack/'release.json').read_text())['Version']
if not args.keep_support_name:
    move('Aetherfall-Downloader', 'Setup')
move('Setup/Aetherfall Setup.exe', 'Aetherfall Setup.exe')
move('Setup/Aetherfall Setup.exe.old', 'Maintenance/History/Aetherfall Setup.exe.old')
for name in ['Aetherfall-0.86.7z', 'Aetherfall-0.86.torrent', 'Aetherfall-Downloader.zip',
             'release-manifest.json', 'SHA256SUMS.txt', 'magnet.txt', 'mirrors.json', 'dht.dat', 'Start-Seeding.cmd']:
    move(name, 'Release/0.86/'+name)
for path in sorted(root.glob('Aetherfall-Patch-*.zip')):
    patch_version = path.stem.removeprefix('Aetherfall-Patch-')
    move(path.name, 'Patches/'+patch_version+'/'+path.name)
move('latest-update.json', 'Patches/'+version+'/latest-update.json')
move('UPDATE-'+version+'.txt', 'Patches/'+version+'/release-notes.txt')
move('baseline-files-0.86.json', 'Maintenance/Baseline/baseline-files-0.86.json')
for name in ['archive-files.txt', 'build-receipt.json', 'packing.log']:
    move(name, 'Maintenance/Logs/'+name)
move('github-release-repo', 'Maintenance/GitHub')
for name in ['build_patch.py', 'publish_update.py', 'publish.py']:
    move(name, 'Maintenance/Scripts/'+name)
for name in ['UPLOAD.txt', 'Publish-Update.cmd']:
    move(name, 'Maintenance/History/'+name)

# Only small support replacements are backed up. Payloads and installed games
# were moved rather than copied; no archive or native game file is rewritten.
receipt.mkdir(parents=True)
records = []


def write(relative, content):
    path = inside(relative)
    path.parent.mkdir(parents=True, exist_ok=True)
    before = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
    if before:
        shutil.copy2(path, receipt/str(len(records)))
    path.write_bytes(content)
    records.append(dict(path=relative, before=before, after=hashlib.sha256(content).hexdigest()))


write('Aetherfall Setup.exe', args.setup.read_bytes())
for name in ['publish_update.py', 'build_patch.py', 'publish.py']:
    write('Maintenance/Scripts/'+name, (HERE/name).read_bytes())
settings = json.loads((support/'settings.json').read_text(encoding='utf-8-sig'))
old_support, new_support = str(root/'Aetherfall-Downloader'), str(support)
for key in ['InstallDirectory', 'DownloadDirectory', 'InstalledPath']:
    if isinstance(settings.get(key), str):
        settings[key] = settings[key].replace(old_support, new_support)
settings['DownloadDirectory'] = '../Release/0.86'
settings['DeleteArchiveAfter'] = False  # Owner's base release must stay available.
games = support/'Games'
found = [p for p in games.glob('Aetherfall-*') if (p/'REPACK.json').is_file()]
if len(found) == 1:
    settings['InstalledPath'] = str(found[0])
write('Setup/settings.json', json.dumps(settings, indent=2).encode())
layout = dict(schema=1, repackFolder=str(repack), baseVersion='0.86',
              baselineIndex='Maintenance/Baseline/baseline-files-0.86.json',
              baseRelease='Release/0.86', patches='Patches', setupSupport=support_name)
write('Maintenance/release-layout.json', json.dumps(layout, indent=2).encode())
prefix = '@echo off\r\nsetlocal\r\ncd /d "%~dp0"\r\n'
command = 'python "%~dp0Maintenance\\Scripts\\publish_update.py" --dist "%~dp0." --prepared'
write('Prepare Patch.cmd', (prefix+command+' --no-upload\r\npause\r\n').encode())
write('Publish Update.cmd', (prefix+command+'\r\npause\r\n').encode())
seed = (root/'Release/0.86/Start-Seeding.cmd').read_text()
seed = seed.replace('"Aetherfall-Downloader\\bin\\aria2c.exe"', '"%~dp0..\\..\\'+support_name+'\\bin\\aria2c.exe"')
write('Release/0.86/Start-Seeding.cmd', seed.encode())
write('Start Seeding.cmd', (prefix+'call "%~dp0Release\\0.86\\Start-Seeding.cmd"\r\n').encode())
write('README.txt', '''AION AETHERFALL - RELEASE WORKSPACE

Aetherfall Setup.exe  Open this to try Setup or manage your installed game.
Prepare Patch.cmd     Build the current repack's cumulative update locally.
Publish Update.cmd    Prepare/reuse that patch and publish it to the existing channel.
Start Seeding.cmd     Seed the original 0.86 download; starts only when clicked.

Release/0.86          Original archive, torrent, hashes and small downloader ZIP.
Patches/<version>     Patch ZIP, latest-update.json and version-specific notes.
Setup                Setup support, settings and your existing test installation.
Maintenance          Publisher scripts/config, baseline, Git checkout and logs.

The full repack is maintained separately; Maintenance/release-layout.json points
to it. After a normal update to that folder, Prepare Patch or Publish Update
compares its current FILES.json with the fixed 0.86 baseline. Only changed files
enter the cumulative patch, so users can update directly from 0.86. The base
archive is never rebuilt, renamed or changed by publishing an update.

Publish Update uses Patches/<current-version>, verifies the ZIP/Setup hashes,
uploads the patch and Setup, then advances the existing public channel. It does
not increment versions or produce unrelated release directories. Run Prepare
Patch to check local output without uploading. Changes to the full repack must
be recorded in its FILES.json by its normal updater before preparing a patch.

Existing Setup/Games and Setup/Downloads remain together, without duplication.
The owner's Setup reads the base archive from Release/0.86 and keeps it after
installation. The distributable ZIP has normal Downloads/Games defaults and
does not include your test game, saved settings paths or historical files.
'''.replace('Setup/', support_name+'/').replace('Setup                ', support_name+'  ').encode())

# New downloads get a clean GUI bundle with hidden support. Old flat bundles
# remain compatible with the same executable and self-update route.
bundle = root/'Release/0.86/Aetherfall-Downloader.zip'
temporary = bundle.with_suffix('.zip.new')
with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.write(root/'Aetherfall Setup.exe', 'Aetherfall Setup.exe')
    for directory, folders, files in os.walk(support):
        folders[:] = [name for name in folders if name.casefold() not in {'games', 'downloads', 'logs'}]
        for name in files:
            path = Path(directory)/name
            relative = path.relative_to(support).as_posix()
            if name.endswith(('.old', '.log', '.lock', '.new')) or relative == 'settings.json':
                continue
            archive.write(path, 'Setup/'+relative)
    public_settings = dict(settings, DownloadDirectory='Downloads', InstallDirectory='Games', InstalledPath='', DeleteArchiveAfter=True)
    archive.writestr('Setup/settings.json', json.dumps(public_settings, indent=2))
# The small old ZIP is backed up; the 36 GiB base archive is never copied.
shutil.copy2(bundle, receipt/'previous-downloader.zip')
os.replace(temporary, bundle)
(receipt/'receipt.json').write_text(json.dumps(dict(status='organized', root=str(root), moves=moves,
    changedSupport=records, archiveContentChanged=False, gamePayloadChanged=False,
    uploaded=False, seedingStarted=False), indent=2))
print('Organized release workspace:', root)
print('Moved', len(moves), 'entries; no full archive or installed-game copy.')
