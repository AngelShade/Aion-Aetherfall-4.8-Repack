"""Use the radar image's actual bounds in the verified 4.8 native renderer."""
import struct

from patch_binary import layout, offset


# DlgRadar::Draw (vtable 0xccee98 + 0x30 -> 0x866880) reads radar_wnd
# (+0x538) into RAX at 0x866e97. It draws a fixed 156*scale square,
# centred using 156*scale horizontally and 165*scale vertically, then passes
# the rectangle through
# 0x570da0 / 0x8664b0 to map drawing and marker projection. Read the image's
# width/height instead; the same downstream rectangle still owns projection.
SITES = (
    (0x866f96, bytes.fromhex('f20f590d2aa54c00'), bytes.fromhex('f20f104868909090')),
    (0x866f9e, bytes.fromhex('f20f5925aa854c00'), bytes.fromhex('f20f106060909090')),
)


def patch(source):
    data = bytearray(source)
    _, opt, _, _, sections = layout(data)
    base = struct.unpack_from('<Q', data, opt + 24)[0]
    if base != 0x10000000 or struct.unpack_from('<Q', data, offset(sections, 0xccee98 + 0x30))[0] != base + 0x866880:
        raise ValueError('Unrecognized native radar renderer')
    states = [bytes(data[offset(sections, rva):offset(sections, rva) + len(old)]) for rva, old, _ in SITES]
    if states == [new for _, _, new in SITES]:
        return source
    if states != [old for _, old, _ in SITES]:
        raise ValueError('Native radar viewport instructions differ; refusing partial/unknown patch')
    for rva, old, new in SITES:
        at = offset(sections, rva)
        data[at:at + len(old)] = new
    return bytes(data)
