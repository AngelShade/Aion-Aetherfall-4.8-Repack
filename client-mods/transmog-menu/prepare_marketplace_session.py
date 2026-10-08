"""Build authenticated Marketplace navigation over the current cumulative client."""
import argparse
import hashlib
import json
import os
from pathlib import Path

import patch_game_dll as hooks
from graphics_compat import prepare_incremental

DEV_ROOT = Path(os.environ.get('AION_DEV_ROOT', 'D:/Proiecte/Project Restructure/Aion Development Workspace')).resolve()
ROUTES = ['http://127.0.0.1:8091/' + suffix for suffix in
          ['shop', 'market', 'market/wardrobe', 'journey', 'market/pass', 'market/companions']]
RANGES = [(hooks.BROWSER_HOOK_RVA, hooks.PREVIEW_DOCK_HOOK_RVA),
          (hooks.MARKET_AUTH_HOOK_RVA, hooks.MARKET_RECT_HOOK_RVA)]


def sha(path):
    if not path.is_file():
        return None
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def patch(data):
    old = [hooks.build_browser_hook_code(ROUTES), hooks.build_market_auth_code(ROUTES[1:], compact=True)]
    new = [hooks.build_browser_hook_code(ROUTES, authenticate_shop=True),
           hooks.build_market_auth_code(ROUTES, compact=True, route_table=True)]
    result = bytearray(data)
    for (start, end), previous, updated in zip(RANGES, old, new):
        if data[start:end] != previous.ljust(end-start, b'\0'):
            raise ValueError('Unknown current browser code; cumulative client must be reviewed before replacement')
        if len(updated) > end-start:
            raise ValueError('Authenticated navigation exceeds its verified cave')
        result[start:end] = updated.ljust(end-start, b'\0')
    return bytes(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--inventory', type=Path, required=True)
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if not output.is_relative_to(DEV_ROOT/'staging/output') or output.exists():
        raise ValueError('Use a fresh external staging directory')
    inventory = json.loads(args.inventory.read_text())
    if Path(inventory['clientRoot']).resolve() != root or not all(inventory['checks'].values()):
        raise ValueError('Current installed mod inventory must pass')
    for entry in inventory['clientFiles']:
        if sha(root/entry['path']) != entry['sha256']:
            raise ValueError('Client changed since inventory: '+entry['path'])
    updated = patch((root/'bin64/Game.dll').read_bytes())
    dest = output/'bin64/Game.dll'
    dest.parent.mkdir(parents=True)
    dest.write_bytes(updated)
    _, graphics = prepare_incremental(root, output, updated)
    if graphics is None:
        raise ValueError('Expected current graphics/cursor recovery records')
    files = [dict(path=f.relative_to(output).as_posix(), original=sha(root/f.relative_to(output)), staged=sha(f))
             for f in sorted(output.rglob('*')) if f.is_file()]
    changed = {e['path'].lower() for e in files}
    preserved = [e for e in inventory['clientFiles'] if e['path'].lower() not in changed]
    manifest = dict(feature='marketplace-session-v1', clientRoot=str(root), files=files,
                    preservedFiles=preserved, graphicsCompatibility=graphics, allowedDllRanges=RANGES,
                    marketplaceTokenRoute=True, compactBrowserTitles=True, companionRoutes=True,
                    status='built; offline install and actual in-game acceptance pending')
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2))
    print('OK: built Marketplace token route from maintained builder; all unrelated DLL bytes, client resources and server files preserved:', output)


if __name__ == '__main__':
    main()
