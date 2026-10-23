"""Give HUD shortcuts square artwork and geometry without replacing native controls.

Each icon state gets a private, zero-border native nine-slice skin whose centre
uses the original sprite. The engine stretches that centre to the chosen square;
original skin definitions, texture pixels and button/gauge handlers stay intact.
"""
import copy
from datetime import datetime
import gzip
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as E

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import binary_xml, encode_binary_xml

PREFIX = 'af_shortcut_'
CONTROLS = ('fly_gauge', 'start_button', 'prophet_button', 'btn_radar_map', 'ingame_web',
            'companion_menu_button', 'season_pass_button', 'journey_menu_button', 'wardrobe_menu_button',
            'central_market_button', 'item_shop_container', 'mail_notice', 'mail_full_notice',
            'express_mail_notice', 'cash_mail_notice', 'aetherfall_broker_button', 'aetherfall_menu_button')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def start(payload):
    root = binary_xml(payload)
    if root.get('name') != 'start_dialog':
        raise ValueError('Expected native start dialog')
    presets = set()
    for widget in root.findall('Widget'):
        if widget.get('name') not in CONTROLS:
            continue
        frame = [float(v) for v in widget.get('frame').split(',')]
        original_width = frame[2]
        widget.set('frame', ','.join(f'{v:g}' for v in frame[:2] + [34, 34]))
        if widget.get('name') == 'item_shop_container':
            for child in widget.findall('Widget'):
                r = [float(v) * 34 / original_width for v in child.get('frame').split(',')]
                if child.get('type') == 'button':
                    r = [0, 0, 34, 34]
                child.set('frame', ','.join(f'{v:g}' for v in r))
        for node in widget.iter('Widget'):
            name = node.get('preset')
            if name:
                original = name.removeprefix(PREFIX)
                presets.add(original)
                node.set('preset', PREFIX + original)
    return encode_binary_xml(root), presets


def preload(payload, presets):
    root = binary_xml(payload)
    by_name = {n.get('name'): n for n in root.iter('Preset')}
    skin_by_name = {n.get('name'): n for n in root.iter('Skin') if n.get('texture')}
    category = next((n for n in root if n.tag == 'Category' and n.get('name') == 'AetherfallShortcutGeometry'), None)
    if category is not None:
        root.remove(category)
    category = E.SubElement(root, 'Category', name='AetherfallShortcutGeometry')
    seen = set()
    for name in sorted(presets):
        if name not in by_name:
            raise ValueError('Missing native shortcut preset: ' + name)
        preset = copy.deepcopy(by_name[name]); preset.set('name', PREFIX + name)
        for ref in preset.iter('SkinRef'):
            skin_name = ref.get('name')
            if skin_name not in skin_by_name:
                raise ValueError('Missing native shortcut skin: ' + str(skin_name))
            if skin_name not in seen:
                skin = copy.deepcopy(skin_by_name[skin_name]); skin.set('name', PREFIX + skin_name)
                if skin.get('src_image') and skin.get('type') != 'tiled':
                    source = skin.get('src_image')
                    skin.set('type', 'tiled'); skin.set('src_middle_middle', source)
                    for part in ('left_top', 'middle_top', 'right_top', 'left_middle', 'middle_middle',
                                 'right_middle', 'left_bottom', 'middle_bottom', 'right_bottom'):
                        skin.set('size_' + part, '0,0')
                category.append(skin); seen.add(skin_name)
            ref.set('name', PREFIX + skin_name)
        category.append(preset)
    return encode_binary_xml(root)


def prepare(client, stage, original, extend_archive, read_pak):
    state = json.loads(original('Mods/AetherfallUI/state.json'))
    if state['Applied'] != 'layout':
        raise ValueError('Shortcut geometry requires the enabled full HUD layout')
    manifest = json.loads(original('Mods/AetherfallUI/manifest.json'))
    graphics = json.loads(original('Mods/GraphicsMenu/installed.json'))
    package = json.loads(original('Mods/GraphicsMenu/package/manifest.json'))
    if graphics.get('restoredAt') or graphics['files'] != package['files']:
        raise ValueError('Graphics package/installed state differ')
    replacements = {}; presets = set()
    for relative, names in [('Data/ui/game_hud_s1/game_hud_s1.pak', ['start_dialog.xml']),
                            ('Data/ui/game_hud_s2/game_hud_s2.pak', ['start_dialog.xml']),
                            ('L10N/enu/data/data.pak', ['ui/game_hud_s1/start_dialog.xml', 'ui/game_hud_s2/start_dialog.xml'])]:
        with read_pak(client / relative) as archive:
            for name in names:
                data, used = start(archive.read(name)); replacements.setdefault(relative, {})[name] = data; presets |= used
    for relative, name in [('Data/ui/ui.pak', 'UI_Preload.xml'), ('L10N/enu/data/data.pak', 'ui/ui_preload.xml')]:
        with read_pak(client / relative) as archive:
            replacements.setdefault(relative, {})[name] = preload(archive.read(name), presets)
    for relative, entries in replacements.items():
        source = original(relative)
        with read_pak(client / relative) as archive:
            for name, data in entries.items():
                owned = manifest['archives'][relative]['entries'][name]
                if sha(archive.read(name)) != owned['layout']:
                    raise ValueError('Active shortcut differs from interface payload: ' + name)
                owned['layout'] = sha(data)
                blob = 'Mods/AetherfallUI/payloads/' + sha(data) + '.gz'
                original(blob); stage(blob, gzip.compress(data, mtime=0))
        data = extend_archive(source, entries, {}); stage(relative, data)
        record = next((e for e in graphics['files'] if e['path'].lower() == relative.lower()), None)
        if record:
            package_rel = 'Mods/GraphicsMenu/package/' + record['path']
            if sha(source) != record['installed'] or sha(original(package_rel)) != record['installed']:
                raise ValueError('Active Graphics package differs: ' + relative)
            stage(package_rel, data)
            for rows in (graphics['files'], package['files']):
                next(e for e in rows if e['path'].lower() == relative.lower())['installed'] = sha(data)
    # Preserve the old recovery folder and compose the affected locale members
    # into a fresh matching baseline, using the existing ownership helper.
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'transmog-menu'))
    from graphics_compat import owned_renderer_backup
    old_backup = owned_renderer_backup(client, graphics['backupRoot'])
    new_backup = 'Mods/Backups/Renderer/service-menu-graphics-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    for entry in graphics['files']:
        if entry.get('original') is None:
            continue
        relative = entry['path']; baseline = original((old_backup / relative).relative_to(client).as_posix())
        if sha(baseline) != entry['original']:
            raise ValueError('Graphics restore baseline differs: ' + relative)
        entries = replacements.get(relative)
        if entries:
            baseline = extend_archive(baseline, entries, {})
            for rows in (graphics['files'], package['files']):
                next(e for e in rows if e['path'].lower() == relative.lower())['original'] = sha(baseline)
        stage(new_backup + '/' + relative, baseline)
    graphics['backupRoot'] = str(client / new_backup)
    stage('Mods/AetherfallUI/manifest.json', json.dumps(manifest, indent=2).encode())
    stage('Mods/GraphicsMenu/installed.json', json.dumps(graphics, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    return replacements
