"""Let food, drinks and potions that did not stack stack to 1,000 (owner request, 9 Nov 2026).

Server: game-server/data/static_data/items/item_templates.xml gets max_stack_count="1000", the CAN_SPLIT mask bit and
no LIMIT_ONE bit (own only one). Client: Data/Items/Items.pak client_items_misc.xml gets max_stack_count 1000,
can_split TRUE and lore FALSE for the same IDs. Every other archive member and record stays identical.

  python prepare.py server            patch the server templates in place (idempotent)
  python prepare.py client OUT        patch the client's installed Items.pak into OUT/Data/Items/Items.pak
  python prepare.py check [PAK]       confirm server and client agree for every ID (default: installed client)
"""
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
from codec import read_pak, binary_xml, encode_binary_xml, encode_pak  # noqa: E402

CLIENT = Path.home() / 'Downloads/Aion Client Development Server 4.8 NA'
TEMPLATES = ROOT / 'game-server/data/static_data/items/item_templates.xml'
MEMBER = 'client_items_misc.xml'
STACK = 1000
LIMIT_ONE, CAN_SPLIT = 1, 1 << 13
# Food (160) and potions (162) with max_stack_count 1, minus event, QA/test, NPC, time-limited and Infinite items and
# Taloc Fruit (a Taloc's Hollow instance item that already has 1,000 uses).
IDS = (160002399, 160002400, 160002401, 160002402, 160002412, 160002413, 160002414, 160002415,
       160005048, 160005049, 160005051, 160005057, 160005058, 160005059, 160009017, 160010238, 162000131)
CLIENT_VALUES = {'max_stack_count': str(STACK), 'can_split': 'TRUE', 'lore': 'FALSE'}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def server():
    text = TEMPLATES.read_bytes().decode('utf-8')
    changed = 0
    for ident in IDS:
        match = re.search(r'<item_template id="%d" [^>]*>' % ident, text)
        if not match:
            raise ValueError('Missing server template %d' % ident)
        tag = match.group(0)
        mask = int(re.search(r' mask="(\d+)"', tag).group(1))
        new = re.sub(r' mask="\d+"', ' mask="%d"' % ((mask | CAN_SPLIT) & ~LIMIT_ONE), tag)
        if ' max_stack_count="' in new:
            new = re.sub(r' max_stack_count="\d+"', ' max_stack_count="%d"' % STACK, new)
        else:
            new = re.sub(r'( mask="\d+")', r'\1 max_stack_count="%d"' % STACK, new, count=1)
        if new != tag:
            text = text[:match.start()] + new + text[match.end():]
            changed += 1
    TEMPLATES.write_bytes(text.encode('utf-8'))
    print('server: %d of %d templates changed' % (changed, len(IDS)))


def server_values():
    text = TEMPLATES.read_text(encoding='utf-8')
    result = {}
    for ident in IDS:
        tag = re.search(r'<item_template id="%d" [^>]*>' % ident, text).group(0)
        stack = re.search(r' max_stack_count="(\d+)"', tag)
        mask = int(re.search(r' mask="(\d+)"', tag).group(1))
        result[ident] = (int(stack.group(1)) if stack else 1, bool(mask & CAN_SPLIT), bool(mask & LIMIT_ONE))
    return result


def client(output):
    source = CLIENT / 'Data/Items/Items.pak'
    original_bytes = source.read_bytes()
    with read_pak(source) as archive:
        original_member = archive.read(MEMBER)
    root = binary_xml(original_member)
    records = {int(e.findtext('id')): e for e in root if e.findtext('id', '').isdigit() and int(e.findtext('id')) in IDS}
    if missing := set(IDS) - records.keys():
        raise ValueError('Missing client records: %s' % sorted(missing))
    changes = {}
    for ident, record in records.items():
        before = {key: record.findtext(key) for key in CLIENT_VALUES}
        for key, value in CLIENT_VALUES.items():
            child = record.find(key)
            if child is None:
                raise ValueError('Client record %d has no %s' % (ident, key))
            child.text = value
        changes[ident] = {'before': before, 'after': dict(CLIENT_VALUES)}
    changed_member = encode_binary_xml(root)
    # Everything outside the touched fields must survive a decode unchanged.
    untouched = binary_xml(original_member)
    for e in untouched:
        if e.findtext('id', '').isdigit() and int(e.findtext('id')) in IDS:
            for key, value in CLIENT_VALUES.items():
                e.find(key).text = value
    assert encode_binary_xml(untouched) == changed_member
    buffer = io.BytesIO()
    with read_pak(source) as archive, zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_DEFLATED) as target:
        for entry in archive.infolist():
            target.writestr(entry, changed_member if entry.filename == MEMBER else archive.read(entry.filename))
    destination = Path(output) / 'Data/Items/Items.pak'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(encode_pak(buffer.getvalue()))
    with read_pak(source) as before, read_pak(destination) as after:
        assert after.testzip() is None
        assert before.namelist() == after.namelist()
        for name in before.namelist():
            if name != MEMBER:
                assert before.read(name) == after.read(name), name
        assert after.read(MEMBER) == changed_member
    receipt = {'source': str(source), 'source_sha256': sha256(original_bytes), 'output': str(destination),
               'output_sha256': sha256(destination.read_bytes()), 'member': MEMBER, 'stack': STACK,
               'changes': {str(k): v for k, v in sorted(changes.items())}}
    (Path(output) / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('client: %d records patched -> %s' % (len(changes), destination))
    check(destination)


def check(pak=None):
    pak = Path(pak) if pak else CLIENT / 'Data/Items/Items.pak'
    with read_pak(pak) as archive:
        root = binary_xml(archive.read(MEMBER))
    records = {int(e.findtext('id')): {k: e.findtext(k) for k in CLIENT_VALUES} for e in root
               if e.findtext('id', '').isdigit() and int(e.findtext('id')) in IDS}
    problems = []
    for ident, (stack, split, limit) in server_values().items():
        c = records.get(ident)
        if stack != STACK or not split or limit:
            problems.append('%d server stack=%d split=%s limit_one=%s' % (ident, stack, split, limit))
        if c != CLIENT_VALUES:
            problems.append('%d client %s' % (ident, c))
    if problems:
        raise SystemExit('Stacking mismatch:\n  ' + '\n  '.join(problems))
    print('check: %d items stack to %d on server and client (%s)' % (len(IDS), STACK, pak))


if __name__ == '__main__':
    command = sys.argv[1] if len(sys.argv) > 1 else ''
    if command == 'server':
        server()
    elif command == 'client' and len(sys.argv) > 2:
        client(sys.argv[2])
    elif command == 'check':
        check(sys.argv[2] if len(sys.argv) > 2 else None)
    else:
        raise SystemExit(__doc__)
