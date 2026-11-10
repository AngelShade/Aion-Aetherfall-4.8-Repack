"""Draw an options page from the offline harness as a wireframe PNG (layout check, not a screenshot).

    python preview.py --client <client> --page Profiles --tab Profile --output page.png
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


def main():
    from PIL import Image, ImageDraw
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--page', required=True)
    parser.add_argument('--tab')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
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
