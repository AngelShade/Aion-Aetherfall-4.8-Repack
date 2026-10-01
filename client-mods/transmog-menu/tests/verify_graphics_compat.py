"""Verify combined PE hooks and restore baselines without loading Game.dll."""
import json
from pathlib import Path
import struct
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from graphics_compat import sha, SITES, read_json, read_pak, binary_xml

package = Path(sys.argv[1])
prior = Path(sys.argv[2]).read_bytes()
manifest = read_json(package / 'manifest.json')
data = (package / 'bin64/game.dll').read_bytes()
pe = struct.unpack_from('<I', data, 60)[0]
opt = pe + 24
table = opt + struct.unpack_from('<H', data, pe + 20)[0]
count = struct.unpack_from('<H', data, pe + 6)[0]
sections = [struct.unpack_from('<8sIIIIIIHHI', data, table + i * 40) for i in range(count)]

def offset(rva):
    for section in sections:
        if section[2] <= rva < section[2] + section[3]:
            return section[4] + rva - section[2]
    raise AssertionError('Unmapped RVA')

imports = []
cursor = offset(struct.unpack_from('<I', data, opt + 120)[0])
while any(data[cursor:cursor+20]):
    name = offset(struct.unpack_from('<I', data, cursor + 12)[0])
    imports.append(data[name:data.index(0, name)].decode('ascii'))
    cursor += 20
assert imports.count('AionIconBridge.dll') == imports.count('AionGraphicsMenu.dll') == 1
assert data[0x551c96:0x551c98] == b'\x90\x90'
allowed = [(pe, table + count * 40), (0x551c96, 0x551c98)]
for site, original, _ in SITES:
    pos = offset(site)
    assert prior[pos:pos+len(original)] == original
    assert data[pos] == 0xe9
    target = site + 5 + struct.unpack_from('<i', data, pos + 1)[0]
    assert next(s for s in sections if s[2] <= target < s[2] + s[3])[0].rstrip(b'\0') == b'.agfx'
    allowed.append((pos, pos + len(original)))
for i, (a, b) in enumerate(zip(prior, data)):
    assert a == b or any(start <= i < end for start, end in allowed), hex(i)
state = read_json(package / 'DXVK/graphics-menu/installed.json')
assert state['files'] == read_json(package / 'DXVK/graphics-menu/package/manifest.json')['files']
backup = package / 'DXVK-backups' / manifest['graphicsCompatibility']['backupName']
base = (backup / 'bin64/Game.dll').read_bytes()
expected = bytearray(prior)
expected[0x551c96:0x551c98] = b'\x90\x90'
assert base == expected
assert (backup / 'cursor-base/bin64/Game.dll').read_bytes() == prior
for relative, name in [('Data/ui/game/game.pak', 'global_option_dialog.xml'), ('L10N/enu/Data/data.pak', 'ui/game/global_option_dialog.xml')]:
    with read_pak(package / relative) as installed, read_pak(backup / relative) as restored:
        assert installed.namelist() == restored.namelist()
        for entry in installed.namelist():
            if entry != name:
                assert installed.read(entry) == restored.read(entry), entry
        a = binary_xml(installed.read(name))
        b = binary_xml(restored.read(name))
        assert a.find(".//Widget[@name='cb_use_vulkan']") is not None
        assert b.find(".//Widget[@name='cb_use_vulkan']") is None
        # English item strings and Inventory layouts remain byte-identical.
print('PASS: combined imports/hooks, exact patch bounds, cursor, Wardrobe restore baseline and unrelated UI entries preserved.')
