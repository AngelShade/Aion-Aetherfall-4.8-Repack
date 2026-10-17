"""Stage the 279-to-288 capacity upgrade without rebuilding other client mods."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import zipfile

import unified_inventory as inventory
import inventory_search as search

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, encode_pak, binary_xml, encode_binary_xml
from graphics_compat import prepare_incremental


def sha(data):
    return hashlib.sha256(data).hexdigest()


def capacity_code(slots, flags):
    saved = inventory.SLOTS, search.SLOTS, search.FLAGS
    try:
        inventory.SLOTS = search.SLOTS = slots
        search.FLAGS = flags
        init, update, _ = inventory.inventory_code()
        return [(inventory.INIT_CAVE, init), (inventory.UPDATE_CAVE, update)] + [
            (cave, inventory.effect_loop_code(cave, target, end))
            for _, target, end, cave in inventory.EFFECT_LOOPS] + [
            (search.POLL, search.poll_code()), (search.DRAW, search.draw_code())]
    finally:
        inventory.SLOTS, search.SLOTS, search.FLAGS = saved


def patch_dll(payload):
    if search.FLAGS + inventory.SLOTS > search.QUERY:
        raise ValueError('Inventory search flags would overlap its query')
    before = capacity_code(279, 0x28)
    after = capacity_code(inventory.SLOTS, search.FLAGS)
    data = bytearray(payload)
    for (start, old), (same, new) in zip(before, after, strict=True):
        if start != same or len(old) != len(new) or data[start:start + len(old)] != old:
            raise ValueError(f'Installed inventory code differs at {start:x}')
        data[start:start + len(new)] = new
    # These exact existing dispatches must still own the rewritten caves.
    for site, cave in [(inventory.INIT_SITE, inventory.INIT_CAVE),
                       (inventory.UPDATE_SITE, inventory.UPDATE_CAVE)]:
        if payload[site:site + 5] != b'\xe9' + struct.pack('<i', cave - site - 5):
            raise ValueError(f'Installed inventory hook differs at {site:x}')
    draw = payload[search.DRAW_SITE:search.DRAW_SITE + 5]
    if draw != b'\xe9' + struct.pack('<i', search.DRAW - search.DRAW_SITE - 5):
        # Expanded warehouses dispatch their own lists first, then tail-call the
        # same inventory draw routine. Keep that existing shared dispatch intact.
        import warehouse_search
        target = search.DRAW_SITE + 5 + struct.unpack('<i', draw[1:])[0]
        code = warehouse_search._draw(target)
        if (draw[0] != 0xe9 or not warehouse_search.CODE <= target < warehouse_search.STATE_CHARACTER
                or payload[target:target + len(code)] != code):
            raise ValueError('Unknown inventory/warehouse draw dispatch')
    return bytes(data)


def patch_archive(path, prefix):
    replacements = {}
    with read_pak(path) as source:
        for short in ('inventory_dialog.xml', 'inventory_dialog_new.xml'):
            name = prefix + short
            root = binary_xml(source.read(name))
            cells = root.findall(".//Widget[@name='inv0']/Widget[@name='list0']")
            if not cells or any(cell.get('slot_num') != '279' for cell in cells):
                raise ValueError('Expected installed 279-slot inventory: ' + name)
            for cell in cells:
                cell.set('slot_num', str(inventory.SLOTS))
            replacements[name] = encode_binary_xml(root)
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as target:
            for entry in source.infolist():
                target.writestr(copy.copy(entry), replacements.get(entry.filename, source.read(entry)))
    return encode_pak(buffer.getvalue())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if output.exists() or root == output or root in output.parents:
        raise ValueError('Use a fresh staging directory outside the client')
    originals = {}

    def stage(relative, payload):
        source = root / relative
        originals[relative] = sha(source.read_bytes()) if source.exists() else None
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)

    dll = patch_dll((root / 'bin64/Game.dll').read_bytes())
    dll, graphics = prepare_incremental(root, output, dll)
    stage('bin64/Game.dll', dll)
    archives = [('Data/ui/game/game.pak', ''), ('L10N/enu/Data/data.pak', 'ui/game/')]
    for relative, prefix in archives:
        if (root / relative).exists():
            stage(relative, patch_archive(root / relative, prefix))
    if graphics:
        # Keep graphics removal/restore baselines at the same inventory capacity.
        records = ['Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json']
        states = [json.loads((output / relative).read_text()) for relative in records]
        for entry in states[0]['files']:
            relative = entry['path']
            if relative not in dict(archives):
                continue
            backup = 'Mods/Backups/Renderer/' + graphics['backupName'] + '/' + relative
            path = output / backup
            path.write_bytes(patch_archive(path, dict(archives)[relative]))
            stage('Mods/GraphicsMenu/package/' + relative, (output / relative).read_bytes())
            for state in states:
                record = next(record for record in state['files'] if record['path'] == relative)
                record.update(original=sha(path.read_bytes()), installed=sha((output / relative).read_bytes()))
        for relative, state in zip(records, states, strict=True):
            stage(relative, json.dumps(state, indent=2).encode())
    files = []
    for path in sorted(output.rglob('*')):
        if path.is_file():
            relative = path.relative_to(output).as_posix()
            source = root / relative
            files.append(dict(path=relative, original=originals.get(relative,
                sha(source.read_bytes()) if source.exists() else None), staged=sha(path.read_bytes())))
    for entry in files:
        source = root / entry['path']
        if (sha(source.read_bytes()) if source.exists() else None) != entry['original']:
            raise ValueError('Client changed during preparation: ' + entry['path'])
    manifest = dict(clientRoot=str(root), feature='inventory-capacity-288',
                    baseSlots=inventory.BASE_SLOTS, maximumSlots=inventory.SLOTS, files=files,
                    graphicsCompatibility=graphics, legacyAddon=[], retiredFiles=[])
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared 189 base / 288 maximum inventory slots; installed client untouched:', output)


if __name__ == '__main__':
    main()
