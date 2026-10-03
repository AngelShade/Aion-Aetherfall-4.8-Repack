"""Stage a bounded title-gap correction against the installed client."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

from patch_game_dll import MARKET_RECT_HOOK_RVA, BROWSER_HOOK_RVA, build_market_rect_code
from graphics_compat import prepare_incremental

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'speech-bubbles'))
from artwork import read_pak, rewrite


def sha(data):
    return hashlib.sha256(data).hexdigest()


def patch_dll(data):
    before = build_market_rect_code(client_origin=False)
    after = build_market_rect_code()
    start, end = MARKET_RECT_HOOK_RVA, BROWSER_HOOK_RVA
    if data[start:end] != before.ljust(end-start, b'\0'):
        raise ValueError('Installed fullscreen layout differs from the verified four-window hook')
    if len(before) != len(after):
        raise ValueError('Title correction must preserve layout code size')
    result = bytearray(data)
    result[start:start+len(after)] = after
    return bytes(result)


def patch_xml(payload, name):
    text = payload.decode('utf-16')
    browser = ET.fromstring(text).find(".//Widget[@name='"+name+"']")
    if browser is None or browser.get('frame') != '0,25,1280,935':
        raise ValueError('Unexpected installed browser frame: '+name)
    # Preserve installed encoding, whitespace, titles, flags and scripts.
    if text.count('frame="0,25,1280,935"') != 1:
        raise ValueError('Ambiguous installed browser frame')
    return text.replace('frame="0,25,1280,935"', 'frame="0,0,1280,935"').encode('utf-16')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if output.exists() or output == root or root in output.parents:
        raise ValueError('Use a new staging directory outside the client')
    original_dll = (root/'bin64/Game.dll').read_bytes()
    dll = patch_dll(original_dll)
    pak = 'Plugin/RelicCalc/RelicCalc.pak'
    source_snapshot = {'bin64/Game.dll':sha(original_dll), pak:sha((root/pak).read_bytes())}
    remembered_login = None
    if (root/'bin64/AionRememberLogin.dll').exists():
        receipts = sorted((root/'RememberLogin-backups').glob('*/manifest.json'))
        if not receipts:
            raise ValueError('Installed Remember Login has no verification receipt')
        receipt = receipts[-1]
        login = json.loads(receipt.read_text(encoding='utf-8-sig'))
        if login.get('feature') != 'remember-login' or Path(login['clientRoot']).resolve() != root:
            raise ValueError('Remember Login receipt belongs to another implementation')
        for entry in login['files']:
            if sha((root/entry['path']).read_bytes()) != entry['installed']:
                raise ValueError('Client differs from latest Remember Login receipt: '+entry['path'])
        remembered_login = dict(receipt=str(receipt), sha256=sha(receipt.read_bytes()), hooks=login['hooks'])
    with read_pak(root/pak) as archive:
        replacements = {name: patch_xml(archive.read(name), browser) for name, browser in
                        [('CashShop.xml', 'PrivateCashShopBrowser'), ('Warehouse.xml', 'PrivateWarehouseBrowser')]}
        preserved_entries = {name:sha(archive.read(name)) for name in archive.namelist() if name not in replacements}
    destination = output/pak
    destination.parent.mkdir(parents=True)
    destination.write_bytes(rewrite(root/pak, replacements))
    with read_pak(destination) as archive:
        assert archive.testzip() is None
        assert all(sha(archive.read(name)) == digest for name, digest in preserved_entries.items())
    subprocess.run(['java', str(HERE/'SignClientPackages.java'), str(root), str(output)], check=True)
    (output/'Pub.key').unlink()  # Keep the installed stock model key.
    dll, graphics = prepare_incremental(root, output, dll)
    game = output/'bin64/Game.dll'
    game.parent.mkdir(parents=True, exist_ok=True)
    game.write_bytes(dll)
    files = [dict(path=f.relative_to(output).as_posix(),
                  original=sha((root/f.relative_to(output)).read_bytes()) if (root/f.relative_to(output)).exists() else None,
                  staged=sha(f.read_bytes())) for f in sorted(output.rglob('*')) if f.is_file()]
    preserved = ['Pub.key', 'bin64/crysystem.dll', 'bin64/AionIconBridge.dll',
                 'bin64/AionMarketShortcut.dll', 'bin64/AionGraphicsMenu.dll',
                 'bin64/XRenderD3D9.dll', 'bin64/Awesomium.dll', 'bin32/bin32.pak',
                 'Data/func_pet/func_pet.pak', 'Data/Items/Items.pak', 'Textures/ui/ui.pak',
                 'Data/ui/ui.pak', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']
    preserved += [rel for rel in ['bin64/AionRememberLogin.dll', 'bin64/AionSpeechBubbles.dll',
                                  'bin64/AionIconBridge.index'] if (root/rel).exists()]
    for rel, digest in source_snapshot.items():
        if sha((root/rel).read_bytes()) != digest:
            raise ValueError('Client changed during preparation: '+rel)
    manifest = dict(clientRoot=str(root), files=files, compactBrowserTitles=True,
                    signatureIsolation='archive-v2', legacyAddon=[], retiredFiles=[],
                    preservedFiles=[dict(path=rel, sha256=sha((root/rel).read_bytes())) for rel in preserved],
                    preservedMenuEntries=preserved_entries)
    if graphics:
        manifest['graphicsCompatibility'] = graphics
    if remembered_login:
        manifest['rememberedLoginPreservation'] = remembered_login
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('OK: compact fullscreen titles prepared; installed client untouched:', output)


if __name__ == '__main__':
    main()
