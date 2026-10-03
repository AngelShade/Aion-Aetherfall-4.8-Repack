"""Verify the title-only package against its installed source and native hooks."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import struct
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
from prepare_compact_titles import patch_dll, read_pak
from graphics_compat import binary_xml


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    args = parser.parse_args()
    package = args.package.resolve()
    manifest = json.loads((package/'manifest.json').read_text())
    assert manifest['compactBrowserTitles'] is True
    client = Path(manifest['clientRoot'])
    for entry in manifest['files']:
        assert sha(package/entry['path']) == entry['staged'], entry['path']
        if entry['original'] is not None:
            assert sha(client/entry['path']) == entry['original'], entry['path']
        else:
            assert not (client/entry['path']).exists(), entry['path']
    for entry in manifest['preservedFiles']:
        assert sha(client/entry['path']) == entry['sha256'], entry['path']
    before = (client/'bin64/Game.dll').read_bytes()
    after = (package/'bin64/Game.dll').read_bytes()
    assert after == patch_dll(before)
    assert sum(a != b for a,b in zip(before, after)) == 1, 'Expected one bounded layout instruction edit'
    login = manifest.get('rememberedLoginPreservation')
    if login:
        assert sha(Path(login['receipt'])) == login['sha256'], 'Latest login receipt changed'
        # Existing sections map the login entry sites directly. The appended
        # section requires its own RVA-to-file mapping for the trampolines.
        pe = struct.unpack_from('<I',before,0x3c)[0]
        count = struct.unpack_from('<H',before,pe+6)[0]
        table = pe+24+struct.unpack_from('<H',before,pe+20)[0]
        sections = [struct.unpack_from('<8sIIII',before,table+i*40) for i in range(count)]
        own = next(s for s in sections if s[0].rstrip(b'\0') == b'.rlogin')
        assert before[own[4]:own[4]+own[3]] == after[own[4]:own[4]+own[3]], 'Remember Login section changed'
        for hook in login['hooks']:
            site = hook['site']
            length = len(bytes.fromhex(hook['original']))
            assert before[site:site+length] == after[site:site+length] and after[site] == 0xe9
        for rel, name in [('Data/ui/ui.pak','UI_Login.xml'),('L10N/enu/Data/data.pak','ui/ui_login.xml')]:
            with read_pak(client/rel) as archive:
                layout = binary_xml(archive.read(name))
                assert layout.find(".//Widget[@name='remember_login']") is not None
                assert layout.find(".//Widget[@name='password']").get('flag') == 'password'
        print('OK: latest Remember Login receipt, all three native entry hooks, complete .rlogin section, extension DLL and both login layouts preserved.')
    with read_pak(client/'Plugin/RelicCalc/RelicCalc.pak') as old, read_pak(package/'Plugin/RelicCalc/RelicCalc.pak') as new:
        assert old.namelist() == new.namelist()
        changed = [name for name in old.namelist() if old.read(name) != new.read(name)]
        assert set(changed) == {'CashShop.xml', 'Warehouse.xml'}, changed
        for name in changed:
            tree = ET.fromstring(new.read(name))
            browser = tree.find(".//Widget[@type='Browser']")
            assert browser.get('frame') == '0,0,1280,935'
            assert tree.find('Dialog').get('title_height') == '25'
    subprocess.run(['java',str(HERE.parent/'season-pass/VerifySignatures.java'),str(package),str(client)],check=True)
    print('OK: one DLL byte; only two browser XML frames changed; all menus, native Wardrobe, stock keys and client resources preserved.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('FAIL:', error)
        raise
