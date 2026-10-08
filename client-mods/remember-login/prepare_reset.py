"""Stage the login reset/notice repair from the actual cumulative client."""
import argparse,json,hashlib,os,sys
from pathlib import Path
from patch_reset import patch
from patch_binary import layout,offset
from prepare import read_pak,binary_xml
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'transmog-menu'))
from graphics_compat import prepare_incremental
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'browser-replacement'))
from audit import PE
DEV_ROOT=Path(os.environ.get('AION_DEV_ROOT','D:/Proiecte/Project Restructure/Aion Development Workspace')).resolve()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--dll',type=Path,required=True);p.add_argument('--inventory',type=Path,required=True)
    a=p.parse_args();root=a.client.resolve();out=a.output.resolve();dll=a.dll.read_bytes()
    if not out.is_relative_to(DEV_ROOT/'staging/output') or out.exists():raise ValueError('Use fresh external staging directory')
    inventory=json.loads(a.inventory.read_text())
    if Path(inventory['clientRoot']).resolve()!=root or not all(inventory['checks'].values()):raise ValueError('Current inventory must pass')
    for e in inventory['clientFiles']:
        if sha(root/e['path'])!=e['sha256']:raise ValueError('Client changed since inventory: '+e['path'])
    receipt=json.loads((root/'RememberLogin-backups/20261003-190013-967/manifest.json').read_text())
    old=next(e for e in receipt['files'] if e['path']=='bin64/AionRememberLogin.dll')
    if sha(root/old['path'])!=old['installed']:raise ValueError('Installed login DLL changed; cumulative source review required')
    exports=set(PE(dll).exports())
    if exports!=set(PE((root/old['path']).read_bytes()).exports()) or any('Test' in name for name in exports):raise ValueError('Production login API changed')
    source=(root/'bin64/Game.dll').read_bytes();original=(root/'bin64/game.dll.orig').read_bytes();_,_,_,_,sections=layout(source)
    # This reader handles LoginNotice XML Contents using the native html widget.
    for start,end in [(0x79df40,0x79e450),(0x79e7f0,0x79e812)]:
        if source[offset(sections,start):offset(sections,end)]!=original[start:end]:raise ValueError('Native login routine differs from supported build')
    with read_pak(root/'L10N/enu/Data/data.pak') as archive:
        notice=binary_xml(archive.read('ui/loginnotice.xml'))
        if notice.tag!='LoginNotice' or not notice.findtext('Contents'):raise ValueError('Localized login announcement missing')
    updated,hook=patch(source,receipt);out.mkdir(parents=True)
    updated,graphics=prepare_incremental(root,out,updated)
    for name,data in [('bin64/Game.dll',updated),('bin64/AionRememberLogin.dll',dll)]:
        f=out/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    entries=[dict(path=f.relative_to(out).as_posix(),original=sha(root/f.relative_to(out)),staged=sha(f)) for f in sorted(out.rglob('*')) if f.is_file()]
    changes={e['path'].lower() for e in entries}
    preserved=[dict(path=e['path'],sha256=e['sha256']) for e in inventory['clientFiles'] if e['path'].lower() not in changes]
    m=dict(feature='remember-login-return-v2',clientRoot=str(root),graphicsCompatibility=graphics,hook=hook,
           files=entries,preservedFiles=preserved,status='staged; actual logout-to-login acceptance pending',
           nativeNoticeLoaderRva=0x79df40)
    (out/'manifest.json').write_text(json.dumps(m,indent=2))
    print('OK: staged',len(entries),'files;',len(preserved),'preserved resources; native notice XML and original routines verified')
if __name__=='__main__':main()
