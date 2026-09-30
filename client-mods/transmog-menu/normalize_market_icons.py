"""Normalize transparent client icon padding before serving artwork to Aion."""
import argparse
from pathlib import Path
from PIL import Image


def normalize(image):
    image = image.convert('RGBA')
    bounds = image.getchannel('A').getbbox()
    if not bounds or bounds == (0, 0, image.width, image.height):
        return image
    artwork = image.crop(bounds)
    side = max(artwork.size)
    canvas = Image.new('RGBA', (side, side))
    canvas.paste(artwork, ((side - artwork.width) // 2, (side - artwork.height) // 2))
    return canvas.resize((64, 64), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    changed = 0
    for path in args.directory.glob('*.png'):
        with Image.open(path) as original:
            result = normalize(original)
            if result.tobytes() != original.convert('RGBA').tobytes():
                result.save(path, optimize=True)
                changed += 1
    print(f'Normalized {changed} icons.')
