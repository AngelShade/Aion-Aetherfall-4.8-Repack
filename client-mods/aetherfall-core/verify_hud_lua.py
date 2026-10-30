"""Exercise AetherfallCore.lua/AetherfallHUD.lua through the client's real Lua 5.1 widget wrappers.

Fake bridge values only; no game. Run with the working directory set to a scratch
folder: it creates Mods/AetherfallUI/state.json there to switch the layer on/off.
"""
import argparse
from pathlib import Path
import os
import re
import sys
import xml.etree.ElementTree as E

HERE = Path(__file__).resolve().parent
if os.environ.get('AION_LUA_TEST_DEPS'):
    sys.path.insert(0, os.environ['AION_LUA_TEST_DEPS'])
sys.path.insert(0, str(HERE.parent / 'expanded-warehouse'))
from lupa.lua51 import LuaRuntime  # noqa: E402
from codec import read_pak  # noqa: E402
sys.path.insert(0, str(HERE))
import prepare  # noqa: E402

FAKE = '''
AetherfallCore=function(cmd,a,b,c,d,e,f,g,h,i)
  if cmd=="version" then return 1,"AetherfallCore 1.0" end
  if cmd=="exp" then return 643,1000,20,120,1000 end
  if cmd=="money" then return 1284377 end
  if cmd=="fps" then return 59.6 end
  if cmd=="scale" then return UISCALE,1280*UISCALE,960*UISCALE end
  if cmd=="auras" then return a=="player" and 2 or 1 end
  if cmd=="aura" then if a=="player" then if b==1 then return 1001,"Shield",3,0,754.2,1800,754200,7,3 else return 1002,"Bless",1,0,8.4,30,8400,7,3 end else return 2001,"Curse",1,1,95,120,95000,9,3 end end
  if cmd=="slot" then if a==0xb0 and b<=2 then return 0x16,1000+b,1000+b,1,nil,nil,true end
    if a==0x20 and c=="debuff_item" and b==1 then return 0x16,2001,2001,1,nil,nil,true end return 0,0,0,0 end
  if cmd=="dialog" then local d=DIALOGS[a]; if not d then return nil end return d.name,d.x,d.y,d.w,d.h,d.shown end
  if cmd=="save" then return SAVEFILE(a,b) end
  if cmd=="cursor" then if CX==false then return nil end return CX or 1,CY or 1,1280*UISCALE,960*UISCALE,CDOWN==true,CSHIFT==true end
  if cmd=="tippin" then if not DIALOGS[a] then return false end PINS[a]={b,c,d,e,f,g,h} return true end
  if cmd=="read" then return READFILE(a) end
  if cmd=="move" then local d=DIALOGS[a]; if not d then return false end if PINS[a] and (PINS[a][3] or 0)==0 then PINS[a]={b,c,0,PINS[a][4]} end d.x,d.y=b,c; MOVES[#MOVES+1]={a,b,c}; return true end
  if cmd=="pin" then
    local k=h and (a..":"..h) or a
    if a==0 then PINS={} return true end
    if b==nil then PINS[k]=nil return true end
    if not DIALOGS[a] then return false end
    PINS[k]={b,c,d or 0,e,f,g,i}
    local t=DIALOGS[a]
    if not h and (d or 0)~=4 then
      if f and f>0 then t.w,t.h=f,g end
      local m=d or 0
      if m==5 then t.x,t.y=-20000,-20000 else t.x=(m==1 or m==3) and b-t.w or b; t.y=m>=2 and c-t.h or c end
      if e then t.x=math.max(0,math.min(1280*UISCALE-t.w,t.x)); t.y=math.max(0,math.min(960*UISCALE-t.h,t.y)) end
    end
    return true end
  if cmd=="gaps" then GAPS[a]=b return true end
  if cmd=="movable" then MOVABLE[a]=b return true end
  if cmd=="hittest" then return true end
  if cmd=="command" then COMMANDS[#COMMANDS+1]=a return true end
  if cmd=="setshown" then local d=DIALOGS[a]; if not d then return false end d.shown=b; SHOWN[#SHOWN+1]={a,b}; return true end
  if cmd=="rect" and not b and DIALOGS[a] then local d=DIALOGS[a] return d.x,d.y,d.w,d.h,d.shown end
  if cmd=="rect" then if a==0xcf then return 10*UISCALE,900*UISCALE,370*UISCALE,30*UISCALE,CHATINPUT==true end
    local i=tonumber((b or ""):match("(%d+)$")) or 0
    if a==0x20 then local t=DIALOGS[0x0b] return t.x+34*(i-1)*UISCALE,t.y+t.h+4*UISCALE,22*UISCALE,22*UISCALE,true end
    return (800+34*i)*UISCALE,30*UISCALE,32*UISCALE,32*UISCALE,true end
end
GetUIScale=function() return UISCALE end  -- a real C function in the client
DIALOGS={[0x11]={name="start_dialog",x=1100*UISCALE,y=520*UISCALE,w=165*UISCALE,h=145*UISCALE,shown=true},
         [0x0e]={name="radar_dialog",x=1092*UISCALE,y=22*UISCALE,w=180*UISCALE,h=180*UISCALE,shown=true},
         [0x14]={name="exp_bar_dialog",x=0,y=955*UISCALE,w=1280*UISCALE,h=5*UISCALE,shown=true},
         [0xb0]={name="buff_status_dialog",x=800*UISCALE,y=22*UISCALE,w=262*UISCALE,h=101*UISCALE,shown=true},
         [0xb1]={name="debuff_status_dialog",x=800*UISCALE,y=22*UISCALE,w=262*UISCALE,h=101*UISCALE,shown=true},
         [0x0b]={name="target_dialog",x=644*UISCALE,y=734*UISCALE,w=251*UISCALE,h=58*UISCALE,shown=true}}
MOVES={} SHOWN={} MENU={} PINS={} GAPS={} COMMANDS={} MOVABLE={}
sha1={sha1=function(s) return "abc123" end}
RegisterMenu=function(label,slash,icon) MENU[#MENU+1]={label,slash,icon} end
-- The server's browser window (RelicCalc's PrivateMenus.lua, loaded before the interface).
WEB={} OPENED=0 MARKETVIS=false
local function fake(name) return setmetatable({}, {__index=function(_, m) return function(self, ...) WEB[#WEB+1]={name, m, ...} if m=="IsVisible" then return MARKETVIS and 1 or 0 end end end}) end
PrivateWarehouse=fake("PrivateWarehouse") PrivateWarehouseBrowser=fake("PrivateWarehouseBrowser")
PrivateWarehouse_Open=function() OPENED=OPENED+1; MARKETVIS=true end
GetServerName=function() return "Aetherfall" end
GetZoneText=function() return "Sanctum" end
GetPlayerMapPosition=function() return 1520.4,1488.2,120 end
GetTime=function() return "21:47" end
UnitName=function() return "Aerin" end UnitLevel=function() return 65 end
UnitHP=function() return 31000 end UnitHPMax=function() return 42000 end
GetNumPartyMembers=function() return 3 end GetNumLegionMembers=function() return 12 end
'''


def state(applied):
    path = Path('Mods/AetherfallUI/state.json')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('{"Desired":"%s","Applied":"%s"}' % (applied, applied), encoding='utf-8')
    (path.parent / 'manifest.json').write_text('{}', encoding='utf-8')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    a = p.parse_args()
    # Our plugin XML may only use the vocabulary of plugin layouts that load in this client:
    # one unknown attribute or element aborts the client's loader for the whole file.
    plugin = read_pak(a.client / 'Plugin/RelicCalc/RelicCalc.pak')
    allowed, values = set(), {}
    for name in ('PlayerBotBar.xml', 'Wardrobe.xml', 'RelicCalc.xml', 'Journey.xml'):
        for node in E.fromstring(plugin.read(name)).iter():
            allowed.add(node.tag)
            for k, v in node.attrib.items():
                allowed.add((node.tag, k))
                if k == 'flag':
                    values.setdefault((node.tag, node.get('type')), set()).update(v.split(';'))
    ours = list(E.fromstring(prepare.hud_xml()).iter()) + list(E.fromstring(prepare.lazy_xml()).iter())
    for node in ours:
        assert node.tag in allowed, 'element ' + node.tag
        for k, v in node.attrib.items():
            assert (node.tag, k) in allowed, f'attribute {node.tag}.{k}'
            if k == 'flag':
                known = values.get((node.tag, node.get('type')), set())
                assert set(v.split(';')) <= known, f'flag {v} on {node.tag}/{node.get("type")}: allowed {sorted(known)}'
    z = read_pak(a.client / 'bin32/bin32.pak')
    for scale in (0.8, 1, 1.125, 1.5):
        lua = LuaRuntime(unpack_returned_tuples=True)
        g = lua.globals()
        widgets, calls = {}, []

        def native(api, *args):
            if api in ('RegisterEvent', 'SetTimer', 'ResetTimer'):
                calls.append((api, args))
                return
            if len(args) < 2:
                return
            st = widgets.setdefault(args[1], dict(text='', visible=1, rect=(0, 0, 1, 1), attr={}))
            v = args[2:]
            if api == 'SetWidgetText':
                st['text'] = str(v[0])
            elif api == 'GetWidgetText':
                return st['text']
            elif api == 'SetWidgetVisible':
                st['visible'] = v[0]
            elif api == 'GetWidgetVisible':
                return st['visible']
            elif api == 'SetWidgetRect':
                st['rect'] = tuple(round(float(n) * scale, 3) for n in v)
            elif api == 'GetWidgetRect':
                return st['rect']
            elif api == 'SetWidgetAttr':
                st['attr'][v[0]] = v[1]
        sources = {}
        for n in z.namelist():
            if n.endswith('.lua'):
                text = z.read(n).decode('utf-8-sig', errors='replace')
                sources[Path(n).stem] = text
                for api in re.findall(r'\b(\w+)\(g_AddonName\b', text):
                    g[api] = lambda *args, api=api: native(api, *args)
        g.GetUIScale = lambda: scale
        g.AddNewWidget = lambda *args: None
        g.GetAionStr = lambda text: text
        g.DisplayChatMessage = lambda *args: calls.append(('chat', args))
        for name in ('Widget', 'Container', 'Button', 'Static', 'StaticImage', 'EditBox', 'MessageWidget', 'SlashCmd'):
            lua.execute(sources[name])
            g.package.loaded[name] = True
        g.package.loaded['sha1'] = True

        def table(n):
            t = lua.table(tag=n.tag, attr=lua.table_from(n.attrib))
            if n.text and n.text.strip():
                t[1] = n.text
            for i, child in enumerate(n, 1):
                t[i] = table(child)
            return t
        g.g_AddonName = 'RelicCalc'
        g.UISCALE = scale
        lua.execute(FAKE)

        def save_file(name, text):
            # Mirrors the bridge: bounded *.cfg names in Mods/AetherfallUI only.
            if not name or not re.fullmatch(r'[A-Za-z0-9_.-]{1,76}\.cfg', name) or '..' in name or name.startswith('.') or len(text) > 32768:
                return False
            Path('Mods/AetherfallUI', name).write_bytes(text.encode('utf-8'))
            return True
        g.SAVEFILE = save_file

        def read_file(name):
            # Mirrors the bridge: .cfg settings or state.json in Mods/AetherfallUI, else nil.
            if not name or not (re.fullmatch(r'[A-Za-z0-9_.-]{1,76}[.]cfg', name) or name == 'state.json') or '..' in name:
                return None
            f = Path('Mods/AetherfallUI', name)
            return f.read_bytes().decode('utf-8') if f.is_file() else None
        g.READFILE = read_file
        # The client's Lua io has no write: remove it here too.
        lua.execute("local mt = getmetatable(io.stdout); mt.__index.write = nil; io.write = nil; io.output = nil")
        for n in E.fromstring(prepare.hud_xml()):
            w = g.CreateWidget('Dialog', n.get('name'))
            w.LoadXML(w, table(n))
        # The client's own XML loader, used in game to build the on-demand dialogs.
        g.lxp = lua.table(lom=lua.table(parse=lambda text: table(E.fromstring(text))))
        lua.execute("function LoadXMLWidget(xmlDoc) local w = CreateWidget('Dialog', xmlDoc.attr.name); w:LoadXML(xmlDoc) end")
        lua.execute((HERE / 'AetherfallCore.lua').read_text(encoding='utf-8'))
        lua.execute(prepare.layout_lua(Path(a.client), HERE.parent).decode('utf-8'))
        lua.execute(prepare.lazy_lua().decode('utf-8'))
        assert 'AFUIOptions' in widgets and 'AFUIMovers' in widgets, 'options and movers are built with the plugin'
        lua.execute((HERE / 'AetherfallHUD.lua').read_text(encoding='utf-8'))
        lua.execute((HERE / 'AetherfallOptions.lua').read_text(encoding='utf-8'))
        g.Widget_TriggerScript('AFHTop', 'OnLoad', '')
        assert widgets['AFHTop']['visible'] == 0
        assert [c for c in calls if c[0] == 'RegisterEvent'], 'events registered'

        state('on')
        for _ in range(12):
            g.AFHUD_OnEvent('ADDON_TIMER')
        assert widgets['AFHTop']['visible'] == 0, 'hidden unless the layout state is applied'

        state('layout')
        g.AFHUD.ticks = 0
        for _ in range(12):
            g.AFHUD_OnEvent('ADDON_TIMER')
        assert lua.eval('AFHUD.error') is None, lua.eval('AFHUD.error')
        top = widgets['AFHTop']
        assert top['visible'] == 1 and top['rect'] == (0, 0, round(1280 * scale, 3), round(18 * scale, 3)), top
        assert 'Sanctum' in widgets['AFHTopLeft']['text'] and '1520, 1488' in widgets['AFHTopLeft']['text']
        assert widgets['AFHTopCenter']['text'] == 'XP 64.3%  (recoverable 2.0%)  repose 12%    1,284,377 Kinah'
        assert widgets['AFHTopRight']['text'] == '60 fps    Group 3    Legion 12'
        chat = widgets['AFHChat']
        # The status line has a fixed spot under the chat input and does not follow it.
        assert chat['visible'] == 1 and chat['rect'] == (round(8 * scale, 3), round(935 * scale, 3), round(361 * scale, 3), round(18 * scale, 3)), chat
        g.CHATINPUT = True
        for _ in range(20):
            g.AFHUD_OnEvent('ADDON_TIMER')
        assert widgets['AFHChat']['visible'] == 1 and widgets['AFHChat']['rect'] == chat['rect'], 'stays put while typing'
        g.CHATINPUT = False
        # No aura timer labels: the game shows buff times, and plugin windows are limited to 20 slots.
        assert not any(k.startswith('AFHT') and k[4:5].isdigit() for k in widgets)
        assert not any(c[0] in ('SetTimer', 'ResetTimer') for c in calls), 'Keep Wardrobe shared timer unchanged'

        # Options window, live settings, movers and persistence.
        for f in Path('Mods/AetherfallUI').glob('ui-*.cfg'):
            f.unlink()
        lua.execute('MENU = {}'); g.AFUI_OnLoad()  # in game it runs once, from the data bar's OnLoad (layout state)
        assert lua.eval('#MENU') == 0, 'no Additional Functions entry for the interface: it has its own button'
        for label, slash in (('Player Companions', '/companions'), ('Aetherfall Season Pass', '/seasonpass'), ('Wardrobe', '/wardrobe'),
                             ('Broker', '/say .broker'), ('Relic Calculator', '/relic')):
            g.RegisterMenu(label, slash, 'icon')
        assert [lua.eval(f'MENU[{k}][2]') for k in range(1, int(lua.eval('#MENU')) + 1)] == ['/relic'], 'HUD buttons left out of the menu'
        g.AFUI_OnEvent('PLAYER_ENTERING_WORLD')
        assert lua.eval('AFUI.ready')
        g.DoSlashCmd('/afui')
        for _ in range(4):  # commands queue; timer ticks build the window, then open it
            g.AFUI_OnEvent('ADDON_TIMER')
        assert widgets['AFUIOptions']['visible'] == 1 and widgets['AFUITab1']['text'] == 'General'
        # Colour theme: the top bar's values and the options accent follow it at once.
        g.AFUI.Click('Tab1'); g.AFUI.Click('Row2More')  # Aether -> Gold
        for _ in range(20):
            g.AFHUD_OnEvent('ADDON_TIMER')
        assert widgets['AFHTopCenter']['attr'].get('textColor', '').startswith('0.839'), widgets['AFHTopCenter']['attr']
        assert widgets['AFUIHeader']['attr'].get('textColor', '').startswith('0.839')
        g.AFUI.Click('Row2Less')  # back to Aether
        g.AFUI.Click('Tab5')  # Data Texts
        assert widgets['AFUIRow1Label']['text'] == 'Top bar' and widgets['AFUIRow2Value']['text'] == 'Zone'
        g.AFUI.Click('Row2Value')
        assert widgets['AFUIRow2Value']['text'] == 'Coordinates' and lua.eval("AFUI.Get('dl1')") == 'coords'
        g.AFUI.Click('Tab8')  # Movers
        assert widgets['AFUIRow4Label']['text'] == 'Grid size' and widgets['AFUIRow4Less']['visible'] == 1
        g.AFUI.Click('Row4More')
        assert lua.eval("AFUI.Get('grid')") == 10
        g.AFUI.Click('Row4Less')
        g.AFUI.Click('Tab7')  # Minimap
        g.AFUI.Click('Row2Value')  # XP bar off
        assert lua.eval("AFUI.Get('xpbar')") == 0
        for _ in range(20):
            g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('#SHOWN') >= 1 and lua.eval('SHOWN[1][1]') == 0x14 and lua.eval('SHOWN[1][2]') is False, 'XP bar hidden natively'
        g.AFUI.Click('Row2Value')  # XP bar back on: shown again right away
        assert lua.eval('SHOWN[#SHOWN][1]') == 0x14 and lua.eval('SHOWN[#SHOWN][2]') is True, 'XP bar shown again when switched on'
        # Design positions replaced the game's saved ones (radar right/top, start menu below it).
        assert abs(lua.eval('DIALOGS[0x0e].x') - 1092 * scale) < 1e-6 and abs(lua.eval('DIALOGS[0x0e].y') - 22 * scale) < 1e-6
        assert abs(lua.eval('DIALOGS[0x14].y') - 955 * scale) < 1e-6
        # Start menu: a click-through window just around its buttons (flight gauge 1..47, shop to 110).
        assert abs(lua.eval('DIALOGS[0x11].x') - 1071 * scale) < 1e-6 and abs(lua.eval('DIALOGS[0x11].y') - 230 * scale) < 1e-6
        assert abs(lua.eval('DIALOGS[0x11].w') - 201 * scale) < 1e-6 and abs(lua.eval('DIALOGS[0x11].h') - 110 * scale) < 1e-6
        assert lua.eval('GAPS[0x11]') is True and abs(lua.eval('PINS["17:central_market_button"][1]') - 87 * scale) < 1e-6
        assert abs(lua.eval('PINS["17:aetherfall_broker_button"][1]') - 47 * scale) < 1e-6, 'Broker is a start-menu button'
        assert 'AFUIBrokerButton' not in widgets and 'AFUIMenuButton' not in widgets, 'no extra plugin windows'
        # An old Menu position does not make the once-a-second pass fight the menu's pin (it bounced).
        lua.execute('AFUI.positions[AFUI.Get("profile") .. ".17"] = {0.5, 0.5}')
        for _ in range(41):
            g.AFUI_OnEvent('ADDON_TIMER')
        assert all(m[1] != 0x11 for m in [list(lua.eval(f'MOVES[{k}]').values()) for k in range(1, int(lua.eval('#MOVES')) + 1)][-40:]), 'menu not moved'
        lua.execute('AFUI.positions[AFUI.Get("profile") .. ".17"] = nil')
        for _ in range(2):
            g.AFUI_OnEvent('ADDON_TIMER')
        # Movers: one overlay, a named box per frame, dragged with the mouse the core reports.
        # The notification icons ("Daily quest has arrived") get a mover even while hidden.
        lua.execute('AFUI.db.snapframes = 0')  # snapping to frames has its own check below
        lua.execute('DIALOGS[0x60] = {name = "help_indicator_dialog", x = 405 * UISCALE, y = 900 * UISCALE, w = 505 * UISCALE, h = 47 * UISCALE, shown = false}')
        g.DoSlashCmd('/moveui')
        for _ in range(4):  # the overlay is built on first use, then unlocked
            g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('AFUI.unlocked') and widgets['AFUIMovers']['visible'] == 1
        movers = {widgets[f'AFUIMove{i}']['text']: i for i in range(1, 57) if widgets[f'AFUIMove{i}']['visible'] == 1}
        assert 'Menu' not in movers and 'Minimap' in movers and 'Target' in movers, movers
        assert all(k in movers for k in ('Start', 'Market', 'Shop', 'Pass', 'Flight', 'Broker', 'Interface')), movers
        assert 'Notifications (hidden)' in movers, movers

        def mouse(x, y, down, shift=False):
            g.CX, g.CY, g.CDOWN, g.CSHIFT = x, y, down, shift
            g.AFUI_OnEvent('ADDON_TIMER')

        def box(i):
            return lua.eval(f'AFUI.movers[{i}].x'), lua.eval(f'AFUI.movers[{i}].y')

        def drag(i, x, y):
            bx, by = box(i)
            mouse(bx + 5, by + 5, False)
            mouse(bx + 5, by + 5, True)   # press on the box
            mouse(x + 5, y + 5, True)     # drag: the native window follows live
            mouse(x + 5, y + 5, False)    # release
        i = movers['Minimap']
        drag(i, 203 * scale, 301 * scale)
        grid = 8 * scale
        last = lua.eval('MOVES[#MOVES]')
        assert last[1] == 0x0e and abs(last[2] - round(203 * scale / grid) * grid) < 1e-6 and abs(last[3] - round(301 * scale / grid) * grid) < 1e-6, list(last.values())
        assert abs(lua.eval('DIALOGS[0x14].y') - 955 * scale) < 1e-6, 'other HUD pieces stay put'
        bx, by = box(i)
        mouse(bx + 5, by + 5, True, True)  # Shift+click resets to the layout spot
        mouse(bx + 5, by + 5, False)
        assert abs(lua.eval('DIALOGS[0x0e].x') - 1092 * scale) < 1e-6 and abs(lua.eval('DIALOGS[0x0e].y') - 22 * scale) < 1e-6, 'per-frame reset to the design position'
        mouse(0, 0, True)  # a press outside every box drags nothing
        mouse(600 * scale, 600 * scale, True)
        mouse(600 * scale, 600 * scale, False)
        assert abs(lua.eval('DIALOGS[0x0e].x') - 1092 * scale) < 1e-6
        # Start-menu buttons move one by one: Central Market goes to the top left; the click-through
        # menu window grows to hold it and the others stay where they were.
        m = movers['Market']
        drag(m, 40 * scale, 40 * scale)
        mx, my = round(40 * scale / grid) * grid, round(40 * scale / grid) * grid
        assert abs(lua.eval('DIALOGS[0x11].x') - mx) < 1e-6 and abs(lua.eval('DIALOGS[0x11].y') - my) < 1e-6, 'menu window holds the moved button'
        assert lua.eval('PINS["17:central_market_button"][1]') == 0 and lua.eval('PINS["17:central_market_button"][2]') == 0
        assert abs(lua.eval('PINS["17:start_button"][1]') - (1118 * scale - mx)) < 1e-6, 'other buttons keep their screen spot'
        assert lua.eval('PINS["17:mail_full_notice"]') is not None, 'every mail icon shares the Mail spot'
        b = movers['Broker']
        drag(b, 510 * scale, 100 * scale)
        bx0 = lua.eval('DIALOGS[0x11].x')
        assert abs(lua.eval('PINS["17:aetherfall_broker_button"][1]') + bx0 - round(510 * scale / grid) * grid) < 1e-6, 'Broker button moves by itself'
        bx, by = box(m)
        mouse(bx + 2, by + 2, True, True)  # Shift+click: Market back in the grid
        mouse(bx + 2, by + 2, False)
        g.AFUI_OnEvent('ADDON_TIMER')
        assert abs(lua.eval('PINS["17:central_market_button"][1]') + lua.eval('DIALOGS[0x11].x') - 1158 * scale) < 1e-6, 'Market back in the grid'
        # Target: the box sits on the target frame, and dragging moves the real target window.
        t = movers['Target']
        tx, ty = box(t)
        assert abs(tx - lua.eval('DIALOGS[0x0b].x')) < 1e-6 and abs(ty - lua.eval('DIALOGS[0x0b].y')) < 1e-6
        drag(t, 300 * scale, 500 * scale)
        assert abs(lua.eval('DIALOGS[0x0b].x') - round(300 * scale / grid) * grid) < 1e-6, 'target window follows its mover'
        # The game re-centres the target at the top when a target is picked: the core's pin keeps
        # the mover spot in that same native SetRect, with no event or tick in between.
        g.AFUI_OnEvent('ADDON_TIMER')
        lua.execute('local p = PINS[0x0b]; DIALOGS[0x0b].x, DIALOGS[0x0b].y = p and p[1] or 700 * UISCALE, p and p[2] or 0')
        assert abs(lua.eval('DIALOGS[0x0b].x') - round(300 * scale / grid) * grid) < 1e-6 and lua.eval('DIALOGS[0x0b].y') > 0, 'target pinned natively'
        assert lua.eval('PINS[0x0e] == nil'), 'a reset window is released'
        lua.execute('DIALOGS[0x0b].x, DIALOGS[0x0b].y = 700 * UISCALE, 0')  # an older core without pins: the event still puts it back
        g.AFUI_OnEvent('USER_CHANGED_TARGET')
        assert abs(lua.eval('DIALOGS[0x0b].x') - round(300 * scale / grid) * grid) < 1e-6, 'target put back on USER_CHANGED_TARGET'
        drag(i, 400 * scale, 400 * scale)
        lua.execute('DIALOGS[0x60] = nil')
        g.DoSlashCmd('/moveui')
        for _ in range(2):
            g.AFUI_OnEvent('ADDON_TIMER')
        assert not lua.eval('AFUI.unlocked') and widgets['AFUIMovers']['visible'] == 0
        lua.execute('DIALOGS[0x60] = nil')
        saved = [f.read_text(encoding='utf-8') for f in Path('Mods/AetherfallUI').glob('ui-abc123.*.cfg')]
        assert saved and any('set.xpbar=1' in t and 'DPS.14=' in t and 'DPS.2061=' in t and 'set.dl1=coords' in t for t in saved), saved
        # Server windows: before the page loads the browser fills the screen and its web view is
        # created at that size (once per screen size); the window itself is sized to the screen.
        lua.execute('WEB = {}'); g.PrivateWarehouse_Open()
        calls = [(lua.eval(f'WEB[{k}][1]'), lua.eval(f'WEB[{k}][2]')) for k in range(1, int(lua.eval('#WEB')) + 1)]
        assert lua.eval('OPENED') == 1 and ('PrivateWarehouseBrowser', 'CreateWebView') in calls, calls
        assert calls.index(('PrivateWarehouseBrowser', 'SetRect')) < calls.index(('PrivateWarehouseBrowser', 'DestroyWebView')) < calls.index(('PrivateWarehouseBrowser', 'CreateWebView'))
        assert abs(lua.eval('WEB[1][5]') - 1280) < 1e-6, 'browser as wide as the screen'
        g.AFUI_OnEvent('ADDON_TIMER')
        assert any(lua.eval(f'WEB[{k}][1]') == 'PrivateWarehouse' and lua.eval(f'WEB[{k}][2]') == 'SetRect' for k in range(1, int(lua.eval('#WEB')) + 1))
        lua.execute('WEB = {}'); g.PrivateWarehouse_Open()
        assert not any(lua.eval(f'WEB[{k}][2]') == 'CreateWebView' for k in range(1, int(lua.eval('#WEB')) + 1)), 'web view kept at the same size'
        lua.execute('MARKETVIS = false'); g.AFUI_OnEvent('ADDON_TIMER')

        # Presets and user profiles.
        # Tooltips (ElvUI), two kinds in the game's tooltip_proto window: the core decides when the game
        # places it (mouse over a game window -> UI anchor, else world anchor). Unnamed pop-ups (start-menu
        # submenus, context menus share slot 0x1ec) are untouched.
        lua.execute('DIALOGS[0x1ec] = {name = "", x = 500, y = 500, w = 120 * UISCALE, h = 22 * UISCALE, shown = true}')
        lua.execute('DIALOGS[0x19d] = {name = "tooltip_proto_dialog", x = 100, y = 100, w = 332 * UISCALE, h = 126 * UISCALE, shown = false}')
        lua.execute('DIALOGS[0x19e] = {name = "tooltip_proto_skill_dialog", x = 100, y = 100, w = 285 * UISCALE, h = 126 * UISCALE, shown = false}')
        for _ in range(2):
            g.AFUI_OnEvent('ADDON_TIMER')
        pin = lua.eval('PINS[0x19d]')
        assert pin is not None and pin[1] == 3 and pin[4] == -1 and pin[7] is True, 'world: mover corner; UI: where the game puts it'
        assert lua.eval('PINS[0x1ec] == nil and AFUI.tooltips[0x1ec] == nil'), 'pop-up menus are no tooltips'
        assert lua.eval('PINS[0x19e] == nil'), 'skill tooltips stay native'
        g.DoSlashCmd('/moveui')
        for _ in range(2):
            g.AFUI_OnEvent('ADDON_TIMER')
        labels = [widgets['AFUIMove%d' % k].get('text') for k in range(1, 57) if widgets['AFUIMove%d' % k]['visible'] == 1]
        assert 'Tooltip (world)' in labels and 'Tooltip (UI)' not in labels and '' not in labels, labels
        g.DoSlashCmd('/moveui')
        for _ in range(2):
            g.AFUI_OnEvent('ADDON_TIMER')
        tab = [i + 1 for i in range(10) if widgets['AFUITab%d' % (i + 1)].get('text') == 'Tooltip'][0]
        g.AFUI.Click('Tab%d' % tab)
        assert widgets['AFUIRow2Label'].get('text') == 'World (NPCs, players)' and widgets['AFUIRow2Value'].get('text') == 'Mover'
        assert widgets['AFUIRow3Label'].get('text') == 'UI (buttons, items)' and widgets['AFUIRow3Value'].get('text') == 'Native'
        g.AFUI.Click('Row3Value')  # UI tooltips on their own mover
        g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('PINS[0x19d][4]') == 3 and lua.eval('PINS[0x19e][1]') == 3 and lua.eval('PINS[0x19e][4]') == 3, 'UI tooltips at the UI mover corner'
        g.AFUI.Click('Row3Less')   # back to Native
        g.AFUI.Click('Row2Value')  # world: Cursor
        g.AFUI.Click('Row5More')   # offset X +8
        g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('PINS[0x19d][1]') == 4 and abs(lua.eval('PINS[0x19d][2]') - 8 * scale) < 1e-6, 'beside the mouse, offset'
        g.AFUI.Click('Row1Value')  # tooltips off
        g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('PINS[0x19d][1]') == 5 and lua.eval('PINS[0x19d][4]') == 5, 'tooltips hidden'
        g.AFUI.Click('Row1Value'); g.AFUI.Click('Row2Less'); g.AFUI.Click('Row5Less')
        assert lua.eval("AFUI.Get('ttanchor')") == 1 and lua.eval("AFUI.Get('ttx')") == 0
        lua.execute('DIALOGS[0x1ec] = nil; DIALOGS[0x19d] = nil; DIALOGS[0x19e] = nil')
        g.AFUI_OnEvent('ADDON_TIMER')
        # Alt+Tab: while the game is not in front nothing is re-placed.
        lua.execute('DIALOGS[0x0b].x = 3')
        g.CX = False
        for _ in range(3):
            g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('AFUI.settled') is False
        g.CX = None
        g.AFUI_OnEvent('ADDON_TIMER')

        # Action bars: buttons per row and size for the bar chosen in "Layout of".
        g.AFUI.Click('Tab3')
        assert widgets['AFUIRow4Label'].get('text') == 'Layout of' and widgets['AFUIRow5Value'].get('text') == '12'
        for _ in range(6):
            g.AFUI.Click('Row5Less')   # 12 -> 6 per row
        g.AFUI.Click('Row6More')      # size 40 -> 42
        lua.execute('DIALOGS[0x10] = {name = "quickbar_dialog", x = 363 * UISCALE, y = 898 * UISCALE, w = 554 * UISCALE, h = 48 * UISCALE, shown = true}')
        g.AFUI_OnEvent('ADDON_TIMER')
        slot7 = lua.eval('PINS["16:shortcut7"]')
        assert slot7 is not None and abs(slot7[1]) < 1e-6 and abs(slot7[2] - 44 * scale) < 1e-6 and abs(slot7[5] - 42 * scale) < 1e-6, 'slot 7 starts row 2'
        assert abs(lua.eval('PINS["16:ctn_shortcut"][5]') - (6 * 44 - 2) * scale) < 1e-6 and lua.eval('PINS["16:ctn_shortcut"][7]') == 'container'
        assert abs(lua.eval('DIALOGS[0x10].y + DIALOGS[0x10].h') - 946 * scale) < 1e-6, 'bar keeps its bottom edge'
        for _ in range(6):
            g.AFUI.Click('Row5More')
        g.AFUI.Click('Row6Less')
        g.AFUI_OnEvent('ADDON_TIMER')
        assert abs(lua.eval('PINS["16:shortcut12"][1]') - 11 * 42 * scale) < 1e-6, 'back to one row (still held until relog)'
        lua.execute('DIALOGS[0x10] = nil')

        # World map: draggable by its frame, the spot it was left at comes back next time.
        lua.execute('DIALOGS[0x40] = {name = "new_map_dialog", x = 200, y = 100, w = 600, h = 500, shown = false}')
        g.AFUI_OnEvent('ADDON_TIMER')
        lua.execute('DIALOGS[0x40].shown = true')
        g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('MOVABLE[0x40]') is True, 'map movable'
        lua.execute('DIALOGS[0x40].x, DIALOGS[0x40].y = 320, 160')  # the player drags it
        for _ in range(21):
            g.AFUI_OnEvent('ADDON_TIMER')
        lua.execute('DIALOGS[0x40].shown = false'); g.AFUI_OnEvent('ADDON_TIMER')
        lua.execute('DIALOGS[0x40].x, DIALOGS[0x40].y = 0, 0; DIALOGS[0x40].shown = true'); g.AFUI_OnEvent('ADDON_TIMER')
        assert abs(lua.eval('DIALOGS[0x40].x') - 320) < 1 and abs(lua.eval('DIALOGS[0x40].y') - 160) < 1, 'map back where it was left'
        lua.execute('DIALOGS[0x40] = nil')

        # Movers: a click selects a frame for the nudge panel (exact position, one-pixel arrows); a
        # dragged box snaps to another frame's edge.
        g.DoSlashCmd('/moveui')
        for _ in range(2):
            g.AFUI_OnEvent('ADDON_TIMER')
        movers = {widgets[f'AFUIMove{i}']['text']: i for i in range(1, 57) if widgets[f'AFUIMove{i}']['visible'] == 1}
        t = movers['Target']
        bx, by = box(t)
        mouse(bx + 3, by + 3, False); mouse(bx + 3, by + 3, True); mouse(bx + 3, by + 3, False)
        assert widgets['AFUINudge']['visible'] == 1 and widgets['AFUINudgeName']['text'] == 'Target'
        g.AFUI.Nudge('Right')
        assert abs(box(t)[0] - (bx + 1)) < 1e-6 and abs(lua.eval('DIALOGS[0x0b].x') - (bx + 1)) < 1e-6, 'nudged one pixel'
        assert widgets['AFUINudgeXY']['text'].startswith('X %d' % int(bx + 1.5))
        p = movers['Minimap']
        px, py = box(p)
        tx = px + lua.eval(f'AFUI.movers[{p}].w') + 3 * scale  # 3 px right of the minimap's right edge
        lua.execute("AFUI.db.snap = 0; AFUI.db.snapframes = 1")
        drag(t, tx, py + 40 * scale)
        assert abs(box(t)[0] - (px + lua.eval(f'AFUI.movers[{p}].w'))) < 1e-6, 'snapped to the minimap edge'
        lua.execute("AFUI.db.snap = nil")
        g.DoSlashCmd('/moveui')
        for _ in range(2):
            g.AFUI_OnEvent('ADDON_TIMER')
        g.AFUI.Click('Tab%d' % (tab + 3))  # Profiles
        g.AFUI.Click('Row4Value')  # Minimal preset
        assert lua.eval("AFUI.Get('databar')") == 0 and lua.eval("AFUI.Get('profile')") == 'Minimal'
        g.AFUI.Click('Row5Value')  # save to User1
        assert lua.eval("AFUI.Get('profile')") == 'User1' and 'databar:0' in lua.eval('AFUI.stored.User1')

        # Logout hides everything; switching the state off hides it too.
        g.UnitName = lambda *x: None
        g.AFHUD.ticks = 0
        g.AFHUD_OnEvent('ADDON_TIMER')
        assert widgets['AFHTop']['visible'] == 0
        g.AFUI_OnEvent('ADDON_TIMER')
        assert lua.eval('next(PINS) == nil'), 'pins released when the interface is off'
        g.DoSlashCmd('/afcore')
        assert any(c[0] == 'chat' and 'Aetherfall Core: 1' in str(c[1][1]) for c in calls), '/afcore report'
        # No interface error reached the chat anywhere above (slash and timer handlers report them there).
        errors = [str(c[1][1]) for c in calls if c[0] == 'chat' and ('attempt to' in str(c[1][1]) or '.lua:' in str(c[1][1]))]
        assert not errors, errors[:3]
        assert any(c[0] == 'chat' and 'plugin: options window yes, movers yes, data bar yes' in str(c[1][1]) for c in calls), '/afcore diagnostics'
    print('OK: plugin XML uses only the vocabulary of loaded plugins; real Lua 5.1 widgets; four UI scales; layout-state gating, data bar, chat panel, no extra aura labels, 5-window budget, movable start-menu buttons, Broker and Interface buttons, Additional Functions without HUD entries, overlay movers dragged by mouse, options window (tabs, toggles, numbers, data texts), native show/hide, movers (drag, snap, per-frame reset, lock/save), presets and user profiles, start-menu entry, design positions over saved ones, diagnostics, logout, /afcore and shared timer preservation.')


if __name__ == '__main__':
    main()
