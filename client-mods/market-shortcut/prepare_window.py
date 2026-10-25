"""Prepare the original Marketplace with the Player Companions frame and sizing.

Reuses CashShop.xml and its browser identity, retaining native auth and preview.
Only its two plugin members and the verified fullscreen layout cave change.
Install through account-window/install.py after closing the original client.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'account-window'))
from prepare_window import extend, parts, read_pak_bytes, RELIC  # noqa: E402
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
from patch_game_dll import MARKET_RECT_HOOK_RVA, BROWSER_HOOK_RVA, build_market_rect_code  # noqa: E402
from graphics_compat import prepare_incremental  # noqa: E402


def sha(data):
    return hashlib.sha256(data).hexdigest()


def menus(data, *, update=False):
    replacement = (HERE / 'MarketplaceWindow.lua').read_bytes().replace(b'\r\n', b'\n').rstrip(b'\n')
    if update:
        pattern = (rb'-- Same movable, resizable frame[^\n]*\n'
                   rb'PRIVATE_CASH_SHOP_MIN_W.*?(?=function PrivateWarehouse_OnLoad\(\))')
        # Recognize the installed revision by hash; keep one maintained controller.
        accepted = {'6a1fd2309fc88d438154f5005eb9aa51c1479dbe523db03e36a9e76d9582dd3f', sha(replacement)}
    else:
        if b'PrivateCashShop_Fit' in data:
            raise ValueError('Marketplace is already windowed; use the retained-layout Lua update')
        pattern = rb'function PrivateCashShop_OnLoad\(\).*?\nend(?:\r*\n)+function PrivateCashShop_Open\(\).*?\nend(?:\r*\n)+'
        accepted = {'6edf2734267361e16e8dc08171ebfdb594fdab30b231951eabbc9f5c4ff71a02'}
    matches = list(re.finditer(pattern, data, re.S))
    if len(matches) != 1:
        raise ValueError('Unexpected installed Cash Shop handlers')
    old = matches[0].group().replace(b'\r', b'').rstrip(b'\n')
    if sha(old) not in accepted:
        raise ValueError('Unrecognized shop implementation; preserve it for review')
    newline = b'\r\r\n' if b'\r\r\n' in matches[0].group() else b'\r\n' if b'\r\n' in data else b'\n'
    replacement += b'\n\n'
    match = matches[0]
    return data[:match.start()] + replacement.replace(b'\n', newline) + data[match.end():]


def dialog(data):
    text = data.decode('utf-16')
    root = ET.fromstring(text)
    window = root.find('Dialog')
    browser = root.find('.//Widget')
    if window.get('name') != 'PrivateCashShop' or browser.get('name') != 'PrivateCashShopBrowser':
        raise ValueError('Unrecognized native shop widgets')
    changes = {'frame="0,0,1280,960"': 'frame="0,0,1180,780"',
               'frame="0,0,1280,935"': 'frame="0,0,1180,755" h_pos_align="proportion" v_pos_align="proportion"',
               'flag="close;title;not_visible;not_movable;fittoscreenwidth;fittoscreenheight"':
               'flag="close;title;not_visible;movable;resizable"',
               'shadow_offset="0,0"': 'shadow_offset="8,8"',
               '<OnLoad>PrivateCashShop_OnLoad();</OnLoad>':
               '<OnLoad>PrivateCashShop_OnLoad();</OnLoad><OnEvent>PrivateCashShop_OnEvent(event);</OnEvent>'}
    for before, after in changes.items():
        if text.count(before) != 1:
            raise ValueError('Unexpected native shop layout: ' + before)
        text = text.replace(before, after)
    return text.encode('utf-16')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external staging directory')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('Original development client only')
    original = (client / 'bin64/Game.dll').read_bytes()
    start, end = MARKET_RECT_HOOK_RVA, BROWSER_HOOK_RVA
    expected = build_market_rect_code().ljust(end - start, b'\0')
    if original[start:end] != expected:
        raise ValueError('Installed native layout differs from the verified current hook')
    updated = build_market_rect_code(windowed_cashshop=True)
    if len(updated) > end - start:
        raise ValueError('Windowed-shop layout exceeds its verified cave')
    dll = original[:start] + updated.ljust(end - start, b'\0') + original[end:]
    raw = (client / RELIC).read_bytes()
    with read_pak_bytes(raw) as z:
        names = z.namelist()
        replacements = {'CashShop.xml': dialog(z.read('CashShop.xml')), 'PrivateMenus.lua': menus(z.read('PrivateMenus.lua'))}
    built = extend(raw, replacements, {})
    before, after = parts(raw)[0], parts(built)[0]
    with read_pak_bytes(built) as z:
        if z.namelist() != names or z.testzip() is not None:
            raise ValueError('Plugin archive structure changed')
        for name in names:
            if name in replacements:
                if z.read(name) != replacements[name]:
                    raise ValueError('Changed member mismatch: ' + name)
            elif before[name][0] != after[name][0]:
                raise ValueError('Unrelated compressed plugin member changed: ' + name)
    destination = output / RELIC
    destination.parent.mkdir(parents=True)
    destination.write_bytes(built)
    subprocess.run(['java', str(HERE.parent / 'transmog-menu/SignClientPackages.java'), str(client), str(output)], check=True)
    (output / 'Pub.key').unlink()
    dll, graphics = prepare_incremental(client, output, dll)
    target = output / 'bin64/Game.dll'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(dll)
    files = [dict(path=p.relative_to(output).as_posix(), original=sha((client / p.relative_to(output)).read_bytes())
                  if (client / p.relative_to(output)).exists() else None, staged=sha(p.read_bytes()))
             for p in sorted(output.rglob('*')) if p.is_file()]
    sources = [Path(__file__).resolve(), HERE / 'MarketplaceWindow.lua',
               HERE.parent / 'account-window/prepare_window.py', HERE.parent / 'transmog-menu/patch_game_dll.py',
               HERE.parent / 'transmog-menu/graphics_compat.py', HERE.parent / 'transmog-menu/SignClientPackages.java']
    preserved = ['Pub.key', 'bin32/bin32.pak', 'Data/func_pet/func_pet.pak', 'Data/ui/game/game.pak',
                 'L10N/enu/Data/data.pak', 'bin64/AionIconBridge.dll', 'bin64/AionUiArt.index',
                 'bin64/AionMarketShortcut.dll', 'bin64/AetherfallCore.dll']
    manifest = dict(feature='marketplace-window', revision='companions-1', clientRoot=str(client), files=files,
                    sourceHashes={str(p): sha(p.read_bytes()) for p in sources}, graphicsCompatibility=graphics,
                    preservedFiles=[dict(path=p, sha256=sha((client / p).read_bytes())) for p in preserved],
                    pluginDialogsAdded=0, allowedDllRange=[start, end], changedPluginMembers=list(replacements),
                    gameplayAcceptance='Pending owner: shop opens at 1180x780, moves/resizes and fills at UI scale; categories, favorites, history, native previews, quantity, purchase review and mail receipt; Companions/Account and other UI regression checks')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(files), 'affected files; unrelated compressed plugin members and DLL bytes preserved:', output)


if __name__ == '__main__':
    main()
