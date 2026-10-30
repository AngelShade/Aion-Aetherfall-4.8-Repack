"""Check the installed camera-variable ABI before building the native bridge."""
import hashlib
import struct


def verify(client):
    inputs = {}

    def image(relative):
        data = (client / relative).read_bytes()
        inputs[relative] = hashlib.sha256(data).hexdigest()
        pe = struct.unpack_from('<I', data, 0x3c)[0]
        if data[pe:pe + 6] != b'PE\0\0\x64\x86':
            raise ValueError('Camera settings require the supported x64 client')
        count = struct.unpack_from('<H', data, pe + 6)[0]
        table = pe + 24 + struct.unpack_from('<H', data, pe + 20)[0]

        def span(rva, size):
            for i in range(count):
                virtual_size, base, raw_size, raw = struct.unpack_from('<IIII', data, table + i * 40 + 8)
                if base <= rva and rva + size <= base + min(virtual_size, raw_size):
                    return data[raw + rva - base:raw + rva - base + size]
            raise ValueError('Unmapped camera ABI RVA')

        return span

    game, cry = image('bin64/Game.dll'), image('bin64/CrySystem.dll')
    checks = [
        (game, 0x67b4f1, 'ff9038010000'),  # native ISystem::GetIConsole
        (game, 0x67b50b, 'ff90c0000000'),  # native IConsole::GetCVar
        (game, 0xc1b078, 'g_minFov'.encode().hex() + '00'),
        (game, 0xc17b48, 'g_camMax'.encode().hex() + '00'),
        (cry, 0x75500, '488b81a80000008b00c3'),
        (cry, 0x75510, '488b81b0000000f30f1000c3'),
        (cry, 0x75530, '488b81a0000000c3'),
        (cry, 0x75a10, '488d4109c3'),
        (cry, 0x75b04, '488d83b8000000'),
        (cry, 0x75b12, '488983a8000000488d83bc00000048898ba0000000488983b0000000'),
    ]
    for read, rva, hex_bytes in checks:
        expected = bytes.fromhex(hex_bytes)
        if read(rva, len(expected)) != expected:
            raise ValueError(f'Camera variable ABI differs at {rva:#x}; inspect before building')
    # Current native class table, including both numeric and string getters.
    if cry(0x21bcf0, 24) != struct.pack('<QQQ', 0x10075500, 0x10075510, 0x10075530):
        raise ValueError('Camera variable class table differs')
    return dict(inputs=inputs, checkedSpans=len(checks) + 1,
                variables=['g_minFov', 'g_camMax'], fovRange=[60, 170], distanceRange=[5, 50],
                default='Native; existing choices unchanged', polling=False)
