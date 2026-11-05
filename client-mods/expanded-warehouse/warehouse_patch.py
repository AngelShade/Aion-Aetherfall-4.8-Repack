"""Version-checked 4.8 NA character/account warehouse client changes.

The native list views are kept intact. We enlarge their XML slot vectors and
repair the warehouse initialization bounds, active-slot counts and shared
native storage capacity setter.
"""

import copy
import hashlib
import io
from pathlib import Path
import struct
import tempfile
import xml.etree.ElementTree as ET
import zipfile

from codec import binary_xml, encode_binary_xml, encode_pak, read_pak


CHARACTER_SLOTS = 360
ACCOUNT_SLOTS = 540
SUPPORTED_DLLS = {
    "5334cf2164468678e45fe1a5decf58a0fbc4fd7f22cfdcbb87d28edce8d2c11c",  # original
    "fdd4e229190d841f96144b51c979ac6466119f6044ba452eada0b5886e0e09f6",  # inventory only
    "71cd8f70411897a61bf861318c05c16577d16e942ab4992cf7dad18fc8f94714",  # local inventory + market
    "25febcdf45125484a24785544246f6170289479f1e523fec68741fec2aa7975d",  # verified Wardrobe + inventory/search
}
CAVE = 0x144E680
HOOKS = (
    (0x946EED, bytes.fromhex("4883ff707c9d")),
    (0x946F8D, bytes.fromhex("4883ff107c9d")),
    (0x9472BB, bytes.fromhex("be03000000")),
)

# The UI's active cell counts do not change the native storage model. Both
# SM_WAREHOUSE_INFO and SM_CUBE_UPDATE reach this setter before item admission.
CAPACITY_SITE = 0x45FA70
CAPACITY_CAVE = CAVE + 0x48
CAPACITY_ORIGINAL = bytes.fromhex('4c8bdc4883ec68')


def patch_capacity_dll(payload):
    data = bytearray(payload)
    # Storage::setCapacity(this, count): +0x34 is native storage type, +0x30
    # is capacity. Keep all other storages and the original setter side effects.
    code = bytes.fromhex('837934017507ba68010000eb0b837934027505ba1c020000')
    code += CAPACITY_ORIGINAL
    code += _jump(CAPACITY_CAVE + len(code), CAPACITY_SITE + len(CAPACITY_ORIGINAL))
    hook = _jump(CAPACITY_SITE, CAPACITY_CAVE).ljust(len(CAPACITY_ORIGINAL), b'\x90')
    if data[CAPACITY_SITE:CAPACITY_SITE+len(hook)] == hook and data[CAPACITY_CAVE:CAPACITY_CAVE+len(code)] == code:
        return payload
    if data[CAPACITY_SITE:CAPACITY_SITE+len(hook)] != CAPACITY_ORIGINAL or any(data[CAPACITY_CAVE:CAPACITY_CAVE+len(code)]):
        raise ValueError('Unknown native warehouse capacity setter/cave')
    # Refuse unexpanded clients; this is an incremental repair of the owned patch.
    if data[HOOKS[0][0]:HOOKS[0][0]+1] != b'\xe9' or data[CAVE:CAVE+7] != bytes.fromhex('4881ff68010000'):
        raise ValueError('Expanded warehouse initialization is required')
    data[CAPACITY_SITE:CAPACITY_SITE+len(hook)] = hook
    data[CAPACITY_CAVE:CAPACITY_CAVE+len(code)] = code
    return bytes(data)
def patch_collapse_dll(payload):
    from warehouse_controls import patch_dll as patch_controls
    return patch_controls(payload)


def _jump(source, target):
    return b"\xe9" + struct.pack("<i", target - source - 5)


def patch_dll(payload):
    digest = hashlib.sha256(payload).hexdigest()
    if digest not in SUPPORTED_DLLS:
        raise ValueError("Unsupported Game.dll. Use the matching original, Inventory-only, or verified local Inventory/Market build.")
    data = bytearray(payload)
    for site, original in HOOKS:
        if data[site:site + len(original)] != original:
            raise ValueError(f"Warehouse instruction differs at {site:x}")
    # Code is placed in the verified empty executable tail of this DLL build.
    code = bytearray()
    entry = []
    for index, (site, original) in enumerate(HOOKS):
        entry.append(CAVE + len(code))
        if index < 2:
            limit = CHARACTER_SLOTS if index == 0 else ACCOUNT_SLOTS
            loop = 0x946E90 if index == 0 else 0x946F30
            tail = site + len(original)
            # The original signed comparison is only four bytes; the larger
            # immediate and both branch targets live in the code cave.
            code += b"\x48\x81\xff" + struct.pack("<I", limit)
            code += b"\x0f\x8c" + struct.pack("<i", loop - (CAVE + len(code) + 6))
            code += _jump(CAVE + len(code), tail)
        else:
            # Warehouse refresh reads these 32-bit counts for character,
            # account, then legion. Preserve all actual item and Kinah data.
            code += b"\xc7\x84\x24\xf4\x00\x00\x00" + struct.pack("<I", CHARACTER_SLOTS)
            code += b"\xc7\x84\x24\xf8\x00\x00\x00" + struct.pack("<I", ACCOUNT_SLOTS)
            code += original
            code += _jump(CAVE + len(code), site + len(original))
    if len(code) > 0x180 or any(data[CAVE:CAVE + len(code)]):
        raise ValueError("Warehouse code does not fit its verified empty region")
    for (site, original), target in zip(HOOKS, entry):
        replacement = _jump(site, target).ljust(len(original), b"\x90")
        data[site:site + len(original)] = replacement
    data[CAVE:CAVE + len(code)] = code
    return patch_capacity_dll(bytes(data))


def patch_layout(root):
    if root.get("type") != "dlg_warehouse":
        raise ValueError("Expected original warehouse dialog")
    root.set("frame", "15,30,590,680")
    root.attrib.pop('align_type', None)
    root.set('flag', 'close;title;movable')
    personal = root.find("Widget[@name='personal_container']")
    personal.set("frame", "0,0,584,615")
    personal.find("Widget[@name='personal_title']").set("frame", "10,5,350,24")
    character = personal.find("Widget[@name='personal_warehouse']")
    character.set("frame", "15,28,560,274")
    character.set("slot_num", str(CHARACTER_SLOTS))
    personal.find("Widget[@name='account_title_button']").set("frame", "0,307,584,25")
    account_container = personal.find("Widget[@name='account_container']")
    account_container.set("frame", "0,334,584,276")
    account = account_container.find("Widget[@name='account_warehouse']")
    account.set("frame", "15,1,560,274")
    account.set("slot_num", str(ACCOUNT_SLOTS))
    # All slots are available from the start under the matching server flag.
    extend = personal.find("Widget[@name='extend']")
    if extend is not None:
        personal.remove(extend)
    root.find("Widget[@name='guild_container']").set("frame", "0,0,584,615")
    root.find("Widget[@name='sort']").set("frame", "552,623,21,21")
    root.find("Widget[@name='money']").set("frame", "278,622,262,24")
    root.find("Widget[@name='money_item']").set("frame", "279,623,24,22")
    from warehouse_search import search_widgets
    result = search_widgets(root)
    from warehouse_controls import patch_layout as patch_controls, money_hitbox
    patch_controls(result)
    money_hitbox(result)
    return result


def patch_archive(payload, prefix):
    name = prefix + "warehouse_dialog.xml"
    with tempfile.TemporaryDirectory(prefix="warehouse-archive-") as temporary:
        source = Path(temporary) / "source.pak"
        source.write_bytes(payload)
        with read_pak(source) as archive:
            info = archive.infolist()
            if len(info) != len({part.filename for part in info}):
                raise ValueError("Duplicate archive entries")
            if name not in archive.namelist():
                raise ValueError(f"Missing {name}")
            content = {part.filename: archive.read(part.filename) for part in info}
            comment = archive.comment
        updated = dict(content)
        layout = patch_layout(binary_xml(content[name]))
        updated[name] = encode_binary_xml(layout)
        if ET.tostring(binary_xml(updated[name])) != ET.tostring(layout):
            raise ValueError("Warehouse XML round trip failed")
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as archive:
            archive.comment = comment
            for part in info:
                archive.writestr(copy.copy(part), updated[part.filename])
        encoded = bytes(encode_pak(output.getvalue()))
        check = Path(temporary) / "check.pak"
        check.write_bytes(encoded)
        with read_pak(check) as archive:
            if archive.namelist() != list(content) or archive.testzip() is not None:
                raise ValueError("Rebuilt warehouse archive failed verification")
            if any(archive.read(part) != value for part, value in updated.items()):
                raise ValueError("Rebuilt warehouse archive differs")
        return encoded
