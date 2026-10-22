"""Package the current normal Setup output with clean, portable download support."""
import json
import zipfile
from pathlib import Path

from setup_version import setup_version


def build_downloader(setup, support, output, changelog):
    import hashlib

    def sha(data):
        return hashlib.sha256(data).hexdigest()

    setup, support, output = Path(setup), Path(support), Path(output)
    if not setup_version(setup):
        raise ValueError('Setup must be a versioned normal builder output')
    manifest_bytes = (support / 'release-manifest.json').read_bytes()
    manifest = json.loads(manifest_bytes.decode('utf-8-sig'))
    torrent_name = manifest['torrentFile']
    if Path(torrent_name).name != torrent_name or '/' in torrent_name or '\\' in torrent_name:
        raise ValueError('Torrent must be a support-folder filename')
    torrent = (support / torrent_name).read_bytes()
    if sha(torrent) != manifest['torrentSha256']:
        raise ValueError('Torrent differs from the pinned base manifest')
    tool_hashes = json.loads((support / 'bin/SHA256.json').read_text(encoding='utf-8-sig'))
    payload = {
        'Aetherfall Setup.exe': setup.read_bytes(),
        'release-manifest.json': manifest_bytes,
        torrent_name: torrent,
        'README.txt': Path(__file__).with_name('README.txt').read_bytes(),
        'CHANGELOG.md': Path(changelog).read_bytes(),
    }
    for tool in ('7za.exe', 'aria2c.exe'):
        data = (support / 'bin' / tool).read_bytes()
        if sha(data) != tool_hashes.get(tool):
            raise ValueError('Download tool checksum mismatch: ' + tool)
        payload['bin/' + tool] = data
    payload['bin/SHA256.json'] = json.dumps(
        {tool: sha(payload['bin/' + tool]) for tool in ('7za.exe', 'aria2c.exe')},
        indent=2).encode('utf-8')
    for name in ('tool-sources.json', '7zip/License.txt', '7zip/readme.txt',
                 'aria2/AUTHORS', 'aria2/COPYING', 'aria2/LICENSE.OpenSSL', 'aria2/README.mingw'):
        payload['licenses/' + name] = (support / 'licenses' / name).read_bytes()
    mirrors = json.loads((support / 'mirrors.json').read_text(encoding='utf-8-sig'))
    if mirrors.get('schema') != 1 or mirrors.get('release') != manifest['version']:
        raise ValueError('Mirror metadata differs from the pinned base manifest')
    payload['mirrors.json'] = json.dumps(mirrors, indent=2).encode('utf-8')
    # Never copy the owner's saved settings, Games, Downloads, logs or profiles.
    payload['settings.json'] = json.dumps(dict(
        MirrorsUrl='https://raw.githubusercontent.com/AngelShade/Aion-Aetherfall-4.8-Repack/main/mirrors.json',
        UpdateUrl='https://raw.githubusercontent.com/AngelShade/Aion-Aetherfall-4.8-Repack/main/latest-update.json',
        Mode='Auto', DownloadDirectory='Downloads', InstallDirectory='Games',
        DeleteArchiveAfter=False), indent=2).encode('utf-8')
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.zip.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(payload.items()):
                entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, data)
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    return output
