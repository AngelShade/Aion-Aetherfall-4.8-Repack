"""Gear-quality crests for bots on the unit frames (owner-approved Playerbot Identity board).

The five paintings in art/identity-crests were made with Codex image generation from that
board's top-centre frame ornaments. They are wide (the two best have wings), so each is baked
into a 64x32 texture and shown twice as wide as tall beside the bot's gear label.

`assets` only reads the baked DDS files, so preparing the client needs no image library.
Run this file after replacing a painting to bake them again (needs Pillow).
"""
from pathlib import Path

RARITIES = ('poor', 'ordinary', 'good', 'great', 'excellent')
# Portrait borders from the same board: one per gear quality and one for the player's own portrait.
# All six share one transparent opening (40..215 of 256), so a portrait sits the same in each.
BORDERS = RARITIES + ('player',)
BORDER_SIZE = 128
BORDER_ART = Path(__file__).resolve().parents[1] / 'art' / 'identity-borders'
WIDTH, HEIGHT = 64, 32
ART = Path(__file__).resolve().parents[1] / 'art' / 'identity-crests'


def assets(prefix):
    result = {}
    for rarity in RARITIES:
        data = (ART / f'crest-{rarity}.dds').read_bytes()
        if len(data) != 128 + WIDTH * HEIGHT * 4 or data[:4] != b'DDS ':
            raise ValueError('Unexpected baked crest: ' + rarity)
        result[f'{prefix}/crest-{rarity}.dds'] = data
    for name in BORDERS:
        data = (BORDER_ART / f'border-{name}.dds').read_bytes()
        if len(data) != 128 + BORDER_SIZE * BORDER_SIZE * 4 or data[:4] != b'DDS ':
            raise ValueError('Unexpected baked portrait border: ' + name)
        result[f'{prefix}/border-{name}.dds'] = data
    return result


def bake():
    import sys
    from PIL import Image
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from options_skin import dds
    for rarity in RARITIES:
        source = Image.open(ART / f'crest-{rarity}.png').convert('RGBA')
        art = source.crop(source.getchannel('A').getbbox())
        # One clear texel all round, so bilinear sampling never drags an edge in.
        scale = min((WIDTH - 2) / art.width, (HEIGHT - 2) / art.height)
        size = max(1, round(art.width * scale)), max(1, round(art.height * scale))
        # Premultiplied resize: transparent texels must not darken the rim.
        small = art.convert('RGBa').resize(size, Image.LANCZOS).convert('RGBA')
        canvas = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
        canvas.paste(small, ((WIDTH - size[0]) // 2, (HEIGHT - size[1]) // 2))
        pixels = canvas.load()
        (ART / f'crest-{rarity}.dds').write_bytes(dds(WIDTH, HEIGHT, lambda x, y: pixels[x, y]))
    for name in BORDERS:
        source = Image.open(BORDER_ART / f'border-{name}.png').convert('RGBA')
        if source.size != (256, 256):
            raise ValueError('Portrait borders are authored at 256x256: ' + name)
        # The whole canvas is kept, not cropped: the shared opening must stay where it is.
        small = source.convert('RGBa').resize((BORDER_SIZE, BORDER_SIZE), Image.LANCZOS).convert('RGBA')
        pixels = small.load()
        (BORDER_ART / f'border-{name}.dds').write_bytes(dds(BORDER_SIZE, BORDER_SIZE, lambda x, y: pixels[x, y]))


if __name__ == '__main__':
    bake()
