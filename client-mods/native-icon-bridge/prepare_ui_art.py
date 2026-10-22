"""Index the original client's UI sprites for the Player Companions window, without extracting or distributing artwork.

Writes bin64/AionUiArt.index: archive hashes, entry offsets/keys and sprite rectangles. AionIconBridge.dll serves
http://127.0.0.1:<port>/market/companions/media/art/<name>.png from the client's own archives in memory. Rectangles
come from the client's UI_Preload.xml skins (class emblems: Textures/ui/skill_bg job_skill textures).
"""
import argparse
import binascii
import hashlib
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import read_pak, KEYS  # noqa: E402

UI, JOBS = 'Textures/ui/ui.pak', 'Textures/ui/skill_bg/skill_bg.pak'
NONE, GOLD, LIFT = 0, 1, 2  # bridge transforms: as is, gold emblem, brightened empty-slot silhouette
# PlayerClass -> the client's job texture name
CLASSES = {'warrior': 'warrior', 'gladiator': 'fighter', 'templar': 'knight', 'scout': 'scout', 'assassin': 'assassin',
           'ranger': 'ranger', 'mage': 'mage', 'sorcerer': 'wizard', 'spirit_master': 'elementalist', 'priest': 'priest',
           'cleric': 'cleric', 'chanter': 'chanter', 'engineer': 'engineer', 'gunner': 'gunner', 'rider': 'rider',
           'artist': 'artist', 'bard': 'bard'}
# name -> (archive, texture, x, y, w, h, transform)
SPRITES = {'slot-bg': (UI, 'v5_hud_s2', 134, 2, 44, 44, NONE), 'slot-filled': (UI, 'v5_hud_s2', 180, 2, 44, 44, NONE),
           'slot-helmet': (UI, 'v5_common_2', 931, 2, 44, 44, LIFT), 'slot-amulet': (UI, 'v5_common_2', 978, 2, 44, 44, LIFT),
           'slot-shoulder': (UI, 'v5_common_2', 978, 90, 44, 44, LIFT), 'slot-learring': (UI, 'v5_common_2', 978, 46, 44, 44, LIFT),
           'slot-rearring': (UI, 'v5_common_2', 931, 46, 44, 44, LIFT), 'slot-armor': (UI, 'v5_common_2', 931, 90, 44, 44, LIFT),
           'slot-glove': (UI, 'v5_common_2', 978, 134, 44, 44, LIFT), 'slot-lring': (UI, 'v5_common_2', 978, 178, 44, 44, LIFT),
           'slot-rring': (UI, 'v5_common_2', 931, 178, 44, 44, LIFT), 'slot-pants': (UI, 'v5_common_2', 931, 134, 44, 44, LIFT),
           'slot-belt': (UI, 'v5_common_2', 978, 222, 44, 44, LIFT), 'slot-shoes': (UI, 'v5_common_2', 931, 222, 44, 44, LIFT),
           'slot-rhand': (UI, 'v5_common_2', 884, 2, 44, 44, LIFT), 'slot-lhand': (UI, 'v5_common_2', 839, 2, 44, 44, LIFT),
           'slot-switch': (UI, 'v5_common_2', 794, 2, 44, 44, LIFT), 'slot-wing': (UI, 'v5_common_2', 807, 48, 44, 44, LIFT),
           'slot-feather': (UI, 'v5_common_2', 817, 231, 35, 34, LIFT), 'slot-battery': (UI, 'v5_common_2', 893, 49, 35, 34, LIFT),
           'stigma-normal': (UI, 'v5_common_5', 575, 80, 78, 78, NONE), 'stigma-highest': (UI, 'v5_common_5', 741, 80, 78, 78, NONE),
           'button-up': (UI, 'v5_hud_s2', 924, 2, 48, 24, NONE), 'button-over': (UI, 'v5_hud_s2', 102, 84, 48, 24, NONE),
           'button-down': (UI, 'v5_hud_s2', 52, 84, 48, 24, NONE), 'button-gold': (UI, 'v5_hud_s2', 974, 2, 48, 24, NONE),
           'button-disabled': (UI, 'v5_hud_s2', 2, 84, 48, 24, NONE), 'tab-up': (UI, 'v5_hud_s2', 798, 2, 40, 26, NONE),
           'tab-down': (UI, 'v5_hud_s2', 840, 2, 40, 26, NONE), 'tab-over': (UI, 'v5_hud_s2', 882, 2, 40, 26, NONE),
           'portrait-bg': (UI, 'v5_common_2', 894, 270, 128, 178, NONE)}
SPRITES.update({'class-' + c: (JOBS, 'job_skill_' + j, 0, 0, 256, 256, GOLD) for c, j in CLASSES.items()})
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


def prepare(client, output):
    archives = [UI, JOBS]
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
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    return {'sprites': len(records), 'indexBytes': len(data), 'archives': {a: hashlib.sha256(r).hexdigest() for a, r in raws.items()}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(prepare(args.client, args.output))
