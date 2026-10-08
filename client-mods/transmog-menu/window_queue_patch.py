"""Bounded incremental repair for RelicCalc's native style-command queue.

This stages bytes only. No game process is opened or native function called.
Successful SetWidgetAttr/SetWidgetUIImage dispatch currently falls through to
the stock zero return, ending the queue drain after every style operation.
Continue at most 63 such operations before retaining one stock yield.
"""
import hashlib
import struct
from patch_game_dll import Assembler, ORIGINAL_SHA256

SITE = 0x60ffa5
EPILOGUE = 0x6103c2
ORIGINAL = bytes.fromhex('33c0e916040000')
BATCH = 64
HOOK_RVA = 0x144e700
COUNTER_RVA = 0x144e7f8


def code(rva, counter):
    a = Assembler(rva)
    a.emit(bytes.fromhex('833b12'))  # only SetWidgetAttr / SetWidgetUIImage
    a.branch(b'\x0f\x84', 'addon')
    a.emit(bytes.fromhex('833b15'))
    a.branch(b'\x0f\x85', 'stock')
    a.label('addon')
    a.emit(bytes.fromhex('488b43104885c0'))
    a.branch(b'\x0f\x84', 'stock')
    a.emit(bytes.fromhex('488b53184829c24883fa50'))
    a.branch(b'\x0f\x82', 'stock')
    # Exact nine-character inline addon name; no prefix/suffix or heap read.
    a.emit(bytes.fromhex('4883781809'))
    a.branch(b'\x0f\x85', 'stock')
    a.emit(bytes.fromhex('488378200f'))
    a.branch(b'\x0f\x85', 'stock')
    a.emit(b'\x49\xba' + b'RelicCal')
    a.emit(bytes.fromhex('4c395008'))
    a.branch(b'\x0f\x85', 'stock')
    a.emit(bytes.fromhex('80781063'))
    a.branch(b'\x0f\x85', 'stock')
    a.relative(b'\xff\x05', counter)
    a.relative(b'\x8b\x05', counter)
    a.emit(bytes.fromhex('83e03f0f95c00fb6c0'))
    a.relative(b'\xe9', EPILOGUE)
    a.label('stock')
    a.emit(bytes.fromhex('33c0'))
    a.relative(b'\xe9', EPILOGUE)
    return a.finish()


def patch(source, original):
    if hashlib.sha256(original).hexdigest() != ORIGINAL_SHA256:
        raise ValueError('Unsupported original client')
    data = bytearray(source)
    pe = struct.unpack_from('<I', data, 60)[0]
    optional = pe + 24
    table = optional + struct.unpack_from('<H', data, pe + 20)[0]
    count = struct.unpack_from('<H', data, pe + 6)[0]
    sections = [struct.unpack_from('<8sIIIIIIHHI', data, table + i*40) for i in range(count)]
    def offset(rva):
        return next(s[4]+rva-s[2] for s in sections if s[2] <= rva < s[2]+s[3])
    # Require the entire stock dispatcher, including all native setters and
    # failure paths. Missing-widget early returns remain byte-identical.
    begin, end = 0x60daf0, 0x61040c
    if data[offset(begin):offset(end)] != original[begin:end]:
        raise ValueError('Native UI dispatcher changed; re-review required')
    if data[offset(SITE):offset(SITE)+7] != ORIGINAL:
        raise ValueError('Unexpected native yield tail')
    section = next(s for s in sections if s[2] <= HOOK_RVA < s[2]+s[3])
    if section[9] & 0xe0000000 != 0xe0000000:
        raise ValueError('Hook storage must be readable, writable and executable')
    # Existing main-section padding is present in both graphics/cursor restore
    # baselines. Appended login-section padding is absent from those baselines.
    rva, counter = HOOK_RVA, COUNTER_RVA
    hook = code(rva, counter)
    if rva + len(hook) > counter:
        raise ValueError('Insufficient unused section padding')
    if any(data[offset(rva):offset(counter)+4]) or any(original[rva:counter+4]):
        raise ValueError('Section padding already belongs to another modification')
    data[offset(rva):offset(rva)+len(hook)] = hook
    data[offset(SITE):offset(SITE)+7] = b'\xe9'+struct.pack('<i', rva-SITE-5)+b'\x90\x90'
    return bytes(data), dict(site=SITE, hookRva=rva, counterRva=counter,
                            batch=BATCH, hookBytes=len(hook))
