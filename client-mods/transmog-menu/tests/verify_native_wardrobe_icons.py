"""Check native Wardrobe DDS selection against the installed bridge index."""
import argparse
import hashlib
import json
import re
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from native_wardrobe_icons import build
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'expanded-warehouse'))
from codec import read_pak


def verify(client, output=None):
    generated = build(client)
    mapping = {int(item): path for item, path in
               re.findall(r'^\[(\d+)\]=("[^"\n]+"),$', generated.decode(), re.M)
               for path in [json.loads(path)]}
    data = (client / 'bin64/AionIconBridge.index').read_bytes()
    assert data[:8] == b'AICON002'
    items, count, size = struct.unpack_from('<IIQ', data, 8)
    raw = (client / 'Data/Items/Items.pak').read_bytes()
    assert len(raw) == size and hashlib.sha256(raw).digest() == data[24:56]
    assert len(data) == 56 + items * 8 + count * 56
    indexed = dict(struct.unpack_from('<II', data, 56 + i * 8) for i in range(items))
    assert mapping.keys() <= indexed.keys(), 'Native paths missing from bridge index'
    legacy = large = 0
    with read_pak(client / 'Data/Items/Items.pak') as archive:
        entries = {Path(info.filename).stem.lower(): info for info in archive.infolist()
                   if info.filename.lower().endswith('.dds')}
        for item, path in mapping.items():
            stem = Path(path).stem.lower()
            info = entries[stem]
            record = struct.unpack_from('<IIIIIHH32s', data, 56 + items * 8 + indexed[item] * 56)
            off = info.header_offset
            fields = struct.unpack_from('<4sHHHHHIIIHH', raw, off)
            actual = (off + 30 + fields[9] + fields[10], fields[7], fields[8], fields[6])
            assert actual == record[:4], (item, path, 'Different DDS loaded and cropped')
            side = 64 if stem.endswith('_64') else 40
            assert side == record[4], (item, path, 'Sprite size mismatch')
            legacy += side == 40
            large += side == 64
    assert legacy and large, 'Both original sprite sizes must be covered'
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(generated)
    print(f'PASS: {len(mapping)} native DDS paths match indexed offsets, CRCs and crop sizes; '
          f'{legacy} legacy sprites and {large} full-size variants; no artwork extracted')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    verify(args.client, args.output)
