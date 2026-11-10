"""Flat ElvUI-style surfaces for the /afui options window; generated, no client art copied.

The client stretches a widget image over the whole widget (no nine-slice), so a
texture with a baked 1 px border is only crisp when it is as large as the widget
it skins. `bordered()` therefore makes one texture per control size; plain fills
are 4x4 and stretch exactly.
"""
from pathlib import Path
import struct

PREFIX = 'Textures/UI/AetherfallOptions'
# docs/AETHERFALL_UI_STYLE.md palette.
PALETTE = dict(abyss='#070d16', deep='#0a1521', surface='#0e1b29', raised='#142538', line='#1f3447',
               line_hi='#2e4a61', aether='#8fe3ec', aether_deep='#3a9fbe', gold='#d6b878', gold_deep='#8a6d3c',
               black='#000000', dim='#6f889a')


def rgb(value):
    value = PALETTE.get(value, value)
    return tuple(bytes.fromhex(value.removeprefix('#')))


def dds(width, height, pixel):
    """Uncompressed BGRA texture; `pixel(x, y)` returns (r, g, b, a)."""
    data = bytearray()
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixel(x, y)
            data.extend((b, g, r, a))
    header = [124, 0x100F, height, width, width * 4, 0, 0] + [0] * 11
    header += [32, 0x41, 0, 32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000, 0x1000, 0, 0, 0, 0]
    return b'DDS ' + struct.pack('<31I', *header) + bytes(data)


def fill(colour, alpha=255):
    c = rgb(colour)
    return dds(4, 4, lambda x, y: c + (alpha,))


def bordered(width, height, colour, border, inner=None):
    """Flat fill with a 1 px border and an optional 1 px inner line (selected state)."""
    c, edge, second = rgb(colour), rgb(border), rgb(inner) if inner else None

    def pixel(x, y):
        ring = min(x, y, width - 1 - x, height - 1 - y)
        return (edge if ring == 0 else second if second and ring == 1 else c) + (255,)
    return dds(width, height, pixel)


def check(size, on):
    """Check box: dark well, or an aether block inset by 2 px when on."""
    well, edge, mark = rgb('surface'), rgb('line_hi'), rgb('aether')

    def pixel(x, y):
        ring = min(x, y, size - 1 - x, size - 1 - y)
        return (edge if ring == 0 else mark if on and ring >= 3 else well) + (255,)
    return dds(size, size, pixel)


def arrow(size=12):
    """Dropdown arrow: a filled aether triangle on transparency."""
    mark = rgb('aether')

    def pixel(x, y):
        top, rows = size // 3, size // 2
        inside = top <= y < top + rows and (y - top) <= x - 1 < size - 1 - (y - top)
        return mark + (255 if inside else 0,)
    return dds(size, size, pixel)


def stage(output, assets):
    for name, data in assets.items():
        path = Path(output) / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def assets():
    """Loose DDS resources used by AF.Skin; borders are composed of 1 px lines."""
    colours = {
        'fill-abyss': 'abyss', 'fill-deep': 'deep', 'fill-raised': 'raised',
        'fill-aether': 'aether', 'fill-aether-deep': 'aether_deep', 'fill-gold': 'gold', 'fill-white': '#ffffff',
        'button': 'surface', 'button-over': 'raised', 'button-down': 'deep',
        'button-selected': 'raised', 'button-disabled': 'deep',
    }
    result = {f'{PREFIX}/{name}.dds': fill(colour) for name, colour in colours.items()}
    for name, colour in (('line', 'line_hi'), ('line-gold', 'gold'), ('line-aether', 'aether')):
        c = rgb(colour)
        result[f'{PREFIX}/{name}.dds'] = dds(1, 1, lambda x, y, c=c: c + (255,))
    result[f'{PREFIX}/check-on.dds'] = check(18, True)
    result[f'{PREFIX}/check-off.dds'] = check(18, False)
    result[f'{PREFIX}/thumb.dds'] = bordered(10, 20, 'aether_deep', 'aether')
    result[f'{PREFIX}/arrow.dds'] = arrow()
    result[f'{PREFIX}/editor-panel.dds'] = bordered(380, 278, 'deep', 'line_hi')
    # Fixed-size editor chrome has baked borders and creates no extra widgets.
    widths = (24, 44, 52, 58, 60, 62, 74, 76, 80, 82, 86, 88, 98, 100, 116, 136, 158)
    for width in widths:
        for name, colour in (('button','surface'), ('button-over','raised'),
                             ('button-down','deep'), ('button-selected','raised'),
                             ('button-disabled','deep'), ('field','abyss')):
            edge = 'aether' if name == 'button-selected' else 'line_hi'
            result[f'{PREFIX}/{name}-{width}x24.dds'] = bordered(width, 24, colour, edge)
    return result
