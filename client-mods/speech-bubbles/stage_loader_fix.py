"""Stage a strictly header-only repair and all matching installed hash records."""
import argparse,copy,hashlib,json
from datetime import datetime
from pathlib import Path
from graphics_compatibility import read
from loader_permissions import writable_iats

def sha(b):return hashlib.sha256(b).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--backup',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    backup=a.backup.resolve();receipt=read(backup/'manifest.json');root=Path(receipt['clientRoot']).resolve();out=a.output.resolve()
    if not backup.is_relative_to(root/'SpeechBubbles-backups') or out.is_relative_to(root):raise ValueError('Invalid staging/receipt path')
    if not receipt.get('graphicsCompatibility'):raise ValueError('Expected composed speech/graphics receipt')
    for e in receipt['files']:
        if sha((root/e['path']).read_bytes())!=e['installed']:raise ValueError('Client changed: '+e['path'])
    state=read(root/'DXVK/graphics-menu/installed.json');graphics=read(root/'DXVK/graphics-menu/package/manifest.json');dxvk=read(root/'DXVK/installed.json')
    if state['files']!=graphics['files'] or Path(state['clientRoot']).resolve()!=root:raise ValueError('Graphics tracking changed')
    baseline=Path(state['backupRoot']).resolve();game=next(e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
    cursor=Path(game['backupPath']).resolve()
    if not baseline.is_relative_to(root/'DXVK-backups') or not cursor.is_relative_to(root/'DXVK-backups'):raise ValueError('Baseline outside client backups')
    paths=['bin64/Game.dll','DXVK/graphics-menu/package/bin64/Game.dll',(baseline/'bin64/Game.dll').relative_to(root).as_posix(),cursor.relative_to(root).as_posix()]
    staged={};permission_changes={}
    for rel in paths:
        original=(root/rel).read_bytes();fixed,changes=writable_iats(original)
        if not changes:raise ValueError('Expected the recorded read-only addon IAT: '+rel)
        staged[rel]=fixed;permission_changes[rel]=changes
    for e in graphics['files']:
        if e['path']=='bin64/Game.dll':
            e['installed']=sha(staged['bin64/Game.dll']);e['original']=sha(staged[paths[2]])
    state['files']=copy.deepcopy(graphics['files']);state['iatPermissionsRepairedAt']=datetime.now().isoformat()
    game['installed']=sha(staged['bin64/Game.dll']);game['original']=sha(staged[paths[3]])
    for rel,value in [('DXVK/graphics-menu/package/manifest.json',graphics),('DXVK/graphics-menu/installed.json',state),('DXVK/installed.json',dxvk)]:
        staged[rel]=json.dumps(value,indent=2).encode()
    # Speech removal restores the pre-speech binaries/tracking from the same
    # original backup; only its expected *installed* hashes change.
    for e in receipt['files']:
        if e['path'] in staged:e['installed']=sha(staged[e['path']])
    receipt['iatPermissionsRepairedAt']=datetime.now().isoformat()
    rel_receipt=(backup/'manifest.json').relative_to(root).as_posix();staged[rel_receipt]=json.dumps(receipt,indent=2).encode()
    files=[]
    for rel,data in staged.items():
        dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
        files.append({'path':rel,'original':sha((root/rel).read_bytes()),'installed':sha(data)})
    (out/'manifest.json').write_text(json.dumps({'clientRoot':str(root),'speechReceipt':rel_receipt,'files':files,'permissionChanges':permission_changes},indent=2))
    print('Staged header-only IAT permission repair:',permission_changes)
if __name__=='__main__':main()
