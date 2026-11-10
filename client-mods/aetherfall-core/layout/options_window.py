"""The options window: one native dialog, created with the plugin, with a reusable cell pool."""
import xml.etree.ElementTree as E

CELLS, CATEGORIES, TABS, GROUPS = 16, 14, 8, 16
DROP_ROWS = 8
EVENTS = ('GAME_UI_EDITBOX_CHANGED',
          'GAME_UI_EDITBOX_ENTER', 'GAME_UI_CANCEL_KEY_PRESSED',
          'GAME_UI_DIALOG_VISIBLE_CHANGED', 'MOUSE_ENTER', 'GAME_UI_MOUSE_ENTER')


def dialog(version='1.0'):
    root = E.Element('Dialog', name='AFUIOptions', frame='140,10,1000,700',
                     preset='v5_hud_pet_command_s1', flag='not_visible;not_movable', title_height='0',
                     close_offset='4000,4000')  # the client's own corner button, moved off the window

    def widget(kind, name, frame='0,0,1,1', text=None, click=None, border=False):
        full = 'AFUIOptions' + name
        attrs = dict(type=kind, name=full, frame=frame)
        if kind in ('button', 'static', 'editbox'):
            attrs['font'] = 'ui_default'
        if kind in ('image', 'static'):
            attrs['flag'] = 'not_active'
        if kind == 'static':
            attrs['flag'] += ';truncate;not_multiline;not_wordwrap'
        if kind == 'button':
            attrs['style'] = 'pushbutton'
        if kind == 'editbox':
            attrs['preset'] = 'v5_editbox'
        if text is not None:
            attrs['text'] = str(text)
        node = E.SubElement(root, 'Widget', **attrs)
        if click:
            E.SubElement(E.SubElement(node, 'Scripts'), 'OnClick').text = "AFUI.Click('%s');" % click
        if border:
            # Border lines are siblings placed by AF.Skin. Only dialogs and containers
            # are proven to hold child widgets in this client; controls are not.
            for edge in ('Top', 'Bottom', 'Left', 'Right'):
                E.SubElement(root, 'Widget', type='image', name=full+'Edge'+edge,
                             frame='0,0,1,1', flag='not_active;not_visible')
        return node

    for name in ('Backdrop', 'Sidebar', 'Content'):
        widget('image', name, border=True)
    widget('button', 'TitleDrag', text='Drag window', border=True)
    widget('static', 'Title', text='Aetherfall Interface')
    widget('static', 'Version', text=str(version))
    for name, text in (('Anchors','Toggle anchors'), ('ResetAnchors','Reset anchors'),
                       ('Reposition','Reposition window'), ('Install','Install')):
        widget('button', name, text=text, click=name, border=True)
    widget('editbox', 'Search', border=True)
    widget('button', 'SearchApply', text='Search', click='SearchApply', border=True)
    widget('button', 'SearchClear', text='Clear', click='SearchClear', border=True)
    for i in range(1, CATEGORIES+1):
        widget('button', f'Category{i}', click=f'Category{i}', border=True)
    for name, text in (('CategoryPrev','<'), ('CategoryNext','>'), ('Previous','<'),
                       ('Next','>'), ('Close','Close')):
        widget('button', name, text=text, click=name, border=True)
    for name in ('CategoryPage', 'Header', 'Description', 'Path', 'Page', 'Status'):
        widget('static', name)
    for i in range(1,TABS+1):
        widget('button', f'Tab{i}', click=f'Tab{i}', border=True)
    for i in range(1,GROUPS+1):
        widget('image', f'Group{i}', border=True)
        widget('static', f'Group{i}Caption')
    # Pool members are reused as the category, tab and page change.
    for i in range(1,CELLS+1):
        for part in ('Label','Desc','MinMax','Red','Green','Blue'):
            node=widget('static',f'Cell{i}{part}')
            if part=='Label':
                node.set('HAlign','center')
        for part in ('Check','Value','Less','More','Track','Apply','Color','HexApply',
                     'RedLess','RedMore','GreenLess','GreenMore','BlueLess','BlueMore'):
            widget('button',f'Cell{i}{part}',click=f'Cell{i}{part}',border=True)
        for part in ('Edit','Hex'):
            widget('editbox',f'Cell{i}{part}',border=True)
        widget('image',f'Cell{i}Arrow')
        for channel in ('Red','Green','Blue'):
            widget('button',f'Cell{i}{channel}Track',click=f'Cell{i}{channel}Track',border=True)
            for part in ('Fill','Thumb'):
                widget('image',f'Cell{i}{channel}{part}')
        for part in ('Fill','Thumb'):
            widget('image',f'Cell{i}{part}')
    widget('editbox','Code',border=True)
    # A single pooled dropdown renders above the regular controls. Its arrow is
    # passive art inside the same clickable rectangle as the displayed value.
    widget('button', 'DropBackdrop', border=True)
    for i in range(1, DROP_ROWS+1):
        widget('button', f'DropChoice{i}', click=f'DropChoice{i}')
    for name in ('DropPrev', 'DropNext'):
        widget('button', name, click=name)
    widget('static', 'DropPage')
    scripts = E.SubElement(root,'Scripts')
    E.SubElement(scripts,'OnLoad').text = (
        "g_AddonName='RelicCalc'; " +
        ' '.join("AFUIOptions:RegisterEvent('%s');" % event for event in EVENTS) +
        " AF:Call('OptionsWindow','OnLoad');")
    E.SubElement(scripts,'OnEvent').text = "AF:Call('OptionsWindow','Event',event,...);"  # the client passes event arguments as the script's varargs
    return root
