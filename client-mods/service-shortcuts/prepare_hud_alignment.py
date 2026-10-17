"""Stage only the Wardrobe-induced native HUD canvas-width correction."""
import argparse
import copy
import json
from pathlib import Path
from wardrobe import archive, binary_xml, encode_binary_xml, NATIVE_HUD_WIDTHS, sha


def align(data):
    tree = binary_xml(data)
    if tree.get('name') != 'start_dialog':
        raise ValueError('Expected start_dialog')
    journey = tree.find("Widget[@name='journey_menu_button']")
    wardrobe = tree.find("Widget[@name='wardrobe_menu_button']")
    if journey is None or wardrobe is None:
        raise ValueError('Expected installed Journey and Wardrobe')
    x, y, w, h = map(int, journey.get('frame').split(','))
    if wardrobe.get('frame') != f'{x + 40},{y},{w},{h}':
        raise ValueError('Unexpected Wardrobe placement')
    frame = list(map(int, tree.get('frame').split(',')))
    native = NATIVE_HUD_WIDTHS.get(tree.get('preset'))
    if native is None or frame[2] not in (native, max(native, x + 40 + w)):
        raise ValueError('Unexpected HUD canvas')
    if frame[2] == native:
        return data
    frame[2] = native
    tree.set('frame', ','.join(map(str, frame)))
    return encode_binary_xml(tree)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(Path(__file__).resolve().parents[2]):
        raise ValueError('Use a fresh external stage')
    output.mkdir(parents=True)
    files = []

    def stage(relative, data):
        before = (client / relative).read_bytes()
        if data == before:
            return
        destination = output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        files.append(dict(path=relative, before=sha(before), after=sha(data)))

    localized = {f'ui/game_hud_s{style}/start_dialog.xml': align for style in (1, 2)}
    for style in (1, 2):
        relative = f'Data/ui/game_hud_s{style}/game_hud_s{style}.pak'
        stage(relative, archive(client / relative, {'start_dialog.xml': align}))
    relative = 'L10N/enu/Data/data.pak'
    stage(relative, archive(client / relative, localized))
    active = next((e for e in files if e['path'] == relative), None)
    graphics = client / 'Mods/GraphicsMenu'
    state = json.loads((graphics / 'installed.json').read_text(encoding='utf-8-sig'))
    package = json.loads((graphics / 'package/manifest.json').read_text(encoding='utf-8-sig'))
    if state['files'] != package['files']:
        raise ValueError('Graphics recovery metadata differs')
    record = next(e for e in state['files'] if e['path'].casefold() == relative.casefold())
    baseline = (Path(state['backupRoot']) / relative).resolve(strict=True)
    if not baseline.is_relative_to(client / 'Mods/Backups/Renderer') or sha(baseline.read_bytes()) != record['original']:
        raise ValueError('Graphics locale baseline differs')
    if sha((client / relative).read_bytes()) != record['installed'] or sha((graphics / 'package' / relative).read_bytes()) != record['installed']:
        raise ValueError('Current graphics locale/payload differ')
    baseline_data = archive(baseline, localized)
    stage(baseline.relative_to(client).as_posix(), baseline_data)
    record.update(original=sha(baseline_data), installed=active['after'] if active else record['installed'])
    package['files'] = copy.deepcopy(state['files'])
    stage('Mods/GraphicsMenu/installed.json', json.dumps(state, indent=2).encode())
    stage('Mods/GraphicsMenu/package/manifest.json', json.dumps(package, indent=2).encode())
    if active:
        stage('Mods/GraphicsMenu/package/' + relative, (output / relative).read_bytes())
    manifest = dict(feature='wardrobe-hud-flight-alignment', clientRoot=str(client), files=files,
                    sourceHashes={str(p): sha(p.read_bytes()) for p in [Path(__file__), Path(__file__).with_name('wardrobe.py')]},
                    ordinaryTestsRun=False, startup=False, repackChanged=False,
                    scope='Restore native s1 HUD width 178 to 165; retain every child attribute and all s2 geometry',
                    installed=False)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    print('Prepared', len(files), 'files:', output)


if __name__ == '__main__':
    main()
