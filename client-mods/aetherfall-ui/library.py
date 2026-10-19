"""Skin library reader: every skin rectangle with its category path and widget kinds."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
import codec  # noqa: E402

BASE_UI = 'Data/ui/ui.pak'
L10N_DATA = 'L10N/enu/data/data.pak'
SHEETS = 'Textures/ui/ui.pak'


def pak_entry(zf, name):
    lower = name.lower()
    for info in zf.infolist():
        if info.filename.lower() == lower:
            return info
    return None


def sheet_name(texture):
    return texture.replace(chr(92), '/').split('/')[-1].lower()


def parts(skin):
    """Source rectangles keyed by attribute: src_image and nine-slice src_* parts."""
    out = {}
    for key, value in skin.attrib.items():
        if key == 'src_image' or key.startswith('src_'):
            numbers = [int(v) for v in value.split(',')]
            if len(numbers) == 4 and numbers[2] > 0 and numbers[3] > 0:
                out[key] = tuple(numbers)
    return out


def rects(skin):
    return sorted(set(parts(skin).values()))


def skins(root):
    """List of dicts: name, path, sheet, rects, kinds (preset types referencing it)."""
    kinds = {}
    for preset in root.iter('Preset'):
        for ref in preset.iter('SkinRef'):
            kinds.setdefault(ref.get('name', ''), set()).add(preset.get('type') or '')
    out = []

    def walk(node, path):
        for child in node:
            if child.tag == 'Category':
                walk(child, path + [child.get('name', '')])
            elif child.tag == 'Skin' and child.get('texture'):
                keyed = parts(child)
                if keyed:
                    name = child.get('name', '')
                    out.append(dict(name=name, path=path, sheet=sheet_name(child.get('texture')), rects=sorted(set(keyed.values())), parts=keyed,
                                    type=child.get('type') or '', kinds=sorted(kinds.get(name, ()))))
            else:
                walk(child, path)
    walk(root, [])
    return out


def load(client):
    client = Path(client)
    base = codec.read_pak(client / BASE_UI)
    l10n = codec.read_pak(client / L10N_DATA)
    return {
        'base': codec.binary_xml(base.read(pak_entry(base, 'UI_Preload.xml'))),
        'l10n': codec.binary_xml(l10n.read(pak_entry(l10n, 'ui/ui_preload.xml'))),
    }


def union(roots):
    """Skins from both libraries, de-duplicated by name, sheet and rectangles."""
    seen, out = set(), []
    for root in roots.values():
        for skin in skins(root):
            key = (skin['name'], skin['sheet'], tuple(skin['rects']))
            if key not in seen:
                seen.add(key)
                out.append(skin)
    return out
