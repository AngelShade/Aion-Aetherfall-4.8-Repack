-- Aetherfall Interface options (/afui or /aetherfall, start menu) and movers (/moveui).
-- ElvUI-style settings applied live through the AetherfallCore bridge, which
-- moves, shows and hides the game's own native windows (native SetRect and
-- show/hide). Art and layout bases live in the launcher's Aetherfall Interface
-- switch (restart). Settings are saved per server/character in two alternating
-- bounded scalar files; neither file is ever executed.
require('sha1')

AFUI = {ready = false, unlocked = false, tab = 'General', ticks = 0, defaults = {}, applied = {}, movers = {}, text = {}}
local U = AFUI
local D = AFCore and AFCore.DIALOG or {}
-- The client gives all plugin windows together 20 dialog slots (Game.dll 0x635093 scans
-- 0x20e..0x221 and deletes a window that finds none). RelicCalc's own windows use 7, so
-- Aetherfall uses 5: data bar, status line, minimap label, options and the movers overlay
-- (its boxes are child widgets, so every frame gets one).
local MOVERS = 56
local PLUGIN_FIRST = 0x20e
local ROWS = 10
local GOLD, ICE, MIST, AETHER = '0.839,0.722,0.471,1', '0.875,0.914,0.941,1', '0.576,0.671,0.737,1', '0.561,0.890,0.925,1'

-- ---------- settings ----------
local DATA = {'zone', 'coords', 'time', 'xp', 'kinah', 'fps', 'group', 'legion', 'level', 'hp', 'off'}
local FONTS = {'Default', 'Option', 'Value', 'Title', 'Large title', 'Window', 'Name', 'Button'}  -- AetherfallHUD.lua FONTS
local THEMES = {'Aether', 'Gold', 'Ice', 'Ember', 'Jade'}  -- AetherfallHUD.lua THEME
local defaults = {
    databar = 1, chatline = 1, minimapinfo = 1, timers = 1, targettimers = 1, warn = 10,
    tot = 1, party = 1, xpbar = 1, startmenu = 1, altbar = 1, extrabar = 1, sepbar = 1,
    snap = 1, grid = 8, showgrid = 1, guidesize = 64, font = 1, theme = 1, alpha = 90,
    ttshow = 1, ttanchor = 1, ttui = 1, ttgrow = 1, ttx = 0, tty = 0, ttclamp = 1, snapframes = 1,
    abbar = 1, abrow1 = 12, abrow2 = 12, abrow3 = 12, abrow4 = 6, absize1 = 40, absize2 = 40, absize3 = 40, absize4 = 40,
    mapx = -1, mapy = -1,
    dl1 = 'zone', dl2 = 'coords', dl3 = 'time', dc1 = 'xp', dc2 = 'kinah', dc3 = 'off', dr1 = 'fps', dr2 = 'group', dr3 = 'legion',
    profile = 'DPS',
}
local NUMERIC = {warn = {0, 60, 1}, grid = {2, 32, 2}, guidesize = {16, 128, 16}, alpha = {30, 100, 10}, ttx = {-64, 64, 8}, tty = {-64, 64, 8},
                 abrow1 = {1, 12, 1}, abrow2 = {1, 12, 1}, abrow3 = {1, 12, 1}, abrow4 = {1, 12, 1},
                 absize1 = {28, 56, 2}, absize2 = {28, 56, 2}, absize3 = {28, 56, 2}, absize4 = {28, 56, 2},
                 mapx = {-1, 1, 0}, mapy = {-1, 1, 0}}
-- Tooltip anchor (ElvUI): on the Tooltip mover, growing away from one of its corners, or
-- at the game's cursor spot plus an offset.
local ANCHORS = {'Mover', 'Cursor', 'Native'}      -- world tooltips (NPCs, monsters, players)
local UI_ANCHORS = {'Native', 'Mover', 'Cursor'}    -- tooltips of buttons, items and skills
local BARS = {'Bar 1', 'Bar 2', 'Bar 3', 'Bar 4'}
local GROW = {'Bottom right', 'Bottom left', 'Top right', 'Top left'}
local GROW_MODE = {3, 2, 1, 0}  -- AFCore.Pin corner modes
local CHOICE = {font = FONTS, theme = THEMES, ttanchor = ANCHORS, ttui = UI_ANCHORS, ttgrow = GROW, abbar = BARS}
local PROFILES = {'DPS', 'Healer', 'Minimal', 'User1', 'User2', 'User3'}
local PRESET = {
    DPS = {},
    Healer = {party = 1, tot = 1, timers = 1, targettimers = 1},
    Minimal = {databar = 0, chatline = 0, minimapinfo = 0, targettimers = 0, altbar = 0, extrabar = 0, sepbar = 0, xpbar = 0},
}
-- Native windows whose visibility the interface may enforce off (engine shows them again otherwise).
local TOGGLES = {tot = 'target_assist', party = 'party', xpbar = 'exp_bar', startmenu = 'start',
                 altbar = 'shortcut_alt', extrabar = 'shortcut_extra', sepbar = {'shortcut_sep', 'shortcut_sep2'}}
-- Windows that are always up in the game: switching them back on shows them again. The
-- separated bars restore only if this controller hid them while shown; conditional
-- unit frames still reappear when the native game next needs them.
local ALWAYS_UP = {xpbar = true, startmenu = true, altbar = true, extrabar = true}

local function call(name, ...)
    local f = _G[name]; if type(f) ~= 'function' then return nil end
    local ok, a, b, c = pcall(f, ...); if ok then return a, b, c end
end
local function n(v) v = tonumber(v); if v and v == v and math.abs(v) < 1e15 then return v end end
local function w(id) return _G['AFUI' .. id] end
local function scale() return math.max(.5, math.min(2, n(call('GetUIScale')) or 1)) end
local function screen()
    local _, sw, sh = AFCore.Scale()
    return n(sw) or 1280 * scale(), n(sh) or 960 * scale()
end
function AFUI.Get(key) local v = U.db and U.db[key]; if v == nil then v = defaults[key] end; return v end

local function readFile(name)
    local s = AFCore.ReadFile(name)
    if not s or #s > 32768 or not s:match('\nEND\n$') then return end
    local seq = tonumber(s:match('^AFUI1 (%d+)\n')); if not seq then return end
    return seq, s
end
local function valid(key, v)
    local d = defaults[key]
    if NUMERIC[key] then
        local x = tonumber(v); local r = NUMERIC[key]
        if x and x >= r[1] and x <= r[2] then return x end
    elseif key == 'profile' then
        for _, p in ipairs(PROFILES) do if p == v then return v end end
    elseif type(d) == 'number' then
        if v == '0' or v == '1' then return tonumber(v) end
        if CHOICE[key] and tonumber(v) and tonumber(v) >= 1 and tonumber(v) <= #CHOICE[key] then return tonumber(v) end
    elseif type(d) == 'string' then
        for _, item in ipairs(DATA) do if item == v then return v end end
    end
end
local function playerIdentity()
    local name = call('UnitName', 'player')
    if type(name) ~= 'string' or name == '' then return end
    return tostring(call('GetServerName') or '') .. ':' .. name
end
local function load()
    U.db, U.positions, U.stored, U.bindings = {}, {}, {}, {}
    local identity = assert(playerIdentity(), 'character is not connected')
    U.file = 'ui-' .. sha1.sha1(identity)
    U.path = AFCore.ModPath(U.file)
    local best, body = 0, nil
    for slot = 0, 1 do local seq, s = readFile(U.file .. '.' .. slot .. '.cfg'); if seq and seq > best then best, body = seq, s end end
    U.sequence = best
    if not body then return end
    for k, v in body:gmatch('\n([%w%.]+)=([^\n]+)') do
        local binding = k:match('^bind%.(%w+%.%d+)$')
        local p, key = k:match('^(%w+)%.(%w+)$')
        if binding and v:match('^[%w_]+$') then U.bindings[binding] = v
        elseif p == 'set' and defaults[key] ~= nil then
            U.db[key] = valid(key, v)
        elseif p and key and tonumber(key) then
            -- position: profile.dialogId = fx,fy (fractions of the screen)
            local fx, fy = v:match('^([%-%d%.]+),([%-%d%.]+)$')
            fx, fy = tonumber(fx), tonumber(fy)
            if fx and fy and fx >= -1 and fy >= -1 and fx <= 1 and fy <= 1 then U.positions[p .. '.' .. key] = {fx, fy} end
        elseif p == 'store' and key then
            U.stored[key] = v:sub(1, 2048)  -- saved user profile snapshot (scalar list)
        end
    end
end
local function dirty() U.saveAt = U.ticks + 20 end
function AFUI.Save()
    -- MoveUI is a draft until Save / Lock. Autosave, exit and disconnect must
    -- never commit a partly arranged layout behind the Cancel button.
    if U.edit and not U.committing then return true end
    if not U.path then U.saveError = 'Settings are not ready to save.'; return false end
    local seq = (U.sequence or 0) + 1; local lines = {'AFUI1 ' .. seq}
    for k in pairs(defaults) do if U.db[k] ~= nil then lines[#lines + 1] = 'set.' .. k .. '=' .. tostring(U.db[k]) end end
    for k, p in pairs(U.positions) do lines[#lines + 1] = k .. '=' .. string.format('%.5f,%.5f', p[1], p[2]) end
    for k, name in pairs(U.bindings or {}) do if U.positions[k] then lines[#lines + 1] = 'bind.' .. k .. '=' .. name end end
    for k, v in pairs(U.stored) do lines[#lines + 1] = 'store.' .. k .. '=' .. v end
    local slot = '.' .. seq % 2 .. '.cfg'
    -- The client's Lua io cannot write; the bridge stores the file, Lua reads it back.
    local ok = pcall(function()
        assert(AFCore.SaveFile(U.file .. slot, table.concat(lines, '\n') .. '\nEND\n'))
        assert(readFile(U.file .. slot) == seq)
    end)
    if ok then U.saveAt = nil else U.saveAt = U.ticks + 100 end
    if ok then U.sequence = seq; U.saveError = nil else U.saveError = 'Could not save settings. Your layout is still open; try locking again.' end
    return ok
end

-- ---------- native window placement ----------
local function key(id) return AFUI.Get('profile') .. '.' .. id end
local function dialog(id)
    local name, x, y, wd, ht, shown, class, instance, flags = AFCore.Dialog(id)
    if not name then return end
    return {name = name, x = x, y = y, w = wd, h = ht, shown = shown, class = class, instance = instance, flags = flags}
end
local function nativeMovable(d) return d and d.flags and math.floor(d.flags / 8) % 2 == 1 end
local function identity(d) return d and (d.name .. ':' .. tostring(d.instance or d.class or '')) end
local function position(id, d)
    local k = key(id); local name = U.bindings and U.bindings[k]
    if d and name and name ~= d.name then return end
    return U.positions[k]
end
local function remember(id, d, x, y, sw, sh)
    U.positions[key(id)] = {x / sw, y / sh}
    if d and d.name:match('^[%w_]+$') then U.bindings[key(id)] = d.name end
    dirty()
end
-- Design positions (AetherfallLayout.lua, reference 1280x960 + alignment) in pixels.
local function design(id)
    local d = AFUI_DESIGN and AFUI_DESIGN[id]; if not d then return end
    local s = n((AFCore.Scale())) or 1
    local sw, sh = screen()
    local x = d[5] == 'center' and sw / 2 + (d[1] - 640) * s or d[5] == 'right' and sw - (1280 - d[1]) * s or d[1] * s
    local y = d[6] == 'bottom' and sh - (960 - d[2]) * s or d[6] == 'middle' and sh / 2 + (d[2] - 480) * s or d[2] * s
    return x, y
end
-- Once per login and per screen size, native HUD windows without a user position
-- go to the Aetherfall design position (the game may restore older saved ones).
-- Frames the game re-anchors each time they appear (target, target of target, cast bars,
-- target auras): held at their design spot while shown. Others are placed once, so a
-- window the player drags natively (chat) is not fought.
local REANCHORED = {}
for _, name in ipairs({'target', 'target_assist', 'target_delay', 'target_assist_delay', 'skill_delay', 'target_buff'}) do
    if D[name] then REANCHORED[D[name]] = true end
end
local HUD_IDS = {}
for _, name in ipairs({'basic_status', 'target', 'target_assist', 'party', 'radar', 'start', 'shortcut', 'shortcut_ctrl',
                      'shortcut_alt', 'shortcut_extra', 'shortcut_sep', 'shortcut_sep2', 'chat', 'chat_input', 'exp_bar',
                      'buff_status', 'debuff_status', 'target_buff', 'skill_delay', 'target_delay'}) do
    if D[name] then HUD_IDS[D[name]] = true end
end
-- Ordinary windows retain their own title-bar dragging after the editor closes.
local function owned(id, d) return REANCHORED[id] or (HUD_IDS[id] and not nativeMovable(d)) end
local function applyDesign()
    local sw, sh = screen(); local size = math.floor(sw) .. 'x' .. math.floor(sh)
    if U.designSize ~= size then U.designSize = size; U.designed = {} end
    for id in pairs(AFUI_DESIGN or {}) do
        -- Windows the pins hold (resized bars, the start menu, tooltips) are placed by holdPinned.
        if not U.positions[key(id)] and not (U.pins and U.pins[id]) then
            local x, y = design(id)
            local d = dialog(id)
            -- Only the window the design was made for (e.g. the second separated bar's id is inferred).
            if d and AFUI_DESIGN[id][7] and d.name ~= AFUI_DESIGN[id][7] then d = nil end
            if x and d and (d.shown or REANCHORED[id]) then
                U.defaults[id] = {x / sw, y / sh}
                if U.designed[id] ~= identity(d) or (REANCHORED[id] and d.shown and (math.abs(d.x - x) > 1 or math.abs(d.y - y) > 1)) then
                    AFCore.Move(id, x, y)
                end
                U.designed[id] = identity(d)
            end
        end
    end
end
-- Show/hide the native windows the options switch (off: kept hidden; back on: shown again).
U.hidden = {}
function AFUI.ApplyToggles()
    for setting, names in pairs(TOGGLES) do
        local off = AFUI.Get(setting) == 0
        local hidden = U.hidden[setting] or {}
        for _, name in ipairs(type(names) == 'table' and names or {names}) do
            local id = D[name]
            local d = id and dialog(id)
            -- The second separated-bar id is inferred; only its verified layout
            -- may be hidden/restored, never a different window using that slot.
            if setting == 'sepbar' and d and d.name ~= 'quickbar_dialog_sep' then d = nil end
            if d and off then
                if d.shown then hidden[id] = true; AFCore.SetShown(id, false) end
            elseif d and U.hidden[setting] and (ALWAYS_UP[setting] or (setting == 'sepbar' and hidden[id])) then
                if not d.shown then AFCore.SetShown(id, true) end
            end
        end
        U.hidden[setting] = off and hidden or nil
    end
end
-- Apply saved positions and visibility to native windows (engine may re-anchor them).
local function enforce()
    applyDesign()
    local sw, sh = screen()
    local size = tostring(sw) .. ':' .. tostring(sh)
    if U.positionSize ~= size then U.positionSize, U.observed = size, {} end
    AFUI.ApplyToggles()
    local held = U.pins or {}
    U.observed = U.observed or {}
    for id = 1, PLUGIN_FIRST - 1 do
        if HUD_IDS[id] or U.positions[key(id)] then
            local d = dialog(id); local p = d and position(id, d)
            local prev = U.observed[id]
            if d and (p or HUD_IDS[id]) then
                if not U.defaults[id] then
                    local dx, dy = design(id)
                    U.defaults[id] = dx and {dx / sw, dy / sh} or {d.x / sw, d.y / sh}
                end
                local fresh = not prev or prev.token ~= identity(d) or prev.profile ~= AFUI.Get('profile') or not prev.shown
                if p and d.shown and not held[id] and fresh then
                    local x = math.max(0, math.min(sw - d.w, p[1] * sw))
                    local y = math.max(0, math.min(sh - d.h, p[2] * sh))
                    if AFCore.Move(id, x, y) then d.x, d.y = x, y end
                elseif d.shown and not U.unlocked and not owned(id, d) and not fresh
                    and (math.abs(d.x - prev.x) > 1 or math.abs(d.y - prev.y) > 1) then
                    remember(id, d, d.x, d.y, sw, sh)
                end
                U.observed[id] = {token = identity(d), shown = d.shown, x = d.x, y = d.y, profile = AFUI.Get('profile')}
            else
                U.observed[id] = nil
            end
        end
    end
end

-- ---------- movers ----------
-- ElvUI-style movers in ONE overlay window (AFUIMovers): a named box for every frame at
-- once. The client gives all plugins only 20 windows and code cannot drag a plugin window,
-- so the core reads the mouse (AFCore.Cursor) and a box being dragged moves its native
-- window live. Shift+click resets a frame to its layout spot.
local function snap(v)
    if AFUI.Get('snap') ~= 1 then return v end
    local g = AFUI.Get('grid') * scale()
    return math.floor(v / g + .5) * g
end
local function movable(d, sw, sh)
    return d and nativeMovable(d) and d.shown and d.w > 0 and d.h > 0 and d.w < sw * .95 and d.h < sh * .95
end
local FRAMES = {
    {'basic_status', 'Player'}, {'target', 'Target'}, {'target_assist', 'Target of Target'}, {'party', 'Party'},
    {'radar', 'Minimap'}, {'start', 'Menu'}, {'shortcut', 'Action Bar 1'}, {'shortcut_ctrl', 'Action Bar 2'},
    {'shortcut_alt', 'Action Bar 3'}, {'shortcut_extra', 'Action Bar 4'}, {'shortcut_sep', 'Action Bar 5'},
    {'shortcut_sep2', 'Action Bar 6'},
    {'chat', 'Chat'}, {'chat_input', 'Chat Input'}, {'exp_bar', 'Experience Bar'}, {'buff_status', 'Buffs'},
    {'debuff_status', 'Debuffs'}, {'target_buff', 'Target Auras'}, {'skill_delay', 'Cast Bar'},
    {'target_delay', 'Target Cast Bar'},
}
-- Open game windows that follow another frame or are not worth a mover, and friendlier names.
local SKIP_MOVERS = {party_buff = true, new_map = true}  -- party buff rows follow the party frames; the map drags itself
-- Tooltips: the game's text tooltip has no layout name; item and skill tooltips are the
-- tooltip_proto windows. They share the Tooltip anchor mover (TOOLTIP, not a game window id).
-- Two kinds, as in ElvUI: world tooltips (NPCs, monsters and players under the mouse) and UI
-- tooltips (buttons, items, skills), each with its own anchor. The game shows both in its
-- tooltip_proto windows (the NPC one adds Altitude / Distance), so the kind is where the mouse is:
-- over a game window it is a UI tooltip, over the world a world tooltip. The skill tooltip
-- (tooltip_proto_skill) is always UI. Unnamed windows are never tooltips: Game.dll gives slot 0x1ec
-- to about sixty pop-ups (start-menu submenus, context menus), which must stay where they open.
local TOOLTIP, TOOLTIP_UI = 0x7f0, 0x7f1
local function tooltipKind(d)
    local name = tostring(d.name)
    if name:match('^tooltip_proto_skill') then return 'ui' end
    if name:match('^tooltip_proto') then return 'context' end
end
local function isTooltip(d) return tooltipKind(d) ~= nil end
local function tooltipBox() local s = scale(); return 240 * s, 100 * s end
local function tooltipAnchor(which)
    local sw, sh = screen(); local bw, bh = tooltipBox()
    local p = U.positions[key(which or TOOLTIP)]
    if p then return p[1] * sw, p[2] * sh end
    if which == TOOLTIP_UI then return 16 * scale(), sh - bh - 380 * scale() end  -- above the chat
    return sw - bw - 16 * scale(), sh - bh - 240 * scale()  -- above the right end of the action bars
end
-- Start-menu buttons, each on its own mover (ElvUI-style). The menu window is made click-through
-- and kept just large enough for its buttons wherever they are; the core pins the window and each
-- button, so the game's and the market-shortcut mod's own placement keep them there. Spots are
-- saved under MENU_ID + index (no game window has those ids). AFUI_MENU (AetherfallLayout.lua):
-- each button's layout spot inside the menu, the interface's Broker and Interface buttons included.
local MENU_ID = 0x800
local MENU_BUTTONS = {
    {'fly_gauge', 'Flight', 'flygauge'}, {'start_button', 'Start', 'button'}, {'prophet_button', 'Influence', 'button'},
    {'btn_radar_map', 'Map', 'button'}, {'ingame_web', 'Web', 'button'}, {'companion_menu_button', 'Companions', 'button'},
    {'season_pass_button', 'Pass', 'button'}, {'journey_menu_button', 'Journey', 'button'},
    {'wardrobe_menu_button', 'Wardrobe', 'button'}, {'central_market_button', 'Market', 'button'},
    {'item_shop_container', 'Shop', 'container'},
    {'mail_notice', 'Mail', 'button', {'mail_full_notice', 'express_mail_notice', 'cash_mail_notice'}},
    -- The interface's own (the core runs "/say .broker" and "/afui" from the start menu's handler).
    {'aetherfall_broker_button', 'Broker', 'button'}, {'aetherfall_menu_button', 'Interface', 'button'},
}
local function menuManaged()
    local d = D.start and AFUI_MENU and dialog(D.start)
    return d and d.name == 'start_dialog' and d or nil
end
-- Absolute spot of button k: its mover's, else its layout spot in the menu (where the old Menu
-- mover or the layout put the menu).
local function buttonSpot(k, sw, sh)
    local s = n((AFCore.Scale())) or 1
    local f = AFUI_MENU and MENU_BUTTONS[k] and AFUI_MENU[MENU_BUTTONS[k][1]]
    if not f then return end
    local p = U.positions[key(MENU_ID + k)]
    if p then return p[1] * sw, p[2] * sh, f[3] * s, f[4] * s end
    local ox, oy
    local m = D.start and U.positions[key(D.start)]
    if m then ox, oy = m[1] * sw, m[2] * sh else ox, oy = design(D.start) end
    if not ox then return end
    return ox + f[1] * s, oy + f[2] * s, f[3] * s, f[4] * s
end
-- Additional Functions menu: entries that have a button on the HUD while the interface is on are
-- left out (RelicCalc registers them from its OnLoad, after every plugin file has run).
local ON_HUD = {['/companions'] = true, ['/seasonpass'] = true, ['/journey'] = true, ['/wardrobe'] = true,
                ['/privatewarehouse'] = true, ['/say .broker'] = true, ['/afui'] = true}
if type(RegisterMenu) == 'function' and not AFUI.NativeRegisterMenu then
    AFUI.NativeRegisterMenu = RegisterMenu
    RegisterMenu = function(label, command, icon, ...)
        if ON_HUD[tostring(command)] and AFHUD_Active and AFHUD_Active() then AFUI.leftOut = (AFUI.leftOut or 0) + 1; return end
        return AFUI.NativeRegisterMenu(label, command, icon, ...)
    end
end
local OTHER_LABELS = {quest_indicator = 'Quest Tracker', subzone_name = 'Zone Name', help_indicator = 'Notifications',
                      move_state = 'Movement', gm_indicator = 'GM Indicator', event_shortcut = 'Event Bar',
                      skill_penalty = 'Skill Penalty', tooltip = 'Tooltip', tooltip_skill = 'Skill Tooltip',
                      general_delay = 'Progress Bar', enchant_delay = 'Enchant Bar', target_assist_delay = 'Target of Target Cast Bar'}
-- Always offered (by layout name, the id is not fixed): the notification icons ("Daily quest has arrived").
local NAMED_MOVERS = {help_indicator = true, general_delay = true, enchant_delay = true, target_assist_delay = true}
-- Every HUD frame (shown or hidden) and other open game windows, in a fixed order.
local function moverCandidates()
    local sw, sh = screen(); local list, seen = {}, {}
    for _, f in ipairs(FRAMES) do
        local id = D[f[1]]
        local d = id and dialog(id)
        local ref = id and AFUI_DESIGN and AFUI_DESIGN[id]
        if not d and ref then
            local x, y = design(id); local s = n((AFCore.Scale())) or 1
            d = {name = ref[7], x = x, y = y, w = ref[3] * s, h = ref[4] * s, shown = false, virtual = true}
        end
        if d and ref and d.name ~= ref[7] then d = nil end
        if d and f[1] == 'shortcut_sep2' and d.name ~= 'quickbar_dialog_sep' then d = nil end
        if d and f[1] == 'start' and menuManaged() then seen[id] = true; d = nil end  -- each button has its own mover
        if d then list[#list + 1] = {id, d, f[2]}; seen[id] = true end
    end
    for id = 1, PLUGIN_FIRST - 1 do
        local d = not seen[id] and dialog(id)
        local name = d and tostring(d.name):gsub('_dialog$', '')
        local wanted = d and (movable(d, sw, sh) or (NAMED_MOVERS[name] and d.w >= 8 and d.h >= 6 and d.w < sw * .95 and d.h < sh * .95)
                              or (nativeMovable(d) and U.seen[id] == d.name and d.w > 0 and d.h > 0 and d.w < sw * .6 and d.h < sh * .6))
        -- Unnamed windows are pop-ups (submenus, context menus): they open where they are needed.
        if wanted and name ~= '' and not SKIP_MOVERS[name] and not isTooltip(d) then
            list[#list + 1] = {id, d, OTHER_LABELS[name] or (name:gsub('_', ' '))}
        end
    end
    return list
end
-- Where a frame is, or for a hidden frame where it will appear (saved spot, else layout spot).
local function auraBounds(id, d)
    if id ~= D.target_buff then return end
    local x0, y0, x1, y1 = math.huge, math.huge, -math.huge, -math.huge
    local first
    for _, prefix in ipairs({'buff_item', 'debuff_item'}) do
        for i = 1, 20 do
            local x, y, wd, ht, shown = AFCore.Rect(id, prefix .. i, 'itemview')
            if x and wd > 0 and ht > 0 then
                if not first then first = {x - d.x, y - d.y, wd, ht} end
                local typ, template = AFCore.Slot(id, i, prefix)
                if shown and typ and typ > 0 and template and template > 0 then
                    x0, y0, x1, y1 = math.min(x0, x - d.x), math.min(y0, y - d.y), math.max(x1, x - d.x + wd), math.max(y1, y - d.y + ht)
                end
            end
        end
    end
    U.auraFootprint = U.auraFootprint or {}
    if x0 < math.huge then
        U.auraFootprint[id] = {x0, y0, x1 - x0, y1 - y0}
        return x0, y0, x1 - x0, y1 - y0
    end
    local f = (not d.shown and U.auraFootprint[id]) or first
    -- The active English target-aura layout has 22px itemviews at 1,1,
    -- including before the native zero-sized canvas has been created.
    f = f or {scale(), scale(), 22 * scale(), 22 * scale()}
    return unpack(f)
end
local function frameRect(id, d)
    local s = scale(); local sw, sh = screen()
    local x, y = d.x, d.y
    if not d.shown then
        local p = position(id, d)
        if p then x, y = p[1] * sw, p[2] * sh else local dx, dy = design(id); if dx then x, y = dx, dy end end
    end
    local ox, oy, wd, ht = auraBounds(id, d)
    if ox then return x + ox, y + oy, wd, ht, ox, oy end
    return x, y, math.max(s, d.w), math.max(s, d.h), 0, 0
end
local function boxSize(mv)
    return mv.w, mv.h
end
local function panel(name)
    for id = PLUGIN_FIRST, 0x221 do
        local d = dialog(id)
        if d and d.name == 'AFUI' .. name then return id, d end
    end
end
local function showPanel(name, on)
    -- Plugin windows must use the client's queued widget operations. Directly
    -- changing their native flags/rectangles bypasses that ordering while the
    -- renderer may still be consuming the previous window state.
    if on then w(name):Show() else w(name):Hide() end
end
local function placeBox(i)
    local mv = U.movers[i]; local s = scale(); local bw, bh = boxSize(mv)
    w('Move' .. i):SetRect(mv.x / s, mv.y / s, bw / s, bh / s)
end
local hideNudge, releasePins  -- editor and native pin cleanup (defined below)
local function layoutMovers()
    local sw, sh = screen(); local s = scale()
    local selectedId = U.selected and U.movers[U.selected] and U.movers[U.selected].id
    U.movers = {}
    w('Movers'):SetRect(0, 0, sw / s, sh / s)
    for i, c in ipairs(moverCandidates()) do
        if i > MOVERS then break end
        local id, d, label = c[1], c[2], c[3]
        if not U.defaults[id] then
            local dx, dy = design(id)
            U.defaults[id] = dx and {dx / sw, dy / sh} or {d.x / sw, d.y / sh}
        end
        local x, y, wd, ht, ox, oy = frameRect(id, d)
        local thinXP = ht < 16 * s or wd < 90 * s
        U.movers[i] = {id = id, x = x, y = y, w = wd, h = ht, ox = ox, oy = oy, label = label, exact = true, virtual = d.virtual}
        placeBox(i)
        -- A full text line cannot fit inside the XP strip. Its hover label and
        -- selected-frame nudge panel identify it without enlarging the hit box.
        w('Move' .. i):SetText(thinXP and '' or (label .. (d.shown and '' or ' (hidden)')))
        w('Move' .. i):SetTooltip(label .. ' - select for position arrows')
        w('Move' .. i):Show()
    end
    for _, t in ipairs({{TOOLTIP, AFUI.Get('ttanchor') == 1, 'Tooltip (world)'}, {TOOLTIP_UI, AFUI.Get('ttui') == 2, 'Tooltip (UI)'}}) do
        if t[2] and #U.movers < MOVERS then
            local i = #U.movers + 1; local x, y = tooltipAnchor(t[1]); local bw, bh = tooltipBox()
            U.movers[i] = {id = t[1], x = x, y = y, w = bw, h = bh, label = t[3]}
            placeBox(i); w('Move' .. i):SetText(t[3]); w('Move' .. i):Show()
        end
    end
    for k = 1, menuManaged() and #MENU_BUTTONS or 0 do  -- last: small boxes on top of the large ones
        local x, y, bw, bh = buttonSpot(k, sw, sh)
        if x and #U.movers < MOVERS then
            local i = #U.movers + 1
            U.movers[i] = {id = MENU_ID + k, x = x, y = y, w = bw, h = bh, exact = true, label = MENU_BUTTONS[k][2]}
            placeBox(i); w('Move' .. i):SetText(MENU_BUTTONS[k][2]); w('Move' .. i):Show()
        end
    end
    for i = #U.movers + 1, MOVERS do w('Move' .. i):Hide() end
    U.selected = nil
    for i, mv in ipairs(U.movers) do if mv.id == selectedId then U.selected = i end end
    if U.edit then AFUI.CaptureEditFrames() end
    if AFUI.EditorLayout then AFUI.EditorLayout() else hideNudge() end
    showPanel('Movers', true)
end
-- Edges within a few pixels of another frame's edges line up with them (ElvUI snapping).
local function snapToFrames(i, tx, ty)
    if AFUI.Get('snapframes') ~= 1 then return tx, ty end
    local mv = U.movers[i]; local bw, bh = boxSize(mv); local near = 6 * scale()
    local bx, by = math.huge, math.huge
    for j, o in ipairs(U.movers) do
        if j ~= i and not o.editorHidden then
            local ow, oh = boxSize(o)
            for _, c in ipairs({{o.x, 0}, {o.x + ow, 0}, {o.x, bw}, {o.x + ow, bw}, {o.x + ow / 2, bw / 2}}) do
                local d = math.abs(tx + c[2] - c[1]); if d < near and d < bx then bx = d; mv.snapx = c[1] - c[2] end
            end
            for _, c in ipairs({{o.y, 0}, {o.y + oh, 0}, {o.y, bh}, {o.y + oh, bh}, {o.y + oh / 2, bh / 2}}) do
                local d = math.abs(ty + c[2] - c[1]); if d < near and d < by then by = d; mv.snapy = c[1] - c[2] end
            end
        end
    end
    return bx < math.huge and mv.snapx or tx, by < math.huge and mv.snapy or ty
end
-- Put mover i (and its window) at x, y in pixels and remember it.
local function moveMover(i, tx, ty)
    local mv = U.movers[i]; local sw, sh = screen()
    tx, ty = math.max(0, math.min(sw - mv.w, tx)), math.max(0, math.min(sh - mv.h, ty))
    if math.abs(tx - mv.x) < .5 and math.abs(ty - mv.y) < .5 then return end
    local rx, ry = tx - (mv.ox or 0), ty - (mv.oy or 0)
    local d = mv.id < PLUGIN_FIRST and dialog(mv.id)
    -- Publish the draft before entering native placement. SetRect can raise a
    -- visibility/target event synchronously; its hold pass must see the new
    -- position, rather than restoring the preceding saved/default anchor.
    local k = key(mv.id); local previous, binding, saveAt = U.positions[k], U.bindings[k], U.saveAt
    remember(mv.id, d, rx, ry, sw, sh)
    local placed = true
    if d then
        -- These frames can be re-anchored by the game between drag samples.
        -- Pin immediately (also retargeting a refreshed native instance), so
        -- the first placement after each sample already holds the draft spot.
        if REANCHORED[mv.id] then placed = AFCore.Pin(mv.id, rx, ry)
        else placed = AFCore.Move(mv.id, rx, ry) end
    end
    if not placed then
        U.positions[k], U.bindings[k], U.saveAt = previous, binding, saveAt
        if not U.moveError then DisplayChatMessage(0, 'Aetherfall: this window could not be moved; its saved position was kept.', .91, .63, .56) end
        U.moveError = mv.label; return false
    end
    if d and REANCHORED[mv.id] then U.pins[mv.id] = true end
    mv.x, mv.y = tx, ty; placeBox(i)
    if U.selected == i then AFUI.ShowNudge() end
end
AFUI.MoveMover = moveMover
-- All editor controls are children of the existing queued plugin overlay.
local EDITOR_PARTS = {
    EditorPanel={0,0,380,278}, EditorTitle={10,4,158,22}, EditorSave={174,3,116,24}, EditorCancel={296,3,74,24},
    EditorProfile={10,31,194,20}, EditorStep={212,29,158,24}, EditorGrid={10,55,74,24}, EditorLess={88,55,24,24}, EditorSpacing={114,55,52,24},
    EditorMore={168,55,24,24}, EditorSnap={198,55,80,24}, EditorSticky={284,55,86,24},
    EditorFilter={10,83,100,24}, EditorHide={114,83,62,24}, EditorRestore={180,83,98,24}, EditorResetAll={282,83,88,24},
    EditorPrevious={10,113,24,24}, EditorNext={298,113,24,24}, EditorSettings={326,113,44,24}, NudgeName={40,115,254,22},
    EditorOrigin={10,143,136,24}, EditorXLabel={150,145,14,20}, EditorX={166,143,60,24},
    EditorYLabel={230,145,14,20}, EditorY={246,143,60,24}, EditorApply={312,143,58,24},
    NudgeLeft={10,173,24,24}, NudgeUp={38,173,24,24}, NudgeDown={66,173,24,24}, NudgeRight={94,173,24,24},
    EditorCenterH={124,173,76,24}, EditorCenterV={206,173,76,24}, NudgeReset={288,173,82,24},
    NudgeXY={10,203,360,20}, EditorStatus={10,226,360,20}, EditorHelp={10,250,360,20},
}
local ORIGINS = {{'Top left',0,0}, {'Top',.5,0}, {'Top right',1,0}, {'Left',0,.5}, {'Center',.5,.5},
                 {'Right',1,.5}, {'Bottom left',0,1}, {'Bottom',.5,1}, {'Bottom right',1,1}}
local FILTERS = {'All frames', 'HUD', 'Action bars', 'Menus', 'Windows'}
local function copy(t)
    local result = {}; for k, v in pairs(t or {}) do result[k] = type(v) == 'table' and copy(v) or v end; return result
end
local function editorStatus(text)
    U.editorStatus = text
    if w('EditorStatus') then w('EditorStatus'):SetText(text or 'Save before travel. Cancel restores your layout.') end
end
local function guide(name, x, y, wd, ht, alpha)
    local widget = w(name); if not widget then return end
    U.guideReady = U.guideReady or {}
    if not U.guideReady[name] then widget:SetUIImage(0, 'Textures/UI/AetherfallMovers/line'); widget:SetAlpha(alpha); U.guideReady[name] = true end
    local s = scale(); widget:SetRect(x / s, y / s, math.max(1, wd) / s, math.max(1, ht) / s); widget:Show()
end
local function editorGrid()
    if not w('GridX1') then return end
    local sw, sh = screen(); local s = scale()
    local step = AFUI.Get('guidesize') * s
    -- At unusually wide viewports display whole multiples of the chosen cell,
    -- keeping the bounded pool complete instead of dropping lines at an edge.
    step = step * math.max(1, math.ceil(sw / (65 * step)), math.ceil(sh / (41 * step)))
    U.guideStep = step
    for _, axis in ipairs({{'X',64,sw}, {'Y',40,sh}}) do
        for i = 1, axis[2] do
            local pos = math.floor(i * step + .5)
            if AFUI.Get('showgrid') == 1 and pos < axis[3] then
                guide('Grid' .. axis[1] .. i, axis[1] == 'X' and pos or 0, axis[1] == 'Y' and pos or 0,
                      axis[1] == 'X' and 1 or sw, axis[1] == 'Y' and 1 or sh, .22)
            else w('Grid' .. axis[1] .. i):Hide() end
        end
    end
    if AFUI.Get('showgrid') == 1 then
        guide('GuideCenterX', math.floor(sw / 2), 0, 2, sh, .65)
        guide('GuideCenterY', 0, math.floor(sh / 2), sw, 2, .65)
    else w('GuideCenterX'):Hide(); w('GuideCenterY'):Hide() end
end
local function editorCategory(mv)
    if mv.id > MENU_ID and mv.id <= MENU_ID + #MENU_BUTTONS then return 'Menus' end
    if tostring(mv.label):match('^Action Bar') then return 'Action bars' end
    return (HUD_IDS[mv.id] or REANCHORED[mv.id] or mv.id == TOOLTIP or mv.id == TOOLTIP_UI) and 'HUD' or 'Windows'
end
local function editorBoxes()
    local filter = FILTERS[U.editorFilter or 1]
    U.hiddenMovers = U.hiddenMovers or {}
    for i, mv in ipairs(U.movers) do
        mv.editorHidden = U.hiddenMovers[mv.id] or (filter ~= 'All frames' and editorCategory(mv) ~= filter)
        if mv.editorHidden then w('Move' .. i):Hide() else w('Move' .. i):Show() end
        w('Move' .. i):SetAlpha(U.selected == i and 1 or .65)
    end
end
local function placeEditor()
    if not w('EditorPanel') then return end
    local sw, sh = screen(); local s = scale()
    local x = math.max(0, math.min(sw / s - 380, U.editorX or (sw / s - 380) / 2))
    local y = math.max(0, math.min(sh / s - 278, U.editorY or 24))
    U.editorX, U.editorY = x, y
    for name, r in pairs(EDITOR_PARTS) do w(name):SetRect(x + r[1], y + r[2], r[3], r[4]); w(name):Show() end
    w('EditorTitle'):SetAttr('textColor', GOLD); w('EditorSave'):SetAttr('textColor', GOLD)
    w('NudgeName'):SetAttr('textColor', AETHER)
end
local function onNudge(cx, cy)
    local s = scale()
    return w('EditorPanel') and cx >= (U.editorX or 0) * s and cx <= ((U.editorX or 0) + 380) * s
        and cy >= (U.editorY or 0) * s and cy <= ((U.editorY or 0) + 278) * s
end
function AFUI.ShowNudge()
    local mv = U.selected and U.movers[U.selected]
    if not w('EditorPanel') then return end
    w('NudgeName'):SetText(mv and tostring(mv.label) or 'Select a frame')
    local o = ORIGINS[U.editorOrigin or 1]; local sw, sh = screen()
    w('EditorOrigin'):SetText('From: ' .. o[1])
    w('EditorX'):SetText(mv and tostring(math.floor(mv.x + mv.w * o[2] - sw * o[2] + .5)) or '')
    w('EditorY'):SetText(mv and tostring(math.floor(mv.y + mv.h * o[3] - sh * o[3] + .5)) or '')
    w('NudgeXY'):SetText(mv and string.format('Position %d, %d   |   Size %d x %d px', mv.x, mv.y, mv.w, mv.h) or 'Select a box, or use the frame selector above.')
    for i = 1, 4 do w('Selection' .. i):Hide() end
    if mv and not mv.editorHidden then
        guide('Selection1', mv.x, mv.y, mv.w, 2, 1); guide('Selection2', mv.x, mv.y + mv.h - 2, mv.w, 2, 1)
        guide('Selection3', mv.x, mv.y, 2, mv.h, 1); guide('Selection4', mv.x + mv.w - 2, mv.y, 2, mv.h, 1)
    end
end
hideNudge = function()
    for i = 1, 4 do if w('Selection' .. i) then w('Selection' .. i):Hide() end end
end
function AFUI.EditorLayout()
    placeEditor(); editorGrid(); editorBoxes(); AFUI.ShowNudge()
    w('EditorProfile'):SetText('Profile: ' .. AFUI.Get('profile'))
    w('EditorStep'):SetText('Snap step: ' .. AFUI.Get('grid') .. ' px')
    w('EditorStep'):SetTooltip('Snap step in UI pixels: click +2, Shift+click -2 (2 to 32). The visible grid has its own spacing.')
    for name, setting in pairs({Grid='showgrid', Snap='snap', Sticky='snapframes'}) do
        w('Editor' .. name):SetText(name .. ': ' .. (AFUI.Get(setting) == 1 and 'On' or 'Off'))
    end
    w('EditorSpacing'):SetText(string.format('%d px', U.guideStep / scale()))
    w('EditorSpacing'):SetTooltip('Visible grid spacing in UI pixels. The center lines mark the screen center.')
    w('EditorSnap'):SetTooltip('Snap dragged boxes to the ' .. AFUI.Get('grid') .. ' UI-pixel grid. Change the step in Movers settings.')
    w('EditorSticky'):SetTooltip('Snap to other frames\' edges and centers.')
    w('EditorFilter'):SetText(FILTERS[U.editorFilter or 1])
    w('EditorOrigin'):SetTooltip('Coordinate reference only. X/Y are physical pixel offsets from this point of the screen to the same point of the box.')
    w('EditorSettings'):SetTooltip('Save / Lock, then open the selected frame\'s settings.')
    editorStatus(U.editorStatus)
end
function AFUI.CaptureEditFrames()
    if not U.edit then return end
    local sw, sh = screen()
    for _, mv in ipairs(U.movers) do
        local d = mv.id < PLUGIN_FIRST and dialog(mv.id)
        if d and not U.edit.frames[mv.id] then U.edit.frames[mv.id] = {d.name, d.x / sw, d.y / sh} end
    end
end
function AFUI.EndEdit(save, quiet, defer)
    if not U.edit then return true end
    if save then
        U.committing = true; local ok = AFUI.Save(); U.committing = nil
        if not ok then editorStatus('Save failed. Layout stays open; try Save again.'); return false end
    else
        local e = U.edit
        releasePins()
        U.db, U.positions, U.bindings, U.stored = e.db, e.positions, e.bindings, e.stored
        U.saveAt, U.saveError = e.saveAt, nil
        U.restoreEdit = {identity=U.identity, frames=e.frames}
        if not defer then AFUI.RestoreEditFrames() end
        U.designed, U.observed, U.checked, U.fitted, U.designSize = {}, {}, {}, {}, nil
    end
    U.edit, U.unlocked, U.drag, U.panelDrag, U.selected = nil, false, nil, nil, nil
    U.hiddenMovers, U.editorStatus, U.resetConfirm, U.refreshMovers = {}, nil, nil, nil
    showPanel('Movers', false); U.movers = {}
    if not defer and AFHUD_Apply then AFHUD_Apply() end
    if not defer and U.settled and AFHUD and AFHUD.on then pcall(AFUI.HoldPinned) end
    -- Once locked, ordinary native windows retain normal title-bar dragging.
    for id = 1, PLUGIN_FIRST - 1 do
        local d = U.positions[key(id)] and dialog(id)
        if d and not owned(id, d) then
            U.observed = U.observed or {}
            U.observed[id] = {token=identity(d), shown=d.shown, x=d.x, y=d.y, profile=AFUI.Get('profile')}
        end
    end
    if not quiet then DisplayChatMessage(0, save and 'Aetherfall layout saved and locked.' or 'Aetherfall layout changes cancelled.', .56, .89, .93) end
    AFUI.Refresh(); return true
end
function AFUI.RestoreEditFrames()
    local restore = U.restoreEdit; if not restore then return end
    if restore.identity == U.identity then
        local sw, sh = screen()
        for id, p in pairs(restore.frames) do
            local d = dialog(id)
            if d and d.name == p[1] then AFCore.Move(id, p[2] * sw, p[3] * sh) end
        end
    end
    U.restoreEdit = nil
end
function AFUI.Editor(action)
    g_AddonName = 'RelicCalc'
    if not U.unlocked or not U.edit or not U.settled or AFCore.Cursor() == nil then return end
    U.drag, U.wasDown, U.panelDrag = nil, true, nil
    if action == 'Save' or action == 'Cancel' then AFUI.EndEdit(action == 'Save'); return end
    if action ~= 'ResetAll' then U.resetConfirm = nil; editorStatus() end
    local mv = U.selected and U.movers[U.selected]
    if action == 'Grid' or action == 'Snap' or action == 'Sticky' then
        local k = ({Grid='showgrid', Snap='snap', Sticky='snapframes'})[action]; U.db[k] = 1 - AFUI.Get(k); dirty()
    elseif action == 'More' or action == 'Less' then
        local size = AFUI.Get('guidesize') + (action == 'More' and 16 or -16)
        U.db.guidesize = math.max(16, math.min(128, size)); dirty()
    elseif action == 'Step' then
        local _, _, _, shift = AFCore.Cursor()
        U.db.grid = math.max(2, math.min(32, AFUI.Get('grid') + (shift and -2 or 2))); dirty()
    elseif action == 'Filter' then U.editorFilter = (U.editorFilter or 1) % #FILTERS + 1
    elseif action == 'Hide' and mv then U.hiddenMovers[mv.id] = true; U.selected = nil
    elseif action == 'Restore' then U.hiddenMovers = {}; U.editorFilter = 1
    elseif action == 'Previous' or action == 'Next' then
        local dir = action == 'Next' and 1 or -1
        for step = 1, #U.movers do
            local i = ((U.selected or (dir == 1 and 0 or 1)) - 1 + step * dir) % #U.movers + 1
            if not U.movers[i].editorHidden then U.selected = i; break end
        end
    elseif action == 'Origin' then U.editorOrigin = (U.editorOrigin or 1) % #ORIGINS + 1
    elseif action == 'Apply' and mv then
        local x, y = n(w('EditorX'):GetText()), n(w('EditorY'):GetText())
        if not x or not y then editorStatus('Enter a number for both X and Y.'); return end
        local sw, sh = screen(); local o = ORIGINS[U.editorOrigin or 1]
        moveMover(U.selected, x + (sw - mv.w) * o[2], y + (sh - mv.h) * o[3])
    elseif (action == 'CenterH' or action == 'CenterV') and mv then
        local sw, sh = screen(); moveMover(U.selected, action == 'CenterH' and (sw - mv.w) / 2 or mv.x, action == 'CenterV' and (sh - mv.h) / 2 or mv.y)
    elseif action == 'ResetAll' then
        if U.resetConfirm and U.ticks <= U.resetConfirm then AFUI.ResetAll(); U.resetConfirm = nil; editorStatus('All positions reset. Save to keep; Cancel to undo.')
        else U.resetConfirm = U.ticks + 100; editorStatus('Reset every position? Click Reset all again.') end
    elseif action == 'Settings' then
        local tab = mv and (editorCategory(mv) == 'Action bars' and 'Action Bars' or mv.id == D.radar and 'Minimap'
                    or (mv.id == TOOLTIP or mv.id == TOOLTIP_UI) and 'Tooltip' or 'Movers') or 'Movers'
        if AFUI.EndEdit(true) then U.tab = tab; AFUI.Open() end; return
    elseif action == 'Reset' or action == 'Left' or action == 'Up' or action == 'Down' or action == 'Right' then AFUI.Nudge(action)
    end
    AFUI.EditorLayout()
end
function AFUI.Nudge(dir)
    g_AddonName = 'RelicCalc'
    local i = U.selected; local mv = i and U.movers[i]; if not mv then return end
    if dir == 'Reset' then AFUI.ResetMover(i); AFUI.ShowNudge(); return end
    local _, _, _, shift = AFCore.Cursor()
    local step = shift and 10 or 1
    local dx = dir == 'Left' and -step or dir == 'Right' and step or 0
    local dy = dir == 'Up' and -step or dir == 'Down' and step or 0
    moveMover(i, mv.x + dx, mv.y + dy)
end
-- Each tick while unlocked: start, follow or end a drag from the core's mouse reading.
local function dragMovers()
    local cx, cy, down, shift = AFCore.Cursor()
    cx, cy = n(cx), n(cy)
    if not cx then U.drag, U.panelDrag = nil, nil; return end
    if U.panelDrag then
        if down then U.editorX, U.editorY = cx / scale() - U.panelDrag.dx, cy / scale() - U.panelDrag.dy; placeEditor()
        else U.panelDrag = nil end
        U.wasDown = down and true or false; return
    end
    if down and not U.wasDown and onNudge(cx, cy) then
        local x, y = cx / scale() - U.editorX, cy / scale() - U.editorY
        if x < 170 and y < 28 then U.panelDrag = {dx=x,dy=y} end
    end
    local drag = U.drag
    if down and not drag and not U.wasDown and not onNudge(cx, cy) then
        for i = #U.movers, 1, -1 do  -- topmost box first
            local mv = U.movers[i]; local bw, bh = boxSize(mv)
            if not mv.editorHidden and cx >= mv.x and cx <= mv.x + bw and cy >= mv.y and cy <= mv.y + bh then
                U.selected = i
                if shift then AFUI.ResetMover(i) else U.drag = {i = i, dx = cx - mv.x, dy = cy - mv.y} end
                AFUI.ShowNudge()
                editorBoxes()
                break
            end
        end
    elseif down and drag then
        local tx, ty = snapToFrames(drag.i, snap(cx - drag.dx), snap(cy - drag.dy))
        moveMover(drag.i, tx, ty)
    elseif not down then
        if drag then
            local tx, ty = snapToFrames(drag.i, snap(cx - drag.dx), snap(cy - drag.dy))
            moveMover(drag.i, tx, ty)
        end
        U.drag = nil
    end
    U.wasDown = down and true or false
end
function AFUI.Unlock()
    g_AddonName = 'RelicCalc'
    U.unlocks = (U.unlocks or 0) + 1
    if not U.ready then pcall(AFUI.Enter) end
    if not U.ready or not _G.AFUIMovers then
        DisplayChatMessage(0, 'Aetherfall Interface: movers unavailable. Type /afcore for details.', 0.91, 0.63, 0.56)
        return
    end
    local arg = tostring(U.pendingArg or ''):lower()
    U.pendingArg = nil
    local _, native = panel('Movers')
    if U.unlocked and native and not native.shown then U.unlocked, U.drag = false, nil end
    if not U.unlocked and arg ~= 'lock' then
        if U.edit then AFUI.EndEdit(false, true) end
        U.edit = {db=copy(U.db), positions=copy(U.positions), bindings=copy(U.bindings), stored=copy(U.stored), frames={}, saveAt=U.saveAt}
        U.open = false; if _G.AFUIOptions then w('Options'):Hide() end
        U.hiddenMovers, U.editorFilter, U.editorOrigin = {}, 1, 1
        U.unlocked, U.drag, U.wasDown = true, nil, true  -- a press must start on a box
        layoutMovers()
        DisplayChatMessage(0, 'Aetherfall MoveUI: drag boxes, then Save / Lock. Cancel restores your starting layout. Drag the panel header to move it.', 0.56, 0.89, 0.93)
    else
        if not AFUI.EndEdit(true) then return end
    end
    AFUI.Refresh()
end
function AFUI.ResetMover(i)
    g_AddonName = 'RelicCalc'
    local mv = U.movers[i]; if not mv then return end
    local sw, sh = screen(); local def = U.defaults[mv.id]
    U.positions[key(mv.id)] = nil; U.bindings[key(mv.id)] = nil; dirty()
    if mv.id == TOOLTIP or mv.id == TOOLTIP_UI then mv.x, mv.y = tooltipAnchor(mv.id); placeBox(i)
    elseif mv.id > MENU_ID and mv.id <= MENU_ID + #MENU_BUTTONS then
        mv.x, mv.y = buttonSpot(mv.id - MENU_ID, sw, sh); placeBox(i)
    elseif def then
        local x, y = def[1] * sw, def[2] * sh
        AFCore.Move(mv.id, x, y)
        mv.x, mv.y = x + (mv.ox or 0), y + (mv.oy or 0); placeBox(i)
    end
end
function AFUI.ResetAll()
    local sw, sh = screen(); local prefix = AFUI.Get('profile') .. '.'
    for k in pairs(U.positions) do
        if k:sub(1, #prefix) == prefix then
            local id = tonumber(k:sub(#prefix + 1)); local def = id and U.defaults[id]
            if def then AFCore.Move(id, def[1] * sw, def[2] * sh) end
            U.positions[k] = nil; U.bindings[k] = nil
        end
    end
    dirty(); if U.unlocked then layoutMovers() end
end
-- Windows the game re-places itself (the target re-centres at the top on every target change;
-- cast bars, target auras, tooltips, notification icons) stay on the spot of their mover, or the
-- layout spot. The core pins them (the game's own re-placing keeps that spot, so nothing is
-- drawn elsewhere first); each tick also puts back a shown one that is off its spot.
U.pins = {}
local function applyPins()
    local sw, sh = screen(); local prefix = AFUI.Get('profile') .. '.'
    local ids = {}
    for k in pairs(U.positions) do
        if k:sub(1, #prefix) == prefix then
            local id = tonumber(k:sub(#prefix + 1)); local d = id and dialog(id)
            if d and (owned(id, d) or U.unlocked) then ids[id] = true end
        end
    end
    for id in pairs(REANCHORED) do ids[id] = true end
    AFUI.FindTooltips()  -- cheap after the first pass: new windows and unnamed candidates only
    for id in pairs(U.tooltips) do ids[id] = nil end  -- tooltips follow the Tooltip anchor below
    local menu = menuManaged()
    if menu then ids[D.start] = nil end  -- held with its buttons below
    local pinned = {}
    for id in pairs(ids) do
        local d = dialog(id)
        local p = d and position(id, d)
        local x, y
        if p then x, y = p[1] * sw, p[2] * sh
        elseif d and REANCHORED[id] and AFUI_DESIGN and AFUI_DESIGN[id] and d.name == AFUI_DESIGN[id][7] then x, y = design(id) end
        if x and not AFUI.ManagedBar(id, d) then
            -- Keep the current draft held even during a drag. Skipping it let
            -- a late-created/recreated target frame use the game's anchor until
            -- mouse-up. The draft is set before native placement can re-enter.
            if AFCore.Pin(id, x, y) then
                pinned[id] = true
                if d.shown and (math.abs(d.x - x) > 1 or math.abs(d.y - y) > 1) then AFCore.Move(id, x, y) end
            end
        end
    end
    -- Tooltips by kind: hidden, beside the mouse, grown from a corner of their mover, or left
    -- where the game puts them (Native: not pinned at all).
    local clamp = AFUI.Get('ttclamp') == 1
    local function anchor(kind)
        local choice = kind == 'ui' and ({'native', 'mover', 'cursor'})[AFUI.Get('ttui')] or ({'mover', 'cursor', 'native'})[AFUI.Get('ttanchor')]
        if AFUI.Get('ttshow') == 0 then return 5, 0, 0 end
        if choice == 'native' then return nil end
        if choice == 'cursor' then return 4, AFUI.Get('ttx') * scale(), AFUI.Get('tty') * scale() end
        local ax, ay = tooltipAnchor(kind == 'ui' and TOOLTIP_UI or TOOLTIP); local bw, bh = tooltipBox()
        local m = GROW_MODE[AFUI.Get('ttgrow')] or 3
        return m, m % 2 == 1 and ax + bw or ax, m >= 2 and ay + bh or ay
    end
    local anchors = {ui = {anchor('ui')}, world = {anchor('world')}}
    -- The core decides each time the game places a tooltip: mouse over a game window -> UI anchor,
    -- over the world -> world anchor (so it opens at the right spot, no jump). Skill tooltips: UI.
    for id, kind in pairs(U.tooltips) do
        local wa, ua = anchors.world, anchors.ui
        if kind == 'ui' then wa = ua end
        if (wa[1] or ua[1]) and not pinned[id] then
            pinned[id] = true
            AFCore.TipPin(id, wa[1] or -1, wa[2] or 0, wa[3] or 0, ua[1] or -1, ua[2] or 0, ua[3] or 0, clamp)
        end
    end
    -- Start menu: each button at its spot; the click-through window just holds them all.
    if menu then
        local spots, x0, y0, x1, y1 = {}, math.huge, math.huge, -math.huge, -math.huge
        for k = 1, #MENU_BUTTONS do
            local x, y, bw, bh = buttonSpot(k, sw, sh)
            if x then
                spots[k] = {x, y}
                x0, y0, x1, y1 = math.min(x0, x), math.min(y0, y), math.max(x1, x + bw), math.max(y1, y + bh)
            end
        end
        if x0 < math.huge then
            if not U.menuGaps then U.menuGaps = AFCore.Gaps(D.start, true) end
            pinned[D.start] = true
            AFCore.Pin(D.start, x0, y0, 0, false, x1 - x0, y1 - y0)
            for k, b in ipairs(MENU_BUTTONS) do
                local sp = spots[k]
                for _, name in ipairs(sp and {b[1], unpack(b[4] or {})} or {}) do
                    AFCore.Pin(D.start, sp[1] - x0, sp[2] - y0, 0, false, 0, 0, name, b[3])
                    pinned[D.start .. ':' .. name] = {D.start, name}
                end
            end
        end
    end
    AFUI.HoldBars(pinned)
    for k, v in pairs(U.pins) do
        if not pinned[k] then
            if type(v) == 'table' then AFCore.Pin(v[1], nil, nil, nil, nil, nil, nil, v[2]) else AFCore.Pin(k) end
        end
    end
    U.pins = pinned
    AFUI.HoldMap()
end
local function holdPinned()
    -- Native placement can synchronously raise visibility events. Do not enter
    -- the placement pass again while its parent event is still running.
    if U.holding then return end
    U.holding = true
    local ok, why = pcall(applyPins)
    U.holding = false
    if not ok then U.pinError = tostring(why) end
end
AFUI.HoldPinned = holdPinned
-- Action bars (ElvUI): buttons per row and button size. The twelve slots are laid out in rows inside
-- their container, the container and the bar window take the new size (bottom edge kept), bar 1's
-- page arrows and lock follow. A bar once changed stays held (also back at its default) until relog.
-- {bar window, slot container (name, or "#1": the bar's first child), its spot in the bar}
local BAR_LAYOUT = {{'shortcut', 'ctn_shortcut', 26, 4}, {'shortcut_ctrl', '#1', 0, 0}, {'shortcut_alt', '#1', 0, 0}, {'shortcut_extra', '#1', 0, 0}}
local BAR_DEFAULT = {12, 12, 12, 6}
U.barsHeld = {}
U.barFit = {}
local function barDialog(i, d)
    local suffix = ({'', '_ctrl', '_alt', '_extra'})[i]
    return d and d.name == 'quickbar_dialog' .. suffix
end
function AFUI.ManagedBar(id, d)
    for i, b in ipairs(BAR_LAYOUT) do
        if D[b[1]] == id and barDialog(i, d) then
            return U.barsHeld[i] or position(id, d) ~= nil
                or AFUI.Get('abrow' .. i) ~= BAR_DEFAULT[i] or AFUI.Get('absize' .. i) ~= 40
        end
    end
    return false
end
-- One geometry calculation for layout and the options' fit indicator. Keep the
-- requested row count in the profile; a smaller viewport can temporarily wrap
-- it into extra columns without shrinking buttons or losing any of the 12 slots.
function AFUI.BarMetrics(i)
    local b = BAR_LAYOUT[i]; if not b then return end
    local s = n((AFCore.Scale())) or 1
    local sw, sh = screen()
    local requested, size = AFUI.Get('abrow' .. i), AFUI.Get('absize' .. i)
    local pitch, gap = (size + 2) * s, 2 * s
    local padw, padh = i == 1 and 52 * s or 0, i == 1 and 8 * s or 0
    local maxcols = math.max(1, math.min(12, math.floor((sw - padw + gap) / pitch)))
    local maxrows = math.max(1, math.floor((sh - padh + gap) / pitch))
    local per = math.min(maxcols, math.max(math.ceil(12 / maxrows), requested))
    local rows = math.ceil(12 / per)
    local cw, ch = per * pitch - gap, rows * pitch - gap
    return {per = per, requested = requested, size = size * s, pitch = pitch, cw = cw, ch = ch,
            ox = b[3] * s, oy = b[4] * s, w = cw + padw, h = math.max(i == 1 and 48 * s or 0, ch + padh)}
end
function AFUI.HoldBars(pinned)
    local s = n((AFCore.Scale())) or 1
    local sw, sh = screen()
    for i, b in ipairs(BAR_LAYOUT) do
        local id = D[b[1]]; local d = id and dialog(id)
        local per, size = AFUI.Get('abrow' .. i), AFUI.Get('absize' .. i)
        if per ~= BAR_DEFAULT[i] or size ~= 40 then U.barsHeld[i] = true end
        if barDialog(i, d) and AFUI.ManagedBar(id, d) then
            local m = AFUI.BarMetrics(i)
            local pitch, per, cw, ch, ox, oy, w0, h0 = m.pitch, m.per, m.cw, m.ch, m.ox, m.oy, m.w, m.h
            local p = position(id, d)
            local x, y = p and p[1] * sw or d.x, p and p[2] * sh or d.y + d.h - h0
            x, y = math.max(0, math.min(sw - w0, x)), math.max(0, math.min(sh - h0, y))
            AFCore.Pin(id, x, y, 0, true, w0, h0); pinned[id] = true
            AFCore.Pin(id, ox, oy, 0, false, cw, ch, b[2], b[2] == '#1' and 'any' or 'container'); pinned[id .. ':' .. b[2]] = {id, b[2]}
            for k = 1, 12 do
                local slot = b[2] == '#1' and ('#1/shortcut' .. k) or ('shortcut' .. k)
                local c, r = (k - 1) % per, math.floor((k - 1) / per)
                AFCore.Pin(id, c * pitch, r * pitch, 0, false, m.size, m.size, slot, 'itemview')
                pinned[id .. ':' .. slot] = {id, slot}
            end
            if i == 1 then
                local px = ox + cw + 6 * s
                for _, e in ipairs({{'page_up', px, 3 * s, 'button'}, {'page_down', px, h0 - 18 * s, 'button'},
                                    {'page', px, h0 / 2 - 6 * s, 'static'}, {'lock', 1 * s, h0 / 2 - 10 * s, 'button'}}) do
                    AFCore.Pin(id, e[2], e[3], 0, false, 0, 0, e[1], e[4]); pinned[id .. ':' .. e[1]] = {id, e[1]}
                end
            end
            -- Refresh the existing box in place, retaining its selection and
            -- queued-widget ordering while dimensions change under an open editor.
            for j, mv in ipairs(U.movers) do
                if mv.id == id and (mv.x ~= x or mv.y ~= y or mv.w ~= w0 or mv.h ~= h0) then
                    mv.x, mv.y, mv.w, mv.h, mv.ox, mv.oy = x, y, w0, h0, 0, 0
                    placeBox(j)
                    if U.selected == j then AFUI.ShowNudge() end
                end
            end
            if U.barFit[i] ~= m.per then
                U.barFit[i] = m.per
                if U.open and U.tab == 'Action Bars' and AFUI.Get('abbar') == i then AFUI.Refresh() end
            end
        end
    end
end
-- The start menu's buttons in pixels and its window frame (for /afcore test).
function AFUI.ButtonRects()
    local d = menuManaged(); if not d then return {} end
    local sw, sh = screen(); local rects = {}
    for k in ipairs(MENU_BUTTONS) do
        local x, y, bw, bh = buttonSpot(k, sw, sh)
        if x then rects[#rects + 1] = {x, y, bw, bh} end
    end
    return rects, {d.x, d.y, d.w, d.h}
end
-- /afcore watch: every game window that opens for 15 seconds, with its class (to tell tooltips apart).
function AFUI.WatchTick()
    if not U.watchUntil then return end
    for id = 1, PLUGIN_FIRST - 1 do
        local name, x, y, wd, ht, shown, class = AFCore.Dialog(id)
        if name and shown and not U.watchSeen[id] then
            U.watchSeen[id] = string.format('0x%x %s class 0x%x %dx%d at %d,%d', id, name ~= '' and name or '(unnamed)', class or 0, wd, ht, x, y)
        end
    end
    if U.ticks >= U.watchUntil then
        U.watchUntil = nil
        local lines = {}
        for _, text in pairs(U.watchSeen) do lines[#lines + 1] = text end
        table.sort(lines)
        DisplayChatMessage(0, 'Aetherfall watch: ' .. #lines .. ' window(s) opened', 0.56, 0.89, 0.93)
        for i = 1, math.min(#lines, 24) do DisplayChatMessage(0, lines[i], 0.56, 0.89, 0.93) end
    end
end
-- World map: draggable by its own frame at any time; where the player left it is remembered.
function AFUI.HoldMap()
    local id = U.mapId; local d = id and dialog(id)
    if not d then U.mapShown = false; return end
    local sw, sh = screen()
    if d.shown and not U.mapShown then
        AFCore.Movable(id, true)
        local mx, my = AFUI.Get('mapx'), AFUI.Get('mapy')
        if mx >= 0 and my >= 0 then AFCore.Move(id, math.min(sw - d.w, mx * sw), math.min(sh - d.h, my * sh)) end
        U.mapAt = U.ticks
    elseif d.shown and U.ticks - (U.mapAt or 0) >= 20 then
        U.mapAt = U.ticks
        local mx, my = d.x / sw, d.y / sh
        if mx >= 0 and my >= 0 and (math.abs(mx - AFUI.Get('mapx')) > .002 or math.abs(my - AFUI.Get('mapy')) > .002) then
            U.db.mapx, U.db.mapy = mx, my; dirty()
        end
    end
    U.mapShown = d.shown
end
-- Tooltip windows by name (checked once per window; new windows are checked when they appear).
function AFUI.FindTooltips()
    U.tooltips, U.checked = U.tooltips or {}, U.checked or {}
    for id = 1, PLUGIN_FIRST - 1 do
        local d = dialog(id)
        if d then
            if U.checked[id] ~= identity(d) then
                U.checked[id] = identity(d); U.tooltips[id] = nil
                local kind = tooltipKind(d)
                -- An unnamed window is the world tooltip only once it has been seen shown and small.
                if kind then U.tooltips[id] = kind end
                if tostring(d.name):match('^new_map') then U.mapId = id end
            end
        else U.checked[id], U.tooltips[id] = nil, nil end
    end
end
-- The server's own windows (Central Market / Season Pass / Companions, Shop, Journey, Wardrobe) are
-- 1280x960 plugin windows the game stretches to the screen: their content stayed 1280 wide and a
-- second close button stayed at the old edge. Sized to the screen here, content with them.
local FIT = {{'PrivateWarehouse', 'PrivateWarehouseBrowser', 25}, {'PrivateCashShop', 'PrivateCashShopBrowser', 25},
             {'PrivateJourney', 'PrivateJourneyBrowser', 0}, {'PrivateWardrobe', nil, 25}}
U.fitted = {}
local function fitWindows()
    local sw, sh = screen(); local s = scale(); local W, H = sw / s, sh / s
    for _, f in ipairs(FIT) do
        local win = _G[f[1]]
        local open = win and win.IsVisible and win:IsVisible() == 1
        if not open then U.fitted[f[1]] = nil
        else
            local want = string.format('%.1fx%.1f', W, H)
            if U.fitted[f[1]] ~= want then
                win:SetRect(0, 0, W, H)
                if win.SetCloseOffset then pcall(win.SetCloseOffset, win, 4, 1) end
                local content = f[2] and _G[f[2]]
                if content then content:SetRect(0, 0, W, H - f[3]) end
                U.fitted[f[1]] = want
            end
        end
    end
end
AFUI.FitWindows = fitWindows
-- Each open sets the 1280x960 seed again (Season Pass and Companions reuse the market window):
-- fit again on the next tick. A browser's web page keeps the size it was created with (its XML
-- 1280x935, about 1706 px wide at UI scale 1.33), so before the page loads the browser is sized
-- to the screen and its web view created again at that size (once per screen size).
-- RelicCalc binds these at its OnLoad, after this file has run.
U.webSize = {}
local OPENS = {PrivateWarehouse_Open = 1, PrivateSeasonPass_Open = 1, PrivateCompanions_Open = 1, PrivateCashShop_Open = 2,
               PrivateJourney_Open = 3, PrivateWardrobe_Open = 4}
local function fitContent(f)
    local content = f[2] and _G[f[2]]
    if not content then return end
    g_AddonName = 'RelicCalc'
    local sw, sh = screen(); local s = scale(); local W, H = sw / s, sh / s
    content:SetRect(0, 0, W, H - f[3])
    local size = string.format('%.0fx%.0f', W, H)
    if U.webSize[f[2]] ~= size then content:DestroyWebView(); content:CreateWebView(); U.webSize[f[2]] = size end
end
for name, index in pairs(OPENS) do
    local open = _G[name]
    if type(open) == 'function' then
        _G[name] = function(...) U.fitted = {}; pcall(fitContent, FIT[index]); return open(...) end
    end
end
releasePins = function()
    if next(U.pins) then AFCore.Pin(); U.pins = {} end
    U.menuGaps = nil  -- releasing every pin also ends the start menu's gaps
end
-- Remember every game window that shows up this session, so /moveui offers a mover for it even
-- when it is closed at that moment (tooltips, notification icons, pop-ups).
U.seen = {}
local function noteShown()
    AFUI.FindTooltips()
    for id = 1, PLUGIN_FIRST - 1 do
        local d = not U.seen[id] and dialog(id)
        if d and d.shown and nativeMovable(d) and d.w > 0 and d.h > 0 then U.seen[id] = d.name end
    end
end

-- ---------- options window ----------
-- ElvUI-style: category sidebar, a panel with the category title, rows of a short label
-- and its control (On/Off, - value +, < choice >, or an action button), and one short hint.
local function onoff(k) return AFUI.Get(k) == 1 and 'On' or 'Off' end
local function toggle(k) return {k = k, kind = 'toggle'} end
local function numeric(k, label) return {k = k, kind = 'number', label = label} end
local function choice(k) return {k = k, kind = 'choice'} end
local function data(k) return {k = k, kind = 'data'} end
local function action(name, arg) return {kind = 'action', action = name, arg = arg} end
-- {category, rows, hint}
local TABS = {
    {'General', {{'Data text font', choice('font')}, {'Colour theme', choice('theme')}, {'Data bar opacity', numeric('alpha')}},
     'Top bar and status line. Window skins: launcher Mods window.'},
    {'Unit Frames', {{'Target of target', toggle('tot')}, {'Party frames', toggle('party')}},
     'Move frames with /moveui.'},
    {'Action Bars', {{'Bar 3', toggle('altbar')}, {'Bar 4', toggle('extrabar')}, {'Bars 5 and 6', toggle('sepbar')},
                     {'Layout of', choice('abbar')}, {'Buttons per row', {kind = 'barnumber', base = 'abrow'}},
                     {'Button size', {kind = 'barnumber', base = 'absize'}}},
     'Rows and size apply to the bar chosen in Layout of.'},
    {'Chat', {{'Status line', toggle('chatline')}}, 'Chat size and tabs are the game\'s own.'},
    {'Data Texts', {{'Top bar', toggle('databar')}, {'Left 1', data('dl1')}, {'Left 2', data('dl2')}, {'Left 3', data('dl3')},
                    {'Centre 1', data('dc1')}, {'Centre 2', data('dc2')}, {'Centre 3', data('dc3')},
                    {'Right 1', data('dr1')}, {'Right 2', data('dr2')}, {'Right 3', data('dr3')}}, nil},
    {'Tooltip', {{'Tooltips', toggle('ttshow')}, {'World (NPCs, players)', choice('ttanchor')}, {'UI (buttons, items)', choice('ttui')},
                 {'Grow from', choice('ttgrow')},
                 {'Cursor offset X', numeric('ttx')}, {'Cursor offset Y', numeric('tty')}, {'Keep on screen', toggle('ttclamp')},
                 {'Tooltip mover', action('unlock')}},
     'Mover: drag the Tooltip boxes in /moveui. Native: where the game puts it.'},
    {'Minimap', {{'Zone and coordinates', toggle('minimapinfo')}, {'XP bar', toggle('xpbar')}, {'Start menu', toggle('startmenu')}}, nil},
    {'Movers', {{'Movers', action('unlock')}, {'Show grid', toggle('showgrid')}, {'Guide spacing', numeric('guidesize')},
                {'Snap to grid', toggle('snap')}, {'Snap to frames', toggle('snapframes')}, {'Snap step', numeric('grid')},
                {'All positions', action('resetall')}},
     'MoveUI has Save / Lock, Cancel, frame selection and exact position controls.'},
    {'Profiles', {{'Profile', {kind = 'profile'}}, {'DPS preset', action('preset', 'DPS')}, {'Healer preset', action('preset', 'Healer')},
                  {'Minimal preset', action('preset', 'Minimal')}, {'User1', action('store', 'User1')},
                  {'User2', action('store', 'User2')}, {'User3', action('store', 'User3')}}, nil},
}
U.TABS = TABS
local TAB_SLOTS = 10  -- sidebar buttons in the layout

local ACTION_LABEL = {unlock = 'Unlock', resetall = 'Reset', preset = 'Apply', store = 'Save'}
local function value(item)
    local spec = item[2]
    if spec.kind == 'toggle' then return onoff(spec.k) end
    if spec.kind == 'number' then return tostring(AFUI.Get(spec.k)) end
    if spec.kind == 'barnumber' then
        local i = AFUI.Get('abbar'); local requested = AFUI.Get(spec.base .. i)
        local m = spec.base == 'abrow' and AFUI.BarMetrics(i)
        return tostring(requested) .. (m and m.per ~= requested and (' (' .. m.per .. ' fit)') or '')
    end
    if spec.kind == 'choice' then return CHOICE[spec.k][AFUI.Get(spec.k)] end
    if spec.kind == 'data' then return AFUI.Get(spec.k) end
    if spec.kind == 'profile' then return AFUI.Get('profile') end
    if spec.kind == 'action' then return spec.action == 'unlock' and (U.unlocked and 'Lock' or 'Unlock') or ACTION_LABEL[spec.action] end
    return ''
end
local function currentTab()
    for _, tab in ipairs(TABS) do if tab[1] == U.tab then return tab end end
    U.tab = TABS[1][1]; return TABS[1]
end

function AFUI.Refresh()
    if not U.ready or not _G.AFUIOptions then return end
    local current = currentTab()
    local accent = AFHUD_Accent and AFHUD_Accent() or GOLD
    for i = 1, TAB_SLOTS do
        local tab = TABS[i]
        if tab then
            w('Tab' .. i):SetText(tab[1]); w('Tab' .. i):SetAttr('textColor', tab == current and accent or MIST); w('Tab' .. i):Show()
        else
            w('Tab' .. i):Hide()
        end
    end
    w('Header'):SetText(current[1]); w('Header'):SetAttr('textColor', accent)
    for i = 1, ROWS do
        local item = current[2][i]
        local label, val, less, more = w('Row' .. i .. 'Label'), w('Row' .. i .. 'Value'), w('Row' .. i .. 'Less'), w('Row' .. i .. 'More')
        if item then
            local kind = item[2].kind
            label:SetText(item[1]); label:Show()
            val:SetText(value(item)); val:Show()
            val:SetAttr('textColor', kind == 'toggle' and (AFUI.Get(item[2].k) == 1 and AETHER or MIST) or ICE)
            if kind == 'number' or kind == 'barnumber' or kind == 'choice' or kind == 'data' or kind == 'profile' then
                local num = kind == 'number' or kind == 'barnumber'
                less:SetText(num and '-' or '<'); more:SetText(num and '+' or '>')
                less:Show(); more:Show()
            else
                less:Hide(); more:Hide()
            end
        else
            label:Hide(); val:Hide(); less:Hide(); more:Hide()
        end
    end
    w('Status'):SetText(U.saveError or current[3] or '')
end

local function set(k, v)
    U.db[k] = v; dirty()
    if AFHUD_Apply then AFHUD_Apply() end
    if TOGGLES[k] then pcall(AFUI.ApplyToggles) end  -- immediately, not on the next second
end
local function cycle(list, cur, dir)
    for i, v in ipairs(list) do if v == cur then return list[(i - 1 + dir) % #list + 1] end end
    return list[1]
end
local function snapshot()
    local parts = {}
    for k in pairs(defaults) do if k ~= 'profile' then parts[#parts + 1] = k .. ':' .. tostring(AFUI.Get(k)) end end
    return table.concat(parts, ';')
end
local function restore(profile)
    -- A profile owns its settings as well as its mover positions. Missing or
    -- older partial snapshots start from that profile's defaults, never from
    -- whichever profile happened to be active before it.
    for k, v in pairs(defaults) do if k ~= 'profile' then U.db[k] = v end end
    for k, v in pairs(PRESET[profile] or {}) do U.db[k] = v end
    for k, v in (U.stored[profile] or ''):gmatch('(%w+):([^;]+)') do
        if defaults[k] ~= nil and k ~= 'profile' then
            local value = valid(k, v)
            if value ~= nil then U.db[k] = value end
        end
    end
end
local function rememberProfile()
    U.stored[AFUI.Get('profile')] = snapshot()
end

function AFUI.Click(what)
    g_AddonName = 'RelicCalc'
    U.clicks, U.lastClick = (U.clicks or 0) + 1, tostring(what)
    if what == 'Close' then U.open = false; if _G.AFUIOptions then w('Options'):Hide() end; return end
    if not U.ready then return end
    local tabIndex = tonumber(tostring(what):match('^Tab(%d+)$'))
    if tabIndex and TABS[tabIndex] then U.tab = TABS[tabIndex][1]; AFUI.Refresh(); return end
    local row, part = tostring(what):match('^Row(%d+)(%a+)$'); row = tonumber(row)
    local item = row and currentTab()[2][row]
    if not item then return end
    local spec = item[2]
    local dir = part == 'Less' and -1 or 1  -- '<' / '-' go back; the value and '>' / '+' go forward
    if spec.kind == 'toggle' then set(spec.k, 1 - AFUI.Get(spec.k))
    elseif spec.kind == 'number' or spec.kind == 'barnumber' then
        local k = spec.kind == 'barnumber' and spec.base .. AFUI.Get('abbar') or spec.k
        local r = NUMERIC[k]; local step = part == 'Less' and -r[3] or r[3]
        if part == 'Value' then step = r[3] end
        local v = AFUI.Get(k) + step; if v > r[2] then v = r[1] elseif v < r[1] then v = r[2] end
        set(k, v)
    elseif spec.kind == 'choice' then set(spec.k, (AFUI.Get(spec.k) - 1 + dir) % #CHOICE[spec.k] + 1)
    elseif spec.kind == 'data' then set(spec.k, cycle(DATA, AFUI.Get(spec.k), dir))
    elseif spec.kind == 'profile' then
        local nxt = cycle(PROFILES, AFUI.Get('profile'), dir)
        rememberProfile()
        U.db.profile = nxt
        restore(nxt)
        U.observed = {}; dirty(); if AFHUD_Apply then AFHUD_Apply() end
        if U.unlocked then layoutMovers() end
    elseif spec.kind == 'action' then
        if spec.action == 'unlock' then AFUI.Unlock(); return
        elseif spec.action == 'resetall' then AFUI.ResetAll()
        elseif spec.action == 'preset' then
            rememberProfile()
            for k, v in pairs(defaults) do if k ~= 'profile' then U.db[k] = v end end
            for k, v in pairs(PRESET[spec.arg] or {}) do U.db[k] = v end
            U.db.profile = spec.arg; dirty(); if AFHUD_Apply then AFHUD_Apply() end
        elseif spec.action == 'store' then
            rememberProfile()
            local prefix = AFUI.Get('profile') .. '.'; local copied, names = {}, {}
            for k, p in pairs(U.positions) do
                if k:sub(1, #prefix) == prefix then
                    local target = spec.arg .. '.' .. k:sub(#prefix + 1)
                    copied[target] = {p[1], p[2]}; names[target] = U.bindings[k]
                end
            end
            local dest = spec.arg .. '.'
            for k in pairs(U.positions) do if k:sub(1, #dest) == dest then U.positions[k], U.bindings[k] = nil, nil end end
            for k, p in pairs(copied) do U.positions[k], U.bindings[k] = p, names[k] end
            U.stored[spec.arg] = snapshot(); U.db.profile = spec.arg; U.observed = {}; dirty()
            if U.unlocked then layoutMovers() end
        end
    end
    AFUI.Refresh()
end

function AFUI.Open()
    g_AddonName = 'RelicCalc'
    if U.edit then editorStatus('Save / Lock or Cancel before opening settings.'); return end
    if not U.ready then pcall(AFUI.Enter) end
    if not _G.AFUIOptions then
        DisplayChatMessage(0, 'Aetherfall Interface: options window did not load (plugin XML). Type /afcore for details.', 0.91, 0.63, 0.56)
        return
    end
    if not U.ready then
        DisplayChatMessage(0, 'Aetherfall Interface: not ready yet (character not loaded).', 0.91, 0.63, 0.56)
        return
    end
    -- Toggle on our own state: hidden plugin dialogs can report stale visibility.
    U.opened = (U.opened or 0) + 1
    -- (the close button hides it natively, so both must agree before it closes).
    local _, native = panel('Options')
    local visible = native and native.shown or (not native and w('Options'):IsVisible() == 1)
    if U.open and visible then U.open = false; showPanel('Options', false) else
        local sw, sh = screen(); local s = scale()
        U.open = true
        w('Options'):SetRect(math.max(0, (sw / s - 640) / 2), math.max(0, (sh / s - 460) / 2), 640, 460)
        showPanel('Options', true); AFUI.Refresh()
    end
end

local function enter()
    local identity = playerIdentity(); if not identity then return end
    if U.identity ~= identity then
        if U.edit then AFUI.EndEdit(false, true, true) end
        if U.ready then AFUI.Save() end
        local ok, why = pcall(load)
        if not ok then U.loadError = tostring(why); U.db, U.positions, U.stored, U.bindings = {}, {}, {}, {} end
        U.identity = identity; U.defaults = {}; U.designed = {}; U.designSize = nil; U.unlocked = false; U.restoreEdit = nil
        U.hidden = {}  -- shown-state restoration belongs to the current character
        U.lastHudShown = nil
        U.initialRadar = true  -- the active Aetherfall layout includes a visible minimap
    end
    U.ready = true
end
AFUI.Enter = enter

-- Slash handlers report failures in chat instead of failing silently.
-- Windows shown from inside chat command dispatch never appear in this client, while
-- everything shown from the addon timer does (data bar, timers, PlayerBotBar). So the
-- commands only queue the request; the next ADDON_TIMER tick performs it.
local ACTIONS = {open = function() AFUI.Open() end, move = function() AFUI.Unlock() end}
local LABELS = {open = '/afui', move = '/moveui'}
function AFUI.Request(what, arg)
    if ACTIONS[what] then
        U.pending, U.pendingArg = what, arg; U.requests = (U.requests or 0) + 1
        -- Explicit editor requests can also wake a lost shared timer. The
        -- existing owner replaces its chain; controls never add another loop.
        if WardrobeNative_ResumeTimer then WardrobeNative_ResumeTimer() end
    end
end
AFUI.SlashOpen = function() AFUI.Request('open') end
AFUI.SlashMove = function(arg) AFUI.Request('move', arg) end
function AFUI.BindCommands()
    if not SlashCmdList then return end
    SlashCmdList.AFOPTIONS = AFUI.SlashOpen
    SLASH_AFOPTIONS1, SLASH_AFOPTIONS2, SLASH_AFOPTIONS3, SLASH_AFOPTIONS4 = '/afui', '/aetherfall', '/aui', '/ec'
    SlashCmdList.AETHERFALLMOVE = AFUI.SlashMove; SLASH_AETHERFALLMOVE1 = '/moveui'
end

local function recoverLayout(keepEditor)
    if not keepEditor and U.edit then AFUI.EndEdit(false, true, true) end
    U.settled = false
    releasePins()
    U.designed, U.observed, U.checked, U.fitted = {}, {}, {}, {}
    U.designSize, U.menuGaps, U.mapShown = nil, nil, false
    U.sizeSince = U.ticks
    U.recoverShown = U.lastHudShown or {}
    -- A movie can hide an open editor without changing our Lua toggle.
    U.drag, U.wasDown, U.panelDrag = nil, true, nil
    if not keepEditor then
        if U.unlocked and _G.AFUIMovers then w('Movers'):Hide() end
        U.unlocked, U.movers = false, {}
    else U.refreshMovers = U.unlocked end
    if AFHUD_Invalidate then AFHUD_Invalidate() end
    AFUI.BindCommands()
end
local RESTORE_HUD = {}
for _, name in ipairs({'radar', 'start', 'exp_bar', 'shortcut', 'shortcut_ctrl', 'shortcut_alt', 'shortcut_extra'}) do
    if D[name] then RESTORE_HUD[D[name]] = true end
end
local function recoverShown()
    for id in pairs(RESTORE_HUD) do
        local d = dialog(id)
        if d and U.recoverShown[id] and not d.shown then AFCore.SetShown(id, true) end
    end
    U.recoverShown = nil
end
local function refreshMoverRects()
    if U.drag then return end
    for i, mv in ipairs(U.movers) do
        local d = mv.id < PLUGIN_FIRST and dialog(mv.id)
        if d then
            local x, y, wd, ht, ox, oy = frameRect(mv.id, d)
            local changed = x ~= mv.x or y ~= mv.y or wd ~= mv.w or ht ~= mv.h
            mv.x, mv.y, mv.w, mv.h, mv.ox, mv.oy = x, y, wd, ht, ox, oy
            placeBox(i)
            if changed and U.selected == i then AFUI.ShowNudge() end
        end
    end
end

-- The options window and movers are built on first use (AetherfallLazy.lua) with the client's
-- own loader, a few dialogs per tick: built during the plugin's one-pass load they lost their
-- own setup in game (rect 0, Show ignored). A request waits until its dialogs exist and the
-- engine has had two ticks to take them.
local BUILD_PER_TICK = {open = 1, move = 4}
U.built = {}
local function build(i)
    local entry = AFUI_LAZY and AFUI_LAZY[i]
    if not entry or U.built[i] then return end
    g_AddonName = 'RelicCalc'
    local doc = lxp.lom.parse(entry[2])
    if type(doc) ~= 'table' then error('could not parse ' .. entry[1]) end
    LoadXMLWidget(doc)
    U.built[i] = U.ticks
end
local function built(what)
    if not AFUI_LAZY or #AFUI_LAZY == 0 then return true end  -- everything is built with the plugin
    local first, last = 1, 1
    if what == 'move' then first, last = 2, AFUI_LAZY and #AFUI_LAZY or 1 end
    local budget, done = BUILD_PER_TICK[what], true
    for i = first, last do
        if not U.built[i] then
            done = false
            if budget > 0 then build(i); budget = budget - 1 end
        elseif U.ticks - U.built[i] < 2 then
            done = false
        end
    end
    return done
end
AFUI.Built = built
local function runPending()
    local what = U.pending; if not what then return end
    local ok, ready = pcall(built, what)
    if ok and not ready then return end  -- keep building next tick
    U.pending = nil
    if not ok then
        U.slashError = LABELS[what] .. ' (building): ' .. tostring(ready)
        DisplayChatMessage(0, 'Aetherfall Interface ' .. U.slashError, 0.91, 0.63, 0.56)
        return
    end
    local ok, why = pcall(ACTIONS[what])
    if not ok then
        U.slashError = LABELS[what] .. ': ' .. tostring(why)
        DisplayChatMessage(0, 'Aetherfall Interface ' .. U.slashError, 0.91, 0.63, 0.56)
    end
end

function AFUI_OnEvent(event, ...)
    g_AddonName = 'RelicCalc'
    if event == 'ADDON_TIMER' then
        U.ticks = U.ticks + 1
        if U.ticks % 20 == 1 then AFUI.BindCommands() end
        local current = playerIdentity()
        if not current then
            if U.connected ~= false then
                if U.edit then AFUI.EndEdit(false, true, true) end
                if U.ready and U.saveAt then AFUI.Save() end
                U.connected, U.ready, U.settled = false, false, false
                U.drag, U.wasDown, U.observed = nil, true, {}
                U.unlocked, U.movers = false, {}
                if _G.AFUIMovers then w('Movers'):Hide() end
                if _G.AFUIOptions then w('Options'):Hide() end
                U.open = false
                releasePins()
            end
            U.ready, U.settled = false, false
            return
        end
        -- Reconnect can deliver world entry before UnitName becomes valid.
        -- Detect that later, including same-character reconnects and switches.
        if not U.ready or U.identity ~= current or not U.connected then
            enter()
            if not U.ready then return end
            U.connected, U.suspended = true, nil
            recoverLayout()
        end
        -- While the game is not in front (Alt+Tab) or the screen size changes (minimised, resized),
        -- windows are left alone: the pins keep them, and placing them for a passing size moved them.
        local sw, sh = screen(); local size = math.floor(sw) .. 'x' .. math.floor(sh) .. ':' .. tostring((AFCore.Scale()))
        local focused = AFCore.Cursor() ~= nil
        if U.focused == true and not focused then
            U.settled, U.drag, U.wasDown, U.panelDrag = false, nil, true, nil
        elseif U.focused == false and focused then
            recoverLayout(true)
        end
        U.focused = focused
        if U.lastSize ~= size then
            U.lastSize, U.sizeSince = size, U.ticks
            U.drag, U.wasDown, U.refreshMovers, U.panelDrag = nil, true, U.unlocked, nil
        end
        local primary, chat = D.basic_status and dialog(D.basic_status), D.chat and dialog(D.chat)
        local worldShown = (primary and primary.shown) or (chat and chat.shown)
        if U.worldShown == true and not worldShown then U.suspended = true; releasePins() end
        if worldShown and U.suspended then U.suspended = nil; recoverLayout() end
        U.worldShown = worldShown and true or false
        local zone = call('GetZoneText')
        if zone and U.zone and zone ~= U.zone then recoverLayout() end
        U.zone = zone or U.zone
        U.settled = focused and sw >= 800 and sh >= 600 and U.ticks - (U.sizeSince or 0) >= 20
        U.settled = U.settled and not U.suspended
        if U.settled then
            if U.restoreEdit then AFUI.RestoreEditFrames() end
            runPending()
            if U.refreshMovers then
                U.refreshMovers = nil
                if U.unlocked then layoutMovers() end
            end
        end
        if U.unlocked and U.settled then
            dragMovers()
            if U.ticks % 5 == 0 then refreshMoverRects() end
        end
        if not (AFHUD and AFHUD.on) then pcall(releasePins) elseif U.settled then
            if U.initialRadar and D.radar then
                local radar = dialog(D.radar)
                if radar and radar.name == 'radar_dialog' then
                    if not radar.shown then AFCore.SetShown(D.radar, true) end
                    U.initialRadar = nil
                end
            end
            if U.recoverShown then recoverShown() end
            holdPinned()
        end
        if U.settled then pcall(fitWindows) end
        if U.watchUntil then pcall(AFUI.WatchTick) end
        if U.settled and U.ticks % 40 == 0 then pcall(noteShown) end
        if U.ticks % 20 == 0 then
            U.alive = U.ticks
            if AFHUD and AFHUD.on and U.settled then
                local ok, why = pcall(enforce)
                if not ok then U.enforceError = tostring(why) end
                U.lastHudShown = {}
                for id in pairs(RESTORE_HUD) do local d = dialog(id); if d then U.lastHudShown[id] = d.shown end end
            end
        end
        if not U.edit and U.saveAt and U.ticks >= U.saveAt then AFUI.Save() end
    elseif event == 'USER_CHANGED_TARGET' or event == 'GAME_UI_DIALOG_VISIBLE_CHANGED' then
        if U.ready and U.connected and U.settled and not U.suspended and playerIdentity() == U.identity
            and AFCore.Cursor() ~= nil and AFHUD and AFHUD.on then holdPinned() end
    elseif event == 'PLAYER_ENTERING_WORLD' then
        enter(); if playerIdentity() then U.connected = true end; recoverLayout()
    elseif event == 'ADDON_EXIT' then
        if U.edit then AFUI.EndEdit(false, true, true) end
        if U.ready then AFUI.Save() end
    end
end

-- Called from the data bar's OnLoad (AFHTop), which already receives ADDON_TIMER and
-- PLAYER_ENTERING_WORLD; the options window and movers are built later, on first use.
function AFUI_OnLoad()
    g_AddonName = 'RelicCalc'
    if _G.AFHTop then
        for _, e in ipairs({'ADDON_EXIT', 'USER_CHANGED_TARGET', 'GAME_UI_DIALOG_VISIBLE_CHANGED'}) do AFHTop:RegisterEvent(e) end
    end
    AFUI.BindCommands()
    -- RelicCalc's Wardrobe owns the shared .05 second timer. Never reset it.
end

-- Registered when this file loads, so the commands exist even if the plugin XML did not.
AFUI.BindCommands()
