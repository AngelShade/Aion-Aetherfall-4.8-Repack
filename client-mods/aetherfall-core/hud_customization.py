"""Compose the borderless radar with the currently installed interface cohort."""
import gzip
from datetime import datetime
import hashlib
import json
import re
import xml.etree.ElementTree as E
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import binary_xml, encode_binary_xml


def sha(data):
    return hashlib.sha256(data).hexdigest()


def radar(payload, border=None):
    root = binary_xml(payload)
    if root.get('name') != 'radar_dialog':
        raise ValueError('Expected native radar dialog')
    if root.get('preset') not in ('v5_hud_radar_s1', 'v5_hud_radar_s2', None):
        raise ValueError('Unrecognized radar preset')
    # These presets contain only the thick window frame. Native radar children,
    # map drawing, vendor notification, zoom and clock retain their identities.
    root.attrib.pop('preset', None)
    for name in ('radar_wnd', 'radar_sight'):
        node = root.find(f"Widget[@name='{name}']")
        if node is None:
            raise ValueError('Missing native radar child: ' + name)
        for skin in node.findall('Skin'):
            if skin.get('name') != 'v5_hud_' + name:
                raise ValueError('Unexpected radar skin')
            node.remove(skin)
        node.set('frame', '0,0,180,180')
    if border:
        if border not in ('v5_hud_radar_s1','v5_hud_radar_s2'):
            raise ValueError('Unrecognized native minimap frame')
        frame = root.find("Widget[@name='af_minimap_border']")
        if frame is None:
            frame = E.SubElement(root,'Widget',name='af_minimap_border',type='image',frame='0,0,180,180',flag='not_active')
            E.SubElement(frame,'Skin',name='v5_hud_radar_wnd')
        elif len(frame.findall('Skin'))!=1 or frame.find('Skin').get('name') not in (border,'v5_hud_radar_wnd'):
            raise ValueError('Unexpected minimap border ownership')
        else:
            # The window frame's centre is opaque (alpha 255) and covers the
            # native terrain pass. This native five-pixel image border has a
            # transparent centre; its existing atlas/theme remains untouched.
            frame.find('Skin').set('name','v5_hud_radar_wnd')
    return encode_binary_xml(root)


def plugin_xml(payload):
    pattern = rb'(<Dialog\s+[^>]*name="AFHMap"[^>]*)(>)'
    matches = list(re.finditer(pattern, payload))
    if len(matches) != 1:
        raise ValueError('Expected exactly one minimap label')
    return re.sub(pattern, lambda m: re.sub(rb'\s+preset="[^"]*"', b'', m[1]) + m[2], payload)


def refresh_inventory(client):
    """Refresh only this installed cohort, without repacking or signing it."""
    inventory = json.loads((client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    paths = ['bin64/AetherfallCore.dll', 'Plugin/RelicCalc/RelicCalc.pak',
             'Plugin/RelicCalc/RelicCalc.pak.sig', 'Addon.key', 'bin32/bin32.pak.sig',
             'Data/func_pet/func_pet.pak.sig', 'Data/ui/game_hud_s1/game_hud_s1.pak',
             'Data/ui/game_hud_s2/game_hud_s2.pak', 'L10N/enu/data/data.pak']
    current = {name.lower(): dict(path=name, installedSha256=sha((client / name).read_bytes()),
                                  bytes=(client / name).stat().st_size) for name in paths}
    for records in inventory['features'].values():
        for entry in records:
            fresh = current.get(entry.get('path', '').lower())
            if fresh:
                entry.update(installedSha256=fresh['installedSha256'], bytes=fresh['bytes'])
    inventory['features']['AetherfallHudCustomization'] = [current[name.lower()] for name in paths]
    return inventory


def prepare(client, stage, original, extend_archive, read_pak, *, restore_border=False):
    state = json.loads(original('Mods/AetherfallUI/state.json'))
    if state['Applied'] != 'layout':
        raise ValueError('Enable the Full HUD layout before preparing this update')
    manifest = json.loads(original('Mods/AetherfallUI/manifest.json'))
    graphics = json.loads(original('Mods/GraphicsMenu/installed.json'))
    package = json.loads(original('Mods/GraphicsMenu/package/manifest.json'))
    if graphics.get('restoredAt') or graphics['files'] != package['files']:
        raise ValueError('Graphics menu records differ')
    affected = []
    locale_replacements = {}
    for rel, names in [
        ('Data/ui/game_hud_s1/game_hud_s1.pak', ['radar_dialog.xml']),
        ('Data/ui/game_hud_s2/game_hud_s2.pak', ['radar_dialog.xml']),
        ('L10N/enu/data/data.pak', ['ui/game_hud_s1/radar_dialog.xml', 'ui/game_hud_s2/radar_dialog.xml'])]:
        source = original(rel)
        replacements = {}
        with read_pak(client / rel) as z:
            for name in names:
                owned = manifest['archives'][rel]['entries'][name]
                payload = z.read(name)
                if sha(payload) != owned['layout']:
                    raise ValueError('Radar differs from its interface payload: ' + name)
                border = ('v5_hud_radar_s2' if 's2' in name or 's2' in rel else 'v5_hud_radar_s1') if restore_border else None
                replacement = radar(payload,border)
                replacements[name] = replacement
                owned['layout'] = sha(replacement)
                blob = 'Mods/AetherfallUI/payloads/' + owned['layout'] + '.gz'
                if original(blob) != gzip.compress(replacement, mtime=0):
                    stage(blob, gzip.compress(replacement, mtime=0))
        data = extend_archive(source, replacements, {})
        stage(rel, data)
        record = next((e for e in graphics['files'] if e['path'].lower() == rel.lower()), None)
        if record:
            package_rel = 'Mods/GraphicsMenu/package/' + record['path']
            if sha(source) != record['installed'] or sha(original(package_rel)) != record['installed']:
                raise ValueError('Active/package Graphics menu hashes differ')
            stage(package_rel, data)
            for records in (graphics['files'], package['files']):
                next(e for e in records if e['path'].lower() == rel.lower())['installed'] = sha(data)
        affected.append((rel, data))
        if rel.lower() == 'l10n/enu/data/data.pak':
            locale_replacements = replacements
    if restore_border:
        # Graphics removal must retain these interface layouts too. Preserve the
        # previous recovery folder and create a fresh matching restore cohort.
        sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'transmog-menu'))
        from graphics_compat import owned_renderer_backup
        old_backup = owned_renderer_backup(client,graphics['backupRoot'])
        new_backup = 'Mods/Backups/Renderer/service-menu-graphics-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f')
        for entry in graphics['files']:
            if entry.get('original') is None:
                continue
            rel = entry['path']
            baseline = original((old_backup / rel).relative_to(client).as_posix())
            if sha(baseline) != entry['original']:
                raise ValueError('Graphics restore baseline differs: ' + rel)
            if rel.lower() == 'l10n/enu/data/data.pak':
                baseline = extend_archive(baseline,locale_replacements,{})
                for records in (graphics['files'],package['files']):
                    next(e for e in records if e['path'].lower()==rel.lower())['original'] = sha(baseline)
            stage(new_backup + '/' + rel,baseline)
        graphics['backupRoot'] = str(client / new_backup)
    stage('Mods/AetherfallUI/manifest.json', json.dumps(manifest, indent=2).encode())
    stage('Mods/GraphicsMenu/installed.json', json.dumps(graphics, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    inventory = json.loads(original('Mods/InstalledMods.json'))
    for records in inventory['features'].values():
        for entry in records:
            for rel, data in affected:
                if entry.get('path', '').lower() == rel.lower():
                    entry.update(installedSha256=sha(data), bytes=len(data))
    inventory['features']['AetherfallHudCustomization'] = [dict(path=rel, installedSha256=sha(data), bytes=len(data))
                                                        for rel, data in affected]
    # The plugin is signed later in the normal builder. Return this record so
    # shared archive owners receive its final hash, rather than the old one.
    return inventory
