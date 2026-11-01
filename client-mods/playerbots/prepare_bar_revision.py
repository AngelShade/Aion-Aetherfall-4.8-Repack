"""Stage only the current bar's Lua/XML and signatures; preserve every other entry."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
from prepare_bar import HERE,sha,read_pak,normalized_pe

def main():
    p=argparse.ArgumentParser();p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--bridge',type=Path);p.add_argument('--baseline',type=Path)
    p.add_argument('--hire-terms-layout',type=Path,help='prepared native Hire details layouts; refresh only their existing UI catalogue rows')
    p.add_argument('--revision',choices=('formations-1','radius-1'));a=p.parse_args()
    root=a.client.resolve();out=a.output.resolve()
    if out==root or root in out.parents or out.exists():raise ValueError('Use a new directory outside installed client')
    bridge='bin64/AionIconBridge.dll'
    if bool(a.bridge)!=bool(a.baseline):raise ValueError('Bridge revision needs its pristine source baseline')
    if a.bridge:
        live=(root/bridge).read_bytes();baseline=(a.baseline/bridge).read_bytes()
        if normalized_pe(live)!=normalized_pe(baseline):raise ValueError('Baseline source must match the current installed bridge')
        if (a.baseline/'bin64/AionIconBridge.index').read_bytes()!=(root/'bin64/AionIconBridge.index').read_bytes():raise ValueError('Native index baseline differs')
    pak='Plugin/RelicCalc/RelicCalc.pak';old=read_pak(root/pak)
    names=['PlayerBotBar.lua','PlayerBotBar.xml']
    if not all(n in old.namelist() and n.encode() in old.read('RelicCalc.toc') for n in names):raise ValueError('Expected installed command bar')
    changes={n:(HERE/n).read_bytes() for n in names}
    if a.hire_terms_layout:
        sys.path.insert(0,str(HERE.parent/'aetherfall-core'))
        from ui_registry import refresh_layouts
        layout=json.loads((a.hire_terms_layout/'manifest.json').read_text())
        entries={r['archive']:[n for n in r['entries'] if n.endswith('party_match_dialog.xml')] for r in layout['variants']}
        changes['AetherfallRegistry.lua'],_=refresh_layouts(old.read('AetherfallRegistry.lua'),a.hire_terms_layout,entries)
    # The normal native PAK rebuilder retains every unchanged compressed member.
    sys.path.insert(0,str(HERE.parent/'bundle-opening'))
    from prepare_client import rebuild,parts
    changes={n:data for n,data in changes.items() if old.read(n)!=data}
    raw=(root/pak).read_bytes();payload,_=rebuild(raw,changes,{i.filename:i for i in old.infolist()})
    before,after=parts(raw)[0],parts(payload)[0]
    if list(before)!=list(after) or any(before[n][0]!=after[n][0] for n in before if n not in changes):
        raise ValueError('Unrelated compressed plugin member changed')
    (out/pak).parent.mkdir(parents=True);(out/pak).write_bytes(payload)
    subprocess.run(['java',str(HERE.parent/'transmog-menu/SignClientPackages.java'),str(root),str(out)],check=True)
    (out/'Pub.key').unlink()
    if a.bridge:
        (out/bridge).parent.mkdir(parents=True,exist_ok=True);(out/bridge).write_bytes(a.bridge.read_bytes())
    files=[dict(path=f.relative_to(out).as_posix(),original=sha((root/f.relative_to(out)).read_bytes()),staged=sha(f.read_bytes())) for f in sorted(out.rglob('*')) if f.is_file()]
    inventory=json.loads((HERE.parents[1]/'docs/INSTALLED_MODS.json').read_text());changed={e['path'].lower() for e in files}
    preserved=[dict(path=e['path'],sha256=sha((root/e['path']).read_bytes())) for e in inventory['clientFiles'] if e['path'].lower() not in changed]
    if (root/'PlayerBotBar.ini').exists():preserved.append(dict(path='PlayerBotBar.ini',sha256=sha((root/'PlayerBotBar.ini').read_bytes())))
    manifest=dict(feature='playerbot-party-bar',revision=a.revision or ('formations-1' if a.bridge else 'layout-4'),clientRoot=str(root),files=files,preservedFiles=preserved,archiveChanges=list(changes),
        serverBaseline=dict(root=inventory['serverRoot'],jar=inventory['serverJarSha256'],launcher=inventory['serverLauncherSha256'],overrides=inventory['serverClassOverrides']))
    if a.bridge:manifest.update(bridgeSourceBaseline=sha(live),baselineRebuild=sha(baseline),baselineRebuildNormalized=sha(normalized_pe(baseline)))
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print('OK: staged',len(files),'files; bounded command-bar revision; other addon entries, saved anchor and server preserved:',out)

if __name__=='__main__':main()
