"""Recover native lamp transparency in installed upscale batches, retaining RGB detail.

Builds a normal texture package for Install.ps1. Only previously upscaled
lightings/ DXT3 members are eligible. Native masks come from the earliest
verified pre-batch backups; no AI run or unrelated texture rewrite is needed.
"""
import argparse
import collections
import contextlib
import io
import json
from pathlib import Path
import sys

from PIL import Image, ImageStat

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
from build import read_pak, sha
from dds_alpha import write_dxt3_alpha
from prepare_client import parts, rebuild


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--previous-package', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external output directory')
    states = sorted((json.loads(p.read_text(encoding='utf-8-sig'))
                     for p in (client / 'Mods/TextureUpscale').glob('installed-*.json')),
                    key=lambda state: state['appliedAt'])
    states = [s for s in states if not s.get('restored')]
    packages = {}
    for directory in args.previous_package:
        manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8-sig'))
        if manifest['schema'] != 1 or Path(manifest['clientRoot']).resolve() != client:
            raise ValueError('Prior package belongs to another client or schema')
        packages[manifest['packageId']] = manifest
    if set(packages) != {s['packageId'] for s in states}:
        raise ValueError('Pass exactly the active installed texture packages')
    baselines, zones = {}, set()
    for state in states:
        package = packages[state['packageId']]
        zones.update(package['zones'])
        installed_files = {f['path']: f for f in state['files']}
        for archive in package['files']:
            if archive['original'] != installed_files[archive['path']]['original']:
                raise ValueError('Prior package differs from installed receipt')
            for entry in archive['changedEntries']:
                if '/lightings/' in entry.lower():
                    if not state.get('backupRoot'):
                        raise ValueError('Native recovery backups are required')
                    baselines.setdefault((archive['path'], entry),
                                         (Path(state['backupRoot']) / archive['path'], archive['original']))
    targets = collections.defaultdict(dict)
    for (archive, entry), baseline in baselines.items():
        targets[archive][entry] = baseline
    output.mkdir(parents=True)
    manifest = dict(schema=1, name='Lamp transparency repair', clientRoot=str(client),
                    zones=sorted(zones), preservedPackages=[s['packageId'] for s in states],
                    method=dict(repair='Native DXT3 alpha at every installed mip',
                                rgb='Original installed compressed RGB blocks retained'), files=[])
    validation = dict(entries=[], archives=[])
    with contextlib.ExitStack() as stack:
        native_readers = {}
        for archive, entries in sorted(targets.items()):
            source = client / archive
            raw = source.read_bytes()
            changed = {}
            with read_pak(source) as current:
                infos = {info.filename: info for info in current.infolist()}
                for entry, (native_path, native_hash) in sorted(entries.items()):
                    data = current.read(entry)
                    if data[84:88] != b'DXT3':
                        continue
                    if native_path not in native_readers:
                        if sha(native_path.read_bytes()) != native_hash:
                            raise ValueError('Native backup hash differs: ' + str(native_path))
                        native_readers[native_path] = stack.enter_context(read_pak(native_path))
                    native_data = native_readers[native_path].read(entry)
                    if native_data[84:88] != b'DXT3':
                        raise ValueError('Native texture format differs: ' + entry)
                    native = Image.open(io.BytesIO(native_data)).convert('RGBA')
                    alpha = native.getchannel('A')
                    if alpha.getextrema() == (255, 255):
                        continue
                    repaired = write_dxt3_alpha(data, alpha)
                    if repaired == data:
                        continue
                    changed[entry] = repaired
                    before = Image.open(io.BytesIO(data)).convert('RGBA')
                    after = Image.open(io.BytesIO(repaired)).convert('RGBA')
                    if before.convert('RGB').tobytes() != after.convert('RGB').tobytes():
                        raise ValueError('Decoded lamp color changed: ' + entry)
                    validation['entries'].append(dict(
                        archive=archive, entry=entry, native=str(native_path),
                        nativeArchiveHash=native_hash, nativeEntryHash=sha(native_data),
                        original=sha(data), staged=sha(repaired),
                        nativeSize=native.size, installedSize=after.size,
                        mips=int.from_bytes(data[28:32], 'little') or 1,
                        alphaMeanBefore=ImageStat.Stat(before.getchannel('A')).mean[0],
                        alphaMeanAfter=ImageStat.Stat(after.getchannel('A')).mean[0]))
            if not changed:
                continue
            encoded, _ = rebuild(raw, changed, infos)
            before_parts, _, _, version = parts(raw)
            after_parts, _, _, new_version = parts(encoded)
            if version != new_version or list(before_parts) != list(after_parts):
                raise ValueError('Texture archive structure changed')
            untouched = 0
            for name in before_parts:
                if name not in changed:
                    if before_parts[name][0] != after_parts[name][0]:
                        raise ValueError('Unrelated compressed member changed: ' + name)
                    untouched += 1
            target = output / archive
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(encoded)
            with read_pak(target) as check:
                for name, data in changed.items():
                    if check.read(name) != data:
                        raise ValueError('Repaired archive member differs: ' + name)
            if source.read_bytes() != raw:
                raise ValueError('Client archive changed during preparation: ' + archive)
            manifest['files'].append(dict(path=archive, original=sha(raw), staged=sha(encoded),
                                          changedEntries=sorted(changed)))
            validation['archives'].append(dict(path=archive, changed=len(changed),
                                               untouchedCompressedMembers=untouched))
            print(f'Prepared {archive}: {len(changed)} repaired masks, {untouched} compressed members preserved', flush=True)
    if not manifest['files']:
        raise ValueError('No damaged lamp masks found')
    manifest['packageId'] = sha(json.dumps(manifest, sort_keys=True).encode())[:16]
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    (output / 'validation.json').write_text(json.dumps(validation, indent=2))
    print(json.dumps(dict(packageId=manifest['packageId'], archives=len(manifest['files']),
                          repairedMasks=len(validation['entries']))))


if __name__ == '__main__':
    main()
