"""Bounded target-frame update composed with the installed interface and Graphics menu."""
import gzip
from datetime import datetime
import hashlib
import json
import re
import struct
import sys
from pathlib import Path
from xml.etree import ElementTree as E

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import binary_xml, encode_binary_xml

DP_PATH = 'Textures/UI/AetherfallMovers/target_dp.dds'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def transform(payload):
    root = binary_xml(payload)
    if root.get('name') != 'target_dialog':
        raise ValueError('Unexpected target layout')
    for node in [root] + list(root.findall('Widget')):
        if node is root or node.get('name', '').startswith('container'):
            frame = node.get('frame').split(',')
            if frame[2:] not in (['251', '58'], ['251', '72']):
                raise ValueError('Unexpected target frame size')
            node.set('frame', ','.join(frame[:2] + ['251', '72']))
    for container in root.findall('Widget'):
        if not container.get('name', '').startswith('container'):
            continue
        bg = container.find("Widget[@name='bg']")
        if bg is not None:
            bg.set('frame', '0,0,251,72')
        for node in container.findall('Widget'):
            name = node.get('name', '')
            if name in ('hp_gauge', 'hp_gauge_bg'):
                node.set('frame', '6,22,239,16')
            elif name in ('mp_gauge', 'mp_gauge_bg'):
                node.set('frame', '6,40,239,13')
            if name in ('hp_gauge', 'mp_gauge'):
                node.set('show_value', 'false')  # the shared overlay supplies readable values
    return encode_binary_xml(root)


def overlay():
    root = E.Element('Dialog', name='AFHTarget', frame='0,0,251,72',
                     preset='v5_hud_pet_command_s1',
                     flag='transparent;not_visible;not_movable;not_active')
    for name, frame in [('DPFill', '6,55,239,13')]:
        E.SubElement(root, 'Widget', type='image', name='AFHTarget' + name,
                     frame=frame, flag='not_active')
    for name, frame in [('HP', '6,22,239,16'), ('MP', '6,40,239,13'), ('DP', '6,55,239,13')]:
        E.SubElement(root, 'Widget', type='static', name='AFHTarget' + name,
                     frame=frame, font='button', HAlign='center',
                     flag='not_active;truncate;not_multiline;not_wordwrap')
    return root


def update_xml(payload):
    # Preserve the exact bytes of all existing plugin dialogs.
    if b'name="AFHTarget"' in payload:
        raise ValueError('Target overlay already installed; use normal retained-layout update')
    if payload.count(b'</Screen>') != 1:
        raise ValueError('Unexpected HUD XML')
    return payload.replace(b'</Screen>', E.tostring(overlay(), encoding='utf-8') + b'\n</Screen>')


def repair_overlay_xml(payload):
    # Replace only this dialog; retain all other generated plugin layout bytes.
    pattern = rb'<Dialog\s+[^>]*name=[\"\']AFHTarget[\"\'][^>]*>.*?</Dialog>'
    matches = list(re.finditer(pattern, payload, re.S))
    if len(matches) != 1:
        raise ValueError('Expected exactly one installed target readout')
    old = E.fromstring(matches[0].group())
    if [n.get('name') for n in old] != [n.get('name') for n in overlay()]:
        raise ValueError('Unexpected target readout children')
    result = re.sub(pattern, lambda _: E.tostring(overlay(), encoding='utf-8'), payload, flags=re.S)
    count = sum(1 for node in E.fromstring(result).iter() if node.tag in ('Dialog', 'Widget'))
    return re.sub(rb'widget_cnt="\d+"', f'widget_cnt="{count}"'.encode(), result, count=1)


def update_design(payload):
    result, count = re.subn(rb'(\[11\] = \{644, 708, 251, )58(,)', rb'\g<1>72\2', payload)
    if count != 1:
        raise ValueError('Unexpected target design')
    return result


def foreground(payload):
    """Numbers belong to the native frame, after its bars, rather than another dialog."""
    root = binary_xml(payload)
    name = root.get('name')
    if name not in ('target_dialog', 'target_assist_dialog'):
        raise ValueError('Unexpected target frame')
    count = 0
    for container in root.findall('Widget'):
        hp = container.find("Widget[@name='hp_gauge']")
        if hp is None or hp.get('preset') != 'v5_hp_gauge_target':
            continue  # Toy-pet feeling gauges are not health bars.
        if any(n.get('name', '').startswith('af_resource_') for n in container):
            raise ValueError('Native target labels already installed; review the current revision')
        resources = [('hp', hp.get('frame'))]
        mp = container.find("Widget[@name='mp_gauge']")
        if mp is not None:
            resources += [('mp', mp.get('frame')), ('dp', '6,55,239,13')]
        hp.set('show_value', 'false')
        if mp is not None:
            mp.set('show_value', 'false')
        for resource, frame in resources:
            E.SubElement(container, 'Widget', type='static', name='af_resource_' + resource,
                         frame=frame, font='button', halign='center', valign='middle',
                         text_color='1,1,1,1', text_shade='True', shade_color='0,0,0,1',
                         shade_offset='1,1', flag='transparent;not_focusable')
            count += 1
    expected = 7 if name == 'target_dialog' else 1
    if count != expected:
        raise ValueError('Unexpected native target gauge inventory: ' + name)
    return encode_binary_xml(root)


def prepare_foreground(client, stage, original, extend_archive, read_pak):
    """Update exactly two layouts in the base/English archives and owned recovery images."""
    state = json.loads(original('Mods/AetherfallUI/state.json'))
    if state['Applied'] != 'layout':
        raise ValueError('Enable the full HUD layout before preparing native target readouts')
    manifest = json.loads(original('Mods/AetherfallUI/manifest.json'))
    graphics = json.loads(original('Mods/GraphicsMenu/installed.json'))
    package = json.loads(original('Mods/GraphicsMenu/package/manifest.json'))
    if graphics.get('restoredAt') or graphics['files'] != package['files']:
        raise ValueError('Graphics recovery ownership differs')
    replacements = {}
    for rel, prefix in [('Data/ui/game/game.pak', ''), ('L10N/enu/data/data.pak', 'ui/game/')]:
        source = original(rel)
        changes = {}
        with read_pak(client / rel) as z:
            for name in ('target_dialog.xml', 'target_assist_dialog.xml'):
                entry = prefix + name
                owned = manifest['archives'][rel]['entries'][entry]
                payload = z.read(entry)
                if sha(payload) != owned['layout']:
                    raise ValueError('Active target differs from its owned layout: ' + entry)
                replacement = foreground(payload)
                changes[entry] = replacement
                owned['layout'] = sha(replacement)
                blob = 'Mods/AetherfallUI/payloads/' + owned['layout'] + '.gz'
                original(blob)
                stage(blob, gzip.compress(replacement, mtime=0))
        replacements[rel.lower()] = changes
        data = extend_archive(source, changes, {})
        stage(rel, data)
        record = next(e for e in graphics['files'] if e['path'].lower() == rel.lower())
        package_rel = 'Mods/GraphicsMenu/package/' + record['path']
        if sha(source) != record['installed'] or sha(original(package_rel)) != record['installed']:
            raise ValueError('Active/package Graphics hashes differ: ' + rel)
        stage(package_rel, data)
        for rows in (graphics['files'], package['files']):
            next(e for e in rows if e['path'].lower() == rel.lower())['installed'] = sha(data)
    # Preserve the previous recovery folder; carry the same labels into a fresh
    # baseline so removing Graphics retains the current full HUD resources.
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'transmog-menu'))
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
        changes = replacements.get(rel.lower())
        if changes:
            baseline = extend_archive(baseline, changes, {})
            for rows in (graphics['files'], package['files']):
                next(e for e in rows if e['path'].lower() == rel.lower())['original'] = sha(baseline)
        stage(new_backup + '/' + rel, baseline)
    graphics['backupRoot'] = str(client / new_backup)
    stage('Mods/AetherfallUI/manifest.json', json.dumps(manifest, indent=2).encode())
    stage('Mods/GraphicsMenu/installed.json', json.dumps(graphics, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())


def prepare(client, stage, original, extend_archive, read_pak):
    state = json.loads(original('Mods/AetherfallUI/state.json'))
    if state['Applied'] != 'layout':
        raise ValueError('This repair requires the installed Aetherfall layout')
    manifest = json.loads(original('Mods/AetherfallUI/manifest.json'))
    graphics = json.loads(original('Mods/GraphicsMenu/installed.json'))
    package = json.loads(original('Mods/GraphicsMenu/package/manifest.json'))
    if graphics.get('restoredAt') or graphics['files'] != package['files']:
        raise ValueError('Graphics menu records differ')
    affected = []
    for rel, entryname in [('Data/ui/game/game.pak', 'target_dialog.xml'),
                           ('L10N/enu/data/data.pak', 'ui/game/target_dialog.xml')]:
        owned = manifest['archives'][rel]['entries'][entryname]
        source = original(rel)
        with read_pak(client / rel) as z:
            payload = z.read(entryname)
        if sha(payload) != owned['layout']:
            raise ValueError('Active target differs from its interface payload')
        replacement = transform(payload)
        owned['layout'] = sha(replacement)
        data = extend_archive(source, {entryname: replacement}, {})
        stage(rel, data)
        blob = 'Mods/AetherfallUI/payloads/' + owned['layout'] + '.gz'
        original(blob)
        stage(blob, gzip.compress(replacement, mtime=0))
        record = next(e for e in graphics['files'] if e['path'].lower() == rel.lower())
        package_rel = 'Mods/GraphicsMenu/package/' + record['path']
        if sha(source) != record['installed'] or sha(original(package_rel)) != record['installed']:
            raise ValueError('Active/package Graphics menu hashes differ')
        stage(package_rel, data)
        for records in (graphics['files'], package['files']):
            next(e for e in records if e['path'].lower() == rel.lower())['installed'] = sha(data)
        affected.append((rel, data))
    stage('Mods/AetherfallUI/manifest.json', json.dumps(manifest, indent=2).encode())
    stage('Mods/GraphicsMenu/installed.json', json.dumps(graphics, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    inventory = json.loads(original('Mods/InstalledMods.json'))
    for records in inventory['features'].values():
        for entry in records:
            for rel, data in affected:
                if entry.get('path', '').lower() == rel.lower():
                    entry.update(installedSha256=sha(data), bytes=len(data))
    inventory['features']['AetherfallTargetDetails'] = [dict(path=rel, installedSha256=sha(data), bytes=len(data))
                                                       for rel, data in affected]
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    header = [124, 0x100F, 4, 4, 16, 0, 0] + [0] * 11
    header += [32, 0x41, 0, 32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000, 0x1000, 0, 0, 0, 0]
    original(DP_PATH)
    stage(DP_PATH, b'DDS ' + struct.pack('<31I', *header) + bytes((90, 150, 75, 255)) * 16)
