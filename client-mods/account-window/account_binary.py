"""Version-checked Game.dll edits for the Aetherfall Account window; disk preparation only.

1. Login route: /market/account joins the Market authentication route table (existing cave) and the
   browser hook, whose last route miss now tries the account route in a new bridge before the
   publisher path.
2. Profile button: the Stigma profile click bridge's fallthrough now first compares the clicked
   button with btn_account_window and runs the /account slash command (the same native executor the
   HUD shortcuts use) before the original Help comparison.
"""
import struct
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
sys.path.insert(0, str(HERE.parent / 'stigma-profile'))
import patch_game_dll as hooks
import patch_binary as stigma
from patch_game_dll import Assembler, emit_exact_route

ORIGIN = 'http://127.0.0.1:8091/'
ROUTES = [ORIGIN + s for s in ['shop', 'market', 'market/wardrobe', 'journey', 'market/pass', 'market/companions']]
ACCOUNT_URL = ORIGIN + 'market/account'
BUTTON = 'btn_account_window'
COMMAND = '/account'
BROWSER_EXT, CLICK_EXT, EXT_END = 0x144e800, 0x144e880, 0x144e900
STRCMP_IMPORT, EXECUTE_COMMAND = 0xb7fd28, hooks.ORIGINAL_EXEC_RVA
HELP_NAME, HELP_RESUME = 0xcccd50, 0x83d52a


def _labels(build):
    """Labels of a maintained builder's assembly (the code itself is compared separately)."""
    seen = []
    finish = Assembler.finish

    def capture(self):
        seen.append(dict(self.labels))
        return finish(self)
    Assembler.finish = capture
    try:
        code = build()
    finally:
        Assembler.finish = finish
    return code, seen[-1]


def browser_ext(authenticated, original):
    a = Assembler(BROWSER_EXT)
    emit_exact_route(a, 'rdx', 'account', 'miss')
    a.relative(b'\xe9', authenticated)
    a.label('miss')
    a.relative(b'\xe9', original)
    a.label('account')
    a.emit(ACCOUNT_URL.encode('ascii') + b'\0')
    code = a.finish()
    if len(code) > CLICK_EXT - BROWSER_EXT:
        raise ValueError('Account route bridge exceeds its region')
    return code


def click_ext():
    a = Assembler(CLICK_EXT)
    # Same frame as the Stigma bridge: the clicked name is saved at [rsp+0x20].
    a.emit(bytes.fromhex('488b4c2420'))
    a.branch(bytes.fromhex('488d15'), 'button')
    a.relative(bytes.fromhex('ff15'), STRCMP_IMPORT)
    a.emit(bytes.fromhex('85c0'))
    a.branch(bytes.fromhex('0f85'), 'original')
    a.branch(bytes.fromhex('488d0d'), 'command')
    a.relative(b'\xe8', EXECUTE_COMMAND)
    a.emit(bytes.fromhex('b8010000004883c428c3'))
    a.label('original')
    a.emit(bytes.fromhex('488b442420'))
    a.relative(bytes.fromhex('488d15'), HELP_NAME)
    a.relative(b'\xe9', HELP_RESUME)
    a.label('button')
    a.emit(BUTTON.encode() + b'\0')
    a.label('command')
    a.emit(COMMAND.encode() + b'\0')
    code = a.finish()
    if len(code) > EXT_END - CLICK_EXT:
        raise ValueError('Account click bridge exceeds its region')
    return code


def patch(source):
    data = bytearray(source)
    browser, labels = _labels(lambda: hooks.build_browser_hook_code(ROUTES, authenticate_shop=True))
    auth_old = hooks.build_market_auth_code(ROUTES, compact=True, route_table=True)
    auth_new = hooks.build_market_auth_code(ROUTES + [ACCOUNT_URL], compact=True, route_table=True)
    click, click_labels = _labels(stigma.click_code)
    auth = (hooks.MARKET_AUTH_HOOK_RVA, hooks.MARKET_RECT_HOOK_RVA)
    checks = [(auth[0], auth_old.ljust(auth[1] - auth[0], b'\0'), 'Market authentication routes'),
              (hooks.BROWSER_HOOK_RVA, browser, 'browser routes'),
              (stigma.CLICK_CAVE, click, 'Stigma profile click bridge'),
              (BROWSER_EXT, bytes(EXT_END - BROWSER_EXT), 'unused account bridge region')]
    for rva, expected, what in checks:
        if bytes(data[rva:rva + len(expected)]) != expected:
            raise ValueError('Unexpected Game.dll ' + what + '; review the newer client first')
    if len(auth_new) > auth[1] - auth[0]:
        raise ValueError('Account route exceeds the authentication cave')
    data[auth[0]:auth[1]] = auth_new.ljust(auth[1] - auth[0], b'\0')
    # The last route miss: e9 <original> directly before journey_load.
    miss = hooks.BROWSER_HOOK_RVA + labels['journey_load'] - 5
    if data[miss] != 0xe9 or struct.unpack_from('<i', data, miss + 1)[0] != labels['original'] - (labels['journey_load']):
        raise ValueError('Browser route fallthrough differs')
    struct.pack_into('<i', data, miss + 1, BROWSER_EXT - miss - 5)
    ext = browser_ext(hooks.BROWSER_HOOK_RVA + labels['authenticated_load'], hooks.BROWSER_HOOK_RVA + labels['original'])
    data[BROWSER_EXT:BROWSER_EXT + len(ext)] = ext
    # Stigma fallthrough: replace its 5-byte "mov rax,[rsp+0x20]" with a jump to the account bridge.
    site = stigma.CLICK_CAVE + click_labels['original']
    if bytes(data[site:site + 5]) != bytes.fromhex('488b442420'):
        raise ValueError('Stigma fallthrough differs')
    data[site:site + 5] = b'\xe9' + struct.pack('<i', CLICK_EXT - site - 5)
    ext = click_ext()
    data[CLICK_EXT:CLICK_EXT + len(ext)] = ext
    return bytes(data)
