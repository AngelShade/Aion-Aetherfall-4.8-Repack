"""Complete native feature assets shared by original-to-repack sync and packaging.

An incremental InstalledMods entry can describe only the latest delivery. The
asset manifest retains the complete rendered texture cohort independently.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

FEATURES = {'PlayerBotPull': ('Mods/PlayerBotBar/assets.json', 'Textures/ui/AetherfallSummon')}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def texture_format(path):
    with path.open('rb') as stream:
        header = stream.read(128)
    if len(header) != 128 or header[:4] != b'DDS ' or struct.unpack_from('<I', header, 4)[0] != 124:
        raise ValueError('Invalid native DDS texture: ' + str(path))
    if header[84:88] not in (b'DXT1', b'DXT5'):
        raise ValueError('Unsupported recruitment DDS format: ' + str(path))
    return header[84:88].decode('ascii')


def build_manifest(client, feature):
    manifest, folder = FEATURES[feature]
    paths = sorted((Path(client) / folder).glob('*.dds'), key=lambda path: path.name.casefold())
    if not paths:
        raise ValueError('No rendered native assets: ' + folder)
    records = [dict(path=path.relative_to(client).as_posix(), bytes=path.stat().st_size,
                    sha256=sha(path), format=texture_format(path)) for path in paths]
    return dict(schema=1, feature=feature, files=records)


def asset_records(client):
    client = Path(client)
    inventory = read(client / 'Mods/InstalledMods.json')
    records = []
    for feature, (relative, folder) in FEATURES.items():
        manifest = client / relative
        if feature not in inventory.get('features', {}) and not manifest.is_file():
            continue
        if not manifest.is_file():
            raise ValueError('Complete native asset manifest is missing: ' + relative)
        data = read(manifest)
        if data.get('schema') != 1 or data.get('feature') != feature or not data.get('files'):
            raise ValueError('Invalid native feature asset manifest: ' + relative)
        seen = set()
        for record in data['files']:
            path = str(record['path']).replace('\\', '/')
            key = path.casefold()
            if (key in seen or '..' in Path(path).parts or Path(path).is_absolute() or
                    not key.startswith(folder.casefold() + '/') or not key.endswith('.dds')):
                raise ValueError('Invalid native asset path: ' + path)
            seen.add(key)
            records.append(dict(record, path=path))
        records.append(dict(path=relative, bytes=manifest.stat().st_size, sha256=sha(manifest)))
    return records


def validate_client_assets(client, entries=None):
    client = Path(client)
    indexed = None if entries is None else {
        entry['path'].replace('\\', '/').casefold().removeprefix('client/'): entry for entry in entries}
    records = asset_records(client)
    for record in records:
        relative = record['path']
        path = client / relative
        if not path.is_file() or path.stat().st_size != record['bytes'] or sha(path) != record['sha256']:
            raise ValueError('Native feature asset is missing or changed: ' + relative)
        if 'format' in record and texture_format(path) != record['format']:
            raise ValueError('Native asset format differs: ' + relative)
        if indexed is not None:
            entry = indexed.get(relative.casefold())
            if entry is None or any(entry[field] != record[field] for field in ('sha256', 'bytes')):
                raise ValueError('Native feature asset is absent from the cumulative inventory: ' + relative)
    return records


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--feature', choices=FEATURES, default='PlayerBotPull')
    parser.add_argument('--output', type=Path, help='Prepare the complete rendered-asset manifest externally')
    args = parser.parse_args()
    if args.output:
        value = build_manifest(args.client, args.feature)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
        print('Prepared', len(value['files']), 'native assets:', args.output)
    else:
        print('Verified', len(validate_client_assets(args.client)), 'native feature assets and manifests.')
