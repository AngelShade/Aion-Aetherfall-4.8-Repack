"""Check native theme DDS opacity, dimensions, and readable state colors."""
from pathlib import Path
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from native_wardrobe_theme import SURFACES, assets, rgb


def luminance(color):
    channels = [c / 255 for c in color]
    channels = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return sum(c * weight for c, weight in zip(channels, (0.2126, 0.7152, 0.0722)))


def contrast(a, b):
    light, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


textures = assets()
for name, data in textures.items():
    assert data[:4] == b'DDS ' and struct.unpack_from('<I', data, 4)[0] == 124
    height, width = struct.unpack_from('<II', data, 12)
    assert len(data) == 128 + width * height * 4
    assert struct.unpack_from('<I', data, 104)[0] == 0xFF000000
    alpha = data[131::4]
    if name.endswith('/selection.dds'):
        assert set(alpha) == {0, 255}, 'Selection outline must preserve item art'
    else:
        assert set(alpha) == {255}, (name, 'Window/control texture must be opaque')
for name, (top, bottom, border, _, _) in SURFACES.items():
    if name == 'button_disabled':
        continue
    text = (245, 252, 255) if name.endswith('_over') else (222, 240, 255)
    assert min(contrast(text, rgb(top)), contrast(text, rgb(bottom))) >= 4.5, name
for name in ('window', 'panel', 'preview', 'field', 'card'):
    for color in SURFACES[name][:2]:
        assert contrast((163, 196, 219), rgb(color)) >= 4.5, (name, 'Secondary text')
assert sum(map(len, textures.values())) < 512 * 1024
print(f'PASS: {len(textures)} DDS surfaces; opaque backgrounds and native control states, '
      'transparent selection interior, readable normal/hover text, total texture data below 512 KB')
