"""Draw options or unit frames as an offline wireframe PNG (not a game screenshot).

    python preview.py --client <client> --page Profiles --tab Profile --output page.png
    python preview.py --client <client> --frames --output unitframes.png
"""
import argparse
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'trace'))
sys.path.insert(0, str(HERE.parent))
from runtime import Recorder, Runtime  # noqa: E402

COLOURS = {'button': (20, 37, 56), 'field': (7, 13, 22), 'panel': (10, 21, 33)}


def frames_preview(client):
    from PIL import Image, ImageDraw
    from unitframes import UnitFrameRuntime, UNITS, sample
    recorder = Recorder(Path(tempfile.mkdtemp(prefix='aetherfall-frame-preview-')) / 'preview.jsonl.gz')
    runtime = UnitFrameRuntime(client, recorder)
    runtime.sw, runtime.sh, runtime.scale = 1920, 1080, 1
    runtime.character = 'PreviewCharacter'
    runtime.frame_units = {unit: sample(7001 + index, 'PreviewCharacter' if unit == 'player' else
                                       'Companion' + str(index), health=1000 - index * 65)
                           for index, unit in enumerate(UNITS)}
    for index in range(1, 7):
        runtime.labels[runtime.g.AFCore.DIALOG.party, 'ctn_member' + str(index) + '/title'] = runtime.frame_units['party' + str(index)][1]
    runtime.identities['Companion3'] = ('Excellent', 'Great')
    runtime.event('PLAYER_ENTERING_WORLD'); runtime.ticks(45)
    image = Image.new('RGB', (1920, 1080), (7, 13, 22))
    draw = ImageDraw.Draw(image)
    draw.text((20, 18), 'Aetherfall unit frames - offline wireframe', fill=(214, 184, 120))
    for name, widget in runtime.widgets.items():
        if not name.startswith('AFHF') or not widget['visible']:
            continue
        x, y, width, height = widget['rect']
        if width <= 0 or height <= 0:
            continue
        color = None
        if 'Edge' in name:
            color = (46, 74, 97)
        elif name.endswith('Backdrop'):
            color = (10, 21, 33)
        elif name.endswith('BG'):
            color = (20, 37, 56)
        elif name.endswith('Fill'):
            color = (94, 168, 131) if 'Health' in name else (83, 140, 188) if 'Power' in name else (214, 184, 120)
        if color:
            draw.rectangle((x, y, x + width - 1, y + height - 1), fill=color)
        value = str(widget.get('text') or '')
        if value:
            draw.text((x + 2, y + max(0, (height - 11) // 2)), value[:max(4, int(width // 6))],
                      fill=(223, 233, 240))
    recorder.close()
    return image


def main():
    from PIL import Image, ImageDraw
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    surface = parser.add_mutually_exclusive_group(required=True)
    surface.add_argument('--page')
    surface.add_argument('--frames', action='store_true', help='draw player, target, target of target and six party frames')
    parser.add_argument('--tab')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.frames:
        frames_preview(args.client).save(args.output)
        print(args.output)
        return
    runtime = Runtime(args.client, Recorder(Path(tempfile.mkdtemp()) / 'preview.jsonl.gz'), current_resources=True)
    runtime.sw, runtime.sh, runtime.scale = 1920, 1080, 1
    runtime.character = 'PreviewCharacter'
    runtime.event('PLAYER_ENTERING_WORLD'); runtime.ticks(45)
    runtime.slash('/afui'); runtime.ticks(5)
    call = lambda method, *a: runtime.g.AF.Call(runtime.g.AF, 'OptionsWindow', method, *a)
    call('Select', args.page, args.tab) if args.tab else call('Select', args.page)
    root = runtime.widgets['AFUIOptions']['rect']
    image = Image.new('RGB', (int(root[2]), int(root[3])), (7, 13, 22))
    draw = ImageDraw.Draw(image)
    for name, widget in runtime.widgets.items():
        if not name.startswith('AFUIOptions') or name == 'AFUIOptions' or not widget['visible']:
            continue
        x, y, w, h = widget['rect']
        if w <= 2 or h <= 2:
            if 'Edge' in name:
                draw.rectangle([x, y, x + max(w, 1) - 1, y + max(h, 1) - 1], fill=(46, 74, 97))
            continue
        short = name[len('AFUIOptions'):]
        is_button = any(key in short for key in ('Value', 'Apply', 'Tab', 'Category', 'Anchors', 'Reposition', 'Install', 'Search', 'Close', 'Less', 'More', 'Previous', 'Next', 'Drop', 'TitleDrag', 'Check'))
        is_field = short.endswith(('Edit', 'Hex', 'Code', 'Search'))
        if short in ('Backdrop',):
            continue
        if short in ('Sidebar', 'Content') or short.startswith('Group') and not short.endswith('Caption'):
            draw.rectangle([x, y, x + w - 1, y + h - 1], fill=COLOURS['panel'])
        elif is_field:
            draw.rectangle([x, y, x + w - 1, y + h - 1], fill=COLOURS['field'], outline=(46, 74, 97))
        elif is_button:
            draw.rectangle([x, y, x + w - 1, y + h - 1], fill=COLOURS['button'], outline=(46, 74, 97))
        text = str(widget.get('text') or '')
        if text:
            draw.text((x + 4, y + max(0, (h - 11) // 2)), text[:int(max(4, w // 6))], fill=(214, 184, 120) if 'Caption' in short or short == 'Header' else (223, 233, 240))
    image.save(args.output)
    print(args.output)


if __name__ == '__main__':
    main()
