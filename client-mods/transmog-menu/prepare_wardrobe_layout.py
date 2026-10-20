"""Stage only WardrobeNative.lua and the addon signatures; preserve every other entry."""
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

PAK = 'Plugin/RelicCalc/RelicCalc.pak'
ENTRY = 'WardrobeNative.lua'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, out = args.client.resolve(), args.output.resolve()
    if out == root or root in out.parents or out.exists():
        raise ValueError('Use a new directory outside the installed client')
    old = read_pak(root / PAK)
    if ENTRY not in old.namelist() or ENTRY.encode() not in old.read('RelicCalc.toc'):
        raise ValueError('Expected installed native Wardrobe')
    payload = (HERE / ENTRY).read_bytes()
    if old.read(ENTRY) == payload:
        raise ValueError('Installed Wardrobe already matches the source')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in old.infolist():
            target.writestr(copy.copy(entry), payload if entry.filename == ENTRY else old.read(entry.filename))
    (out / PAK).parent.mkdir(parents=True)
    (out / PAK).write_bytes(encode_pak(buffer.getvalue()))
    staged = read_pak(out / PAK)
    assert staged.testzip() is None and staged.namelist() == old.namelist()
    for name in old.namelist():
        assert staged.read(name) == (payload if name == ENTRY else old.read(name)), name
    subprocess.run(['java', str(HERE / 'SignClientPackages.java'), str(root), str(out)], check=True)
    (out / 'Pub.key').unlink()
    files = [dict(path=f.relative_to(out).as_posix(), original=sha((root / f.relative_to(out)).read_bytes()),
                  staged=sha(f.read_bytes())) for f in sorted(out.rglob('*')) if f.is_file()]
    inventory = json.loads((HERE.parents[1] / 'docs/INSTALLED_MODS.json').read_text(encoding='utf-8'))
    changed = {e['path'].lower() for e in files}
    preserved = [dict(path=e['path'], sha256=sha((root / e['path']).read_bytes()))
                 for e in inventory['clientFiles'] if e['path'].lower() not in changed and (root / e['path']).exists()]
    manifest = dict(feature='native-wardrobe-layout', revision='title-band-1', clientRoot=str(root),
                    files=files, preservedFiles=preserved, archiveChanges=[ENTRY])
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('OK: staged', len(files), 'files; only', ENTRY, 'changed in the addon archive:', out)


if __name__ == '__main__':
    main()
