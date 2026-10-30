"""Adapt a downloaded voice pack to the installed 4.8 voice paths. No runtime writes.

Only OGG/WAV members present in the current English 4.8 voice archives are mapped;
unmatched voices retain their existing English bytes. Newer game data is never used.
"""
import argparse
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
import codec


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_source(path):
    # The publisher's Japanese archive includes ordinary ZIPs named .pak.
    return zipfile.ZipFile(path) if zipfile.is_zipfile(path) else codec.read_pak(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--source', required=True, type=Path, help='Extracted sounds folder')
    parser.add_argument('--code', required=True, choices=['kor', 'jpn', 'deu', 'fra', 'rus', 'esn', 'ita', 'plk', 'trk', 'chs', 'cht', 'ptb'])
    parser.add_argument('--output', required=True, type=Path, help='External launcher build stage')
    parser.add_argument('--source-url', required=True)
    args = parser.parse_args()
    client, source, output = args.client.resolve(strict=True), args.source.resolve(strict=True), args.output.resolve()
    if output.is_relative_to(client) or output.is_relative_to(Path(__file__).resolve().parents[2]):
        raise ValueError('Use an external output folder')
    destination = output / 'Mods/Languages/Packs' / args.code
    if destination.exists():
        raise FileExistsError('Use a fresh language package output')
    baseline = client / 'L10N/enu/sounds'
    native = {}
    archives = {}
    for path in sorted(source.rglob('*.pak')):
        if path.is_symlink() or path.is_junction():
            raise ValueError('Linked source archives are unsupported')
        archive = read_source(path)
        folder = path.parent.relative_to(source).as_posix()
        for name in archive.namelist():
            if Path(name).suffix.lower() not in {'.ogg', '.wav'}:
                continue
            key = (folder + '/' + name).lstrip('./').casefold()
            if key in native:
                raise ValueError('Ambiguous voice member: ' + key)
            native[key] = path, name
        archive.close()
    report = dict(language=args.code, sourceUrl=args.source_url, mapped=0, fallback=0, archives=[], sourceArchives={})
    for path in sorted(baseline.rglob('*.pak')):
        english = codec.read_pak(path)
        folder = path.parent.relative_to(baseline).as_posix()
        buffer = io.BytesIO()
        mapped = fallback = 0
        with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as out:
            for name in english.namelist():
                data = english.read(name)
                match = native.get((folder + '/' + name).lstrip('./').casefold())
                if match:
                    source_path, source_name = match
                    if source_path not in archives:
                        archives[source_path] = read_source(source_path)
                    translated = archives[source_path].read(source_name)
                    if not (translated.startswith(b'OggS') or translated.startswith(b'RIFF')):
                        raise ValueError('Unsupported voice audio: ' + source_name)
                    data = translated
                    mapped += 1
                elif Path(name).suffix.lower() in {'.ogg', '.wav'}:
                    fallback += 1
                info = zipfile.ZipInfo(name, (2026, 10, 29, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                out.writestr(info, data)
        relative = path.relative_to(baseline)
        target = destination / 'sounds' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(codec.encode_pak(buffer.getvalue()))
        verified = codec.read_pak(target)
        if verified.namelist() != english.namelist():
            raise ValueError('Generated voice paths differ from the 4.8 client')
        # Read every output audio member through the native decoder/CRC checks.
        for name in verified.namelist():
            verified.read(name)
        verified.close()
        english.close()
        report['mapped'] += mapped
        report['fallback'] += fallback
        report['archives'].append(dict(path=target.relative_to(output).as_posix(), sha256=sha(target), mapped=mapped, fallback=fallback))
    for path, archive in archives.items():
        archive.close()
        report['sourceArchives'][path.relative_to(source).as_posix()] = sha(path)
    if report['mapped'] < 1000:
        raise ValueError('The voice pack does not match enough installed 4.8 voice paths')
    (destination / 'coverage.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: report[k] for k in ['language', 'mapped', 'fallback', 'sourceUrl']}))


if __name__ == '__main__':
    main()
