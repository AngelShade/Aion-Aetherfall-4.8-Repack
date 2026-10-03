"""Verify a staged companion patch against the current installed baseline. Never installs."""
import argparse
import json
from pathlib import Path
import subprocess
import prepare

def verify(root, staged):
    manifest = json.loads((staged/'manifest.json').read_text(encoding='utf-8'))
    assert manifest['feature'] == 'player-companions'
    assert Path(manifest['clientRoot']).resolve() == root
    expected = {'bin64/Game.dll', 'Plugin/RelicCalc/RelicCalc.pak', 'Plugin/RelicCalc/RelicCalc.pak.sig',
                'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig', 'Addon.key'}
    assert {f['path'] for f in manifest['files']} == expected
    assert {f.relative_to(staged).as_posix() for f in staged.rglob('*') if f.is_file()} == expected | {'manifest.json'}
    for entry in manifest['files']:
        current = root/entry['path']; result = staged/entry['path']
        assert (prepare.sha(current.read_bytes()) if current.is_file() else None) == entry['original'], 'installed baseline changed: '+entry['path']
        assert prepare.sha(result.read_bytes()) == entry['staged'], 'staged payload changed: '+entry['path']
    for entry in manifest['preservedFiles']:
        assert prepare.sha((root/entry['path']).read_bytes()) == entry['sha256'], 'preserved client changed: '+entry['path']
    assert not (staged/'Pub.key').exists(), 'stock model key must never enter the installation payload'
    assert (staged/'bin64/Game.dll').read_bytes() == prepare.patch((root/'bin64/Game.dll').read_bytes()), 'DLL changes exceed the two verified browser caves'
    before = prepare.read_pak(root/'Plugin/RelicCalc/RelicCalc.pak'); after = prepare.read_pak(staged/'Plugin/RelicCalc/RelicCalc.pak')
    assert before.namelist() == after.namelist() and after.testzip() is None
    for name in before.namelist():
        original = before.read(name)
        assert after.read(name) == (prepare.menu(original) if name == 'PrivateMenus.lua' else original), 'unrelated menu archive entry changed: '+name
    subprocess.run(['java',str(prepare.HERE.parent/'season-pass/VerifySignatures.java'),str(staged),str(root)],check=True)
    print('OK: all six staged files match the current client; exact browser caves, native menu injection, all preserved archive entries, stock key and three addon signatures verified. No installation performed.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True);parser.add_argument('--staged',type=Path,required=True)
    args=parser.parse_args()
    try:verify(args.client.resolve(),args.staged.resolve())
    except Exception as error:
        print('FAIL:',error);raise SystemExit(1)
