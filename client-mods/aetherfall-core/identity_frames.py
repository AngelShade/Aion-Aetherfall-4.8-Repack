"""Bot gear rarity and combat rank inside the native target and party frames.

The first version drew loose text beside these windows from a separate plugin dialog,
where it overlapped the party buff icons and floated above the target frame. These
labels are children of the native frames instead, drawn after the frame's own bars:

- Target frame (player container): the empty third row under the MP bar, which only
  your own character uses (DP), shows "◆ Excellent gear" in the rarity colour on the
  left and "Rank ★★★☆☆" in gold on the right.
- Party rows: a bot's name is drawn in its gear rarity colour over the native name,
  and its rank stars sit at the right end of its HP bar.

Colours cannot be changed on a native label at run time, so each rarity has its own
pre-coloured label; the Lua fills one and empties the rest (AetherfallHUD.lua).
"""
import gzip
import hashlib
import json
from datetime import datetime
from pathlib import Path
import sys
from xml.etree import ElementTree as E

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import binary_xml, encode_binary_xml

RARITIES = ['poor', 'ordinary', 'good', 'great', 'excellent']
# Matches the item-quality feel used by the Find Group hire question and the old label.
COLOUR = {'poor': '0.79,0.82,0.85,1', 'ordinary': '0.50,0.83,0.54,1', 'good': '0.44,0.72,1,1',
          'great': '0.76,0.56,1,1', 'excellent': '1,0.74,0.35,1'}
RANK_COLOUR = '1,0.84,0.45,1'
SHADE = dict(text_shade='True', shade_color='0,0,0,1', shade_offset='1,1')
FEATURE = 'AetherfallBotIdentity'
ENTRIES = ('target_dialog.xml', 'party_dialog.xml')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def label(parent, name, frame, font, colour, halign):
    E.SubElement(parent, 'Widget', type='static', name=name, frame=frame, font=font, halign=halign, valign='middle',
                 text_color=colour, flag='transparent;not_focusable;truncate;not_multiline;not_wordwrap', **SHADE)


def owned(container):
    if any(n.get('name', '').startswith('af_identity_') for n in container):
        raise ValueError('Bot identity labels already installed; review the current revision')


def target(payload):
    root = binary_xml(payload)
    if root.get('name') != 'target_dialog':
        raise ValueError('Unexpected target layout')
    players = [c for c in root.findall('Widget') if c.find("Widget[@name='mp_gauge']") is not None
               and c.find("Widget[@name='af_resource_dp']") is not None]
    if len(players) != 1:
        raise ValueError('Expected one player target container with the DP row')
    container = players[0]
    owned(container)
    row = container.find("Widget[@name='af_resource_dp']").get('frame')
    if row != '6,55,239,13':
        raise ValueError('Unexpected target DP row: ' + row)
    for rarity in RARITIES:
        label(container, 'af_identity_gear_' + rarity, '10,55,130,13', 'button', COLOUR[rarity], 'left')
    label(container, 'af_identity_rank', '140,55,101,13', 'button', RANK_COLOUR, 'right')
    return encode_binary_xml(root)


def party(payload):
    root = binary_xml(payload)
    if root.get('name') != 'party_dialog':
        raise ValueError('Unexpected party layout')
    rows = [c for c in root.findall('Widget') if c.get('name', '').startswith('ctn_member')]
    if [c.get('name') for c in rows] != ['ctn_member%d' % i for i in range(1, 7)]:
        raise ValueError('Unexpected party rows')
    for row in rows:
        owned(row)
        title, hp = row.find("Widget[@name='title']"), row.find("Widget[@name='hp']")
        if title is None or hp is None or title.get('frame') != '6,2,112,16' or hp.get('frame') != '5,20,190,13':
            raise ValueError('Unexpected party row geometry: ' + row.get('name'))
        for rarity in RARITIES:
            label(row, 'af_identity_name_' + rarity, title.get('frame'), title.get('font', 'name'), COLOUR[rarity], 'left')
        label(row, 'af_identity_rank', '100,20,92,13', 'button', RANK_COLOUR, 'right')
    return encode_binary_xml(root)


TRANSFORMS = {'target_dialog.xml': target, 'party_dialog.xml': party}


def remove_overlay_xml(payload):
    """Drop the old loose-label plugin dialog: it frees one of the 20 plugin windows."""
    import re
    pattern = rb'<Dialog\s+[^>]*name=[\"\']AFHIdentity[\"\'][^>]*>.*?</Dialog>\s*'
    result, count = re.subn(pattern, b'', payload, flags=re.S)
    if count != 1:
        raise ValueError('Expected exactly one installed AFHIdentity dialog')
    total = sum(1 for node in E.fromstring(result).iter() if node.tag in ('Dialog', 'Widget'))
    return re.sub(rb'widget_cnt="\d+"', f'widget_cnt="{total}"'.encode(), result, count=1)


def prepare(client, stage, original, extend_archive, read_pak):
    """Same ownership chain as target_details.prepare_foreground: Aetherfall layout payloads,
    Graphics menu active/package/restore copies, and the installed-mods inventory."""
    state = json.loads(original('Mods/AetherfallUI/state.json'))
    if state['Applied'] != 'layout':
        raise ValueError('Enable the full HUD layout before preparing bot identity labels')
    manifest = json.loads(original('Mods/AetherfallUI/manifest.json'))
    from client_ownership import independent, verify as verify_parts
    independent_graphics = independent(client)
    if independent_graphics:
        verify_parts(client)
    graphics = json.loads(original('Mods/GraphicsMenu/installed.json'))
    package = json.loads(original('Mods/GraphicsMenu/package/manifest.json'))
    if graphics.get('restoredAt') or graphics['files'] != package['files']:
        raise ValueError('Graphics recovery ownership differs')
    replacements, affected = {}, []
    for rel, prefix in [('Data/ui/game/game.pak', ''), ('L10N/enu/data/data.pak', 'ui/game/')]:
        source = original(rel)
        changes = {}
        with read_pak(client / rel) as z:
            for name in ENTRIES:
                entry = prefix + name
                record = manifest['archives'][rel]['entries'][entry]
                payload = z.read(entry)
                if sha(payload) != record['layout']:
                    raise ValueError('Active layout differs from its owned interface payload: ' + entry)
                replacement = TRANSFORMS[name](payload)
                changes[entry] = replacement
                record['layout'] = sha(replacement)
                blob = 'Mods/AetherfallUI/payloads/' + record['layout'] + '.gz'
                original(blob)
                stage(blob, gzip.compress(replacement, mtime=0))
        replacements[rel.lower()] = changes
        data = extend_archive(source, changes, {})
        stage(rel, data)
        affected.append((rel, data))
        if not independent_graphics:
            row = next(e for e in graphics['files'] if e['path'].lower() == rel.lower())
            package_rel = 'Mods/GraphicsMenu/package/' + row['path']
            if sha(source) != row['installed'] or sha(original(package_rel)) != row['installed']:
                raise ValueError('Active/package Graphics hashes differ: ' + rel)
            stage(package_rel, data)
            for rows in (graphics['files'], package['files']):
                next(e for e in rows if e['path'].lower() == rel.lower())['installed'] = sha(data)
    if not independent_graphics:
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
        stage('Mods/GraphicsMenu/installed.json', json.dumps(graphics, indent=2).encode())
        stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    stage('Mods/AetherfallUI/manifest.json', json.dumps(manifest, indent=2).encode())
    return affected
