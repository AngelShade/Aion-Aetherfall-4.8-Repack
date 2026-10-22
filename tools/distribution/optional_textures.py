"""Keep the optional world texture component out of cumulative core payloads."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

OBSOLETE_TOOLS = {'Manage HD Textures.cmd', 'Make Admin.cmd', 'Make-Admin.ps1', 'Client/Mods/HDTextures/Manage.ps1'}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def load(package):
    path = package / 'Client/Mods/HDTextures/component.json'
    return read(path) if path.is_file() else None


def standard_assets(contract):
    if not contract:
        return {}
    assets = {'Client/' + e['path']: e for e in contract['files']}
    source = {e['path']: e for e in contract['files']}
    assets.update({'Client/' + e['packagePath']: source[e['path']] for e in contract['shared']})
    return assets


def standard_manifest(document, contract):
    shared = {e['path']: e for e in contract['shared']}
    if document['packageId'] != contract['graphicsPackageId']:
        raise ValueError('Rebase the HD component for the new graphics package before updating')
    for i, entry in enumerate(document['files']):
        if entry['path'] in shared:
            allowed = shared[entry['path']]
            if entry != allowed['standardEntry'] and entry != allowed['hdEntry']:
                raise ValueError('Shared graphics data needs an HD component rebase: ' + entry['path'])
            document['files'][i] = allowed['standardEntry']
    return document


def validate_core(package, entries):
    contract = load(package)
    if not contract:
        return
    owned = {p.casefold(): e for p, e in standard_assets(contract).items()}
    graphics = read(package / 'Client' / contract['graphicsManifest'])
    shared = {e['path']: e for e in contract['shared']}
    for record in graphics['files']:
        if record['path'] in shared and record != shared[record['path']]['standardEntry']:
            raise ValueError('The full repack must use the standard shared graphics manifest')
    for item in entries:
        relative = item['path'].casefold()
        if relative in {p.casefold() for p in OBSOLETE_TOOLS}:
            raise ValueError('Standalone tools were replaced by Hub Mods & tools: ' + item['path'])
        if relative.startswith('client/mods/textureupscale/') or relative.startswith('client/mods/hdtextures/standard-cache/') or relative.startswith('client/mods/hdtextures/.transactions/') or relative in {'client/mods/hdtextures/installed.json', 'client/mods/hdtextures/mod.json'}:
            raise ValueError('Optional texture state/cache cannot be distributed: ' + item['path'])
        if relative in owned and (item['sha256'] != owned[relative]['standardSha256'] or item['bytes'] != owned[relative]['standardBytes']):
            raise ValueError('HD texture bytes cannot be included in the core patch: ' + item['path'])


def build(contract_path, client, output):
    contract = read(contract_path)
    if output.exists():
        raise ValueError('Use a fresh HD archive output path')
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_STORED, allowZip64=True) as archive:
        header = {k: v for k, v in contract.items() if k not in {'archiveSha256', 'archiveBytes', 'downloadUrl'}}
        archive.writestr('hd-pack-manifest.json', json.dumps(header, indent=2))
        archive.writestr('README.txt', 'AETHERFALL OPTIONAL HD WORLD TEXTURES\r\n\r\nUpdate to repack 0.94 first. Close Aion normally. Open Aetherfall Hub,\r\nchoose Mods & tools, then Install ZIP and select this ZIP intact.\r\nDo not extract it into the game. Allow 10 GiB free on the game drive\r\nafter downloading. Use the HD switch to restore standard textures.\r\nHD textures do not change gameplay. Keep this download for reinstallation.\r\n')
        for entry in contract['files']:
            relative = entry['path']
            source = (client / relative).resolve(strict=True)
            if not source.is_relative_to(client.resolve()) or source.stat().st_size != entry['hdBytes'] or sha(source) != entry['hdSha256']:
                raise ValueError('HD source does not match the component: ' + relative)
            archive.write(source, 'payload/' + relative)
    result = {'archiveFile': output.name, 'archiveBytes': output.stat().st_size, 'archiveSha256': sha(output), 'assetVersion': contract['assetVersion'], 'files': len(contract['files'])}
    output.with_suffix('.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--contract', type=Path, required=True)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.contract, args.client, args.output)))
