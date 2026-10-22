"""Build the Aetherfall Interface addon from the currently installed client.

Stages `Mods/AetherfallUI`: content-addressed payloads for every addon-owned
archive entry and loose file in each state (off = installed, on = skin + text
colours, layout = on + target frame above the action bars), a manifest with their
hashes and the installed archive hashes, and review contact sheets. Each state is
applied to a copy and verified. Nothing in the client is written; the launcher
(`--aetherfall-interface`) applies a state entry by entry.
"""
import argparse
import copy
import gzip
import hashlib
import io
import json
import shutil
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import dds  # noqa: E402
import library  # noqa: E402
import recolor  # noqa: E402

codec = library.codec
VERSION = '1.0.0'

SHEETS_PAK = 'Textures/ui/ui.pak'
L10N_PAK = 'L10N/enu/data/data.pak'
GAME_PAK = 'Data/ui/game/game.pak'
HUD_PAKS = ['Data/ui/game_hud_s1/game_hud_s1.pak', 'Data/ui/game_hud_s2/game_hud_s2.pak']
WARDROBE_DIR = 'Textures/ui/WardrobeNative'

# Text colours: (attribute, original RGBA) -> Aetherfall RGBA. Everything else stays.
ICE = '0.875,0.914,0.941,1.0'
TEXT_COLOURS = {
    ('text_color', '0.95,1.0,0.90,1.0'): ICE,
    ('text_over_color', '0.95,1.0,0.90,1.0'): '0.788,0.941,0.961,1.0',
    ('text_selected_color', '0.95,1.0,0.90,1.0'): '0.561,0.890,0.925,1.0',
    ('text_color', '0.66,0.72,0.72,1.0'): '0.576,0.671,0.737,1.0',
    ('text_color', '0.66,0.72,0.72,0.7'): '0.576,0.671,0.737,0.7',
    ('text_selected_color', '0.64,1.0,0.6,1.0'): '0.561,0.890,0.925,1.0',
    ('text_color', '0.9176,0.9137,0.4588,1.0'): '0.945,0.863,0.659,1.0',
    ('text_selected_color', '0.95,0.95,0,1.0'): '0.839,0.722,0.471,1.0',
    ('text_color', '0.92,0.92,0.92,1.0'): ICE,
    ('text_color', '0.93,0.93,0.87,1.0'): '0.957,0.910,0.784,1.0',
    ('text_color', '0.78,0.78,0.78,1.0'): '0.663,0.749,0.804,1.0',
    ('text_over_color', '0.45,0.90,0.95,1.0'): '0.561,0.890,0.925,1.0',
    ('text_selected_color', '0.37,0.78,0.90,1.0'): '0.227,0.624,0.745,1.0',
}

# Layout preset: target cluster moves from the top edge to just above the action bars.
# Reference space is 960 high; bottom-aligned dialogs keep their distance to the bottom.
TARGET_SHIFT = 764
LAYOUT_DIALOGS = ['target_dialog.xml', 'target_assist_dialog.xml', 'target_buff_status_dialog.xml']

# Native Wardrobe surfaces (loose DDS owned by the transmog mod), same geometry.
WARDROBE = {
    'window': ('#0c1826', '#070d16', None),
    'panel': ('#0e1b29', '#0a1521', '#2e4a61'),
    'preview': ('#0e1b29', '#08111c', '#8a6d3c'),
    'field': ('#08121d', '#08121d', '#2e4a61'),
    'card': ('#142538', '#0e1b29', '#2e4a61'),
    'card_over': ('#1c3550', '#13263a', '#3a9fbe'),
    'card_selected': ('#15304a', '#102438', '#8fe3ec'),
    'button': ('#1f3850', '#11202f', '#46708d'),
    'button_over': ('#28496a', '#16293c', '#8fe3ec'),
    'button_down': ('#0e1b29', '#13263a', '#3a9fbe'),
    'button_selected': ('#15304a', '#102438', '#8fe3ec'),
    'button_disabled': ('#0d1823', '#0a1521', '#1f3447'),
    'primary': ('#b18f4f', '#5f4824', '#f1dca8'),
    'primary_over': ('#c9a660', '#725a2e', '#fff0c4'),
    'primary_down': ('#5f4824', '#7a5f30', '#d6b878'),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def find(zf, name):
    info = library.pak_entry(zf, name)
    if info is None:
        raise ValueError(f'missing archive entry {name}')
    return info


def verify_pak(path, original, replacements):
    """Every untouched entry must be byte-identical; replaced entries must match."""
    with codec.read_pak(path) as staged:
        if staged.testzip() is not None or staged.namelist() != original.namelist():
            raise ValueError(f'{path}: CRC or entry order changed')
        for info in original.infolist():
            expected = replacements.get(info.filename)
            actual = staged.read(info.filename)
            if expected is None:
                if actual != original.read(info):
                    raise ValueError(f'{path}: untouched entry changed {info.filename}')
            elif actual != expected:
                raise ValueError(f'{path}: replaced entry mismatch {info.filename}')


# ---------- skin ----------

def build_skin(client, review):
    roots = library.load(client)
    skins = library.union(roots)
    config, rules = recolor.load_rules()
    include = set(config['sheets']['include'])
    never = set(config['sheets']['never'])
    source = codec.read_pak(client / SHEETS_PAK)
    replacements, report = {}, {}
    for info in source.infolist():
        sheet = info.filename.rsplit('.', 1)[0].lower()
        if sheet not in include or sheet in never:
            continue
        raw = source.read(info)
        if not dds.is_bgra32(raw):
            report[sheet] = 'skipped: not uncompressed BGRA'
            continue
        image = dds.to_image(raw)
        before = np.array(image)
        grid, names = recolor.role_map(skins, sheet, image.size, rules, config.get('default', 'keep'))
        after = recolor.apply(before, grid, names)
        hand_art(sheet, after, skins)
        if not np.array_equal(before[..., 3], after[..., 3]):
            raise ValueError(f'{sheet}: alpha changed')
        new = dds.replace_pixels(raw, Image.fromarray(after))
        if len(new) != len(raw) or new[:128] != raw[:128]:
            raise ValueError(f'{sheet}: header or size changed')
        if new != raw:
            replacements[info.filename] = new
            changed = int((before != after).any(-1).sum())
            report[sheet] = f'{changed} pixels recoloured'
            contact(review / f'skin_{sheet}.png', before, after)
    counts = {}
    for skin in skins:
        role = recolor.classify(skin, rules, config.get('default', 'keep'))
        counts[role] = counts.get(role, 0) + 1
    return source, replacements, dict(sheets=report, roles=counts)


def contact(path, before, after):
    def flat(a):
        bg = Image.new('RGBA', (a.shape[1], a.shape[0]), (40, 40, 40, 255))
        bg.alpha_composite(Image.fromarray(a))
        return bg
    w, h = before.shape[1], before.shape[0]
    sheet = Image.new('RGBA', (w * 2, h))
    sheet.paste(flat(before), (0, 0))
    sheet.paste(flat(after), (w, 0))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert('RGB').save(path, optimize=True)


GOLD_RULE = np.array([0xd6, 0xb8, 0x78], dtype=np.float32)
GOLD_DEEP = np.array([0x8a, 0x6d, 0x3c], dtype=np.float32)
AETHER = np.array([0x8f, 0xe3, 0xec], dtype=np.float32)


def hand_art(sheet, pixels, skins):
    """Aetherfall accents drawn inside existing window/tooltip art only.

    The outermost opaque row of each window's top edge receives a gold rule
    (tooltips: an aether hairline) and the bottom edge a gold-deep rule. For
    nine-slice skins the row is taken from the middle piece and applied to the
    matching corner pieces so the rule is continuous. Only RGB of opaque pixels
    changes; alpha and geometry are untouched.
    """
    for skin in skins:
        if skin['sheet'] != sheet or not set(skin['kinds']) & {'window', 'dialog'}:
            continue
        accent = AETHER if 'tooltip' in skin['name'].lower() else GOLD_RULE
        keyed = skin['parts']
        if 'src_middle_top' in keyed:
            groups = [(('src_middle_top', 'src_left_top', 'src_right_top'), True, accent, 0.75),
                      (('src_middle_bottom', 'src_left_bottom', 'src_right_bottom'), False, GOLD_DEEP, 0.6)]
        elif 'src_image' in keyed:
            groups = [(('src_image',), True, accent, 0.75), (('src_image',), False, GOLD_DEEP, 0.6)]
        else:
            continue
        for keys, top, colour, strength in groups:
            pieces = [keyed[k] for k in keys if k in keyed]
            if not pieces or keys[0] not in keyed:
                continue
            x, y, w, h = pieces[0]
            if h < 4:
                continue
            alpha = pixels[y:y + h, x:x + w, 3] > 200
            row = _edge_row(alpha, top)
            if row is None:
                continue
            offset = row if top else h - 1 - row
            for px, py, pw, ph in pieces:
                r = py + offset if top else py + ph - 1 - offset
                if not (py <= r < py + ph):
                    continue
                line = pixels[r, px:px + pw]
                mask = line[:, 3] > 200
                if keys == ('src_image',):
                    cols = np.nonzero(mask)[0]
                    if cols.size < 8:
                        continue
                    mask[cols[:3]] = False
                    mask[cols[-3:]] = False
                rgb = line[:, :3].astype(np.float32)
                rgb[mask] = rgb[mask] * (1 - strength) + colour * strength
                line[:, :3] = np.clip(rgb + 0.5, 0, 255).astype(np.uint8)


def _edge_row(alpha, top):
    rows = range(alpha.shape[0]) if top else range(alpha.shape[0] - 1, -1, -1)
    for row in rows:
        if alpha[row].sum() >= alpha.shape[1] * 0.6:
            return row
    return None


# ---------- text colours and layout ----------

def themed_xml(payload, layout_name=None):
    """Return (new payload or None, colour changes, layout changed)."""
    root = codec.binary_xml(payload)
    changes = 0
    for element in root.iter():
        for key, value in list(element.attrib.items()):
            new = TEXT_COLOURS.get((key, value))
            if new:
                element.set(key, new)
                changes += 1
    moved = False
    if layout_name and root.tag == 'Dialog' and root.get('v_pos_align') == 'top':
        x, y, w, h = (int(v) for v in root.get('frame').split(','))
        root.set('frame', f'{x},{y + TARGET_SHIFT},{w},{h}')
        root.set('v_pos_align', 'bottom')
        moved = True
    if not changes and not moved:
        return None, 0, False
    encoded = codec.encode_binary_xml(root)
    if codec.ET.tostring(codec.binary_xml(encoded)) != codec.ET.tostring(root):
        raise ValueError('binary XML round trip failed')
    return encoded, changes, moved


def build_xml(client, rel, prefix, layout):
    source = codec.read_pak(client / rel)
    replacements, colours, moved = {}, 0, []
    for info in source.infolist():
        lower = info.filename.lower().replace(chr(92), '/')
        if not lower.endswith('.xml') or not lower.startswith(prefix) or 'preload' in lower:
            continue
        base = lower.rsplit('/', 1)[-1]
        payload = source.read(info)
        try:
            new, count, did_move = themed_xml(payload, base if layout and base in LAYOUT_DIALOGS else None)
        except ValueError:
            raise
        except Exception:
            continue  # not binary XML (plain text or HTML payloads)
        if new is not None:
            replacements[info.filename] = new
            colours += count
            if did_move:
                moved.append(info.filename)
    return source, replacements, dict(colour_attributes=colours, entries=len(replacements), moved=moved)


# ---------- wardrobe ----------

def wardrobe_dds(original, top, bottom, border):
    h = dds.header(original)
    width, height = h['width'], h['height']
    t, b = np.array(_rgb(top), np.float32), np.array(_rgb(bottom), np.float32)
    ys = np.linspace(0, 1, height, dtype=np.float32)[:, None, None]
    pixels = np.repeat(t * (1 - ys) + b * ys, width, axis=1)
    if border:
        e = np.array(_rgb(border), np.float32)
        pixels[0, :], pixels[-1, :], pixels[:, 0], pixels[:, -1] = e, e, e, e
    rgba = np.concatenate([np.clip(pixels + 0.5, 0, 255).astype(np.uint8), np.full((height, width, 1), 255, np.uint8)], -1)
    return dds.replace_pixels(original, Image.fromarray(rgba))


def _rgb(value):
    return [int(value.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)]


# ---------- assemble ----------

def store(mod, data):
    """Content-addressed gzip payload; returns its SHA-256."""
    digest = sha(data)
    path = mod / 'payloads' / f'{digest}.gz'
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(gzip.compress(data, compresslevel=9, mtime=0))
    return digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True, help='fresh staging folder outside source and client')
    args = parser.parse_args()
    client, output = args.client.resolve(), args.output.resolve()
    for forbidden in (client, HERE.parents[1]):
        if output == forbidden or forbidden in output.parents:
            raise SystemExit('output must be outside the client and the source tree')
    if output.exists() and any(output.iterdir()):
        raise SystemExit('output must be a fresh, empty folder')
    mod = output / 'Mods' / 'AetherfallUI'
    review = output / 'review'
    mod.mkdir(parents=True)
    report = {}
    manifest = dict(name='Aetherfall Interface', version=VERSION, archives={}, files={}, sources={})

    def record(rel, source, on, layout):
        """Entries changed in either state, with off/on/layout payload hashes."""
        entries = {}
        for name in sorted(set(on) | set(layout)):
            original = source.read(name)
            entries[name] = dict(off=store(mod, original), on=store(mod, on.get(name, original)),
                                 layout=store(mod, layout.get(name, on.get(name, original))))
        if entries:
            manifest['archives'][rel] = dict(entries=entries)
            manifest['sources'][rel] = sha((client / rel).read_bytes())
        # Prove the launcher's work: apply each state in Python and verify the archive.
        for state in ('on', 'layout'):
            replacements = {n: gzip.decompress((mod / 'payloads' / f"{e[state]}.gz").read_bytes()) for n, e in entries.items()}
            if not replacements:
                continue
            plain = io.BytesIO()
            with zipfile.ZipFile(plain, 'w') as zf:
                for info in source.infolist():
                    zf.writestr(copy.copy(info), replacements.get(info.filename, source.read(info)))
            check = output / 'verify' / state / rel
            check.parent.mkdir(parents=True, exist_ok=True)
            check.write_bytes(bytes(codec.encode_pak(plain.getvalue())))
            verify_pak(check, source, replacements)
            check.unlink()

    sheets, skin, report['skin'] = build_skin(client, review)
    record(SHEETS_PAK, sheets, skin, skin)
    sheets.close()

    for rel, prefix in [(L10N_PAK, 'ui/'), (GAME_PAK, '')] + [(p, '') for p in HUD_PAKS]:
        source, on, report[rel + ' (on)'] = build_xml(client, rel, prefix, layout=False)
        source.close()
        source, layout, report[rel + ' (layout)'] = build_xml(client, rel, prefix, layout=True)
        record(rel, source, on, layout)
        source.close()

    wardrobe = client / WARDROBE_DIR
    if wardrobe.is_dir():
        for name, colours in WARDROBE.items():
            path = wardrobe / f'{name}.dds'
            if path.exists():
                original = path.read_bytes()
                themed = wardrobe_dds(original, *colours)
                manifest['files'][f'{WARDROBE_DIR}/{name}.dds'] = dict(off=store(mod, original), on=store(mod, themed), layout=store(mod, themed))

    shutil.rmtree(output / 'verify', ignore_errors=True)
    manifest['report'] = report
    (mod / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    (mod / 'state.json').write_text(json.dumps(dict(Desired='on', Applied='off', Stamps={})), encoding='utf-8')
    (mod / 'mod.json').write_text(json.dumps(dict(
        Title='Aetherfall Interface',
        Description="Aetherfall skin for the whole Aion interface with an optional target frame above the action bars. Switch it in the launcher's Mods window.",
        Listed=True, Files=[]), indent=2), encoding='utf-8')
    shutil.copy2(HERE / 'README.md', mod / 'README.md')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
