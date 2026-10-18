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
BUTTON = 'btn_stigma_upgrade'
CLICK_CAVE, SLOT_CAVE, CAVE_END = 0x144daa0, 0x144db30, 0x144dc00
# Each branch tests only the native NPC Stigma-edit flag. Ownership, item type,
# class, unlocked slots, prices and confirmation paths remain downstream.
GATES = [(0x452483, '741a', 'inventory double-click'),
         (0x570a83, '0f8445010000', 'Stigma item action'),
         (0x83421b, '7430', 'profile drag admission'),
         (0x83429e, '0f84c1000000', 'profile drop dispatch'),
         (0x83d576, '7417', 'profile item click')]


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
    if len(code) > CAVE_END - SLOT_CAVE:
        raise ValueError('Stigma slot bridge exceeds its reserved region')
    return code


def patch(source, expected=EXPECTED):
    source_hash = hashlib.sha256(source).hexdigest()
    if source_hash == INSTALLED_STIGMA_BUILD:
        return source
    if source_hash not in (expected, INITIAL_STIGMA_BUILD):
        raise ValueError('Unexpected Game.dll; inspect later changes before preparing Stigma access')
    data = bytearray(source)
    def replace(rva, before, after):
        pos = offset(data, rva)
        if data[pos:pos + len(before)] != before or len(before) != len(after):
            raise ValueError('Native Stigma guard differs at ' + hex(rva))
        data[pos:pos + len(before)] = after
    if source_hash != INITIAL_STIGMA_BUILD:
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
    for site, kind in [(0x83d2e6, 0x47), (0x83d395, 0x48), (0x83d449, 0x49)]:
        replace(site, struct.pack('<I', 0x4a), struct.pack('<I', kind))
    return bytes(data)
