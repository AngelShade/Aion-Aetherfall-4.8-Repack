"""Optional language assets are separate from the English core update."""
import hashlib
import json
from pathlib import Path


def optional_path(relative):
    value = str(relative).replace('\\', '/').casefold()
    return value.startswith(('client/mods/languages/packs/', 'client/mods/languages/cache/',
                             'client/mods/languages/installed/'))


def validate_core(package, entries):
    for entry in entries:
        if optional_path(entry['path']):
            raise ValueError('Optional language payload/state is in the core inventory: ' + entry['path'])


def assets(package, patch_dir):
    catalog = Path(package) / 'Client/Mods/Languages/catalog.json'
    if not catalog.is_file():
        return []
    document = json.loads(catalog.read_text(encoding='utf-8-sig'))
    if document.get('schema') != 1:
        raise ValueError('Unsupported language catalog')
    result = []
    for pack in document['packs']:
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
