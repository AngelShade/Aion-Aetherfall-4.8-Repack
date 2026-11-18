"""Version-checked Aion 4.8 NA Stigma profile access; disk preparation only."""
import hashlib
import struct
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
from patch_game_dll import Assembler

EXPECTED = '01784637e17ce712f6f64b6d56b1b5565cf06619f3bbbde8d83086d46393a5e8'
INITIAL_STIGMA_BUILD = '7225a21491cd4e5a5c8ce8e80ca786e7452eb347acc6defe77df7fda9fb74b7f'
INSTALLED_STIGMA_BUILD = 'b3ad20db678e7a3c35bfc9731d08915012e225c42cae3d9c1be8ef623a0ffaff'
CURRENT_STIGMA_BUILD = '1820bb008928b7e8ba9538eb86c242420e02a02411b1f5d223da76f6c37f5913'
BUTTON = 'btn_stigma_upgrade'
CLICK_CAVE, SLOT_CAVE, CAVE_END = 0x144daa0, 0x144db30, 0x144dc00
# The existing slot bridge ends at 0x144db85. This aligned tail belongs to
# Stigma; keep all other native mods and the remaining reserved bytes intact.
LOCKED_VISUAL_CAVE = 0x144db90
LOCKED_VISUAL_SITE, LOCKED_VISUAL_RESUME = 0x83ca21, 0x83ca35
LOCKED_VISUAL_ORIGINAL = bytes.fromhex(
    '498b4d004533c0488b01418d5003ff90d8000000'
    '4d8b5d0041c7838c04000001000000')
LOCKED_VISUAL_BASELINES = frozenset({
    '9efbb3b280ac6b1e9be9cd3b9047fe3f99d4e1156f845dd12ca9a6c7a33cce84',
})
# Each branch tests only the native NPC Stigma-edit flag. Ownership, item type,
# class, unlocked slots, prices and confirmation paths remain downstream.
GATES = [(0x452483, '741a', 'inventory double-click'),
         (0x570a83, '0f8445010000', 'Stigma item action'),
         (0x83421b, '7430', 'profile drag admission'),
         (0x83429e, '0f84c1000000', 'profile drop dispatch'),
         (0x83d576, '7417', 'profile item click')]
# These native lifecycle paths run after slot construction. They used to change
# all six slot kinds back to the display-only kind (0x4a), silently disabling
# both inventory actions and drops. Retain each constructor's interactive kind;
# native enabled/locked flags, slot indices and item validation remain intact.
LIFECYCLE_RESETS = [(0x53a80, 'NPC service close'),
                    (0x53c08, 'secondary NPC service close'),
                    (0x832f2c, 'Profile reset/init')]
VIEW_ONLY_STORE = bytes.fromhex('c781880300004a000000')


def offset(data, rva):
    pe = struct.unpack_from('<I', data, 60)[0]
    table = pe + 24 + struct.unpack_from('<H', data, pe + 20)[0]
    for i in range(struct.unpack_from('<H', data, pe + 6)[0]):
        section = struct.unpack_from('<8sIIIIIIHHI', data, table + i * 40)
        if section[2] <= rva < section[2] + section[3]:
            return section[4] + rva - section[2]
    raise ValueError('Unmapped native RVA ' + hex(rva))


def click_code():
    a = Assembler(CLICK_CAVE)
    # Enter after native GetName, within its existing aligned 0x28 stack frame.
    a.emit(bytes.fromhex('4889442420488bc8'))
    a.branch(bytes.fromhex('488d15'), 'button')
    a.relative(bytes.fromhex('ff15'), 0xb7fd28)  # existing strcmp import
    a.emit(bytes.fromhex('85c0'))
    a.branch(bytes.fromhex('0f85'), 'original')
    a.relative(bytes.fromhex('488d0d'), 0x130c5d0)  # native UI manager
    a.emit(bytes.fromhex('baaf0100004533c04533c9'))
    # Native local dialog opener, dialog 431 = dlg_enchant_stigma, no NPC target.
    a.relative(b'\xe8', 0x176550)
    a.emit(bytes.fromhex('b8010000004883c428c3'))
    a.label('original')
    a.emit(bytes.fromhex('488b442420'))
    a.relative(bytes.fromhex('488d15'), 0xcccd50)  # original Help button name
    a.relative(b'\xe9', 0x83d52a)
    a.label('button')
    a.emit(BUTTON.encode() + b'\0')
    code = a.finish()
    if len(code) > SLOT_CAVE - CLICK_CAVE:
        raise ValueError('Stigma click bridge exceeds its reserved region')
    return code


def slot_code():
    a = Assembler(SLOT_CAVE)
    a.emit(bytes.fromhex('488d83900600004533c9'))
    a.label('slot')
    a.emit(bytes.fromhex('488b084885c9'))
    a.branch(bytes.fromhex('0f84'), 'next')
    a.emit(bytes.fromhex('ba470000004183f903'))
    a.branch(bytes.fromhex('0f82'), 'store')
    a.emit(bytes.fromhex('ba480000004183f905'))
    a.branch(bytes.fromhex('0f82'), 'store')
    a.emit(bytes.fromhex('ba49000000'))
    a.label('store')
    a.emit(bytes.fromhex('899188030000'))
    a.label('next')
    a.emit(bytes.fromhex('4883c00841ffc14183f906'))
    a.branch(bytes.fromhex('0f82'), 'slot')
    a.relative(b'\xe9', 0x8349fd)
    code = a.finish()
    if len(code) > LOCKED_VISUAL_CAVE - SLOT_CAVE:
        raise ValueError('Stigma slot bridge exceeds its reserved region')
    return code


def patch(source, expected=EXPECTED):
    # Normalize only this repair's exact instructions for hash admission. This
    # accepts an already-prepared output without allowing unrelated DLL changes.
    normalized = bytearray(source)
    for rva, _ in LIFECYCLE_RESETS:
        pos = offset(normalized, rva)
        current = normalized[pos:pos + len(VIEW_ONLY_STORE)]
        if current == b'\x90' * len(VIEW_ONLY_STORE):
            normalized[pos:pos + len(VIEW_ONLY_STORE)] = VIEW_ONLY_STORE
        elif current != VIEW_ONLY_STORE:
            raise ValueError('Native Stigma lifecycle store differs at ' + hex(rva))
    source_hash = hashlib.sha256(normalized).hexdigest()
    installed = source_hash in (INSTALLED_STIGMA_BUILD, CURRENT_STIGMA_BUILD)
    if source_hash not in (expected, INITIAL_STIGMA_BUILD, INSTALLED_STIGMA_BUILD, CURRENT_STIGMA_BUILD):
        raise ValueError('Unexpected Game.dll; inspect later changes before preparing Stigma access')
    data = bytearray(source)
    def replace(rva, before, after):
        pos = offset(data, rva)
        if data[pos:pos + len(before)] != before or len(before) != len(after):
            raise ValueError('Native Stigma guard differs at ' + hex(rva))
        data[pos:pos + len(before)] = after
    if not installed and source_hash != INITIAL_STIGMA_BUILD:
        for rva, original, _ in GATES:
            before = bytes.fromhex(original)
            replace(rva, before, b'\x90' * len(before))
        for site, original, cave, code in [
            (0x83d523, '488d1526f84800', CLICK_CAVE, click_code()),
            (0x8349d5, '488d8390060000', SLOT_CAVE, slot_code())]:
            before = bytes.fromhex(original)
            replace(site, before, b'\xe9' + struct.pack('<i', cave - site - 5) + b'\x90' * (len(before) - 5))
            replace(cave, bytes(len(code)), code)
    # Initial construction also needs interactive slot kinds: the NPC mode
    # setter is not guaranteed to run before a player first opens Profile.
    if not installed:
        for site, kind in [(0x83d2e6, 0x47), (0x83d395, 0x48), (0x83d449, 0x49)]:
            replace(site, struct.pack('<I', 0x4a), struct.pack('<I', kind))
    for rva, _ in LIFECYCLE_RESETS:
        pos = offset(data, rva)
        if data[pos:pos + len(VIEW_ONLY_STORE)] == VIEW_ONLY_STORE:
            replace(rva, VIEW_ONLY_STORE, b'\x90' * len(VIEW_ONLY_STORE))
    if installed:
        verify_incremental_preservation(source, data)
    verify_access(data)
    return bytes(data)


def verify_incremental_preservation(source, result):
    """Keep every later mod byte outside the three Stigma stores unchanged."""
    if len(source) != len(result):
        raise ValueError('Incremental Stigma repair changed the DLL size')
    previous = 0
    for pos in sorted(offset(source, rva) for rva, _ in LIFECYCLE_RESETS):
        if source[previous:pos] != result[previous:pos]:
            raise ValueError('Incremental Stigma repair changed another native feature')
        previous = pos + len(VIEW_ONLY_STORE)
    if source[previous:] != result[previous:]:
        raise ValueError('Incremental Stigma repair changed another native feature')


def verify_access(data):
    """Reject prepared images that can return Stigma slots to display-only mode."""
    expected = [(rva, b'\x90' * len(bytes.fromhex(before))) for rva, before, _ in GATES]
    expected += [(site, struct.pack('<I', kind)) for site, kind in
                 [(0x83d2e6, 0x47), (0x83d395, 0x48), (0x83d449, 0x49)]]
    expected += [(rva, b'\x90' * len(VIEW_ONLY_STORE)) for rva, _ in LIFECYCLE_RESETS]
    expected += [(0x8349d5, b'\xe9' + struct.pack('<i', SLOT_CAVE - 0x8349d5 - 5) + b'\x90\x90'),
                 (SLOT_CAVE, slot_code())]
    for rva, wanted in expected:
        pos = offset(data, rva)
        if data[pos:pos + len(wanted)] != wanted:
            raise ValueError('Incomplete native Stigma access at ' + hex(rva))



def locked_visual_code():
    """Keep unavailable Profile slots visible, disabled and natively locked."""
    a = Assembler(LOCKED_VISUAL_CAVE)
    # This branch already failed the native quest/equipped-item and level test.
    # Constructor/reset state may have cleared visibility, so clearing only the
    # enabled bit would leave the locked slot invisible on its first opening.
    # Native +0xd0 sets flag bits; +0xd8 clears them. 1=visible, 2=enabled.
    for method, mask in [(0xd0, 1), (0xd8, 2)]:
        a.emit(bytes.fromhex('498b4d004533c0488b01'))  # slot, no child recursion
        a.emit(b'\xba' + struct.pack('<I', mask))
        a.emit(bytes.fromhex('ff90') + struct.pack('<I', method))
    # Rejoin the untouched write of slot+0x48c=1 and the original loop. The
    # eligible branch, item kinds/indices and every item admission stay native.
    a.relative(b'\xe9', LOCKED_VISUAL_RESUME)
    code = a.finish()
    if len(code) > CAVE_END - LOCKED_VISUAL_CAVE:
        raise ValueError('Stigma locked-slot bridge exceeds its reserved region')
    return code


def _locked_visual_window():
    return (b'\xe9' + struct.pack('<i', LOCKED_VISUAL_CAVE - LOCKED_VISUAL_SITE - 5)
            + b'\x90\x90' + LOCKED_VISUAL_ORIGINAL[7:])


def _locked_visual_state(data):
    """Accept only complete original or complete visual-hook instructions."""
    pos, cave = offset(data, LOCKED_VISUAL_SITE), offset(data, LOCKED_VISUAL_CAVE)
    code = locked_visual_code()
    window = data[pos:pos + len(LOCKED_VISUAL_ORIGINAL)]
    bridge = data[cave:cave + len(code)]
    if window == LOCKED_VISUAL_ORIGINAL and bridge == bytes(len(code)):
        return False
    if window == _locked_visual_window() and bridge == code:
        return True
    raise ValueError('Native Stigma locked-slot instructions differ')


def verify_locked_visuals(data):
    """Verify native access plus visible, disabled locked-slot rendering."""
    verify_access(data)
    if not _locked_visual_state(data):
        raise ValueError('Native Stigma locked-slot visuals are not installed')


def verify_locked_visual_preservation(source, result):
    """The visual hook may change only its seven-byte site and its own cave."""
    if len(source) != len(result):
        raise ValueError('Stigma visual repair changed the DLL size')
    previous = 0
    regions = [(LOCKED_VISUAL_SITE, 7),
               (LOCKED_VISUAL_CAVE, len(locked_visual_code()))]
    for pos, size in sorted((offset(source, rva), size) for rva, size in regions):
        if source[previous:pos] != result[previous:pos]:
            raise ValueError('Stigma visual repair changed another native feature')
        previous = pos + size
    if source[previous:] != result[previous:]:
        raise ValueError('Stigma visual repair changed another native feature')


def patch_locked_visuals(source):
    """Incrementally repair an explicitly supported current client; idempotent.

    The older access builder's hash guard intentionally remains unchanged. This
    mode requires that access repair already installed, the exact untouched
    native locked branch, and a known whole-image baseline after normalizing
    only this visual hook. It cannot admit an unknown later native build.
    """
    verify_access(source)
    installed = _locked_visual_state(source)
    normalized = bytearray(source)
    pos = offset(source, LOCKED_VISUAL_SITE)
    cave = offset(source, LOCKED_VISUAL_CAVE)
    code = locked_visual_code()
    normalized[pos:pos + 7] = LOCKED_VISUAL_ORIGINAL[:7]
    normalized[cave:cave + len(code)] = bytes(len(code))
    if hashlib.sha256(normalized).hexdigest() not in LOCKED_VISUAL_BASELINES:
        raise ValueError('Unexpected Game.dll; review later changes before Stigma visual repair')
    if installed:
        verify_locked_visuals(source)
        return bytes(source)
    data = bytearray(source)
    data[pos:pos + 7] = _locked_visual_window()[:7]
    data[cave:cave + len(code)] = code
    verify_locked_visuals(data)
    verify_locked_visual_preservation(source, data)
    return bytes(data)
