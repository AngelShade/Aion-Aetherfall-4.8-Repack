"""Stage login-return callbacks on the current client and compose its recovery guards."""
import argparse,json
from pathlib import Path
from prepare import compile_dll,sha,read,PRESERVED
from patch_return import patch

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args();root=a.client.resolve();out=a.output.resolve()
    if out.exists() or out.is_relative_to(root):raise ValueError('Use fresh staging outside the client')
    staged={}
    def stage(rel,data):
        rel=Path(rel).as_posix();target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);staged[rel]=data
    game,hooks=patch((root/'bin64/Game.dll').read_bytes());stage('bin64/Game.dll',game)
    stage('bin64/AionRememberLogin.dll',compile_dll(out))
    g=read(root/'DXVK/graphics-menu/installed.json');m=read(root/'DXVK/graphics-menu/package/manifest.json')
    if g['files']!=m['files']:raise ValueError('Graphics recovery metadata differs')
    e=next(e for e in g['files'] if e['path']=='bin64/Game.dll');baseline=Path(g['backupRoot'])/e['path']
    if not baseline.resolve().is_relative_to(root/'DXVK-backups') or sha(baseline.read_bytes())!=e['original'] or sha((root/e['path']).read_bytes())!=e['installed']:raise ValueError('Graphics DLL guards differ')
    changed,_=patch(baseline.read_bytes());stage(baseline.relative_to(root),changed);e.update(original=sha(changed),installed=sha(game))
    stage('DXVK/graphics-menu/package/'+e['path'],game);m['files']=g['files']
    stage('DXVK/graphics-menu/installed.json',json.dumps(g,indent=2).encode());stage('DXVK/graphics-menu/package/manifest.json',json.dumps(m,indent=2).encode())
    cursor=read(root/'DXVK/installed.json');e=next(e for e in cursor['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll');baseline=Path(e['backupPath'])
    if not baseline.resolve().is_relative_to(root/'DXVK-backups') or sha(baseline.read_bytes())!=e['original'] or sha((root/e['path']).read_bytes())!=e['installed']:raise ValueError('Cursor DLL guards differ')
    changed,_=patch(baseline.read_bytes());stage(baseline.relative_to(root),changed);e.update(original=sha(changed),installed=sha(game));stage('DXVK/installed.json',json.dumps(cursor,indent=2).encode())
    preserved=PRESERVED+['Data/ui/ui.pak','L10N/enu/Data/data.pak']
    receipt=dict(feature='remember-login',revision='return-1',clientRoot=str(root),sourceKey=sha((root/'Pub.key').read_bytes()),hooks=hooks,
        files=[dict(path=rel,original=sha((root/rel).read_bytes()),installed=sha(data)) for rel,data in staged.items()],
        preservedFiles=[dict(path=rel,sha256=sha((root/rel).read_bytes())) for rel in preserved])
    (out/'manifest.json').write_text(json.dumps(receipt,indent=2));print('OK:',len(staged),'files staged; two login-only callbacks; client unchanged')
if __name__=='__main__':main()
