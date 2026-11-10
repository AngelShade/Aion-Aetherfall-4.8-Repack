"""Capture generator products for the pre-refactor reference (read-only client)."""
import gzip
import hashlib
import json
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent.parent
FIXTURES = HERE / 'fixtures'
sys.path.insert(0, str(SOURCE))
sys.path.insert(0, str(SOURCE.parent / 'expanded-warehouse'))
from codec import read_pak


def main():
    client = Path(sys.argv[1])
    scope = {'__file__': str(SOURCE / 'prepare.py'), '__name__': 'frozen_prepare'}
    exec(compile(gzip.decompress((FIXTURES / 'prepare.py.gz').read_bytes()), scope['__file__'], 'exec'), scope)
    plugin = read_pak(client / 'Plugin/RelicCalc/RelicCalc.pak')
    resources = {
        # Installed generator output is authoritative when sibling generator
        # sources are absent from this checkout; never invent native geometry.
        'AetherfallLayout.lua': plugin.read('AetherfallLayout.lua'),
        'AetherfallLazy.lua': scope['lazy_lua'](),
        'AetherfallHUD.xml': scope['hud_xml'](),
        'AetherfallRegistry.lua': plugin.read('AetherfallRegistry.lua'),
    }
    manifest = json.loads((FIXTURES / 'source-sha256.json').read_text())
    for name, data in resources.items():
        (FIXTURES / (name + '.gz')).write_bytes(gzip.compress(data, mtime=0))
        manifest[name] = hashlib.sha256(data).hexdigest()
    (FIXTURES / 'source-sha256.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Captured', ', '.join(resources))


if __name__ == '__main__':
    main()
