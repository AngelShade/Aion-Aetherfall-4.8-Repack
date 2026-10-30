"""Adapt verified donor text to current native keys, IDs, dialogue paths and fonts.

Only a managed source pack is produced; the launcher composes it around the current
English archives at Play. No donor layouts, country configuration or overrides ship.
"""
import argparse
from collections import Counter
import copy
from functools import lru_cache
import hashlib
import html
import io
import json
from pathlib import Path
import re
import sys
import zipfile
from xml.etree import ElementTree as ET

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
import codec


def open_pak(path):
    return zipfile.ZipFile(path) if zipfile.is_zipfile(path) else codec.read_pak(path)


def decode_text(raw):
    return raw.decode('utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')


def xml_text(value):
    # Some exported donor XML leaves raw ampersands in URLs. Decode only XML
    # entities; retain literal ampersands instead of a lossy XML recovery parser.
    if value.startswith('<![CDATA[') and value.endswith(']]>'):
        return value[9:-3]
    return re.sub(r'&(?:lt|gt|amp|quot|apos|#[0-9]+|#x[0-9a-f]+);',
                  lambda m: html.unescape(m[0]), value, flags=re.I)


def decode_xml(raw):
    if raw[0] == 128:
        return codec.binary_xml(raw)
    text = decode_text(raw)
    try:
        return ET.fromstring(text)
    except ET.ParseError:
        if not re.search(r'<strings\b', text):
            raise
        tree = ET.Element('strings')
        blocks = re.findall(r'<string\b[^>]*>(.*?)</string>', text, re.S | re.I)
        if len(blocks) != len(re.findall(r'<string\b', text, re.I)):
            raise ValueError('Incomplete donor string rows')
        for block in blocks:
            row = ET.SubElement(tree, 'string')
            for name in ('id', 'name', 'body'):
                match = re.search('<' + name + r'\b[^>]*>(.*?)</' + name + '>', block, re.S | re.I)
                if match is None:
                    if name == 'body':
                        if re.search(r'<body\b[^>]*/>', block, re.I):
                            ET.SubElement(row, 'body').text = ''
                        continue  # Native unused rows may omit body; keep English.
                    raise ValueError('Donor string is missing ' + name)
                value = xml_text(match[1])
                if '\x00' in value:
                    raise ValueError('NUL in donor string')
                ET.SubElement(row, name).text = value
        return tree


@lru_cache(maxsize=2048)
def html_mask(a, b):
    start = a, b
    mask = bytearray()
    while True:
        a = ((a + 0x1d) ^ b) & 255
        b = (b + 3) & 255
        mask.append(a)
        if (a, b) == start:
            return bytes(mask)
        if len(mask) > 65536:
            raise ValueError('Native HTML key cycle did not close')


def crypt_html(raw, name):
    stem = Path(name).stem
    a = b = 0
    for i, char in enumerate(stem):
        value = (ord(char) & 15) + i
        a = (a + value) & 0xffffffff
        b ^= value
    mask = html_mask(a & 255, b & 255)
    key = (mask * ((len(raw) + len(mask) - 1) // len(mask)))[:len(raw)]
    return (int.from_bytes(raw, 'little') ^ int.from_bytes(key, 'little')).to_bytes(len(raw), 'little')


def native_html(raw, source_name, target_name):
    if raw[0] == 129:
        plain = crypt_html(raw[1:], source_name)
        if not plain.startswith(b'\x81\xff\xfe'):
            raise ValueError('Unsupported donor dialogue encoding: ' + source_name)
        text = plain[3:].decode('utf-16le')
    else:
        text = decode_text(raw)
    if not re.search(r'<HtmlPage\b', text, re.I):
        return None
    text = re.sub('encoding="UTF-8"', 'encoding="UTF-16"', text, flags=re.I)
    return b'\x81' + crypt_html(b'\x81\xff\xfe' + text.encode('utf-16le'), target_name)


def tokens(value):
    return Counter(re.findall(r'%(?:[A-Za-z_]*\d+|[A-Za-z_]+)|\[%(?:[^:\]\s]+):[^\]]+\]', value or ''))


def foreign_branding(value):
    return re.search(r'gamez\s*aion|gamezaion|gamez\s*network|gameznetwork', value, re.I) is not None


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--source', type=Path, required=True, help='Verified donor data.pak')
    parser.add_argument('--code', choices=['deu', 'fra', 'esn', 'rus', 'chs'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, source, output = args.client.resolve(strict=True), args.source.resolve(strict=True), args.output.resolve()
    if output.is_relative_to(client) or output.is_relative_to(Path(__file__).resolve().parents[2]):
        raise ValueError('Use external build output')
    target = output / 'Mods/Languages/Packs' / args.code
    if target.exists():
        raise FileExistsError('Use a fresh pack output')
    report = dict(code=args.code, sourceSha256=sha(source), originalSha256=sha(client/'L10N/enu/data/data.pak'),
                  strings=0, fallback=0, unsafeFormatting=0, webLinks=0, foreignBranding=0, dialogues=0, fonts=0,
                  skippedDialogues=[], originalModified=False)
    payload = {}
    with codec.read_pak(client/'L10N/enu/data/data.pak') as english, open_pak(source) as donor:
        names = {}
        for name in donor.namelist():
            key = name.replace('\\', '/').lower()
            if key in names:
                raise ValueError('Case-colliding donor entries: ' + key)
            names[key] = name
        for name in english.namelist():
            other = names.get(name.lower())
            if name.startswith('strings/') and name.endswith('.xml'):
                current = codec.binary_xml(english.read(name))
                if other is None:
                    report['fallback'] += len(list(current))
                    continue
                translated = {}
                for row in decode_xml(donor.read(other)):
                    key = row.findtext('name')
                    if key is not None:
                        translated.setdefault(key.lower(), row)
                result = ET.Element(current.tag, current.attrib)
                for row in current:
                    native = translated.get((row.findtext('name') or '').lower())
                    body = row.findtext('body')
                    if native is None or native.findtext('id') != row.findtext('id') or body is None or native.find('body') is None:
                        report['fallback'] += 1
                        continue
                    other_body = ''.join(native.find('body').itertext())
                    if foreign_branding(other_body):
                        report['foreignBranding'] += 1
                        continue
                    if re.search(r'https?://|\[%web:|\[%shoplink', body + other_body, re.I):
                        report['webLinks'] += 1
                        continue
                    if tokens(body) != tokens(other_body):
                        report['unsafeFormatting'] += 1
                        continue
                    new = copy.deepcopy(row)
                    new.find('body').clear()
                    new.find('body').text = other_body
                    result.append(new)
                    report['strings'] += 1
                if len(result):
                    payload[name] = bytes(codec.encode_binary_xml(result))
            elif other and name.startswith('dialogs/') and name.endswith('.html'):
                try:
                    result = native_html(donor.read(other), other, name)
                    if result is not None and foreign_branding(crypt_html(result[1:], name)[3:].decode('utf-16le')):
                        report['foreignBranding'] += 1
                        report['skippedDialogues'].append(dict(file=name, reason='Foreign server branding'))
                        continue
                    if result is not None:
                        payload[name] = result
                        report['dialogues'] += 1
                except (UnicodeError, ValueError) as error:
                    report['skippedDialogues'].append(dict(file=name, reason=str(error)))
        # Font faces are source assets. The runtime merges only face names into
        # CURRENT native styles, retaining sizes, colours, custom styles and UI.
        source_fonts = decode_xml(donor.read(names['fonts/fontshader.xml']))
        source_styles = {node.get('name'): node for node in source_fonts.findall('fontshader')}
        current_fonts = codec.binary_xml(english.read('fonts/fontshader.xml'))
        font_map = ET.Element('fontshaders')
        required = set()
        for style in current_fonts.findall('fontshader'):
            wanted = source_styles.get(style.get('name'))
            if wanted is None:
                wanted = source_styles.get('default')
            font = wanted.find('font') if wanted is not None else None
            if font is None or not font.get('name'):
                continue
            entry = ET.SubElement(font_map, 'fontshader', {'name': style.get('name')})
            ET.SubElement(entry, 'font', {key: font.get(key) for key in ['name', 'editor_name'] if font.get(key) is not None})
            required.add(font.get('name').lower())
        source_ttf = {Path(key).stem.lower(): key for key in names if key.startswith('fonts/') and key.endswith('.ttf')}
        current_ttf = {Path(name).stem.lower(): name for name in english.namelist() if name.startswith('fonts/') and name.endswith('.ttf')}
        reused = []
        for face in sorted(required):
            key = source_ttf.get(face)
            if face in current_ttf and (key is None or donor.read(names[key]) == english.read(current_ttf[face])):
                reused.append(face)
                continue
            if key is None:
                raise ValueError('Unresolved native font dependency: ' + face)
            payload[key] = donor.read(names[key])
            report['fonts'] += 1
        if not required or 'default' not in {node.get('name') for node in font_map}:
            raise ValueError('Native default font mapping is incomplete')
        report.update(requiredFontFaces=sorted(required), reusedEnglishFontFaces=reused)
        payload['fonts/fontshader.xml'] = bytes(codec.encode_binary_xml(font_map))
    if report['strings'] < 100000 or report['dialogues'] < 10000:
        raise ValueError('Donor coverage is insufficient: ' + json.dumps(report))
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as pak:
        for name, data in payload.items():
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            pak.writestr(info, data)
    pak_path = target/'data/data.pak'
    pak_path.parent.mkdir(parents=True)
    pak_path.write_bytes(codec.encode_pak(buffer.getvalue()))
    with codec.read_pak(pak_path) as verified:
        if verified.testzip() is not None or set(verified.namelist()) != set(payload):
            raise ValueError('Generated native archive failed validation')
    report.update(outputSha256=sha(pak_path), outputBytes=pak_path.stat().st_size, members=len(payload))
    (target/'coverage.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'skippedDialogues'}), flush=True)


if __name__ == '__main__':
    main()
