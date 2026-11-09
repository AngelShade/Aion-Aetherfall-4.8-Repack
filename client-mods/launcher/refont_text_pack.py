"""Apply prepare_text_pack's native face overrides to an already prepared text pack.

Strings and dialogues stay byte-identical; only the font map and the font files the
map needs change. Writes a fresh pack under external build output.
"""
import argparse
import io
import json
from pathlib import Path
import sys
import zipfile
from xml.etree import ElementTree as ET

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_text_pack import NATIVE_FACE_OVERRIDES, apply_face_overrides, bundled_face, codec, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--pack', type=Path, required=True, help='Prepared Mods/Languages/Packs/<code>/data/data.pak')
    parser.add_argument('--code', choices=sorted(NATIVE_FACE_OVERRIDES), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, pack, output = args.client.resolve(strict=True), args.pack.resolve(strict=True), args.output.resolve()
    if output.is_relative_to(client) or output.is_relative_to(Path(__file__).resolve().parents[2]):
        raise ValueError('Use external build output')
    target = output / 'Mods/Languages/Packs' / args.code / 'data/data.pak'
    if target.exists():
        raise FileExistsError('Use a fresh pack output')
    with codec.read_pak(client / 'L10N/enu/data/data.pak') as english:
        current_fonts = codec.binary_xml(english.read('fonts/fontshader.xml'))
    with codec.read_pak(pack) as source:
        members = [(info, source.read(info.filename)) for info in source.infolist()]
    data = {info.filename: raw for info, raw in members}
    font_map = codec.binary_xml(data['fonts/fontshader.xml'])
    before = sorted({e.find('font').get('name') for e in font_map.findall('fontshader')})
    apply_face_overrides(args.code, current_fonts, font_map)
    required = {e.find('font').get('name').lower() for e in font_map.findall('fontshader')}
    fonts = {}
    for face in sorted(required):
        raw = bundled_face(face) or data.get('fonts/' + face + '.ttf')
        if raw is None:
            raise ValueError('Unresolved font face: ' + face)
        fonts['fonts/' + face + '.ttf'] = raw
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as out:
        for info, raw in members:
            name = info.filename
            if name.startswith('fonts/') and name.endswith('.ttf'):
                continue
            if name == 'fonts/fontshader.xml':
                raw = bytes(codec.encode_binary_xml(font_map))
            out.writestr(info, raw)
        for name, raw in fonts.items():
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, raw)
    target.parent.mkdir(parents=True)
    target.write_bytes(codec.encode_pak(buffer.getvalue()))
    with codec.read_pak(target) as verified:
        if verified.testzip() is not None:
            raise ValueError('Generated native archive failed validation')
        check = codec.binary_xml(verified.read('fonts/fontshader.xml'))
        after = sorted({e.find('font').get('name') for e in check.findall('fontshader')})
        changed = [n for n in verified.namelist() if n.startswith(('strings/', 'dialogs/')) and verified.read(n) != data[n]]
        if changed or len([n for n in verified.namelist() if n.startswith(('strings/', 'dialogs/'))]) != len([n for n in data if n.startswith(('strings/', 'dialogs/'))]):
            raise ValueError('Text members changed during refont')
    print(json.dumps(dict(code=args.code, facesBefore=before, facesAfter=after, fontFiles=sorted(fonts),
                          sourceSha256=sha(pack), outputSha256=sha(target), outputBytes=target.stat().st_size)))


if __name__ == '__main__':
    main()
