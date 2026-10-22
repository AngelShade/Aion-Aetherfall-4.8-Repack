-- Aetherfall Core: Lua names for the AetherfallCore.dll bridge plus /afcore,
-- a chat report of what each bridge call returns (no frames are created).
-- Everything is read-only; nil means the bridge or the engine value is absent.

AFCore = AFCore or {}
local core = AetherfallCore

-- Native dialog type ids (Game.dll dialog table).
AFCore.DIALOG = {
    shortcut = 0x10, shortcut_ctrl = 0x12, shortcut_alt = 0x13, shortcut_extra = 0x16, shortcut_sep = 0x17,
    start = 0x11, chat = 0x0d, chat_input = 0xcf, basic_status = 0x0a, party = 0x21, radar = 0x0e, exp_bar = 0x14,
    skill_delay = 0x15a, target_delay = 0x17d, target_assist_delay = 0x17c,
    target = 0x0b, target_assist = 0x0c, buff_status = 0xb0, debuff_status = 0xb1, target_buff = 0x20,
    shortcut_sep2 = 0x18,  -- second separated bar (multi-copy dialogs take consecutive ids)
}

function AFCore.Loaded() return core ~= nil end

-- Mods/AetherfallUI resolved against the client's working folder (client root or bin64).
local modBase
function AFCore.ModPath(name)
    if not modBase and io and io.open then
        for _, base in ipairs({'Mods/AetherfallUI/', '../Mods/AetherfallUI/', '../../Mods/AetherfallUI/'}) do
            local f = io.open(base .. 'manifest.json', 'r')
            if f then f:close(); modBase = base; break end
        end
    end
    return (modBase or 'Mods/AetherfallUI/') .. (name or '')
end
function AFCore.Version() if core then return core("version") end end
function AFCore.Money() if core then return core("money") end end
-- current, needed for this level, recoverable, repose current, repose max
function AFCore.Exp() if core then return core("exp") end end
function AFCore.FPS() if core then return core("fps") end end
-- unit: "player", "target", "targettarget"
function AFCore.NumAuras(unit) if core then return core("auras", unit) end end
-- skill id, name, level, slot (0 buff, 1 debuff, 2 chant, 3 special, 4 special2, 5 boost),
-- native remaining seconds (nil for untimed), total nil (not exposed), raw remaining ms,
-- first field, time mode (2 native remaining ms, 0 untimed). No history or clock estimate.
function AFCore.Aura(unit, index) if core then return core("aura", unit, index) end end
-- object id, name, level, hp, max hp, mp, max mp ("target", "targettarget")
function AFCore.Unit(unit) if core then return core("unit", unit) end end
function AFCore.SkillName(id) if core then return core("skill", id) end end
-- type, template id, id, count, cooldown value, cooldown max, shown
function AFCore.Slot(dialog, index, prefix) if core then return core("slot", dialog, index, prefix) end end
-- value, max, shown (for example the native cast bars' "delay" gauges)
function AFCore.Gauge(dialog, widget) if core then return core("gauge", dialog, widget) end end
function AFCore.Text(dialog, widget) if core then return core("text", dialog, widget) end end
-- absolute native rectangle in screen pixels: x, y, width, height, shown (widget optional)
function AFCore.Rect(dialog, widget, kind) if core then return core("rect", dialog, widget, kind) end end
-- engine UI scale, screen width, screen height
function AFCore.Scale() if core then return core("scale") end end
-- Native dialog: layout name, x/y/width/height (pixels), shown, class,
-- instance identity (session only) and native flags (movable bit 0x8).
function AFCore.Dialog(dialog) if core then return core("dialog", dialog) end end
-- interface placement of a native dialog (pixels); returns true when applied
function AFCore.Move(dialog, x, y, width, height) if core then return core("move", dialog, x, y, width, height) end end
--- keep a native dialog's spot (pixels) when the game re-places it itself (the target re-centres
--- on every target change, tooltips follow the mouse). mode: 0 top-left at x,y (default);
--- 1 top-right, 2 bottom-left, 3 bottom-right corner at x,y; 4 x,y added to the game's spot;
--- 5 kept off screen. clamp: keep it inside the screen. width/height above 0 replace the game's size.
--- widget, kind ("button", "container", "flygauge"): pin that widget of the dialog instead (its spot
--- relative to the dialog). Applied at once. AFCore.Pin(dialog[, nil x 6, widget]) releases it, AFCore.Pin() all.
function AFCore.Pin(dialog, x, y, mode, clamp, width, height, widget, kind)
    if core then return core("pin", dialog or 0, x, y, mode or 0, clamp and true or false, width or 0, height or 0, widget, kind) end
end
--- a tooltip held by where the mouse is when the game places it: over a game window the UI spot
--- (umode, ux, uy), else the world spot (wmode, wx, wy); modes as AFCore.Pin, -1 = where the game puts it
function AFCore.TipPin(dialog, wmode, wx, wy, umode, ux, uy, clamp)
    if core then return core("tippin", dialog, wmode, wx, wy, umode, ux, uy, clamp and true or false) end
end
--- the native "movable" flag: the window can be dragged by its frame (the world map)
function AFCore.Movable(dialog, on) if core then return core("movable", dialog, on and true or false) end end
--- whether the dialog takes the mouse at x, y (pixels), as the game asks it
function AFCore.HitTest(dialog, x, y) if core then return core("hittest", dialog, x, y) end end
--- the dialog takes the mouse only over its pinned widgets (the start menu's spread-out buttons)
function AFCore.Gaps(dialog, on) if core then return core("gaps", dialog, on and true or false) end end
--- one line through the chat box as if typed and sent, e.g. "/say .broker"
function AFCore.Command(text) if core then return core("command", text) end end
--- a pin's state: pinned, SetRect calls it caught, times the game recreated the window, shown
function AFCore.PinInfo(dialog, widget) if core then return core("pininfo", dialog, widget) end end
function AFCore.SetShown(dialog, on) if core then return core("setshown", dialog, on and true or false) end end
function AFCore.Shown(dialog) if core then return core("shown", dialog) end end
-- The client's Lua io has no write: store one bounded settings file (name.cfg, at most
-- 32 KiB) in Mods/AetherfallUI. Returns true when written. Read it back with io.open.
function AFCore.SaveFile(name, text) if core then return core("save", name, text) end end
-- Mouse for addon dragging: x, y in screen pixels (the engine's screen size), left button
-- down, Shift down; nil when the game window is not in front.
function AFCore.Cursor()
    if not core then return end
    local x, y, cw, ch, down, shift = core("cursor")
    if not x or not cw or cw <= 0 or ch <= 0 then return end
    local _, sw, sh = AFCore.Scale()
    sw, sh = tonumber(sw) or cw, tonumber(sh) or ch
    return x * sw / cw, y * sh / ch, down, shift
end
-- Contents of Mods/AetherfallUI/<name> (a .cfg file or state.json, at most 64 KiB), or nil.
function AFCore.ReadFile(name) if core then return core("read", name) end end

local function say(text)
    DisplayChatMessage(0, tostring(text), 0.56, 0.89, 0.93)
end

local function list(...)
    local out = ""
    for i = 1, select("#", ...) do out = out .. (i > 1 and ", " or "") .. tostring((select(i, ...))) end
    return out
end

local function auras(unit)
    local count = AFCore.NumAuras(unit)
    say(unit .. " auras: " .. (count == nil and "unavailable" or tostring(count)))
    for i = 1, math.min(count or 0, 8) do say("  " .. i .. ": " .. list(AFCore.Aura(unit, i))) end
end

function AFCore.Report(arg)
    if not core then say("Aetherfall Core: bridge not loaded") return end
    local D = AFCore.DIALOG
    -- "/afcore open" and "/afcore move" call the interface commands directly (bypassing chat dispatch).
    arg = tostring(arg or ''):lower():match('^%s*(%a*)')
    if arg == 'test' then AFCore.SelfTest(); return end
    if arg == 'watch' then
        if AFUI then AFUI.watchUntil, AFUI.watchSeen = (AFUI.ticks or 0) + 300, {} end
        say("Aetherfall watch: for 15 seconds every game window that opens is noted. Hover a button, an item, a skill, an NPC and a monster.")
        return
    end
    if arg == 'open' or arg == 'move' then
        if AFUI and AFUI.Request then AFUI.Request(arg) end
        say("Aetherfall Interface " .. arg .. ": requested (runs on the next interface tick)")
        return
    end
    say("Aetherfall Core: " .. list(AFCore.Version()))
    say("money: " .. list(AFCore.Money()))
    say("exp (cur, max, recoverable, repose, repose max): " .. list(AFCore.Exp()))
    say("fps: " .. list(AFCore.FPS()))
    say("target: " .. list(AFCore.Unit("target")))
    say("target of target: " .. list(AFCore.Unit("targettarget")))
    auras("player")
    auras("target")
    for i = 1, 3 do say("bar1 slot " .. i .. " (type, template, id, count, cd, cd max, shown): " .. list(AFCore.Slot(D.shortcut, i))) end
    say("player cast: " .. list(AFCore.Gauge(D.skill_delay, "delay")) .. " / " .. list(AFCore.Text(D.skill_delay, "name")))
    say("target cast: " .. list(AFCore.Gauge(D.target_delay, "delay")) .. " / " .. list(AFCore.Text(D.target_delay, "name")))
    -- Interface diagnostics: what loaded and why something is missing.
    local function has(name) return _G[name] ~= nil and 'yes' or 'NO' end
    say("plugin: options window " .. has('AFUIOptions') .. ", movers " .. has('AFUIMovers') .. ", data bar " .. has('AFHTop')
        .. ", options code " .. has('AFUI') .. ", HUD code " .. has('AFHUD') .. ", design " .. has('AFUI_DESIGN'))
    say("files: io " .. (io and io.open and 'yes' or 'NO') .. ", mod folder " .. AFCore.ModPath()
        .. ", layout state " .. tostring(AFHUD_Active and AFHUD_Active()))
    if AFHUD then say("HUD: active " .. tostring(AFHUD.on) .. ", ticks " .. tostring(AFHUD.ticks) .. ", error " .. tostring(AFHUD.error)
        .. ", timer labels skipped " .. tostring(AFHUD.skipped)) end
    if WardrobeNative_TimerStatus then
        local ticks,resumes,why=WardrobeNative_TimerStatus()
        say('shared timer: ticks '..tostring(ticks)..', resumes '..tostring(resumes)..', error '..tostring(why))
    end
    -- Aura icon geometry: window rect, then first slot rect (absolute) for player and target auras.
    local function geo(id, slot)
        local _, x, y, w, h = AFCore.Dialog(id)
        local sx, sy = AFCore.Rect(id, slot, 'itemview')
        return string.format('%s,%s %sx%s / %s %s,%s', tostring(x), tostring(y), tostring(w), tostring(h), slot, tostring(sx), tostring(sy))
    end
    say("aura icons: buffs " .. geo(D.buff_status, 'buff_item1') .. "; target " .. geo(D.target_buff, 'buff_item1')
        .. "; target frame " .. list(select(2, AFCore.Dialog(D.target))))
    if AFUI then
        say("options: ready " .. tostring(AFUI.ready) .. ", ticks " .. tostring(AFUI.ticks) .. ", profile " .. tostring(AFUI.Get and AFUI.Get('profile'))
            .. ", load error " .. tostring(AFUI.loadError) .. ", apply error " .. tostring(AFUI.enforceError) .. ", save " .. tostring(AFUI.saveError)
            .. ", event " .. tostring(AFUI.eventError) .. ", pins " .. tostring(AFUI.pinError) .. ", suspended " .. tostring(AFUI.suspended)
            .. ", connected " .. tostring(AFUI.connected) .. ", focused " .. tostring(AFUI.focused) .. ", settled " .. tostring(AFUI.settled))
        local function widget(name)
            local wd = _G[name]; if not wd then return name .. ' missing' end
            g_AddonName = 'RelicCalc'
            local ok, v = pcall(function() return wd:IsVisible() end)
            local ok2, a, b, c, d = pcall(function() return wd:GetRect() end)
            return string.format('%s visible %s rect %s', name, tostring(ok and v or v), ok2 and list(a, b, c, d) or tostring(a))
        end
        local mv = AFUI.movers and AFUI.movers[1]
        local cx, cy, down = AFCore.Cursor()
        say("windows: " .. widget('AFUIOptions') .. "; " .. widget('AFUIMovers') .. "; " .. widget('AFUIMove1') .. "; mover1 "
            .. (mv and string.format('at %.0f,%.0f', mv.x, mv.y) or 'none') .. ", movers " .. #(AFUI.movers or {})
            .. ", dragging " .. tostring(AFUI.drag and AFUI.drag.i) .. ", cursor " .. tostring(cx and math.floor(cx)) .. ","
            .. tostring(cy and math.floor(cy)) .. " " .. tostring(down) .. ", slash error " .. tostring(AFUI.slashError))
        -- Plugin layout check: the first and last pieces of our layout against a plugin window that works.
        say("layout: " .. widget('PlayerBotBar') .. "; " .. widget('AFHTop') .. "; " .. widget('AFHChat') .. "; "
            .. widget('AFUITab1') .. "; " .. widget('AFUIMove40'))
        -- Chat dispatch: does the client's slash lookup reach our handlers, and did they run?
        local function found(cmd)
            if type(IsSlashCmd) ~= 'function' then return 'no IsSlashCmd' end
            local ok, b, f = pcall(IsSlashCmd, cmd)
            if not ok then return 'lookup error ' .. tostring(b) end
            return (f == AFUI.SlashOpen and 'options') or (f == AFUI.SlashMove and 'movers') or (f and 'other handler') or 'none'
        end
        say("commands: /afui -> " .. found('/afui') .. ", /moveui -> " .. found('/moveui') .. "; requests " .. tostring(AFUI.requests or 0) .. ", opened "
            .. tostring(AFUI.opened or 0) .. ", unlocks " .. tostring(AFUI.unlocks or 0) .. ", open " .. tostring(AFUI.open)
            .. ", unlocked " .. tostring(AFUI.unlocked) .. ", clicks " .. tostring(AFUI.clicks or 0) .. " (last "
            .. tostring(AFUI.lastClick) .. ", tab " .. tostring(AFUI.tab) .. "), built " .. (function()
                local c = 0; for _ in pairs(AFUI.built or {}) do c = c + 1 end
                return c .. '/' .. tostring(AFUI_LAZY and #AFUI_LAZY) end)())
    end
    -- All plugins share 20 window slots (0x20e..0x221); a window that finds none is deleted.
    local used, names = 0, {}
    for id = 0x20e, 0x221 do
        local name = AFCore.Dialog(id)
        if name then used = used + 1; names[#names + 1] = name end
    end
    say("plugin window slots: " .. used .. "/20: " .. table.concat(names, ', '))
    local state = AFCore.ReadFile('state.json')
    say("state.json (native read): " .. (state and (#state .. ' bytes, applied ' .. tostring(state:match('"Applied"%s*:%s*"(%a+)"'))) or 'unreadable'))
    local positions = {}
    for _, id in ipairs({D.basic_status, D.target, D.radar, D.start, D.shortcut, D.chat}) do
        local name, x, y, w, h, shown = AFCore.Dialog(id)
        if name then positions[#positions + 1] = string.format('%s %d,%d %dx%d%s', name:gsub('_dialog', ''), x, y, w, h, shown and '' or ' hidden') end
    end
    say("windows: " .. table.concat(positions, '; '))
    -- Tooltips: whether the game places them through the pinned SetRect, and whether it recreates them.
    local tips = {}
    for id in pairs(AFUI and AFUI.tooltips or {}) do
        local name, x, y, w, h, shown = AFCore.Dialog(id)
        local pinned, calls, recreated = AFCore.PinInfo(id)
        tips[#tips + 1] = string.format('0x%x %s %d,%d %dx%d%s pinned %s caught %s recreated %s', id, name ~= '' and name or '(text)',
            x or 0, y or 0, w or 0, h or 0, shown and '' or ' hidden', tostring(pinned), tostring(calls), tostring(recreated))
    end
    say("tooltips: " .. (#tips > 0 and table.concat(tips, '; ') or 'none found'))
    local s, sw, sh = AFCore.Scale()
    say(string.format("screen %sx%s, engine scale %s, Lua scale %s", tostring(sw), tostring(sh), tostring(s), tostring(GetUIScale and GetUIScale())))
end

-- In-game proof of the native parts (the offline checks only simulate the game): PASS / FAIL lines.
function AFCore.SelfTest()
    local function line(ok, what, detail) say((ok and 'PASS ' or 'FAIL ') .. what .. (detail and (': ' .. detail) or '')) end
    line(core ~= nil, 'bridge', core and tostring(AFCore.Version()) or 'not registered')
    if not core then return end
    -- The pin hook on a real window: the interface's own minimap label, pinned by its top-right corner
    -- (mode 1), then moved: the game's SetRect must land on the pinned corner.
    local map
    for id = 0x20e, 0x221 do if AFCore.Dialog(id) == 'AFHMap' then map = id end end
    if map then
        local _, x0, y0, w0 = AFCore.Dialog(map)
        local accepted = AFCore.Pin(map, 500, 300, 1, false)
        AFCore.Move(map, 100, 100)
        local _, x1, y1 = AFCore.Dialog(map)
        AFCore.Pin(map); AFCore.Move(map, x0, y0)
        line(math.abs(x1 + w0 - 500) < 1 and math.abs(y1 - 300) < 1, 'pin hook (window SetRect)',
             string.format('%d,%d%s', x1 or -1, y1 or -1, accepted and '' or ', pin refused'))
    else line(false, 'pin hook', 'AFHMap window not found') end
    -- Start menu: the mouse is taken over a button and passes through a gap.
    local s, sw, sh = AFCore.Scale()
    local menuOk, detail = false, 'start menu not held'
    if AFUI and AFUI.ButtonRects then
        local rects, frame = AFUI.ButtonRects()
        if frame and #rects > 0 then
            local b = rects[1]
            local over = AFCore.HitTest(AFCore.DIALOG.start, b[1] + b[3] / 2, b[2] + b[4] / 2)
            local gap
            for gy = frame[2] + 1, frame[2] + frame[4] - 1, 4 do
                for gx = frame[1] + 1, frame[1] + frame[3] - 1, 4 do
                    local inside = false
                    for _, r in ipairs(rects) do if gx >= r[1] and gx < r[1] + r[3] and gy >= r[2] and gy < r[2] + r[4] then inside = true end end
                    if not inside then gap = {gx, gy}; break end
                end
                if gap then break end
            end
            local through = gap and AFCore.HitTest(AFCore.DIALOG.start, gap[1], gap[2])
            menuOk = over == true and (not gap or through == false)
            detail = 'over a button ' .. tostring(over) .. ', in a gap ' .. tostring(through)
        end
    end
    line(menuOk, 'start menu (buttons take clicks, gaps pass)', detail)
    -- Every window the interface holds must have its pin (a refused one is only put back a tick later).
    local refused = {}
    for k in pairs(AFUI and AFUI.pins or {}) do
        local id, widget = k, nil
        if type(k) == 'string' then id, widget = k:match('^(%d+):(.+)$'); id = tonumber(id) end
        local ok, why = AFCore.PinInfo(id, widget)
        if id and not ok then
            refused[#refused + 1] = (widget and string.format('0x%x %s', id, widget) or string.format('0x%x', id)) .. (type(why) == 'string' and (' (' .. why .. ')') or '')
        end
    end
    table.sort(refused)
    line(#refused == 0, 'held windows pinned', #refused == 0 and 'all' or table.concat(refused, ', '))
    local chat = AFCore.Dialog(0xcf)
    line(chat == 'chat_input_dialog', 'chat box for the Broker button', tostring(chat))
    local kinds = {}
    for id, kind in pairs(AFUI and AFUI.tooltips or {}) do kinds[#kinds + 1] = string.format('0x%x %s', id, kind) end
    line(#kinds > 0, 'tooltip windows found', table.concat(kinds, ', '))
    line(AFUI and AFUI.mapId ~= nil, 'world map found', AFUI and AFUI.mapId and string.format('0x%x', AFUI.mapId) or 'open the map once')
end

if SlashCmdList then
    SlashCmdList["AETHERFALLCORE"] = AFCore.Report
    SLASH_AETHERFALLCORE1 = "/afcore"
end
