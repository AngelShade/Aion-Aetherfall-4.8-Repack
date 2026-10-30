"""Stage the Wardrobe Dyes tab for an installed client; preserve every other archive entry.

Changes only:
- Plugin/RelicCalc/RelicCalc.pak: Wardrobe.xml, WardrobeNative.lua, WardrobeTheme.lua (re-signed, Addon.key flow)
- Data/Items/Items.pak: client_items_etc.xml gains the Aether Palette item
- L10N/enu/data/data.pak: strings/client_strings_item.xml gains its name and description
- bin64/AionIconBridge.dll (dyed character preview) and its index for the new Items.pak
Install with install_wardrobe_dyes.py while Aion is closed.
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
sys.path.insert(0, str(HERE.parent / 'native-icon-bridge'))
from codec import read_pak, encode_pak
from native_wardrobe_layout import build as layout
from wardrobe_item import prepare_wardrobe_item

PAK = 'Plugin/RelicCalc/RelicCalc.pak'
# prepare_wardrobe_item writes these exact relative paths.
CHANGED_MEMBERS = {'Data/Items/Items.pak': {'client_items_etc.xml'},
                   'L10N/enu/data/data.pak': {'strings/client_strings_item.xml'}}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def addon(root, out):
    payload = {'Wardrobe.xml': layout().replace("encoding='utf-8'", "encoding='utf-16'").encode('utf-16'),
               'WardrobeNative.lua': (HERE / 'WardrobeNative.lua').read_bytes(),
               'WardrobeTheme.lua': (HERE / 'WardrobeTheme.lua').read_bytes()}
    old = read_pak(root / PAK)
    toc = old.read('RelicCalc.toc')
    if any(name not in old.namelist() or name.encode() not in toc for name in payload):
        raise ValueError('Expected installed native Wardrobe')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in old.infolist():
            target.writestr(copy.copy(entry), payload.get(entry.filename, old.read(entry.filename)))
    (out / PAK).parent.mkdir(parents=True, exist_ok=True)
    (out / PAK).write_bytes(encode_pak(buffer.getvalue()))
    staged = read_pak(out / PAK)
    assert staged.testzip() is None and staged.namelist() == old.namelist()
    for name in old.namelist():
        assert staged.read(name) == payload.get(name, old.read(name)), name
    return sorted(n for n in payload if payload[n] != old.read(n))


def members(path):
    with read_pak(path) as archive:
        return {name: archive.read(name) for name in archive.namelist()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, out = args.client.resolve(), args.output.resolve()
    if out == root or root in out.parents or out.exists():
        raise ValueError('Use a new directory outside the installed client')
    out.mkdir(parents=True)
    changes = {PAK: addon(root, out)}
    prepare_wardrobe_item(root, out)
    for rel, allowed in CHANGED_MEMBERS.items():
        before, after = members(root / rel), members(out / rel)
        if before.keys() != after.keys():
            raise ValueError('Archive member list changed: ' + rel)
        changes[rel] = sorted(n for n in before if before[n] != after[n])
        if not set(changes[rel]) <= allowed:
            raise ValueError('Unexpected archive changes in %s: %s' % (rel, changes[rel]))
    from build_bridge import build as build_bridge
    from prepare_index import prepare as prepare_index
    work = out.parent / (out.name + '-bridge')
    build_bridge(root, work)
    (out / 'bin64').mkdir(exist_ok=True)
    (out / 'bin64/AionIconBridge.dll').write_bytes((work / 'bin64/AionIconBridge.dll').read_bytes())
    # Item icon offsets and the archive hash must describe the staged Items.pak.
    index = prepare_index(out, out / 'bin64/AionIconBridge.index')
    if (work / 'bin64/AionUiArt.index').read_bytes() != (root / 'bin64/AionUiArt.index').read_bytes():
        raise ValueError('UI art index differs from the installed one; rebuild it separately')
    subprocess.run(['java', str(HERE / 'SignClientPackages.java'), str(root), str(out)], check=True)
    (out / 'Pub.key').unlink()
    files = [dict(path=f.relative_to(out).as_posix(), original=sha((root / f.relative_to(out)).read_bytes()),
                  staged=sha(f.read_bytes())) for f in sorted(out.rglob('*')) if f.is_file()]
    inventory = json.loads((HERE.parents[1] / 'docs/INSTALLED_MODS.json').read_text(encoding='utf-8'))
    changed = {e['path'].lower() for e in files}
    preserved = [dict(path=e['path'], sha256=sha((root / e['path']).read_bytes()))
                 for e in inventory['clientFiles'] if e['path'].lower() not in changed and (root / e['path']).exists()]
    manifest = dict(feature='wardrobe-dyes', revision='dyes-1', clientRoot=str(root), files=files,
                    preservedFiles=preserved, archiveChanges=changes, iconIndex=index)
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('OK: staged', len(files), 'files:', out)
    for rel, names in changes.items():
        print(' ', rel, 'changed members:', ', '.join(names))


if __name__ == '__main__':
    main()
