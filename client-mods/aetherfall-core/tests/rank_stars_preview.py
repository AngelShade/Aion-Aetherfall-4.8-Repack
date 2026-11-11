"""Render the actual rank-star DDSs without building or installing the client.

    python client-mods/aetherfall-core/tests/rank_stars_preview.py

Pillow is needed only here. Outputs go to the repository's ignored diagnostics
directory; the texture generator itself uses only the Python standard library.
"""
import io
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
CORE = HERE.parent
sys.path.insert(0, str(CORE))
from layout.rank_stars import assets  # noqa: E402

OUTPUT = CORE.parents[1] / 'diagnostics' / 'rank-stars'
BACKDROP = (16, 24, 32, 255)  # #101820
TEXT = (112, 184, 255, 255)  # #70b8ff


def _font():
    windows = Path(os.environ.get('WINDIR', 'C:/Windows'))
    for name in (str(windows / 'Fonts' / 'tahoma.ttf'), 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, 11)
        except OSError:
            pass
    try:
        return ImageFont.load_default()
    except OSError:
        return None


def _bilinear(image, size):
    """Four-texel, straight-alpha sampling, as a native bilinear UI sampler.

    Pillow's reducing BILINEAR filter averages a wider area and premultiplies
    RGBA. Sampling each channel directly also exposes potential dark fringes.
    """
    width, height = size
    source = image.load()
    result = Image.new('RGBA', size)
    pixels = result.load()
    for y in range(height):
        py = (y + .5) * image.height / height - .5
        y0 = int(py)
        fy = py - y0
        for x in range(width):
            px = (x + .5) * image.width / width - .5
            x0 = int(px)
            fx = px - x0
            pixels[x, y] = tuple(round(
                source[x0, y0][c] * (1 - fx) * (1 - fy)
                + source[x0 + 1, y0][c] * fx * (1 - fy)
                + source[x0, y0 + 1][c] * (1 - fx) * fy
                + source[x0 + 1, y0 + 1][c] * fx * fy) for c in range(4))
    return result


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    textures = {}
    for name, data in assets('rank-stars').items():
        stem = Path(name).stem
        with Image.open(io.BytesIO(data)) as image:
            texture = image.convert('RGBA')
        textures[stem] = _bilinear(texture, (10, 10))
        enlarged = texture.resize((texture.width * 8, texture.height * 8),
                                  Image.Resampling.NEAREST)
        preview = Image.new('RGBA', enlarged.size, BACKDROP)
        preview.alpha_composite(enlarged)
        preview.convert('RGB').save(OUTPUT / f'{stem}-8x.png')

    font = _font()
    label = 'Equip: Good'
    measure = ImageDraw.Draw(Image.new('RGB', (1, 1)))
    box = measure.textbbox((0, 0), label, font=font) if font else (0, 0, 0, 0)
    star_x = 8 + (box[2] - box[0] + 10 if font else 0)
    rows = Image.new('RGBA', (star_x + 5 * 10 + 4 + 8, 5 * 20 + 8), BACKDROP)
    draw = ImageDraw.Draw(rows)
    for rank in range(1, 6):
        top = 8 + (rank - 1) * 20
        if font:
            draw.text((8 - box[0], top + (10 - (box[3] - box[1])) // 2 - box[1]),
                      label, fill=TEXT, font=font)
        for index in range(5):
            key = 'star-on' if index < rank else 'star-off'
            rows.alpha_composite(textures[key], (star_x + index * 11, top))
    rows = rows.convert('RGB')
    rows.save(OUTPUT / 'ranks-1x.png')
    rows.resize((rows.width * 6, rows.height * 6), Image.Resampling.NEAREST).save(
        OUTPUT / 'ranks-6x.png')
    for path in sorted(OUTPUT.glob('*.png')):
        print(path)


if __name__ == '__main__':
    main()
