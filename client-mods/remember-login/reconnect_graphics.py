"""Guarded per-image Reset relocation in graphics/cursor recovery baselines."""
import copy,json
from datetime import datetime
from pathlib import Path
from prepare_reset import sha
from patch_reset import relocate_baseline
import hashlib

def digest(data):return hashlib.sha256(data).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))

def prepare(root,out,updated,legacy_iat):
    state=read(root/'DXVK/graphics-menu/installed.json')
    manifest=read(root/'DXVK/graphics-menu/package/manifest.json')
    dx=read(root/'DXVK/installed.json')
    if state.get('restoredAt') or Path(state['clientRoot']).resolve()!=root or state['files']!=manifest['files']:
        raise ValueError('Graphics state and package disagree')
    allowed={'bin64/Game.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak','bin64/AionGraphicsMenu.dll'}
    if len(state['files']) not in (3,4) or len({e['path'] for e in state['files']})!=len(state['files']) or {e['path'] for e in state['files']}-allowed:
        raise ValueError('Unexpected graphics scope')
    backup=Path(state['backupRoot']).resolve()
    if not backup.is_relative_to(root/'DXVK-backups'):raise ValueError('Graphics backup escapes installation')
    for entry in state['files']:
        if sha(root/entry['path'])!=entry['installed'] or sha(root/'DXVK/graphics-menu/package'/entry['path'])!=entry['installed'] or sha(backup/entry['path'])!=entry['original']:
            raise ValueError('Graphics payload/recovery checksum mismatch: '+entry['path'])
    records=[e for e in dx['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll']
    if len(records)!=1:raise ValueError('Expected one cursor Game.dll record')
    for entry in dx['nativeCursorPatch']['files']:
        recovery=Path(entry['backupPath']).resolve()
        if not recovery.is_relative_to(root/'DXVK-backups') or sha(root/entry['path'])!=entry['installed'].lower() or sha(recovery)!=entry['original'].lower():
            raise ValueError('Cursor payload/recovery checksum mismatch')
    cursor=records[0];game=next(e for e in state['files'] if e['path']=='bin64/Game.dll')
    if cursor['installed'].lower()!=game['installed']:raise ValueError('Cursor and graphics Game.dll disagree')
    source=(root/'bin64/Game.dll').read_bytes()
    if len(source)!=len(updated):raise ValueError('Main DLL length changed')
    baseline,graphics_hook=relocate_baseline((backup/'bin64/Game.dll').read_bytes(),legacy_iat)
    cursor_base,cursor_hook=relocate_baseline(Path(cursor['backupPath']).read_bytes(),legacy_iat)
    name='service-menu-graphics-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    relative='DXVK-backups/'+name
    def stage(path,data):
        f=out/path;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    files=[]
    for entry in state['files']:
        path=entry['path']
        if path=='bin64/AionGraphicsMenu.dll':files.append(copy.deepcopy(entry));continue
        installed=updated if path=='bin64/Game.dll' else (root/path).read_bytes()
        original=baseline if path=='bin64/Game.dll' else (backup/path).read_bytes()
        stage(relative+'/'+path,original);stage('DXVK/graphics-menu/package/'+path,installed)
        files.append(dict(path=path,original=digest(original),installed=digest(installed)))
    cursor_relative=relative+'/cursor-base/bin64/Game.dll';stage(cursor_relative,cursor_base)
    manifest['files']=files;state.update(files=files,backupRoot=str(root/relative),rebasedAt=datetime.now().isoformat())
    cursor.update(installed=digest(updated),original=digest(cursor_base),backupPath=str(root/cursor_relative))
    for path,value in [('DXVK/graphics-menu/package/manifest.json',manifest),('DXVK/graphics-menu/installed.json',state),('DXVK/installed.json',dx)]:
        stage(path,json.dumps(value,indent=2).encode())
    print('OK: independently relocated verified graphics/cursor images with their own Visibility imports')
    return updated,dict(backupName=name,localized=any(e['path'].startswith('L10N/') for e in files),
                        resetRestoreHooks=dict(graphics=graphics_hook,cursor=cursor_hook))
