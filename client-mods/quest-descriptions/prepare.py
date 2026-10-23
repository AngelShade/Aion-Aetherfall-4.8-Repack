"""Prepare missing native English quest summaries and matching archive ownership."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RELATIVE = 'L10N/enu/Data/data.pak'
MEMBER = 'dialogs/quest_q3074.html'
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
sys.path.insert(0, str(ROOT / 'client-mods/bundle-opening'))
sys.path.insert(0, str(ROOT / 'client-mods/transmog-menu'))
from codec import binary_xml, read_pak
from prepare_client import parts, rebuild
from graphics_compat import owned_renderer_backup

spec = importlib.util.spec_from_file_location('quest_journal_codec', ROOT / 'client-mods/quest-red-journal/prepare.py')
journal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(journal)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def patch_archive(path, summary):
    raw = path.read_bytes()
    with read_pak(path) as source:
        native = source.read(MEMBER)
        text = journal.decode_html(native, Path(MEMBER).name)
        assert journal.encode_html(text, Path(MEMBER).name) == native, 'Native HTML roundtrip failed'
        before = ET.fromstring(text)
        assert before.find("HtmlPage[@name='quest_summary']") is None, 'Summary already exists; review instead of duplicating'
        patched_text = text.replace('</HtmlPages>', summary + '\n</HtmlPages>')
        after = ET.fromstring(patched_text)
        assert len(after) == len(before) + 1
        assert [ET.tostring(p) for p in after[:-1]] == [ET.tostring(p) for p in before]
        after[-1].tail = None
        assert ET.tostring(after[-1]) == ET.tostring(ET.fromstring(summary))
        encoded = journal.encode_html(patched_text, Path(MEMBER).name)
        assert journal.decode_html(encoded, Path(MEMBER).name) == patched_text
        result, _ = rebuild(raw, {MEMBER: encoded}, {e.filename: e for e in source.infolist()})
    old, _, _, version = parts(raw)
    new, _, _, changed_version = parts(result)
    assert version == changed_version and old.keys() == new.keys()
    assert all(old[n][0] == new[n][0] for n in old if n != MEMBER), 'Unrelated compressed member changed'
    return result, dict(preservedCompressedMembers=len(old)-1, preservedDialogPages=len(before),
                        beforeDialogSha256=sha(native), afterDialogSha256=sha(encoded))


def prepare(client, output):
    assert not output.exists() and not output.is_relative_to(client) and not output.is_relative_to(ROOT), 'Use fresh external staging'
    assert not (client.parent / 'REPACK.json').exists(), 'Original client only'
    summary = (HERE / 'quest_q3074_summary.html').read_text(encoding='utf-8').strip()
    ET.fromstring(summary)
    handler = ROOT / 'game-server/data/handlers/quest/theobomos/_3074DangerousProbability.java'
    server = handler.read_text(encoding='utf-8')
    assert 'registerQuestNpc(798193)' in server
    assert re.findall(r'case ([012]) -> (1000|5000|25000);', server) == [('0', '1000'), ('1', '5000'), ('2', '25000')]
    assert 'case 0 -> 1; case 1 -> Rnd.get(1, 3); case 2 -> Rnd.get(1, 6);' in server
    assert 'new QuestItems(186000005, count)' in server and 'decreaseByItemId(186000037, 1)' in server
    items = ET.parse(ROOT / 'game-server/data/static_data/items/item_templates.xml').getroot()
    assert items.find("item_template[@id='186000005']").get('name') == 'Platinum Coin'
    assert items.find("item_template[@id='186000037']").get('name') == "Angel's Eye"
    with read_pak(client / RELATIVE) as source:
        for table, key, label in [('people', 'STR_DIC_N_Shugo_LF2a_14', 'Nagrunerk'),
                                  ('item', 'STR_DIC_I_Angeleyes', "Angel's Eye")]:
            strings = binary_xml(source.read('strings/client_strings_dic_' + table + '.xml'))
            match = [s.findtext('body') for s in strings if s.findtext('name') == key]
            assert len(match) == 1 and match[0].split(';')[0] == label and key in summary

    state = load(client / 'Mods/GraphicsMenu/installed.json')
    package = load(client / 'Mods/GraphicsMenu/package/manifest.json')
    assert not state.get('restoredAt') and state['files'] == package['files']
    baseline = owned_renderer_backup(client, state['backupRoot'])
    locale = next(e for e in state['files'] if e['path'].casefold() == RELATIVE.casefold())
    assert sha((client / RELATIVE).read_bytes()) == locale['installed']
    assert sha((client / 'Mods/GraphicsMenu/package' / RELATIVE).read_bytes()) == locale['installed']
    assert sha((baseline / RELATIVE).read_bytes()) == locale['original']
    with read_pak(client / RELATIVE) as active, read_pak(baseline / RELATIVE) as restore:
        assert active.read(MEMBER) == restore.read(MEMBER), 'Restore has distinct quest text'
    active, active_checks = patch_archive(client / RELATIVE, summary)
    restored, restore_checks = patch_archive(baseline / RELATIVE, summary)
    output.mkdir(parents=True)
    records = []

    def stage(relative, data):
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        current = client / relative
        records.append(dict(path=relative, original=sha(current.read_bytes()), staged=sha(data)))

    stage(RELATIVE, active)
    stage('Mods/GraphicsMenu/package/' + RELATIVE, active)
    stage(baseline.relative_to(client).as_posix() + '/' + RELATIVE, restored)
    for entries in (state['files'], package['files']):
        next(e for e in entries if e['path'].casefold() == RELATIVE.casefold()).update(installed=sha(active), original=sha(restored))
    for relative, data in [('Mods/GraphicsMenu/installed.json', state), ('Mods/GraphicsMenu/package/manifest.json', package)]:
        stage(relative, (json.dumps(data, indent=2)+'\n').encode())
    installed = load(client / 'Mods/InstalledMods.json')
    for entries in installed['features'].values():
        for entry in entries:
            if entry.get('path', '').casefold() == RELATIVE.casefold():
                entry.update(installedSha256=sha(active), bytes=len(active))
    installed['features']['QuestDescriptions'] = [dict(path=RELATIVE, installedSha256=sha(active), bytes=len(active))]
    stage('Mods/InstalledMods.json', (json.dumps(installed, indent=2)+'\n').encode())
    with read_pak(output / RELATIVE) as check:
        assert check.read(MEMBER) == journal.encode_html(journal.decode_html(check.read(MEMBER), Path(MEMBER).name), Path(MEMBER).name)
        assert ET.fromstring(journal.decode_html(check.read(MEMBER), Path(MEMBER).name)).find("HtmlPage[@name='quest_summary']") is not None
    source_paths = [Path(__file__), HERE/'README.md', HERE/'quest_q3074_summary.html', handler,
                    ROOT/'client-mods/quest-red-journal/prepare.py', ROOT/'client-mods/bundle-opening/prepare_client.py',
                    ROOT/'client-mods/expanded-warehouse/codec.py', ROOT/'client-mods/transmog-menu/graphics_compat.py']
    manifest = dict(feature='quest-descriptions', questId=3074, clientRoot=str(client), originalOnly=True,
                    files=records, sourceHashes={str(p):sha(p.read_bytes()) for p in source_paths},
                    validation=dict(active=active_checks, restore=restore_checks, nativeDictionaryLinks=2,
                                    serverOffersVerified=True, nativeHtmlRoundtrip=True),
                    inventoryBefore=next(e['sha256'] for e in load(ROOT/'docs/INSTALLED_MODS.json')['clientFiles'] if e['path'].casefold()==RELATIVE.casefold()),
                    gameplayAcceptance='Restart client and open quest 3074; confirm the description and dictionary links are visible.',
                    installed=False, serverChanged=False, repackChanged=False)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('Prepared quest 3074 summary; native encoding, six existing pages and all other compressed members verified. Six affected files staged; client unchanged.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.client.resolve(strict=True), args.output.resolve())
