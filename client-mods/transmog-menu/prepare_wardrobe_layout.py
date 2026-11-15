"""Stage the native Wardrobe controller, layout and theme plus the addon signatures; preserve every other entry.

Only WardrobeNative.lua, Wardrobe.xml and WardrobeTheme.lua can change inside the addon archive,
and only those whose source differs from the installed member are listed in the manifest.
Install with InstallWardrobeLayout.ps1 while Aion is closed.
"""
import argparse
import copy
import hashlib
import io
import json
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, encode_pak
from native_wardrobe_layout import build as layout

PAK = 'Plugin/RelicCalc/RelicCalc.pak'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def payloads():
    # The installed layout is UTF-16 with LF line ends, the same bytes prepare_native_wardrobe.py writes.
    return {'WardrobeNative.lua': (HERE / 'WardrobeNative.lua').read_bytes(),
            'Wardrobe.xml': layout().replace("encoding='utf-8'", "encoding='utf-16'").encode('utf-16'),
            'WardrobeTheme.lua': (HERE / 'WardrobeTheme.lua').read_bytes()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--revision', default='title-band-1', help='manifest revision label for this delivery')
    args = parser.parse_args()
    root, out = args.client.resolve(), args.output.resolve()
    if out == root or root in out.parents or out.exists():
        raise ValueError('Use a new directory outside the installed client')
    old = read_pak(root / PAK)
    toc = old.read('RelicCalc.toc')
    source = payloads()
    if any(name not in old.namelist() or name.encode() not in toc for name in source):
        raise ValueError('Expected installed native Wardrobe')
    payload = {name: data for name, data in source.items() if old.read(name) != data}
    if not payload:
        raise ValueError('Installed Wardrobe already matches the source')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in old.infolist():
            target.writestr(copy.copy(entry), payload.get(entry.filename, old.read(entry.filename)))
    (out / PAK).parent.mkdir(parents=True)
    (out / PAK).write_bytes(encode_pak(buffer.getvalue()))
    staged = read_pak(out / PAK)
    assert staged.testzip() is None and staged.namelist() == old.namelist()
    for name in old.namelist():
        assert staged.read(name) == payload.get(name, old.read(name)), name
    subprocess.run(['java', str(HERE / 'SignClientPackages.java'), str(root), str(out)], check=True)
    (out / 'Pub.key').unlink()
    files = [dict(path=f.relative_to(out).as_posix(), original=sha((root / f.relative_to(out)).read_bytes()),
                  staged=sha(f.read_bytes())) for f in sorted(out.rglob('*')) if f.is_file()]
    inventory = json.loads((HERE.parents[1] / 'docs/INSTALLED_MODS.json').read_text(encoding='utf-8'))
    changed = {e['path'].lower() for e in files}
    preserved = [dict(path=e['path'], sha256=sha((root / e['path']).read_bytes()))
                 for e in inventory['clientFiles'] if e['path'].lower() not in changed and (root / e['path']).exists()]
    manifest = dict(feature='native-wardrobe-layout', revision=args.revision, clientRoot=str(root),
                    files=files, preservedFiles=preserved, archiveChanges=sorted(payload))
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('OK: staged', len(files), 'files; changed in the addon archive:', ', '.join(sorted(payload)), '->', out)


if __name__ == '__main__':
    main()
