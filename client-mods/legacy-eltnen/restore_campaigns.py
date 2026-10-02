"""Align native client prerequisites with the restored legacy Eltnen campaigns.

Creates a verified staged archive and manifest by default. --install backs up and
replaces only Data/Quest/Quest.pak. Run with the game closed, then restart the client.
"""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import shutil
import sys
import zipfile
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
from codec import binary_xml, encode_binary_xml, encode_pak, read_pak


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'output/legacy-eltnen-client')
    parser.add_argument('--install', action='store_true')
    args = parser.parse_args()
    archive = args.client / 'Data/Quest/Quest.pak'
    original = archive.read_bytes()
    source = read_pak(archive)
    tree = binary_xml(source.read('quest.xml'))
    before = {quest.findtext('id'): ET.tostring(quest) for quest in tree}
    changed = set()
    for quest in tree:
        qid = quest.findtext('id')
        if qid in {str(i) for i in range(14020, 14027)}:
            for child in list(quest):
                if child.tag in ('unfinished_quest_cond1', 'noacquired_quest_cond1') and child.text == 'Q1300':
                    quest.remove(child)
            changed.add(qid)
        elif qid == '1039':
            assert quest.findtext('finished_quest_cond1') == 'Q1035,Q1016'
            alternative = quest.find('finished_quest_cond2')
            if alternative is None:
                alternative = ET.SubElement(quest, 'finished_quest_cond2')
            alternative.text = 'Q1035,Q14016'
            changed.add(qid)
    assert changed == {'1039', *(str(i) for i in range(14020, 14027))}
    for quest in tree:
        if quest.findtext('id') not in changed:
            assert ET.tostring(quest) == before[quest.findtext('id')]
    encoded = encode_binary_xml(tree)
    assert ET.tostring(binary_xml(encoded)) == ET.tostring(tree)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as target:
        target.comment = source.comment
        for entry in source.infolist():
            target.writestr(copy.copy(entry), encoded if entry.filename == 'quest.xml' else source.read(entry.filename))
    args.output.mkdir(parents=True, exist_ok=True)
    staged = args.output / 'Quest.pak'
    staged.write_bytes(encode_pak(buffer.getvalue()))
    verified = read_pak(staged)
    assert verified.namelist() == source.namelist()
    for name in source.namelist():
        assert verified.read(name) == (encoded if name == 'quest.xml' else source.read(name))
    manifest = {'target': str(archive), 'original_sha256': sha(original),
                'patched_sha256': sha(staged.read_bytes()), 'changed_quests': sorted(changed),
                'other_quests_preserved': len(before) - len(changed), 'archive_entries_verified': len(source.namelist())}
    if args.install:
        assert sha(archive.read_bytes()) == manifest['original_sha256'], 'Archive changed since staging'
        backup = args.output / ('Quest.original.' + sha(original)[:12] + '.pak')
        if not backup.exists():
            backup.write_bytes(original)
        manifest['backup'] = str(backup)
        shutil.copyfile(staged, archive)
        assert sha(archive.read_bytes()) == manifest['patched_sha256']
        manifest['installed'] = True
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
