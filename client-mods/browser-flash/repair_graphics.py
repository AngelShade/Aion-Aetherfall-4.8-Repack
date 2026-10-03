"""Repair only the exact diagnostic's missing launcher tracking, with backups."""
import argparse
from datetime import datetime
import json
from pathlib import Path
import shutil
from graphics_compat import prepare,read
from stage import sha
from install import closed,replace

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--backup',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args()
    out=a.output.resolve();backup=a.backup.resolve();receipt=read(backup/'receipt.json');root=Path(receipt['client']).resolve()
    if not backup.is_relative_to(root/'BrowserFrame-backups') or out.is_relative_to(root):raise ValueError('Unexpected repair paths')
    for name,digest in receipt['files'].items():
        if name not in ['Game.dll','AionBrowserProbe.dll'] or sha((root/'bin64'/name).read_bytes())!=digest:raise ValueError('Diagnostic changed after installation')
    prior=(backup/'Game.dll').read_bytes()
    if sha(prior)!=receipt['originalGameSha256']:raise ValueError('Diagnostic backup changed')
    if not a.apply:
        out.mkdir(parents=True,exist_ok=True);installed=(root/'bin64/Game.dll').read_bytes()
        extras,compat=prepare(root,out,prior,installed,already_installed=True)
        if not compat:raise ValueError('No active Graphics menu')
        # The synthetic verification client also receives the live Game.dll.
        (out/'bin64').mkdir(exist_ok=True);(out/'bin64/Game.dll').write_bytes(installed)
        manifest=dict(clientRoot=str(root),files=extras+[dict(path='bin64/Game.dll',original=sha(prior),installed=sha(installed))],graphicsCompatibility=compat,compactBrowserTitles=True,repairFiles=extras,diagnosticBackup=str(backup))
        (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
        print('OK: staged Graphics tracking and diagnostic-preserving restore baselines; client unchanged');return
    manifest=read(out/'manifest.json')
    if manifest['clientRoot']!=str(root) or manifest['diagnosticBackup']!=str(backup):raise ValueError('Unexpected staged repair')
    closed();saved=root/'BrowserFrame-backups'/('graphics-tracking-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));saved.mkdir(parents=True,exist_ok=False)
    entries=manifest['repairFiles'];preserved={name:sha((root/'bin64'/name).read_bytes()) for name in receipt['files']}
    for e in entries:
        rel=Path(e['path']);live=root/rel
        if rel.is_absolute() or not live.resolve().is_relative_to(root):raise ValueError('Repair target outside client')
        current=sha(live.read_bytes()) if live.exists() else None
        if current!=e['original'] or sha((out/rel).read_bytes())!=e['installed']:raise ValueError('Repair inputs changed: '+e['path'])
        if live.exists():
            target=saved/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(live,target)
    (saved/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    applied=[]
    try:
        for e in entries:
            live=root/e['path'];live.parent.mkdir(parents=True,exist_ok=True);replace(live,(out/e['path']).read_bytes());applied.append(e)
        for e in entries:assert sha((root/e['path']).read_bytes())==e['installed']
        for name,digest in preserved.items():assert sha((root/'bin64'/name).read_bytes())==digest
        receipt.update(graphicsTrackingBackup=str(saved),graphicsCompatibility=manifest['graphicsCompatibility'],graphicsTrackingFiles=entries)
        replace(backup/'receipt.json',json.dumps(receipt,indent=2).encode('utf-8'))
    except Exception:
        for e in reversed(applied):
            live=root/e['path']
            if sha(live.read_bytes())!=e['installed']:continue
            if e['original'] is None:live.unlink()
            else:replace(live,(saved/e['path']).read_bytes())
        raise
    print('OK: launcher tracking repaired; Game.dll unchanged; hash guards preserved. Backup:',saved)
if __name__=='__main__':main()
