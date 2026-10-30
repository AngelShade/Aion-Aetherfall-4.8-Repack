"""Bounded ZIP assets and a cumulative manifest; installed files supply unchanged chunks."""
import hashlib
import json
import zipfile
from pathlib import Path

CHUNK_BYTES = 32 * 1024 * 1024
SMALL_BYTES = 1024 * 1024
GITHUB_ASSET_BYTES = 2 * 1024 * 1024 * 1024


def sha256_file(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def build_chunked(package, manifest, output, url_base='', chunk_bytes=CHUNK_BYTES, source_overrides=None):
    if not 0 < chunk_bytes <= CHUNK_BYTES:
        raise ValueError('Update chunks must be at most 32 MiB')
    output.mkdir(parents=True, exist_ok=True)
    assets = {}
    buckets = [[] for _ in range(16)]
    bucket_sizes = [0] * 16

    def flush(records):
        if not records:
            return
        temporary = output / '.chunk-building.zip'
        unique = {part['sha256']: (part, data) for part, data in records}
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for digest, (_, data) in sorted(unique.items()):
                info = zipfile.ZipInfo(digest)
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, data)
        digest = sha256_file(temporary)
        name = 'Aetherfall-Chunk-' + digest + '.zip'
        destination = output / name
        temporary.replace(destination)
        assets[name] = dict(file=name, bytes=destination.stat().st_size, sha256=digest,
                            url=url_base.rstrip('/') + '/' + name if url_base else '')
        for part, _ in records:
            part['asset'] = name
        records.clear()

    files = []
    for original in manifest['files']:
        entry = dict(original, parts=[])
        files.append(entry)
        full_hash = hashlib.sha256()
        count = 0
        source = (source_overrides or {}).get(original['path'], package / original['path'])
        with Path(source).open('rb') as stream:
            while data := stream.read(chunk_bytes):
                full_hash.update(data)
                count += len(data)
                part = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
                entry['parts'].append(part)
                if original['bytes'] <= min(SMALL_BYTES, chunk_bytes):
                    bucket = int(hashlib.sha256(original['path'].casefold().encode()).hexdigest()[0], 16)
                    if bucket_sizes[bucket] + len(data) > chunk_bytes:
                        flush(buckets[bucket])
                        bucket_sizes[bucket] = 0
                    buckets[bucket].append((part, data))
                    bucket_sizes[bucket] += len(data)
                else:
                    flush([(part, data)])
        if count != original['bytes'] or full_hash.hexdigest() != original['sha256']:
            raise ValueError('File changed while preparing update: ' + original['path'])
    for bucket in buckets:
        flush(bucket)
    # Leave room for Setup, downloader and the manifest under GitHub's 1000-asset limit.
    if len(assets) > 990:
        raise ValueError('Update exceeds 990 chunk assets; increase the base release before publishing')
    result = dict(manifest, schema=2, format='chunked-v1', files=files, assets=list(assets.values()))
    name = 'Aetherfall-Patch-' + manifest['version'] + '.json'
    path = output / name
    path.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return path, result


def read_patch(path):
    if path.suffix.casefold() == '.json':
        return json.loads(path.read_text(encoding='utf-8-sig'))
    with zipfile.ZipFile(path) as archive:
        return json.loads(archive.read('patch-manifest.json'))


def verify_assets(output, manifest):
    records = list(manifest.get('assets', []))
    if len(records) > 990:
        raise ValueError('Too many update assets for GitHub')
    for asset in records:
        name = asset['file']
        if Path(name).name != name or '/' in name or '\\' in name:
            raise ValueError('Unsafe update asset name')
        path = output / name
        if (asset['bytes'] >= GITHUB_ASSET_BYTES or path.stat().st_size != asset['bytes']
                or sha256_file(path) != asset['sha256']):
            raise ValueError('Prepared update asset is invalid: ' + name)
    return records
