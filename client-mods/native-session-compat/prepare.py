"""Build the bounded session repair and composed graphics/cursor recovery cohort."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'transmog-menu'))
from graphics_compat import prepare_incremental, compatibility_paths
from patch_game_dll import patch_development_session


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, out = args.client.resolve(), args.output.resolve()
    dev = Path(os.environ['AION_DEV_ROOT']).resolve()
    if out.exists() or not out.is_relative_to(dev / 'staging/output'):
        raise ValueError('Use a fresh external staging output')
    inventory = json.loads((Path(__file__).resolve().parents[2] / 'docs/INSTALLED_MODS.json').read_text(encoding='utf-8-sig'))
    if Path(inventory['clientRoot']).resolve() != root:
        raise ValueError('Expected the inventoried original client')
    source = (root / 'bin64/Game.dll').read_bytes()
    current = next(e for e in inventory['clientFiles'] if e['path'] == 'bin64/Game.dll')
    if hashlib.sha256(source).hexdigest() != current['sha256']:
        raise ValueError('Installed Game.dll differs from the current inventory')
    updated = patch_development_session(source)
    changed = [i for i, (a, b) in enumerate(zip(source, updated)) if a != b]
    if len(source) != len(updated) or changed != [0x2c30a1]:
        raise ValueError('Expected exactly the verified one-byte repair')
    # Refresh only the native mod/locale/signing cohort relevant to this edit.
    names = ['bin64/AetherfallCore.dll', 'bin64/AionRememberLogin.dll',
             'bin64/AionGraphicsMenu.dll', 'bin64/AionMarketShortcut.dll',
             'bin64/AionIconBridge.dll', 'bin64/AionSpeechBubbles.dll',
             'bin64/version.dll', 'bin64/Awesomium.dll', 'bin64/crysystem.dll',
             'bin64/XRenderD3D9.dll', 'bin64/AionLootRoll.dll',
             'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak',
             'Data/func_pet/func_pet.pak', 'Data/func_pet/func_pet.pak.sig',
             'bin32/bin32.pak', 'bin32/bin32.pak.sig', 'Addon.key', 'Pub.key',
             'Data/CutScene/CutScene.pak', 'bin64/mods.ini']
    preserved = [{'path': n, 'sha256': sha(root / n)} for n in names if (root / n).is_file()]
    out.mkdir(parents=True)
    updated, graphics = prepare_incremental(root, out, updated)
    if not graphics:
        raise ValueError('Expected the original graphics/cursor installation')
    target = out / 'bin64/Game.dll'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(updated)
    allowed = compatibility_paths(graphics) | {'bin64/Game.dll'}
    actual = {p.relative_to(out).as_posix() for p in out.rglob('*') if p.is_file()}
    if actual != allowed:
        raise ValueError('Unexpected native session payload scope')
    files = [{'path': n, 'original': sha(root / n), 'staged': sha(out / n)} for n in sorted(actual)]
    manifest = dict(feature='native-session-compat-v1', clientRoot=str(root),
                    graphicsCompatibility=graphics, files=files, preservedFiles=preserved,
                    changedOffset=0x2c30a1, originalByte='74', repairedByte='eb',
                    status='Prepared; owner cinematic and normal-play acceptance pending')
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    print('OK: one-byte native repair built; graphics/cursor recovery composed;', len(preserved), 'preserved resources')


if __name__ == '__main__':
    main()
