"""One passive startup canvas for the bounded, runtime-placed unit-frame pool."""
import re
import xml.etree.ElementTree as E

PREFIX = 'Textures/UI/AetherfallUnitFrames'
FRAMES = ('Player', 'Target', 'TargetTarget') + tuple(f'Party{i}' for i in range(1, 7))
IMAGES = ('Backdrop', 'HealthBG', 'HealthFill', 'PowerBG', 'PowerFill', 'DPBG', 'DPFill')
STARS = tuple(f'Star{i}' for i in range(1, 6))  # bot rank; the game fonts have no star glyph
BORDERED = ('Backdrop', 'HealthBG', 'PowerBG', 'DPBG')
EDGES = ('Top', 'Bottom', 'Left', 'Right')
TEXT = tuple(resource + 'Text' + align for resource in ('Health', 'Power')
             for align in ('Left', 'Centre', 'Right')) + ('Level', 'Identity', 'State', 'DPTextCentre')


def assets():
    """RGB444 solid fills: the framework has image/alpha setters, but no RGB tint.

    Runtime selects each channel's nearest nibble. Opacity uses SetAlpha instead
    of duplicating the palette for each alpha. No client artwork is copied.
    """
    from options_skin import dds
    result = {}
    for value in range(0x1000):
        colour = ((value >> 8) * 17, ((value >> 4) & 15) * 17, (value & 15) * 17, 255)
        result[f'{PREFIX}/solid-{value:03x}.dds'] = dds(4, 4, lambda x, y, c=colour: c)
    from layout.rank_stars import assets as rank_stars  # earned and empty rank stars
    result.update(rank_stars(PREFIX))
    return result


def dialog():
    """All decorative controls are direct dialog children and never capture input."""
    # Same attributes as the 1.2.94 bot label window AFHIdentity, which drew over the native
    # frames in game; 1.4.0 added the pet-command preset and nothing on it showed.
    root = E.Element('Dialog', name='AFHFrames', frame='0,0,3840,2160',
                     flag='transparent;not_visible;not_movable;not_active')
    for frame in FRAMES:
        prefix = 'AFHF' + frame
        for part in IMAGES + STARS:
            E.SubElement(root, 'Widget', type='image', name=prefix + part,
                         frame='0,0,1,1', flag='not_active;not_visible')
        # AF.Skin.Border resolves these siblings by the owning image's name.
        for part in BORDERED:
            for edge in EDGES:
                E.SubElement(root, 'Widget', type='image', name=prefix + part + 'Edge' + edge,
                             frame='0,0,1,1', flag='not_active;not_visible')
        for part in TEXT:
            align = 'center' if part.endswith('Centre') else 'right' if part.endswith('Right') else 'left'
            E.SubElement(root, 'Widget', type='static', name=prefix + part,
                         frame='0,0,1,1', font='ui_default', HAlign=align,
                         flag='not_active;not_visible;truncate;not_multiline;not_wordwrap')
    return root


def update_xml(payload):
    """Reconcile only AFHFrames and the Screen count, retaining every other byte."""
    parsed = E.fromstring(payload)
    if parsed.tag != 'Screen' or payload.count(b'</Screen>') != 1:
        raise ValueError('Unexpected HUD XML for unit frames')
    count = sum(1 for node in parsed.iter('Dialog') if node.get('name') == 'AFHFrames')
    pattern = rb'<Dialog\s+[^>]*\bname=["\']AFHFrames["\'][^>]*?(?:/\s*>|>.*?</Dialog>)'
    matches = list(re.finditer(pattern, payload, re.S))
    if count != len(matches) or count > 1:
        raise ValueError('Unexpected or duplicate AFHFrames dialog')
    replacement = E.tostring(dialog(), encoding='utf-8')
    if matches:
        updated = re.sub(pattern, lambda _: replacement, payload, count=1, flags=re.S)
    else:
        updated = payload.replace(b'</Screen>', replacement + b'\n</Screen>')
    total = sum(1 for node in E.fromstring(updated).iter() if node.tag in ('Dialog', 'Widget'))
    if not re.search(rb'<Screen\b[^>]*\bwidget_cnt=["\']\d+["\']', updated):
        raise ValueError('HUD XML has no widget count')
    return re.sub(rb'(<Screen\b[^>]*\bwidget_cnt=)["\']\d+["\']',
                  lambda match: match[1] + b'"' + str(total).encode() + b'"', updated, count=1)
