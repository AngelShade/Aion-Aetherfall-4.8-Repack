"""Stage movement for Profile, full Inventory and native Warehouse only."""
import argparse
import hashlib
import json
from pathlib import Path
from graphics_compat import prepare_incremental
from movable_windows import patch_archive
from detached_inventory import patch_detached_inventory


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();root=args.client.resolve();out=args.output.resolve()
    if out.exists() or out==root or root in out.parents:raise ValueError('Use a fresh staging folder outside the client')
    dll=(root/'bin64/Game.dll').read_bytes()
    if patch_detached_inventory(dll)!=dll:raise ValueError('Expected the installed Inventory split')
    out.mkdir(parents=True)
    _,graphics=prepare_incremental(root,out,dll)
    ui=[('Data/ui/game/game.pak',''),('L10N/enu/Data/data.pak','ui/game/')]
    if graphics:
        state_path=out/'DXVK/graphics-menu/installed.json'
        manifest_path=out/'DXVK/graphics-menu/package/manifest.json'
        state=json.loads(state_path.read_text());graphics_manifest=json.loads(manifest_path.read_text())
        baseline=out/'DXVK-backups'/graphics['backupName']
    for relative,prefix in ui:
        source=root/relative
        if not source.exists():raise ValueError('Expected English client UI')
        installed=patch_archive(source,prefix)
        destination=out/relative;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(installed)
        if graphics:
            record=next(e for e in state['files'] if e['path'].lower()==relative.lower())
            backup=baseline/record['path']
            original=patch_archive(backup,prefix);backup.write_bytes(original)
            (out/'DXVK/graphics-menu/package'/record['path']).write_bytes(installed)
            record.update(original=hashlib.sha256(original).hexdigest(),installed=hashlib.sha256(installed).hexdigest())
    if graphics:
        graphics_manifest['files']=state['files']
        state_path.write_text(json.dumps(state,indent=2));manifest_path.write_text(json.dumps(graphics_manifest,indent=2))
    preserved=['bin64/Game.dll','bin64/CrySystem.dll','bin64/AionIconBridge.dll','bin64/AionIconBridge.index',
        'Plugin/RelicCalc/RelicCalc.pak','Plugin/RelicCalc/RelicCalc.pak.sig','Pub.key','Addon.key',
        'Data/Items/Items.pak','bin32/bin32.pak','Data/func_pet/func_pet.pak','Aion Start.bat']
    manifest=dict(clientRoot=str(root),nativeWindowMovement='v1',graphicsCompatibility=graphics,
        legacyAddon=[],retiredFiles=[],
        files=[dict(path=p.relative_to(out).as_posix(),original=digest(root/p.relative_to(out)) if (root/p.relative_to(out)).exists() else None,staged=digest(p)) for p in sorted(out.rglob('*')) if p.is_file()],
        preservedFiles=[dict(path=n,sha256=digest(root/n)) for n in preserved])
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print(f'Prepared {len(manifest["files"])} files; native movement for three dialogs in both UI archives and matching graphics restore baselines. Client untouched.')


if __name__=='__main__':main()
