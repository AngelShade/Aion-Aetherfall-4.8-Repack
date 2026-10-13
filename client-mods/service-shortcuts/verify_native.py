"""Compile changed native source and execute installed hooks in isolated memory.

No client process is started or attached and no installed file is modified.
Generated DLLs, compiler logs and evidence remain in the external output directory.
"""
import argparse
import ctypes as C
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / 'market-shortcut' / 'market_shortcut.cpp'
DEFAULT_CLIENT = (Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
DEFAULT_OUT = Path(r'D:\Proiecte\Project Restructure\Aion Development Workspace\staging\output\service-shortcuts-native')
VCVARS = Path(r'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat')
sys.path.insert(0, str(HERE.parent / 'speech-bubbles'))
from loader_permissions import writable_iats
from capstone import Cs, CS_ARCH_X86, CS_MODE_64


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compile_source(out, test):
    name = 'ServiceTest' if test else 'AionMarketShortcut'
    dll = out / (name + '.dll')
    script = out / (name + '.cmd')
    script.write_text(
        '@echo off\n'
        f'call "{VCVARS}" >nul\nif errorlevel 1 exit /b %errorlevel%\n'
        'cl /nologo /std:c++17 /EHsc /O2 /MT /LD '
        + ('/DMARKET_TEST ' if test else '')
        + f'/Fo:"{out / (name + ".obj")}" "{SOURCE}" /link '
        + f'/OUT:"{dll}" /IMPLIB:"{out / (name + ".lib")}"\n', encoding='utf-8')
    result = subprocess.run(f'cmd.exe /d /s /c ""{script}""',
                            capture_output=True, text=True)
    (out / (name + '-compile.txt')).write_text(result.stdout + result.stderr, encoding='utf-8')
    assert result.returncode == 0, f'Compilation failed: {name}-compile.txt'
    assert dll.is_file(), 'Compiled DLL absent'
    return dll


def verify(client, out, manifest_path):
    game_path = client / 'bin64/Game.dll'
    original_game_sha = sha(game_path)
    source_sha = sha(SOURCE)
    manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    hooks = manifest['hooks']['bin64/Game.dll']
    assert [h['export'] for h in hooks] == ['AionMarketLayout', 'AionMarketCommand']
    production_dll = compile_source(out, False)
    test_dll = compile_source(out, True)
    # Load the freshly built product too: exports must exist in the actual deliverable.
    product = C.WinDLL(str(production_dll))
    assert product.AionMarketLayout and product.AionMarketCommand
    native = C.WinDLL(str(test_dll))
    native.AionMarketTestBase.argtypes = [C.c_void_p]
    native.AionMarketLayout.argtypes = [C.c_void_p]
    native.AionMarketCommand.argtypes = [C.c_void_p, C.c_void_p]
    native.AionMarketCommand.restype = C.c_int

    b = game_path.read_bytes()
    assert writable_iats(b)[0] == b, 'Current IAT permissions would require a Game.dll change'
    pe = struct.unpack_from('<I', b, 60)[0]
    assert b[pe:pe + 4] == b'PE\0\0'
    opt = pe + 24
    assert struct.unpack_from('<H', b, opt)[0] == 0x20b, 'Expected x64 client'
    table = opt + struct.unpack_from('<H', b, pe + 20)[0]
    sections = [struct.unpack_from('<8sIIIIIIHHI', b, table + i * 40)
                for i in range(struct.unpack_from('<H', b, pe + 6)[0])]

    def off(rva):
        for section in sections:
            if section[2] <= rva < section[2] + section[3]:
                return section[4] + rva - section[2]
        raise AssertionError(f'Unmapped RVA {rva:x}')

    def cstring(rva):
        start = off(rva)
        return b[start:b.index(b'\0', start)].decode('ascii')

    cursor = off(struct.unpack_from('<I', b, opt + 120)[0])
    slots = {}
    while any(b[cursor:cursor + 20]):
        lookup, _, _, name, iat = struct.unpack_from('<IIIII', b, cursor)
        if cstring(name).lower() == 'aionmarketshortcut.dll':
            n = 0
            while True:
                entry = struct.unpack_from('<Q', b, off(lookup or iat) + n * 8)[0]
                if not entry:
                    break
                assert not entry & (1 << 63), 'Unexpected ordinal import'
                slots[cstring(entry + 2)] = iat + n * 8
                n += 1
        cursor += 20
    assert set(slots) == {'AionMarketLayout', 'AionMarketCommand'}, slots
    kernel = C.WinDLL('kernel32', use_last_error=True)
    kernel.VirtualAlloc.argtypes = [C.c_void_p, C.c_size_t, C.c_ulong, C.c_ulong]
    kernel.VirtualAlloc.restype = C.c_void_p
    kernel.VirtualFree.argtypes = [C.c_void_p, C.c_size_t, C.c_ulong]
    kernel.VirtualFree.restype = C.c_int
    size = struct.unpack_from('<I', b, opt + 56)[0] + 0x1000
    mem = kernel.VirtualAlloc(None, size, 0x3000, 0x40)
    assert mem, C.get_last_error()
    checks = []

    def check(condition, label):
        assert condition, label
        checks.append(label)

    try:
        owned = next(s for s in sections if s[0].rstrip(b'\0') == b'.mkt')
        C.memmove(mem + owned[2], b[owned[4]:owned[4] + owned[3]], owned[3])
        records = {}
        handled = 1
        F = C.CFUNCTYPE(C.c_size_t, C.c_size_t, C.c_size_t, C.c_size_t, C.c_size_t)
        cb0 = F(lambda a, b, c, d: records.update(layout=a) or 0)
        cb1 = F(lambda a, b, c, d: records.update(command=(a, b, c, d)) or handled)
        for i, hook in enumerate(hooks):
            C.c_size_t.from_address(mem + slots[hook['export']]).value = C.cast([cb0, cb1][i], C.c_void_p).value
            site, target = int(hook['site'], 16), int(hook['hook'], 16)
            original = bytes.fromhex(hook['original'])
            installed = b[off(site):off(site) + len(original)]
            check(installed[0] == 0xe9 and site + 5 + struct.unpack_from('<i', installed, 1)[0] == target,
                  f'Installed {hook["export"]} jump targets verified hook')
            C.memmove(mem + site, installed, len(installed))
            ins = list(Cs(CS_ARCH_X86, CS_MODE_64).disasm(b[off(target):off(target) + hook['size']], target))
            check(sum(x.size for x in ins) == hook['size'], f'{hook["export"]} complete hook disassembly')
            continuation = (bytes.fromhex('488b4424284883c4305bc3') if i == 0
                            else bytes.fromhex('488b5c24504883c448b82a000000c3'))
            C.memmove(mem + site + len(original), continuation, len(continuation))
        wrapper = mem + size - 0x1000
        site = int(hooks[0]['site'], 16)
        prefix = bytes.fromhex('534883ec30488bd948894c2428')
        code = prefix + b'\xe9' + struct.pack('<i', mem + site - (wrapper + len(prefix) + 5))
        C.memmove(wrapper, code, len(code))
        layout_hook = F(wrapper)
        click_hook = F(mem + int(hooks[1]['site'], 16))
        check(layout_hook(123, 456, 789, 101) == 123 and records['layout'] == 123,
              'Layout hook preserves original stack/register continuation')
        check(click_hook(11, 22, 33, 44) == 1 and records['command'] == (11, 22, 33, 44),
              'Command hook forwards native arguments and consumes handled event')
        handled = 0
        check(click_hook(11, 22, 33, 44) == 42, 'Unhandled hook reaches original continuation')
        # The same installed hooks now invoke the freshly compiled actual callbacks.
        native.AionMarketTestBase(mem)
        for hook in hooks:
            C.c_size_t.from_address(mem + slots[hook['export']]).value = C.cast(getattr(native, hook['export']), C.c_void_p).value
        callbacks = []
        vt = (C.c_void_p * 200)()
        widgets, visible, names = {}, {}, {}
        moves, events = [], []
        all_names = ['central_market_button', 'season_pass_button', 'companion_menu_button',
                     'journey_menu_button', 'item_shop_container', 'item_ingame_web_shop',
                     'item_shop', 'item_shop_gf', 'fly_gauge']
        for name in all_names:
            buf = C.create_string_buffer(0x300)
            C.c_size_t.from_buffer(buf).value = C.addressof(vt)
            widgets[name] = buf
            names[C.addressof(buf)] = C.create_string_buffer(name.encode())
        dialog = C.create_string_buffer(0x300)
        C.c_size_t.from_buffer(dialog).value = C.addressof(vt)
        dialog_address = C.addressof(dialog)

        def bind(slot, signature, fn):
            cb = signature(fn)
            callbacks.append(cb)
            vt[slot // 8] = C.cast(cb, C.c_void_p).value

        bind(0x338, C.CFUNCTYPE(C.c_void_p, C.c_void_p, C.c_char_p, C.c_int),
             lambda o, n, t: C.addressof(widgets[n.decode()]) if n.decode() in widgets else None)
        bind(0xb8, C.CFUNCTYPE(C.c_int, C.c_void_p), lambda o: visible.get(o, 0))
        bind(0xa8, C.CFUNCTYPE(C.c_void_p, C.c_void_p),
             lambda o: C.addressof(names[o]) if names.get(o) is not None else None)

        class Rect(C.Structure):
            _fields_ = [(n, C.c_double) for n in ['x', 'y', 'w', 'h']]

        def move(o, rect):
            moves.append(o)
            C.memmove(o + 0x50, rect, C.sizeof(Rect))

        bind(0x1a8, C.CFUNCTYPE(None, C.c_void_p, C.POINTER(Rect)), move)
        dispatcher = C.CFUNCTYPE(C.c_int, C.c_char_p)(lambda cmd: events.append(cmd) or 1)
        callbacks.append(dispatcher)
        stub = b'\x48\xb8' + struct.pack('<Q', C.cast(dispatcher, C.c_void_p).value) + b'\xff\xe0'
        C.memmove(mem + 0x628270, stub, len(stub))

        def put(name, values):
            r = Rect(*values)
            C.memmove(C.addressof(widgets[name]) + 0x50, C.byref(r), C.sizeof(r))

        def get(name):
            r = Rect.from_buffer(widgets[name], 0x50)
            return r.x, r.y, r.w, r.h

        def layout():
            check(layout_hook(dialog_address, 0, 0, 0) == dialog_address, 'Actual callback returns through layout hook')

        # Actual native HUD frames: Market=(64,53) or (77,35).
        for scale in [.75, 1, 1.5, 2]:
            C.c_double.from_address(mem + 0x1378ec8).value = scale
            for style, anchor, mx, my in [(1, (87, 29, 64, 64), 64, 53), (2, (100, 11, 64, 64), 77, 35)]:
                for variant in ['item_shop', 'item_shop_gf', 'item_ingame_web_shop']:
                    visible.clear()
                    visible[C.addressof(widgets[variant])] = 1
                    put('item_shop_container', [v * scale for v in anchor])
                    for shop in ['item_shop', 'item_shop_gf', 'item_ingame_web_shop']:
                        put(shop, [v * scale for v in ((17, 24, 30, 35) if shop == variant else (501, 501, 30, 35))])
                    for name in all_names[:4]:
                        put(name, (-1, -1, -1, -1))
                    layout()
                    label = f'style{style}/scale{scale}/{variant}'
                    check(get('central_market_button') == (mx * scale, my * scale, 34 * scale, 36 * scale), label + '/Market')
                    for name, offset in [('season_pass_button', 0), ('companion_menu_button', -40), ('journey_menu_button', 40)]:
                        check(get(name) == ((mx + offset) * scale, (my - 34) * scale, 34 * scale, 32 * scale), label + '/' + name)
                        check(get(name)[1] >= 0 and get(name)[1] + get(name)[3] + 2 * scale == my * scale, label + '/' + name + '/row-gap')
                    count = len(moves)
                    layout()
                    check(len(moves) == count, label + '/idempotent')
        expected = {'central_market_button': b'/privatewarehouse', 'season_pass_button': b'/seasonpass',
                    'companion_menu_button': b'/companions', 'journey_menu_button': b'/journey',
                    'item_shop': b'/privatecashshop', 'item_shop_gf': b'/privatecashshop',
                    'item_ingame_web_shop': b'/privatecashshop'}
        for name, command in expected.items():
            event = C.c_void_p(C.addressof(widgets[name]))
            before = len(events)
            check(click_hook(dialog_address, C.addressof(event), 0, 0) == 1 and len(events) == before + 1 and events[-1] == command,
                  name + '/actual hook and command dispatch')
        before = len(events)
        event = C.c_void_p(C.addressof(widgets['fly_gauge']))
        check(click_hook(dialog_address, C.addressof(event), 0, 0) == 42 and len(events) == before, 'Unknown click actual hook passthrough')
        check(native.AionMarketCommand(None, None) == 0, 'Null event ignored')
        event = C.c_void_p()
        check(native.AionMarketCommand(None, C.byref(event)) == 0, 'Null event button ignored')
        unnamed = C.addressof(widgets['fly_gauge'])
        names[unnamed] = None
        event = C.c_void_p(unnamed)
        check(native.AionMarketCommand(None, C.byref(event)) == 0 and len(events) == before, 'Null name ignored')
        count = len(moves)
        native.AionMarketLayout(None)
        check(len(moves) == count, 'Null dialog is a harmless no-op')
        for value in [0, -1, 4.1, float('nan'), float('inf')]:
            C.c_double.from_address(mem + 0x1378ec8).value = value
            count = len(moves)
            native.AionMarketLayout(dialog_address)
            check(len(moves) == count, f'Invalid scale {value} does not move widgets')
        C.c_double.from_address(mem + 0x1378ec8).value = 1
        # Each absent shortcut must leave other present shortcuts supported.
        for missing in ['season_pass_button', 'companion_menu_button', 'journey_menu_button']:
            saved = widgets.pop(missing)
            for name in all_names[:4]:
                if name in widgets:
                    put(name, (-1, -1, -1, -1))
            layout()
            check(all(get(name)[0] >= 0 for name in all_names[:4] if name in widgets), 'Missing ' + missing + '/other shortcuts work')
            widgets[missing] = saved
        saved = {name: widgets.pop(name) for name in ['companion_menu_button', 'journey_menu_button']}
        put('season_pass_button', (-1, -1, -1, -1))
        layout()
        check(get('season_pass_button')[0] >= 0, 'Older HUD without either new button keeps Season Pass')
        widgets.update(saved)
        # Missing Market/container/all Shop anchors must be a harmless no-op.
        for missing in [['central_market_button'], ['item_shop_container'], ['item_shop', 'item_shop_gf', 'item_ingame_web_shop']]:
            saved = {name: widgets.pop(name) for name in missing}
            count = len(moves)
            layout()
            check(len(moves) == count, 'Missing required anchor ' + ','.join(missing) + '/no-op')
            widgets.update(saved)
        visible.clear()
        put('item_ingame_web_shop', (17, 24, 30, 35))
        put('item_shop_container', (100, 11, 64, 64))
        layout()
        check(get('central_market_button') == (77, 35, 34, 36), 'Hidden Shop variants preserve existing web Shop fallback')
        for name, frame in [('item_ingame_web_shop', (17, 24, 30, 35)),
                            ('item_shop_gf', (117, 124, 30, 35)), ('item_shop', (217, 224, 30, 35))]:
            put(name, frame)
            visible[C.addressof(widgets[name])] = 1
        layout()
        check(get('central_market_button') == (77, 35, 34, 36), 'Existing visible Shop precedence: web before GF before stock')
        visible[C.addressof(widgets['item_ingame_web_shop'])] = 0
        layout()
        check(get('central_market_button') == (177, 135, 34, 36), 'Existing visible Shop precedence: GF before stock')
        check(sha(game_path) == original_game_sha, 'Installed Game.dll unchanged')
        check(sha(SOURCE) == source_sha, 'Compiled source unchanged through verification')
        evidence = {'status': 'PASS', 'checks': len(checks), 'assertions': checks,
                    'source': str(SOURCE), 'sourceSha256': source_sha,
                    'fixture': str(Path(__file__).resolve()), 'fixtureSha256': sha(Path(__file__).resolve()),
                    'productDll': str(production_dll), 'productDllSha256': sha(production_dll),
                    'testDll': str(test_dll), 'testDllSha256': sha(test_dll),
                    'gameDll': str(game_path), 'gameDllSha256': original_game_sha,
                    'hookManifest': str(manifest_path), 'hookManifestSha256': sha(manifest_path),
                    'hooks': hooks, 'commands': [v.decode() for v in events],
                    'boundary': 'Isolated compiled callbacks and installed machine-code hooks; no client attach/start or installation. In-game acceptance remains user testing.'}
        (out / 'evidence.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
        print(f'PASS: {len(checks)} native hook/callback checks; actual source-built DLL; both HUD styles/four scales; missing controls and original commands; Game.dll unchanged.')
    finally:
        assert kernel.VirtualFree(mem, 0, 0x8000), C.get_last_error()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, default=DEFAULT_CLIENT)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    parser.add_argument('--manifest', type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = args.manifest or sorted((args.client / 'MarketShortcut-backups').glob('*/manifest.json'))[-1]
    verify(args.client.resolve(), args.out.resolve(), manifest.resolve())


if __name__ == '__main__':
    main()
