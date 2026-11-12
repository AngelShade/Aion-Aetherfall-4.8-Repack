"""Add the maintained Aetherfall translations to an existing Simplified Chinese PAK."""
import argparse
from collections import Counter
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
import runpy
import sys

sys.dont_write_bytecode = True
from prepare_text_pack import codec, crypt_html, sha, tokens


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PACK = Path('Mods/Languages/Packs/chs/data/data.pak')
ENGLISH = Path('L10N/enu/data/data.pak')
NATIVE = runpy.run_path(str(ROOT / 'client-mods/quest-red-journal/prepare.py'))
PAGE = re.compile(r'<HtmlPage\b[^>]*>.*?</HtmlPage>', re.IGNORECASE | re.DOTALL)
PAGE_NAME = re.compile(r"""\bname\s*=\s*(['"])(.*?)\1""", re.IGNORECASE)
MARKUP = re.compile(r"""<(?:[^<>"']|"[^"]*"|'[^']*')*>""")
ESCAPES = re.compile(r'%%|\\[nrt]')
HAN = re.compile(r'[\u3400-\u9fff]')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def check_translation(english, chinese, label):
    require(HAN.search(chinese), f'Chinese text missing: {label}')
    require(MARKUP.findall(english) == MARKUP.findall(chinese), f'Markup changed: {label}')
    require(tokens(english) == tokens(chinese) and
            Counter(ESCAPES.findall(english)) == Counter(ESCAPES.findall(chinese)),
            f'Format placeholders changed: {label}')


def translated_strings(english, chinese, entries, name):
    source = codec.binary_xml(english)
    target = codec.binary_xml(chinese)
    source_rows = {row.findtext('name', '').lower(): row for row in source}
    target_rows = {row.findtext('name', '').lower(): row for row in target}
    target_ids = {row.findtext('id') for row in target}
    for key, entry in entries.items():
        label = f'{name}/{key}'
        ident, original, translated = entry['id'], entry['english'], entry['chinese']
        current = source_rows.get(key.lower())
        require(current is not None and current.findtext('id') == ident and
                current.findtext('body') == original, f'English string changed: {label}')
        require(not re.search(r'https?://|\[%web:|\[%shoplink', original, re.I),
                f'The launcher retains the original web destination: {label}')
        check_translation(original, translated, label)
        row = target_rows.get(key.lower())
        if row is None:
            require(ident not in target_ids, f'Chinese string ID already used: {label}')
            row = deepcopy(current)
            target.append(row)
            target_ids.add(ident)
        require(row.findtext('id') == ident and row.find('body') is not None,
                f'Chinese row identity changed: {label}')
        row.find('body').text = translated
    return bytes(codec.encode_binary_xml(target))


def decode_dialogue(raw, name):
    plain = crypt_html(raw[1:], name)
    require(raw.startswith(b'\x81') and plain.startswith(b'\x81\xff\xfe'),
            f'Unexpected native dialogue encoding: {name}')
    return plain[3:].decode('utf-16le')


def encode_dialogue(text, name):
    return b'\x81' + crypt_html(b'\x81\xff\xfe' + text.encode('utf-16le'), name)


def named_pages(text):
    pages = {}
    for page in PAGE.finditer(text):
        opener = page.group().split('>', 1)[0]
        key = PAGE_NAME.search(opener)
        if key:
            pages.setdefault(key[2], page)
    return pages


def checked_dialogue(english, entry, label):
    require(hashlib.sha256(english.encode('utf-8')).hexdigest() == entry['englishSha256'],
            f'English dialogue changed: {label}')
    translated = entry['chinese']
    check_translation(english, translated, label)
    return translated


def translated_dialogue(english, chinese, entry, name):
    if 'pages' not in entry:
        return checked_dialogue(english, entry, name)

    require(chinese is not None, f'Stock Chinese dialogue missing: {name}')
    source, target = named_pages(english), named_pages(chinese)
    edits = []
    for key, page in entry['pages'].items():
        label = f'{name}/{key}'
        require(key in source, f'English page missing: {label}')
        translated = checked_dialogue(source[key].group(), page, label)
        if key in target:
            edits.append((*target[key].span(), translated))
        else:
            closing = chinese.rfind('</HtmlPages>')
            require(closing != -1, f'Closing dialogue tag missing: {name}')
            edits.append((closing, closing, translated + '\n'))
    for start, stop, value in sorted(edits, reverse=True):
        chinese = chinese[:start] + value + chinese[stop:]
    return chinese


def prepare(client, source, output):
    require(not output.exists() and all(not output.is_relative_to(root) for root in (ROOT, client)),
            'Use a fresh external output directory')
    english_hash, chinese_hash = sha(client / ENGLISH), sha(source)
    strings = load(HERE / 'chs/strings.json')
    dialogues = load(HERE / 'chs/dialogues.json')
    replacements = {}
    additions = {}
    changed_strings = sum(len(entries) for entries in strings.values())
    changed_pages = 0
    with codec.read_pak(client / ENGLISH) as english, codec.read_pak(source) as chinese:
        existing = set(chinese.namelist())
        for name, entries in strings.items():
            replacements[name] = translated_strings(english.read(name), chinese.read(name), entries, name)
        for name, entry in dialogues.items():
            current = decode_dialogue(english.read(name), name)
            old = decode_dialogue(chinese.read(name), name) if name in existing else None
            translated = translated_dialogue(current, old, entry, name)
            (replacements if name in existing else additions)[name] = encode_dialogue(translated, name)
            changed_pages += len(entry['pages']) if 'pages' in entry else len(named_pages(translated))

    original = source.read_bytes()
    packed = NATIVE['extend_archive'](original, replacements, additions)
    old_members, old_index, _, old_version = NATIVE['parts'](original)
    members, index, _, version = NATIVE['parts'](packed)
    require(list(members) == list(old_members) + list(additions) and version == old_version,
            'Chinese PAK member order or encoding changed')
    preserved = old_members.keys() - replacements.keys()
    for name in preserved:
        require(old_members[name][0] == members[name][0] and
                old_index[name][:42] + old_index[name][46:] == index[name][:42] + index[name][46:],
                f'Unrelated compressed Chinese member changed: {name}')
    target = output / PACK
    target.parent.mkdir(parents=True)
    target.write_bytes(packed)
    with codec.read_pak(target) as archive:
        require(archive.testzip() is None, 'Generated Chinese PAK failed CRC validation')
        for name, data in (replacements | additions).items():
            require(archive.read(name) == data, f'Generated Chinese PAK member differs: {name}')
    require(sha(client / ENGLISH) == english_hash and sha(source) == chinese_hash,
            'A language input changed during preparation')
    report = dict(englishSha256=english_hash, baseChineseSha256=chinese_hash,
                  outputSha256=sha(target), outputBytes=target.stat().st_size,
                  strings=changed_strings, dialogueFiles=len(dialogues), dialoguePages=changed_pages,
                  preservedCompressedMembers=len(preserved))
    (output / 'coverage.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--source', type=Path, help='Existing managed Chinese data.pak (defaults to installed pack)')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client = args.client.resolve(strict=True)
    source = (args.source or client / PACK).resolve(strict=True)
    prepare(client, source, args.output.resolve())


if __name__ == '__main__':
    main()
