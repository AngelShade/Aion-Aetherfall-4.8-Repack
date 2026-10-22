"""Append an AetherfallCore.dll import to Game.dll; no native code is changed.

The bridge registers its Lua function from DllMain by renaming the duplicate
luaL_Reg row in memory, so the import is the only file change.
"""
import struct
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'speech-bubbles'))
from loader_permissions import writable_iats

MODULE = b'AetherfallCore.dll'
EXPORT = b'AetherfallCoreStatus'
# The duplicate Lua row the bridge renames at load; verified in every patched image.
LUA_ROWS = 0xbad1d0
LUA_FN = 0x606ed0


def align(v, n):
    return (v + n - 1) // n * n


def layout(data):
    pe = struct.unpack_from('<I', data, 60)[0]
    opt = pe + 24
    count = struct.unpack_from('<H', data, pe + 6)[0]
    table = opt + struct.unpack_from('<H', data, pe + 20)[0]
    return pe, opt, count, table, [struct.unpack_from('<8sIIIIIIHHI', data, table + i * 40) for i in range(count)]


def offset(sections, rva):
    for s in sections:
        if s[2] <= rva < s[2] + s[3]:
            return s[4] + rva - s[2]
    raise ValueError('Unmapped RVA ' + hex(rva))


def check_lua_rows(data, sections, base):
    first, second = (struct.unpack_from('<QQ', data, offset(sections, LUA_ROWS + 16 * i)) for i in range(2))
    name = data[offset(sections, first[0] - base):].split(b'\0', 1)[0]
    if first != second or first[1] - base != LUA_FN or name != b'UnregisterPredefinedQuestIndicator':
        raise ValueError('Unexpected native Lua registration table')


def patch(source):
    data = bytearray(source)
    pe, opt, count, table, sections = layout(data)
    if MODULE + b'\0' in data:
        raise ValueError('AetherfallCore already imported')
    base = struct.unpack_from('<Q', data, opt + 24)[0]
    if base != 0x10000000 or struct.unpack_from('<I', data, opt + 152 + 4)[0] > 8:
        raise ValueError('Unexpected Game.dll base/relocations')
    check_lua_rows(data, sections, base)
    # Extend only the final mapped section (the cumulative client has used its
    # spare PE headers); preserve every existing byte and RVA.
    last = max(sections, key=lambda s: s[2])
    if last[4] + last[3] != len(data) or not last[-1] & 0x20000000:
        raise ValueError('Unsupported final PE section')
    sa, fa = struct.unpack_from('<II', data, opt + 32)
    extension = align(max(last[1], last[3]), 16)
    rva = last[2] + extension
    raw = last[4] + extension
    imports = bytearray()
    c = offset(sections, struct.unpack_from('<I', data, opt + 120)[0])
    while any(data[c:c + 20]):
        imports += data[c:c + 20]
        c += 20
    payload = bytearray(imports + bytes(40))
    descriptor = len(imports)

    def append(b, boundary=1):
        payload.extend(bytes(align(len(payload), boundary) - len(payload)))
        address = rva + len(payload)
        payload.extend(b)
        return address

    module = append(MODULE + b'\0')
    name = append(b'\0\0' + EXPORT + b'\0', 2)
    lookup = append(struct.pack('<2Q', name, 0), 8)
    iat = append(struct.pack('<2Q', name, 0), 8)
    struct.pack_into('<IIIII', payload, descriptor, lookup, 0, 0, module, iat)
    vs = extension + len(payload)
    rs = align(vs, fa)
    payload.extend(bytes(rs - extension - len(payload)))
    data.extend(bytes(raw - len(data)))
    data.extend(payload)
    header = table + sections.index(last) * 40
    struct.pack_into('<I', data, header + 8, vs)
    struct.pack_into('<I', data, header + 16, rs)
    struct.pack_into('<I', data, opt + 56, align(last[2] + vs, sa))
    struct.pack_into('<I', data, opt + 4, struct.unpack_from('<I', data, opt + 4)[0] + rs - last[3])
    struct.pack_into('<I', data, opt + 64, 0)  # checksum, as the other bridges leave it
    struct.pack_into('<II', data, opt + 120, rva, len(imports) + 40)
    patched = writable_iats(bytes(data))[0]
    # Every original byte is preserved; only headers and the appended tail differ.
    first_section = min(s[4] for s in sections)
    if patched[first_section:len(source)] != source[first_section:]:
        raise ValueError('Original section bytes changed')
    return patched, dict(importDirectory=hex(rva), module=MODULE.decode(), export=EXPORT.decode(), iat=hex(iat))
