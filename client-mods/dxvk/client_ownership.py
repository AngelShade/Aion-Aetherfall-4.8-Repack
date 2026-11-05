"""Read-only counterpart of ClientOwnership.ps1 for mod preparation tools.

Unrelated changes never need Graphics package copies or restore snapshots.
The installed definition is authoritative; no baseline is learned from a mod's
proposed bytes. The launch scripts also check this definition independently.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import read_pak, binary_xml


def independent(root):
    return (root / 'Mods/GraphicsMenu/installed.json').is_file() or (root / 'Mods/GraphicsMenu/ownership.json').is_file()


def verify(root, game=None, archives=None):
    definition = root / 'Mods/GraphicsMenu/ownership.json'
    if not definition.is_file():
        definition = Path(__file__).with_name('ownership.json')
    spec = json.loads(definition.read_text(encoding='utf-8-sig'))
    if spec['schema'] != 2:
        raise ValueError('Unsupported Graphics ownership definition')
    dll = (root / 'bin64/AionGraphicsMenu.dll').read_bytes()
    if hashlib.sha256(dll).hexdigest() != spec['dllHash'].lower():
        raise ValueError('Graphics menu DLL damaged')
    data = (root / 'bin64/Game.dll').read_bytes() if game is None else game
    for offset, expected in [(spec['sectionOffset'], spec['sectionHeader']),
                             *((r['offset'], r['installed']) for r in spec['ranges'])]:
        expected = bytes.fromhex(expected)
        if data[offset:offset + len(expected)] != expected:
            raise ValueError(f'Graphics hook/import bytes damaged at {offset:#x}')
    pe = struct.unpack_from('<I', data, 60)[0]
    table = pe + 24 + struct.unpack_from('<H', data, pe + 20)[0]
    sections = [struct.unpack_from('<8sIIII', data, table + i * 40)
                for i in range(struct.unpack_from('<H', data, pe + 6)[0])]

    def offset(rva):
        return next(s[4] + rva - s[2] for s in sections if s[2] <= rva < s[2] + s[3])

    pos = offset(struct.unpack_from('<I', data, pe + 24 + 120)[0])
    found = []
    for _ in range(4096):
        descriptor = data[pos:pos + 20]
        if not any(descriptor):
            break
        name = offset(struct.unpack_from('<I', descriptor, 12)[0])
        if data[name:data.index(0, name)].lower() == b'aiongraphicsmenu.dll':
            found.append(descriptor.hex())
        pos += 20
    if found != [spec['descriptor']]:
        raise ValueError('Graphics menu active import damaged')
    for path, at in [('bin64/Game.dll', 0x551c96), ('bin64/XRenderD3D9.dll', 0x1202d7)]:
        value = data if path.endswith('/Game.dll') else (root / path).read_bytes()
        if value[at:at + 2] != b'\x90\x90':
            raise ValueError('Native cursor instructions damaged: ' + path)
    for part in spec['ui']:
        source = (archives or {}).get(part['path'], root / part['path'])
        with read_pak(source) as pak:
            tree = binary_xml(pak.read(part['entry']))
        page = tree.findall(".//Widget[@name='page_screen']")
        if len(page) != 1:
            raise ValueError('Graphics Screen page missing or duplicated')
        for text in part['widgets']:
            expected = ET.fromstring(text)
            found = tree.findall(".//Widget[@name='%s']" % expected.get('name'))
            if (len(found) != 1 or found[0] not in list(page[0]) or
                    found[0].attrib != expected.attrib or list(found[0]) or
                    (found[0].text or '') != (expected.text or '')):
                raise ValueError('Graphics menu control damaged: ' + expected.get('name'))
    return {'ownershipSchema': 2, 'localized': True}
