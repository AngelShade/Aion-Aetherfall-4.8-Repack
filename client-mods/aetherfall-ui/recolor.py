"""Aetherfall colour transforms for Aion UI art.

Only near-neutral pixels (Aion's dark glass and steel) and, for controls, Aion's
blue accents are remapped. Saturated art (icons, gauge fills, warnings) keeps its
colour. Alpha is never changed.
"""
import json
import re
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent


def _hex(value):
    value = value.lstrip('#')
    return [int(value[i:i + 2], 16) for i in (0, 2, 4)]


# Luminance -> colour ramps built from docs/AETHERFALL_UI_STYLE.md tokens.
ICE = [(0.00, '#03070d'), (0.10, '#081326'), (0.20, '#0d1d33'), (0.32, '#172e4c'), (0.46, '#2e4a61'),
       (0.62, '#6f889a'), (0.78, '#a9bfcd'), (0.90, '#dfe9f0'), (1.00, '#f6fafc')]
GOLD = [(0.00, '#03070d'), (0.10, '#081326'), (0.20, '#0d1d33'), (0.32, '#172e4c'), (0.46, '#2e4a61'),
        (0.60, '#8a6d3c'), (0.74, '#c4a463'), (0.88, '#f1dca8'), (1.00, '#fff6dc')]
AETHER = [(0.00, '#020a10'), (0.18, '#0a2a3a'), (0.38, '#1c5a72'), (0.55, '#3a9fbe'), (0.72, '#8fe3ec'),
          (0.88, '#c9f5f8'), (1.00, '#f2feff')]


def ramp(stops):
    xs = np.array([s[0] for s in stops], dtype=np.float32)
    cs = np.array([_hex(s[1]) for s in stops], dtype=np.float32)
    lut = np.empty((256, 3), dtype=np.float32)
    grid = np.linspace(0, 1, 256, dtype=np.float32)
    for c in range(3):
        lut[:, c] = np.interp(grid, xs, cs[:, c])
    return lut


LUT = {'ice': ramp(ICE), 'gold': ramp(GOLD), 'aether': ramp(AETHER)}

# role -> (neutral ramp, gold weight for bright neutrals, blue accents -> aether)
ROLES = {
    'frame': dict(neutral='ice', gold=0.0, blue=True),
    'window': dict(neutral='ice', gold=1.0, blue=True),
    'control': dict(neutral='ice', gold=0.0, blue=True),
    'slot': dict(neutral='ice', gold=0.35, blue=True),
    'gauge': dict(neutral='ice', gold=0.0, blue=False),
}


def transform(rgba, role):
    """Return a recoloured copy of an RGBA uint8 array for the given role."""
    spec = ROLES[role]
    out = rgba.copy()
    rgb = rgba[..., :3].astype(np.float32)
    mx, mn = rgb.max(-1), rgb.min(-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1), 0)
    lum = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2])
    # Contrast lift so Aion's mid-grey glass reads as navy and bevels as metal.
    index = np.clip(lum, 0, 255).astype(np.uint8)
    neutral_w = np.clip(1 - (sat - 0.12) / 0.18, 0, 1)
    neutral_w = np.maximum(neutral_w, np.clip(1 - mx / 40, 0, 1))  # near-black counts as neutral
    target = LUT[spec['neutral']][index]
    if spec['gold']:
        bright = np.clip((lum - 120) / 90, 0, 1)[..., None] * spec['gold']
        target = target * (1 - bright) + LUT['gold'][index] * bright
    result = rgb * (1 - neutral_w[..., None]) + target * neutral_w[..., None]
    if spec['blue']:
        r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        hue = _hue(r, g, b, mx, mn)
        blue_w = np.clip((sat - 0.15) / 0.15, 0, 1) * np.clip(1 - np.abs(hue - 205) / 30, 0, 1)
        result = result * (1 - blue_w[..., None]) + LUT['aether'][np.clip(lum * 1.15, 0, 255).astype(np.uint8)] * blue_w[..., None]
    out[..., :3] = np.clip(result + 0.5, 0, 255).astype(np.uint8)
    return out


def _hue(r, g, b, mx, mn):
    d = np.maximum(mx - mn, 1e-6)
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4))
    return h * 60


def load_rules(path=HERE / 'roles.json'):
    data = json.loads(Path(path).read_text(encoding='utf-8'))
    rules = []
    for rule in data['rules']:
        rules.append(dict(rule, path_re=re.compile(rule['path']) if 'path' in rule else None,
                          name_re=re.compile(rule['name']) if 'name' in rule else None))
    return data, rules


def classify(skin, rules, default='keep'):
    path = '/'.join(skin['path'])
    kinds = set(skin['kinds'])
    for rule in rules:
        if rule['path_re'] and not rule['path_re'].search(path):
            continue
        if rule['name_re'] and not rule['name_re'].search(skin['name']):
            continue
        if 'kinds' in rule and not kinds & set(rule['kinds']):
            continue
        if 'except_kinds' in rule and kinds & set(rule['except_kinds']):
            continue
        return rule['role']
    return default


PRIORITY = {'keep': 5, 'gauge': 4, 'slot': 3, 'control': 2, 'window': 1, 'frame': 0}


def role_map(skins, sheet, size, rules, default='keep'):
    """Per-pixel role index for one sheet. Overlaps resolve to the most conservative role."""
    names = list(PRIORITY)
    grid = np.full((size[1], size[0]), -1, dtype=np.int8)
    level = np.full((size[1], size[0]), -1, dtype=np.int8)
    for skin in skins:
        if skin['sheet'] != sheet:
            continue
        role = classify(skin, rules, default)
        for x, y, w, h in skin['rects']:
            x2, y2 = min(x + w, size[0]), min(y + h, size[1])
            if x >= x2 or y >= y2:
                continue
            higher = level[y:y2, x:x2] < PRIORITY[role]
            grid[y:y2, x:x2][higher] = names.index(role)
            level[y:y2, x:x2][higher] = PRIORITY[role]
    return grid, names


def apply(rgba, grid, names):
    out = rgba.copy()
    for index, role in enumerate(names):
        if role == 'keep':
            continue
        mask = grid == index
        if mask.any():
            ys, xs = np.nonzero(mask)
            y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
            block = transform(rgba[y0:y1, x0:x1], role)
            region = mask[y0:y1, x0:x1]
            out[y0:y1, x0:x1][region] = block[region]
    return out
