"""Stage the native English Red Journal continuation, preserving other PAK members."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import sys
import zipfile
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'client-mods/custom-quests/Quests/3999-TheUnfinishedEntry'
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
sys.path.insert(0, str(ROOT / 'client-mods/bundle-opening'))
from codec import binary_xml, encode_binary_xml, read_pak
from prepare_client import encrypted_zip, parts

QID = '3999'
TITLE_ID = '1801364'
TITLE = 'The Unfinished Entry'
DIALOG = 'dialogs/quest_q3999.html'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def html_stream(payload, filename):
    # Aion's symmetric filename-keyed 0x81 stream. Algorithm documented by
    # giordanidev/aion-pak-manager, backend/parse/html-crypt.ts (AIONdisasm 1.6.5).
    stem = Path(filename).stem
    edx = ebx = 0
    for i, c in enumerate(stem):
        value = (ord(c) & 15) + i
        edx = (edx + value) & 0xffffffff
        ebx ^= value
    result = bytearray()
    for value in payload:
        edx = ((edx + 0x1d) ^ ebx) & 0xffffffff
        ebx = (ebx + 3) & 0xffffffff
        result.append(value ^ (edx & 255))
    return bytes(result)


def encode_html(text, filename):
    # The client expects the original UTF-16 document with BOM inside the blob.
    text = re.sub(r'encoding="UTF-8"', 'encoding="UTF-16"', text, count=1)
    return b'\x81' + html_stream(b'\x81' + text.encode('utf-16'), filename)


def decode_html(data, filename):
    assert data[:1] == b'\x81'
    plain = html_stream(data[1:], filename)
    assert plain[:3] == b'\x81\xff\xfe'
    return plain[1:].decode('utf-16')


def load_registration():
    registration = ET.parse(ASSETS/'registration.xml').getroot()
    # Native binary XML has no inter-element tails or formatting whitespace.
    for node in registration.iter():
        node.tail = None
        if node.text is not None and not node.text.strip():
            node.text = None
    return registration


def extend_archive(original, replacements, additions):
    """Preserve every untouched compressed entry; append new members in order."""
    old, central, end, version = parts(original)
    temp = io.BytesIO()
    with zipfile.ZipFile(temp, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in {**replacements, **additions}.items():
            z.writestr(name, data)
    changed, new_central, _, _ = parts(encrypted_zip(temp.getvalue(), version))
    result = bytearray()
    offsets = {}
    names = list(old) + list(additions)
    assert not set(additions) & set(old)
    for name in names:
        offsets[name] = len(result)
        result.extend((changed[name] if name in changed else old[name])[0])
    central_start = len(result)
    for name in names:
        record = bytearray(new_central[name] if name in new_central else central[name])
        struct.pack_into('<I', record, 42, offsets[name])
        result.extend(record)
    central_size = len(result) - central_start
    tail = bytearray(end)
    struct.pack_into('<HHII', tail, 8, len(names), len(names), central_size, central_start)
    result.extend(tail)
    return bytes(result)


def prepare(client, output):
    assert not output.exists(), 'Use fresh external staging'
    assert ROOT not in output.parents and client not in output.parents
    template = ET.parse(ROOT / 'game-server/data/static_data/quest_data/quest_data.xml').getroot().find(f"quest[@id='{QID}']")
    assert template is not None and template.get('nameId') == TITLE_ID
    assert template.find('start_conditions/finished').get('quest_id') == '3060'
    output.mkdir(parents=True)
    files = []
    for relative in ('Data/Quest/Quest.pak', 'L10N/ENU/data/data.pak'):
        live = client / relative
        original = live.read_bytes()
        replacements, additions = {}, {}
        with read_pak(live) as z:
            if relative.startswith('Data/Quest'):
                tree = binary_xml(z.read('quest.xml'))
                assert all(q.findtext('id') != QID for q in tree), 'Quest ID already used'
                quest = copy.deepcopy(next(q for q in tree if q.findtext('id') == '3060'))
                for field, value in dict(id=QID, name='Q'+QID, desc='STR_QUEST_NAME_Q'+QID).items():
                    quest.find(field).text = value
                quest.remove(quest.find('quest_work_item1'))
                ET.SubElement(quest, 'finished_quest_cond1').text = 'Q3060'
                for field, value in dict(reward_exp1='5390338', reward_gold1='47430').items():
                    assert quest.findtext(field) == value
                tree.append(quest)
                replacements['quest.xml'] = encode_binary_xml(tree)
                assert ET.tostring(binary_xml(replacements['quest.xml'])) == ET.tostring(tree)
                # Native objective guidance for the scripted kill at progress 2.
                csv = z.read('quest_script_monster.csv')
                assert not re.search(rb'(?m)^3999,', csv)
                replacements['quest_script_monster.csv'] = csv + b'3999,Progress(2),,killedByUser,,1,LF2A_ShugoMaleNMQ_50_An\r\n'
                # New quest IDs also need a native client quest-class registration.
                # ScriptDLL64's compiled retail classes do not know custom Q3999.
                registrations = binary_xml(z.read('data_driven_quest.xml'))
                assert all(q.findtext('id') != QID for q in registrations)
                registration = load_registration()
                assert registration.findtext('id') == QID and registration.findtext('value0_acquire_') == 'Shugo_LF2a_14'
                existing_registrations = [ET.tostring(q) for q in registrations]
                registrations.append(registration)
                replacements['data_driven_quest.xml'] = encode_binary_xml(registrations)
                checked_registrations = binary_xml(replacements['data_driven_quest.xml'])
                assert len(checked_registrations) == len(existing_registrations)+1
                assert [ET.tostring(q) for q in checked_registrations[:-1]] == existing_registrations
            else:
                # Verify the stream against the installed native document before encoding.
                native = z.read('dialogs/quest_q3060.html')
                decoded = decode_html(native, 'quest_q3060.html')
                assert 'Titirunerk took this along with all my' in decoded
                assert encode_html(decoded, 'quest_q3060.html') == native
                strings = binary_xml(z.read('strings/client_strings_quest.xml'))
                assert all(s.findtext('id') != TITLE_ID and s.findtext('name') != 'STR_QUEST_NAME_Q'+QID for s in strings)
                s = ET.SubElement(strings, 'string')
                for field, value in dict(id=TITLE_ID, name='STR_QUEST_NAME_Q'+QID, body=TITLE).items():
                    ET.SubElement(s, field).text = value
                replacements['strings/client_strings_quest.xml'] = encode_binary_xml(strings)
                text = (ASSETS/'dialog.html').read_text(encoding='utf-8')
                ET.fromstring(text)
                additions[DIALOG] = encode_html(text, Path(DIALOG).name)
                assert ET.tostring(ET.fromstring(decode_html(additions[DIALOG], Path(DIALOG).name))) == ET.tostring(ET.fromstring(text))
            patched = extend_archive(original, replacements, additions)
            staged = output / relative
            staged.parent.mkdir(parents=True, exist_ok=True)
            staged.write_bytes(patched)
            with read_pak(staged) as check:
                assert check.namelist() == z.namelist() + list(additions)
                for name in z.namelist():
                    assert check.read(name) == (replacements[name] if name in replacements else z.read(name)), name
                for name, value in additions.items():
                    assert check.read(name) == value
                if relative.startswith('Data/Quest'):
                    before, after = binary_xml(z.read('quest.xml')), binary_xml(check.read('quest.xml'))
                else:
                    before = binary_xml(z.read('strings/client_strings_quest.xml'))
                    after = binary_xml(check.read('strings/client_strings_quest.xml'))
                assert len(after) == len(before) + 1
                assert all(ET.tostring(a) == ET.tostring(b) for a, b in zip(before, after))
            old_records, _, _, _ = parts(original)
            new_records, _, _, _ = parts(patched)
            for name in old_records:
                if name not in replacements:
                    assert old_records[name][0] == new_records[name][0], name
            files.append(dict(path=relative, before=sha(original), after=sha(patched), changed=list(replacements), added=list(additions), preservedCompressedMembers=len(old_records)-len(replacements)))
    sources = ['client-mods/quest-red-journal/prepare.py', str((ASSETS/'dialog.html').relative_to(ROOT)),
               str((ASSETS/'registration.xml').relative_to(ROOT)),
               'game-server/data/handlers/quest/theobomos/_3999TheUnfinishedEntry.java',
               'game-server/data/static_data/quest_data/quest_data.xml',
               'game-server/data/static_data/quest_script_data/theobomos.xml']
    manifest = dict(feature='red-journal-continuation', questId=int(QID), title=TITLE, client=str(client), files=files,
                    sourceHashes={n: sha((ROOT/n).read_bytes()) for n in sources}, gameplayVerified=False)
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print('Prepared quest 3999; native dialogue roundtrip and unrelated quest/string/compressed-member preservation passed.')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    prepare(a.client.resolve(strict=True), a.output.resolve())
