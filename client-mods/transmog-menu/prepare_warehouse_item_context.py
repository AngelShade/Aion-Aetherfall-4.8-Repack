"""Stage a bounded native Warehouse repair over the current client."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys

from detached_inventory import patch_warehouse_item_context, CONTEXT_CAVE, CONTEXT_REGION_SIZE, CONTEXT_LOAD
from graphics_compat import prepare_incremental, compatibility_paths
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'expanded-warehouse'))
from warehouse_patch import patch_collapse_dll, patch_capacity_dll, CAPACITY_SITE, CAPACITY_CAVE, CAPACITY_ORIGINAL
from codec import read_pak, binary_xml
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'bundle-opening'))
import warehouse_controls as controls


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--repair', choices=['item-context', 'collapse', 'capacity'], default='item-context')
    args = parser.parse_args()
    root, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(root) or output.is_relative_to(Path(__file__).resolve().parents[2]):
        raise ValueError('Use a fresh external staging directory')
    if (root.parent / 'REPACK.json').exists():
        raise ValueError('Original client only')
    source = root / 'bin64/Game.dll'
    prior = source.read_bytes()
    if args.repair == 'collapse':
        if patch_warehouse_item_context(prior) != prior:
            raise ValueError('Install the current Warehouse item-context repair first')
        for rel, prefix in [('Data/ui/game/game.pak', ''), ('L10N/enu/Data/data.pak', 'ui/game/')]:
            with read_pak(root / rel) as archive:
                controls.validate_layout(binary_xml(archive.read(prefix + 'warehouse_dialog.xml')))
        updated = patch_collapse_dll(prior)
        ranges = [(controls.START, controls.END), (controls.INIT_SITE, controls.INIT_SITE+6),
                  (controls.SHOW_SITE, controls.SHOW_SITE+5), (0x948150, 0x94815a)] + [(s,s+33) for s in controls.MAP_SITES]
        feature, inventory_feature = 'warehouse-controls-v1', 'WarehouseControls'
        acceptance = 'Pending owner: both collapsible sections, compact window, expanded-section deposits and last-used reopening; single-click Kinah in Inventory/Warehouse'
    elif args.repair == 'capacity':
        updated = patch_capacity_dll(prior)
        ranges = [(CAPACITY_SITE, CAPACITY_SITE+len(CAPACITY_ORIGINAL)), (CAPACITY_CAVE, CAPACITY_CAVE+64)]
        feature, inventory_feature = 'warehouse-capacity-v1', 'WarehouseCapacity'
        acceptance = 'Pending owner: right-click, drag, whole and partial stacks through slots 360/540, reopen/relog and full rejection'
    else:
        updated = patch_warehouse_item_context(prior)
        ranges = [(CONTEXT_LOAD, CONTEXT_LOAD+6), (CONTEXT_CAVE, CONTEXT_CAVE+CONTEXT_REGION_SIZE)]
        feature, inventory_feature = 'warehouse-item-context-v1', 'WarehouseItemContext'
        acceptance = 'Pending owner: NPC Warehouse right-click, stack count, tabs, reopen, normal Inventory use'
    if updated == prior:
        raise ValueError('Requested Warehouse repair is already installed')
    if len(prior) != len(updated) or any(a != b and not any(s <= i < e for s, e in ranges)
                                       for i, (a, b) in enumerate(zip(prior, updated))):
        raise ValueError('Unexpected native edit scope')
    target = output / 'bin64/Game.dll'
    target.parent.mkdir(parents=True)
    target.write_bytes(updated)
    _, graphics = prepare_incremental(root, output, updated)
    if not graphics:
        raise ValueError('Expected the current graphics/cursor recovery cohort')
    expected = compatibility_paths(graphics) | {'bin64/Game.dll'}
    if {p.relative_to(output).as_posix() for p in output.rglob('*') if p.is_file()} != expected:
        raise ValueError('Unexpected graphics compatibility outputs')
    if args.repair == 'collapse':
        skin=json.loads((root/'Mods/AetherfallUI/manifest.json').read_text(encoding='utf-8-sig'))
        names={'warehouse_dialog.xml','inventory_dialog.xml','inventory_dialog_new.xml'}
        if any(n.rsplit('/',1)[-1] in names for row in skin['archives'].values() for n in row['entries']):
            raise ValueError('Affected UI is now skin-owned; review its switch payloads')
        if graphics.get('ownershipSchema') == 2:
            for rel in ['Data/ui/game/game.pak','L10N/enu/Data/data.pak']:
                active=controls.patch_archive(root/rel,rel.startswith('L10N/'))
                target_ui=output/rel;target_ui.parent.mkdir(parents=True,exist_ok=True);target_ui.write_bytes(active)
        else:
            state_path=output/'Mods/GraphicsMenu/installed.json'
            package_path=output/'Mods/GraphicsMenu/package/manifest.json'
            state=json.loads(state_path.read_text())
            package_state=json.loads(package_path.read_text())
            baseline=output/'Mods/Backups/Renderer'/graphics['backupName']
            for rel in ['Data/ui/game/game.pak','L10N/enu/Data/data.pak']:
                active=controls.patch_archive(root/rel,rel.startswith('L10N/'))
                restored=controls.patch_archive(baseline/rel,rel.startswith('L10N/'))
                target_ui=output/rel;target_ui.parent.mkdir(parents=True,exist_ok=True);target_ui.write_bytes(active)
                (output/'Mods/GraphicsMenu/package'/rel).write_bytes(active)
                (baseline/rel).write_bytes(restored)
                record=next(r for r in state['files'] if r['path']==rel)
                record.update(installed=hashlib.sha256(active).hexdigest(),original=hashlib.sha256(restored).hexdigest())
            package_state['files']=state['files']
            state_path.write_text(json.dumps(state,indent=2))
            package_path.write_text(json.dumps(package_state,indent=2))
        expected |= {'Data/ui/game/game.pak','L10N/enu/Data/data.pak'}
        catalogue=controls.stage_catalogue(root,output)
        (output/'Pub.key').unlink()  # identical model key verified; never replace it
        expected |= {'Plugin/RelicCalc/RelicCalc.pak','Plugin/RelicCalc/RelicCalc.pak.sig',
                     'Addon.key','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig'}

    inventory = json.loads((root / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    records_updated = 0
    refreshed_records = []
    affected = {p.casefold() for p in expected}
    for records in inventory['features'].values():
        for record in records:
            relative = record.get('path', '')
            if relative.casefold() in affected:
                staged = output / relative
                if not staged.is_file():
                    raise ValueError('Missing affected inventory output: ' + relative)
                # This inventory is a dated snapshot. prepare_incremental has
                # already verified the owning active/package/restore records.
                actual = sha(root / relative)
                if record.get('installedSha256', '').lower() != actual:
                    refreshed_records.append(dict(path=relative, recorded=record.get('installedSha256'), actual=actual))
                record.update(installedSha256=sha(staged), bytes=staged.stat().st_size)
                records_updated += 1
    inventory['features'][inventory_feature] = [dict(path='bin64/Game.dll',
        installedSha256=sha(target), bytes=target.stat().st_size)]
    inventory[inventory_feature + 'UpdatedAt'] = datetime.now(timezone.utc).isoformat()
    (output / 'Mods/InstalledMods.json').write_text(json.dumps(inventory, indent=2), encoding='utf-8')
    files = [dict(path=p.relative_to(output).as_posix(), original=sha(root / p.relative_to(output)), staged=sha(p))
             for p in sorted(output.rglob('*')) if p.is_file()]
    preserved = ['bin64/AetherfallCore.dll', 'bin64/CrySystem.dll', 'bin64/AionIconBridge.dll',
        'bin64/AionIconBridge.index', 'bin64/AionLootRoll.dll', 'bin64/AionGraphicsMenu.dll',
        'Plugin/RelicCalc/RelicCalc.pak', 'Plugin/RelicCalc/RelicCalc.pak.sig', 'Addon.key', 'Pub.key',
        'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig',
        'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak',
        'Mods/AetherfallUI/manifest.json', 'Mods/AetherfallUI/installed.json']
    manifest = dict(feature=feature, clientRoot=str(root), files=files,
        preservedFiles=[dict(path=n, sha256=sha(root/n)) for n in preserved if (root/n).is_file() and n not in expected],
        graphicsCompatibility=graphics, allowedDllRanges=ranges, affectedInventoryRecords=records_updated,
        refreshedDatedInventoryRecords=refreshed_records,
        catalogueRowsRefreshed=catalogue if args.repair=='collapse' else {},
        sourceHashes={str(p): sha(p) for p in [Path(__file__), Path(__file__).with_name('detached_inventory.py'),
                                             Path(__file__).with_name('graphics_compat.py'),
                                             Path(__file__).parent.parent / 'expanded-warehouse/warehouse_patch.py',
                                             Path(controls.__file__),
                                             Path(__file__).parent.parent / 'aetherfall-core/ui_registry.py']},
        gameplayAcceptance=acceptance)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(files), 'files;', records_updated,
          'existing inventory records refreshed; native transfer/count handlers retained; client untouched:', output)


if __name__ == '__main__':
    main()
