"""Prepare cumulative chunked updates against baseline 0.86 and publish only when requested.

The prepared flow retains the installed version/index. The legacy scan flow also
refreshes release metadata before entering the same chunk builder and publisher.
"""
import argparse
import hashlib
import json
import os
import subprocess
import urllib.error
import urllib.request
import urllib.parse
import zipfile
from datetime import datetime
from pathlib import Path
from setup_version import setup_version
from build_downloader import build_downloader
from release_workspace import release_path
from chunked_patch import read_patch, verify_assets, GITHUB_ASSET_BYTES
from optional_languages import assets as language_assets, optional_path

try:
    import certifi  # Windows' store can lack the root of GitHub's current chain (USERTrust ECC).
    os.environ.setdefault('SSL_CERT_FILE', certifi.where())
except ImportError:
    pass

DOWNLOAD_URL = 'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/latest/download/Aetherfall-Downloader.zip'
LEGACY_DOWNLOAD_URL = 'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.86/Aetherfall-Downloader.zip'


def prepare_downloader(dist_dir, output_dir, setup, latest, changelog):
    layout_path = dist_dir / 'Maintenance/release-layout.json'
    layout = json.loads(layout_path.read_text(encoding='utf-8-sig')) if layout_path.is_file() else {}
    support = dist_dir / layout.get('setupSupport', 'Aetherfall-Downloader')
    archive = build_downloader(setup, support,
        output_dir / 'Aetherfall-Downloader.zip', changelog)
    base = 'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v' + latest['version']
    latest.update(downloaderUrl=base+'/Aetherfall-Downloader.zip',
                  downloaderBytes=archive.stat().st_size, downloaderSha256=sha256_file(archive))
    return archive


def refresh_public_download_links(contents_api, headers):
    import base64
    request = urllib.request.Request(contents_api + '/README.md?ref=main', headers=headers)
    with urllib.request.urlopen(request) as response:
        record = json.loads(response.read().decode())
    before = base64.b64decode(record['content']).decode('utf-8')
    after = before.replace(LEGACY_DOWNLOAD_URL, DOWNLOAD_URL)
    if after == before:
        return
    body = dict(message='Point new installations to the current downloader', branch='main',
                sha=record['sha'], content=base64.b64encode(after.encode('utf-8')).decode('ascii'))
    request = urllib.request.Request(contents_api + '/README.md',
        data=json.dumps(body).encode('utf-8'), headers=headers, method='PUT')
    with urllib.request.urlopen(request):
        print('      Public download links now follow the latest downloader ZIP.')

EXCLUDED_PREFIXES = (
    'server/database/data/',
    'server/gameserver/log/',
    'server/loginserver/log/',
    'server/chatserver/log/',
    'server/gameserver/cache/',
    'control/logs/',
    'control/pids/',
    'control/updatebackups/',
    'updates/',
)


def sha256_file(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def next_version(current_ver: str) -> str:
    parts = current_ver.strip().split('.')
    if len(parts) == 2 and parts[1].isdigit():
        width = len(parts[1])
        return f"{parts[0]}.{int(parts[1]) + 1:0{width}d}"
    return current_ver + '.1'


def get_github_token() -> str:
    proc = subprocess.run(
        ['git', 'credential', 'fill'],
        input='protocol=https\nhost=github.com\n\n',
        text=True,
        capture_output=True,
        check=True,
    )
    creds = dict(line.split('=', 1) for line in proc.stdout.splitlines() if '=' in line)
    return creds['password']


def scan_and_build_patch(package: Path, baseline_path: Path, dist_dir: Path, target_version: str = '', publish_github: bool = True, prepared: bool = False):
    if prepared:
        publish_prepared(package, baseline_path, dist_dir, target_version, publish_github)
        return
    release_file = package / 'release.json'
    repack_file = package / 'REPACK.json'
    files_file = package / 'FILES.json'
    client_release_file = package / 'Client' / 'Mods' / 'release.json'

    release_data = json.loads(release_file.read_text(encoding='utf-8-sig'))
    current_ver = str(release_data.get('Version', '0.86'))
    if not target_version:
        target_version = next_version(current_ver)

    print(f'[1/4] Scanning repack folder against baseline 0.86 (target version: {target_version})...')
    baseline = json.loads(baseline_path.read_text(encoding='utf-8-sig'))
    current_index = json.loads(files_file.read_text(encoding='utf-8-sig'))

    base_map = {e['path'].replace('\\', '/').casefold(): e for e in baseline['files']}
    curr_map = {e['path'].replace('\\', '/').casefold(): e for e in current_index['files']}

    # Reference timestamp of FILES.json from last build so unchanged 36 GB .pak files aren't re-hashed
    index_mtime = files_file.stat().st_mtime - 2.0

    new_files_list = []
    changed_entries = []

    for root, dirs, files in os.walk(package):
        rel_dir = Path(root).relative_to(package).as_posix()
        if rel_dir == '.':
            rel_dir = ''
        # Prune excluded runtime/data folders
        dirs[:] = [
            d for d in dirs
            if not ((f'{rel_dir}/{d}/' if rel_dir else f'{d}/').casefold().startswith(EXCLUDED_PREFIXES))
        ]
        for fname in files:
            rel_path = f'{rel_dir}/{fname}' if rel_dir else fname
            key = rel_path.casefold()
            if optional_path(rel_path):
                continue
            if key.startswith(EXCLUDED_PREFIXES) or key.endswith(('.lock', '.err', '.pid')):
                continue
            if key in ('files.json', 'repack.json', 'release.json', 'client/mods/release.json'):
                continue

            full = Path(root) / fname
            st = full.stat()
            size = st.st_size
            prev = curr_map.get(key)

            if prev is not None and prev['bytes'] == size and st.st_mtime <= index_mtime:
                file_sha = prev['sha256']
            else:
                file_sha = sha256_file(full)

            entry = dict(prev or {}, path=rel_path, bytes=size, sha256=file_sha)
            new_files_list.append(entry)

            base_old = base_map.get(key)
            if base_old is None or base_old['sha256'] != file_sha or base_old['bytes'] != size:
                changed_entries.append(entry)

    # Update release metadata files
    now_iso = datetime.now().astimezone().isoformat()
    release_data['Version'] = target_version
    release_data['Release'] = f"release{target_version.replace('.', '').zfill(3)}"
    release_data['UpdatedAt'] = now_iso
    release_bytes = (json.dumps(release_data, indent=2) + '\n').encode('utf-8')
    release_file.write_bytes(release_bytes)
    if client_release_file.parent.is_dir():
        client_release_file.write_bytes(release_bytes)

    if repack_file.is_file():
        repack_data = json.loads(repack_file.read_text(encoding='utf-8-sig'))
        repack_data['version'] = target_version
        repack_data['release'] = release_data['Release']
        repack_data['updatedAt'] = now_iso
        repack_file.write_text(json.dumps(repack_data, indent=2) + '\n', encoding='utf-8')

    for meta_rel in ('release.json', 'Client/Mods/release.json', 'REPACK.json'):
        mpath = package / meta_rel
        if mpath.is_file():
            mentry = {'path': meta_rel, 'bytes': mpath.stat().st_size, 'sha256': sha256_file(mpath)}
            new_files_list.append(mentry)
            changed_entries.append(mentry)

    new_files_list.sort(key=lambda x: x['path'].casefold())
    current_index['version'] = target_version
    current_index['release'] = release_data['Release']
    current_index['generatedAt'] = now_iso
    current_index['fileCount'] = len(new_files_list)
    current_index['files'] = new_files_list
    files_file.write_text(json.dumps(current_index, indent=2) + '\n', encoding='utf-8')

    files_entry = {'path': 'FILES.json', 'bytes': files_file.stat().st_size, 'sha256': sha256_file(files_file)}
    changed_entries.append(files_entry)
    changed_entries.sort(key=lambda x: x['path'].casefold())

    new_keys = {e['path'].casefold() for e in new_files_list} | {'files.json'}
    removed_entries = [
        {'path': old['path'], 'sha256': old['sha256']}
        for key, old in sorted(base_map.items())
        if key not in new_keys
    ]

    # Use the same cumulative coverage and settings rules for both owner entry points.
    publish_prepared(package, baseline_path, dist_dir, target_version, publish_github)


def publish_prepared(package, baseline_path, dist_dir, target_version, upload):
    from build_patch import build_patch
    current = json.loads((package / 'release.json').read_text(encoding='utf-8-sig'))['Version']
    if target_version and target_version != current:
        raise ValueError('Prepared publication must retain the already installed repack version')
    tag = 'v' + current
    base = 'https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/' + tag
    organized = (dist_dir / 'Maintenance/release-layout.json').is_file()
    patch_dir = dist_dir / 'Patches' / current if organized else dist_dir
    layout = json.loads((dist_dir / 'Maintenance/release-layout.json').read_text(encoding='utf-8-sig')) if organized else {}
    setup = dist_dir / layout.get('setupExecutable', 'Aetherfall Setup.exe') if organized else dist_dir / 'Aetherfall-Downloader' / 'Aetherfall Setup.exe'
    latest_path = patch_dir / 'latest-update.json'
    latest = json.loads(latest_path.read_text()) if latest_path.is_file() else {}
    patch_path = patch_dir / ('Aetherfall-Patch-' + current + '.json')
    rebuild = latest.get('version') != current or latest.get('patchFormat') != 'chunked-v1' or not patch_path.is_file() or not latest.get('setupExeSha256') or not latest.get('patchUrl')
    if not rebuild:
        patch = read_patch(patch_path)
        entry = next((record for record in patch['files'] if record['path'].casefold() == 'files.json'), None)
        rebuild = not entry or entry['sha256'] != sha256_file(package / 'FILES.json')
    if rebuild:
        build_patch(package, baseline_path, patch_dir, base, setup)
        latest = json.loads(latest_path.read_text())
    patch = read_patch(patch_path)
    entry = next((record for record in patch['files'] if record['path'].casefold() == 'files.json'), None)
    if not entry or entry['sha256'] != sha256_file(package / 'FILES.json'):
        raise ValueError('The repack changed after its patch was prepared; rebuild the local patch first')
    verify_assets(patch_dir, patch)
    optional_assets = language_assets(package, patch_dir)
    latest['languageAssets'] = [dict(file=path.name, bytes=path.stat().st_size,
                                    sha256=sha256_file(path)) for path in optional_assets]
    if len(patch['assets']) + len(optional_assets) > 990:
        raise ValueError('Core and optional assets exceed the release asset limit')
    if latest.get('patchSha256') != sha256_file(patch_path):
        raise ValueError('The prepared patch checksum changed')
    if setup.is_file():
        hub = next((record for record in patch['files'] if record['path'].casefold() == 'aetherfall hub.exe'), None)
        if not hub or hub['sha256'] != sha256_file(setup) or sha256_file(package / 'Aetherfall Hub.exe') != hub['sha256']:
            raise ValueError('Setup and the prepared patch Hub differ; install the same build before publishing')
        latest.update(setupExeUrl=base+'/Aetherfall-Setup.exe', setupExeBytes=setup.stat().st_size,
                      setupExeSha256=sha256_file(setup), setupVersion=setup_version(setup))
        validate_launcher_bridge(latest, setup)
        prepare_downloader(dist_dir, patch_dir, setup, latest, package / 'CHANGELOG.md')
        latest_path.write_text(json.dumps(latest, indent=2)+'\n', encoding='utf-8')
    else:
        raise ValueError('A normal Setup/Hub build is required to bridge older launchers')
    if upload:
        publish_assets(patch_dir, latest, setup)
    else:
        print('Prepared update metadata for ' + tag + '; no upload or channel change performed.')



def validate_launcher_bridge(latest, setup):
    """Keep the schema-1 self-update contract understood by earlier Hubs."""
    if latest.get('schema') != 1:
        raise ValueError('Older launchers require update channel schema 1')
    if any(not latest.get(key) for key in ('setupExeUrl', 'setupExeSha256', 'setupExeBytes', 'setupVersion')):
        raise ValueError('The update must include the legacy launcher self-update fields')
    url = urllib.parse.urlsplit(latest['setupExeUrl'])
    if url.scheme != 'https' or not url.netloc or url.username or url.password:
        raise ValueError('Launcher self-update requires a public HTTPS asset URL')
    if not setup.is_file() or not 0 < setup.stat().st_size < GITHUB_ASSET_BYTES:
        raise ValueError('The launcher upgrade is missing or exceeds the asset size limit')
    if (setup.stat().st_size != latest['setupExeBytes']
            or sha256_file(setup) != latest['setupExeSha256']
            or setup_version(setup) != latest['setupVersion']):
        raise ValueError('The launcher upgrade and legacy self-update metadata differ')


def publish_assets(dist_dir, latest, setup_path=None):
    target_version = latest['version']
    patch_name = latest['patchFile']
    patch_path = dist_dir / patch_name
    if sha256_file(patch_path) != latest['patchSha256'] or patch_path.stat().st_size != latest['patchBytes']:
        raise ValueError('Prepared patch differs from its update metadata')
    patch = read_patch(patch_path)
    chunk_assets = verify_assets(dist_dir, patch)
    optional_assets = []
    for entry in latest.get('languageAssets', []):
        name = entry['file']
        if Path(name).name != name or '/' in name or '\\' in name:
            raise ValueError('Invalid optional language asset path')
        path = dist_dir / name
        if not path.is_file() or path.stat().st_size != entry['bytes'] or sha256_file(path) != entry['sha256']:
            raise ValueError('Prepared optional language asset differs: ' + name)
        optional_assets.append(path)
    tag_name = 'v' + target_version
    setup_exe = setup_path or dist_dir / 'Aetherfall-Downloader' / 'Aetherfall Setup.exe'
    validate_launcher_bridge(latest, setup_exe)
    if setup_exe.is_file() and (sha256_file(setup_exe) != latest.get('setupExeSha256') or setup_exe.stat().st_size != latest.get('setupExeBytes')):
        raise ValueError('Prepared Setup differs from its update metadata')
    downloader = dist_dir / 'Aetherfall-Downloader.zip'
    if not downloader.is_file() or sha256_file(downloader) != latest.get('downloaderSha256') or downloader.stat().st_size != latest.get('downloaderBytes'):
        raise ValueError('Prepared downloader ZIP differs from its update metadata')
    with zipfile.ZipFile(downloader) as archive:
        if hashlib.sha256(archive.read('Aetherfall Setup.exe')).hexdigest() != latest.get('setupExeSha256'):
            raise ValueError('Downloader ZIP and published Setup differ')
    for asset_path in [patch_path, downloader, setup_exe] + [dist_dir / asset['file'] for asset in chunk_assets] + optional_assets:
        if asset_path.is_file() and asset_path.stat().st_size >= GITHUB_ASSET_BYTES:
            raise ValueError('GitHub release asset must be under 2 GiB: ' + asset_path.name)
    print(f'[3/4] Uploading {patch_name} and Aetherfall-Setup.exe to GitHub Release {tag_name}...')
    token = get_github_token()
    headers = {
        'Authorization': f'Bearer {token}',
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'Aetherfall-Publisher',
        'Content-Type': 'application/json',
    }
    rel_api = 'https://api.github.com/repos/AngelShade/Aion-Aetherfall-4.8-Repack/releases'
    payload = json.dumps({
        'tag_name': tag_name,
        'target_commitish': 'main',
        'name': f'Aion Aetherfall Repack v{target_version} (Delta Update Patch)',
        'body': f"Chunked update to **v{target_version}** ({latest['filesChanged']} updated files).\n\nPlayers: Open **`Aetherfall Setup.exe`** and click **`Update Repack`** to download only required chunks. Older Setup versions restart after updating themselves; click Update Repack again afterward.",
        'draft': False,
        'prerelease': False,
    }).encode('utf-8')
    try:
        req = urllib.request.Request(rel_api, data=payload, headers=headers, method='POST')
        with urllib.request.urlopen(req) as resp:
            rel_info = json.loads(resp.read().decode())
    except urllib.error.HTTPError:
        req = urllib.request.Request(f'{rel_api}/tags/{tag_name}', headers=headers)
        with urllib.request.urlopen(req) as resp:
            rel_info = json.loads(resp.read().decode())

    upload_base = rel_info['upload_url'].split('{')[0]
    existing_assets = {a['name']: a['id'] for a in rel_info.get('assets', [])}

    def upload_asset(file_path: Path, asset_name: str, content_type: str):
        if asset_name in existing_assets:
            del_req = urllib.request.Request(f"{rel_api}/assets/{existing_assets[asset_name]}", headers=headers, method='DELETE')
            with urllib.request.urlopen(del_req):
                pass
        raw_data = file_path.read_bytes()
        up_headers = {
            'Authorization': f'Bearer {token}',
            'Accept': 'application/vnd.github+json',
            'User-Agent': 'Aetherfall-Publisher',
            'Content-Type': content_type,
            'Content-Length': str(len(raw_data)),
        }
        u_req = urllib.request.Request(f'{upload_base}?name={asset_name}', data=raw_data, headers=up_headers, method='POST')
        with urllib.request.urlopen(u_req) as u_resp:
            u_info = json.loads(u_resp.read().decode())
            print('      Uploaded:', u_info['browser_download_url'])

    for asset in chunk_assets:
        upload_asset(dist_dir / asset['file'], asset['file'], 'application/zip')
    for path in optional_assets:
        upload_asset(path, path.name, 'application/zip')
    upload_asset(patch_path, patch_name, 'application/json' if patch_path.suffix == '.json' else 'application/zip')
    if setup_exe.is_file():
        upload_asset(setup_exe, 'Aetherfall-Setup.exe', 'application/octet-stream')
    upload_asset(downloader, 'Aetherfall-Downloader.zip', 'application/zip')
    # Change public entry points only after the matching complete kit is available.
    refresh_public_download_links(
        'https://api.github.com/repos/AngelShade/Aion-Aetherfall-4.8-Repack/contents', headers)

    print('[4/4] Publishing latest-update.json to GitHub main branch via API...')
    import base64
    contents_api = 'https://api.github.com/repos/AngelShade/Aion-Aetherfall-4.8-Repack/contents/latest-update.json'
    old_sha = None
    try:
        get_req = urllib.request.Request(f'{contents_api}?ref=main', headers=headers)
        with urllib.request.urlopen(get_req) as g_resp:
            old_sha = json.loads(g_resp.read().decode()).get('sha')
    except urllib.error.HTTPError:
        pass

    put_body = {
        'message': f'Publish live update manifest for v{target_version}',
        'content': base64.b64encode((json.dumps(latest, indent=2) + '\n').encode('utf-8')).decode('ascii'),
        'branch': 'main',
    }
    if old_sha:
        put_body['sha'] = old_sha
    put_req = urllib.request.Request(contents_api, data=json.dumps(put_body).encode('utf-8'), headers=headers, method='PUT')
    with urllib.request.urlopen(put_req):
        print(f'SUCCESS! Update v{target_version} is now live for all players via "Update Repack"!')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, help='Repack folder; defaults to repackFolder in the configured release layout')
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--dist', type=Path)
    parser.add_argument('--version', default='', help='Target version (e.g. 0.87). Auto-increments if omitted.')
    parser.add_argument('--prepared', action='store_true', help='Use the current installed repack index and version; no rescan, version bump or repack metadata writes')
    parser.add_argument('--no-upload', action='store_true', help='Build patch ZIP locally without uploading to GitHub')
    args = parser.parse_args()
    args.dist = release_path(args.dist)
    args.baseline = release_path(args.baseline) if args.baseline else args.dist / 'Maintenance/Baseline/baseline-files-0.86.json'
    layout_path = args.dist / 'Maintenance/release-layout.json'
    if layout_path.is_file():
        layout = json.loads(layout_path.read_text(encoding='utf-8-sig'))
        args.package = Path(layout['repackFolder'])
        args.baseline = args.dist / layout['baselineIndex']
        args.prepared = True
    if args.package is None:
        parser.error('Pass --package or configure repackFolder in Maintenance/release-layout.json')
    scan_and_build_patch(args.package.resolve(), args.baseline.resolve(), args.dist.resolve(), args.version, not args.no_upload, args.prepared)
