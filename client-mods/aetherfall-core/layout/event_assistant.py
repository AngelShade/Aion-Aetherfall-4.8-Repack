"""The World Event assistant's pieces on the passive startup canvas (AFHFrames).

Guild Wars 2 shows a running event in a top-right tracker: title, objective, progress bar, timer and the
player's medal. The plugin's 20 dialog slots are spent (prepare.BUDGET), so these pieces are direct
children of the unit-frame canvas, which already spans the screen and takes no input. The Lua module
EventAssistant owns them; UnitFrames never touches them.

The paintings in art/event-assistant were made with Codex image generation in the Aetherfall UI style.
`assets` only reads the baked DDS files, so preparing the client needs no image library. Run this file
after replacing a painting to bake them again (needs Pillow).
"""
from pathlib import Path
import re
import xml.etree.ElementTree as E

ART = Path(__file__).resolve().parents[1] / 'art' / 'event-assistant'
PREFIX = 'Textures/UI/AetherfallEvents'
NAME = 'AFHFEvent'
# name -> baked texture size. Icons and medals are square; the panel and bar keep their painted ratio.
ICONS = ('kill', 'defend', 'escort', 'boss', 'retake', 'group', 'chest')
MEDALS = ('gold', 'silver', 'bronze')
SIZES = {**{f'icon-{k}': (64, 64) for k in ICONS}, **{f'medal-{k}': (64, 64) for k in MEDALS},
         'panel': (512, 160), 'bar-back': (256, 16), 'bar-fill': (256, 16), 'map-event': (64, 64)}
# Declaration order is drawing order: panel under everything, the bar fill over its trough.
IMAGES = ('Panel', 'BarBack', 'BarFill', 'Icon', 'Medal')
TEXTS = (('Title', 'left'), ('Group', 'right'), ('Stage', 'left'), ('Objective', 'left'), ('Count', 'right'), ('Timer', 'right'))


def widgets(parent):
    """Append the assistant's passive pieces to the canvas dialog. They start hidden and never take input."""
    for part in IMAGES:
        E.SubElement(parent, 'Widget', type='image', name=NAME + part, frame='0,0,1,1', flag='not_active;not_visible')
    for part, align in TEXTS:
        E.SubElement(parent, 'Widget', type='static', name=NAME + part, frame='0,0,1,1', font='ui_default', HAlign=align,
                     flag='not_active;not_visible;truncate;not_multiline;not_wordwrap')
    return parent


def update_xml(payload):
    """Reconcile only the assistant's pieces inside AFHFrames and the Screen count, retaining every other byte.

    layout.unitframes.update_xml rebuilds AFHFrames without them, so this runs after it on retained updates.
    """
    if payload.count(b'</Screen>') != 1:
        raise ValueError('Unexpected HUD XML for the event assistant')
    block = re.search(rb'<Dialog\s+[^>]*\bname=["\']AFHFrames["\'][^>]*>.*?</Dialog>', payload, re.S)
    if not block:
        raise ValueError('The unit-frame canvas AFHFrames is missing')
    inner = re.sub(rb'\s*<Widget\s+[^>]*\bname=["\']' + NAME.encode() + rb'[A-Za-z]+["\'][^>]*/>', b'', block[0])
    pieces = b''.join(b'\n    ' + E.tostring(node, encoding='utf-8') for node in widgets(E.Element('Dialog')))
    rebuilt = inner[:inner.rindex(b'</Dialog>')] + pieces + b'\n  </Dialog>'
    updated = payload[:block.start()] + rebuilt + payload[block.end():]
    total = sum(1 for node in E.fromstring(updated).iter() if node.tag in ('Dialog', 'Widget'))
    if not re.search(rb'<Screen\b[^>]*\bwidget_cnt=["\']\d+["\']', updated):
        raise ValueError('HUD XML has no widget count')
    return re.sub(rb'(<Screen\b[^>]*\bwidget_cnt=)["\']\d+["\']',
                  lambda match: match[1] + b'"' + str(total).encode() + b'"', updated, count=1)


def assets():
    result = {}
    for name, (width, height) in SIZES.items():
        data = (ART / f'{name}.dds').read_bytes()
        if len(data) != 128 + width * height * 4 or data[:4] != b'DDS ':
            raise ValueError('Unexpected baked event texture: ' + name)
        result[f'{PREFIX}/{name}.dds'] = data
    return result


def bake():
    import sys
    from PIL import Image
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from options_skin import dds
    for name, (width, height) in SIZES.items():
        source = Image.open(ART / f'{name}.png').convert('RGBA')
        # The bar trough and fill share one painted frame, so they keep their whole canvas and stay aligned;
        # the panel too. Icons and medals are cropped to their painting first.
        whole = name in ('panel', 'bar-back', 'bar-fill')
        box = (0, 0, source.width, source.height) if whole else source.getchannel('A').getbbox() or (0, 0, source.width, source.height)
        art = source.crop(box)
        # One clear texel all round, so bilinear sampling never drags an edge in; premultiplied resize keeps rims clean.
        scale = min(width / art.width, height / art.height) if whole else min((width - 2) / art.width, (height - 2) / art.height)
        size = max(1, round(art.width * scale)), max(1, round(art.height * scale))
        small = art.convert('RGBa').resize(size, Image.LANCZOS).convert('RGBA')
        canvas = Image.new('RGBA', (width, height), (0, 0, 0, 0))
        canvas.paste(small, ((width - size[0]) // 2, (height - size[1]) // 2))
        pixels = canvas.load()
        (ART / f'{name}.dds').write_bytes(dds(width, height, lambda x, y: pixels[x, y]))
        print('baked', name, size)


if __name__ == '__main__':
    bake()
