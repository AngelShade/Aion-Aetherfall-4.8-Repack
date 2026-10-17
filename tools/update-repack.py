"""Update an existing portable repack from installed files, without launching games or databases."""
import argparse
import hashlib
import json
import os
import re
import shutil
from datetime import datetime
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def encoded(value, compact=False):
    return json.dumps(value, **({'separators': (',', ':')} if compact else {'indent': 2})).encode()


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


parser = argparse.ArgumentParser(description=__doc__)
for name in ['package', 'client', 'baseline-build', 'current-build', 'launcher', 'stage', 'backup']:
    parser.add_argument('--'+name, required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
pack, client = args.package.resolve(strict=True), args.client.resolve(strict=True)
stage, backup = args.stage.resolve(), args.backup.resolve()
for folder in [stage, backup]:
    if folder.exists() or folder.is_relative_to(pack) or folder.is_relative_to(client):
        raise ValueError('Use fresh external stage and backup directories')
if not all((pack/name).exists() for name in ['FILES.json', 'REPACK.json', 'Client', 'Server', 'Control']):
    raise ValueError('Expected the existing portable repack')
if (pack/'Server/database/data').exists():
    raise ValueError('This update includes fresh-schema changes; an initialized database needs a separate migration')
stage.mkdir(parents=True)
index, release = read(pack/'FILES.json'), read(pack/'REPACK.json')
entries = {e['path'].casefold(): e for e in index['files']}
old_inputs = read(args.baseline_build/'build-manifest.json')['inputs']
current_inputs = read(args.current_build/'build-manifest.json')['inputs']
inventory = read(repo/'docs/INSTALLED_MODS.json')
build = read(args.current_build/'build-manifest.json')
overlays = {}
for key, delivery in inventory.items():
    if not key.startswith('last') or not isinstance(delivery, dict) or not delivery.get('receipt'):
        continue
    receipt_path = Path(delivery['receipt'])
    if not receipt_path.is_file():
        continue
    for record in read(receipt_path).get('files', []):
        source = Path(record['path'])
        if source.is_absolute() and source.is_relative_to(repo/'target-deploy'):
            overlays[source.relative_to(repo/'target-deploy').as_posix()] = record.get('after')
        elif record.get('component') == 'server':
            overlays['game-server/'+record['path']] = record.get('after')
if digest(repo/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar') != build['artifacts']['assembly-libs/game-server-4.8-SNAPSHOT.jar']:
    raise ValueError('The installed GameServer differs from its normal builder artifact')
version = read(repo/'release.json')
release_number = version['Version'].split('.')[-1].zfill(3)
writes, removals, metadata_files = {}, {}, set()


def target(relative):
    relative = entries.get(relative.casefold(), {}).get('path', relative)
    path = pack/relative
    if not path.resolve().is_relative_to(pack):
        raise ValueError('Path escapes package: '+relative)
    return relative, path


def put(relative, reason, source=None, data=None, expected=None, mutable=None):
    relative, destination = target(relative)
    if source is not None:
        sha, size = digest(source), source.stat().st_size
    else:
        sha, size = hashlib.sha256(data).hexdigest(), len(data)
    if expected is not None and sha != expected:
        raise ValueError('Installed source differs from its receipt: '+str(source))
    old = entries.get(relative.casefold())
    if old and old['sha256'] == sha and destination.is_file() and destination.stat().st_size == size:
        return
    if destination.is_file() and destination.stat().st_size == size and digest(destination) == sha:
        # A mutable runtime file may already match the desired source.
        if old:
            old.update(sha256=sha, bytes=size)
        return
    output = stage/relative
    output.parent.mkdir(parents=True, exist_ok=True)
    if source is not None:
        shutil.copy2(source, output)
    else:
        output.write_bytes(data)
    writes[relative] = dict(path=relative, sha256=sha, bytes=size, reason=reason,
                            mutable=old.get('mutable', False) if old else bool(mutable))


def remove(relative, reason):
    relative, path = target(relative)
    if path.exists():
        removals[relative] = dict(path=relative, sha256=digest(path), reason=reason)


def portable(value):
    if isinstance(value, dict):
        return {k: portable(v) for k, v in value.items()}
    if isinstance(value, list):
        return [portable(v) for v in value]
    if isinstance(value, str):
        return value.replace(str(client), str(pack/'Client')).replace(client.as_posix(), (pack/'Client').as_posix())
    return value


def property_keys(text):
    return {line.split('=', 1)[0].strip() for line in text.splitlines()
            if '=' in line and not line.lstrip().startswith(('#', '!'))}


print('Preparing server binaries, configuration and changed native resources.', flush=True)
for component in ['game-server', 'login-server', 'chat-server']:
    live = repo/'target-deploy'/component
    for source in sorted((live/'libs').glob('*.jar')):
        put('Server/'+component+'/libs/'+source.name, 'Current installed server library', source=source)
    available = {p.name.casefold() for p in (live/'libs').glob('*.jar')}
    for obsolete in (pack/'Server'/component/'libs').glob('*.jar'):
        if obsolete.name.casefold() not in available:
            remove(obsolete.relative_to(pack).as_posix(), 'Retire superseded classpath library')
    for source in sorted((live/'config').rglob('*')):
        if not source.is_file():
            continue
        relative = source.relative_to(live).as_posix()
        packaged = 'Server/'+component+'/'+relative
        if relative.startswith('config/network/') or re.fullmatch(r'config/my\w+\.properties', relative):
            existing = pack/packaged
            if not existing.is_file():
                raise ValueError('Missing portable connection configuration: '+packaged)
            # Preserve package-local credentials, ports and advertisement values.
            if source.suffix == '.properties':
                original = existing.read_text(encoding='utf-8-sig')
                keys = property_keys(original)
                missing = [line for line in source.read_text(encoding='utf-8-sig').splitlines()
                           if '=' in line and not line.lstrip().startswith(('#', '!'))
                           and line.split('=', 1)[0].strip() not in keys]
                if missing:
                    put(packaged, 'New connection defaults; existing package-local values retained',
                        data=(original.rstrip()+'\n\n# Additional server defaults\n'+'\n'.join(missing)+'\n').encode())
            continue
        put(packaged, 'Current installed server configuration and media', source=source)
    for key, expected in current_inputs.items():
        if not key.startswith(component+'/data/'):
            continue
        relative = key[len(component)+1:]
        existing = entries.get(('Server/'+component+'/'+relative).casefold())
        expected = overlays.get(key) or expected
        if existing and existing['sha256'] == expected:
            continue
        source = live/relative
        if not source.is_file():
            raise ValueError('Current built resource is missing: '+str(source))
        # Installed data overlays (such as release084 spawns) outrank build inputs.
        put('Server/'+component+'/'+relative, 'Current installed native server resource', source=source, expected=expected)
    for key in old_inputs:
        if key.startswith(component+'/data/') and key not in current_inputs:
            remove('Server/'+key, 'Retire resource removed by the upstream merge')
        if key.startswith(component+'/config/') and key not in current_inputs:
            remove('Server/'+key, 'Retire configuration removed by the upstream merge')
    for source in (live/'sql').glob('*.sql'):
        put('Server/'+component+'/sql/'+source.name, 'Installed feature schema reference', source=source)

print('Preparing installed client features and portable Mods support.', flush=True)
native_paths = {entry['path'] for entry in inventory['clientFiles'] if not entry['path'].startswith('Mods/')}
mods = read(client/'Mods/InstalledMods.json')
for records in mods['features'].values():
    native_paths.update(record['path'] for record in records if not record['path'].startswith('Mods/'))
owner_settings = {'system.cfg', 'SystemOptionGraphics.cfg', 'DXVK/renderer.ini', 'PlayerBotBar.ini', 'SpeechBubbles.ini'}
for relative in sorted(native_paths):
    if relative in owner_settings or relative == 'Aetherfall Launcher.exe':
        continue
    put('Client/'+relative, 'Current installed native client feature', source=client/relative)
support_files = []
for directory, folders, files in os.walk(client/'Mods'):
    folders[:] = [name for name in folders if name.casefold() not in {'backups', 'logs', 'cache', '__pycache__'}]
    support_files.extend(Path(directory)/name for name in files)
for source in sorted(support_files):
    relative = source.relative_to(client).as_posix()
    parts = source.relative_to(client/'Mods').parts
    # Historical backups, logs, compiled shader caches and per-user preferences
    # are not release payloads. Only active renderer recovery is added below.
    if not source.is_file() or any(part.casefold() in {'backups', 'logs', 'cache', '__pycache__'} for part in parts):
        continue
    if source.suffix.casefold() in {'.log', '.dmp', '.pdb', '.obj', '.lib', '.exp', '.tmp'}:
        continue
    if relative in {'Mods/InstalledMods.json', 'Mods/Launcher/connection.json', 'Mods/Launcher/preferences.json', 'Mods/portable-client.json'}:
        continue
    if source.suffix.lower() == '.json':
        obj = read(source)
        updated = portable(obj)
        if updated != obj:
            metadata_files.add(relative)
            put('Client/'+relative, 'Portable client ownership and recovery metadata', data=encoded(updated))
        else:
            put('Client/'+relative, 'Current client mod metadata', source=source)
    elif relative.endswith('/ReShade.ini'):
        data = source.read_text(encoding='utf-8-sig')
        put('Client/'+relative, 'Portable shader search and cache paths', data=portable(data).encode())
    else:
        put('Client/'+relative, 'Current structured client mod support', source=source)

# Include native graphics assets and only the originals required by the active
# renderer/menu recovery guards, preserving all cumulative client modifications.
for name in ['GraphicsOverhaul', 'VisualOverhaul']:
    for manifest in (client/'Mods'/name/'packages').glob('*/manifest.json'):
        for record in read(manifest).get('files', []):
            relative = record['path']
            if (client/relative).is_file():
                inherited = entries.get(('Client/'+relative).casefold())
                if inherited and inherited['sha256'] == record.get('staged') and (pack/'Client'/relative).is_file():
                    continue  # The same installed graphics package is already in the repack.
                put('Client/'+relative, 'Installed native graphics asset', source=client/relative)
graphics = read(client/'Mods/GraphicsMenu/installed.json')
renderer = read(client/'Mods/Renderer/installed.json')
recover = [Path(graphics['backupRoot'])/e['path'] for e in graphics['files'] if e.get('original')]
recover += [Path(e['backupPath']) for e in renderer.get('nativeCursorPatch', {}).get('files', []) if e.get('original')]
for source in recover:
    if not source.resolve().is_relative_to(client/'Mods/Backups'):
        raise ValueError('Renderer recovery is outside the client: '+str(source))
    put('Client/'+source.relative_to(client).as_posix(), 'Active native renderer recovery baseline', source=source)

put('Client/Aetherfall Launcher.exe', 'Normal graphical launcher build with portable preflight', source=args.launcher)
connection = pack/'Client/Mods/Launcher/connection.json'
if connection.is_file():
    settings = read(connection)
else:
    bat = (pack/'Client/Aion Start.bat').read_text(encoding='utf-8-sig')
    command = re.search(r'^start\s+""\s+"bin64\\aion\.bin"\s+([^\r\n]+)', bat, re.I | re.M)
    if not command:
        raise ValueError('Cannot preserve repack connection arguments')
    settings = dict(Arguments=command[1].strip())
put('Client/Mods/Launcher/connection.json', 'Existing local client connection arguments', data=encoded(settings), mutable=True)
if not (pack/'Client/Mods/Launcher/preferences.json').is_file():
    put('Client/Mods/Launcher/preferences.json', 'Default graphical launcher preferences',
        data=encoded(dict(Animations=True, CloseAfter=False)), mutable=True)
remove('Client/Aion Start.bat', 'Replaced by the graphical launcher with built-in preparation')
put('3 - Start Client.cmd', 'Compatibility shortcut to the graphical launcher',
    data=b'@echo off\r\nstart "" "%~dp0Client\\Aetherfall Launcher.exe"\r\n')


def final_sha(relative):
    canonical, path = target(relative)
    return writes[canonical]['sha256'] if canonical in writes else digest(path)


for records in mods['features'].values():
    for record in records:
        relative = 'Client/'+record['path']
        if (pack/relative).is_file() or relative in writes:
            record['installedSha256'] = final_sha(relative)
            record['bytes'] = (stage/relative if relative in writes else pack/relative).stat().st_size
mods = portable(mods)
put('Client/Mods/InstalledMods.json', 'Current native feature inventory with packaged hashes', data=encoded(mods))
portable_state = dict(version=1, preparedRoot=str(pack/'Client'), gameDllSha256=final_sha('Client/bin64/Game.dll'),
                      aionBinSha256=final_sha('Client/bin64/aion.bin'), metadataFiles=sorted(metadata_files))
put('Client/Mods/portable-client.json', 'Current portable ownership baseline', data=encoded(portable_state))
prepare = (pack/'Control/Prepare-Client.ps1').read_text(encoding='utf-8-sig')
anchor="$old = [string]$state.preparedRoot"
if '$state.aionBinSha256' not in prepare:
    prepare = prepare.replace(anchor,
        "if ((Get-FileHash -LiteralPath (Join-Path $client 'bin64/aion.bin')).Hash -ne $state.aionBinSha256) { throw 'Client startup binary changed after packaging.' }\n"+anchor)
put('Control/Prepare-Client.ps1', 'Portable preflight includes the native startup bridge', data=prepare.encode())

print('Preparing fresh schema compatibility and release notes.', flush=True)
schema_path = pack/'Server/database/schema/aetherfall_gs.sql'
original_schema = schema_path.read_text(encoding='utf-8-sig')
schema = original_schema
for table, after, column, definition in [
    ('players', 'reposte_energy', 'pvp_exp', "bigint NOT NULL DEFAULT '0'"),
    ('player_recipes', 'recipe_id', 'production_limit', "tinyint NOT NULL DEFAULT '0'")]:
    pattern = r'(CREATE TABLE `'+table+r'`\s*\()(.*?)(\n\))'
    match = re.search(pattern, schema, re.S)
    if not match:
        raise ValueError('Missing schema table: '+table)
    body = match[2]
    if '`'+column+'`' not in body:
        body, count = re.subn(r'(^\s*`'+after+r'`[^\n]*\n)',
            lambda m: m[0]+'  `'+column+'` '+definition+',\n', body, count=1, flags=re.M)
        if count != 1:
            raise ValueError('Missing schema column anchor: '+table+'.'+after)
        schema = schema[:match.start(2)]+body+schema[match.end(2):]
schema = schema.replace('`jaw_height`', '`chin_height`').replace('`chin_jut`', '`cheek_bones`')
put('Server/database/schema/aetherfall_gs.sql', 'Fresh schema matches current PvP EXP, crafting and appearance fields', data=schema.encode())
bootstrap = (pack/'Server/database/bootstrap.sql').read_text(encoding='utf-8-sig')
if bootstrap.count(original_schema) != 1:
    raise ValueError('Fresh schema bootstrap differs from its packaged schema')
put('Server/database/bootstrap.sql', 'Current fresh schema; no account or character rows copied',
    data=bootstrap.replace(original_schema, schema, 1).encode())
put('Server/game-server/sql/update.sql', 'Upstream schema migration reference; not executed', source=repo/'game-server/sql/update.sql')
put('release.json', 'Current product release metadata', data=encoded(version))

readme = '''AION AETHERFALL - 4.8 NA - REPACK {version}

START
1. Run "1 - Start Database.cmd". On first use it creates the repack's empty
   databases. Wait for "Database ready".
2. Run "2 - Start Servers.cmd" and wait for GameServer startup to finish.
3. Open "Client/Aetherfall Launcher.exe" and press Play. The launcher prepares
   the client and keeps its loading bar visible until the account/password
   screen reports ready. "3 - Start Client.cmd" opens the same launcher.
4. Log in with a new account name and password. The login server creates a
   normal player account on first login.
5. Close Aion and all three servers normally, then run "4 - Stop Database.cmd".

IN THIS UPDATE
- Graphical launcher with Aetherfall artwork, installed Mods list and settings.
- Startup change to skip the native splash and hold loading behind the launcher.
- Account alts share completed quests and rewards, including past completions.
- Current companion appearance, movement, descent and glide changes.
- Need/Greed/Pass loot panels and matching server/bot distribution rules.
- Wardrobe HUD shortcut, larger inventories and current admin inventory controls.
- Upstream 4.8 repairs and the 30-second Altgard Mosbear quest-target respawns.
- Current native UI, item/icon, graphics, browser and login modifications.
- Cash Shop quantity/Max controls shared with Central Market.
- Current combat regroup, corridor movement and Aethertech companion fixes.
- Supported Gson JSON dependency across GameServer, LoginServer and ChatServer.

These are copied development updates. Startup and gameplay acceptance remain
the player's next run; file-copy verification does not certify those behaviors.

PORTABILITY AND SETTINGS
Java and MariaDB are bundled. No database Windows service is installed.
The database uses this repack's Server/database/data folder and loopback port
33306. The update does not copy personal accounts, characters or gameplay rows.
Connection credentials stay in the package-local configuration. Server settings
are under Server/game-server/config; client tools and metadata are under Mods.
The native DLLs and archives remain at the game paths where Aion loads them.

Move the whole repack only with every component stopped. The graphical launcher
rebinds graphics ownership and shader paths before starting the moved client.
Historical development logs, shader caches and unrelated backups are excluded;
active native renderer/menu recovery baselines are included.

"5 - Verify Repack.cmd" is an optional full immutable-file check. This update
only verifies copied-file hashes; it does not start the database, servers or game.
VirusTotal reports remain in Client/Mods/Security. Their hashes identify the
specific versions scanned; later modified files are not covered by older reports.
Some engines reported detections. These reports are not a safety certificate.
'''
put('README.txt', 'Current player instructions and update contents', data=readme.replace('{version}', version['Version']).encode())
mods_readme = (client/'Mods/README.txt').read_text(encoding='utf-8-sig')
mods_readme = mods_readme.replace('Aion Start.bat', 'Aetherfall Launcher.exe')
put('Client/Mods/README.txt', 'Current Mods structure and graphical launch instructions', data=mods_readme.encode())
release.update(release=release_number, productVersion=version['Version'], updated=datetime.now().date().isoformat(),
               gameServerSha256=inventory['serverJarSha256'])
release['update'+release_number] = dict(sourceBuild=str(args.current_build), sourceClient=str(client),
    serverComponents=['game-server', 'login-server', 'chat-server'], nativeLauncher=True,
    freshSchemaUpdated=True, sourceRowsCopied=0, testsRun=False, startup=False,
    verification='Hashes of copied files; unchanged package inventory inherited')
release['transforms'].extend(list(writes.values()))
put('REPACK.json', 'Current release package metadata and update record', data=encoded(release))
for relative, record in writes.items():
    entry = entries.get(relative.casefold())
    if entry is None:
        entry = dict(path=relative, mutable=record['mutable'])
        index['files'].append(entry)
        entries[relative.casefold()] = entry
    entry.update(sha256=record['sha256'], bytes=record['bytes'])
index['files'] = [e for e in index['files'] if e['path'] not in removals]
put('FILES.json', 'Update checksums for changed payloads; untouched hashes inherited', data=encoded(index, compact=True))
(stage/'copy-plan.json').write_bytes(encoded(dict(files=list(writes.values()), removed=list(removals.values()))))
print('Prepared', len(writes), 'file copies and', len(removals), 'superseded files to archive.', flush=True)

backup.mkdir(parents=True)
before = {}
for relative in list(writes)+list(removals):
    destination = pack/relative
    before[relative] = digest(destination) if destination.is_file() else None
    if destination.is_file():
        saved = backup/relative
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(destination, saved)
written, removed = [], []
try:
    for relative, record in writes.items():
        destination = pack/relative
        if (digest(destination) if destination.is_file() else None) != before[relative]:
            raise ValueError('Package changed during copying: '+relative)
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_name(destination.name+'.update'+release_number)
        shutil.copy2(stage/relative, temporary)
        os.replace(temporary, destination)
        written.append(relative)
        if digest(destination) != record['sha256']:
            raise ValueError('Copied-file mismatch: '+relative)
    for relative in removals:
        destination = pack/relative
        if digest(destination) != before[relative]:
            raise ValueError('Superseded file changed during copying: '+relative)
        destination.unlink()
        removed.append(relative)
except BaseException:
    for relative in reversed(written+removed):
        destination = pack/relative
        saved = backup/relative
        if saved.is_file():
            shutil.copy2(saved, destination)
        elif destination.exists():
            destination.unlink()
    raise
receipt = dict(status='complete', productVersion=version['Version'], release=release_number, package=str(pack),
    installedAt=datetime.now().astimezone().isoformat(), files=list(writes.values()), removed=list(removals.values()),
    originalFilesChanged=False, sourceRowsCopied=0, testsRun=False, startup=False,
    gameServerSha256=inventory['serverJarSha256'], backup=str(backup),
    verification='All copied hashes match; unchanged payloads inherited from package inventory')
(backup/'receipt.json').write_bytes(encoded(receipt))
print('COMPLETE:', len(writes), 'copied hashes verified; receipt:', backup/'receipt.json', flush=True)
