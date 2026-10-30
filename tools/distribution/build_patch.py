"""Build a cumulative chunk manifest and bounded assets against baseline FILES.json."""
import argparse
import hashlib
import json
from datetime import datetime
from pathlib import Path
from setup_version import setup_version
from repack_recovery import validate_recovery_payload
from optional_textures import validate_core, OBSOLETE_TOOLS
from optional_languages import validate_core as validate_languages, optional_path
from player_settings import preserve_rules, KEEP
from stock_defaults import recover as recover_stock_default
from chunked_patch import build_chunked


def sha256_file(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def build_patch(package: Path, baseline_index_path: Path, output_dir: Path, patch_url_base: str = '', setup_path=None):
    setup = setup_path or output_dir / 'Aetherfall-Downloader' / 'Aetherfall Setup.exe'
    setup_build = None
    if setup.is_file():
        hub = package / 'Aetherfall Hub.exe'
        if not hub.is_file() or sha256_file(hub) != sha256_file(setup):
            raise ValueError('Setup and the repack Hub differ; install the same build before preparing a patch')
        setup_build = setup_version(setup)
    release = json.loads((package / 'release.json').read_text(encoding='utf-8-sig'))
    version = release['Version']
    baseline = json.loads(baseline_index_path.read_text(encoding='utf-8-sig'))
    current = json.loads((package / 'FILES.json').read_text(encoding='utf-8-sig'))
    validate_core(package, current['files'])
    validate_languages(package, current['files'])

    base_map = {e['path'].casefold(): e for e in baseline['files']}
    curr_map = {e['path'].casefold(): e for e in current['files']}

    changed = []
    source_overrides = {}
    validate_recovery_payload(package, [entry for key, entry in curr_map.items()
                                     if key not in base_map or any(base_map[key][field] != entry[field]
                                                                 for field in ('sha256', 'bytes'))])
    for key, entry in sorted(curr_map.items()):
        rel = entry['path']
        old = base_map.get(key)
        if old is None or old['sha256'] != entry['sha256'] or old['bytes'] != entry['bytes']:
            full = package / rel
            if not full.is_file():
                raise FileNotFoundError(f'Missing repack file: {rel}')
            actual_sha = sha256_file(full)
            if actual_sha != entry['sha256']:
                if not entry.get('mutable') or rel.casefold() not in {path.casefold() for path in KEEP}:
                    raise ValueError(f'FILES.json hash mismatch for {rel}')
                full = recover_stock_default(entry, output_dir)
                source_overrides[rel] = full
                actual_sha = sha256_file(full)
                print('Using recorded stock default; owner settings retained:', rel, flush=True)
            changed.append({'path': rel, 'bytes': full.stat().st_size, 'sha256': actual_sha})

    for meta_rel in ['FILES.json', 'REPACK.json', 'release.json']:
        if not any(c['path'].casefold() == meta_rel.casefold() for c in changed):
            full = package / meta_rel
            if full.is_file():
                changed.append({'path': meta_rel, 'bytes': full.stat().st_size, 'sha256': sha256_file(full)})

    removed = []
    for key, old in sorted(base_map.items()):
        if key not in curr_map and not optional_path(old['path']):
            removed.append({'path': old['path'], 'sha256': old['sha256']})
    # Older cumulative patches may have added development backups after 0.86.
    # Retire those explicitly too, so updating any earlier release removes them.
    retired = json.loads((package / 'REPACK.json').read_text(encoding='utf-8-sig')).get('retiredFiles', [])
    removed_map = {entry['path'].casefold(): entry for entry in removed}
    for entry in retired:
        relative = entry['path'].replace('\\', '/')
        if ('..' in Path(relative).parts or
                (not relative.casefold().startswith('client/mods/backups/') and relative not in OBSOLETE_TOOLS)):
            raise ValueError('Invalid retired package file: ' + relative)
        if relative.casefold() in curr_map:
            raise ValueError('Retired backup is still packaged: ' + relative)
        removed_map[relative.casefold()] = {'path': relative, 'sha256': entry['sha256']}
    removed = [removed_map[key] for key in sorted(removed_map)]

    output_dir.mkdir(parents=True, exist_ok=True)

    patch_manifest = {
        'schema': 1,
        'product': 'Aion Aetherfall Repack',
        'version': version,
        'baseVersion': '0.86',
        'createdAt': datetime.now().astimezone().isoformat(),
        'files': changed,
        'removed': removed,
        # Player-changed settings are kept by Update-Repack.ps1 (see player_settings.py).
        'preserve': preserve_rules(current['files']),
    }

    patch_path, chunk_manifest = build_chunked(package, patch_manifest, output_dir, patch_url_base, source_overrides=source_overrides)
    patch_name = patch_path.name

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
        'patchFormat': 'chunked-v1',
        'downloadBytes': sum(asset['bytes'] for asset in chunk_manifest['assets']),
        'assetCount': len(chunk_manifest['assets']),
        'filesChanged': len(changed),
        'filesRemoved': len(removed),
    }
    if setup.is_file():
        latest.update(setupExeUrl=(patch_url_base.rstrip('/')+'/Aetherfall-Setup.exe') if patch_url_base else '',
                      setupExeBytes=setup.stat().st_size, setupExeSha256=sha256_file(setup),
                      setupVersion=setup_build)
    (output_dir / 'latest-update.json').write_text(json.dumps(latest, indent=2), encoding='utf-8')
    print(f'Built chunked update {patch_path} ({len(changed)} files, {latest["assetCount"]} assets, {latest["downloadBytes"]} total bytes, sha256={patch_sha})')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True, help='Path to updated portable repack')
    parser.add_argument('--baseline', type=Path, required=True, help='Path to baseline 0.86 FILES.json')
    parser.add_argument('--output', type=Path, required=True, help='Output directory for the patch manifest, chunk ZIPs and latest-update.json')
    parser.add_argument('--url-base', default='', help='Optional public HTTPS base URL where patch ZIP will be hosted')
    args = parser.parse_args()
    build_patch(args.package.resolve(strict=True), args.baseline.resolve(strict=True), args.output.resolve(), args.url_base)
