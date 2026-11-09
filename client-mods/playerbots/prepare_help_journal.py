"""Prepare the help request's buttons inside the native quest details; the original is read only.

Owner 8 Nov: everything in the Help requests window also in the native quest window.
DlgQuestSummary (quest_summary_dialog) gains three af_help_* buttons just under its
HTML view, inside the papyrus (y 428; at 455 they sat on the lower roll). They
start hidden; AetherfallCore help_journal.h shows them only for the help quest and
answers their clicks. Same PAK codec, Graphics recovery ownership and inventory
handling as prepare_hire_terms.py; every variant of the layout is transformed.

Owner 9 Nov: AetherfallCore 1.3.15 keeps these three buttons hidden. The help page now
carries native in-page Locate/Track buttons under its objectives and Collect reward is
Q's own button, as for a normal quest; the installed layout needs no change for that.
"""
import argparse
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
sys.path.insert(0, str(HERE.parent / 'dxvk'))
from client_ownership import independent as owned_graphics, verify as verify_graphics

LAYOUT = 'quest_summary_dialog.xml'
STRINGS_ENTRY = 'strings/client_strings_ui.xml'
FIRST_ID = 990100400
# (widget, string key, label, frame) - three equal buttons spanning the HTML view's width.
BUTTONS = [
    ('af_help_location', 'STR_AETHERFALL_HELP_JOURNAL_LOCATION', 'Show location', '23,428,111,24'),
    ('af_help_track', 'STR_AETHERFALL_HELP_JOURNAL_TRACK', 'Track', '137,428,111,24'),
    ('af_help_claim', 'STR_AETHERFALL_HELP_JOURNAL_CLAIM', 'Collect reward', '251,428,111,24'),
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def layout(payload):
    root = binary_xml(payload)
    if root.get('name') != 'quest_summary_dialog' or root.get('type') != 'dlg_quest_summary':
        raise ValueError('Unexpected quest details layout')
    view = next((n for n in root if n.get('name') == 'quest_summary'), None)
    if view is None or view.get('type') != 'htmlview' or view.get('frame') != '23,32,339,383':
        raise ValueError('Quest details HTML view changed; review the button row')
    if root.get('frame', '').split(',')[3:] != ['512']:
        raise ValueError('Quest details height changed; review the button row')
    for n in list(root):
        if (n.get('name') or '').startswith('af_help_'):
            root.remove(n)
    for name, key, _, frame in BUTTONS:
        # The journal's own button style (quest_dialog share/giveup/cancel).
        E.SubElement(root, 'Widget', defbutton='False', flag='not_visible', font='ui_default', frame=frame,
                     name=name, preset='v5_button', text=key, tooltip=key, type='button', v_pos_align='bottom')
    return encode_binary_xml(root)


def strings(payload):
    root = binary_xml(payload)
    for number, (_, key, label, _) in enumerate(BUTTONS, FIRST_ID):
        row = next((e for e in root if e.findtext('name') == key), None)
        if row is None:
            if any(e.findtext('id') == str(number) for e in root):
                raise ValueError('Help journal string id collision: ' + key)
            row = E.SubElement(root, 'string')
            E.SubElement(row, 'id').text = str(number)
            E.SubElement(row, 'name').text = key
            E.SubElement(row, 'body')
        row.find('body').text = label
    return encode_binary_xml(root)


def patch_archive(path, localized=False):
    raw = path.read_bytes()
    changes = {}
    with read_pak(path) as source:
        infos = {i.filename: i for i in source.infolist()}
        if len(infos) != len(source.infolist()):
            raise ValueError('Duplicate UI archive member')
        for name in source.namelist():
            if name.lower().endswith(LAYOUT):
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
    # Read back what was written: the layout parses with exactly the three hidden buttons.
    with read_pak_bytes(result) as check:
        for name in changes:
            if name.lower().endswith(LAYOUT):
                got = [w.get('name') for w in binary_xml(check.read(name)) if (w.get('name') or '').startswith('af_help_')]
                if got != [b[0] for b in BUTTONS]:
                    raise ValueError('Written layout lacks the help buttons: ' + name)
    return result, list(changes)


def read_pak_bytes(data):
    import tempfile
    handle = tempfile.NamedTemporaryFile(suffix='.pak', delete=False)
    handle.write(data)
    handle.close()
    class Reader:
        def __enter__(self):
            self.z = read_pak(Path(handle.name)); self.inner = self.z.__enter__(); return self.inner
        def __exit__(self, *a):
            self.z.__exit__(*a); Path(handle.name).unlink()
    return Reader()


def prepare(client, output):
    client, output = client.resolve(strict=True), output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external output directory')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('Original client only')
    # Independent Graphics ownership (current clients): UI archives need no Graphics
    # package or restore copies; only its hooks are verified, as prepare_lfg_movement does.
    independent = owned_graphics(client)
    if independent:
        verify_graphics(client)
    else:
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
            names = [n for n in z.namelist() if n.lower().endswith(LAYOUT)]
        if not names:
            continue
        owned = next((r['entries'] for n, r in theme['archives'].items() if n.lower() == rel.lower()), {})
        if any(n in owned for n in names):
            raise ValueError('Quest details became theme-owned; prepare its variants together')
        localized = rel.lower().startswith('l10n/')
        data, changed = patch_archive(client / rel, localized)
        stage(rel, data)
        variants.append(dict(archive=rel, entries=changed))
        if independent:
            continue
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
    if not variants:
        raise ValueError('No quest details layout found')
    if not independent:
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
    inventory['features']['PlayerBotHelpJournal'] = [dict(path=r['path'], installedSha256=r['installed'], bytes=r['bytes'])
                                                  for r in files if r['path'].lower().startswith(('data/ui/', 'l10n/'))]
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    record = dict(feature='native-quest-details-help-buttons', clientRoot=str(client), files=files,
                  variants=variants, sourceHashes={str(Path(__file__)): sha(Path(__file__).read_bytes())},
                  nativeHandlerRequired='AetherfallCore.dll 1.3.9 help_journal.h', liveClientWritten=False)
    (output / 'manifest.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print('Prepared native help journal buttons:', len(files), 'files;', len(variants), 'layout archives; original read only.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.client, args.output)
