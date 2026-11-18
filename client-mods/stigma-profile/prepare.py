"""Prepare native Stigma profile controls and matching graphics/cursor recovery."""
import argparse
import copy
import gzip
import hashlib
import json
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

from patch_binary import (
    BUTTON, CLICK_CAVE, GATES, LIFECYCLE_RESETS, SLOT_CAVE,
    LOCKED_VISUAL_CAVE, LOCKED_VISUAL_SITE, patch, patch_locked_visuals,
    verify_access, verify_locked_visuals,
)

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, binary_xml, encode_binary_xml
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
from prepare_client import rebuild, parts
sys.path.insert(0, str(HERE.parent / 'transmog-menu'))
from graphics_compat import prepare_incremental


def sha(data):
    return hashlib.sha256(data).hexdigest()


TEXT_KEY = 'STR_AETHERFALL_STIGMA_PROFILE_UPGRADE'
TOOLTIP_KEY = 'STR_AETHERFALL_STIGMA_PROFILE_UPGRADE_HELP'
PROFILE_FILES = ('bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak')
VISUAL_ARCHIVES = {
    'Data/ui/ui.pak': 'UI_Preload.xml',
    'L10N/enu/Data/data.pak': 'ui/ui_preload.xml',
}
INTERFACE_MANIFEST = 'Mods/AetherfallUI/manifest.json'
INTERFACE_STATE = 'Mods/AetherfallUI/state.json'
STIGMA_PRESETS = tuple('v5_stigma_stone_' + kind for kind in ('normal', 'high', 'highest'))


def locked_slot_skins(data):
    root = binary_xml(data)
    if root.tag != 'SkinLibrary':
        raise ValueError('Expected native preloaded UI skin library')
    before = copy.deepcopy(root)
    added = []
    for name in STIGMA_PRESETS:
        presets = root.findall(f".//Preset[@name='{name}']")
        skins = root.findall(f".//Skin[@name='{name}']")
        if (len(presets) != 1 or presets[0].get('type') != 'itemview' or len(skins) != 1
                or skins[0].get('src_image', '').split(',')[-2:] != ['78', '78']
                or skins[0].get('texture', '').lower() != 'textures/ui/v5_common_5'):
            raise ValueError('Expected the native Stigma border: ' + name)
        preset = presets[0]
        slot = preset.findall("SkinRef[@state='slot']")
        if len(slot) != 1 or slot[0].get('name') != name:
            raise ValueError('Unexpected original Stigma slot skin: ' + name)
        closed = preset.findall("SkinRef[@state='slot_close']")
        if closed:
            if len(closed) != 1 or closed[0].attrib != {'name': name, 'state': 'slot_close'}:
                raise ValueError('Unexpected locked Stigma slot skin: ' + name)
        else:
            ET.SubElement(preset, 'SkinRef', name=name, state='slot_close')
            added.append(name)
    if not added:
        return data
    result = encode_binary_xml(root)
    check = binary_xml(result)
    for name in added:
        preset = check.find(f".//Preset[@name='{name}']")
        preset.remove(preset.find("SkinRef[@state='slot_close']"))
    if ET.tostring(check) != ET.tostring(before):
        raise ValueError('An unrelated UI skin changed')
    return result


def stage_interface_skins(client, output, stage):
    """Keep every Interface switch payload in step with the live preload."""
    path = client / INTERFACE_MANIFEST
    if not path.is_file():
        return []
    manifest = json.loads(path.read_text(encoding='utf-8-sig'))
    archives = {key.casefold(): key for key in manifest['archives']}
    theme_layouts = (manifest.get('themes') or {}).get('layout') or {}
    new_payloads = set()
    mapped = {}

    def repair(digest):
        if digest in mapped:
            return mapped[digest]
        if len(digest) != 64 or any(c not in '0123456789abcdef' for c in digest):
            raise ValueError('Invalid Interface payload hash')
        old = gzip.decompress((client / f'Mods/AetherfallUI/payloads/{digest}.gz').read_bytes())
        if sha(old) != digest:
            raise ValueError('Interface preset payload hash differs: ' + digest)
        updated = locked_slot_skins(old)
        new_digest = sha(updated)
        rel = f'Mods/AetherfallUI/payloads/{new_digest}.gz'
        if (client / rel).is_file():
            if gzip.decompress((client / rel).read_bytes()) != updated:
                raise ValueError('Existing Interface payload differs: ' + rel)
        elif rel not in new_payloads:
            stage(rel, gzip.compress(updated, mtime=0))
            new_payloads.add(rel)
        mapped[digest] = new_digest
        return new_digest

    for rel, member in VISUAL_ARCHIVES.items():
        key = archives[rel.casefold()]
        entry = manifest['archives'][key]['entries'][member]
        hashes = [(entry, state) for state in ('off', 'on', 'layout')]
        for theme in theme_layouts.values():
            members = theme.get(key, {})
            if member in members:
                hashes.append((members, member))
        with read_pak(client / rel) as archive:
            live = sha(archive.read(member))
        with read_pak(output / rel) as archive:
            prepared = sha(archive.read(member))
        expected = set()
        for container, name in hashes:
            old = container[name]
            container[name] = repair(old)
            if old == live:
                expected.add(container[name])
        if expected != {prepared}:
            raise ValueError('Live preload does not match an Interface variant: ' + rel)
    stage(INTERFACE_MANIFEST, json.dumps(manifest, indent=2).encode())
    return sorted(new_payloads)


def strings(data):
    root = binary_xml(data)
    for number, name, body in [(990100040, TEXT_KEY, 'Upgrade'),
                               (990100041, TOOLTIP_KEY, 'Open Stigma Upgrade')]:
        matches = [e for e in root if e.findtext('name') == name or e.findtext('id') == str(number)]
        if matches:
            if len(matches) != 1 or any(matches[0].findtext(k) != v for k, v in
                                        [('id', str(number)), ('name', name), ('body', body)]):
                raise ValueError('Stigma localization entry collision: ' + name)
            continue
        row = ET.SubElement(root, 'string')
        for key, value in [('id', str(number)), ('name', name), ('body', body)]:
            ET.SubElement(row, key).text = value
    return encode_binary_xml(root)


def layout(data):
    root = binary_xml(data)
    page = root.find(".//Widget[@name='tabpage_stigma']")
    help_button = root.find(".//Widget[@name='btn_stigma_help']")
    if root.get('type') != 'dlg_player_info' or page is None or help_button is None:
        raise ValueError('Expected native profile Stigma tab and Help control')
    existing = page.find("Widget[@name='" + BUTTON + "']")
    before = copy.deepcopy(root)
    if existing is not None:
        if (existing.get('frame') != '263,321,132,24'
                or (existing.get('text'), existing.get('tooltip')) not in
                [('Upgrade', 'Open Stigma Upgrade'), (TEXT_KEY, TOOLTIP_KEY)]):
            raise ValueError('Installed Stigma control differs')
        existing.set('text', TEXT_KEY)
        existing.set('tooltip', TOOLTIP_KEY)
        before_page = before.find(".//Widget[@name='tabpage_stigma']")
        before_page.remove(before_page.find("Widget[@name='" + BUTTON + "']"))
    else:
        ET.SubElement(page, 'Widget', name=BUTTON, type='button', preset='v5_button',
                      font='ui_default', frame='263,321,132,24', v_pos_align='bottom',
                      text=TEXT_KEY, tooltip=TOOLTIP_KEY)
    replacement = encode_binary_xml(root)
    check = binary_xml(replacement)
    new_page = check.find(".//Widget[@name='tabpage_stigma']")
    new_page.remove(new_page.find("Widget[@name='" + BUTTON + "']"))
    if ET.tostring(check) != ET.tostring(before):
        raise ValueError('An unrelated profile widget changed')
    return replacement


def archive(path, localized):
    name = 'ui/game/player_info_dialog.xml' if localized else 'player_info_dialog.xml'
    transforms = {name: layout}
    if localized:
        transforms['strings/client_strings_ui.xml'] = strings
    return rewrite_archive(path, transforms)


def rewrite_archive(path, transforms):
    raw = path.read_bytes()
    with read_pak(path) as z:
        infos = {e.filename: e for e in z.infolist()}
        changes = {}
        for name, transform in transforms.items():
            original = z.read(name)
            updated = transform(original)
            if updated != original:
                changes[name] = updated
        if not changes:
            return raw
        result, _ = rebuild(raw, changes, infos)
    before, _, _, version = parts(raw)
    after, _, _, updated_version = parts(result)
    if version != updated_version or list(before) != list(after):
        raise ValueError('Profile archive structure changed')
    for member in before:
        if member not in changes and before[member][0] != after[member][0]:
            raise ValueError('Unrelated compressed archive member changed: ' + member)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--locked-visuals', action='store_true',
                        help='Keep unavailable slots visible on an existing Stigma Profile installation')
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external preparation folder')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('This delivery is scoped to the original development client')
    output.mkdir(parents=True)
    patch_current = patch_locked_visuals if args.locked_visuals else patch
    dll = patch_current((client / 'bin64/Game.dll').read_bytes())
    _, compatibility = prepare_incremental(client, output, dll)
    if not compatibility:
        raise ValueError('Expected installed native graphics/cursor recovery')
    def stage(relative, data):
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    interface_payloads = []
    if args.locked_visuals:
        if compatibility.get('ownershipSchema') != 2:
            raise ValueError('Locked slot visuals require the current independent Graphics ownership')
        verify_locked_visuals(dll)
        stage('bin64/Game.dll', dll)
        for rel, member in VISUAL_ARCHIVES.items():
            stage(rel, rewrite_archive(client / rel, {member: locked_slot_skins}))
        interface_payloads = stage_interface_skins(client, output, stage)
        from client_ownership import verify as verify_graphics
        verify_graphics(client, game=dll, archives={'L10N/enu/Data/data.pak': output / 'L10N/enu/Data/data.pak'})
    elif compatibility.get('ownershipSchema') == 2:
        verify_access(dll)
        stage('bin64/Game.dll', dll)
        for rel in ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']:
            stage(rel, archive(client / rel, rel.startswith('L10N/')))
    else:
        staged_state = output / 'Mods/GraphicsMenu/installed.json'
        state = json.loads(staged_state.read_text())
        package_path = output / 'Mods/GraphicsMenu/package/manifest.json'
        package = json.loads(package_path.read_text())
        backup = output / 'Mods/Backups/Renderer' / compatibility['backupName']
        for image in [dll, (output / 'Mods/GraphicsMenu/package/bin64/Game.dll').read_bytes(),
                      (backup / 'bin64/Game.dll').read_bytes(),
                      (backup / 'cursor-base/bin64/Game.dll').read_bytes()]:
            verify_access(image)

        def stage(relative, data):
            path = output / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)

        stage('bin64/Game.dll', dll)
        for rel in ['Data/ui/game/game.pak', 'L10N/enu/Data/data.pak']:
            active = archive(client / rel, rel.startswith('L10N/'))
            baseline = archive(backup / rel, rel.startswith('L10N/'))
            stage(rel, active)
            stage('Mods/GraphicsMenu/package/' + rel, active)
            stage(backup.relative_to(output).as_posix() + '/' + rel, baseline)
            entry = next(e for e in state['files'] if e['path'] == rel)
            entry.update(original=sha(baseline), installed=sha(active))
        package['files'] = copy.deepcopy(state['files'])
        stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
        stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    profile_files = list(PROFILE_FILES)
    if args.locked_visuals:
        profile_files.append('Data/ui/ui.pak')
    stage('Mods/StigmaProfile/mod.json', json.dumps(dict(
        Title='Stigma profile controls', Listed=True,
        Description='View locked slots and equip or remove available Stigmas from Profile. Upgrade opens Stigma charging.'
                    if args.locked_visuals else
                    'Equip and remove Stigmas from Profile. Upgrade opens the native Stigma charging window.',
        Files=profile_files), indent=2).encode())
    stage('Mods/StigmaProfile/README.md', (HERE / 'README.md').read_bytes())
    inventory = json.loads((client / 'Mods/InstalledMods.json').read_text(encoding='utf-8-sig'))
    active_records = {}
    for rel in profile_files:
        staged = output / rel
        path = staged if staged.is_file() else client / rel
        active_records[rel] = dict(path=rel, installedSha256=sha(path.read_bytes()), bytes=path.stat().st_size)
    changed_active = {rel.casefold(): record for rel, record in active_records.items() if (output / rel).is_file()}
    if (output / INTERFACE_MANIFEST).is_file():
        data = (output / INTERFACE_MANIFEST).read_bytes()
        changed_active[INTERFACE_MANIFEST.casefold()] = dict(installedSha256=sha(data), bytes=len(data))
    for entries in inventory['features'].values():
        for entry in entries:
            match = changed_active.get(entry.get('path', '').casefold())
            if match and entry.get('installedSha256'):
                entry.update(installedSha256=match['installedSha256'], bytes=match['bytes'])
    inventory['features']['StigmaProfile'] = list(active_records.values())
    stage('Mods/InstalledMods.json', json.dumps(inventory, indent=2).encode())
    records = []
    for path in sorted(output.rglob('*')):
        if not path.is_file():
            continue
        rel = path.relative_to(output).as_posix()
        old = client / rel
        records.append(dict(path=rel, original=sha(old.read_bytes()) if old.exists() else None,
                            staged=sha(path.read_bytes())))
    manifest = dict(feature='stigma-profile', clientRoot=str(client), files=records,
                    graphicsCompatibility=compatibility, serverChanged=False, repackChanged=False,
                    nativeGates=[dict(rva=hex(r), purpose=p) for r, _, p in GATES],
                    lifecycleResets=[dict(rva=hex(r), purpose=p) for r, p in LIFECYCLE_RESETS],
                    bridges=[hex(CLICK_CAVE), hex(SLOT_CAVE)],
                    sourceHashes={str(p): sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file()},
                    preservedFiles=[dict(path=rel, sha256=sha((client / rel).read_bytes())) for rel in
                                    ['Pub.key', 'Addon.key', 'bin64/crysystem.dll', 'bin32/bin32.pak.sig',
                                     'Data/func_pet/func_pet.pak.sig', 'Plugin/RelicCalc/RelicCalc.pak.sig',
                                     'bin64/AionRememberLogin.dll', 'bin64/AionIconBridge.dll', 'bin64/AionLootRoll.dll']],
                    gameplayAcceptance='Pending normal launch and player equip/remove/upgrade checks')
    if args.locked_visuals:
        manifest.update(
            mode='locked-visuals',
            lockedVisuals=dict(site=hex(LOCKED_VISUAL_SITE), bridge=hex(LOCKED_VISUAL_CAVE),
                               presetNames=STIGMA_PRESETS, archives=VISUAL_ARCHIVES),
            gameplayAcceptance='Pending first Profile opening on a new character and normal Stigma equip/remove/Upgrade',
        )
        retained = ['Data/ui/game/game.pak', 'Textures/ui/ui.pak', 'bin64/AionUiArt.index', 'bin64/AetherfallCore.dll']
        if (output / INTERFACE_MANIFEST).is_file():
            manifest['lockedVisuals']['interfacePayloads'] = interface_payloads
            retained.append(INTERFACE_STATE)
        for rel in retained:
            manifest['preservedFiles'].append(dict(path=rel, sha256=sha((client / rel).read_bytes())))
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(records), 'affected files; original client remains unchanged:', output)


if __name__ == '__main__':
    main()
