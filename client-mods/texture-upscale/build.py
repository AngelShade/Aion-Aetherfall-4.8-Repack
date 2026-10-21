"""Build a 2x AI-upscaled texture package for the textures a set of zones actually place.

The builder never changes the client. It maps each zone's placed brushes/objects to
their meshes and DDS textures, upscales colour with Real-ESRGAN (wrap-padded so tiling
textures stay seamless), keeps the original fine grain, rebuilds the original DXT format
with a full mip chain and repacks the archives with every other entry byte-identical.
"""
import argparse, collections, copy, hashlib, io, json, re, shutil, struct, subprocess, sys, zipfile
from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, encode_pak

PAD = 16           # wrap padding around each tile, in source pixels
MIN_SIDE = 64      # tiny textures gain nothing visible
MAX_SOURCE = 512   # 1024+ sources stay as they are to bound video memory
ORIGINAL_MIX = 0.10
CONTRAST = 1.10      # owner review: plain upscale read as faded old film
SATURATION = 1.15
FORMATS = {b'DXT1': 'DXT1', b'DXT3': 'DXT3', b'DXT5': 'DXT5'}


def sha(data): return hashlib.sha256(data).hexdigest()
def norm(p): return p.replace('\\', '/').lower()
def strip_common(p): return p[len('levels/common/'):] if p.startswith('levels/common/') else p


def zone_targets(client, zones):
    """Return {archive relative path: set(entry names)} for the textures the zones use."""
    common = client / 'Levels' / 'common'
    meshes, textures = {}, {}
    for pak in sorted(common.glob('Mesh_*.pak')):
        index = meshes if pak.name.startswith('Mesh_Meshes') else textures if pak.name.startswith('Mesh_Textures') else None
        if index is None: continue
        with read_pak(pak) as z:
            for info in z.infolist(): index[norm(info.filename)] = (pak.name, info.filename)
    targets = collections.defaultdict(set)
    for zone in zones:
        level_dir = client / 'Levels' / zone
        cgfs = set()
        with read_pak(level_dir / 'Level.pak') as z:
            for name in ('brush.lst', 'objects.lst', 'entitycontexts.lst', 'entitycontexts2.lst'):
                if name not in z.namelist(): continue
                for m in re.finditer(rb'[A-Za-z0-9_\\/.\-]{6,}\.cgf', z.read(name), re.I):
                    cgfs.add(strip_common(norm(m.group(0).decode('latin1'))))
        readers = {}
        for cgf in sorted(cgfs):
            if cgf not in meshes: continue
            pak, entry = meshes[cgf]
            readers.setdefault(pak, read_pak(common / pak))
            for m in re.finditer(rb'[A-Za-z0-9_\\/.\-]{4,}\.(?:dds|tif)', readers[pak].read(entry), re.I):
                key = re.sub(r'\.tif$', '.dds', strip_common(norm(m.group(0).decode('latin1'))))
                if key in textures:
                    pak_name, real = textures[key]
                    targets['Levels/common/' + pak_name].add(real)
        for r in readers.values(): r.close()
        # the zone's own terrain detail layers
        zone_pak = next(level_dir.glob(zone + '.pak'))
        with read_pak(zone_pak) as z:
            for name in z.namelist():
                if name.lower().startswith('detail/') and name.lower().endswith('.dds'):
                    targets[zone_pak.relative_to(client).as_posix()].add(name)
    return targets


def eligible(data):
    if data[:4] != b'DDS ' or data[84:88] not in FORMATS: return None
    height, width = struct.unpack_from('<II', data, 12)
    if min(width, height) < MIN_SIDE or max(width, height) > MAX_SOURCE: return None
    if width & (width - 1) or height & (height - 1): return None
    return FORMATS[data[84:88]], width, height


def wrap_pad(image):
    w, h = image.size
    tiled = Image.new(image.mode, (w * 3, h * 3))
    for x in range(3):
        for y in range(3): tiled.paste(image, (x * w, y * h))
    return tiled.crop((w - PAD, h - PAD, 2 * w + PAD, 2 * h + PAD))


def finish(original, upscaled4, style='boost'):
    """Crop the padding, reduce to 2x (plain, or with clarity and a little colour), keep alpha."""
    w, h = original.size
    ai = upscaled4.convert('RGB').crop((PAD * 4, PAD * 4, PAD * 4 + w * 4, PAD * 4 + h * 4))
    target = (w * 2, h * 2)
    ai = ai.resize(target, Image.Resampling.LANCZOS)
    base = original.convert('RGB').resize(target, Image.Resampling.LANCZOS)
    if style == 'plain':
        rgb = ai
    else:
        rgb = Image.blend(ai, base, ORIGINAL_MIX).filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))
        rgb = ImageEnhance.Color(ImageEnhance.Contrast(rgb).enhance(CONTRAST)).enhance(SATURATION)
    alpha = original.getchannel('A')
    lo, hi = alpha.getextrema()
    if lo == 255:
        out_alpha = Image.new('L', target, 255)
    else:
        out_alpha = alpha.resize(target, Image.Resampling.LANCZOS)
        if set(alpha.histogram()[1:255]) == {0}:     # cut-out alpha stays a clean cut-out
            out_alpha = out_alpha.point(lambda v: 255 if v >= 128 else 0)
    rgb.putalpha(out_alpha)
    return rgb


def encode_dds(image, fmt):
    size = image.size; payloads, header = [], None
    while True:
        buffer = io.BytesIO(); image.save(buffer, format='DDS', pixel_format=fmt)
        data = buffer.getvalue()
        if header is None: header = bytearray(data[:128])
        payloads.append(data[128:])
        if image.size == (1, 1): break
        image = image.resize((max(1, image.width // 2), max(1, image.height // 2)), Image.Resampling.LANCZOS)
    struct.pack_into('<I', header, 8, struct.unpack_from('<I', header, 8)[0] | 0x20000)
    struct.pack_into('<I', header, 28, len(payloads))
    struct.pack_into('<I', header, 108, 0x401008)
    result = bytes(header) + b''.join(payloads)
    check = Image.open(io.BytesIO(result)); check.load()
    if check.size != size or check.format != 'DDS': raise ValueError('DDS decode mismatch')
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--zones', required=True, help='comma-separated level folders, e.g. lf1,df1')
    p.add_argument('--esrgan', type=Path, help='realesrgan-ncnn-vulkan.exe (fills <work>/upscaled)')
    p.add_argument('--upscaled', type=Path, help='folder of 4x outputs from another model, e.g. pbrify_upscale.py')
    p.add_argument('--model-name', default='Real-ESRGAN ncnn realesrgan-x4plus')
    p.add_argument('--finish', choices=['plain', 'boost'], default='boost')
    p.add_argument('--skip-tracked', type=Path, help='a GraphicsOverhaul manifest; archives it tracks are left alone')
    p.add_argument('--previous-package', type=Path, action='append', default=[],
                   help='installed package folder; preserve its already-upscaled entries')
    p.add_argument('--prepare-only', action='store_true', help='extract pending source tiles without packing')
    p.add_argument('--work', type=Path, required=True, help='reusable cache folder outside the client')
    p.add_argument('--output', type=Path, required=True, help='new package folder')
    args = p.parse_args()
    client = args.client.resolve(); out = args.output.resolve(); work = args.work.resolve()
    for folder in (out, work):
        if folder == client or client in folder.parents: raise ValueError('Use folders outside the client')
    if out.exists(): raise ValueError('Use a new output folder')
    zones = [z for z in args.zones.split(',') if z]
    targets = zone_targets(client, zones)
    if args.skip_tracked:
        tracked = {f['path'].lower() for f in json.loads(args.skip_tracked.read_text())['files']}
        for archive in [a for a in targets if a.lower() in tracked]:
            print('Skipping archive tracked by the graphics package:', archive, flush=True); del targets[archive]
    preserved = collections.defaultdict(set)
    previous_ids = []
    for previous in args.previous_package:
        prior = json.loads((previous / 'manifest.json').read_text(encoding='utf-8-sig'))
        if prior['schema'] != 1 or Path(prior['clientRoot']).resolve() != client:
            raise ValueError('Previous package belongs to another client or schema')
        previous_ids.append(prior['packageId'])
        for archive in prior['files']:
            preserved[archive['path']].update(archive['changedEntries'])
    src_dir, up_dir = work / 'source', (args.upscaled.resolve() if args.upscaled else work / 'upscaled')
    src_dir.mkdir(parents=True, exist_ok=True); up_dir.mkdir(parents=True, exist_ok=True)

    plan = {}   # (archive, entry) -> (fmt, tile name)
    for archive, entries in sorted(targets.items()):
        with read_pak(client / archive) as z:
            for entry in sorted(entries):
                if entry in preserved[archive]: continue
                data = z.read(entry); info = eligible(data)
                if not info: continue
                tile = sha((archive + '|' + entry + '|' + sha(data)).encode())[:24] + '.png'
                plan[(archive, entry)] = (info[0], tile)
                if not (src_dir / tile).exists():
                    wrap_pad(Image.open(io.BytesIO(data)).convert('RGBA').convert('RGB')).save(src_dir / tile)
    pending = [t for _, t in plan.values() if not (up_dir / t).exists()]
    print(f'{len(plan)} textures in {len(targets)} archives; {len(pending)} need the GPU upscaler', flush=True)
    if args.prepare_only: return
    if pending and not args.esrgan:
        raise RuntimeError(f'{len(pending)} textures have no 4x output in {up_dir}')
    if pending:
        batch = work / 'batch'; shutil.rmtree(batch, ignore_errors=True); batch.mkdir()
        for t in pending: shutil.copyfile(src_dir / t, batch / t)
        subprocess.run([str(args.esrgan), '-i', str(batch), '-o', str(up_dir), '-n', 'realesrgan-x4plus', '-f', 'png'],
                       check=True, cwd=args.esrgan.parent, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        shutil.rmtree(batch)
    missing = [t for _, t in plan.values() if not (up_dir / t).exists()]
    if missing: raise RuntimeError(f'Upscaler produced no output for {len(missing)} textures')

    out.mkdir(parents=True)
    manifest = dict(schema=1, name='World texture upscale', zones=zones, clientRoot=str(client),
                    preservedPackages=previous_ids,
                    method=dict(upscaler=args.model_name, finish=args.finish, scale=2, originalMix=ORIGINAL_MIX, contrast=CONTRAST, saturation=SATURATION,
                                wrapPadding=PAD, sourceRange=[MIN_SIDE, MAX_SOURCE]), files=[])
    by_archive = collections.defaultdict(dict)
    for (archive, entry), value in plan.items(): by_archive[archive][entry] = value
    for archive, entries in sorted(by_archive.items()):
        source = client / archive; original = source.read_bytes()
        changed = {}
        with read_pak(source) as z:
            infos = z.infolist()
            if len({i.filename for i in infos}) != len(infos): raise ValueError('Duplicate entries in ' + archive)
            for entry, (fmt, tile) in entries.items():
                native = Image.open(io.BytesIO(z.read(entry))).convert('RGBA')
                changed[entry] = encode_dds(finish(native, Image.open(up_dir / tile), args.finish), fmt)
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as dest:
                for info in infos: dest.writestr(copy.copy(info), changed.get(info.filename, z.read(info)))
        encoded = encode_pak(buffer.getvalue())
        target = out / archive; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(encoded)
        # verify: same entry order, untouched entries identical, changed entries decode at 2x
        with read_pak(target) as built, read_pak(source) as base:
            if [i.filename for i in built.infolist()] != [i.filename for i in base.infolist()]: raise ValueError('Entry order changed: ' + archive)
            for info in base.infolist():
                data = built.read(info.filename)
                if info.filename in changed:
                    if data != changed[info.filename]: raise ValueError('Changed entry mismatch')
                elif data != base.read(info): raise ValueError('Untouched entry differs: ' + info.filename)
        if source.read_bytes() != original: raise ValueError('Client changed during build: ' + archive)
        manifest['files'].append(dict(path=archive, original=sha(original), staged=sha(encoded), changedEntries=sorted(changed)))
        print(f'Packed {archive}: {len(changed)} textures', flush=True)
    manifest['packageId'] = sha(json.dumps(manifest, sort_keys=True).encode())[:16]
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=1))
    print(json.dumps(dict(packageId=manifest['packageId'], archives=len(manifest['files']),
                          textures=sum(len(f['changedEntries']) for f in manifest['files'])), indent=1))


if __name__ == '__main__':
    main()
