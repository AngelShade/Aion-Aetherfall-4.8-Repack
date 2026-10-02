"""Stage only the optional Poeta journey additions, preserving installed client changes."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from patch_game_dll import BROWSER_HOOK_RVA, MARKET_AUTH_HOOK_RVA, build_browser_hook_code, build_market_auth_code, MARKET_RECT_HOOK_RVA, build_market_rect_code


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client-path', type=Path, required=True)
    parser.add_argument('--codec-directory', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--revise-installed', action='store_true', help='Revise a verified already-installed Poeta journey')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, output = args.client_path.resolve(), args.output.resolve()
    if output.exists() or output == root or root in output.parents:
        raise ValueError('Use a new staging directory outside the client')
    sys.path.insert(0, str(args.codec_directory.resolve()))
    from fire_temple_probe import read_pak
    from patch_client_world import encode_pak
    mod = Path(__file__).resolve().parent
    with read_pak(root / 'Plugin/RelicCalc/RelicCalc.pak') as archive:
        content = {name:archive.read(name) for name in archive.namelist()}
    installed_lua = content['PrivateMenus.lua'].decode('utf-8').replace('\r','')
    source_lua = (mod/'PrivateMenus.lua').read_text(encoding='utf-8-sig')
    begin = source_lua.index('function PrivateJourney_OnLoad()')
    end = source_lua.index('function PrivateMenus_Register()', begin)
    old_lua = source_lua[:begin] + source_lua[end:]
    journey_menu = '    SlashCmdList["PRIVATEJOURNEY"] = PrivateJourney_Open;\n    SLASH_PRIVATEJOURNEY1 = "/journey";\n    RegisterMenu("Choose Your Journey", SLASH_PRIVATEJOURNEY1, "v5_start_menu_relic_up");\n'
    if old_lua.count(journey_menu) != 1:
        raise ValueError('Journey menu registration differs from the verified additions')
    old_lua = old_lua.replace(journey_menu,'')
    if args.revise_installed:
        if not installed_lua.endswith(source_lua):
            raise ValueError('Installed journey Lua differs from the verified source')
        prefix = installed_lua[:-len(source_lua)]
        if prefix.count('PRIVATE_JOURNEY_URL = "http://127.0.0.1:8091/journey";') != 1:
            raise ValueError('Installed journey URL differs')
    else:
        if not installed_lua.endswith(old_lua):
            raise ValueError('Installed Lua differs from the verified existing menus')
        prefix = installed_lua[:-len(old_lua)] + 'PRIVATE_JOURNEY_URL = "http://127.0.0.1:8091/journey";\n'
    content['PrivateMenus.lua'] = (prefix + source_lua).replace('\n','\r\n').encode()
    content['Journey.xml'] = (mod/'Journey.xml').read_text(encoding='utf-8-sig').replace('UTF-8','UTF-16').replace('\n','\r\n').encode('utf-16')
    ET.fromstring(content['Journey.xml'])
    toc = content['RelicCalc.toc'].decode().replace('\r','').splitlines()
    if args.revise_installed:
        if toc.count('Journey.xml') != 1: raise ValueError('Installed journey XML registration differs')
    else:
        toc.insert(toc.index('PrivateMenus.lua'), 'Journey.xml')
    content['RelicCalc.toc'] = ('\r\n'.join(toc)+'\r\n').encode()
    data = io.BytesIO()
    with zipfile.ZipFile(data,'w',compression=zipfile.ZIP_DEFLATED) as archive:
        for name, payload in content.items():
            archive.writestr(name,payload)
    pak = output/'Plugin/RelicCalc/RelicCalc.pak'
    pak.parent.mkdir(parents=True)
    pak.write_bytes(encode_pak(data.getvalue()))
    with read_pak(pak) as archive:
        assert archive.testzip() is None
        assert all(archive.read(name) == payload for name,payload in content.items())
    subprocess.run([str(args.java),str(mod/'SignClientPackages.java'),str(root),str(output)],check=True)

    old_routes = ['http://127.0.0.1:8091/shop','http://127.0.0.1:8091/market','http://127.0.0.1:8091/market/wardrobe']
    routes = old_routes + ['http://127.0.0.1:8091/journey']
    original = (root/'bin64/game.dll').read_bytes()
    dll = bytearray(original)
    ranges = []
    for offset, before, after, limit in [
        (BROWSER_HOOK_RVA,build_browser_hook_code(routes if args.revise_installed else old_routes),build_browser_hook_code(routes),1024),
        (MARKET_AUTH_HOOK_RVA,build_market_auth_code(routes[1:] if args.revise_installed else old_routes[1:]),build_market_auth_code(routes[1:]),512),
        (MARKET_RECT_HOOK_RVA,build_market_rect_code(False),build_market_rect_code(),512)]:
        if dll[offset:offset+len(before)] != before or len(after)>limit:
            raise ValueError('Installed browser or layout hook differs from the verified current code')
        end = offset + max(len(before),len(after))
        if any(dll[offset+len(before):end]):
            raise ValueError('Browser code expansion would overwrite another patch')
        dll[offset:end] = after + bytes(end-offset-len(after))
        ranges.append((offset,end))
    assert len(dll) == len(original)
    assert all(a==b or any(start<=i<end for start,end in ranges) for i,(a,b) in enumerate(zip(original,dll)))
    from graphics_compat import prepare_incremental
    dll, graphics_compatibility = prepare_incremental(root,output,bytes(dll))
    (output/'bin64').mkdir(exist_ok=True)
    (output/'bin64/game.dll').write_bytes(dll)
    # The installed archive loader already isolates the stock model key. Keep it.
    (output/'bin64/crysystem.dll').write_bytes((root/'bin64/crysystem.dll').read_bytes())
    source = mod.parent/'native-icon-bridge'
    sys.path.insert(0,str(source))
    from patch_client import AWESOMIUM_SHA256
    if digest(root/'bin64/Awesomium.dll') != AWESOMIUM_SHA256:
        raise ValueError('Wrong native browser version')
    work = output.parent/(output.name+'-native-build');work.mkdir()
    vcvars = Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    script = work/'compile.cmd'
    script.write_text(f'@echo off\ncall "{vcvars}" >nul && cl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"{work/"bridge.obj"}" "{source/"icon_bridge.cpp"}" /link /OUT:"{output/"bin64/AionIconBridge.dll"}" /IMPLIB:"{work/"bridge.lib"}" windowscodecs.lib ole32.lib bcrypt.lib user32.lib\n')
    subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True)
    files = [dict(path=f.relative_to(output).as_posix(),original=digest(root/f.relative_to(output)) if (root/f.relative_to(output)).is_file() else None,staged=digest(f)) for f in sorted(output.rglob('*')) if f.is_file()]
    preserved = ['bin64/AionIconBridge.index','Data/Items/Items.pak','Data/ui/game/game.pak','L10N/enu/data/data.pak','bin32/bin32.pak','Data/func_pet/func_pet.pak']
    manifest = dict(clientRoot=str(root),files=files,signatureIsolation='archive-v2',poetaJourney=True,
                    legacyAddon=[],retiredFiles=[],preservedFiles=[dict(path=p,sha256=digest(root/p)) for p in preserved])
    if graphics_compatibility:
        manifest['graphicsCompatibility'] = graphics_compatibility
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print(f'Prepared {len(files)} hash-checked files; native UI archives, icon index, items and pet archive preserved. Client untouched: {output}')


if __name__ == '__main__':
    main()
