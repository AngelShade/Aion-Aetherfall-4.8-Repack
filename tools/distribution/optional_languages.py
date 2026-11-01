"""Optional language assets are separate from the English core update."""
import hashlib
import json
import urllib.request
from pathlib import Path

RELEASES_API = 'https://api.github.com/repos/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tags/'


def optional_path(relative):
    value = str(relative).replace('\\', '/').casefold()
    return value.startswith(('client/mods/languages/packs/', 'client/mods/languages/cache/',
                             'client/mods/languages/installed/'))


def validate_core(package, entries):
    for entry in entries:
        if optional_path(entry['path']):
            raise ValueError('Optional language payload/state is in the core inventory: ' + entry['path'])


def packs(package):
    catalog = Path(package) / 'Client/Mods/Languages/catalog.json'
    if not catalog.is_file():
        return []
    document = json.loads(catalog.read_text(encoding='utf-8-sig'))
    if document.get('schema') != 1:
        raise ValueError('Unsupported language catalog')
    return document['packs']


def hosted_here(pack, tag):
    return f'/releases/download/{tag}/' in pack['url']


def verify_published(package, tag):
    """Unchanged packs stay on the release that first hosted them; that release must still serve them."""
    releases = {}
    for pack in packs(package):
        if hosted_here(pack, tag):
            continue
        host = pack['url'].split('/releases/download/')[1].split('/')[0]
        if host not in releases:
            request = urllib.request.Request(RELEASES_API + host, headers={'User-Agent': 'Aetherfall-Publisher'})
            with urllib.request.urlopen(request) as response:
                releases[host] = {a['name']: a['size'] for a in json.load(response)['assets']}
        if releases[host].get(pack['file']) != pack['bytes']:
            raise ValueError('Published optional language asset differs: ' + pack['url'])


def assets(package, patch_dir, tag):
    """Only packs whose catalog URL names this release are uploaded with it."""
    result = []
    for pack in packs(package):
        if not hosted_here(pack, tag):
            continue
        name = pack['file']
        if Path(name).name != name or '/' in name or '\\' in name:
            raise ValueError('Unsafe optional language asset name')
        path = Path(patch_dir) / name
        if not path.is_file() or path.stat().st_size != pack['bytes'] or path.stat().st_size >= 2 * 1024 ** 3:
            raise ValueError('Missing or invalid optional language asset: ' + name)
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != pack['sha256']:
            raise ValueError('Optional language asset checksum mismatch: ' + name)
        result.append(path)
    return result
