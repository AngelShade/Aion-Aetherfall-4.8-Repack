"""Organize an existing portable repack's client support files without rebuilding mods."""
import argparse
import hashlib
import json
import re
import shutil
from datetime import datetime
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--package', type=Path, required=True)
parser.add_argument('--backup', type=Path, required=True)
args = parser.parse_args()
pack = args.package.resolve(strict=True)
client = (pack / 'Client').resolve(strict=True)
backup = args.backup.resolve()
if backup.is_relative_to(pack) or (client / 'Mods').exists():
    raise ValueError('Use an external backup and a client not already organized.')

prefixes = [
    ('DXVK/renderer.ini', 'DXVK/renderer.ini'),  # Native AionGraphicsMenu.dll reads this fixed path.
    ('DXVK/graphics-menu', 'Mods/GraphicsMenu'),
    ('DXVK/cursor-fix', 'Mods/CursorFix'),
    ('DXVK-backups', 'Mods/Backups/Renderer'),
    ('GraphicsOverhaul-backups', 'Mods/Backups/GraphicsOverhaul'),
    ('VisualOverhaul-backups', 'Mods/Backups/VisualOverhaul'),
    ('GraphicsOverhaul', 'Mods/GraphicsOverhaul'),
    ('VisualOverhaul', 'Mods/VisualOverhaul'),
    ('DXVK', 'Mods/Renderer'),
    ('Enable-FreeFlight.ps1', 'Mods/FreeFlight/Install-FreeFlight.ps1'),
    ('VirusTotal Reports.html', 'Mods/Security/VirusTotal Reports.html'),
    ('portable-client.json', 'Mods/portable-client.json'),
]
renames = {
    'ApplyGraphicsMenu.ps1': 'Install-GraphicsMenu.ps1',
    'InstallNativeCursorPatch.ps1': 'Install-CursorFix.ps1',
    'ApplyGlobal.ps1': 'Install-GraphicsOverhaul.ps1',
    'ApplyPackage.ps1': 'Install-VisualOverhaul.ps1',
}


def mapped(relative):
    relative = relative.replace('\\', '/')
    for old, new in prefixes:
        if relative.casefold() == old.casefold() or relative.casefold().startswith(old.casefold() + '/'):
            relative = new + relative[len(old):]
            break
    parent, sep, name = relative.rpartition('/')
    return parent + sep + renames.get(name, name)


patterns = [(re.escape(old).replace('/', r'[\\/]'), new) for old, new in prefixes]
path_pattern = re.compile(r'(?<![\w-])(?<!Mods/)(?<!Mods\\)(?:' + '|'.join('(' + p + ')' for p, _ in patterns) + r")(?=[\\/'\"]|$)", re.I)


def rewrite(text):
    text = re.sub(r'%~dp0([^"\r\n;]+)', lambda m: '%~dp0' + mapped(m[1]), text)
    text = text.replace("Join-Path $modRoot 'graphics-menu/", "Join-Path $clientRoot 'Mods/GraphicsMenu/")
    def replace(match):
        for i, (_, new) in enumerate(patterns, 1):
            if match.group(i) is not None:
                # Use the input separator for Windows launcher/env paths.
                return new.replace('/', '\\') if '\\' in match[0] else new
        raise AssertionError('Unmatched path rule')
    text = path_pattern.sub(replace, text)
    for old, new in renames.items():
        text = text.replace(old, new)
    return text


def rewrite_json(value):
    if isinstance(value, str):
        return rewrite(value)
    if isinstance(value, list):
        return [rewrite_json(v) for v in value]
    if isinstance(value, dict):
        return {k: rewrite_json(v) for k, v in value.items()}
    return value


def sha(path):
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, 'sha256').hexdigest()


def jbytes(value, compact=False):
    return json.dumps(value, **({'separators': (',', ':')} if compact else {'indent': 2})).encode('utf-8')


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def inside(path, root):
    resolved = path.resolve()
    if not resolved.is_relative_to(root):
        raise ValueError('Path escapes intended directory: ' + str(path))
    return resolved


index = read(pack / 'FILES.json')
release = read(pack / 'REPACK.json')
sources = []
for name in ['DXVK', 'DXVK-backups', 'GraphicsOverhaul', 'VisualOverhaul',
             'GraphicsOverhaul-backups', 'VisualOverhaul-backups']:
    folder = client / name
    if folder.exists():
        sources.extend(p for p in folder.rglob('*') if p.is_file())
sources.extend(client / name for name in ['Enable-FreeFlight.ps1', 'VirusTotal Reports.html', 'portable-client.json'])
if any(not p.is_file() for p in sources):
    raise ValueError('Missing expected client support file')
sources = sorted(set(sources))
moves, writes = [], {}
for source in sources:
    relative = source.relative_to(client).as_posix()
    target = inside(client / mapped(relative), client)
    inside(source, client)
    if source != target:
        if target.exists():
            raise ValueError('Destination exists: ' + str(target))
        moves.append(dict(source=str(source), target=str(target), bytes=source.stat().st_size))
    # Keep historical backup contents intact. Only current support files need new paths.
    if '-backups/' in relative or source.suffix.lower() not in {'.ps1', '.bat', '.json', '.ini', '.md', '.txt'}:
        continue
    original = source.read_bytes()
    if source.suffix.lower() == '.json':
        updated = jbytes(rewrite_json(json.loads(original.decode('utf-8-sig'))))
    else:
        text = rewrite(original.decode('utf-8-sig'))
        if source.suffix.lower() == '.ps1':
            text = text.replace('(Split-Path -Parent $PSScriptRoot)',
                                "([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\\..')))")
            if relative == 'Enable-FreeFlight.ps1':
                text = text.replace('[string]$ClientPath = $PSScriptRoot',
                                    "[string]$ClientPath = ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\\..')))")
        # The moved cursor helper's legacy relative copy targets the renderer directory.
        text = text.replace("'DXVK/' + $helper", "'Mods/Renderer/' + $helper")
        updated = text.encode('utf-8')
    if original != updated:
        writes[target] = updated

for source in [client / 'Aion Start.bat', pack / 'Control/Prepare-Client.ps1', pack / 'README.txt']:
    original = source.read_bytes()
    updated = rewrite(original.decode('utf-8-sig')).encode('utf-8')
    if source.name == 'Aion Start.bat':
        updated = re.sub(rb'(?<=%~dp0)[^"\r\n]+', lambda m: m[0].replace(b'/', b'\\'), updated)
    if original != updated:
        writes[source] = updated

# Every fixed-location native mod is listed here instead of moving its loaded binary/archive.
features = {
    'FreeFlight': ['system.cfg'],
    'Renderer': ['bin32/d3d9.dll', 'bin64/d3d9.dll', 'dxvk.conf', 'DXVK/renderer.ini', 'SystemOptionGraphics.cfg'],
    'GraphicsMenu': ['bin64/AionGraphicsMenu.dll', 'bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak'],
    'CursorFix': ['bin64/XRenderD3D9.dll', 'bin64/Game.dll'],
    'RememberLogin': ['bin64/AionRememberLogin.dll', 'bin64/crysystem.dll', 'Data/ui/ui.pak', 'L10N/enu/Data/data.pak'],
    'SpeechBubbles': ['bin64/AionSpeechBubbles.dll', 'SpeechBubbles.ini'],
    'NativeIcons': ['bin64/AionIconBridge.dll', 'bin64/AionIconBridge.index'],
    'MarketShortcut': ['bin64/AionMarketShortcut.dll'],
    'PlayerBotBar': ['bin64/version.dll', 'PlayerBotBar.ini'],
    'LoginBranding': ['Data/ui/ui.pak', 'Data/ui/skins/skin00.pak', 'L10N/enu/Data/data.pak'],
}
existing_index = {e['path'].casefold(): e for e in index['files']}
source_inventory = Path(__file__).resolve().parents[1] / 'docs/INSTALLED_MODS.json'
covered = {p.casefold() for paths in features.values() for p in paths}
features['NativeClientAssets'] = [e['path'] for e in read(source_inventory)['clientFiles']
    if mapped(e['path']) == e['path'] and e['path'].casefold() not in covered
    and e['path'].casefold() != 'aion start.bat']
native_inventory = {}
for name, paths in features.items():
    found = []
    for relative in paths:
        entry = existing_index.get(('Client/' + relative).casefold())
        if entry and (client / relative).is_file():
            found.append(dict(path=relative, packagedSha256=entry['sha256'], bytes=entry['bytes']))
    native_inventory[name] = found
    if not any(mapped(p.relative_to(client).as_posix()).startswith('Mods/' + name + '/') for p in sources):
        writes[client / 'Mods' / name / 'README.txt'] = (
            name + '\n\nAlready installed. Aion loads this feature from its native game paths:\n' +
            ''.join('  ' + e['path'] + '\n' for e in found) +
            '\nKeep these runtime files in place. Mods/InstalledMods.json records their packaged hashes.\n'
        ).encode()

guide = '''AION AETHERFALL - CLIENT MODS

These features are already installed. Launch Aion normally with Aion Start.bat.
The launcher applies or verifies its startup settings from the folders below.
Do not run a restore script unless you intend to remove that feature.

FreeFlight         Install-FreeFlight.ps1 enables the client flight setting.
Renderer           Vulkan/Direct3D selection, graphics settings and renderer recovery.
GraphicsMenu       Install-GraphicsMenu.ps1 maintains the in-game renderer option.
CursorFix          Install-CursorFix.ps1 maintains the native cursor correction.
GraphicsOverhaul   Install-GraphicsOverhaul.ps1, world textures and Vulkan post effects.
VisualOverhaul     Install-VisualOverhaul.ps1 and the earlier Poeta/Sanctum visual package.
RememberLogin      Installed account/password remembrance and login-screen changes.
SpeechBubbles      Installed speech-bubble DLL and its native configuration path.
NativeIcons        Installed native item icon bridge and index.
MarketShortcut     Installed marketplace shortcut.
PlayerBotBar       Installed companion toolbar and its native configuration path.
LoginBranding      Installed Aetherfall login artwork and announcements.
NativeClientAssets Other installed native archives, client integration and signatures.
Security           VirusTotal Reports.html: exact hashes, findings and public reports.
Backups            Existing renderer/menu originals and future feature recovery folders.

Runtime locations
DLLs remain in bin32/bin64 and modified game archives remain in Data, L10N,
Levels and effects because Aion loads them there. Native settings such as
SpeechBubbles.ini, PlayerBotBar.ini, dxvk.conf and SystemOptionGraphics.cfg
also stay at their required locations. The in-game renderer switch reads
DXVK/renderer.ini directly; that small compatibility file remains there.
InstalledMods.json lists fixed-location feature files and packaged hashes.

Installers and restore scripts
Use the feature-specific Install-*.ps1 file in the feature folder. Existing
command-line parameters, including -ClientPath and -PackagePath where required,
still apply. Aion Start.bat supplies these automatically for normal startup.
Restore scripts retain their existing checks and recovery requirements. Moving
the tools does not recreate older archive backups absent from this repack.
portable-client.json is used by the repack launcher when the whole repack moves.
'''
writes[client / 'Mods/README.txt'] = guide.encode()
writes[client / 'Mods/InstalledMods.json'] = jbytes(dict(version=1, features=native_inventory,
    note='Fixed runtime paths; hashes inherited from the package inventory. No game binaries were rebuilt.'))

# Update only inventory paths and the hashes of the changed support files.
for entry in index['files']:
    if entry['path'].casefold().startswith('client/'):
        entry['path'] = 'Client/' + mapped(entry['path'][7:])
indexed = {e['path'].casefold(): e for e in index['files']}
if len(indexed) != len(index['files']):
    raise ValueError('Inventory path collision')
release = rewrite_json(release)
release['clientModsLayout'] = dict(root='Client/Mods', organizedAt=datetime.now().astimezone().isoformat(),
    movedFiles=len(moves), originalClientChanged=False,
    runtimeNote='Native loaded DLL/archive/settings locations retained; support tools and packages grouped by feature.')
writes[pack / 'REPACK.json'] = jbytes(release)
for path, data in writes.items():
    relative = path.relative_to(pack).as_posix()
    entry = indexed.get(relative.casefold())
    if entry is None:
        entry = dict(path=relative, mutable=False)
        index['files'].append(entry)
        indexed[relative.casefold()] = entry
    entry.update(sha256=hashlib.sha256(data).hexdigest(), bytes=len(data))
writes[pack / 'FILES.json'] = jbytes(index, compact=True)

# Resolve every source/destination before any move. No recursive move/delete is used.
for move in moves:
    inside(Path(move['source']), client)
    inside(Path(move['target']), client)
for path in writes:
    inside(path, pack)
backup.mkdir(parents=True, exist_ok=False)
text_backups = []
move_sources = {Path(m['target']): Path(m['source']) for m in moves}
for i, path in enumerate(writes):
    source = move_sources.get(path, path)
    saved = backup / str(i)
    if source.exists():
        shutil.copy2(source, saved)
        text_backups.append(dict(path=str(path), source=str(source), backup=str(saved)))
    else:
        text_backups.append(dict(path=str(path), source=str(source), backup=None))

native_before = {p: sha(client / p) for p in ['bin64/Game.dll', 'bin64/AionGraphicsMenu.dll',
    'bin64/AionRememberLogin.dll', 'bin64/AionSpeechBubbles.dll', 'bin64/AionIconBridge.dll',
    'bin64/AionMarketShortcut.dll', 'bin64/crysystem.dll', 'bin64/version.dll', 'bin64/XRenderD3D9.dll']}
plan = dict(status='prepared', package=str(pack), moves=moves, edits=text_backups, nativeDlls=native_before)
(backup / 'receipt.json').write_bytes(jbytes(plan))
completed, written = [], []
try:
    for move in moves:
        source, target = Path(move['source']), Path(move['target'])
        target.parent.mkdir(parents=True, exist_ok=True)
        source.rename(target)
        completed.append(move)
    for path, data in writes.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        written.append(path)
        if sha(path) != hashlib.sha256(data).hexdigest():
            raise ValueError('Copied file differs: ' + str(path))
    if any(sha(client / p) != digest for p, digest in native_before.items()):
        raise ValueError('A native DLL changed during reorganization')
except BaseException:
    for entry in reversed(text_backups):
        target = Path(entry['path'])
        if entry['backup']:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(entry['backup'], target)
        elif target in written:
            target.unlink()
    for move in reversed(completed):
        source, target = Path(move['source']), Path(move['target'])
        source.parent.mkdir(parents=True, exist_ok=True)
        target.rename(source)
    raise

# Remove only now-empty old folders. Each candidate is checked inside the client.
for root_name in ['DXVK', 'DXVK-backups', 'GraphicsOverhaul', 'VisualOverhaul',
                  'GraphicsOverhaul-backups', 'VisualOverhaul-backups']:
    root = client / root_name
    if root.is_dir():
        folders = sorted((p for p in root.rglob('*') if p.is_dir()), key=lambda p: len(p.parts), reverse=True)
        for folder in folders + [root]:
            inside(folder, client)
            if not any(folder.iterdir()):
                folder.rmdir()
plan.update(status='installed', installedAt=datetime.now().astimezone().isoformat(),
    changedSupportFiles=len(writes), ordinaryTestsRun=False, startup=False,
    verification='Moved files retain their inventory hashes; changed support-file copies and nine native DLL hashes verified.')
(backup / 'receipt.json').write_bytes(jbytes(plan))
print(json.dumps(dict(status='installed', mods=str(client / 'Mods'), movedFiles=len(moves),
    changedSupportFiles=len(writes), backup=str(backup), nativeDllsUnchanged=True), indent=2))
