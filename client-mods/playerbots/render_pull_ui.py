"""Export the authored recruitment atlas and serif labels as native DXT5 textures.

The generated source plates stay blank. Only finite UI labels are baked here;
the recruited name always uses the client's native Unicode text widget.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import sys
import xml.etree.ElementTree as E

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np
from scipy.ndimage import label as components

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import read_pak, binary_xml

CLASSES = {'TEMPLAR': ('Templar', 'knight'), 'GLADIATOR': ('Gladiator', 'fighter'),
           'ASSASSIN': ('Assassin', 'assassin'), 'RANGER': ('Ranger', 'ranger'),
           'SORCERER': ('Sorcerer', 'wizard'), 'SPIRIT_MASTER': ('Spiritmaster', 'elementalist'),
           'CLERIC': ('Cleric', 'cleric'), 'CHANTER': ('Chanter', 'chanter'),
           'GUNNER': ('Gunner', 'gunner'), 'RIDER': ('Aethertech', 'rider'), 'BARD': ('Songweaver', 'bard')}
VALUES = {'POOR': '#c6cbd3', 'ORDINARY': '#eee5cf', 'GOOD': '#9de8bd',
          'GREAT': '#7de5ed', 'EXCELLENT': '#d4a3ff'}


def panel_entrance(output, panel_source=None, count=18):
    """Finite native DXT5 clip; no browser animation or runtime particle work.

    Ice/gold wisps converge on the result frame as the native result group
    settles. This is decorative, independent of both recorded quality rolls.
    """
    output.mkdir(parents=True, exist_ok=True)
    previews = output.parent / 'panel-in-preview'
    previews.mkdir(exist_ok=True)
    rng = np.random.default_rng(4801)
    particles = [(rng.uniform(.15, .85), rng.uniform(.03, .97), rng.uniform(.65, 1.3), i % 2)
                 for i in range(44)]
    records = {}
    silhouette = None
    if panel_source is not None:
        panel = panel_source if isinstance(panel_source, Image.Image) else Image.open(panel_source)
        silhouette = Image.new('L', (512, 1024))
        silhouette.paste(panel.convert('RGBA').getchannel('A').resize((460, 964), Image.Resampling.LANCZOS), (26, 30))
        silhouette = silhouette.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(8))
    for index in range(count):
        t = index / (count - 1)
        power = math.sin(math.pi * t) ** 1.2
        image = Image.new('RGBA', (512, 1024))
        glow = Image.new('RGBA', image.size)
        draw = ImageDraw.Draw(glow)
        # Derive bloom from the actual ornate silhouette; never introduce a
        # generic rectangular outline around the authored metalwork.
        if silhouette is not None:
            bloom = Image.new('RGBA', image.size, (148,211,255,0))
            bloom.putalpha(silhouette.point(lambda a:round(a*.32*power)))
            image.alpha_composite(bloom)
        for start, along, speed, side in particles:
            travel = min(1.0, t * speed)
            # Start beyond the frame and travel inward, ending on its trim.
            x = (8 + 28 * travel) if side == 0 else (504 - 28 * travel)
            y = 40 + along * 944 + (start - .5) * (1-travel) * 95
            alpha = round(225 * power * (1-abs(travel-.7)*.5))
            tail = 22 * (1-travel) + 4
            direction = -1 if side == 0 else 1
            colour = (255, 221, 151, alpha) if start > .5 else (175, 228, 255, alpha)
            draw.line((x+direction*tail, y-8, x, y), fill=colour, width=2)
            draw.ellipse((x-2,y-2,x+2,y+2),fill=(255,250,220,alpha))
        image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(7)))
        image.alpha_composite(glow)
        name = f'panel-in-{index:03d}.dds'
        image.save(output / name, format='DDS', pixel_format='DXT5')
        image.save(previews / f'{index:03d}.png')
        records[name] = hashlib.sha256((output / name).read_bytes()).hexdigest()
    (output/'panel-in-manifest.json').write_text(json.dumps(dict(
        frames=count, fps=20, durationSeconds=.9, dimensions=[512,1024],
        format='DXT5', loop=False, qualityDependent=False, textures=records),indent=2)+'\n')
    return records


def label(text, size, font, colour='#f4ead7', centre=True):
    image = Image.new('RGBA', size)
    draw = ImageDraw.Draw(image)
    bounds = draw.textbbox((0, 0), text, font=font, stroke_width=1)
    x = (size[0] - bounds[2] + bounds[0]) / 2 if centre else 3
    y = (size[1] - bounds[3] + bounds[1]) / 2 - bounds[1]
    draw.text((x, y), text, font=font, fill=colour, stroke_width=1, stroke_fill='#0a0e1c')
    return image


def export(atlas, medal_source, client, output, font_root):
    output.mkdir(parents=True, exist_ok=True)
    sources = output.parent / 'art' / 'exported'
    sources.mkdir(parents=True, exist_ok=True)
    art = Image.open(atlas).convert('RGBA')
    medals = Image.open(medal_source).convert('RGBA')
    if art.size != (1254, 1254) or medals.size != (1536, 1024):
        raise ValueError('These crop coordinates belong to the reviewed source atlases')
    regular = lambda size: ImageFont.truetype(str(font_root / 'georgia.ttf'), size)
    bold = lambda size: ImageFont.truetype(str(font_root / 'georgiab.ttf'), size)
    records = {}
    images = {}

    def save(name, image, texture_size):
        images[name] = image
        image.save(sources / (name + '.png'))
        target = output / (name + '.dds')
        image.resize(texture_size, Image.Resampling.LANCZOS).save(target, format='DDS', pixel_format='DXT5')
        records[target.name] = hashlib.sha256(target.read_bytes()).hexdigest()

    save('panel', art.crop((14, 0, 675, 1245)), (512, 1024))
    save('title', label('Companion recruited', (700, 104), regular(53), '#f1dbff'), (1024, 128))
    save('row', art.crop((670, 718, 1244, 818)), (512, 128))
    save('gear-icon', art.crop((678, 820, 953, 1114)), (128, 128))
    save('rank-icon', art.crop((951, 819, 1243, 1113)), (128, 128))
    divider = art.crop((699, 1091, 1220, 1241))
    # The atlas's neighbouring medal tips enter this crop. Select the
    # divider's connected alpha island so those separate sprites stay out.
    islands, count = components(np.asarray(divider.getchannel('A')) > 0)
    if count:
        sizes = np.bincount(islands.ravel());sizes[0] = 0
        alpha = np.asarray(divider.getchannel('A')).copy()
        alpha[islands != sizes.argmax()] = 0
        divider.putalpha(Image.fromarray(alpha))
    save('divider', divider, (512, 128))
    save('gear-label', label('Auto-equip', (440, 64), regular(39), centre=False), (512, 64))
    save('rank-label', label('Combat rank', (440, 64), regular(39), centre=False), (512, 64))
    for name, colour in VALUES.items():
        save('value-' + name, label(name.title(), (460, 76), bold(47), colour, centre=False), (512, 128))
    note = label('Your journey together begins.', (680, 140), regular(32), '#c7d5e9')
    save('note', note, (1024, 256))
    buttons = [art.crop(rect) for rect in [(679, 70, 1243, 215), (678, 229, 1243, 361), (679, 382, 1242, 508)]]
    for state, button in enumerate(buttons):
        for clip, text, size, text_size in [('continue', 'Continue', (680, 132), 47), ('skip', 'Skip reveal', (380, 88), 29)]:
            image = button.resize(size, Image.Resampling.LANCZOS)
            image.alpha_composite(label(text, size, regular(text_size), '#fff2d3'))
            save(f'{clip}-{state}', image, (1024, 256) if clip == 'continue' else (512, 128))

    # Use the actual native advanced-class symbols, not an invented emblem or
    # a generic portrait that could misrepresent the recruited character.
    with read_pak(client / 'Data/ui/ui.pak') as archive:
        data = archive.read('UI_Preload.xml')
        tree = binary_xml(data) if data[0] == 0x80 else E.fromstring(data)
    skins = {node.get('name'): node for node in tree.iter('Skin')}
    with read_pak(client / 'Textures/ui/ui.pak') as archive:
        names = {Path(n).stem.lower(): n for n in archive.namelist()}
        textures = {}
        for key, (title, skin) in CLASSES.items():
            save('class-' + key, label(title, (470, 80), regular(44), '#becfe7', centre=False), (512, 128))
            node = skins['v5_class_' + skin]
            texture = Path(node.get('texture')).name.lower()
            if texture not in textures:
                textures[texture] = Image.open(io.BytesIO(archive.read(names[texture]))).convert('RGBA')
            x, y, width, height = map(int, node.get('src_image').split(','))
            emblem = textures[texture].crop((x, y, x + width, y + height)).resize((102, 102), Image.Resampling.LANCZOS)
            medal = medals.crop((390, 264, 1145, 1009)).resize((256, 256), Image.Resampling.LANCZOS)
            medal.alpha_composite(emblem, (77, 77))
            save('medal-' + key, medal, (256, 256))
    (output / 'ui-manifest.json').write_text(json.dumps(dict(
        sources={str(p.name): hashlib.sha256(p.read_bytes()).hexdigest() for p in (atlas, medal_source)},
        font='Georgia regular/bold; native dynamic name: credit_team (active enu Vera 28px, white, shadow)',
        textures=records), indent=2) + '\n')
    # Offline composition proof. Deliberately no substitute character: model
    # transparency, feet and native rendering remain owner in-game checks.
    return images


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--atlas', type=Path)
    parser.add_argument('--medal-source', type=Path)
    parser.add_argument('--client', type=Path)
    parser.add_argument('--panel-in-only', action='store_true')
    parser.add_argument('--panel-source', type=Path, help='Existing authored panel PNG/DDS for silhouette-matched entrance bloom')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--font-root', type=Path, default=Path('C:/Windows/Fonts'))
    args = parser.parse_args()
    if args.panel_in_only:
        print('Exported',len(panel_entrance(args.output,args.panel_source)),'finite native result entrance frames to',args.output)
        return
    if not all((args.atlas,args.medal_source,args.client)):
        parser.error('--atlas, --medal-source and --client are required without --panel-in-only')
    images = export(args.atlas, args.medal_source, args.client, args.output, args.font_root)
    panel_entrance(args.output,images['panel'])
    print('Exported', len(images), 'native DXT5 UI textures to', args.output)


if __name__ == '__main__':
    main()
