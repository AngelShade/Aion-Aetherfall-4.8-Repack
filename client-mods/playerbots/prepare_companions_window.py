"""Give Player Companions its own movable, resizable window (over the installed client), like the Account window.

Plugin/RelicCalc/RelicCalc.pak changes (plus its regenerated signatures): a new Companions.xml plugin dialog
(one of the 20 shared plugin dialog slots) with a browser that follows the dialog size, its .toc entry, and a windowed
PrivateCompanions_Open in PrivateMenus.lua. PRIVATE_COMPANIONS_WINDOWED = false in PrivateMenus.lua restores the old
fullscreen Market dialog. PlayerBotBar.lua/.xml gain the Pull and Mark buttons, and the rebuilt AionIconBridge.dll with
its AionUiArt.index serves the window's art from the client's own archives. Every other member stays byte-identical.
Install with account-window/install.py.
"""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ACCOUNT = HERE.parent / 'account-window'
sys.path.insert(0, str(ACCOUNT))
import prepare_window as account_window  # noqa: E402
from prepare_window import extend, parts, read_pak_bytes, RELIC  # noqa: E402

WIDTH, HEIGHT, TITLE = 1180, 780, 25
DIALOG = ('<?xml version="1.0" encoding="UTF-16"?>\r\n<Screen widget_cnt="2">\r\n'
          f'  <Dialog name="PrivateCompanions" frame="0,0,{WIDTH},{HEIGHT}" text="Player Companions" close_offset="4,1" close_preset="v5_close" '
          'flag="close;title;not_visible;movable;resizable" preset="v5_dialog2" shadow_offset="8,8" '
          f'title_height="{TITLE}" title_offset="0,5">\r\n'
          '    <Scripts><OnLoad>PrivateCompanions_OnLoad();</OnLoad><OnEvent>PrivateCompanions_OnEvent(event);</OnEvent></Scripts>\r\n'
          f'    <Widget type="Browser" name="PrivateCompanionsBrowser" frame="0,0,{WIDTH},{HEIGHT - TITLE}" '
          'h_pos_align="proportion" v_pos_align="proportion"/>\r\n'
          '  </Dialog>\r\n</Screen>\r\n')
OLD_OPEN = '''function PrivateCompanions_Open()
    PrivateWarehouse:Show();
    PrivateWarehouse:SetText("Player Companions");
    PrivateWarehouse:SetRect(0, 0, 1280, 960);
    PrivateWarehouseBrowser:LoadUrlWithWebAuth(PRIVATE_PLAYERBOTS_URL);
end
'''
REVISION1 = f'''-- false: open Companions in the old fullscreen Market dialog instead of its own window.
PRIVATE_COMPANIONS_WINDOWED = true;
PrivateCompanionsSize = {{}};

function PrivateCompanions_OnLoad()
    PrivateCompanionsBrowser:CreateWebView();
    PrivateCompanions:Hide();
    PrivateCompanions:RegisterEvent("ADDON_TIMER");
end

function PrivateCompanions_OnEvent(event)
    -- A web page keeps the size its view was created with. After the player resizes the window (and stops for
    -- half a second), size the browser to the new client area and create its view again so the page reflows.
    local S = PrivateCompanionsSize;
    if event ~= "ADDON_TIMER" or not S.applied or PrivateCompanions:IsVisible() ~= 1 then return; end
    local _, _, w, h = PrivateCompanions:GetRect();
    local s = (GetUIScale and GetUIScale()) or 1;
    local size = string.format("%.0fx%.0f", w / s, h / s);
    if size == S.applied then S.pending = nil; return; end
    if S.pending ~= size then S.pending = size; S.ticks = 0; return; end
    S.ticks = S.ticks + 1;
    if S.ticks < 10 then return; end
    g_AddonName = "RelicCalc";
    S.applied, S.pending = size, nil;
    PrivateCompanionsBrowser:SetRect(0, 0, w / s, h / s - {TITLE});
    PrivateCompanionsBrowser:DestroyWebView();
    PrivateCompanionsBrowser:CreateWebView();
    PrivateCompanionsBrowser:LoadUrlWithWebAuth(PRIVATE_PLAYERBOTS_URL);
end

function PrivateCompanions_Open()
    if not PRIVATE_COMPANIONS_WINDOWED then
        PrivateWarehouse:Show();
        PrivateWarehouse:SetText("Player Companions");
        PrivateWarehouse:SetRect(0, 0, 1280, 960);
        PrivateWarehouseBrowser:LoadUrlWithWebAuth(PRIVATE_PLAYERBOTS_URL);
        return;
    end
    -- A normal movable, resizable window. Centre it once per session; later opens keep the player's spot and size.
    PrivateCompanions:Show();
    if not PrivateCompanionsSize.applied then
        PrivateCompanionsSize.applied = "{WIDTH}x{HEIGHT}";
        local ok, _, sw, sh = pcall(function() if AFCore and AFCore.Scale then return AFCore.Scale() end end);
        local s = (GetUIScale and GetUIScale()) or 1;
        if ok and sw and sh and sw > 0 and sh > 0 and s > 0 then
            PrivateCompanions:SetRect(math.max(0, math.floor((sw / s - {WIDTH}) / 2)), math.max(0, math.floor((sh / s - {HEIGHT}) / 2)), {WIDTH}, {HEIGHT});
        end
    end
    PrivateCompanionsBrowser:LoadUrlWithWebAuth(PRIVATE_PLAYERBOTS_URL);
end
'''
# Second revision: the page follows the window's pixel size (UI scale included) and the window keeps a minimum size.
NEW_OPEN = f'''-- false: open Companions in the old fullscreen Market dialog instead of its own window.
PRIVATE_COMPANIONS_WINDOWED = true;
-- Smallest window the page lays out in (UI units).
PRIVATE_COMPANIONS_MIN_W, PRIVATE_COMPANIONS_MIN_H = 760, 520;
PrivateCompanionsSize = {{}};

function PrivateCompanions_OnLoad()
    PrivateCompanionsBrowser:CreateWebView();
    PrivateCompanions:Hide();
    PrivateCompanions:RegisterEvent("ADDON_TIMER");
end

-- A web page is drawn at the pixel size its view was created with, which ignores the UI scale and later resizes.
-- Size the browser to the window's client area (never below the minimum) and create its view again whenever
-- that pixel size differs from the view's, then load the page.
function PrivateCompanions_Fit(load)
    local S = PrivateCompanionsSize;
    local x, y, w, h = PrivateCompanions:GetRect();
    local s = (GetUIScale and GetUIScale()) or 1;
    if not w or not h or s <= 0 then
        -- Not measurable yet: show the page now; the timer fits it once the window has a size.
        S.view = "?";
        if load then PrivateCompanionsBrowser:LoadUrlWithWebAuth(PRIVATE_PLAYERBOTS_URL); end
        return;
    end
    local uw, uh = math.max(w / s, PRIVATE_COMPANIONS_MIN_W), math.max(h / s, PRIVATE_COMPANIONS_MIN_H);
    if uw > w / s + 0.5 or uh > h / s + 0.5 then PrivateCompanions:SetRect(x / s, y / s, uw, uh); end
    local size = string.format("%.0fx%.0f", uw * s, uh * s);
    S.pending = nil;
    if size ~= S.view then
        g_AddonName = "RelicCalc";
        S.view = size;
        PrivateCompanionsBrowser:SetRect(0, 0, uw, uh - {TITLE});
        PrivateCompanionsBrowser:DestroyWebView();
        PrivateCompanionsBrowser:CreateWebView();
        load = true;
    end
    if load then PrivateCompanionsBrowser:LoadUrlWithWebAuth(PRIVATE_PLAYERBOTS_URL); end
end

function PrivateCompanions_OnEvent(event)
    -- After the player resizes the window and stops for half a second, fit the page to it again.
    local S = PrivateCompanionsSize;
    if event ~= "ADDON_TIMER" or not S.view or PrivateCompanions:IsVisible() ~= 1 then return; end
    local _, _, w, h = PrivateCompanions:GetRect();
    local size = string.format("%.0fx%.0f", w, h);
    if size == S.view then S.pending = nil; return; end
    if S.pending ~= size then S.pending = size; S.ticks = 0; return; end
    S.ticks = S.ticks + 1;
    if S.ticks >= 10 then PrivateCompanions_Fit(false); end
end

function PrivateCompanions_Open()
    if not PRIVATE_COMPANIONS_WINDOWED then
        PrivateWarehouse:Show();
        PrivateWarehouse:SetText("Player Companions");
        PrivateWarehouse:SetRect(0, 0, 1280, 960);
        PrivateWarehouseBrowser:LoadUrlWithWebAuth(PRIVATE_PLAYERBOTS_URL);
        return;
    end
    -- A normal movable, resizable window. Centre it once per session; later opens keep the player's spot and size.
    PrivateCompanions:Show();
    if not PrivateCompanionsSize.placed then
        PrivateCompanionsSize.placed = true;
        local ok, _, sw, sh = pcall(function() if AFCore and AFCore.Scale then return AFCore.Scale() end end);
        local s = (GetUIScale and GetUIScale()) or 1;
        if ok and sw and sh and sw > 0 and sh > 0 and s > 0 then
            PrivateCompanions:SetRect(math.max(0, math.floor((sw / s - {WIDTH}) / 2)), math.max(0, math.floor((sh / s - {HEIGHT}) / 2)), {WIDTH}, {HEIGHT});
        end
    end
    PrivateCompanions_Fit(true);
end
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def menus(data):
    bom = data.startswith(b'\xef\xbb\xbf')
    text = data.decode('utf-8-sig')
    newline = '\r\n' if '\r\n' in text else '\n'
    text = text.replace('\r\n', '\n')
    if NEW_OPEN in text:
        return data
    # Fresh client: the fullscreen opener; installed first revision: the window that did not follow the UI scale.
    old = REVISION1 if REVISION1 in text else OLD_OPEN
    if text.count(old) != 1:
        raise ValueError('Unexpected installed PrivateCompanions_Open')
    out = text.replace(old, NEW_OPEN).replace('\n', newline).encode('utf-8')
    return (b'\xef\xbb\xbf' + out) if bom else out


def toc(data):
    text = data.decode('utf-8')
    anchor = 'Account.xml\r\n'
    if '\r\nCompanions.xml\r\n' in text:
        return data
    if text.count(anchor) != 1:
        raise ValueError('Unexpected RelicCalc.toc (install the Account window first)')
    return text.replace(anchor, anchor + 'Companions.xml\r\n').encode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--bridge', type=Path, help='native-icon-bridge build output with bin64/AionIconBridge.dll and AionUiArt.index')
    args = parser.parse_args()
    client, output = args.client.resolve(strict=True), args.output.resolve()
    if output.exists() or output.is_relative_to(client) or output.is_relative_to(HERE.parents[1]):
        raise ValueError('Use a fresh external preparation folder')
    if (client.parent / 'REPACK.json').exists():
        raise ValueError('This delivery is scoped to the original development client')
    raw = (client / RELIC).read_bytes()
    with read_pak_bytes(raw) as z:
        names = z.namelist()
        others = {n: z.read(n) for n in names}
    replacements = {'PrivateMenus.lua': menus(others['PrivateMenus.lua']), 'RelicCalc.toc': toc(others['RelicCalc.toc']),
                    # The command bar's Pull and Mark buttons (their orders are in the rebuilt bridge).
                    'PlayerBotBar.lua': (HERE / 'PlayerBotBar.lua').read_bytes(), 'PlayerBotBar.xml': (HERE / 'PlayerBotBar.xml').read_bytes()}
    dialog = b'\xff\xfe' + DIALOG.encode('utf-16-le')
    replacements = {n: v for n, v in replacements.items() if v != others[n]}
    added = [] if 'Companions.xml' in names else ['Companions.xml']
    if not added and others['Companions.xml'] != dialog:
        raise ValueError('Unexpected installed Companions dialog')
    built = extend(raw, replacements, {'Companions.xml': dialog} if added else {})
    with read_pak_bytes(built) as check:
        if check.namelist() != names + added or check.testzip() is not None:
            raise ValueError('RelicCalc archive structure changed')
        for n in names:
            if check.read(n) != replacements.get(n, others[n]):
                raise ValueError('Unexpected member content: ' + n)
        if check.read('Companions.xml') != dialog:
            raise ValueError('Companions dialog differs')
    before, after = parts(raw)[0], parts(built)[0]
    for n in names:
        if n not in replacements and before[n][0] != after[n][0]:
            raise ValueError('Unrelated compressed member changed: ' + n)
    target = output / RELIC
    target.parent.mkdir(parents=True)
    target.write_bytes(built)
    subprocess.run(['java', str(HERE.parent / 'transmog-menu/SignClientPackages.java'), str(client), str(output)], check=True)
    (output / 'Pub.key').unlink(missing_ok=True)
    # The rebuilt icon bridge serves the window's art from the client's own archives; its item index stays the live one.
    bridge = args.bridge.resolve(strict=True) if args.bridge else None
    if bridge and (bridge / 'bin64/AionIconBridge.index').read_bytes() != (client / 'bin64/AionIconBridge.index').read_bytes():
        raise ValueError('Installed native item index drifted; rebuild the bridge against the current client')
    for name in ('AionIconBridge.dll', 'AionUiArt.index') if bridge else ():
        (output / 'bin64').mkdir(exist_ok=True)
        (output / 'bin64' / name).write_bytes((bridge / 'bin64' / name).read_bytes())
    records = []
    for path in sorted(output.rglob('*')):
        if path.is_file():
            rel = path.relative_to(output).as_posix()
            old = client / rel
            records.append(dict(path=rel, original=sha(old.read_bytes()) if old.exists() else None, staged=sha(path.read_bytes())))
    sources = [Path(__file__).resolve(), Path(account_window.__file__).resolve(), HERE / 'PlayerBotBar.lua', HERE / 'PlayerBotBar.xml',
               HERE / 'playerbot_bar.h', HERE.parent / 'native-icon-bridge/icon_bridge.cpp', HERE.parent / 'native-icon-bridge/prepare_ui_art.py']
    manifest = dict(feature='companions-window', revision='fitted-window', clientRoot=str(client), files=records, graphicsCompatibility=None,
                    serverChanged=False, repackChanged=False, pluginDialogs='+1 (PrivateCompanions)',
                    sourceHashes={str(p): sha(p.read_bytes()) for p in sources},
                    preservedFiles=[dict(path=rel, sha256=sha((client / rel).read_bytes())) for rel in
                                    ['bin64/Game.dll', 'bin64/AionIconBridge.index', 'Data/ui/game/game.pak', 'L10N/enu/Data/data.pak', 'Pub.key']],
                    gameplayAcceptance='Pending: Player Companions opens as a normal, movable, resizable window and its page fills it at any UI scale and after a resize, never smaller than 760x520')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared', len(records), 'affected files; original client unchanged:', output)


if __name__ == '__main__':
    main()
