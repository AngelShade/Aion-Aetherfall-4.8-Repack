"""Build separate, verified text/voice downloads from normal original-client sources."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def archive_name(pack):
    name = pack['file']
    if not isinstance(name, str) or Path(name).name != name or '/' in name or chr(92) in name:
        raise ValueError('Unsafe previous language asset name')
    return name


def verified_archive(path, pack):
    if not path.is_file() or path.stat().st_size != pack['bytes'] or sha(path) != pack['sha256']:
        raise ValueError('Previous optional asset differs from its catalog: ' + str(path))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--text-stage', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--version', required=True, help='Version for changed pack content; unchanged packs retain their existing identity')
    parser.add_argument('--previous-assets', type=Path, help='Verified prior ZIP folder when preparing a fresh release output')
    parser.add_argument('--reuse-verified', action='store_true', help='Retained for compatibility; existing catalog assets are always verified before reuse')
    args = parser.parse_args()
    if not re.fullmatch(r'\d+\.\d+\.\d+', args.version):
        raise ValueError('Use a three-part language pack version')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    previous = json.loads(args.catalog.read_text(encoding='utf-8-sig')) if args.catalog.is_file() else dict(schema=1, packs=[])
    if previous.get('schema') != 1 or not isinstance(previous.get('packs'), list) or not isinstance(previous.get('previousVersions', []), list):
        raise ValueError('Unsupported previous language catalog')
    known = {}
    for pack in previous['packs'] + previous.get('previousVersions', []):
        archive_name(pack)
        key = (pack['id'], pack['version'])
        if key in known:
            raise ValueError('Duplicate previous language pack version: ' + '/'.join(key))
        known[key] = pack
    catalog = dict(schema=1, packs=[])
    parts = [(code, 'text', args.text_stage) for code in ['deu', 'fra', 'esn', 'rus', 'chs']]
    parts += [(code, 'voice', args.client) for code in ['kor', 'jpn']]
    for code, kind, root in parts:
        prefix = Path('Mods/Languages/Packs') / code
        sources = [root/prefix/'data/data.pak'] if kind == 'text' else sorted((root/prefix/'sounds').rglob('*.pak'))
        if not sources or any(not p.is_file() for p in sources):
            raise ValueError('Missing prepared source: ' + code + '-' + kind)
        records = [dict(path=p.relative_to(root).as_posix(), bytes=p.stat().st_size, sha256=sha(p)) for p in sources]
        identity = code+'-'+kind
        old = next((pack for pack in known.values() if pack['id'] == identity and pack['files'] == records), None)
        if old is not None:
            name = archive_name(old)
            path = output/name
            if not path.is_file():
                candidates = ([args.previous_assets/name] if args.previous_assets else []) + [args.catalog.parent/name]
                source = next((candidate for candidate in candidates if candidate.is_file()), None)
                if source is None:
                    raise FileNotFoundError('Keep the unchanged pack ZIP or provide --previous-assets: ' + name)
                verified_archive(source, old)
                shutil.copy2(source, path)
            verified_archive(path, old)
            header = dict(old)
        else:
            if (identity, args.version) in known:
                raise ValueError('Changed language content needs a new pack version: ' + identity)
            header = dict(id=identity, code=code, kind=kind, version=args.version, files=records)
            name = 'Aetherfall-Language-'+identity+'-'+args.version+'.zip'
            path = output/name
            if path.exists():
                raise FileExistsError('Do not replace an existing optional asset: ' + str(path))
            with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as archive:
                archive.writestr('language-pack.json', json.dumps(header, separators=(',', ':')))
                for record, source in zip(records, sources):
                    if sha(source) != record['sha256']:
                        raise ValueError('Source changed during packaging: ' + record['path'])
                    archive.write(source, 'payload/'+record['path'])
            if path.stat().st_size >= 2*1024**3:
                raise ValueError('Optional download exceeds the release asset limit')
            with zipfile.ZipFile(path) as archive:
                if archive.testzip() is not None:
                    raise ValueError('Optional ZIP failed CRC validation')
            header.update(file=name, url='https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v'+args.version+'/'+name,
                          bytes=path.stat().st_size, sha256=sha(path))
        catalog['packs'].append(header)
        print(json.dumps({key: header[key] for key in ['id', 'file', 'bytes', 'sha256']}), flush=True)
    active = {(pack['id'], pack['version']) for pack in catalog['packs']}
    history = [pack for key, pack in known.items() if key not in active]
    if history:
        catalog['previousVersions'] = history
    args.catalog.parent.mkdir(parents=True, exist_ok=True)
    args.catalog.write_text(json.dumps(catalog, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    main()
