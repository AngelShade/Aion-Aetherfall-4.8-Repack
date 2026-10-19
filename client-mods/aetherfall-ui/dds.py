"""Uncompressed 32-bit BGRA DDS read/write that preserves the original header."""
import struct


def header(data):
    if data[:4] != b'DDS ':
        raise ValueError('not a DDS file')
    height, width, pitch, depth, mips = struct.unpack_from('<5I', data, 12)
    flags, fourcc, bits = struct.unpack_from('<I4sI', data, 80)
    return dict(width=width, height=height, mips=mips, flags=flags, fourcc=fourcc, bits=bits)


def is_bgra32(data):
    h = header(data)
    return h['fourcc'] == b'\0\0\0\0' and h['bits'] == 32 and len(data) >= 128 + h['width'] * h['height'] * 4


def to_image(data):
    from PIL import Image
    h = header(data)
    return Image.frombuffer('RGBA', (h['width'], h['height']), bytes(data[128:128 + h['width'] * h['height'] * 4]), 'raw', 'BGRA', 0, 1)


def replace_pixels(data, image):
    """Return the original DDS bytes with only the top-level BGRA pixels replaced."""
    h = header(data)
    if image.size != (h['width'], h['height']):
        raise ValueError('size changed')
    r, g, b, a = image.convert('RGBA').split()
    from PIL import Image
    pixels = Image.merge('RGBA', (b, g, r, a)).tobytes()
    end = 128 + len(pixels)
    return bytes(data[:128]) + pixels + bytes(data[end:])
