"""Stage only a rebuilt AionIconBridge.dll for install.py, retaining indexes, Core and plugins."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'native-icon-bridge'))
from build_bridge import build


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parent.parent):
        raise ValueError('Use a fresh external output directory')
    output.mkdir(parents=True)
    bridge = build(client, output, refresh_indexes=False)
    relative = 'bin64/AionIconBridge.dll'
    built = (output / relative).read_bytes()
    original = (client / relative).read_bytes()
    inventory_path = 'Mods/InstalledMods.json'
    current = (client / inventory_path).read_bytes()
    inventory = json.loads(current)
    updated = 0
    for records in inventory['features'].values():
        for entry in records:
            if entry.get('path') == relative:
                entry.update(installedSha256=sha(built), bytes=len(built))
                updated += 1
    if not updated:
        raise ValueError('The installed inventory does not list the bridge')
    staged = json.dumps(inventory, indent=2).encode()
    (output / inventory_path).parent.mkdir(parents=True, exist_ok=True)
    (output / inventory_path).write_bytes(staged)
    (output / 'bridge-inputs.json').write_text(json.dumps(bridge, indent=2), encoding='utf-8')
    manifest = dict(feature='aetherfall-core', clientRoot=str(client), inputGuards=bridge['inputGuards'],
                    files=[dict(path=relative, original=sha(original), installed=sha(built)),
                           dict(path=inventory_path, original=sha(current), installed=sha(staged))],
                    testsRun=False, gameStarted=False)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Stage ready:', output)


if __name__ == '__main__':
    main()
