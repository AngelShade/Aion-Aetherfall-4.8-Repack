"""Pack and verify DXT3 transparency without relying on the DDS encoder's BC2 packer."""
import io
import struct

from PIL import Image


def quantized_alpha(alpha):
    return alpha.convert('L').point(lambda value: ((value + 8) // 17) * 17)


def write_dxt3_alpha(data, alpha):
    """Replace only DXT3 alpha blocks; retain header, RGB blocks and dimensions.

    Alpha is recovered from a trusted native mask, resized without AI. Validate
    every mip against independently decoded four-bit alpha before returning it.
    Pillow 12.2's BC2 encoder packs eight-bit values into four-bit fields, so its
    output must not be trusted for smooth transparency.
    """
    if data[:4] != b'DDS ' or data[84:88] != b'DXT3':
        raise ValueError('Expected a legacy DXT3 DDS')
    height, width = struct.unpack_from('<II', data, 12)
    mips = struct.unpack_from('<I', data, 28)[0] or 1
    if not width or not height or mips > max(width, height).bit_length():
        raise ValueError('Invalid DDS dimensions or mip count')
    result = bytearray(data)
    mask = alpha.convert('L').resize((width, height), Image.Resampling.LANCZOS)
    offset = 128
    for _ in range(mips):
        blocks_x, blocks_y = (width + 3) // 4, (height + 3) // 4
        size = blocks_x * blocks_y * 16
        if offset + size > len(data):
            raise ValueError('Truncated DXT3 mip')
        pixels = quantized_alpha(mask).load()
        for by in range(blocks_y):
            for bx in range(blocks_x):
                packed = 0
                for y in range(4):
                    for x in range(4):
                        value = pixels[min(bx * 4 + x, width - 1), min(by * 4 + y, height - 1)] // 17
                        packed |= value << (4 * (y * 4 + x))
                struct.pack_into('<Q', result, offset + (by * blocks_x + bx) * 16, packed)
        # Decode this mip as a standalone DDS to check pixel order and values.
        header = bytearray(data[:128])
        struct.pack_into('<II', header, 12, height, width)
        struct.pack_into('<I', header, 20, size)
        struct.pack_into('<I', header, 28, 1)
        decoded = Image.open(io.BytesIO(bytes(header) + result[offset:offset + size])).convert('RGBA')
        if decoded.getchannel('A').tobytes() != quantized_alpha(mask).tobytes():
            raise ValueError('DXT3 alpha round-trip mismatch')
        for block in range(blocks_x * blocks_y):
            pos = offset + block * 16 + 8
            if result[pos:pos + 8] != data[pos:pos + 8]:
                raise ValueError('DXT3 RGB block changed')
        offset += size
        width, height = max(1, width // 2), max(1, height // 2)
        mask = mask.resize((width, height), Image.Resampling.LANCZOS)
    if offset != len(data):
        raise ValueError('Unexpected trailing DXT3 data')
    if result[:128] != data[:128]:
        raise ValueError('DDS header changed')
    return bytes(result)
