"""Build the first Poeta/Sanctum art pass without changing the installed client.

Pillow only performs format conversion, resolution normalization and mipmaps.
The replacement art itself is authored with the built-in image generation tool.
"""
import argparse
from collections import defaultdict
import copy
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import zipfile
from xml.etree import ElementTree as ET

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / "expanded-warehouse"))
from codec import read_pak, encode_pak, binary_xml, encode_binary_xml


def digest(data):
    return hashlib.sha256(data).hexdigest()


def make_dds(image, size, original):
    """Use legacy BC1/BC3 headers and a complete mip chain, never DX10 DDS."""
    fmt = original[84:88].decode("ascii")
    if fmt not in ("DXT1", "DXT5"):
        raise ValueError(f"Unsupported original texture format {fmt}")
    reference = Image.open(io.BytesIO(original)).convert("RGBA")
    if reference.getextrema()[3] != (255, 255):
        raise ValueError("This first pack supports only opaque source textures")
    if size[0] * reference.height != size[1] * reference.width:
        raise ValueError("Replacement must preserve the original aspect ratio")
    image = image.convert("RGB").resize(size, Image.Resampling.LANCZOS)
    payloads, header = [], None
    while True:
        encoded = io.BytesIO()
        image.convert("RGBA").save(encoded, format="DDS", pixel_format=fmt)
        data = encoded.getvalue()
        if header is None:
            header = bytearray(data[:128])
        payloads.append(data[128:])
        if image.size == (1, 1):
            break
        image = image.resize((max(1, image.width // 2), max(1, image.height // 2)),
                             Image.Resampling.LANCZOS)
    struct.pack_into("<I", header, 8, struct.unpack_from("<I", header, 8)[0] | 0x20000)
    struct.pack_into("<I", header, 28, len(payloads))
    struct.pack_into("<I", header, 108, 0x401008)  # texture, complex, mipmap
    result = bytes(header) + b"".join(payloads)
    validate_dds(result, size, fmt)
    return result


def validate_dds(data, size, fmt):
    assert data[:4] == b"DDS " and data[84:88] == fmt.encode()
    assert Image.open(io.BytesIO(data)).size == tuple(size)
    width, height = size
    count = struct.unpack_from("<I", data, 28)[0]
    block_bytes = 8 if fmt == "DXT1" else 16
    offset = 128
    for level in range(count):
        length = ((width + 3) // 4) * ((height + 3) // 4) * block_bytes
        # Decode each mip independently to catch invalid compressed payloads.
        header = bytearray(data[:128])
        struct.pack_into("<II", header, 12, height, width)
        struct.pack_into("<I", header, 28, 1)
        mip = Image.open(io.BytesIO(bytes(header) + data[offset:offset + length])).convert("RGBA")
        assert mip.size == (width, height) and mip.getextrema()[3] == (255, 255)
        offset += length
        if level + 1 < count:
            assert (width, height) != (1, 1)
        width, height = max(1, width // 2), max(1, height // 2)
    assert mip.size == (1, 1) and offset == len(data)


def environment(payload, zone):
    """Adjust authored atmosphere, retaining entity/gameplay and time settings."""
    binary = payload[0] == 0x80
    root = binary_xml(payload) if binary else ET.fromstring(payload)
    changed = []
    settings = {
        "Fog": {"Start": "56", "End": "1280" if zone == "lf1" else "1720"},
        "EnvState": {"AmbientAmplify": "0.92", "SunAmplify": "1.06"},
        "MRTSky": {"MieScattering": "9.7", "ZenithColor": "74,137,188"},
        "MRTOcean": {"ReflectAmount": "0.58", "RefractAmount": "0.36",
                     "FogDensity": "0.095", "SunColorMultiplier": "4.2"},
    }
    for env in root.iter("Environment"):
        for tag, attributes in settings.items():
            node = env.find(tag)
            if node is None:
                raise ValueError(f"Missing {zone} environment {tag}")
            for key, value in attributes.items():
                if key not in node.attrib:
                    raise ValueError(f"Missing {zone} environment {tag}/{key}")
                changed.append({"element": tag, "attribute": key,
                                "before": node.get(key), "after": value})
                node.set(key, value)
    if not changed:
        raise ValueError("No environment nodes found")
    result = encode_binary_xml(root) if binary else ET.tostring(root, encoding="utf-16")
    # Verify the serialized tree exactly matches the intended tree.
    decoded = binary_xml(result) if binary else ET.fromstring(result)
    assert ET.tostring(decoded) == ET.tostring(root)
    return result, changed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--client-path", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "package")
    args = parser.parse_args()
    client, output = args.client_path.resolve(), args.output.resolve()
    if output == client or client in output.parents or output.exists():
        raise ValueError("Output must be a new directory outside the client")
    assets = json.loads((ROOT / "assets.json").read_text())
    groups = defaultdict(list)
    for asset in assets:
        groups[asset["archive"]].append(asset)
    for zone in ("lf1", "lc1"):
        groups[f"Levels/{zone}/Level.pak"]  # atmosphere archives
    manifest = {"name": "Poeta and Sanctum visual pass 1", "clientRoot": str(client),
                "files": [], "textures": [], "environmentChanges": {},
                "validation": "Archives, all mipmaps, alpha, unchanged entries; live visuals pending"}
    previews = []
    output.mkdir(parents=True)
    for relative, entries in groups.items():
        source_path = client / relative
        original_archive = source_path.read_bytes()
        with read_pak(source_path) as archive:
            original = {info.filename: archive.read(info) for info in archive.infolist()}
            infos = archive.infolist()
        assert len(original) == len(infos), "Duplicate archive names"
        content = dict(original)
        allowed_changes = set()
        for asset in entries:
            name = asset["entry"]
            source_image = ROOT / "assets" / (asset["asset"] + ".png")
            replacement = make_dds(Image.open(source_image), tuple(asset["size"]), original[name])
            content[name] = replacement
            allowed_changes.add(name)
            before = Image.open(io.BytesIO(original[name])).convert("RGB")
            after = Image.open(io.BytesIO(replacement)).convert("RGB")
            previews.append((asset["asset"], before, after))
            manifest["textures"].append({**asset, "originalSize": list(before.size),
                                         "format": original[name][84:88].decode(),
                                         "mipCount": struct.unpack_from("<I", replacement, 28)[0],
                                         "artSha256": digest(source_image.read_bytes()),
                                         "ddsSha256": digest(replacement)})
        if relative.endswith("/Level.pak"):
            zone = source_path.parent.name
            name = "mission_mission0.xml"
            content[name], changes = environment(original[name], zone)
            manifest["environmentChanges"][zone] = changes
            allowed_changes.add(name)
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as archive:
            for info in infos:
                entry = copy.copy(info)
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, content[info.filename])
        destination = output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        staged = bytes(encode_pak(buf.getvalue()))
        destination.write_bytes(staged)
        with read_pak(destination) as archive:
            assert archive.namelist() == list(original)
            assert archive.testzip() is None
            for name in original:
                actual = archive.read(name)
                assert actual == content[name], name
                if name not in allowed_changes:
                    assert actual == original[name], name
        # Do not proceed if the client changed while preparation was running.
        assert source_path.read_bytes() == original_archive
        manifest["files"].append({"path": relative, "original": digest(original_archive),
                                  "staged": digest(staged), "changedEntries": sorted(allowed_changes),
                                  "unchangedEntries": len(original) - len(allowed_changes)})
        print(f"Verified {relative}: {len(allowed_changes)} changed / {len(original)} entries")
    manifest["packageId"] = digest(json.dumps(manifest, sort_keys=True).encode())[:16]
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    # Compare the actual compressed textures that the client will receive.
    sheet = Image.new("RGB", (640, len(previews) * 188), (25, 27, 32))
    draw = ImageDraw.Draw(sheet)
    for row, (name, before, after) in enumerate(previews):
        y = row * 188
        draw.text((12, y + 2), name + "   Original / Replacement", fill="white")
        for col, im in enumerate((before, after)):
            thumb = im.copy()
            thumb.thumbnail((300, 156))
            sheet.paste(thumb, (12 + col * 320, y + 22))
    sheet.save(ROOT / "comparison.png")
    print(f"Prepared {len(assets)} textures and two atmosphere passes in {len(groups)} archives.")
    print(f"Package: {output}; client unchanged. ID {manifest['packageId']}")


if __name__ == "__main__":
    main()
