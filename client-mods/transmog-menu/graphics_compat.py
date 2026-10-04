"""Compose verified native graphics hooks with a freshly built service-menu DLL.

Stages graphics package/state records and new restore baselines together. Never
writes to the client. Unknown live changes still fail the input hash checks.
"""
import copy
from datetime import datetime
import hashlib
import io
import importlib.util
import json
from pathlib import Path
import struct
import re
import sys
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'dxvk'))
from build_graphics_menu import patch, SITES
from codec import read_pak, encode_pak, binary_xml, encode_binary_xml


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def cursor(data):
    result = bytearray(data)
    if result[0x551c96:0x551c98] not in (b'\x78\x18', b'\x90\x90'):
        raise ValueError('Unknown native cursor instruction')
    result[0x551c96:0x551c98] = b'\x90\x90'
    return bytes(result)


def strip_graphics(path, name):
    with read_pak(path) as archive:
        root = binary_xml(archive.read(name))
        page = root.find(".//Widget[@name='page_screen']")
        if page is None:
            raise ValueError('Graphics page missing')
        for widget in ('cb_use_vulkan', 'st_vulkan_status'):
            nodes = [n for n in page if n.get('name') == widget]
            if len(nodes) != 1:
                raise ValueError('Installed Graphics controls missing or duplicated')
            page.remove(nodes[0])
        replacement = encode_binary_xml(root)
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as out:
            for entry in archive.infolist():
                out.writestr(copy.copy(entry), replacement if entry.filename == name else archive.read(entry))
    return encode_pak(buffer.getvalue())


def prepare(root, output, dll, repair_backup=None):
    state_path = root / 'DXVK/graphics-menu/installed.json'
    if not state_path.exists():
        return dll, None
    state = read_json(state_path)
    if state.get('restoredAt'):
        return dll, None
    if Path(state['clientRoot']).resolve() != root.resolve():
        raise ValueError('Graphics menu belongs to another client')
    package = root / 'DXVK/graphics-menu/package'
    manifest = read_json(package / 'manifest.json')
    if state['files'] != manifest['files']:
        raise ValueError('Graphics state and package disagree')
    allowed = {'bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak', 'bin64/AionGraphicsMenu.dll'}
    if len(state['files']) not in (3, 4) or {e['path'] for e in state['files']} - allowed:
        raise ValueError('Unexpected graphics package entries')
    repair = None
    if repair_backup:
        repair = read_json(repair_backup / 'manifest.json')
        if Path(repair['clientRoot']).resolve() != root.resolve() or not repair.get('wardrobe'):
            raise ValueError('Expected this client Wardrobe backup for the one-time repair')
        for entry in repair['files']:
            if sha((root / entry['path']).read_bytes()) != entry['staged']:
                raise ValueError('Client changed after the Wardrobe installation')
    for entry in state['files']:
        if sha((package / entry['path']).read_bytes()) != entry['installed']:
            raise ValueError('Graphics package payload changed')
        current = sha((root / entry['path']).read_bytes())
        if current != entry['installed']:
            known = [] if repair is None else [e for e in repair['files'] if e['path'].lower() == entry['path'].lower()]
            if len(known) != 1 or known[0]['original'] != entry['installed'] or known[0]['staged'] != current:
                raise ValueError('Unknown client change conflicts with Graphics menu: ' + entry['path'])
    dxvk = read_json(root / 'DXVK/installed.json')
    game_record = [e for e in dxvk['nativeCursorPatch']['files'] if e['path'] == 'bin64/Game.dll']
    old_game = next(e for e in state['files'] if e['path'] == 'bin64/Game.dll')
    if len(game_record) != 1 or game_record[0]['installed'].lower() != old_game['installed'].lower():
        raise ValueError('Native cursor and Graphics menu records disagree')
    for entry in dxvk['nativeCursorPatch']['files']:
        if entry['path'] != 'bin64/Game.dll' and sha((root / entry['path']).read_bytes()) != entry['installed'].lower():
            raise ValueError('Other native cursor files changed')

    baseline = cursor(dll)
    combined = patch(baseline, expected=sha(baseline))
    backup_name = 'service-menu-graphics-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup_relative = 'DXVK-backups/' + backup_name
    backup = root / backup_relative
    extras = []

    def stage(relative, data):
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        extras.append(relative)

    # Renderer removal first removes Graphics menu, then restores this exact
    # service-menu build without the native cursor patch. Wardrobe stays intact.
    cursor_base = bytearray(baseline)
    cursor_base[0x551c96:0x551c98] = b'\x78\x18'
    cursor_relative = backup_relative + '/cursor-base/bin64/Game.dll'
    stage(cursor_relative, bytes(cursor_base))
    new_files = []
    for entry in state['files']:
        relative = entry['path']
        if relative == 'bin64/AionGraphicsMenu.dll':
            new_files.append(copy.deepcopy(entry))
            continue
        staged = output / relative
        if relative == 'bin64/Game.dll':
            installed, original = combined, baseline
        else:
            source = staged if staged.exists() else root / relative
            installed = source.read_bytes()
            original = strip_graphics(source, 'global_option_dialog.xml' if relative.startswith('Data/') else 'ui/game/global_option_dialog.xml')
        stage(backup_relative + '/' + relative, original)
        stage('DXVK/graphics-menu/package/' + relative, installed)
        new_files.append({'path': relative, 'original': sha(original), 'installed': sha(installed)})
    manifest['files'] = new_files
    state['files'] = new_files
    state['backupRoot'] = str(backup)
    state['rebasedAt'] = datetime.now().isoformat()
    game_record[0]['installed'] = sha(combined)
    game_record[0]['original'] = sha(bytes(cursor_base))
    game_record[0]['backupPath'] = str(root / cursor_relative)
    for relative, value in [('DXVK/graphics-menu/package/manifest.json', manifest),
                            ('DXVK/graphics-menu/installed.json', state), ('DXVK/installed.json', dxvk)]:
        stage(relative, json.dumps(value, indent=2).encode('utf-8'))
    print('Preserved native Graphics menu and cursor; staged matching package, tracking and restore baselines.')
    return combined, {'backupName': backup_name, 'localized': any(e['path'].startswith('L10N/') for e in new_files)}


def compatibility_paths(record):
    """Exact payload cohort shared by native companion staging and verification."""
    if not record:
        return set()
    name = record['backupName']
    if not re.fullmatch(r'service-menu-graphics-\d{8}-\d{6}-\d{6}', name):
        raise ValueError('Unexpected graphics compatibility backup name')
    base = 'DXVK-backups/' + name
    result = {'DXVK/installed.json', 'DXVK/graphics-menu/installed.json',
              'DXVK/graphics-menu/package/manifest.json', 'DXVK/graphics-menu/package/bin64/Game.dll',
              'DXVK/graphics-menu/package/Data/ui/game/game.pak', base+'/bin64/Game.dll',
              base+'/Data/ui/game/game.pak', base+'/cursor-base/bin64/Game.dll'}
    if record['localized']:
        result.update({'DXVK/graphics-menu/package/L10N/enu/Data/data.pak', base+'/L10N/enu/Data/data.pak'})
    return result


def prepare_incremental(root, output, dll, repair_backup=None, *, backup_name=None, rebased_at=None):
    """Track a bounded edit to the installed DLL without rebuilding graphics hooks.

    Every changed byte must also exist unchanged in both verified restore
    baselines. Thus edits to graphics/cursor hooks cannot silently cross domains.
    A repair accepts only a fully hash-verified known installer receipt.
    """
    state_path = root / 'DXVK/graphics-menu/installed.json'
    if not state_path.exists():
        return dll, None
    state = read_json(state_path)
    if state.get('restoredAt'):
        return dll, None
    if Path(state['clientRoot']).resolve() != root.resolve():
        raise ValueError('Graphics state belongs to another client')
    package = root / 'DXVK/graphics-menu/package'
    manifest = read_json(package / 'manifest.json')
    allowed = {'bin64/Game.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak','bin64/AionGraphicsMenu.dll'}
    if state['files'] != manifest['files'] or len(state['files']) not in (3,4) or len({e['path'] for e in state['files']}) != len(state['files']) or {e['path'] for e in state['files']} - allowed:
        raise ValueError('Graphics package and state disagree')
    receipt = None
    if repair_backup:
        receipt = read_json(repair_backup / 'manifest.json')
        companion = receipt.get('feature') == 'player-companions'
        if Path(receipt['clientRoot']).resolve() != root.resolve() or not (receipt.get('poetaJourney') is True or companion):
            raise ValueError('Expected this client Poeta journey or companion installer receipt')
        if companion:
            # A receipt alone cannot authorize arbitrary native changes. Verify
            # the saved pre-install DLL and the exact two companion browser caves.
            spec = importlib.util.spec_from_file_location('companion_patch_for_recovery', HERE.parent / 'playerbots/prepare.py')
            companions = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(companions)
            saved = (repair_backup / 'bin64/Game.dll').read_bytes()
            entry = next(e for e in receipt['files'] if e['path'] == 'bin64/Game.dll')
            if sha(saved) != entry['original'] or companions.patch(saved) != (root / 'bin64/Game.dll').read_bytes():
                raise ValueError('Companion repair exceeds the verified browser edits')
        for entry in receipt['files']:
            if sha((root/entry['path']).read_bytes()) != entry['staged']:
                raise ValueError('Client changed after the recorded installation')
        for entry in receipt.get('preservedFiles',[]):
            if sha((root/entry['path']).read_bytes()) != entry['sha256']:
                raise ValueError('Preserved client file changed')
    for entry in state['files']:
        if sha((package/entry['path']).read_bytes()) != entry['installed']:
            raise ValueError('Graphics payload changed')
        current = sha((root/entry['path']).read_bytes())
        if current != entry['installed']:
            known = [] if receipt is None else [e for e in receipt['files'] if e['path'].lower() == entry['path'].lower()]
            if len(known) != 1 or known[0]['original'] != entry['installed'] or known[0]['staged'] != current:
                raise ValueError('Unknown later client change: '+entry['path'])
    dxvk = read_json(root/'DXVK/installed.json')
    records = [e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll']
    old_game = next(e for e in state['files'] if e['path']=='bin64/Game.dll')
    if len(records)!=1 or records[0]['installed'].lower()!=old_game['installed'].lower():
        raise ValueError('Cursor and graphics tracking disagree')
    game_record = records[0]
    for entry in dxvk['nativeCursorPatch']['files']:
        if entry['path']!='bin64/Game.dll' and sha((root/entry['path']).read_bytes())!=entry['installed'].lower():
            raise ValueError('Other native cursor files changed')
    old_backup = Path(state['backupRoot']).resolve()
    cursor_path = Path(game_record['backupPath']).resolve()
    if not old_backup.is_relative_to((root/'DXVK-backups').resolve()) or not cursor_path.is_relative_to((root/'DXVK-backups').resolve()):
        raise ValueError('Restore baseline is outside client backups')
    prior = (package/'bin64/Game.dll').read_bytes()
    baseline = bytearray((old_backup/'bin64/Game.dll').read_bytes())
    cursor_base = bytearray(cursor_path.read_bytes())
    if sha(baseline)!=old_game['original'] or sha(cursor_base)!=game_record['original'].lower():
        raise ValueError('Restore baseline checksum mismatch')
    if len(dll)!=len(prior):
        raise ValueError('Incremental edit must preserve installed DLL length')
    for i,(before,after) in enumerate(zip(prior,dll)):
        if before!=after:
            if i>=len(baseline) or i>=len(cursor_base) or baseline[i]!=before or cursor_base[i]!=before:
                raise ValueError('Incremental edit overlaps graphics/cursor restore ownership')
            baseline[i]=cursor_base[i]=after
    backup_name = backup_name or 'service-menu-graphics-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    if not re.fullmatch(r'service-menu-graphics-\d{8}-\d{6}-\d{6}', backup_name):
        raise ValueError('Unexpected graphics compatibility backup name')
    backup_relative = 'DXVK-backups/'+backup_name

    def stage(relative,data):
        target=output/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)

    new_files=[]
    for entry in state['files']:
        relative=entry['path']
        if relative=='bin64/AionGraphicsMenu.dll':
            new_files.append(copy.deepcopy(entry));continue
        if relative=='bin64/Game.dll':
            installed,original=dll,bytes(baseline)
        else:
            installed=(root/relative).read_bytes();original=(old_backup/relative).read_bytes()
            if sha(original)!=entry['original']:
                raise ValueError('UI restore baseline checksum mismatch')
        stage(backup_relative+'/'+relative,original)
        stage('DXVK/graphics-menu/package/'+relative,installed)
        new_files.append(dict(path=relative,original=sha(original),installed=sha(installed)))
    cursor_relative=backup_relative+'/cursor-base/bin64/Game.dll'
    stage(cursor_relative,bytes(cursor_base))
    state.update(files=new_files,backupRoot=str(root/backup_relative),rebasedAt=rebased_at or datetime.now().isoformat())
    manifest['files']=new_files
    game_record.update(installed=sha(dll),original=sha(cursor_base),backupPath=str(root/cursor_relative))
    for relative,value in [('DXVK/graphics-menu/package/manifest.json',manifest),('DXVK/graphics-menu/installed.json',state),('DXVK/installed.json',dxvk)]:
        stage(relative,json.dumps(value,indent=2).encode())
    print('Staged matching Graphics/cursor records and exact incremental restore baselines; client untouched.')
    return dll,dict(backupName=backup_name,localized=any(e['path'].startswith('L10N/') for e in new_files))
