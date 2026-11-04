"""Prepare Find Group's native Hire details children; the original is read only.

Uses the normal PAK codec/rebuilder, existing Graphics recovery ownership and
native English string keys. HUD S1/S2 inherit this base dialog; any explicit
party_match variant found in their archives is transformed as well.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
from xml.etree import ElementTree as E

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
from codec import binary_xml, encode_binary_xml, read_pak
from prepare_client import parts, rebuild
from graphics_compat import owned_renderer_backup

STRINGS_ENTRY = 'strings/client_strings_ui.xml'
LABELS = [
    ('STR_AETHERFALL_HIRE_DETAILS', 'Hire details'),
    ('STR_AETHERFALL_HIRE_TERM_1', 'Invite or Apply shows the price and term before Yes.'),
    ('STR_AETHERFALL_HIRE_TERM_2', 'One payment in Kinah to this helper.'),
    ('STR_AETHERFALL_HIRE_TERM_3', 'The term starts on joining and does not renew.'),
    ('STR_AETHERFALL_HIRE_TERM_4', 'Disconnecting, dismissal or leaving the party ends it.'),
    ('STR_AETHERFALL_HIRE_TERM_5', 'There is no refund after the helper joins.'),
    ('STR_AETHERFALL_HIRE_TERM_6', 'Choosing No, expiry or a failed join costs nothing.'),
    ('STR_AETHERFALL_HIRE_TERM_7', 'The helper keeps its own items and earnings.'),
    ('STR_AETHERFALL_HIRE_BEFORE', 'Before you hire'),
    ('STR_AETHERFALL_HIRE_ENDING', 'When it ends'),
    ('STR_AETHERFALL_HELP_REQUESTS', 'Help requests'),
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def menus(payload):
    """Extend the installed menu owner without replacing unrelated menu changes."""
    text = payload.decode('utf-8-sig')
    addition = '''
function PrivateHelpRequests_Open()
    -- Game.dll only authenticates exact window addresses, so this opens the normal
    -- Companions page; the native bridge adds the help section to that navigation.
    if AetherfallCore then AetherfallCore('helpview') end
    PrivateCompanions_Open();
end
'''
    if 'function PrivateHelpRequests_Open()' in text:
        # Replace the first version, which loaded an address Game.dll rejects.
        start = text.index('function PrivateHelpRequests_Open()')
        end = text.index('\nend', start) + len('\nend')
        text = text[:start] + addition.strip() + text[end:]
    else:
        anchor = 'function PrivateMenus_Register()'
        if text.count(anchor) != 1:
            raise ValueError('Private menu owner changed')
        text = text.replace(anchor, addition + '\n' + anchor, 1)
    if 'SLASH_PRIVATEHELPREQUESTS1' not in text:
        anchor = '    SLASH_PRIVATECOMPANIONS1 = "/companions";'
        if text.count(anchor) != 1:
            raise ValueError('Companions menu registration changed')
        text = text.replace(anchor, anchor + '\n    SlashCmdList["PRIVATEHELPREQUESTS"] = PrivateHelpRequests_Open;\n    SLASH_PRIVATEHELPREQUESTS1 = "/helprequests";', 1)
    return text.encode('utf-8')


def layout(payload):
    root = binary_xml(payload)
    if root.get('name') != 'party_match_dialog' or root.get('type') != 'dlg_party_match':
        raise ValueError('Unexpected Find Group layout')
    help = next((n for n in root if n.get('name') == 'help'), None)
    if help is None or help.get('type') != 'button':
        raise ValueError('Find Group Help is not a direct native button')
    for n in list(root):
        if n.get('name') in ('af_hire_details', 'af_hire_terms', 'af_help_requests'):
            root.remove(n)
    button = copy.deepcopy(help)
    button.set('name', 'af_hire_details')
    button.set('text', LABELS[0][0])
    button.set('tooltip', LABELS[0][0])
    x, y, w, h = map(float, help.get('frame').split(','))
    if help.get('h_pos_align') != 'right' or help.get('v_pos_align') != 'bottom':
        raise ValueError('Find Group Help anchor changed')
    # The button follows Help on its right. Core keeps it there from Help's
    # resolved local rectangle after native alignment/move/resize.
    button.set('frame', ','.join(format(v, 'g') for v in (x + w + 4, y, 104, h)))
    root.append(button)
    requests = copy.deepcopy(button)
    requests.set('name', 'af_help_requests')
    requests.set('text', LABELS[10][0])
    requests.set('tooltip', LABELS[10][0])
    requests.set('frame', ','.join(format(v, 'g') for v in (x + w + 112, y, 97, h)))
    root.append(requests)
    panel = E.SubElement(root, 'Widget', type='container', name='af_hire_terms',
                         frame='3,136,394,216', flag='not_visible',
                         h_pos_align='right', v_pos_align='bottom')
    E.SubElement(panel, 'Skin', name='v5_tooltip', state='0')
    def line(name, y, key, **style):
        E.SubElement(panel, 'Widget', type='static', name=name, frame=f'14,{y},366,18', text=key,
                     flag='not_active;not_multiline;not_wordwrap', **style)
    line('af_hire_terms_title', 8, LABELS[0][0], font='ui_static_title', text_color='0.945,0.863,0.659,1.0', text_shade='True')
    E.SubElement(panel, 'Widget', type='image', name='af_hire_terms_rule', frame='14,28,366,7', preset='v5_seperator', flag='not_active')
    y = 38
    for heading, keys in ((8, (1, 2, 3)), (9, (4, 5, 6, 7))):
        line('af_hire_terms_group_' + str(heading), y, LABELS[heading][0], font='ui_default', text_color='0.84,0.72,0.47,1', text_shade='True')
        y += 18
        for i in keys:
            line('af_hire_term_' + str(i), y, LABELS[i][0], font='ui_default', text_shade='True')
            y += 18
        y += 8
    return encode_binary_xml(root)


def strings(payload):
    root = binary_xml(payload)
    for number, (name, body) in enumerate(LABELS, 990100300):
        row = next((e for e in root if e.findtext('name') == name), None)
        if row is None:
            if any(e.findtext('id') == str(number) for e in root):
                raise ValueError('Hire details string id collision: ' + name)
            row = E.SubElement(root, 'string')
            E.SubElement(row, 'id').text = str(number)
            E.SubElement(row, 'name').text = name
            E.SubElement(row, 'body')
        row.find('body').text = body
    return encode_binary_xml(root)


def patch_archive(path, localized=False):
    raw = path.read_bytes()
    changes = {}
    with read_pak(path) as source:
        infos = {i.filename: i for i in source.infolist()}
        if len(infos) != len(source.infolist()):
            raise ValueError('Duplicate UI archive member')
        for name in source.namelist():
            if name.lower().endswith('party_match_dialog.xml'):
                changes[name] = layout(source.read(name))
        if localized:
            changes[STRINGS_ENTRY] = strings(source.read(STRINGS_ENTRY))
    if not changes:
        return raw, []
    result, _ = rebuild(raw, changes, infos)
    before, _, _, version = parts(raw)
    after, _, _, current = parts(result)
    if version != current or list(before) != list(after):
        raise ValueError('UI archive structure changed')
    for name in before:
        if name not in changes and before[name][0] != after[name][0]:
            raise ValueError('Unrelated compressed UI member changed: ' + name)
    return result, list(changes)


def prepare(client, output):
    client, output = client.resolve(strict=True), output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external output directory')
    graphics = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text(encoding='utf-8-sig'))
    package = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text(encoding='utf-8-sig'))
    if graphics.get('restoredAt') or graphics['files'] != package['files']:
        raise ValueError('Graphics active/package records disagree')
    baseline = owned_renderer_backup(client, graphics['backupRoot'])
    theme = json.loads((client / 'Mods/AetherfallUI/manifest.json').read_text(encoding='utf-8-sig'))
    files, variants = [], []
    def stage(rel, data):
        target = output / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        prior = client / rel
        files.append(dict(path=rel, original=sha(prior.read_bytes()) if prior.is_file() else None,
                          installed=sha(data), bytes=len(data)))
    archives = [p.relative_to(client).as_posix() for p in (client / 'Data/ui').glob('*/*.pak')]
    archives.append('L10N/enu/data/data.pak')
    for rel in archives:
        with read_pak(client / rel) as z:
            names = [n for n in z.namelist() if n.lower().endswith('party_match_dialog.xml')]
        if not names:
            continue
        owned = next((r['entries'] for n, r in theme['archives'].items() if n.lower() == rel.lower()), {})
        if any(n in owned for n in names):
            raise ValueError('Find Group became theme-owned; prepare its variants together')
        localized = rel.lower().startswith('l10n/')
        data, changed = patch_archive(client / rel, localized)
        stage(rel, data)
        variants.append(dict(archive=rel, entries=changed))
        record = next((r for r in graphics['files'] if r['path'].lower() == rel.lower()), None)
        if not record:
            raise ValueError('UI archive lacks Graphics ownership: ' + rel)
        rel = record['path']
        saved = baseline / rel
        if (sha((client / rel).read_bytes()) != record['installed']
                or sha((client / 'Mods/GraphicsMenu/package' / rel).read_bytes()) != record['installed']
                or sha(saved.read_bytes()) != record['original']):
            raise ValueError('Graphics active/package/restore hashes disagree: ' + rel)
        restore, _ = patch_archive(saved, localized)
        stage('Mods/GraphicsMenu/package/' + rel, data)
        stage(baseline.relative_to(client).as_posix() + '/' + rel, restore)
        record.update(installed=sha(data), original=sha(restore))
    package['files'] = graphics['files']
    stage('Mods/GraphicsMenu/installed.json', json.dumps(graphics, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    inventory = json.loads((client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    staged = {r['path'].lower(): r for r in files}
    for rows in inventory['features'].values():
        for row in rows:
            if row.get('path', '').lower() in staged:
                new = staged[row['path'].lower()]
                row.update(installedSha256=new['installed'], bytes=new['bytes'])
    inventory['features']['PlayerBotHireTerms'] = [dict(path=r['path'], installedSha256=r['installed'], bytes=r['bytes'])
                                                for r in files if r['path'].lower().startswith(('data/ui/', 'l10n/'))]
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    record = dict(feature='native-find-group-hire-details', clientRoot=str(client), files=files,
                  variants=variants, sourceHashes={str(Path(__file__)): sha(Path(__file__).read_bytes())},
                  nativeHandlerRequired='AetherfallCore.dll lfg_hire.h', liveClientWritten=False)
    (output / 'manifest.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print('Prepared native Hire details:', len(files), 'files;', len(variants), 'layout archives; original read only.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.client, args.output)
