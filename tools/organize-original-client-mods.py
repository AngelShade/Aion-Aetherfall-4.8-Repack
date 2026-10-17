"""Move the original client's mod support into the shared Mods layout. No game patching."""
import argparse
import hashlib
import json
import re
import shutil
from datetime import datetime
from pathlib import Path
from client_mod_layout import PREFIXES, mapped, rewrite, rewrite_json

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--client', type=Path, required=True)
parser.add_argument('--repack-client', type=Path, required=True)
parser.add_argument('--backup', type=Path, required=True)
args = parser.parse_args()
CLIENT = args.client.resolve(strict=True)
REPACK = args.repack_client.resolve(strict=True)
BACKUP = args.backup.resolve()
ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / 'docs/INSTALLED_MODS.json'
if (CLIENT / 'Mods').exists() or BACKUP.is_relative_to(CLIENT) or BACKUP.exists():
    raise ValueError('Use an unmigrated client and a fresh external backup')


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def js(value):
    return json.dumps(value, indent=2).encode()


def inside(path):
    if not path.resolve().is_relative_to(CLIENT):
        raise ValueError('Path escapes the original client: ' + str(path))


inventory = json.loads(INVENTORY.read_text(encoding='utf-8-sig'))
inventory_before = sha(INVENTORY)
directory_names = [old for old, new in PREFIXES if '/' not in old and '.' not in old]
sources = []
old_directories = []
for name in directory_names:
    folder = CLIENT / name
    if folder.is_dir():
        old_directories.append(folder)
        sources.extend(p for p in folder.rglob('*') if p.is_file())
sources.append(CLIENT / 'Enable-FreeFlight.ps1')
sources = sorted(set(sources))
moves, writes = [], {}
for source in sources:
    inside(source)
    relative = source.relative_to(CLIENT).as_posix()
    target = CLIENT / mapped(relative)
    inside(target)
    if source != target:
        if target.exists():
            raise ValueError('Destination already exists: ' + str(target))
        moves.append(dict(source=str(source), target=str(target), bytes=source.stat().st_size))
    if '-backups/' in relative or source.suffix.lower() not in {'.ps1', '.bat', '.json', '.ini', '.md', '.txt'}:
        continue
    before = source.read_bytes()
    if source.suffix.lower() == '.json':
        obj = json.loads(before.decode('utf-8-sig'))
        new = rewrite_json(obj)
        after = before if obj == new else js(new)
    else:
        text = rewrite(before.decode('utf-8-sig'))
        if source.suffix.lower() == '.ps1':
            text = text.replace('(Split-Path -Parent $PSScriptRoot)',
                "([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\\..')))")
            if relative == 'Enable-FreeFlight.ps1':
                text = text.replace('[string]$ClientPath = $PSScriptRoot',
                    "[string]$ClientPath = ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\\..')))")
        if source.suffix.lower() == '.bat':
            text = re.sub(r'(?<=%~dp0)[^"\r\n;]+', lambda m: m[0].replace('/', '\\'), text)
        after = text.encode()
    if after != before:
        writes[target] = after

launcher = CLIENT / 'Aion Start.bat'
text = rewrite(launcher.read_text(encoding='utf-8-sig'))
text = re.sub(r'(?<=%~dp0)[^"\r\n;]+', lambda m: m[0].replace('/', '\\'), text)
writes[launcher] = text.replace('\r\n', '\n').replace('\n', '\r\n').encode()

# Align feature documentation with the repack, using this client's own file records.
reference = json.loads((REPACK / 'Mods/InstalledMods.json').read_text())
files_by_path = {e['path'].casefold(): e for e in inventory['clientFiles']}
features = {}
for feature, entries in reference['features'].items():
    current = []
    for entry in entries:
        relative = entry['path']
        path = CLIENT / relative
        if path.exists():
            recorded = files_by_path.get(relative.casefold(), {})
            current.append(dict(path=relative, installedSha256=recorded.get('sha256'), bytes=path.stat().st_size))
    features[feature] = current
    if not any(mapped(p.relative_to(CLIENT).as_posix()).startswith('Mods/' + feature + '/') for p in sources):
        writes[CLIENT / 'Mods' / feature / 'README.txt'] = (feature + '\n\nAlready installed in the original client.\n' +
            ''.join('  ' + e['path'] + '\n' for e in current) +
            '\nNative DLLs, game archives and runtime settings must retain these locations.\n').encode()
features['WardrobeHUD'] = [dict(path=p, installedSha256=files_by_path.get(p.casefold(), {}).get('sha256')) for p in
    ['bin64/AionMarketShortcut.dll', 'Data/ui/game_hud_s1/game_hud_s1.pak', 'Data/ui/game_hud_s2/game_hud_s2.pak']]
writes[CLIENT / 'Mods/WardrobeHUD/README.txt'] = b'Wardrobe HUD shortcut\n\nInstalled to the right of Journey in both HUD styles. Uses /wardrobe and the native wardrobe icon.\n'
features['Inventory'] = [dict(path=p, installedSha256=files_by_path.get(p.casefold(), {}).get('sha256')) for p in
    ['bin64/Game.dll', 'Data/ui/ui.pak', 'L10N/enu/Data/data.pak']]
writes[CLIENT / 'Mods/Inventory/README.txt'] = b'Expanded inventory\n\nCurrent original client: base 189 slots, maximum 288. Saved expansion credits are retained.\n'
writes[CLIENT / 'Mods/Security/README.txt'] = b'''VirusTotal reports

The existing public scan page is in the repack at Mods/Security/VirusTotal Reports.html.
Those results apply only to its listed hashes. This original client has newer
Wardrobe/inventory binaries; the repack's older reports do not cover changed files.
'''
writes[CLIENT / 'Mods/InstalledMods.json'] = js(dict(version=1, features=features,
    note='Installed hashes inherited from the current source delivery inventory. Native DLLs are checked during the move.'))
guide = (REPACK / 'Mods/README.txt').read_text()
guide = guide.replace('Security           VirusTotal Reports.html: exact hashes, findings and public reports.',
    'Security           Scan-report scope and the differences from the older repack.')
guide = guide.replace('packaged hashes', 'installed hashes').replace('this repack', 'this client')
guide = guide.replace('portable-client.json is used by the repack launcher when the whole repack moves.',
    'The original client does not use the repack portability launcher.\nWardrobeHUD and Inventory describe the newer original-client changes.\nDo not replace this client with older repack files. Future transfers must start\nfrom the latest original client and preserve target connection settings.\nHistorical backups are kept byte-for-byte under Mods/Backups; old receipts\nmay describe the layout used when they were created.')
writes[CLIENT / 'Mods/README.txt'] = guide.encode()

# Preserve exact current binaries; do not rebuild or transfer them from the older repack.
native = [p for p in CLIENT.glob('bin64/*.dll') if p.name.lower() in {
    'game.dll', 'crysystem.dll', 'xrenderd3d9.dll', 'version.dll', 'aiongraphicsmenu.dll',
    'aioniconbridge.dll', 'aionmarketshortcut.dll', 'aionrememberlogin.dll', 'aionspeechbubbles.dll'}]
native_before = {p.relative_to(CLIENT).as_posix(): sha(p) for p in native}
fixed_state = {p: (CLIENT / p).stat() for p in ['Data/ui/ui.pak', 'L10N/enu/Data/data.pak',
    'Data/ui/game_hud_s1/game_hud_s1.pak', 'Data/ui/game_hud_s2/game_hud_s2.pak', 'system.cfg', 'SystemOptionGraphics.cfg']}
BACKUP.mkdir(parents=True)
original_sources = {Path(m['target']): Path(m['source']) for m in moves}
edits = []
for i, target in enumerate(writes):
    inside(target)
    source = original_sources.get(target, target)
    saved = BACKUP / str(i)
    if source.exists():
        shutil.copy2(source, saved)
        edits.append(dict(path=str(target), backup=str(saved), before=sha(source)))
    else:
        edits.append(dict(path=str(target), backup=None, before=None))
shutil.copy2(INVENTORY, BACKUP / 'INSTALLED_MODS.before.json')
receipt = dict(status='prepared', client=str(CLIENT), moves=moves, edits=edits, nativeDlls=native_before,
    repackChanged=False, ordinaryTestsRun=False, startup=False)
(BACKUP / 'receipt.json').write_bytes(js(receipt))
done, written = [], []
try:
    for move in moves:
        source, target = Path(move['source']), Path(move['target'])
        inside(source)
        inside(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        source.rename(target)
        done.append(move)
    for target, data in writes.items():
        target.parent.mkdir(parents=True, exist_ok=True)
        written.append(target)
        target.write_bytes(data)
        if sha(target) != hashlib.sha256(data).hexdigest():
            raise ValueError('Copied support file differs: ' + str(target))
    if any(sha(CLIENT / p) != digest for p, digest in native_before.items()):
        raise ValueError('A native DLL changed')
    for relative, before in fixed_state.items():
        after = (CLIENT / relative).stat()
        if (after.st_size, after.st_mtime_ns) != (before.st_size, before.st_mtime_ns):
            raise ValueError('A native archive or user setting changed: ' + relative)
    if sha(INVENTORY) != inventory_before:
        raise ValueError('The delivery inventory changed concurrently')
    updated = rewrite_json(inventory)
    for entry in updated['clientFiles']:
        entry['path'] = mapped(entry['path'])
        path = CLIENT / entry['path']
        if path in writes:
            entry['sha256'] = hashlib.sha256(writes[path]).hexdigest()
    updated['clientReceipts'].append(str(BACKUP / 'receipt.json'))
    updated['lastIncrementalClientUpdate'] = dict(scope='Original client Mods organization; Wardrobe HUD/inventory079 retained',
        receipt=str(BACKUP / 'receipt.json'), fullInventoryRerun=False)
    INVENTORY.write_bytes(js(updated))
except BaseException:
    for edit in reversed(edits):
        target = Path(edit['path'])
        if edit['backup']:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(edit['backup'], target)
        elif target in written and target.exists():
            target.unlink()
    for move in reversed(done):
        source, target = Path(move['source']), Path(move['target'])
        source.parent.mkdir(parents=True, exist_ok=True)
        target.rename(source)
    raise

for root in old_directories:
    folders = sorted([p for p in root.rglob('*') if p.is_dir()], key=lambda p: len(p.parts), reverse=True)
    for folder in folders + [root]:
        inside(folder)
        if not any(folder.iterdir()):
            folder.rmdir()
receipt.update(status='installed', installedAt=datetime.now().astimezone().isoformat(),
    changedSupportFiles=len(writes), verification='Moved paths and edited support-file hashes; nine unchanged DLL hashes and unchanged native archive/settings timestamps')
(BACKUP / 'receipt.json').write_bytes(js(receipt))
print(json.dumps(dict(status='installed', movedFiles=len(moves), changedSupportFiles=len(writes),
    nativeDllsUnchanged=True, originalMods=str(CLIENT / 'Mods'), backup=str(BACKUP)), indent=2))
