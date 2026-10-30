"""Deliver exact normal language outputs to original and English-core repack only."""
import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

from optional_languages import optional_path, validate_core
from release_workspace import release_root


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def encoded(value):
    return (json.dumps(value, indent=2)+'\n').encode()


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def closed(roots):
    # Get-Process is an accessible native process enumeration even when CIM is
    # unavailable. Failure or an unreadable affected process is never closure.
    command = "$ErrorActionPreference='Stop'; $rows=@(Get-Process -ErrorAction Stop | Where-Object { $_.ProcessName -match '^(aion|Aetherfall Launcher|Aetherfall Hub|Aetherfall Setup)(\\.bin)?$' } | ForEach-Object { if(-not $_.Path){throw 'Cannot inspect affected process path'}; [PSCustomObject]@{name=$_.ProcessName;path=$_.Path;id=$_.Id} }); ConvertTo-Json -InputObject $rows -Compress"
    rows = json.loads(subprocess.check_output(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', command], text=True))
    for row in rows:
        if any(Path(row['path']).resolve().is_relative_to(root) for root in roots):
            raise RuntimeError('Close the affected client/launcher/Hub normally: '+row['name'])
    # A restricted process view can omit a desktop process. Also require exclusive
    # access to the known affected executable paths; this probe changes no bytes.
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                  wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    kernel.CreateFileW.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    names = ['Aetherfall Launcher.exe', 'Aetherfall Hub.exe', 'Aetherfall Setup.exe',
             'bin64/aion.bin', 'bin32/aion.bin', 'Client/Aetherfall Launcher.exe',
             'Client/bin64/aion.bin', 'Client/bin32/aion.bin']
    for root in roots:
        for name in names:
            path = root/name
            if path.is_file():
                handle = kernel.CreateFileW(str(path), 0xC0000000, 0, None, 3, 0x80, None)
                if handle == ctypes.c_void_p(-1).value:
                    code = ctypes.get_last_error()
                    raise RuntimeError('Affected binary is open or inaccessible; close it normally before delivery: '+str(path)+' (Windows '+str(code)+')')
                kernel.CloseHandle(handle)


def update_inventory(document, changes):
    for feature, names in [('Launcher', ['Aetherfall Launcher.exe', 'Mods/Launcher/README.md']),
                           ('Languages', [p for p in changes if p.startswith('Mods/Languages/')])]:
        records = document.setdefault('features', {}).setdefault(feature, [])
        mapped = {row['path']: row for row in records}
        for relative in names:
            source = changes[relative]
            row = mapped.get(relative)
            if row is None:
                row = dict(path=relative)
                records.append(row)
            row.update(installedSha256=sha(source), bytes=source.stat().st_size)
    return document


def resume_delivery(plan_path, backup, stage, hub_source, roots):
    """Finish an interrupted affected-file copy from its preserved exact plan."""
    expected_plan = backup.parent/(backup.name+'-metadata')/'delivery-plan.json'
    if plan_path != expected_plan or not backup.is_dir():
        raise ValueError('Resume requires the matching existing plan and backup')
    plan = read(plan_path)
    if Path(plan['normalBuild']) != stage:
        raise ValueError('Resume normal build differs from the plan')
    for binary, manifest, field in [(stage/'Aetherfall Launcher.exe', stage/'launcher-build.json', 'launcherSha256'),
                                    (hub_source, Path(str(hub_source)+'.build.json'), 'hubSha256')]:
        document = read(manifest)
        if sha(binary) != document[field]:
            raise ValueError('Normal output changed before resume')
        for name, digest in document['inputs'].items():
            source = Path(name) if Path(name).is_absolute() else Path(__file__).resolve().parents[2]/name
            if sha(source) != digest:
                raise ValueError('Build input changed before resume: '+name)
    closed(roots)
    for root, values in plan['preserved'].items():
        if any(sha(Path(root)/name) != digest for name, digest in values.items()):
            raise ValueError('Protected settings/resources changed before resume')
    pending = []
    for entry in plan['files']:
        root, relative = Path(entry['root']), entry['path']
        target, source = root/relative, Path(entry['source'])
        if root not in roots or not target.resolve().is_relative_to(root) or sha(source) != entry['sha256']:
            raise ValueError('Invalid or changed resume source/destination')
        if entry['before'] and sha(backup/entry['group']/relative) != entry['before']:
            raise ValueError('Resume backup checksum differs')
        current = sha(target) if target.is_file() else None
        if current == entry['sha256']:
            continue
        if current != entry['before']:
            raise ValueError('Resume destination contains newer or partial bytes: '+relative)
        pending.append(entry)
    for entry in pending:
        closed(roots)
        target = Path(entry['root'])/entry['path']
        if (sha(target) if target.is_file() else None) != entry['before']:
            raise ValueError('Resume destination changed during preparation')
        shutil.copy2(entry['source'], target)
        if sha(target) != entry['sha256']:
            raise ValueError('Resumed copy checksum differs')
    for entry in plan['files']:
        if sha(Path(entry['root'])/entry['path']) != entry['sha256']:
            raise ValueError('Final resumed delivery checksum differs')
    for root, values in plan['preserved'].items():
        if any(sha(Path(root)/name) != digest for name, digest in values.items()):
            raise ValueError('Protected settings/resources changed during resume')
    plan['resumedAfterPartialDelivery'] = True
    (backup/'receipt.json').write_bytes(encoded(plan))
    print(json.dumps(dict(status='installed', resumedFiles=len(pending), receipt=str(backup/'receipt.json'), publicUpload=False)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['client', 'stage', 'text-stage', 'backup']:
        parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--expected-original-launcher', required=True)
    parser.add_argument('--hub-stage', type=Path, help='Exact normal Hub builder output')
    parser.add_argument('--expected-original-hub')
    parser.add_argument('--expected-repack-hub')
    parser.add_argument('--resume-plan', type=Path, help='Finish a verified interrupted delivery from its exact existing plan')
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--refresh-prepared', action='store_true', help='Refresh this same unpublished local version after a focused correction')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    client, stage, texts, backup = [p.resolve() for p in [args.client, args.stage, args.text_stage, args.backup]]
    dist = release_root(repo)
    package = Path(read(dist/'Maintenance/release-layout.json')['repackFolder']).resolve()
    if args.resume_plan:
        if not args.hub_stage:
            raise ValueError('Resume requires the normal Hub output')
        resume_delivery(args.resume_plan.resolve(), backup, stage, args.hub_stage.resolve(), [client, package, repo])
        return
    if backup.exists() or any(backup.is_relative_to(root) for root in [repo, client, package]):
        raise ValueError('Use fresh external backup output')
    build = read(stage/'launcher-build.json')
    if sha(stage/'Aetherfall Launcher.exe') != build['launcherSha256']:
        raise ValueError('Normal launcher output changed')
    for path, digest in build['inputs'].items():
        if sha(repo/path) != digest:
            raise ValueError('Maintained launcher source changed after normal build: '+path)
    if sha(client/'Aetherfall Launcher.exe') != args.expected_original_launcher.lower():
        raise ValueError('A newer original launcher was installed; reconcile before delivery')
    hub_source = args.hub_stage.resolve() if args.hub_stage else None
    if hub_source:
        hub_build = read(Path(str(hub_source)+'.build.json'))
        if sha(hub_source) != hub_build['hubSha256']:
            raise ValueError('Normal Hub output changed')
        for path, digest in hub_build['inputs'].items():
            if sha(Path(path)) != digest:
                raise ValueError('Maintained Hub source changed after normal build: '+path)
        for path, expected in [(repo/'Aetherfall Hub.exe', args.expected_original_hub),
                               (package/'Aetherfall Hub.exe', args.expected_repack_hub)]:
            if not expected or sha(path) != expected.lower():
                raise ValueError('Hub installation changed; reconcile before delivery: '+str(path))
    old_version = read(package/'release.json')['Version']
    if tuple(map(int, args.version.split('.'))) < tuple(map(int, old_version.split('.'))) or (args.version == old_version and not args.refresh_prepared):
        raise ValueError('Repack version changed or target does not advance')
    catalog = read(stage/'Mods/Languages/catalog.json')
    original_changes = {'Aetherfall Launcher.exe': stage/'Aetherfall Launcher.exe',
                        'Mods/Launcher/README.md': repo/'client-mods/launcher/README.md',
                        'Mods/Languages/catalog.json': stage/'Mods/Languages/catalog.json'}
    for pack in catalog['packs']:
        for entry in pack['files']:
            source = (texts if pack['kind'] == 'text' else client)/entry['path']
            if source.stat().st_size != entry['bytes'] or sha(source) != entry['sha256']:
                raise ValueError('Prepared language source changed: '+entry['path'])
            if pack['kind'] == 'text':
                coverage = read(source.parent.parent/'coverage.json')
                if coverage['originalSha256'] != sha(client/'L10N/enu/data/data.pak'):
                    raise ValueError('Original English resources changed after text preparation')
                original_changes[entry['path']] = source
    scratch = backup.parent/(backup.name+'-metadata')
    if scratch.exists():
        raise FileExistsError('Use fresh metadata staging')
    scratch.mkdir(parents=True)
    def staged(relative, document, compact=False):
        path = scratch/relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(((json.dumps(document, separators=(',', ':'))+'\n').encode() if compact else encoded(document)) if not isinstance(document, bytes) else document)
        return path
    original_inventory = update_inventory(read(client/'Mods/InstalledMods.json'), original_changes)
    original_inventory['optionalLanguagePacks'] = dict(catalog='Mods/Languages/catalog.json',
        text=['enu', 'kor', 'deu', 'fra', 'esn', 'rus', 'chs'], voices=['enu', 'kor', 'jpn'])
    original_changes['Mods/InstalledMods.json'] = staged('original/InstalledMods.json', original_inventory)
    repack_changes = {'Client/'+p: source for p, source in original_changes.items()
                      if not p.startswith('Mods/Languages/Packs/') and p != 'Mods/InstalledMods.json'}
    if hub_source:
        repack_changes['Aetherfall Hub.exe'] = hub_source
    release = dict(Product='Aion Aetherfall Repack', Version=args.version)
    repack_changes.update({'release.json': staged('repack/release.json', release),
                          'Client/Mods/release.json': staged('repack/client-release.json', release),
                          'CHANGELOG.md': repo/'CHANGELOG.md'})
    identity = read(package/'REPACK.json')
    identity.update(productVersion=args.version, release=args.version.replace('.', ''),
                    updatedAt=datetime.now().astimezone().isoformat(),
                    optionalLanguagePacks=dict(catalog='Client/Mods/Languages/catalog.json',
                                               englishIncluded=True, independentTextAndVoice=True))
    repack_changes['REPACK.json'] = staged('repack/REPACK.json', identity)
    inventory = update_inventory(read(package/'Client/Mods/InstalledMods.json'),
                                 {p.removeprefix('Client/'): src for p, src in repack_changes.items() if p.startswith('Client/')})
    for records in inventory.get('features', {}).values():
        records[:] = [row for row in records if not optional_path('Client/'+row['path'])]
    inventory['optionalLanguagePacks'] = dict(catalog='Mods/Languages/catalog.json', downloadOnDemand=True,
                                             installedChoicesPreserved=True)
    repack_changes['Client/Mods/InstalledMods.json'] = staged('repack/InstalledMods.json', inventory)
    index = read(package/'FILES.json')
    retired = [row for row in index['files'] if optional_path(row['path'])]
    index['files'] = [row for row in index['files'] if not optional_path(row['path'])]
    mapped = {row['path'].casefold(): row for row in index['files']}
    for relative, source in repack_changes.items():
        row = mapped.get(relative.casefold())
        if row is None:
            row = dict(path=relative, mutable=False)
            index['files'].append(row)
        row.update(bytes=source.stat().st_size, sha256=sha(source))
    index['fileCount'] = len(index['files'])
    if 'totalBytes' in index:
        index['totalBytes'] = sum(row['bytes'] for row in index['files'])
    validate_core(package, index['files'])
    repack_changes['FILES.json'] = staged('repack/FILES.json', index, compact=True)
    docs = read(repo/'docs/INSTALLED_MODS.json')
    preferences = read(client/'Mods/Launcher/preferences.json')
    docs['optionalLanguagePacks20261030'] = dict(status='Installed original and prepared English-core repack',
        receipt=str(backup/'receipt.json'), normalBuild=str(stage),
        textLanguages=['enu', 'kor', 'deu', 'fra', 'esn', 'rus', 'chs'],
        voiceLanguages=['enu', 'kor', 'jpn'], selectedText=preferences.get('TextLanguage', 'enu'),
        selectedVoice=preferences.get('VoiceLanguage', 'enu'), repackVersion=args.version,
        publication='Not performed', gameplayAcceptance='Pending owner language/font/dialogue and live download checks',
        downloadOwner='Repack Aetherfall Hub', originalSelectionOwner='Aetherfall game launcher',
        originalHubLanguages=False, repackHubLanguages=True, repackLauncherLanguages=True,
        themedSelectors=True, donorClientRequired=False)
    if hub_source:
        docs['optionalLanguagePacks20261030']['normalHubBuild'] = str(hub_source)
    for row in docs.get('clientFiles', []):
        relative = row.get('path')
        if relative in original_changes:
            row['installedSha256'] = sha(original_changes[relative])
            row['bytes'] = original_changes[relative].stat().st_size
    docs_source = staged('docs/INSTALLED_MODS.json', docs)
    source_changes = {'docs/INSTALLED_MODS.json': docs_source}
    if hub_source:
        source_changes['Aetherfall Hub.exe'] = hub_source
    plans = [(client, original_changes, 'original'), (package, repack_changes, 'repack'),
             (repo, source_changes, 'source-inventory')]
    protected_names = ['Mods/Launcher/preferences.json', 'Mods/Launcher/connection.json', 'system.cfg',
                       'DXVK/renderer.ini', 'PlayerBotBar.ini', 'bin64/Game.dll', 'L10N/enu/data/data.pak', 'L10N/enu/enu.pak',
                       'L10N/enu/textures/textures.pak', 'Mods/Languages/state.json', 'L10N/fra/data/data.pak']
    guards = {}
    for root in [client, package/'Client']:
        guards[str(root)] = {relative: sha(root/relative) for relative in protected_names if (root/relative).is_file()}
    guards[str(repo)] = {name: sha(repo/name) for name in ['OriginalHub.json', 'settings.json', 'release.json'] if (repo/name).is_file()}
    records = []
    for root, changes, label in plans:
        for relative, source in changes.items():
            target = root/relative
            if not target.resolve().is_relative_to(root):
                raise ValueError('Unsafe delivery target')
            before = sha(target) if target.is_file() else None
            after = sha(source)
            if before != after:
                records.append(dict(root=str(root), path=relative, source=str(source), before=before,
                                    sha256=after, bytes=source.stat().st_size, group=label))
    receipt = dict(version=args.version, previousVersion=old_version, normalBuild=str(stage),
                   files=records, preserved=guards, optionalCoreEntriesRemoved=len(retired),
                   optionalCoreBytesRemoved=sum(row['bytes'] for row in retired),
                   optionalInstalledFilesDeleted=False, gameStarted=False, serversChanged=False,
                   publicUpload=False, installed=bool(args.install))
    staged('delivery-plan.json', receipt)
    if not args.install:
        print(json.dumps(dict(status='prepared', plan=str(scratch/'delivery-plan.json'), files=len(records))))
        return
    closed([client, package, repo])
    backup.mkdir(parents=True)
    for entry in records:
        target = Path(entry['root'])/entry['path']
        if entry['before']:
            saved = backup/entry['group']/entry['path']
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, saved)
            if sha(saved) != entry['before']:
                raise ValueError('Backup checksum mismatch')
    copied = []
    try:
        closed([client, package, repo])
        for entry in records:
            target, source = Path(entry['root'])/entry['path'], Path(entry['source'])
            if (sha(target) if target.is_file() else None) != entry['before'] or sha(source) != entry['sha256']:
                raise ValueError('Source/destination changed during delivery: '+entry['path'])
            target.parent.mkdir(parents=True, exist_ok=True)
            copied.append(entry)
            shutil.copy2(source, target)
            if sha(target) != entry['sha256']:
                raise ValueError('Copied checksum mismatch')
        for root, values in guards.items():
            if any(sha(Path(root)/relative) != digest for relative, digest in values.items()):
                raise ValueError('Protected current settings/resources changed')
    except BaseException as error:
        failures = []
        for entry in reversed(copied):
            target = Path(entry['root'])/entry['path']
            try:
                if (sha(target) if target.is_file() else None) == entry['before']:
                    continue
                if entry['before']:
                    shutil.copy2(backup/entry['group']/entry['path'], target)
                elif target.exists():
                    target.unlink()
            except OSError as rollback_error:
                failures.append(dict(path=str(target), error=str(rollback_error)))
        if failures:
            (backup/'rollback-failures.json').write_bytes(encoded(failures))
            raise RuntimeError('Some rollback copies failed; preserved backup and exact plan require review') from error
        raise
    (backup/'receipt.json').write_bytes(encoded(receipt))
    print(json.dumps(dict(status='installed', files=len(records), receipt=str(backup/'receipt.json'),
                          optionalPayloadBytesExcluded=receipt['optionalCoreBytesRemoved'], publicUpload=False)))


if __name__ == '__main__':
    main()
