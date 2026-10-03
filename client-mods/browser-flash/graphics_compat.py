"""Compose the diagnostic into graphics/cursor tracking and restore baselines."""
import copy
from datetime import datetime
import json
from pathlib import Path
from stage import patch,sha

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))

def prepare(root,out,prior,installed,already_installed=False):
    state_path=root/'DXVK/graphics-menu/installed.json'
    if not state_path.exists():return [],None
    state=read(state_path)
    if state.get('restoredAt') or Path(state['clientRoot']).resolve()!=root.resolve():raise ValueError('Inactive or foreign Graphics menu')
    package=root/'DXVK/graphics-menu/package';manifest=read(package/'manifest.json')
    allowed={'bin64/Game.dll','Data/ui/game/game.pak','bin64/AionGraphicsMenu.dll','L10N/enu/Data/data.pak'}
    if state['files']!=manifest['files'] or len(state['files']) not in (3,4) or len({e['path'] for e in state['files']})!=len(state['files']) or {e['path'] for e in state['files']}-allowed:raise ValueError('Graphics state/package mismatch')
    if patch(prior)[0]!=installed:raise ValueError('Game.dll differs from the exact bounded diagnostic patch')
    for e in state['files']:
        if sha((package/e['path']).read_bytes())!=e['installed']:raise ValueError('Graphics payload changed: '+e['path'])
        expected=sha(installed if already_installed else prior) if e['path']=='bin64/Game.dll' else e['installed']
        if sha((root/e['path']).read_bytes())!=expected:raise ValueError('Unrecorded client change: '+e['path'])
        if e['path']=='bin64/Game.dll' and e['installed']!=sha(prior):raise ValueError('Diagnostic original does not match Graphics tracking')
    dxvk=read(root/'DXVK/installed.json')
    if Path(dxvk['clientRoot']).resolve()!=root.resolve() or dxvk.get('restoredAt'):raise ValueError('Inactive or foreign renderer')
    records=[e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll']
    game=next(e for e in state['files'] if e['path']=='bin64/Game.dll')
    if len(records)!=1 or records[0]['installed'].lower()!=game['installed']:raise ValueError('Cursor and Graphics tracking disagree')
    record=records[0]
    for e in dxvk['nativeCursorPatch']['files']:
        if e['path'] not in {'bin64/Game.dll','bin64/XRenderD3D9.dll'}:raise ValueError('Unexpected cursor target')
        if e['path']!='bin64/Game.dll' and sha((root/e['path']).read_bytes())!=e['installed'].lower():raise ValueError('Cursor renderer changed')
    old_backup=Path(state['backupRoot']).resolve();cursor=Path(record['backupPath']).resolve()
    if not old_backup.is_relative_to(root/'DXVK-backups') or not cursor.is_relative_to(root/'DXVK-backups'):raise ValueError('Restore baseline outside client backups')
    for e in state['files']:
        if e['original'] is not None and sha((old_backup/e['path']).read_bytes())!=e['original']:raise ValueError('Graphics restore baseline changed')
    if sha(cursor.read_bytes())!=record['original'].lower():raise ValueError('Cursor restore baseline changed')
    backup_name='browser-probe-graphics-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f');base='DXVK-backups/'+backup_name
    extras=[]
    def stage(rel,data):
        target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        live=root/rel;extras.append(dict(path=rel,original=sha(live.read_bytes()) if live.exists() else None,installed=sha(data)))
    files=copy.deepcopy(state['files'])
    for e in files:
        if e['original'] is None:continue
        original=(old_backup/e['path']).read_bytes()
        if e['path']=='bin64/Game.dll':
            original=patch(original)[0];e.update(original=sha(original),installed=sha(installed))
            stage('DXVK/graphics-menu/package/bin64/Game.dll',installed)
        stage(base+'/'+e['path'],original)
    cursor_base=patch(cursor.read_bytes())[0];cursor_relative=base+'/cursor-base/bin64/Game.dll';stage(cursor_relative,cursor_base)
    manifest['files']=files
    state.update(files=files,backupRoot=str(root/base),rebasedAt=datetime.now().isoformat())
    record.update(installed=sha(installed),original=sha(cursor_base),backupPath=str(root/cursor_relative))
    for rel,value in [('DXVK/graphics-menu/package/manifest.json',manifest),('DXVK/graphics-menu/installed.json',state),('DXVK/installed.json',dxvk)]:stage(rel,json.dumps(value,indent=2).encode('utf-8'))
    return extras,dict(backupName=backup_name,localized=any(e['path'].startswith('L10N/') for e in files))
