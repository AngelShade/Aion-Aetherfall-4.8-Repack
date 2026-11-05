"""Prepare quest 30500's Monodia journal text and Graphics menu records."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys as _ownership_sys
_ownership_sys.path.insert(0, str(next(p for p in Path(__file__).resolve().parents if p.name == 'client-mods') / 'dxvk'))
from client_ownership import independent as _owned_graphics, verify as _verify_graphics
import re
from xml.etree import ElementTree as ET

from prepare import ROOT, RELATIVE, journal, load, owned_renderer_backup, parts, read_pak, rebuild, sha

MEMBER = 'dialogs/30000_39999/quest_q30500.html'
BEFORE = 'Ancanus told you that the Rentus Base, a significant Reian post, was in trouble.'
INTERIM = 'You were told that the Rentus Base, a significant Reian post, was in trouble.'
AFTER = 'Monodia told you that the Rentus Base, a significant Reian post, was in trouble.'
FEATURE = 'quest-desperation-text-v1'


def patch_archive(path):
    raw = path.read_bytes()
    with read_pak(path) as source:
        native = source.read(MEMBER)
        text = journal.decode_html(native, Path(MEMBER).name)
        assert journal.encode_html(text, Path(MEMBER).name) == native, 'Native HTML roundtrip failed'
        pages = ET.fromstring(text).findall('HtmlPage')
        references = {p.get('name'): len(re.findall('Ancanus', ET.tostring(p, encoding='unicode'), re.I)) for p in pages}
        complete = [p for p in pages if p.get('name') == 'quest_complete']
        assert len(complete) == 1
        matches = [sentence for sentence in (BEFORE, INTERIM, AFTER) if sentence in text]
        assert len(matches) == 1, 'Unexpected journal wording'
        previous = matches[0]
        assert text.count(previous) == 1 and previous in ''.join(complete[0].itertext())
        expected_references = int(previous == BEFORE)
        assert references['quest_complete'] == expected_references
        assert sum(references.values()) == expected_references, 'Additional Ancanus references require review'
        changed = text.replace(previous, AFTER, 1)
        updated = ET.fromstring(changed).findall('HtmlPage')
        assert [p.get('name') for p in pages] == [p.get('name') for p in updated]
        assert all(ET.tostring(a) == ET.tostring(b) for a, b in zip(pages, updated) if a.get('name') != 'quest_complete')
        encoded = journal.encode_html(changed, Path(MEMBER).name)
        assert journal.decode_html(encoded, Path(MEMBER).name) == changed
        result = raw if encoded == native else rebuild(raw, {MEMBER: encoded}, {e.filename: e for e in source.infolist()})[0]
    old, central, end, version = parts(raw)
    new, new_central, new_end, new_version = parts(result)
    assert version == new_version and list(old) == list(new) and list(central) == list(new_central)
    assert end[:12] + end[20:] == new_end[:12] + new_end[20:]
    for name in old:
        if name != MEMBER:
            assert old[name][0] == new[name][0], 'Unrelated compressed member changed: ' + name
            assert central[name][:42] + central[name][46:] == new_central[name][:42] + new_central[name][46:]
    return result, dict(pagesChecked=references, preservedPages=len(pages)-1, nativeHtmlRoundtrip=True,
                        preservedCompressedMembers=len(old)-1, unchangedCentralMetadataExceptOffsets=True,
                        beforeDialogSha256=sha(native), afterDialogSha256=sha(encoded), alreadyCurrent=text == changed,
                        beforeSentence=previous, afterSentence=AFTER)


def prepare(client, output):
    assert not output.exists() and not output.is_relative_to(client) and not output.is_relative_to(ROOT), 'Use fresh external staging'
    assert not (client.parent / 'REPACK.json').exists(), 'Original client only'
    state = load(client / 'Mods/GraphicsMenu/installed.json')
    package = load(client / 'Mods/GraphicsMenu/package/manifest.json')
    assert Path(state['clientRoot']).resolve() == client and not state.get('restoredAt')
    assert state['files'] == package['files']
    independent_graphics = _owned_graphics(client)
    if independent_graphics:
        _verify_graphics(client)
    baseline = client if independent_graphics else owned_renderer_backup(client, state['backupRoot'])
    preserved = []
    if not independent_graphics:
        for entry in state['files']:
            relative = entry['path']
            for path, expected in [(client / relative, entry['installed']),
                                   (client / 'Mods/GraphicsMenu/package' / relative, entry['installed'])]:
                assert sha(path.read_bytes()) == expected, 'Graphics payload changed: ' + str(path)
                if relative != RELATIVE:
                    preserved.append(dict(path=path.relative_to(client).as_posix(), sha256=expected))
            if entry['original'] is not None:
                path = baseline / relative
                assert sha(path.read_bytes()) == entry['original'], 'Graphics restore image changed: ' + str(path)
                if relative != RELATIVE:
                    preserved.append(dict(path=path.relative_to(client).as_posix(), sha256=entry['original']))
    renderer = client / 'Mods/Renderer/installed.json'
    preserved.append(dict(path=renderer.relative_to(client).as_posix(), sha256=sha(renderer.read_bytes())))
    with read_pak(client / RELATIVE) as active, read_pak(baseline / RELATIVE) as restore:
        assert active.read(MEMBER) == restore.read(MEMBER), 'Restore has distinct quest text'
    active, active_checks = patch_archive(client / RELATIVE)
    restored, restore_checks = patch_archive(baseline / RELATIVE)
    output.mkdir(parents=True)
    records = []

    def stage(relative, data):
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        records.append(dict(path=relative, original=sha((client / relative).read_bytes()), staged=sha(data)))

    stage(RELATIVE, active)
    if not independent_graphics:
        stage('Mods/GraphicsMenu/package/' + RELATIVE, active)
        stage(baseline.relative_to(client).as_posix() + '/' + RELATIVE, restored)
        for entries in (state['files'], package['files']):
            next(e for e in entries if e['path'] == RELATIVE).update(installed=sha(active), original=sha(restored))
        state['rebasedAt'] = datetime.now(timezone.utc).isoformat()
        for relative, data in [('Mods/GraphicsMenu/installed.json', state), ('Mods/GraphicsMenu/package/manifest.json', package)]:
            stage(relative, (json.dumps(data, indent=2)+'\n').encode())
    installed = load(client / 'Mods/InstalledMods.json')
    for entries in installed['features'].values():
        for entry in entries:
            if entry.get('path', '').casefold() == RELATIVE.casefold():
                entry.update(installedSha256=sha(active), bytes=len(active))
    stage('Mods/InstalledMods.json', (json.dumps(installed, indent=2)+'\n').encode())
    sources = [Path(__file__), Path(__file__).with_name('prepare.py'), ROOT/'client-mods/quest-red-journal/prepare.py',
               ROOT/'client-mods/transmog-menu/graphics_compat.py', ROOT/'client-mods/transmog-menu/install_window_queue.py',
               ROOT/'client-mods/bundle-opening/prepare_client.py', ROOT/'client-mods/expanded-warehouse/codec.py']
    manifest = dict(feature=FEATURE, clientRoot=str(client), originalOnly=True, files=records, preservedFiles=preserved,
                    graphicsCompatibility=(dict(ownershipSchema=2, localized=True) if independent_graphics else dict(backupName=baseline.name, localized=True)),
                    sourceHashes={p.relative_to(ROOT).as_posix():sha(p.read_bytes()) for p in sources},
                    validation=dict(active=active_checks, restore=restore_checks), member=MEMBER,
                    before=active_checks['beforeSentence'], after=AFTER, acceptedBefore=[BEFORE, INTERIM])
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('Prepared quest 30500: one sentence; all eight pages checked; other compressed entries and Graphics restore ownership verified.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.client.resolve(strict=True), args.output.resolve())
