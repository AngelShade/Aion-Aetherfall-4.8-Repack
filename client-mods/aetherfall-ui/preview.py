"""Offline previewer for native Aion 4.8 windows.

Draws dialog layouts (binary XML from the English override and HUD archives) with
the client's skin library and UI sheets, so interface designs can be reviewed as
images before anything is installed. Approximate: alignment rules, fonts, and
widget-specific drawing (gauges, item icons, 3D portraits) are simplified.

    python preview.py --client <client> --output <folder> [--sheets <stage ui.pak>] [--hud s1|s2]
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import dds  # noqa: E402
import library  # noqa: E402

codec = library.codec
REF_W, REF_H = 1280, 960
FONT_PATH = Path('C:/Windows/Fonts/tahoma.ttf')
HUD = {
    's1': ['ui/game_hud_s1/basic_status_dialog.xml', 'ui/game_hud_s1/quickbar_dialog.xml', 'ui/game_hud_s1/exp_bar_dialog.xml',
           'ui/game_hud_s1/radar_dialog.xml', 'ui/game_hud_s1/start_dialog.xml'],
    's2': ['ui/game_hud_s2/basic_status_dialog.xml', 'ui/game_hud_s2/quickbar_dialog.xml', 'ui/game_hud_s2/exp_bar_dialog.xml',
           'ui/game_hud_s2/radar_dialog.xml', 'ui/game_hud_s2/start_dialog.xml'],
}
COMMON_HUD = ['ui/game/target_dialog.xml', 'ui/game/party_dialog.xml', 'ui/game/chat_dialog.xml',
              'ui/game/quickbar_dialog_ctrl.xml', 'ui/game/quickbar_dialog_alt.xml']


class Library:
    def __init__(self, client, sheets_pak=None):
        root = library.load(client)['l10n']
        self.skins = {e.get('name'): e for e in root.iter('Skin')}
        self.presets = {e.get('name'): e for e in root.iter('Preset')}
        self.client = Path(client)
        self.pak = codec.read_pak(sheets_pak or self.client / library.SHEETS)
        self.sheets = {}

    def sheet(self, texture):
        name = library.sheet_name(texture)
        if name not in self.sheets:
            image = None
            info = library.pak_entry(self.pak, name + '.dds')
            if info is not None:
                raw = self.pak.read(info)
                if dds.is_bgra32(raw):
                    image = dds.to_image(raw)
            else:
                loose = self.client / (texture.replace(chr(92), '/') + '.dds')
                if loose.exists() and dds.is_bgra32(loose.read_bytes()):
                    image = dds.to_image(loose.read_bytes())
            self.sheets[name] = image
        return self.sheets[name]

    def preset_skin(self, name, prefer=('frame', 'back', 'up', 'normal')):
        preset = self.presets.get(name)
        if preset is None:
            return self.skins.get(name)
        refs = list(preset.iter('SkinRef'))
        for state in prefer:
            for ref in refs:
                if ref.get('state') == state or (ref.get('sub_state') == state and ref.get('main_state', '0') == '0'):
                    return self.skins.get(ref.get('name'))
        return self.skins.get(refs[0].get('name')) if refs else None


def pair(value):
    a, b = (int(v) for v in value.split(','))
    return a, b


def draw_skin(canvas, lib, skin, x, y, w, h):
    if skin is None or w <= 0 or h <= 0:
        return
    sheet = lib.sheet(skin.get('texture', ''))
    if sheet is None:
        return

    def piece(key, box):
        src = skin.get('src_' + key)
        if not src or box[2] <= 0 or box[3] <= 0:
            return
        sx, sy, sw, sh = (int(v) for v in src.split(','))
        if sw <= 0 or sh <= 0:
            return
        part = sheet.crop((sx, sy, sx + sw, sy + sh)).resize((int(box[2]), int(box[3])), Image.NEAREST)
        canvas.alpha_composite(part, (int(box[0]), int(box[1])))

    if skin.get('type') == 'tiled':
        def size(key):
            value = skin.get('size_' + key)
            return pair(value) if value else (0, 0)
        left = max(size('left_top')[0], size('left_middle')[0], size('left_bottom')[0])
        right = max(size('right_top')[0], size('right_middle')[0], size('right_bottom')[0])
        top = max(size('left_top')[1], size('middle_top')[1], size('right_top')[1])
        bottom = max(size('left_bottom')[1], size('middle_bottom')[1], size('right_bottom')[1])
        mid_w, mid_h = max(0, w - left - right), max(0, h - top - bottom)
        cols = {'left': (x, left), 'middle': (x + left, mid_w), 'right': (x + left + mid_w, right)}
        rows = {'top': (y, top), 'middle': (y + top, mid_h), 'bottom': (y + top + mid_h, bottom)}
        for c, (cx, cw) in cols.items():
            for r, (ry, rh) in rows.items():
                piece(f'{c}_{r}', (cx, ry, cw, rh))
    else:
        piece('image', (x, y, w, h))


def colour(value, default=(223, 233, 240, 255)):
    try:
        parts = [float(v) for v in value.split(',')]
        return tuple(int(max(0, min(1, v)) * 255) for v in (parts + [1, 1, 1, 1])[:4])
    except (AttributeError, ValueError):
        return default


_fonts = {}


def font(size):
    if size not in _fonts:
        _fonts[size] = ImageFont.truetype(str(FONT_PATH), size) if FONT_PATH.exists() else ImageFont.load_default()
    return _fonts[size]


def draw_widget(canvas, lib, node, ox, oy, depth=0):
    frame = node.get('frame')
    if not frame:
        return
    x, y, w, h = (int(float(v)) for v in frame.split(','))
    x, y = ox + x, oy + y
    if 'not_visible' in (node.get('flag') or '') and depth > 0:
        return
    kind = (node.get('type') or node.tag).lower()
    if node.get('preset'):
        prefer = ('frame', 'back', 'up', 'normal')
        if kind == 'gauge':
            prefer = ('back', 'bar', 'frame', 'up')
        draw_skin(canvas, lib, lib.preset_skin(node.get('preset'), prefer), x, y, w, h)
        if kind == 'gauge':
            draw_skin(canvas, lib, lib.preset_skin(node.get('preset'), ('bar', 'fill', 'gauge', 'front')), x, y, int(w * 0.72), h)
    for skin in node.findall('Skin'):
        draw_skin(canvas, lib, lib.skins.get(skin.get('name')), x, y, w, h)
    if kind in ('itemview',) and not node.get('preset'):
        ImageDraw.Draw(canvas).rectangle((x, y, x + w - 1, y + h - 1), outline=(80, 96, 110, 255))
    text = node.get('text')
    if text and kind in ('static', 'button', 'dialog', 'checkbox', 'radiobutton', 'editbox'):
        size = abs(int(node.get('font_size') or 0)) or (13 if kind == 'dialog' else 12)
        draw = ImageDraw.Draw(canvas)
        fill = colour(node.get('text_color'))
        tw = draw.textlength(text, font=font(size))
        tx = x + (w - tw) / 2 if node.get('halign') == 'center' or kind == 'button' else x + 4
        ty = y + 3 if kind == 'dialog' else y + max(0, (h - size) / 2 - 1)
        draw.text((tx, ty), text, font=font(size), fill=fill)
    for child in node:
        if child.tag in ('Widget', 'Dialog'):
            draw_widget(canvas, lib, child, x, y, depth + 1)


def dialog_root(archive, name):
    info = library.pak_entry(archive, name)
    return codec.binary_xml(archive.read(info)) if info is not None else None


def render_dialog(lib, root, scale=1.0):
    x, y, w, h = (int(float(v)) for v in root.get('frame').split(','))
    canvas = Image.new('RGBA', (max(1, w), max(1, h)), (0, 0, 0, 0))
    draw_widget(canvas, lib, root, -x, -y)
    return canvas


def render_hud(lib, archive, names, background):
    canvas = background.copy() if background else Image.new('RGBA', (REF_W, REF_H), (38, 52, 44, 255))
    for name in names:
        root = dialog_root(archive, name)
        if root is not None and root.get('frame'):
            draw_widget(canvas, lib, root, 0, 0)
    return canvas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sheets', type=Path, help='alternative Textures/ui/ui.pak (e.g. a staged design)')
    parser.add_argument('--hud', choices=['s1', 's2'], default='s1')
    parser.add_argument('--dialogs', nargs='*', default=['ui/game/inventory_dialog.xml', 'ui/game/player_info_dialog.xml',
                                                          'ui/game/skill_dialog.xml', 'ui/game/quest_dialog.xml', 'ui/game/system_menu_dialog.xml'])
    args = parser.parse_args()
    lib = Library(args.client, args.sheets)
    archive = codec.read_pak(args.client / library.L10N_DATA)
    args.output.mkdir(parents=True, exist_ok=True)
    hud = render_hud(lib, archive, HUD[args.hud] + COMMON_HUD, None)
    hud.convert('RGB').save(args.output / f'hud_{args.hud}.png')
    for name in args.dialogs:
        root = dialog_root(archive, name)
        if root is None or not root.get('frame'):
            print('missing', name)
            continue
        image = render_dialog(lib, root)
        bg = Image.new('RGBA', image.size, (38, 52, 44, 255))
        bg.alpha_composite(image)
        bg.convert('RGB').save(args.output / (Path(name).stem + '.png'))
    print('rendered to', args.output)


if __name__ == '__main__':
    main()
