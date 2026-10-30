"""Codex layouts: world-map side panel, target-frame chest and their skin.

Composed with the installed interface by `aetherfall-core/prepare.py --codex-update`:
every owned Aetherfall UI variant (off/on/layout) of the target frame and skin
library, the base and English map layouts, the Graphics menu package and a fresh
restore baseline. Archive members other than these keep their compressed bytes.
"""
import gzip
import hashlib
import json
import sys
from datetime import datetime
from pathlib import Path
from xml.etree import ElementTree as E

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import binary_xml, encode_binary_xml

TEXTURE = 'Textures/UI/AetherfallCodex/loot.dds'
TARGET_TEXTURE = 'Textures/UI/AetherfallCodex/loot-target.dds'
SKIN = 'af_codex_loot'
TARGET_SKIN = 'af_codex_loot_target'
CATEGORY = 'AetherfallCodex'
# Members per archive: target frames and skin libraries are owned per UI variant;
# the map is not owned by the interface switcher, so only its active member changes.
TARGETS = {'Data/ui/game/game.pak': 'target_dialog.xml', 'L10N/enu/data/data.pak': 'ui/game/target_dialog.xml'}
MAPS = {'Data/ui/game/game.pak': 'new_map_dialog.xml', 'L10N/enu/data/data.pak': 'ui/game/new_map_dialog.xml'}
SKINS = {'Data/ui/ui.pak': 'UI_Preload.xml', 'L10N/enu/data/data.pak': 'ui/ui_preload.xml'}
# Native layouts show literal button text as "????": text and tooltips are string ids.
STRINGS_ENTRY = ('L10N/enu/data/data.pak', 'strings/client_strings_ui.xml')
TEXT, MAP_HELP, TARGET_HELP = 'STR_AETHERFALL_CODEX', 'STR_AETHERFALL_CODEX_MAP_HELP', 'STR_AETHERFALL_CODEX_TARGET_HELP'
STRINGS = [(990100200, TEXT, 'Codex'),
           (990100201, MAP_HELP, 'Codex: search items and creatures beside the map; select a spawn area to show it on the map'),
           (990100202, TARGET_HELP, "Codex: this creature's loot and spawn areas")]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def strings(payload):
    root = binary_xml(payload)
    for number, name, body in STRINGS:
        row = next((e for e in root if e.findtext('name') == name), None)
        if row is not None:
            row.find('body').text = body
            continue
        if any(e.findtext('id') == str(number) for e in root): raise ValueError('Codex string id collision: ' + name)
        row = E.SubElement(root, 'string')
        for key, value in [('id', str(number)), ('name', name), ('body', body)]:
            E.SubElement(row, key).text = value
    return encode_binary_xml(root)


def skins(payload):
    root = binary_xml(payload)
    for parent in root.iter():
        for child in list(parent):
            if child.get('name') == CATEGORY or child.get('name', '').startswith('af_codex_'): parent.remove(child)
    category = E.SubElement(root, 'Category', name=CATEGORY)
    E.SubElement(category, 'Skin', name=SKIN, type='single', texture=TEXTURE[:-4], src_image='0,0,32,32')
    # Bright gold/teal chest fills the target hit area; map markers keep their
    # original artwork and source rectangle.
    E.SubElement(category, 'Skin', name=TARGET_SKIN, type='single', texture=TARGET_TEXTURE[:-4], src_image='0,0,32,32')
    arrow = E.SubElement(category, 'Preset', name='af_codex_toggle', type='button')
    for state, skin in [('up', 'up'), ('over', 'over'), ('down', 'down')]:
        E.SubElement(arrow, 'SkinRef', main_state='0', sub_state=state, name='v5_narrow_' + skin)
        E.SubElement(arrow, 'SkinRef', main_state='1', sub_state=state, name='v5_expand_' + skin)
    for name, up in [('af_codex_row', 'v5_row_even'), ('af_codex_field', 'v5_static_bg2')]:
        preset = E.SubElement(category, 'Preset', name=name, type='button')
        for state, skin in [('up', up), ('over', 'v5_item_mouseover'), ('down', 'v5_item_selected')]:
            E.SubElement(preset, 'SkinRef', main_state='0', sub_state=state, name=skin)
    return encode_binary_xml(root)


def panel():
    """Native children of DlgNewMap; all surfaces use the map's skin library."""
    # v5_dialog reserves 32 px for its bottom frame. Root height also includes
    # the 25 px title: 735 - 25 - 8(top) - 32(frame) - 4(gap) = 666.
    root = E.Element('Widget', type='container', name='af_codex_panel', frame='654,8,270,666', preset='v5_staticbox', flag='not_visible')
    def w(kind, name, frame, parent=root, preset=None, multiline=False, color=None, visible=True):
        attrs = dict(type=kind, name='af_codex_' + name, frame=frame)
        if preset: attrs['preset'] = preset
        if kind in ('static', 'button', 'editbox'): attrs['font'] = 'ui_default'
        if kind == 'button': attrs['style'] = 'pushbutton'
        flags = [] if visible else ['not_visible']
        if kind in ('static', 'image'): flags += ['not_active']
        if kind == 'static' and not multiline: flags += ['truncate', 'not_multiline', 'not_wordwrap']
        if flags: attrs['flag'] = ';'.join(flags)
        if color: attrs['text_color'] = color
        return E.SubElement(parent, 'Widget', attrs)
    w('static', 'brand', '66,10,170,20', color='0.84,0.72,0.47,1')
    w('button', 'back', '8,8,50,24', preset='v5_button')
    w('button', 'close', '244,9,18,18', preset='v5_close')
    w('button', 'items', '8,38,124,24', preset='v5_button')
    w('button', 'npcs', '138,38,124,24', preset='v5_button')
    w('editbox', 'search', '8,69,178,24', preset='v5_editbox')
    w('button', 'search_go', '192,69,70,24', preset='v5_button')
    w('button', 'filters_toggle', '8,99,124,22', preset='v5_button')
    w('button', 'reset', '138,99,124,22', preset='v5_button')
    w('button', 'world_drops', '8,99,254,22', preset='v5_button', visible=False)
    filters = w('container', 'filters', '8,126,254,192', visible=False)
    for name, label, x, y, width in [('category','category_label',0,0,124),('grade','grade_label',130,0,124),
                                    ('group','group_label',0,43,124),('race','race_label',130,43,124),
                                    ('source','source_label',0,130,124),('sort','sort_label',130,130,124)]:
        w('static', label, f'{x},{y},{width},14', parent=filters, color='0.57,0.67,0.74,1')
        w('button', name, f'{x},{y+14},{width},22', parent=filters, preset='af_codex_field')
    w('editbox', 'area', '0,57,124,22', parent=filters, preset='v5_editbox', visible=False)
    w('static', 'level_label', '0,86,254,14', parent=filters, color='0.57,0.67,0.74,1')
    w('editbox', 'level_min', '0,100,110,22', parent=filters, preset='v5_editbox')
    w('static', 'level_dash', '114,100,24,22', parent=filters)
    w('editbox', 'level_max', '144,100,110,22', parent=filters, preset='v5_editbox')
    w('button', 'apply', '0,170,254,22', parent=filters, preset='v5_button')
    w('static', 'heading', '8,129,148,20', color='0.84,0.72,0.47,1')
    w('button', 'view_action', '156,126,106,24', preset='v5_button', visible=False)
    w('static', 'notice', '8,153,254,50', multiline=True, color='0.57,0.67,0.74,1')
    for i in range(1, 8):
        row = w('button', 'row_' + str(i), f'8,{210+(i-1)*56},254,54', preset='af_codex_row', visible=False)
        w('image', 'icon_' + str(i), '6,8,32,32', parent=row)
        w('static', 'name_' + str(i), '44,3,204,25', parent=row, multiline=True)
        w('static', 'detail_' + str(i), '44,28,204,13', parent=row, color='0.57,0.67,0.74,1')
        w('static', 'rate_' + str(i), '44,41,204,12', parent=row, color='0.84,0.72,0.47,1')
        w('button', 'sources_' + str(i), '164,31,84,20', parent=row, preset='v5_button', visible=False)
    # Hidden 1 px browser view that only carries native item tooltip events (loot_codex.h tooltip()).
    E.SubElement(root, 'Widget', type='browser', name='af_codex_tip', frame='0,0,1,1')
    w('static', 'count', '8,608,254,18', color='0.57,0.67,0.74,1')
    w('button', 'market_return', '158,605,104,24', preset='v5_button', visible=False)
    w('button', 'previous', '8,635,76,24', preset='v5_button')
    w('static', 'page', '88,637,94,20')
    w('button', 'next', '186,635,76,24', preset='v5_button')
    w('button', 'dismiss', '0,0,270,666', visible=False)
    # Opaque like the native combobox list (v5_combobox_list's v5_droplist_bg), drawn last so it covers the panel.
    menu = w('container', 'menu', '8,164,254,298', visible=False)
    # Not named *_bg: the interface's opacity setting fades every *_bg/bg_* layer of a window.
    E.SubElement(w('image', 'menu_fill', '0,0,254,298', parent=menu), 'Skin', name='v5_droplist_bg')
    for i in range(1, 13): w('button', 'choice_' + str(i), f'4,{4+(i-1)*22},246,22', parent=menu, preset='v5_popup_button')
    w('button', 'menu_previous', '4,271,64,22', parent=menu, preset='v5_button')
    w('static', 'menu_page', '75,273,104,18', parent=menu)
    w('button', 'menu_next', '186,271,64,22', parent=menu, preset='v5_button')
    return root


def world_map(payload):
    """Keep native map geometry; the controller extends the frame for an independent right pane."""
    root = binary_xml(payload)
    if root.get('name') != 'new_map_dialog' or root.get('frame').split(',')[2:] != ['932', '735']:
        raise ValueError('Unexpected world map layout')
    for parent in root.iter():
        for child in list(parent):
            if child.get('name', '').startswith('af_codex_'): parent.remove(child)
    frames = {n.get('name'): n.get('frame') for n in root if n.tag == 'Widget'}
    container = root.find("Widget[@name='ui_container']")
    expand = container.find("Widget[@name='expand']") if container is not None else None
    if (frames.get('min_dlg_frame'), frames.get('ui_container_frame'), frames.get('map_container')) != ('276,0,656,735', '-3,0,932,710', '295,84,590,590')             or expand is None or expand.get('frame') != '281,4,19,19':
        raise ValueError('Unexpected native map size layout')
    # Mirrored native legend arrow, at the map's right edge. Runtime placement
    # follows native compact/expanded sizes without hiding the left arrow.
    E.SubElement(root, 'Widget', type='button', name='af_codex_open', frame='902,4,19,19',
                 preset='af_codex_toggle', tooltip=MAP_HELP)
    root.append(panel())
    # One small marker per recorded spawn of the listed creature (loot_codex.h MARKS),
    # then the selected spawn's marker, drawn above them.
    for i in range(1, 49):
        spot = E.SubElement(root, 'Widget', type='image', name=f'af_codex_marker_{i}', frame='0,0,12,12',
                            flag='not_visible;not_active')
        E.SubElement(spot, 'Skin', name=SKIN)
    marker = E.SubElement(root, 'Widget', type='image', name='af_codex_marker', frame='0,0,18,18',
                          flag='not_visible;not_active')
    E.SubElement(marker, 'Skin', name=SKIN)
    return encode_binary_xml(root)


def target(payload):
    root = binary_xml(payload)
    if root.get('name') != 'target_dialog':
        raise ValueError('Unexpected target layout')
    for i in range(1, 5):
        container = root.find(f"Widget[@name='container{i}']")
        name = container.find("Widget[@name='name']") if container is not None else None
        hp = container.find("Widget[@name='hp_gauge']") if container is not None else None
        if name is None or hp is None:
            raise ValueError('Unexpected creature name layout')
        existing = container.find("Widget[@name='af_codex_open']")
        if existing is not None: container.remove(existing)
        x, y, w, h = (int(float(v)) for v in name.get('frame').split(','))
        if w < 100 or h not in (16, 18):
            raise ValueError('Unexpected creature name geometry')
        # Reserve the chest at the start of the existing name area, in every
        # variant (the Aetherfall header and the native portrait frame). Root,
        # health bars, emblem, level and the player/pet containers keep their geometry.
        if existing is None: name.set('frame', f'{x + 20},{y},{w - 20},{h}')
        else: x -= 20
        top = max(0, y + (h - 16) // 2 - 1)
        name.set('flag', ';'.join(filter(None, [name.get('flag'), 'truncate;not_multiline;not_wordwrap'])))
        # An image-only button keeps its artwork and hit area above the HP bar.
        button = E.SubElement(container, 'Widget', type='button', name='af_codex_open', frame=f'{x},{top},18,16',
                              tooltip=TARGET_HELP)
        icon = E.SubElement(button, 'Widget', type='image', name='af_codex_icon', frame='0,0,18,16', flag='not_active')
        E.SubElement(icon, 'Skin', name=TARGET_SKIN)
        if top + 16 > int(float(hp.get('frame').split(',')[1])) - 3:
            raise ValueError('Loot button crowds the HP gauge')
    return encode_binary_xml(root)


def prepare(client, stage, original, extend_archive, read_pak):
    """Stage the Codex layouts, owned UI payloads, Graphics package/baseline and texture."""
    state = json.loads(original('Mods/AetherfallUI/state.json'))
    if state['Applied'] != state['Desired']:
        raise ValueError('Apply the selected Aetherfall interface before adding the Codex')
    applied = state['Applied']
    manifest = json.loads(original('Mods/AetherfallUI/manifest.json'))
    graphics = json.loads(original('Mods/GraphicsMenu/installed.json'))
    package = json.loads(original('Mods/GraphicsMenu/package/manifest.json'))
    if graphics.get('restoredAt') or graphics['files'] != package['files']:
        raise ValueError('Graphics recovery ownership differs')
    replacements = {}
    for rel in sorted(set(TARGETS) | set(MAPS) | set(SKINS)):
        source = original(rel)
        changes = {}
        with read_pak(client / rel) as z:
            for entry, transform in [(TARGETS.get(rel), target), (SKINS.get(rel), skins), (MAPS.get(rel), world_map)]:
                if not entry:
                    continue
                payload = z.read(entry)
                entries = manifest['archives'][rel]['entries']
                if entry not in entries:
                    # Adopt the existing map member into the same switcher as
                    # the rest of the native UI. Every theme retains the feature.
                    replacement = transform(payload)
                    digest = sha(replacement)
                    blob = 'Mods/AetherfallUI/payloads/' + digest + '.gz'
                    original(blob)
                    stage(blob, gzip.compress(replacement, mtime=0))
                    entries[entry] = {variant: digest for variant in ('off', 'on', 'layout')}
                    changes[entry] = replacement
                    continue
                owned = entries[entry]
                if sha(payload) != owned[applied]:
                    raise ValueError('Active member differs from its owned interface payload: ' + entry)
                active_digest = owned[applied]
                done = {}
                for variant, digest in owned.items():
                    if digest not in done:
                        blob = original('Mods/AetherfallUI/payloads/' + digest + '.gz')
                        if blob is None or sha(gzip.decompress(blob)) != digest:
                            raise ValueError('Missing owned interface payload: ' + entry + ' ' + variant)
                        replacement = transform(gzip.decompress(blob))
                        done[digest] = sha(replacement)
                        name = 'Mods/AetherfallUI/payloads/' + done[digest] + '.gz'
                        original(name)
                        stage(name, gzip.compress(replacement, mtime=0))
                        if digest == active_digest:
                            changes[entry] = replacement
                    owned[variant] = done[digest]
            if rel == STRINGS_ENTRY[0]:
                changes[STRINGS_ENTRY[1]] = strings(z.read(STRINGS_ENTRY[1]))
        replacements[rel] = changes
        data = extend_archive(source, changes, {})
        stage(rel, data)
        record = next((e for e in graphics['files'] if e['path'].lower() == rel.lower()), None)
        if record:
            package_rel = 'Mods/GraphicsMenu/package/' + record['path']
            if sha(source) != record['installed'] or sha(original(package_rel)) != record['installed']:
                raise ValueError('Active/package Graphics hashes differ: ' + rel)
            stage(package_rel, data)
            for rows in (graphics['files'], package['files']):
                next(e for e in rows if e['path'].lower() == rel.lower())['installed'] = sha(data)
    # Keep the previous recovery folder; carry the same members into a fresh
    # baseline so removing Graphics retains the Codex resources.
    sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
    from graphics_compat import owned_renderer_backup
    old_backup = owned_renderer_backup(client, graphics['backupRoot'])
    new_backup = 'Mods/Backups/Renderer/service-menu-graphics-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    for record in graphics['files']:
        if record.get('original') is None:
            continue
        rel = record['path']
        baseline = original((old_backup / rel).relative_to(client).as_posix())
        if sha(baseline) != record['original']:
            raise ValueError('Graphics restore baseline changed: ' + rel)
        changes = next((c for r, c in replacements.items() if r.lower() == rel.lower()), None)
        if changes:
            baseline = extend_archive(baseline, changes, {})
            for rows in (graphics['files'], package['files']):
                next(e for e in rows if e['path'].lower() == rel.lower())['original'] = sha(baseline)
        stage(new_backup + '/' + rel, baseline)
    graphics['backupRoot'] = str(client / new_backup)
    stage('Mods/AetherfallUI/manifest.json', json.dumps(manifest, indent=2).encode())
    stage('Mods/GraphicsMenu/installed.json', json.dumps(graphics, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    for path, asset in [(TEXTURE, 'loot.dds'), (TARGET_TEXTURE, 'loot-target.dds')]:
        texture = (HERE / 'assets' / asset).read_bytes()
        if original(path) != texture:
            stage(path, texture)
    return replacements
