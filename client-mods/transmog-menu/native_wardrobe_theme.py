"""Small opaque DDS surfaces for native Wardrobe controls; no client art copied."""
from pathlib import Path
import struct

PREFIX = 'Textures/UI/WardrobeNative'
# Native controls retain their geometry, text, input, and original item art.
SURFACES = {
    'window': ('#142B40', '#0D1C2B', None, 64, 64),
    'panel': ('#1B3449', '#13283A', '#395B73', 128, 64),
    'preview': ('#193348', '#0E2233', '#395B73', 128, 64),
    'field': ('#102537', '#102537', '#496F88', 128, 32),
    'card': ('#213D53', '#193348', '#395970', 128, 64),
    'card_over': ('#2C536C', '#24475F', '#6194B2', 128, 64),
    'card_selected': ('#2B617F', '#214D69', '#87D3F4', 128, 64),
    'button': ('#315975', '#25465F', '#567F9B', 128, 32),
    'button_over': ('#356987', '#28546F', '#97CDE8', 128, 32),
    'button_down': ('#1D415B', '#234D67', '#74BADB', 128, 32),
    'button_selected': ('#2D6685', '#214D68', '#8BD8F8', 128, 32),
    'button_disabled': ('#263B4B', '#203240', '#3D5262', 128, 32),
    'primary': ('#28698C', '#1C506F', '#8AD1F3', 128, 32),
    'primary_over': ('#317699', '#266080', '#C1EBFF', 128, 32),
    'primary_down': ('#194560', '#245E7D', '#85CFF2', 128, 32),
}


def rgb(value):
    return tuple(bytes.fromhex(value.removeprefix('#')))


def dds(top, bottom, border, width, height):
    top, bottom = rgb(top), rgb(bottom)
    edge = rgb(border) if border else None
    pixels = bytearray()
    for y in range(height):
        color = tuple(round(a + (b - a) * y / (height - 1)) for a, b in zip(top, bottom))
        for x in range(width):
            r, g, b = edge if edge and (x in (0, width - 1) or y in (0, height - 1)) else color
            pixels.extend((b, g, r, 255))
    header = [124, 0x100F, height, width, width * 4, 0, 0] + [0] * 11
    header += [32, 0x41, 0, 32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000, 0x1000, 0, 0, 0, 0]
    return b'DDS ' + struct.pack('<31I', *header) + pixels


def selection():
    size = 44
    pixels = bytearray()
    for y in range(size):
        for x in range(size):
            edge = min(x, y, size - 1 - x, size - 1 - y)
            # The overlay outline is transparent inside so the item stays visible.
            pixels.extend((244, 211, 135, 255 if edge < 2 else 0))
    base = bytearray(dds('#000000', '#000000', None, size, size))
    base[128:] = pixels
    return bytes(base)


def assets():
    result = {f'{PREFIX}/{name}.dds': dds(*values) for name, values in SURFACES.items()}
    result[f'{PREFIX}/selection.dds'] = selection()
    return result


def stage(output):
    for name, data in assets().items():
        path = Path(output) / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
