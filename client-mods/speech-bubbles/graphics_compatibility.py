"""Compose speech bubbles into verified graphics/cursor restore baselines.

Only stages files. The existing launch guards remain unchanged and reject any
unrecorded client change. A repair requires the exact speech installer backup.
"""
import argparse,copy,hashlib,json,shutil
from datetime import datetime
from pathlib import Path
from build_package import patch,ui,read_pak

def sha(b):return hashlib.sha256(b).hexdigest()
def digest(p):return sha(p.read_bytes())
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))

def prepare(root,out,files,patch_dll,patch_ui,receipt=None):
    state_path=root/'DXVK/graphics-menu/installed.json'
    if not state_path.exists():return None
    state=read(state_path)
    if state.get('restoredAt'):raise ValueError('Graphics menu was restored; review this client before composition')
    if Path(state['clientRoot']).resolve()!=root.resolve():raise ValueError('Graphics state belongs to another client')
    package=root/'DXVK/graphics-menu/package';manifest=read(package/'manifest.json')
    allowed={'bin64/Game.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak','bin64/AionGraphicsMenu.dll'}
    if state['files']!=manifest['files'] or len(state['files']) not in (3,4) or len({e['path'] for e in state['files']})!=len(state['files']) or {e['path'] for e in state['files']} - allowed:
        raise ValueError('Unexpected graphics package/state')
    changes={e['path']:e for e in files}
    for e in files:
        if digest(out/e['path'])!=e['installed']:raise ValueError('Speech payload changed')
        actual=digest(root/e['path']) if (root/e['path']).exists() else None
        if actual!=(e['installed'] if receipt else e['original']):raise ValueError('Speech installation changed: '+e['path'])
        if receipt and e['original'] is not None and digest(receipt/e['path'])!=e['original']:raise ValueError('Speech backup changed')
    for e in state['files']:
        if digest(package/e['path'])!=e['installed']:raise ValueError('Graphics payload changed')
        prior=receipt/e['path'] if receipt and e['path'] in changes else root/e['path']
        if digest(prior)!=e['installed']:raise ValueError('Speech input and graphics tracking disagree: '+e['path'])
    dxvk=read(root/'DXVK/installed.json')
    if Path(dxvk['clientRoot']).resolve()!=root.resolve() or dxvk.get('restoredAt'):raise ValueError('Inactive renderer installation')
    records=[e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll']
    old_game=next(e for e in state['files'] if e['path']=='bin64/Game.dll')
    if len(records)!=1 or records[0]['installed'].lower()!=old_game['installed'].lower():raise ValueError('Graphics and cursor tracking disagree')
    for e in dxvk['nativeCursorPatch']['files']:
        if e['path'] not in {'bin64/Game.dll','bin64/XRenderD3D9.dll'}:raise ValueError('Unexpected cursor target')
        if e['path']!='bin64/Game.dll' and digest(root/e['path'])!=e['installed'].lower():raise ValueError('Cursor renderer changed')
    game=records[0];old_backup=Path(state['backupRoot']).resolve();cursor=Path(game['backupPath']).resolve()
    if not old_backup.is_relative_to((root/'DXVK-backups').resolve()) or not cursor.is_relative_to((root/'DXVK-backups').resolve()):raise ValueError('Restore baseline outside client backups')
    for e in state['files']:
        if e['original'] is not None and digest(old_backup/e['path'])!=e['original']:raise ValueError('Graphics restore baseline changed')
    if digest(cursor)!=game['original'].lower():raise ValueError('Cursor restore baseline changed')
    backup_name='service-menu-graphics-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f');base='DXVK-backups/'+backup_name
    extras=[];baseline_hooks={}
    def stage(relative,data):
        target=out/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        live=root/relative
        extras.append({'path':relative,'original':digest(live) if live.exists() else None,'installed':sha(data)})
    new_files=[]
    for e in state['files']:
        rel=e['path']
        if rel=='bin64/AionGraphicsMenu.dll':new_files.append(copy.deepcopy(e));continue
        if rel=='bin64/Game.dll':
            baseline,hooks=patch_dll((old_backup/rel).read_bytes(),expected=e['original']);baseline_hooks['graphics']=hooks
        else:
            name='chat_option_dialog.xml' if rel.startswith('Data/') else 'ui/game/chat_option_dialog.xml'
            prior=receipt/rel if receipt else root/rel
            if read_pak(old_backup/rel).read(name)!=read_pak(prior).read(name):raise ValueError('Graphics baseline has different Chat Options')
            baseline,_=patch_ui(old_backup/rel,name,out/base/rel)
            if read_pak(out/base/rel).read(name)!=read_pak(out/rel).read(name):raise ValueError('Chat Options differs between installed and restore baseline')
        installed=(out/rel).read_bytes()
        stage(base+'/'+rel,baseline);stage('DXVK/graphics-menu/package/'+rel,installed)
        new_files.append({'path':rel,'original':sha(baseline),'installed':sha(installed)})
    cursor_base,hooks=patch_dll(cursor.read_bytes(),expected=game['original'].lower());baseline_hooks['cursor']=hooks
    cursor_relative=base+'/cursor-base/bin64/Game.dll';stage(cursor_relative,cursor_base)
    state.update(files=new_files,backupRoot=str(root/base),rebasedAt=datetime.now().isoformat())
    manifest['files']=new_files
    game.update(installed=changes['bin64/Game.dll']['installed'],original=sha(cursor_base),backupPath=str(root/cursor_relative))
    for rel,value in [('DXVK/graphics-menu/package/manifest.json',manifest),('DXVK/graphics-menu/installed.json',state),('DXVK/installed.json',dxvk)]:
        stage(rel,json.dumps(value,indent=2).encode())
    files.extend(extras)
    (out/'graphics-baseline-hooks.json').write_text(json.dumps(baseline_hooks,indent=2))
    return {'backupName':backup_name,'localized':any(e['path'].startswith('L10N/') for e in new_files)}

def main():
    p=argparse.ArgumentParser();p.add_argument('--backup',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    backup=a.backup.resolve();manifest=read(backup/'manifest.json');root=Path(manifest['clientRoot']).resolve();out=a.output.resolve()
    if not backup.is_relative_to(root/'SpeechBubbles-backups') or out.is_relative_to(root):raise ValueError('Invalid backup/output paths')
    if manifest.get('graphicsCompatibility'):raise ValueError('Launcher compatibility was already recorded')
    expected={'bin64/Game.dll','bin64/AionSpeechBubbles.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak'}
    if len(manifest['files'])!=4 or {e['path'] for e in manifest['files']}!=expected:raise ValueError('Expected the original speech bubble receipt')
    for e in manifest['files']:
        dest=out/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/e['path'],dest)
    manifest['graphicsCompatibility']=prepare(root,out,manifest['files'],patch,ui,backup)
    if not manifest['graphicsCompatibility']:raise ValueError('No active graphics menu to repair')
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print('Staged verified graphics/cursor tracking and speech-preserving restore baselines. Client untouched.')
if __name__=='__main__':main()
