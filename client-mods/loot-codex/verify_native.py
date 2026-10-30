"""Build-time ABI checks for the native map and image operations; never attaches to Aion."""
import hashlib
import struct


def verify(client):
    data = (client / 'bin64/Game.dll').read_bytes()
    pe = struct.unpack_from('<I', data, 0x3c)[0]
    if data[pe:pe+6] != b'PE\0\0\x64\x86': raise ValueError('Codex requires the supported x64 client')
    count = struct.unpack_from('<H', data, pe+6)[0]
    table = pe+24+struct.unpack_from('<H', data, pe+20)[0]
    sections = [struct.unpack_from('<IIII', data, table+i*40+8) for i in range(count)]

    def read(rva, length):
        for virtual_size, base, raw_size, raw in sections:
            if base <= rva and rva+length <= base+min(virtual_size, raw_size):
                return data[raw+rva-base:raw+rva-base+length]
        raise ValueError('Unmapped Codex ABI RVA')

    checks = {
        0x4dc7b0: '488bc44881eca80000004885c9488970',
        0x61c390: '4883ec38488bc2488b511048897c2458',
        # Native AddonStaticImage uses double zero offsets, then the existing texture flags.
        0x61c3ac: '660f57d24883c250488bc8f20f10da',
        # Exact-type image and browser lookup used by Codex visuals.
        0x54a2e0: 'b821200000c3',
        0x4dd7c0: 'b812200000c3',
        # Native browser parser transport and tooltip-data setter. The setter
        # copies the native string and payload; Codex never constructs that ABI.
        0x5ad590: '4883ec2848895c2440c7812002000000',
        0x4de010: '488bc44883ec7883b940030000004889',
        0x12e7e0: '4c8bdc4881ecc8000000c74424200e00',
        # The original item-tooltip URI parser; native CRT string operations
        # are called through the same game-owned slots as this function.
        0x131820: '488bc44883ec7848896810488be94889',
        0x7d47a0: '4883ec188b9170030000',
        0x7d65e0: '488bc44881ec58030000',
        0x7d5ba0: '488bc44883ec784883b9f002000000',
        0x7d7090: '488bc44881ecb800000085d2448b9170030000',
    }
    for rva, value in checks.items():
        expected = bytes.fromhex(value)
        if read(rva, len(expected)) != expected: raise ValueError(f'Codex native contract differs at {rva:#x}')
    for slot, function in [(0xccfb88+0x490, 0x7ef580), (0xce9ce8+0x490, 0x4ae9d0)]:
        if read(slot, 8) != struct.pack('<Q', 0x10000000+function): raise ValueError('Codex command-slot contract differs')
    return {'gameSha256': hashlib.sha256(data).hexdigest(), 'checkedSpans': len(checks)+2}
