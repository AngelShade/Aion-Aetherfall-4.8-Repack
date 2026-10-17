"""Build a small delta update ZIP (Aetherfall-Patch-<version>.zip) and latest-update.json by comparing a repack against a baseline FILES.json."""
import argparse
import hashlib
import json
import zipfile
from datetime import datetime
from pathlib import Path


def sha256_file(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def build_patch(package: Path, baseline_index_path: Path, output_dir: Path, patch_url_base: str = '', setup_path=None):
    release = json.loads((package / 'release.json').read_text(encoding='utf-8-sig'))
    version = release['Version']
    baseline = json.loads(baseline_index_path.read_text(encoding='utf-8-sig'))
    current = json.loads((package / 'FILES.json').read_text(encoding='utf-8-sig'))

    base_map = {e['path'].casefold(): e for e in baseline['files']}
    curr_map = {e['path'].casefold(): e for e in current['files']}

    changed = []
    for key, entry in sorted(curr_map.items()):
        rel = entry['path']
        old = base_map.get(key)
        if old is None or old['sha256'] != entry['sha256'] or old['bytes'] != entry['bytes']:
            full = package / rel
            if not full.is_file():
                raise FileNotFoundError(f'Missing repack file: {rel}')
            actual_sha = sha256_file(full)
            if actual_sha != entry['sha256']:
                raise ValueError(f'FILES.json hash mismatch for {rel}')
            changed.append({'path': rel, 'bytes': full.stat().st_size, 'sha256': actual_sha})

    for meta_rel in ['FILES.json', 'REPACK.json', 'release.json']:
        if not any(c['path'].casefold() == meta_rel.casefold() for c in changed):
            full = package / meta_rel
            if full.is_file():
                changed.append({'path': meta_rel, 'bytes': full.stat().st_size, 'sha256': sha256_file(full)})

    removed = []
    for key, old in sorted(base_map.items()):
        if key not in curr_map:
            removed.append({'path': old['path'], 'sha256': old['sha256']})

    output_dir.mkdir(parents=True, exist_ok=True)
    patch_name = f'Aetherfall-Patch-{version}.zip'
    patch_path = output_dir / patch_name

    patch_manifest = {
        'schema': 1,
        'product': 'Aion Aetherfall Repack',
        'version': version,
        'baseVersion': '0.86',
        'createdAt': datetime.now().astimezone().isoformat(),
        'files': changed,
        'removed': removed,
    }

    with zipfile.ZipFile(patch_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.writestr('patch-manifest.json', json.dumps(patch_manifest, indent=2))
        for item in changed:
            zf.write(package / item['path'], 'payload/' + item['path'])

    patch_sha = sha256_file(patch_path)
    patch_url = (patch_url_base.rstrip('/') + '/' + patch_name) if patch_url_base else ''
    latest = {
        'schema': 1,
        'product': 'Aion Aetherfall Repack',
        'version': version,
        'patchFile': patch_name,
        'patchBytes': patch_path.stat().st_size,
        'patchSha256': patch_sha,
        'patchUrl': patch_url,
        'filesChanged': len(changed),
        'filesRemoved': len(removed),
    }
    setup = setup_path or output_dir / 'Aetherfall-Downloader' / 'Aetherfall Setup.exe'
    if setup.is_file():
        latest.update(setupExeUrl=(patch_url_base.rstrip('/')+'/Aetherfall-Setup.exe') if patch_url_base else '',
                      setupExeBytes=setup.stat().st_size, setupExeSha256=sha256_file(setup))
    (output_dir / 'latest-update.json').write_text(json.dumps(latest, indent=2), encoding='utf-8')
    print(f'Built delta patch {patch_path} ({len(changed)} changed files, {patch_path.stat().st_size} bytes, sha256={patch_sha})')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True, help='Path to updated portable repack')
    parser.add_argument('--baseline', type=Path, required=True, help='Path to baseline 0.86 FILES.json')
    parser.add_argument('--output', type=Path, required=True, help='Output directory for patch ZIP and latest-update.json')
    parser.add_argument('--url-base', default='', help='Optional public HTTPS base URL where patch ZIP will be hosted')
    args = parser.parse_args()
    build_patch(args.package.resolve(strict=True), args.baseline.resolve(strict=True), args.output.resolve(), args.url_base)
