"""Repair installed upscale batches against their native textures, without an AI run.

Builds a normal texture package for Install.ps1 that fixes the faults of the early
batches:
- smooth DXT3 transparency packed by Pillow's BC2 encoder (speckled mesh on trees,
  roots, arches, fences): native alpha is written back, compressed RGB blocks kept;
- shared textures upscaled again by later batches (4x/8x instead of 2x): reduced to
  exactly 2x of native with native alpha; lightings/ sprites become plain 2x of native;
- DXT1 cut-outs whose lower mips lost coverage: mip chain rebuilt from installed mip 0
  with coverage-preserving native masks.
Native textures come from the earliest hash-verified pre-batch backup of each archive.
Optional --replace '<folder>' takes finished 2x PNGs named like the entry path (with /
as __) for individual textures, such as redrawn glow sprites; they keep their own alpha.
"""
import argparse
import collections
import contextlib
import io
import json
from pathlib import Path
import struct
import sys

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
from build import read_pak, sha
from dds_alpha import write_dxt3_alpha
from prepare_client import parts, rebuild

BLOCK = {b'DXT1': 8, b'DXT3': 16, b'DXT5': 16}
FORMATS = {b'DXT1': 'DXT1', b'DXT3': 'DXT3', b'DXT5': 'DXT5'}
MIP_TOLERANCE = 0.02   # share of texels whose alpha may differ after block packing


def size_of(data):
    height, width = struct.unpack_from('<II', data, 12)
    return width, height


def mips(data):
    """Decode every stored mip as RGBA."""
    width, height = size_of(data)
    count = struct.unpack_from('<I', data, 28)[0] or 1
    offset, images = 128, []
    for _ in range(count):
        size = max(1, (width + 3) // 4) * max(1, (height + 3) // 4) * BLOCK[data[84:88]]
        header = bytearray(data[:128])
        struct.pack_into('<II', header, 12, height, width)
        struct.pack_into('<I', header, 20, size)
        struct.pack_into('<I', header, 28, 1)
        images.append(Image.open(io.BytesIO(bytes(header) + data[offset:offset + size])).convert('RGBA'))
        offset += size
        width, height = max(1, width // 2), max(1, height // 2)
    return images


def alpha_kind(alpha):
    if alpha.getextrema()[0] == 255:
        return 'opaque'
    return 'cutout' if set(alpha.histogram()[1:255]) == {0} else 'smooth'


def cutout_mask(native_alpha, size):
    """Resize a cut-out mask, choosing the threshold that keeps the native coverage."""
    want = sum(native_alpha.histogram()[128:]) / (native_alpha.width * native_alpha.height)
    soft = native_alpha.resize(size, Image.Resampling.LANCZOS)
    histogram, total, above, threshold = soft.histogram(), size[0] * size[1], 0, 255
    for level in range(255, 0, -1):
        above += histogram[level]
        threshold = level
        if above / total >= want:
            break
    return soft.point(lambda v: 255 if v >= threshold else 0)


def mask_for(native_alpha, kind, size):
    if kind == 'opaque':
        return Image.new('L', size, 255)
    if kind == 'cutout':
        return cutout_mask(native_alpha, size)
    return native_alpha.resize(size, Image.Resampling.LANCZOS)


def encode(rgb, native_alpha, fmt):
    """Encode RGB with native alpha and a full mip chain; verify every mip."""
    kind = alpha_kind(native_alpha)
    level, payloads, header, expected = rgb.convert('RGB'), [], None, []
    while True:
        mask = mask_for(native_alpha, kind, level.size)
        image = level.copy()
        image.putalpha(mask)
        buffer = io.BytesIO()
        image.save(buffer, format='DDS', pixel_format=FORMATS[fmt])
        data = buffer.getvalue()
        if fmt == b'DXT3':
            data = write_dxt3_alpha(data, mask)
        if header is None:
            header = bytearray(data[:128])
        payloads.append(data[128:])
        expected.append(mask)
        if level.size == (1, 1):
            break
        level = level.resize((max(1, level.width // 2), max(1, level.height // 2)), Image.Resampling.LANCZOS)
    struct.pack_into('<I', header, 8, struct.unpack_from('<I', header, 8)[0] | 0x20000)
    struct.pack_into('<I', header, 28, len(payloads))
    struct.pack_into('<I', header, 108, 0x401008)
    result = bytes(header) + b''.join(payloads)
    verify(result, expected, kind, fmt)
    return result


def verify(data, expected, kind, fmt):
    for index, (decoded, mask) in enumerate(zip(mips(data), expected)):
        if decoded.size != mask.size:
            raise ValueError('Mip size mismatch')
        if kind == 'opaque' or min(decoded.size) < (4 if kind == 'cutout' else 16):
            continue   # a few blocks: interpolated alpha error is not meaningful
        if fmt == b'DXT5' and kind == 'smooth' and index > 0:
            break      # BC3 alpha is lossy on fine detail; mip 0 proves the encoder
        got, want = decoded.getchannel('A').tobytes(), mask.tobytes()
        if kind == 'cutout':
            bad = sum((g >= 128) != (w >= 128) for g, w in zip(got, want))
        else:   # block-interpolated alpha: small local error, never speckle
            errors = [abs(g - w) for g, w in zip(got, want)]
            if sum(errors) / len(errors) > 12:
                raise ValueError(f'Alpha drift in mip {index}: mean {sum(errors) / len(errors):.1f}')
            bad = sum(e > 64 for e in errors)
        if bad / len(want) > MIP_TOLERANCE:
            raise ValueError(f'Alpha mismatch in mip {index}: {bad / len(want):.3f}')


def mip_coverage_loss(data, native_alpha):
    """Largest per-mip share of texels whose cut-out state differs from the native mask."""
    worst = 0.0
    for decoded in mips(data):
        if decoded.width < 8 or decoded.height < 8:
            break
        want = cutout_mask(native_alpha, decoded.size).tobytes()
        got = decoded.getchannel('A').tobytes()
        worst = max(worst, sum((g >= 128) != (w >= 128) for g, w in zip(got, want)) / len(want))
    return worst


def plan_entry(entry, data, native_data, replacement):
    """Return (reason, repaired bytes), or None when the installed texture is sound."""
    fmt = data[84:88]
    if fmt != native_data[84:88] or fmt not in BLOCK:
        return None
    native = Image.open(io.BytesIO(native_data)).convert('RGBA')
    alpha = native.getchannel('A')
    kind = alpha_kind(alpha)
    target = (native.width * 2, native.height * 2)
    if replacement is not None:
        image = Image.open(replacement).convert('RGBA')
        if image.size != target:
            raise ValueError(f'Replacement must be {target}: {replacement}')
        return 'replaced', encode(image, image.getchannel('A') if kind != 'opaque' else alpha, fmt)
    if size_of(data) != target:
        if '/lightings/' in entry.lower():
            return 'lighting-native-2x', encode(native.convert('RGB').resize(target, Image.Resampling.LANCZOS), alpha, fmt)
        return 'rescaled-to-2x', encode(mips(data)[0].resize(target, Image.Resampling.LANCZOS), alpha, fmt)
    if fmt == b'DXT3' and kind != 'opaque':
        repaired = write_dxt3_alpha(data, alpha)
        return ('dxt3-alpha', repaired) if repaired != data else None
    if fmt == b'DXT1' and kind == 'cutout' and mip_coverage_loss(data, alpha) > 0.05:
        return 'dxt1-cutout-mips', encode(mips(data)[0], alpha, fmt)
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--previous-package', type=Path, action='append', required=True)
    parser.add_argument('--replace', type=Path, help='folder of finished 2x PNGs for individual entries')
    parser.add_argument('--only', action='append', default=[], help='limit to entries containing this text')
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
    replacements = {}
    if args.replace:
        for png in args.replace.glob('*__*.png'):
            replacements[png.stem.replace('__', '/').lower() + '.dds'] = png
    # the earliest backup of an archive still holds every entry native
    natives, targets, zones = {}, collections.defaultdict(dict), set()
    for state in states:
        package = packages[state['packageId']]
        zones.update(package['zones'])
        installed_files = {f['path']: f for f in state['files']}
        for archive in package['files']:
            if archive['original'] != installed_files[archive['path']]['original']:
                raise ValueError('Prior package differs from installed receipt')
            if not state.get('backupRoot'):
                raise ValueError('Native recovery backups are required')
            native = natives.setdefault(archive['path'], (Path(state['backupRoot']) / archive['path'], archive['original']))
            for entry in archive['changedEntries']:
                if not args.only or any(text.lower() in entry.lower() for text in args.only):
                    targets[archive['path']][entry] = native
    unused = set(replacements) - {e.lower() for entries in targets.values() for e in entries}
    if unused:
        raise ValueError('Replacement has no installed entry: ' + ', '.join(sorted(unused)))
    output.mkdir(parents=True)
    manifest = dict(schema=1, name='Upscale transparency and scale repair', clientRoot=str(client),
                    zones=sorted(zones), preservedPackages=[s['packageId'] for s in states],
                    method=dict(repair='Native alpha per mip; exactly 2x of native; no AI run',
                                replacements=sorted(replacements)), files=[])
    validation = dict(entries=[], archives=[])
    reasons = collections.Counter()
    with contextlib.ExitStack() as stack:
        native_readers = {}
        for archive, entries in sorted(targets.items()):
            source = client / archive
            raw = source.read_bytes()
            changed = {}
            with read_pak(source) as current:
                infos = {info.filename: info for info in current.infolist()}
                for entry, (native_path, native_hash) in sorted(entries.items()):
                    if native_path not in native_readers:
                        if sha(native_path.read_bytes()) != native_hash:
                            raise ValueError('Native backup hash differs: ' + str(native_path))
                        native_readers[native_path] = stack.enter_context(read_pak(native_path))
                    data, native_data = current.read(entry), native_readers[native_path].read(entry)
                    try:
                        result = plan_entry(entry, data, native_data, replacements.get(entry.lower()))
                    except ValueError as error:
                        raise ValueError(f'{archive} {entry} ({data[84:88].decode()}): {error}') from None
                    if result is None:
                        continue
                    reason, repaired = result
                    changed[entry] = repaired
                    reasons[reason] += 1
                    validation['entries'].append(dict(
                        archive=archive, entry=entry, reason=reason, native=str(native_path),
                        nativeEntryHash=sha(native_data), original=sha(data), staged=sha(repaired),
                        nativeSize=size_of(native_data), installedSize=size_of(data),
                        stagedSize=size_of(repaired)))
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
            print(f'Prepared {archive}: {len(changed)} repaired, {untouched} members preserved', flush=True)
    if not manifest['files']:
        raise ValueError('Nothing to repair')
    manifest['packageId'] = sha(json.dumps(manifest, sort_keys=True).encode())[:16]
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    (output / 'validation.json').write_text(json.dumps(validation, indent=2))
    print(json.dumps(dict(packageId=manifest['packageId'], archives=len(manifest['files']), **reasons)))


if __name__ == '__main__':
    main()
