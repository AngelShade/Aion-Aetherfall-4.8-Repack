"""Stage the native party command bar from the current installed companion client."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import sys
import zipfile

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'expanded-warehouse'))
from codec import read_pak,encode_pak

def sha(data):return hashlib.sha256(data).hexdigest()
def normalized_pe(data):
    b=bytearray(data);pe=struct.unpack_from('<I',b,60)[0];optional=pe+24
    b[pe+8:pe+12]=bytes(4)
    count=struct.unpack_from('<H',b,pe+6)[0];table=optional+struct.unpack_from('<H',b,pe+20)[0]
    debug,size=struct.unpack_from('<II',b,optional+112+6*8)
    for i in range(count):
        _,_,rva,raw_size,raw,*_=struct.unpack_from('<8sIIIIIIHHI',b,table+i*40)
        if rva<=debug<rva+raw_size:
            for j in range(0,size,28):b[raw+debug-rva+j+4:raw+debug-rva+j+8]=bytes(4)
    return bytes(b)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--baseline',type=Path,required=True)
    a=p.parse_args();root=a.client.resolve();out=a.output.resolve()
    if root==out or root in out.parents:raise ValueError('Stage outside installed client')
    # The pristine source rebuild must match every installed byte except PE
    # build timestamps, proving this extension retains all installed bridge work.
    bridge='bin64/AionIconBridge.dll'
    baseline=(a.baseline/bridge).read_bytes();live=(root/bridge).read_bytes()
    if normalized_pe(baseline)!=normalized_pe(live):raise ValueError('Bridge source differs from actual installation; preserve and review newer work')
    if not (out/bridge).is_file():raise ValueError('Compile the updated bridge into the staging directory first')
    pak='Plugin/RelicCalc/RelicCalc.pak';z=read_pak(root/pak)
    if 'PlayerBotBar.lua' in z.namelist():raise ValueError('Command bar already present; stage a bounded revision instead')
    toc=z.read('RelicCalc.toc')
    if b'PrivateMenus.lua' not in toc or b'WardrobeNative.lua' not in toc:raise ValueError('Expected current installed native menus')
    changes={'RelicCalc.toc':toc+b'\r\nPlayerBotBar.lua\r\nPlayerBotBar.xml\r\n',
        'PlayerBotBar.lua':(HERE/'PlayerBotBar.lua').read_bytes(),'PlayerBotBar.xml':(HERE/'PlayerBotBar.xml').read_bytes()}
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED) as target:
        for entry in z.infolist():target.writestr(copy.copy(entry),changes.get(entry.filename,z.read(entry.filename)))
        for name in ('PlayerBotBar.lua','PlayerBotBar.xml'):target.writestr(name,changes[name])
    (out/pak).parent.mkdir(parents=True,exist_ok=True);(out/pak).write_bytes(encode_pak(buffer.getvalue()))
    subprocess.run(['java',str(HERE.parent/'transmog-menu/SignClientPackages.java'),str(root),str(out)],check=True)
    (out/'Pub.key').unlink()
    # The icon index was rebuilt only as a regression check; keep the exact live one.
    index=out/'bin64/AionIconBridge.index'
    if (index if index.exists() else a.baseline/'bin64/AionIconBridge.index').read_bytes()!=(root/'bin64/AionIconBridge.index').read_bytes():raise ValueError('Installed native item index drifted')
    if index.exists():index.unlink()
    files=[dict(path=f.relative_to(out).as_posix(),original=sha((root/f.relative_to(out)).read_bytes()),staged=sha(f.read_bytes())) for f in sorted(out.rglob('*')) if f.is_file() and f.name!='manifest.json']
    inventory=json.loads((HERE.parents[1]/'docs/INSTALLED_MODS.json').read_text())
    changed={e['path'].lower() for e in files}
    preserved=[dict(path=e['path'],sha256=sha((root/e['path']).read_bytes())) for e in inventory['clientFiles'] if e['path'].lower() not in changed]
    manifest=dict(feature='playerbot-party-bar',clientRoot=str(root),files=files,preservedFiles=preserved,
        bridgeSourceBaseline=sha(live),baselineRebuild=sha(baseline),baselineRebuildNormalized=sha(normalized_pe(baseline)),
        archiveChanges=['RelicCalc.toc','PlayerBotBar.lua','PlayerBotBar.xml'],
        serverBaseline=dict(root=inventory['serverRoot'],jar=inventory['serverJarSha256'],launcher=inventory['serverLauncherSha256'],overrides=inventory['serverClassOverrides']))
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('OK: staged six files; source matches installed bridge except build timestamps; Game.dll, native layouts, all other menu entries and server preserved:',out)

if __name__=='__main__':main()
