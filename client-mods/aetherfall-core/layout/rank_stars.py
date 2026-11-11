"""Small, baked-colour rank stars; no runtime tint or image library required.

32 px preserves the silhouette when bilinearly sampled at about 10 px. Both
states share one centred outline, with two clear texels of transparent padding.
All geometry and 8x8 coverage calculations use integers for reproducible DDSs.
"""
from options_skin import dds

SIZE = 32
_SAMPLES = 8
_GRID = SIZE * _SAMPLES * 2

# Authored on a 64-unit square: bounding box (4, 4)-(60, 60), centre (32, 32).
# Broad shoulders and an open lower notch remain legible at the display size.
_POINTS = ((32, 4), (40, 23), (60, 25), (45, 39), (49, 60),
           (32, 49), (15, 60), (19, 39), (4, 25), (24, 23))
_VERTICES = tuple((x * (_GRID // 64), y * (_GRID // 64)) for x, y in _POINTS)
_EDGES = tuple(zip(_VERTICES, _VERTICES[1:] + _VERTICES[:1]))

GOLD_HIGHLIGHT = (241, 220, 168)  # #f1dca8
GOLD = (230, 184, 92)            # #e6b85c
GOLD_SHADE = (214, 164, 79)      # #d6a44f
OUTLINE = (8, 14, 20)           # #080e14
EMPTY_OUTLINE = (92, 112, 130)   # #5c7082
EMPTY_FILL = (10, 18, 27)       # #0a121b, alpha 160


def _inside(x, y):
    hit = False
    for (ax, ay), (bx, by) in _EDGES:
        if (ay > y) != (by > y):
            cross = (bx - ax) * (y - ay) - (x - ax) * (by - ay)
            if (cross > 0) == (by > ay):
                hit = not hit
    return hit


def _near_edge(x, y, width):
    """Constant-width inner rim, including round joins at concave corners."""
    width2 = width * width
    for (ax, ay), (bx, by) in _EDGES:
        dx, dy = bx - ax, by - ay
        px, py = x - ax, y - ay
        length2 = dx * dx + dy * dy
        along = px * dx + py * dy
        if along <= 0:
            near = px * px + py * py <= width2
        elif along >= length2:
            near = (x - bx) ** 2 + (y - by) ** 2 <= width2
        else:
            near = (dx * py - dy * px) ** 2 <= width2 * length2
        if near:
            return True
    return False


def _gold(y):
    # Restrained vertical light, without a shiny centre or a blurred glow.
    if y < _GRID // 2:
        first, last = GOLD_HIGHLIGHT, GOLD
        weight = max(0, min(256, (y - _GRID // 8) * 256 // (3 * _GRID // 8)))
    else:
        first, last = GOLD, GOLD_SHADE
        weight = max(0, min(256, (y - _GRID // 2) * 256 // (3 * _GRID // 8)))
    return tuple((a * (256 - weight) + b * weight + 128) // 256
                 for a, b in zip(first, last))


def _texture(earned):
    edge = OUTLINE if earned else EMPTY_OUTLINE
    # About 0.31 / 0.61 display pixels: a dark gold rim / a legible hollow rim.
    stroke = 16 if earned else 31

    def pixel(x, y):
        red = green = blue = alpha = 0
        for sy in range(_SAMPLES):
            py = 2 * (y * _SAMPLES + sy) + 1
            fill = _gold(py) if earned else EMPTY_FILL
            for sx in range(_SAMPLES):
                px = 2 * (x * _SAMPLES + sx) + 1
                if not _inside(px, py):
                    continue
                rim = _near_edge(px, py, stroke)
                colour = edge if rim else fill
                a = 255 if earned else 232 if rim else 160
                red += colour[0] * a
                green += colour[1] * a
                blue += colour[2] * a
                alpha += a
        if not alpha:
            # Bleed the rim's RGB into clear padding for straight-alpha sampling.
            return edge + (0,)
        # Coverage is averaged in premultiplied space, then UNpremultiplied for
        # storage: partial-alpha texels retain their full, untinted RGB values.
        rgb = tuple((value + alpha // 2) // alpha for value in (red, green, blue))
        return rgb + ((alpha + _SAMPLES * _SAMPLES // 2) // (_SAMPLES * _SAMPLES),)

    return dds(SIZE, SIZE, pixel)


def assets(prefix):
    """Return the two uncompressed 32-bit BGRA DDS resources under ``prefix``."""
    return {f'{prefix}/star-on.dds': _texture(True),
            f'{prefix}/star-off.dds': _texture(False)}
