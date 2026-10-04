"""Stage an incremental companion menu over the current client. Never installs or starts Aion."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
import patch_game_dll as hooks
from codec import read_pak, encode_pak
from graphics_compat import prepare_incremental

ROUTES = ['http://127.0.0.1:8091/shop', 'http://127.0.0.1:8091/market', 'http://127.0.0.1:8091/market/wardrobe', 'http://127.0.0.1:8091/journey', 'http://127.0.0.1:8091/market/pass']
URL = 'http://127.0.0.1:8091/market/companions'
RANGES = [(hooks.MARKET_AUTH_HOOK_RVA, hooks.MARKET_RECT_HOOK_RVA), (hooks.BROWSER_HOOK_RVA, hooks.PREVIEW_DOCK_HOOK_RVA)]
PRESERVED = ['Pub.key', 'bin64/crysystem.dll', 'bin64/AionIconBridge.dll', 'bin64/AionGraphicsMenu.dll', 'bin64/XRenderD3D9.dll', 'bin64/AionMarketShortcut.dll',
             'bin64/AionRememberLogin.dll', 'bin64/AionSpeechBubbles.dll', 'bin64/Awesomium.dll', 'Aion Start.bat',
             'bin32/bin32.pak', 'Data/func_pet/func_pet.pak', 'Data/ui/game/game.pak', 'Data/ui/ui.pak', 'L10N/enu/data/data.pak', 'Data/Items/Items.pak']

def sha(data): return hashlib.sha256(data).hexdigest()

def patch(data):
    previous = [hooks.build_market_auth_code(ROUTES[1:], compact=True), hooks.build_browser_hook_code(ROUTES)]
    updated = [hooks.build_market_auth_code(ROUTES[1:] + [URL], compact=True), hooks.build_browser_hook_code(ROUTES + [URL])]
    result = bytearray(data)
    for (start, end), old, new in zip(RANGES, previous, updated):
        if data[start:end] != old.ljust(end-start, b'\0'):
            raise ValueError('Current browser hook differs from the verified Season Pass baseline; preserve and review the newer client first')
        if len(new) > end-start: raise ValueError('Companion hook exceeds its existing cave')
        result[start:end] = new.ljust(end-start, b'\0')
    return bytes(result)

def menu(data):
    text = data.decode('utf-8-sig').replace('\r\n', '\n')
    anchor = 'function PrivateMenus_Register()\n'
    if 'PRIVATECOMPANIONS' in text or text.count(anchor) != 1 or 'PRIVATESEASONPASS' not in text:
        raise ValueError('Unexpected current native menu baseline')
    source = (HERE.parent / 'transmog-menu/PrivateMenus.lua').read_text(encoding='utf-8-sig')
    function = re.search(r'function PrivateCompanions_Open\(\)\n.*?\nend\n', source, re.S).group()
    addition = '    SlashCmdList["PRIVATECOMPANIONS"] = PrivateCompanions_Open;\n    SLASH_PRIVATECOMPANIONS1 = "/companions";\n    RegisterMenu("Player Companions", SLASH_PRIVATECOMPANIONS1, "v5_start_menu_relic_up");\n'
    return ('PRIVATE_PLAYERBOTS_URL = ' + json.dumps(URL) + ';\n' + function + '\n' + text.replace(anchor, anchor + addition)).replace('\n', '\r\n').encode('utf-8')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True); parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); root = args.client.resolve(); output = args.output.resolve()
    if output.exists() or output == root or root in output.parents: raise ValueError('Use a new staging directory outside the client')
    payloads = {'bin64/Game.dll': patch((root/'bin64/Game.dll').read_bytes())}
    pak = 'Plugin/RelicCalc/RelicCalc.pak'; archive = read_pak(root/pak)
    replaced = menu(archive.read('PrivateMenus.lua')); buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_DEFLATED) as target:
        for info in archive.infolist(): target.writestr(copy.copy(info), replaced if info.filename == 'PrivateMenus.lua' else archive.read(info.filename))
    payloads[pak] = encode_pak(buffer.getvalue())
    for rel, data in payloads.items():
        path = output/rel; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    subprocess.run(['java', str(HERE.parent/'transmog-menu/SignClientPackages.java'), str(root), str(output)], check=True)
    # Stock Pub.key is verified by the signer and deliberately omitted from installation payloads.
    (output/'Pub.key').unlink()
    _, graphics = prepare_incremental(root, output, payloads['bin64/Game.dll'])
    files = [dict(path=f.relative_to(output).as_posix(), original=sha((root/f.relative_to(output)).read_bytes()) if (root/f.relative_to(output)).is_file() else None,
                  staged=sha(f.read_bytes())) for f in sorted(output.rglob('*')) if f.is_file()]
    manifest = dict(feature='player-companions', clientRoot=str(root), files=files, allowedDllRanges=RANGES, graphicsCompatibility=graphics,
                    preservedFiles=[dict(path=rel, sha256=sha((root/rel).read_bytes())) for rel in PRESERVED if (root/rel).is_file()])
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    staged = read_pak(output/pak)
    assert staged.namelist() == archive.namelist() and staged.testzip() is None
    for entry in archive.namelist(): assert staged.read(entry) == (replaced if entry == 'PrivateMenus.lua' else archive.read(entry)), entry
    print('OK: staged',len(files),'incremental files; archive entries and other DLL bytes preserved. Installed client untouched:',output)

if __name__ == '__main__':
    main()
