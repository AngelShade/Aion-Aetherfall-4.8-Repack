"""Recover recorded default settings without packaging or changing owner choices."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

from player_settings import KEEP


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def recover(entry, output):
    if not entry.get('mutable') or entry['path'].casefold() not in {path.casefold() for path in KEEP}:
        raise ValueError('Only recorded player-setting defaults can be recovered')
    folder = Path(output)/'stock-defaults'
    folder.mkdir(parents=True, exist_ok=True)
    target = folder/(entry['sha256']+'.bin')
    if target.is_file() and target.stat().st_size == entry['bytes'] and sha(target) == entry['sha256']:
        return target
    candidates = sorted((p for p in Path(output).parent.iterdir() if p.is_dir() and re.fullmatch(r'\d+\.\d+\.\d+', p.name)),
                        key=lambda p: tuple(map(int, p.name.split('.'))), reverse=True)
    for previous in candidates:
        if previous.resolve() == Path(output).resolve():
            continue
        latest_path = previous/'latest-update.json'
        if not latest_path.is_file():
            continue
        latest = json.loads(latest_path.read_text(encoding='utf-8-sig'))
        manifest_name = latest.get('patchFile', '')
        if Path(manifest_name).name != manifest_name or not manifest_name.endswith('.json'):
            continue
        manifest_path = previous/manifest_name
        if not manifest_path.is_file() or sha(manifest_path) != latest.get('patchSha256'):
            continue
        manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
        record = next((row for row in manifest.get('files', []) if row['path'].casefold() == entry['path'].casefold()
                       and row['sha256'] == entry['sha256'] and row['bytes'] == entry['bytes']), None)
        if record is None or not record.get('parts'):
            continue
        assets = {row['file']: row for row in manifest['assets']}
        with target.open('wb') as destination:
            for part in record['parts']:
                name = part['asset']
                if Path(name).name != name or '/' in name or '\\' in name or name not in assets:
                    raise ValueError('Invalid recorded stock asset path')
                asset = previous/name
                if asset.stat().st_size != assets[name]['bytes'] or sha(asset) != assets[name]['sha256']:
                    raise ValueError('Recorded stock asset differs from its normal manifest')
                with zipfile.ZipFile(asset) as archive:
                    payload = archive.read(part['sha256'])
                if len(payload) != part['bytes'] or hashlib.sha256(payload).hexdigest() != part['sha256']:
                    raise ValueError('Recorded stock part failed verification')
                destination.write(payload)
        if target.stat().st_size != entry['bytes'] or sha(target) != entry['sha256']:
            raise ValueError('Recorded stock settings failed full-file verification')
        pointer = folder/(entry['sha256']+'.json')
        pointer.write_text(json.dumps(dict(path=entry['path'], sha256=entry['sha256'],
                                          normalManifest=str(manifest_path), ownerFileChanged=False), indent=2), encoding='utf-8')
        return target
    raise ValueError('Recorded default bytes unavailable; preserve owner settings and recover the prior normal artifact: '+entry['path'])
