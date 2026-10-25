"""Index the original client's UI sprites for the Player Companions window, without extracting or distributing artwork.

Writes bin64/AionUiArt.index: archive hashes, entry offsets/keys and sprite rectangles. AionIconBridge.dll serves
http://127.0.0.1:<port>/market/companions/media/art/<name>.png from the client's own archives in memory. Rectangles
come from the client's UI_Preload.xml skins (class emblems: Textures/ui/skill_bg job_skill textures).
"""
import argparse
import binascii
import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import read_pak, KEYS  # noqa: E402

# The launcher embeds this same recipe, so theme switches rebuild matching metadata
# without Python or a second independently maintained sprite mapping.
RECIPE = json.loads(Path(__file__).with_name('ui_art.json').read_text(encoding='utf-8'))
SPRITES = RECIPE['sprites']
SPRITE = struct.Struct('<32sBBHHHHHIIII H32s')


def entry(raw, archive, texture):
    names = {n.lower(): n for n in archive.namelist()}
    info = archive.getinfo(names[texture.lower() + '.dds'])
    sig, _, flags, method, _, _, crc, compressed, size, namesz, extra = struct.unpack_from('<4sHHHHHIIIHH', raw, info.header_offset)
    if flags & 8 or method not in (0, 8) or sig != bytes.fromhex('afb4fcfb'):
        raise ValueError('Unsupported original DDS archive entry: ' + texture)
    start = info.header_offset + 30 + namesz + extra
    for candidate, table in enumerate(KEYS, 1):
        key_offset = (compressed & 31) * 32 if candidate == 1 else compressed & 1023
        key = table[key_offset:key_offset + min(32, compressed)]
        trial = bytearray(raw[start:start + compressed])
        for i, value in enumerate(key):
            trial[i] ^= value
        try:
            plain = zlib.decompress(trial, -15) if method == 8 else trial
        except zlib.error:
            continue
        if len(plain) == size and binascii.crc32(plain) == crc and plain[:4] == b'DDS ':
            height, width = struct.unpack_from('<II', plain, 12)
            return start, compressed, size, crc, method, key, width, height
    raise ValueError('DDS decoding check failed: ' + texture)


def build(client):
    archives = RECIPE['archives']
    raws = {a: (client / a).read_bytes() for a in archives}
    paks = {a: read_pak(client / a) for a in archives}
    records = []
    for name, (archive, texture, x, y, w, h, transform) in SPRITES.items():
        start, compressed, size, crc, method, key, width, height = entry(raws[archive], paks[archive], texture)
        if x + w > width or y + h > height or len(name) > 31:
            raise ValueError('Sprite rectangle exceeds texture: ' + name)
        records.append(SPRITE.pack(name.encode().ljust(32, b'\0'), archives.index(archive), transform, x, y, w, h, method,
                                   start, compressed, size, crc, len(key), key.ljust(32, b'\0')))
    header = struct.pack('<8sI', b'AIART001', len(records))
    for a in archives:
        header += a.encode().ljust(64, b'\0') + struct.pack('<Q', len(raws[a])) + hashlib.sha256(raws[a]).digest()
    data = header + b''.join(records)
    return data, {'sprites': len(records), 'indexBytes': len(data), 'archives': {a: hashlib.sha256(r).hexdigest() for a, r in raws.items()}}


def prepare(client, output):
    data, report = build(client)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, help='staged output, or the index to check with --verify')
    parser.add_argument('--verify', action='store_true', help='verify all archive fingerprints, DDS entries and sprite records without writing')
    args = parser.parse_args()
    if args.verify:
        expected, report = build(args.client)
        target = args.output or args.client / 'bin64/AionUiArt.index'
        if target.read_bytes() != expected:
            raise SystemExit('Companion artwork index is stale; rebuild it against the current client archives: ' + str(target))
        print(json.dumps(dict(report, verified=str(target))))
    else:
        if args.output is None:
            parser.error('--output is required unless --verify is used')
        print(json.dumps(prepare(args.client, args.output)))
