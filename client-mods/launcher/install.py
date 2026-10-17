"""Install the graphical launcher, preserving connection arguments and game settings."""
import argparse
import hashlib
import json
import shutil
import re
from datetime import datetime
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--stage', type=Path, required=True)
parser.add_argument('--client', type=Path, required=True)
parser.add_argument('--backup', type=Path, required=True)
parser.add_argument('--art-only', action='store_true', help='Replace background artwork and its prompt record without rewriting the executable')
parser.add_argument('--launcher-only', action='store_true', help='Replace only the graphical launcher and its documentation, retaining native modules and artwork')
parser.add_argument('--login-module', action='store_true', help='Include the rebuilt existing native login module for the ready handshake')
parser.add_argument('--startup-bridge', action='store_true', help='Include the matched native splash entry bridge')
args = parser.parse_args()
stage, client, backup = args.stage.resolve(strict=True), args.client.resolve(strict=True), args.backup.resolve()
if backup.exists() or backup.is_relative_to(client) or stage.is_relative_to(client):
    raise ValueError('Use a staged build and a fresh external backup')
if not (client / 'Mods/InstalledMods.json').is_file() or not (client / 'bin64/aion.bin').is_file():
    raise ValueError('Expected organized Aetherfall client')
if (client.parent / 'Control/Prepare-Client.ps1').is_file():
    raise ValueError('Prepare repack portability integration before installing into a repack')
root = Path(__file__).resolve().parents[2]
inventory_path = root / 'docs/INSTALLED_MODS.json'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def js(value):
    return json.dumps(value, indent=2).encode()


payloads = {
    'Aetherfall Launcher.exe': stage / 'Aetherfall Launcher.exe',
    'Mods/Launcher/Assets/atreia.png': stage / 'Mods/Launcher/Assets/atreia.png',
    'Mods/Launcher/Assets/frame.png': stage / 'Mods/Launcher/Assets/frame.png',
    'Mods/Launcher/Assets/aetherfall-logo.png': stage / 'Mods/Launcher/Assets/aetherfall-logo.png',
    'Mods/Launcher/Assets/aion-native-atlas.png': stage / 'Mods/Launcher/Assets/aion-native-atlas.png',
    'Mods/Launcher/Assets/logo-source.json': stage / 'Mods/Launcher/Assets/logo-source.json',
    'Mods/Launcher/README.md': Path(__file__).with_name('README.md'),
    'Mods/Launcher/Assets/prompts.md': Path(__file__).with_name('assets') / 'prompts.md',
}
if args.launcher_only:
    if args.art_only or args.login_module or args.startup_bridge:
        raise ValueError('Launcher-only delivery cannot include artwork or native binary changes')
    payloads = {relative: source for relative, source in payloads.items()
                if relative in {'Aetherfall Launcher.exe', 'Mods/Launcher/README.md'}}
if not args.art_only:
    payloads['Mods/MultiClient/mod.json'] = Path(__file__).with_name('multiclient') / 'mod.json'
    payloads['Mods/MultiClient/README.txt'] = Path(__file__).with_name('multiclient') / 'README.txt'
if args.art_only:
    payloads = {relative: source for relative, source in payloads.items()
                if relative in {'Mods/Launcher/Assets/atreia.png', 'Mods/Launcher/Assets/prompts.md'}}
if args.login_module:
    if args.art_only:
        raise ValueError('Login readiness requires the matching launcher executable')
    payloads['bin64/AionRememberLogin.dll'] = stage / 'bin64/AionRememberLogin.dll'
if args.startup_bridge:
    if not args.login_module:
        raise ValueError('Native startup requires the matching login module')
    bridge = json.loads((stage / 'startup-bridge.json').read_text())
    if sha(client / 'bin64/aion.bin') != bridge['originalSha256']:
        raise ValueError('The client startup binary changed since preparation')
    if sha(stage / 'bin64/aion.bin') != bridge['installedSha256']:
        raise ValueError('The staged startup binary does not match its bridge record')
    payloads['bin64/aion.bin'] = stage / 'bin64/aion.bin'
    payloads['Mods/Launcher/startup-bridge.json'] = stage / 'startup-bridge.json'
if not args.art_only and not args.launcher_only:
    payloads['Mods/release.json'] = stage / 'Mods/release.json'
writes = {client / relative: source.read_bytes() for relative, source in payloads.items()}
if not args.art_only:
    catalog = json.loads(Path(__file__).with_name('mod-descriptions.json').read_text(encoding='utf-8'))
    for folder in sorted((client / 'Mods').iterdir()):
        if not folder.is_dir() or folder.is_symlink() or folder.name == 'Backups':
            continue
        description = folder / 'mod.json'
        if description.exists():
            continue
        info = catalog.get(folder.name, dict(Title=re.sub(r'(?<=[a-z0-9])(?=[A-Z])', ' ', folder.name),
            Description='Additional client features provided by ' + folder.name + '.', Listed=True, Files=[]))
        writes[description] = js(info)
removed = {}
connection = client / 'Mods/Launcher/connection.json'
legacy = client / 'Aion Start.bat'
if not args.art_only:
    if connection.exists():
        settings = json.loads(connection.read_text(encoding='utf-8-sig'))
    else:
        command = re.search(r'^start\s+""\s+"bin64\\aion\.bin"\s+([^\r\n]+)', legacy.read_text(), re.I | re.M)
        if not command:
            raise ValueError('Cannot migrate the existing client connection arguments')
        settings = dict(Arguments=command[1].strip())
    arguments = settings.get('Arguments')
    if not isinstance(arguments, str) or not arguments.strip() or any(c in arguments for c in '&|<>\r\n\0'):
        raise ValueError('Invalid connection arguments')
    if legacy.exists():
        old_data = legacy.read_bytes()
        command = re.search(r'^start\s+""\s+"bin64\\aion\.bin"\s+([^\r\n]+)', old_data.decode(), re.I | re.M)
        if not command or command[1].strip() != arguments.strip():
            raise ValueError('Connection settings and legacy BAT disagree')
        removed[legacy] = old_data
    writes[connection] = js(settings)
    payloads['Mods/Launcher/connection.json'] = connection
preferences = client / 'Mods/Launcher/preferences.json'
if not preferences.exists():
    writes[preferences] = js(dict(Animations=True, CloseAfter=False))
mods_path = client / 'Mods/InstalledMods.json'
mods = json.loads(mods_path.read_text())
launcher_records = {record['path']: record for record in mods['features'].get('Launcher', [])}
for relative in payloads:
    if relative == 'bin64/AionRememberLogin.dll':
        records = mods['features']['RememberLogin']
        record = next(record for record in records if record['path'] == relative)
        if sha(client / relative) != record['installedSha256']:
            raise ValueError('Native login module changed since its installed inventory')
        record.update(installedSha256=hashlib.sha256(writes[client / relative]).hexdigest(), bytes=len(writes[client / relative]))
        continue
    launcher_records[relative] = dict(path=relative, installedSha256=hashlib.sha256(writes[client / relative]).hexdigest(),
                                     bytes=len(writes[client / relative]))
mods['features']['Launcher'] = list(launcher_records.values())
for records in mods['features'].values():
    if isinstance(records, list):
        records[:] = [record for record in records if (client / record['path']) not in removed]
writes[mods_path] = js(mods)
inventory = json.loads(inventory_path.read_text(encoding='utf-8-sig'))
if Path(inventory['clientRoot']).resolve() != client:
    raise ValueError('The delivery inventory belongs to another client')
inventory['clientFiles'] = [record for record in inventory['clientFiles'] if (client / record['path']) not in removed]
by_path = {e['path'].casefold(): e for e in inventory['clientFiles']}
for path, data in writes.items():
    relative = path.relative_to(client).as_posix()
    if relative.startswith('Mods/') and relative.endswith('/mod.json'):
        continue  # Owner-editable descriptions are not protected native payloads.
    record = by_path.get(relative.casefold())
    if record is None:
        record = dict(path=relative)
        inventory['clientFiles'].append(record)
    record['sha256'] = hashlib.sha256(data).hexdigest()
inventory['clientReceipts'].append(str(backup / 'receipt.json'))
inventory['lastIncrementalClientUpdate'] = dict(scope='Graphical Aetherfall launcher; original client only',
    receipt=str(backup / 'receipt.json'), fullInventoryRerun=False)
writes[inventory_path] = js(inventory)
preserved = {relative: sha(client / relative) for relative in
    ['DXVK/renderer.ini', 'system.cfg', 'SystemOptionGraphics.cfg',
     'bin64/Game.dll', 'bin64/AionMarketShortcut.dll']}
backup.mkdir(parents=True)
records = []
for path, data in removed.items():
    saved = backup / path.name
    saved.write_bytes(data)
    if sha(path) != hashlib.sha256(data).hexdigest():
        raise ValueError('Legacy launcher changed during migration')
for i, (path, data) in enumerate(writes.items()):
    saved = backup / str(i)
    before = sha(path) if path.exists() else None
    if path.exists():
        shutil.copy2(path, saved)
    records.append(dict(path=str(path), before=before, after=hashlib.sha256(data).hexdigest(),
                        backup=str(saved) if before else None))
written = []
try:
    for record in records:
        path = Path(record['path'])
        if (sha(path) if path.exists() else None) != record['before']:
            raise ValueError('File changed during preparation: ' + str(path))
        path.parent.mkdir(parents=True, exist_ok=True)
        written.append(record)
        path.write_bytes(writes[path])
        if sha(path) != record['after']:
            raise ValueError('Copied-file mismatch: ' + str(path))
    if any(sha(client / relative) != digest for relative, digest in preserved.items()):
        raise ValueError('Existing game/startup file changed during installation')
    for path, data in removed.items():
        if path.read_bytes() != data:
            raise ValueError('Legacy launcher changed during installation')
        path.unlink()
except BaseException:
    for path, data in removed.items():
        if not path.exists():
            path.write_bytes(data)
    for record in reversed(written):
        path = Path(record['path'])
        if record['backup']:
            shutil.copy2(record['backup'], path)
        elif path.exists():
            path.unlink()
    raise
receipt = dict(status='installed', installedAt=datetime.now().astimezone().isoformat(),
    removed=[dict(path=str(path),sha256=hashlib.sha256(data).hexdigest(),backup=str(backup / path.name)) for path,data in removed.items()],
    files=records, preserved=preserved, ordinaryTestsRun=False, gameStarted=False, repackChanged=False,
    sourceHashes={p.name: sha(p) for p in Path(__file__).parent.iterdir() if p.is_file()},
    loginModuleSourceSha256=sha(root / 'client-mods/remember-login/remember_login.cpp') if args.login_module else None)
if args.startup_bridge:
    receipt['startupGateSourceSha256'] = sha(root / 'client-mods/remember-login/startup_gate.h')
    receipt['startupBridge'] = bridge
(backup / 'receipt.json').write_bytes(js(receipt))
print('Installed:', client / 'Aetherfall Launcher.exe')
print('Receipt:', backup / 'receipt.json')
