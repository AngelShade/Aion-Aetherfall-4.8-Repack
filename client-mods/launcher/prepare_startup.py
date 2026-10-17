"""Stage the supported client's splash-entry bridge; never modify the live client."""
import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'transmog-menu'))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'speech-bubbles'))
from patch_game_dll import Assembler
from loader_permissions import writable_iats

SUPPORTED_SHA256 = 'cfcccf32c90be6ee381027fe3a96e2e749ab2078208fd0168471ea97a354bbdc'
SITE = 0x1650
ORIGINAL = bytes.fromhex('40535556574154')


def align(value, boundary):
    return (value + boundary - 1) // boundary * boundary


def patch(source):
    if hashlib.sha256(source).hexdigest() != SUPPORTED_SHA256:
        raise ValueError('Unsupported or already modified aion.bin; retain the current file')
    data = bytearray(source)
    pe = struct.unpack_from('<I', data, 60)[0]
    opt = pe + 24
    if data[pe:pe+6] != b'PE\0\0\x64\x86':
        raise ValueError('Expected the supported x64 client')
    count = struct.unpack_from('<H', data, pe+6)[0]
    table = opt + struct.unpack_from('<H', data, pe+20)[0]
    sections = [struct.unpack_from('<8sIIIIIIHHI', data, table+i*40) for i in range(count)]

    def offset(rva):
        for section in sections:
            if section[2] <= rva < section[2]+section[3]:
                return section[4]+rva-section[2]
        raise ValueError('Unmapped RVA ' + hex(rva))

    header = table+count*40
    if header+40 > struct.unpack_from('<I', data, opt+60)[0] or any(data[header:header+40]):
        raise ValueError('No spare PE section header')
    pos = offset(SITE)
    if data[pos:pos+len(ORIGINAL)] != ORIGINAL:
        raise ValueError('Native splash prologue changed')
    # The native routine at 1650 creates the boot bitmap/window, then returns.
    # Its sole direct caller is 58f1. Both texture and class names remain intact.
    if data[offset(0x58f1):offset(0x58f1)+5] != b'\xe8'+struct.pack('<i', SITE-0x58f1-5):
        raise ValueError('Native splash caller changed')
    if data[offset(0x860d0):offset(0x860d0)+18] != b'SplashWindowClass\0':
        raise ValueError('Native splash class changed')
    sa, fa = struct.unpack_from('<II', data, opt+32)
    rva = align(max(s[2]+max(s[1], s[3]) for s in sections), sa)
    raw = align(len(data), fa)
    cursor = offset(struct.unpack_from('<I', data, opt+120)[0])
    imports = bytearray()
    while any(data[cursor:cursor+20]):
        imports += data[cursor:cursor+20]
        cursor += 20
    payload = bytearray(imports+bytes(40))

    def append(value, boundary=1):
        payload.extend(bytes(align(len(payload), boundary)-len(payload)))
        result = rva+len(payload)
        payload.extend(value)
        return result

    module = append(b'AionRememberLogin.dll\0')
    name = append(b'\0\0AionStartupSuppressSplash\0', 2)
    lookup = append(struct.pack('<2Q', name, 0), 8)
    iat = append(struct.pack('<2Q', name, 0), 8)
    struct.pack_into('<5I', payload, len(imports), lookup, 0, 0, module, iat)
    hook = append(b'', 16)
    assembly = Assembler(hook)
    # Preserve native parameters, volatile registers, flags and XMM0..5. The
    # ordinary (non-launcher) path executes the exact displaced prologue.
    assembly.emit(bytes.fromhex('4881ec18010000'))
    assembly.emit(bytes.fromhex('488944242048894c242848895424304c894424384c894c24404c895424484c895c24509c584889442458'))
    for reg in range(6):
        assembly.emit(b'\xf3\x0f\x7f'+bytes([0x84+reg*8, 0x24])+struct.pack('<I', 0x60+reg*16))
    assembly.relative(b'\xff\x15', iat)
    assembly.emit(bytes.fromhex('898424d0000000'))
    for reg in range(6):
        assembly.emit(b'\xf3\x0f\x6f'+bytes([0x84+reg*8, 0x24])+struct.pack('<I', 0x60+reg*16))
    assembly.emit(bytes.fromhex('488b442458509d488b442420488b4c2428488b5424304c8b4424384c8b4c24404c8b5424484c8b5c2450'))
    assembly.emit(bytes.fromhex('83bc24d000000000'))
    assembly.branch(b'\x0f\x84', 'ordinary')
    assembly.emit(bytes.fromhex('4881c41801000031c0c3'))
    assembly.label('ordinary')
    assembly.emit(bytes.fromhex('4881c418010000')+ORIGINAL)
    assembly.relative(b'\xe9', SITE+len(ORIGINAL))
    payload += assembly.finish()
    data[pos:pos+len(ORIGINAL)] = b'\xe9'+struct.pack('<i', hook-SITE-5)+bytes.fromhex('9090')
    virtual = len(payload)
    size = align(virtual, fa)
    payload.extend(bytes(size-virtual))
    data.extend(bytes(raw-len(data)))
    data.extend(payload)
    struct.pack_into('<8sIIIIIIHHI', data, header, b'.afstart', virtual, rva, size, raw, 0, 0, 0, 0, 0xe0000020)
    struct.pack_into('<H', data, pe+6, count+1)
    struct.pack_into('<I', data, opt+56, align(rva+virtual, sa))
    struct.pack_into('<I', data, opt+4, struct.unpack_from('<I', data, opt+4)[0]+size)
    struct.pack_into('<I', data, opt+64, 0)
    struct.pack_into('<II', data, opt+120, rva, len(imports)+40)
    result, _ = writable_iats(bytes(data))
    return result, dict(originalSha256=SUPPORTED_SHA256, installedSha256=hashlib.sha256(result).hexdigest(),
                        site=SITE, original=ORIGINAL.hex(), hook=hook, importIat=iat,
                        ordinaryEntryPointRetained=True, originalImportsRetained=len(imports)//20)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--stage', type=Path, required=True)
    args = parser.parse_args()
    client = args.client.resolve(strict=True)
    stage = args.stage.resolve()
    if stage == client or stage.is_relative_to(client):
        raise ValueError('Use an external staging directory')
    result, info = patch((client / 'bin64/aion.bin').read_bytes())
    destination = stage / 'bin64/aion.bin'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(result)
    (stage / 'startup-bridge.json').write_text(json.dumps(info, indent=2))
    print('Staged native startup bridge:', destination)
