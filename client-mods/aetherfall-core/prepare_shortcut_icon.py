"""Build the Interface shortcut artwork for the installed native HUD."""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from PIL import Image

MODS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODS / 'dxvk'))
from client_ownership import verify as verify_graphics


def load_module(name, path):
    sys.path.insert(0, str(path.parent))
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


journal = load_module('red_journal', MODS / 'quest-red-journal/prepare.py')
shortcuts = load_module('service_shortcuts', MODS / 'service-shortcuts/prepare.py')
TEXTURES = 'Textures/ui/ui.pak'
ATLAS = 'aetherfall_interface_shortcut.dds'
PRELOADS = {
    'Data/ui/ui.pak': 'UI_Preload.xml',
    'L10N/enu/data/data.pak': 'ui/ui_preload.xml',
}
STATE = 'Mods/AetherfallUI/state.json'
INTERFACE = 'Mods/AetherfallUI/manifest.json'
INVENTORY = 'Mods/InstalledMods.json'
BUTTON = 'aetherfall_menu_button'
STATES = ('up', 'over', 'down')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encode(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def retarget(payload, artwork):
    root = shortcuts.binary_xml(payload)
    for prefix in ('', 'af_shortcut_'):
        [preset] = root.findall(f".//Preset[@name='{prefix}{BUTTON}']")
        references = {ref.get('sub_state'): ref.get('name') for ref in preset.findall('SkinRef')}
        if references != {state: prefix + 'aetherfall_menu_' + state for state in STATES}:
            raise ValueError('Unexpected Interface shortcut state mapping')
        for state, name in references.items():
            [skin] = root.findall(f".//Skin[@name='{name}']")
            skin.set('texture', artwork[state]['texture'])
            skin.set('src_image', artwork[state]['src_image'])
            if skin.get('type') == 'tiled':
                skin.set('src_middle_middle', artwork[state]['src_image'])
    result = shortcuts.encode_binary_xml(root)
    if ET.tostring(shortcuts.binary_xml(result)) != ET.tostring(root):
        raise ValueError('Native preload XML did not round-trip')
    return result


def verify_archive(path, original, data, replacements, additions):
    before, central, _, version = journal.parts(original)
    after, new_central, _, new_version = journal.parts(data)
    assert list(after) == [*before, *additions] and version == new_version
    for name in before.keys() - replacements.keys():
        assert before[name][0] == after[name][0], name
        assert central[name][:42] + central[name][46:] == new_central[name][:42] + new_central[name][46:], name
    with journal.read_pak(path) as archive:
        for name, value in {**replacements, **additions}.items():
            assert archive.read(name) == value, name
    return len(before) - len(replacements)


def verify_routes(client, output, artwork, active):
    for number in (1, 2):
        base = f'Data/ui/game_hud_s{number}/game_hud_s{number}.pak'
        localized = f'ui/game_hud_s{number}/start_dialog.xml'
        for relative, name in ((base, 'start_dialog.xml'), ('L10N/enu/data/data.pak', localized)):
            path = output / relative if (output / relative).is_file() else client / relative
            with journal.read_pak(path) as archive:
                layout = shortcuts.binary_xml(archive.read(name))
            widget = layout.find(f"Widget[@name='{BUTTON}']")
            if active == 'layout':
                assert widget is not None and widget.get('preset') == 'af_shortcut_' + BUTTON
                assert widget.get('tooltip') == 'STR_TUTORIAL_TITLE_INTERFACE'
    with Image.open(output / 'icon-states.png') as atlas:
        states = []
        for state in STATES:
            x, y, w, h = map(int, artwork[state]['src_image'].split(','))
            states.append(atlas.crop((x, y, x + w, y + h)))
        assert all(icon.getchannel('A').getextrema() == (0, 255) for icon in states)
        assert len({icon.getchannel('A').tobytes() for icon in states}) == 1
        assert len({icon.tobytes() for icon in states}) == len(STATES)


def prepare(client, output):
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(MODS.parent):
        raise ValueError('Use fresh staging outside the original client and source')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('This builder targets the original client')
    if verify_graphics(client)['ownershipSchema'] != 2:
        raise ValueError('The original client requires independent Graphics ownership')
    state = (client / STATE).read_bytes()
    active = json.loads(state)['Applied']
    manifest_bytes = (client / INTERFACE).read_bytes()
    manifest = json.loads(manifest_bytes)
    output.mkdir(parents=True)
    files = []

    def stage(relative, original, data):
        if original != data:
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            files.append(dict(path=relative, original=sha(original) if original is not None else None,
                              installed=sha(data), bytes=len(data)))

    def store(data):
        digest = sha(data)
        relative = f'Mods/AetherfallUI/payloads/{digest}.gz'
        existing = client / relative
        if existing.is_file():
            assert gzip.decompress(existing.read_bytes()) == data
        elif not (output / relative).exists():
            stage(relative, None, gzip.compress(data, mtime=0))
        return digest

    def stage_archive(relative, original, replacements, additions):
        data = journal.extend_archive(original, replacements, additions)
        stage(relative, original, data)
        return verify_archive(output / relative, original, data, replacements, additions)

    icon = ('aetherfall_menu', 'Interface', '/afui', 0, 0, 'interface-outlined.png')
    definitions, dds = shortcuts.artwork(output, [icon], ATLAS, alpha_threshold=8)
    artwork = {state: definitions[i].attrib for i, state in enumerate(STATES)}
    preserved = {}
    for relative, member in PRELOADS.items():
        original = (client / relative).read_bytes()
        entry = manifest['archives'][relative]['entries'][member]
        layout = gzip.decompress((client / f"Mods/AetherfallUI/payloads/{entry['layout']}.gz").read_bytes())
        assert sha(layout) == entry['layout']
        with journal.read_pak(client / relative) as archive:
            assert sha(archive.read(member)) == entry[active], 'Current preload differs from the Interface manifest'
        updated = retarget(layout, artwork)
        entry['layout'] = store(updated)
        if active == 'layout':
            preserved[relative] = stage_archive(relative, original, {member: updated}, {})

    original = (client / TEXTURES).read_bytes()
    with journal.read_pak(client / TEXTURES) as archive:
        assert ATLAS not in archive.namelist(), 'Interface shortcut atlas already present'
    preserved[TEXTURES] = stage_archive(TEXTURES, original, {}, {ATLAS: dds})
    digest = store(dds)
    manifest['archives'][TEXTURES]['entries'][ATLAS] = dict(off=digest, on=digest, layout=digest)
    stage(INTERFACE, manifest_bytes, encode(manifest))

    original = (client / INVENTORY).read_bytes()
    inventory = json.loads(original)
    changed = {row['path'].casefold(): row for row in files if not row['path'].endswith('.gz')}
    for group in inventory['features'].values():
        for row in group:
            update = changed.get(row['path'].casefold())
            if update:
                row.update(installedSha256=update['installed'], bytes=update['bytes'])
    inventory['features']['AetherfallInterfaceShortcut'] = [
        dict(path=row['path'], installedSha256=row['installed'], bytes=row['bytes']) for row in changed.values()
    ]
    stage(INVENTORY, original, encode(inventory))
    verify_routes(client, output, artwork, active)
    verify_graphics(client, archives={p: output / p for p in PRELOADS if (output / p).is_file()})
    (output / 'manifest.json').write_bytes(encode(dict(
        feature='aetherfall-core', change='Aetherfall Interface shortcut icon', clientRoot=str(client), files=files,
        inputGuards={STATE: sha(state)}, preservedCompressedMembers=preserved)))
    print(f'Built three Interface icon states. Verified both native HUD layouts and {sum(preserved.values())} preserved compressed members.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.client.resolve(strict=True), args.output.resolve())
