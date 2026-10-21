"""Give the installed Account window its own movable, resizable dialog (over the installed prepare.py package).

Only Plugin/RelicCalc/RelicCalc.pak changes (plus its regenerated signatures): a new Account.xml plugin
dialog (one of the 20 shared plugin dialog slots) with a browser that follows the dialog size, its
.toc entry, and PrivateAccount_Open/OnLoad in PrivateMenus.lua. Every other member stays byte-identical.
"""
import argparse
import hashlib
import io
import json
import re
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
sys.path.insert(0, str(HERE.parent / 'bundle-opening'))
from codec import read_pak  # noqa: E402
from prepare_client import parts, encrypted_zip  # noqa: E402
from prepare import read_pak_bytes, ACCOUNT_URL, RELIC  # noqa: E402

WIDTH, HEIGHT, TITLE = 1180, 780, 25
DIALOG = ('<?xml version="1.0" encoding="UTF-16"?>\r\n<Screen widget_cnt="2">\r\n'
          f'  <Dialog name="PrivateAccount" frame="0,0,{WIDTH},{HEIGHT}" text="Aetherfall Account" close_offset="4,1" close_preset="v5_close" '
          'flag="close;title;not_visible;movable;resizable" preset="v5_dialog2" shadow_offset="8,8" '
          f'title_height="{TITLE}" title_offset="0,5">\r\n'
          '    <Scripts><OnLoad>PrivateAccount_OnLoad();</OnLoad></Scripts>\r\n'
          f'    <Widget type="Browser" name="PrivateAccountBrowser" frame="0,0,{WIDTH},{HEIGHT - TITLE}" '
          'h_pos_align="proportion" v_pos_align="proportion"/>\r\n'
          '  </Dialog>\r\n</Screen>\r\n')
OLD_OPEN = '''function PrivateAccount_Open()
    -- Reuse the verified, viewport-sized native browser dialog, like the Season Pass.
    PrivateWarehouse:Show();
    PrivateWarehouse:SetText("Aetherfall Account");
    PrivateWarehouse:SetRect(0, 0, 1280, 960);
    PrivateWarehouseBrowser:LoadUrlWithWebAuth(PRIVATE_ACCOUNT_URL);
end
'''
NEW_OPEN = f'''function PrivateAccount_OnLoad()
    PrivateAccountBrowser:CreateWebView();
    PrivateAccount:Hide();
end

function PrivateAccount_Open()
    -- A normal movable, resizable window. Centre it once per session; later opens keep the player's spot and size.
    PrivateAccount:Show();
    if not PRIVATE_ACCOUNT_PLACED then
        PRIVATE_ACCOUNT_PLACED = true;
        local ok, _, sw, sh = pcall(function() if AFCore and AFCore.Scale then return AFCore.Scale() end end);
        local s = (GetUIScale and GetUIScale()) or 1;
        if ok and sw and sh and sw > 0 and sh > 0 and s > 0 then
            PrivateAccount:SetRect(math.max(0, math.floor((sw / s - {WIDTH}) / 2)), math.max(0, math.floor((sh / s - {HEIGHT}) / 2)), {WIDTH}, {HEIGHT});
        end
    end
    PrivateAccountBrowser:LoadUrlWithWebAuth(PRIVATE_ACCOUNT_URL);
end
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def menus(data):
    bom = data.startswith(b'\xef\xbb\xbf')
    text = data.decode('utf-8-sig')
    newline = '\r\n' if '\r\n' in text else '\n'
    text = text.replace('\r\n', '\n')
    if ('PRIVATE_ACCOUNT_URL = ' + json.dumps(ACCOUNT_URL) + ';') not in text:
        raise ValueError('Install the Account window package (prepare.py) first')
    if 'function PrivateAccount_OnLoad()' in text:
        return data
    if text.count(OLD_OPEN) != 1:
        raise ValueError('Unexpected installed PrivateAccount_Open')
    out = text.replace(OLD_OPEN, NEW_OPEN).replace('\n', newline).encode('utf-8')
    return (b'\xef\xbb\xbf' + out) if bom else out


def toc(data):
    text = data.decode('utf-8')
    if '\r\nAccount.xml\r\n' in text:
        return data
    anchor = 'Journey.xml\r\n'
    if text.count(anchor) != 1:
        raise ValueError('Unexpected RelicCalc.toc')
    return text.replace(anchor, anchor + 'Account.xml\r\n').encode('utf-8')


def extend(raw, replacements, additions):
    """Preserve every untouched compressed member; append new members (quest-red-journal's method)."""
    old, central, end, version = parts(raw)
    if set(additions) & set(old):
        raise ValueError('Member already exists: ' + ', '.join(set(additions) & set(old)))
    temp = io.BytesIO()
    with zipfile.ZipFile(temp, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in {**replacements, **additions}.items():
            z.writestr(name, data)
    changed, new_central, _, _ = parts(encrypted_zip(temp.getvalue(), version))
    result, offsets = bytearray(), {}
    names = list(old) + list(additions)
    for name in names:
        offsets[name] = len(result)
        result.extend((changed[name] if name in changed else old[name])[0])
    central_start = len(result)
    for name in names:
        record = bytearray(new_central[name] if name in new_central else central[name])
        struct.pack_into('<I', record, 42, offsets[name])
        result.extend(record)
    tail = bytearray(end)
    struct.pack_into('<HHII', tail, 8, len(names), len(names), len(result) - central_start, central_start)
    result.extend(tail)
    return bytes(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external preparation folder')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('This delivery is scoped to the original development client')
    raw = (client / RELIC).read_bytes()
    with read_pak_bytes(raw) as z:
        names = z.namelist()
        lua, contents = z.read('PrivateMenus.lua'), z.read('RelicCalc.toc')
        others = {n: z.read(n) for n in names}
    replacements = {'PrivateMenus.lua': menus(lua), 'RelicCalc.toc': toc(contents)}
    dialog = b'\xff\xfe' + DIALOG.encode('utf-16-le')
    built = extend(raw, replacements, {'Account.xml': dialog})
    with read_pak_bytes(built) as check:
        if check.namelist() != names + ['Account.xml'] or check.testzip() is not None:
            raise ValueError('RelicCalc archive structure changed')
        for n in names:
            if check.read(n) != replacements.get(n, others[n]):
                raise ValueError('Unexpected member content: ' + n)
        if check.read('Account.xml') != dialog:
            raise ValueError('Account dialog differs')
    before, after = parts(raw)[0], parts(built)[0]
    for n in names:
        if n not in replacements and before[n][0] != after[n][0]:
            raise ValueError('Unrelated compressed member changed: ' + n)
    target = output / RELIC
    target.parent.mkdir(parents=True)
    target.write_bytes(built)
    subprocess.run(['java', str(HERE.parent / 'transmog-menu/SignClientPackages.java'), str(client), str(output)], check=True)
    (output / 'Pub.key').unlink(missing_ok=True)
    records = []
    for path in sorted(output.rglob('*')):
        if path.is_file():
            rel = path.relative_to(output).as_posix()
            old = client / rel
            records.append(dict(path=rel, original=sha(old.read_bytes()) if old.exists() else None, staged=sha(path.read_bytes())))
    manifest = dict(feature='account-window', revision='own-window', clientRoot=str(client), files=records, graphicsCompatibility=None,
                    serverChanged=False, repackChanged=False, pluginDialogs='+1 (PrivateAccount)',
                    sourceHashes={str(p): sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file()},
                    preservedFiles=[dict(path=rel, sha256=sha((client / rel).read_bytes())) for rel in
                                    ['bin64/Game.dll', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak', 'Pub.key']],
                    gameplayAcceptance='Pending: the Account window opens as a normal, movable, resizable window')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(records), 'affected files; original client unchanged:', output)


if __name__ == '__main__':
    main()
