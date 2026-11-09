"""Stage the Broker Swap module into the installed client's RelicCalc plugin (no live change).

Adds BrokerSwap.lua and BrokerSwap.xml to Plugin/RelicCalc/RelicCalc.pak and lists them
last in RelicCalc.toc, or replaces exactly those two members on a later revision. Every
other archive member stays byte-identical. The three addon archives are signed together
with a fresh key, as the other plugin mods do. Requires native AetherfallCore 1.3.8 and the
icon bridge with AionBrokerState; the module stays inert without them.
"""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from codec import read_pak, encode_pak  # noqa: E402

MEMBERS = ('BrokerSwap.lua', 'BrokerSwap.xml')
PAK = 'Plugin/RelicCalc/RelicCalc.pak'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    root, out = a.client.resolve(strict=True), a.output.resolve()
    if out.exists() or out == root or root in out.parents or HERE.parents[1] in out.parents:
        raise SystemExit('Use a fresh output folder outside the client and the repository')
    for dll, export in (('bin64/AetherfallCore.dll', b'brokerstate'), ('bin64/AionIconBridge.dll', b'AionBrokerState')):
        if export not in (root / dll).read_bytes():
            raise SystemExit(f'Install the native part first: {dll} lacks {export.decode()}')
    z = read_pak(root / PAK)
    toc = z.read('RelicCalc.toc').decode('utf-8-sig').replace('\r', '').split('\n')
    if 'PrivateMenus.lua' not in toc or 'AetherfallCore.lua' not in toc:
        raise SystemExit('Unexpected installed plugin: PrivateMenus.lua and AetherfallCore.lua must be listed')
    toc = [line for line in toc if line not in MEMBERS]
    while toc and not toc[-1]:
        toc.pop()
    toc += list(MEMBERS)
    changes = {'RelicCalc.toc': ('\r\n'.join(toc) + '\r\n').encode('utf-8')}
    for name in MEMBERS:
        changes[name] = (HERE / name).read_bytes()
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in z.infolist():
            target.writestr(copy.copy(entry), changes.pop(entry.filename, None) or z.read(entry.filename))
        for name, data in changes.items():
            target.writestr(name, data)
    (out / PAK).parent.mkdir(parents=True)
    (out / PAK).write_bytes(encode_pak(buffer.getvalue()))
    subprocess.run(['java', str(HERE.parent / 'transmog-menu/SignClientPackages.java'), str(root), str(out)], check=True)
    if (out / 'Pub.key').exists():
        (out / 'Pub.key').unlink()  # the stock model key stays installed
    staged = read_pak(out / PAK)
    old, new = set(z.namelist()), set(staged.namelist())
    changed = sorted(n for n in old & new if z.read(n) != staged.read(n))
    if not new >= old or not (new - old) <= set(MEMBERS) or not set(changed) <= {'RelicCalc.toc', *MEMBERS}:
        raise SystemExit('Staged archive changed members other than the module and its toc lines')
    files = []
    for f in sorted(out.rglob('*')):
        if f.is_file():
            rel = f.relative_to(out).as_posix()
            live = root / rel
            files.append(dict(path=rel, original=sha(live.read_bytes()) if live.exists() else None, installed=sha(f.read_bytes())))
    manifest = dict(feature='broker-swap', clientRoot=str(root), files=files,
                    archiveChanges=dict(added=sorted(new - old), changed=changed))
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('OK: staged', len(files), 'files; archive members added', sorted(new - old), 'changed', changed, '->', out)


if __name__ == '__main__':
    main()
