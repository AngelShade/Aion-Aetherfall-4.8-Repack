"""Prepare a signed local-client modification; never write to the client here."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import zipfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--codec-directory', type=Path, required=True)
    parser.add_argument('--client-path', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.codec_directory.resolve()))
    from fire_temple_probe import read_pak
    from patch_client_world import encode_pak

    root = args.client_path.resolve()
    output = args.output.resolve()
    if output == root or root in output.parents:
        raise ValueError('Preparation output must be outside the client')
    if output.exists():
        raise ValueError('Use a new output directory to avoid stale staged files')
    source = root / 'Plugin/RelicCalc/RelicCalc.pak'
    with read_pak(source) as archive:
        content = {name: archive.read(name) for name in archive.namelist()}
    original = content['RelicCalc.lua']
    anchor = b'\tRegisterMenu(GetAionStr("STR_RELICCALC_TITLE"), lastCommand, "v5_start_menu_relic_up");'
    insertion = b'\tRegisterMenu("Transmog", "/say .transmog", "v5_start_menu_relic_up");\r\n'
    if original.count(anchor) != 1 or b'"Transmog"' in original:
        raise ValueError('Expected unmodified RelicCalc menu registration exactly once')
    content['RelicCalc.lua'] = original.replace(anchor, insertion + anchor)
    assert content['RelicCalc.lua'].replace(insertion, b'', 1) == original
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, payload in content.items():
            info = zipfile.ZipInfo(name, (2026, 9, 29, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, payload)
    patched = output / 'Plugin/RelicCalc/RelicCalc.pak'
    patched.parent.mkdir(parents=True)
    patched.write_bytes(encode_pak(data.getvalue()))
    with read_pak(patched) as archive:
        assert archive.testzip() is None
        assert archive.namelist() == list(content)
        for name, payload in content.items():
            assert archive.read(name) == payload, name
    signer = Path(__file__).with_name('SignClientPackages.java')
    subprocess.run([str(args.java), str(signer), str(root), str(output)], check=True)
    replacements = []
    for staged in sorted(output.rglob('*')):
        if staged.is_file():
            relative = staged.relative_to(output).as_posix()
            replacements.append({'path': relative, 'original': digest(root / relative), 'staged': digest(staged)})
    previous_addon = root / 'Plugin/TransmogMenu'
    legacy = [{'path': f.relative_to(previous_addon).as_posix(), 'sha256': digest(f)}
              for f in sorted(previous_addon.rglob('*')) if f.is_file()]
    manifest = {'clientRoot': str(root), 'files': replacements, 'legacyAddon': legacy}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(f'Prepared {len(replacements)} replacements in {output}; client untouched.')
    print('Only Lua change: register Transmog immediately before Relic Appraiser.')


if __name__ == '__main__':
    main()
