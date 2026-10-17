"""Keep the native quest reward preview aligned with the customized server rewards.

Stages Data/Quest/Quest.pak only. No client lifecycle or installation is performed.
All other quest records and compressed archive members are preserved.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
sys.path.insert(0, str(ROOT / 'client-mods/bundle-opening'))
from codec import binary_xml, encode_binary_xml, read_pak
from prepare_client import rebuild, parts

CHANGES = {
    '29045': ('reward_item1_2', 'petcard_deco_l_Debrie_01_a 1', 'petcard_mult_u_BabyTiamat_01 1', '190020208'),
    '29048': ('reward_item1_1', 'add_customize_motion_ninja_01', 'add_customize_motion_ninja_02', '188508001'),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def prepare(client, output):
    assert not output.exists(), 'Use a fresh staging directory'
    archive = client / 'Data/Quest/Quest.pak'
    original = archive.read_bytes()
    server = ET.parse(ROOT / 'game-server/data/static_data/quest_data/quest_data.xml').getroot()
    with read_pak(archive) as source:
        tree = binary_xml(source.read('quest.xml'))
        before = [ET.tostring(q) for q in tree]
        changed = set()
        for index, quest in enumerate(tree):
            qid = quest.findtext('id')
            if qid not in CHANGES:
                continue
            field, old, new, item_id = CHANGES[qid]
            assert server.find(f"quest[@id='{qid}']/rewards/reward_item[@item_id='{item_id}']") is not None, 'Server reward differs'
            child = quest.find(field)
            assert child is not None and child.text == old, ('Unexpected client reward', qid)
            child.text = new
            changed.add(qid)
            expected = ET.fromstring(before[index])
            expected.find(field).text = new
            assert ET.tostring(quest) == ET.tostring(expected)
        assert changed == set(CHANGES)
        for index, quest in enumerate(tree):
            if quest.findtext('id') not in CHANGES:
                assert ET.tostring(quest) == before[index], 'Unrelated quest changed'
        encoded = encode_binary_xml(tree)
        assert ET.tostring(binary_xml(encoded)) == ET.tostring(tree), 'Native binary XML roundtrip failed'
        infos = {entry.filename: entry for entry in source.infolist()}
        patched, _ = rebuild(original, {'quest.xml': encoded}, infos)
        output.mkdir(parents=True)
        staged = output / 'Quest.pak'
        staged.write_bytes(patched)
        with read_pak(staged) as check:
            assert check.namelist() == source.namelist()
            for name in source.namelist():
                assert check.read(name) == (encoded if name == 'quest.xml' else source.read(name)), name
        old_records, _, _, version = parts(original)
        new_records, _, _, new_version = parts(patched)
        assert version == new_version
        for name in old_records:
            if name != 'quest.xml':
                assert old_records[name][0] == new_records[name][0], 'Unchanged compressed member differs'
        manifest = dict(client=str(client), path='Data/Quest/Quest.pak', before=sha(original), after=sha(patched),
                        staged=str(staged), changes=CHANGES, otherQuestsPreserved=len(before)-len(CHANGES),
                        otherArchiveEntriesPreserved=len(infos)-1, lifecyclePerformed=False)
        (output / 'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
        print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    prepare(args.client.resolve(strict=True), args.output.resolve())
