"""Stage a focused correction without rebuilding installed UI or game.dll."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from patch_plugin_key import patch_plugin_key

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--client-path',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--java',type=Path,required=True)
    args=parser.parse_args()
    root=args.client_path.resolve();output=args.output.resolve()
    if output.exists() or root==output or root in output.parents:
        raise ValueError('Use a new staging directory outside the client')
    output.mkdir(parents=True)
    subprocess.run([str(args.java),str(Path(__file__).with_name('SignClientPackages.java')),str(root),str(output)],check=True)
    engine=output/'bin64/crysystem.dll';engine.parent.mkdir(parents=True,exist_ok=True)
    engine.write_bytes(patch_plugin_key((root/'bin64/crysystem.dll').read_bytes()))
    replacements=[]
    for path in sorted(output.rglob('*')):
        if path.is_file():
            relative=path.relative_to(output).as_posix();source=root/relative
            replacements.append({'path':relative,'original':digest(source) if source.exists() else None,'staged':digest(path)})
    legacy=root/'Plugin/TransmogMenu'
    if legacy.exists():raise ValueError('Legacy addon present; use the complete menu builder')
    manifest={'clientRoot':str(root),'files':replacements,'legacyAddon':[],'retiredFiles':[],
              'inventorySlots':0,'signatureIsolation':'archive-v2','signatureRepair':True}
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(f'Prepared {len(replacements)} signing replacements; installed UI and game.dll preserved.')

if __name__=='__main__':main()
