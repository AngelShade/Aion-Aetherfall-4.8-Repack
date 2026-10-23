"""Native MoveUI child widgets; one dialog, bounded grid, no additional windows."""
import re
import struct
import xml.etree.ElementTree as E

LINE_PATH = 'Textures/UI/AetherfallMovers/line.dds'
GRID_X, GRID_Y = 64, 40


def line_texture():
    # Opaque cyan texel surface, stretched into one physical pixel guide lines.
    header = [124, 0x100F, 4, 4, 16, 0, 0] + [0] * 11
    header += [32, 0x41, 0, 32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000, 0x1000, 0, 0, 0, 0]
    return b'DDS ' + struct.pack('<31I', *header) + bytes((225, 205, 120, 255)) * 16


def dialog(movers=56):
    root = E.Element('Dialog', name='AFUIMovers', frame='0,0,1280,960',
                     preset='v5_hud_pet_command_s1', flag='not_visible;not_movable')

    def widget(kind, name, frame, text=None, click=None, preset=None):
        attrs = dict(type=kind, name='AFUI' + name, frame=frame)
        if kind in ('static', 'button', 'editbox'):
            attrs['font'] = 'ui_default'
        if kind in ('static', 'image'):
            attrs['flag'] = 'not_active'
        if kind == 'static':
            attrs['flag'] += ';truncate;not_multiline;not_wordwrap'
        if kind == 'button':
            attrs.update(style='pushbutton', preset=preset or 'v5_button')
        elif preset:
            attrs['preset'] = preset
        if text:
            attrs['text'] = text
        node = E.SubElement(root, 'Widget', **attrs)
        if click:
            E.SubElement(E.SubElement(node, 'Scripts'), 'OnClick').text = "AFUI.Editor('%s');" % click
        return node

    for axis, count in (('X', GRID_X), ('Y', GRID_Y)):
        for i in range(1, count + 1):
            widget('image', f'Grid{axis}{i}', '0,0,1,1')
    for name in ('CenterX', 'CenterY'):
        widget('image', 'Guide' + name, '0,0,1,1')
    for i in range(1, movers + 1):
        widget('button', f'Move{i}', '0,0,120,30')
    # Passive captions and tethers preserve even the smallest native hit boxes.
    for i in range(1, movers + 1):
        widget('image', f'MoveLeader{i}', '0,0,1,1')
        widget('static', f'MoveLabel{i}', '0,0,120,20', preset='v5_staticbox')
    for i in range(1, 5):
        widget('image', f'Selection{i}', '0,0,1,1')

    # The panel is positioned as a set of queued child-widget operations by Lua.
    widget('image', 'EditorPanel', '0,0,380,278', preset='v5_staticbox')
    widget('static', 'EditorTitle', '10,4,158,22', 'MOVE UI  |  drag header')
    widget('button', 'EditorSave', '174,3,116,24', 'Save / Lock', 'Save')
    widget('button', 'EditorCancel', '296,3,74,24', 'Cancel', 'Cancel')
    widget('static', 'EditorProfile', '10,31,194,20')
    widget('button', 'EditorStep', '212,29,158,24', 'Snap step: 8 px', 'Step')
    for name, frame, text, action in (
        ('Grid', '10,55,74,24', 'Grid: On', 'Grid'),
        ('Less', '88,55,24,24', '-', 'Less'),
        ('Spacing', '114,55,52,24', '64 px', 'More'),
        ('More', '168,55,24,24', '+', 'More'),
        ('Snap', '198,55,80,24', 'Snap: On', 'Snap'),
        ('Sticky', '284,55,86,24', 'Sticky: On', 'Sticky'),
        ('Filter', '10,83,100,24', 'All frames', 'Filter'),
        ('Hide', '114,83,62,24', 'Hide box', 'Hide'),
        ('Restore', '180,83,98,24', 'Show all boxes', 'Restore'),
        ('ResetAll', '282,83,88,24', 'Reset all', 'ResetAll'),
        ('Previous', '10,113,24,24', '<', 'Previous'),
        ('Next', '298,113,24,24', '>', 'Next'),
        ('Settings', '326,113,44,24', 'Setup', 'Settings'),
    ):
        widget('button', 'Editor' + name, frame, text, action)
    widget('static', 'NudgeName', '40,115,254,22', 'Select a frame')
    widget('button', 'EditorOrigin', '10,143,136,24', 'From: Top left', 'Origin')
    widget('static', 'EditorXLabel', '150,145,14,20', 'X')
    widget('editbox', 'EditorX', '166,143,60,24', preset='v5_editbox')
    widget('static', 'EditorYLabel', '230,145,14,20', 'Y')
    widget('editbox', 'EditorY', '246,143,60,24', preset='v5_editbox')
    widget('button', 'EditorApply', '312,143,58,24', 'Apply', 'Apply')
    for name, x, text in (('Left', 10, '<'), ('Up', 38, '^'), ('Down', 66, 'v'), ('Right', 94, '>')):
        widget('button', 'Nudge' + name, f'{x},173,24,24', text, name)
    widget('button', 'EditorCenterH', '124,173,76,24', 'Center X', 'CenterH')
    widget('button', 'EditorCenterV', '206,173,76,24', 'Center Y', 'CenterV')
    widget('button', 'NudgeReset', '288,173,82,24', 'Reset frame', 'Reset')
    widget('static', 'NudgeXY', '10,203,360,20')
    widget('static', 'EditorStatus', '10,226,360,20', 'Choose Save / Lock to keep your changes.')
    widget('static', 'EditorHelp', '10,250,360,20', 'Drag a box. Arrows: 1 px; Shift: 10 px.')
    return root


def update_xml(xml):
    """Replace only this dialog; keep all other installed dialog bytes untouched."""
    replacement = E.tostring(dialog(), encoding='utf-8')
    pattern = rb'<Dialog\s+[^>]*name=[\"\']AFUIMovers[\"\'][^>]*>.*?</Dialog>'
    if len(re.findall(pattern, xml, re.S)) != 1:
        raise ValueError('Expected exactly one installed AFUIMovers dialog')
    updated = re.sub(pattern, lambda _: replacement, xml, count=1, flags=re.S)
    parsed = E.fromstring(updated)
    count = sum(1 for n in parsed.iter() if n.tag in ('Dialog', 'Widget'))
    return re.sub(rb'(<Screen\b[^>]*\bwidget_cnt=)[\"\']\d+[\"\']',
                  lambda m: m[1] + b'"' + str(count).encode() + b'"', updated, count=1)
