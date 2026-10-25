-- Aetherfall HUD data layer (ElvUI-style data texts and aura timers).
-- Active only while the Aetherfall Interface "layout" state is applied: the
-- native HUD is rewritten by that state; this layer adds only what native
-- windows cannot show, read through the AetherfallCore bridge (AFCore):
-- the top data bar, the chat data line, minimap zone/coordinates and remaining
-- time under native buff/debuff icons. Every label is a small not_active
-- dialog beside, never over, a native widget. Settings: AetherfallOptions.lua
-- (/afui). Cooldown numbers use the native show_cooltime option.

AFHUD = {ticks = 0, on = false, labels = {}, text = {}, shown = {}}
local H = AFHUD
local D = AFCore and AFCore.DIALOG or {}
local POOL = 0  -- no timer labels: plugin windows are limited (20 slots for all plugins) and the game shows buff times
-- Colour themes {labels, values, accent}: data texts, status line, minimap label and the
-- options window's highlights. Each is clearly different (ElvUI's value colour idea).
local THEME = {
    {'0.875,0.914,0.941,1', '0.561,0.890,0.925,1', '0.561,0.890,0.925,1'},  -- Aether: ice / aether cyan
    {'0.945,0.863,0.659,1', '0.839,0.722,0.471,1', '0.839,0.722,0.471,1'},  -- Gold
    {'0.800,0.890,1.000,1', '1.000,1.000,1.000,1', '0.600,0.780,1.000,1'},  -- Ice: pale blue / white
    {'1.000,0.820,0.640,1', '1.000,0.560,0.300,1', '1.000,0.560,0.300,1'},  -- Ember: peach / orange
    {'0.760,0.950,0.800,1', '0.420,0.880,0.560,1', '0.420,0.880,0.560,1'},  -- Jade: mint / green
}
function AFHUD_Accent() local t = THEME[AFUI and AFUI.Get and AFUI.Get('theme') or 1] or THEME[1]; return t[3] end
local FALL = '0.910,0.627,0.557,1'
-- Client font names (used by the game's own layouts); labels in the options window.
local FONTS = {'ui_default', 'v3_option', 'v3_value', 'ui_static_title', 'v3_option_title', 'window', 'name', 'button'}
local DEFAULTS = {databar = 1, chatline = 1, minimapinfo = 1, timers = 1, targettimers = 1, warn = 10, font = 1, theme = 1, alpha = 90, dl1 = 'zone', dl2 = 'coords', dl3 = 'time', dc1 = 'xp', dc2 = 'kinah', dc3 = 'off',
                  dr1 = 'fps', dr2 = 'group', dr3 = 'legion'}

local function opt(k) if AFUI and AFUI.Get then return AFUI.Get(k) end return DEFAULTS[k] end
local function w(id) return _G['AFH' .. id] end
local function call(name, ...)
    local f = _G[name]; if type(f) ~= 'function' then return nil end
    local ok, a, b, c = pcall(f, ...); if ok then return a, b, c end
end
local function n(v) v = tonumber(v); if v and v == v and math.abs(v) < 1e15 then return v end end
local function scale() return math.max(.5, math.min(2, n(call('GetUIScale')) or 1)) end
local function colours() return THEME[opt('theme')] or THEME[1] end

local function show(id, on)
    if H.shown[id] == on and (w(id):IsVisible() == 1) == on then return end
    H.shown[id] = on
    if on then w(id):Show() else w(id):Hide() end
end
local function settext(id, s, colour)
    s = tostring(s or '')
    if H.text[id] ~= s then w(id):SetText(s); H.text[id] = s end
    if colour and H.text[id .. '#'] ~= colour then w(id):SetAttr('textColor', colour); H.text[id .. '#'] = colour end
end
-- Native rectangles are screen pixels; Lua rectangles are pixels / UI scale.
local function place(id, x, y, width, height)
    local s = scale(); local key = string.format('%d,%d,%d,%d', x, y, width, height)
    if H.text[id .. '@'] ~= key then w(id):SetRect(x / s, y / s, width / s, height / s); H.text[id .. '@'] = key end
end

function AFHUD_Active()
    if not (AFCore and AFCore.Loaded()) then return false end
    -- Read natively: the client's io goes through its pak file system.
    local s = AFCore.ReadFile and AFCore.ReadFile('state.json')
    if not s and io and io.open then
        local f = io.open(AFCore.ModPath('state.json'), 'r')
        if f then s = f:read('*a'); f:close() end
    end
    return type(s) == 'string' and s:find('"Applied"%s*:%s*"layout"') ~= nil
end

local function thousands(v)
    local s = string.format('%d', math.floor(v + .5))
    while true do local k; s, k = s:gsub('^(-?%d+)(%d%d%d)', '%1,%2'); if k == 0 then return s end end
end
local TARGET_PANELS = {'container1', 'container2', 'container3', 'container4', 'container7'}
local function resourceValue(cur, max)
    cur, max = n(cur), n(max)
    return cur and max and max > 0 and (thousands(cur) .. '/' .. thousands(max)) or ''
end
-- Labels are children of each native frame, after its bars in drawing order.
-- A separate plugin dialog cannot guarantee foreground order against native HUD.
-- The native target record has
-- HP/MP but no DP; self-target DP comes from the player's native DP gauge.
-- Unavailable resources keep their row empty, including on monster targets.
function AFHUD_TargetDetails()
    if not w('Target') or not AFCore.SetText then return end
    local assistName, _, _, _, _, assistShown = AFCore.Dialog(D.target_assist)
    local assistId, _, _, assistHP, assistMax = AFCore.Unit('targettarget')
    local assistActive = assistShown and assistName == 'target_assist_dialog' and assistId and assistId > 0
    AFCore.SetText(D.target_assist, 'container/af_resource_hp', assistActive and resourceValue(assistHP, assistMax) or '')
    -- Retain the existing passive DP fill, but never leave duplicate text in
    -- the old cross-dialog overlay, even after a reconnect or target change.
    settext('TargetHP', '')
    settext('TargetMP', '')
    settext('TargetDP', '')
    if H.targetWidget ~= w('TargetDPFill') then
        w('TargetDPFill'):SetUIImage(0, 'Textures/UI/AetherfallMovers/target_dp.dds')
        H.targetWidget = w('TargetDPFill')
    end
    local name, x, y, width, height, shown = AFCore.Dialog(D.target)
    local id, unit, level, hp, hpmax, mp, mpmax = AFCore.Unit('target')
    local active = shown and id and id > 0 and name == 'target_dialog'
    if not active then show('Target', false); return end
    -- Plugin dialogs have native saved/automatic window placement. SetRect
    -- alone can be superseded when Show restores that placement. Keep this
    -- passive readout over the native target using queued widget operations;
    -- refresh even when the target itself has not moved, and force after Show.
    local s = scale()
    w('Target'):SetRect(x / s, y / s, width / s, height / s)
    show('Target', true)
    w('Target'):SetPosForced(x / s, y / s)
    local dp, dpmax
    if AFCore.PlayerId and id == AFCore.PlayerId() then dp, dpmax = AFCore.Gauge(D.basic_status, 'dp_gauge_text') end
    dp, dpmax = n(dp), n(dpmax)
    local available = dp and dpmax and dpmax > 0
    local hpText, mpText = resourceValue(hp, hpmax), resourceValue(mp, mpmax)
    for _, panel in ipairs(TARGET_PANELS) do
        AFCore.SetText(D.target, panel .. '/af_resource_hp', hpText)
    end
    AFCore.SetText(D.target, 'container7/af_resource_mp', mpText)
    AFCore.SetText(D.target, 'container7/af_resource_dp', available and resourceValue(dp, dpmax) or '')
    if available and dp > 0 then
        w('TargetDPFill'):SetRect(6, 55, 239 * math.max(0, math.min(1, dp / dpmax)), 13)
        w('TargetDPFill'):Show()
    else w('TargetDPFill'):Hide() end
end
local function clock(sec)
    if not sec then return '' end
    if sec >= 3600 then return string.format('%dh', math.floor(sec / 3600 + .5)) end
    if sec >= 600 then return string.format('%dm', math.floor(sec / 60 + .5)) end
    if sec >= 60 then return string.format('%d:%02d', math.floor(sec / 60), math.floor(sec % 60)) end
    return string.format('%d', math.floor(sec + .5))
end

-- ---------- data texts ----------
local function experience()
    local cur, max, rec, rep, repmax = AFCore.Exp()
    cur, max = n(cur), n(max)
    if not cur or not max or max <= 0 then return nil end
    local s = string.format('XP %.1f%%', 100 * cur / max)
    if n(rec) and rec > 0 then s = s .. string.format('  (recoverable %.1f%%)', 100 * rec / max) end
    if n(rep) and n(repmax) and repmax > 0 and rep > 0 then s = s .. string.format('  repose %d%%', math.floor(100 * rep / repmax + .5)) end
    return s
end
local SOURCES = {
    zone = function() local z = call('GetZoneText'); return z and tostring(z) end,
    coords = function()
        local x, y = call('GetPlayerMapPosition', 'player')
        if n(x) and n(y) then return string.format('%d, %d', math.floor(x + .5), math.floor(y + .5)) end
    end,
    time = function() local t = call('GetTime'); return t and tostring(t) end,
    xp = experience,
    kinah = function() local m = n(AFCore.Money()); return m and (thousands(m) .. ' Kinah') end,
    fps = function() local f = n(AFCore.FPS()); return f and string.format('%d fps', math.floor(f + .5)) end,
    group = function() local g = n(call('GetNumPartyMembers')); return g and g > 0 and ('Group ' .. g) or nil end,
    legion = function() local l = n(call('GetNumLegionMembers')); return l and l > 0 and ('Legion ' .. l) or nil end,
    level = function() local l = n(call('UnitLevel', 'player')); return l and ('Level ' .. l) end,
    hp = function()
        local hp, max = n(call('UnitHP', 'player')), n(call('UnitHPMax', 'player'))
        return hp and max and max > 0 and string.format('HP %s / %s', thousands(hp), thousands(max)) or nil
    end,
}
local function compose(prefix)
    local out = {}
    for i = 1, 3 do
        local src = SOURCES[opt(prefix .. i)]
        local ok, s = pcall(function() return src and src() end)
        -- GetTime uses the client's full date string, including its trailing
        -- newline. Static widgets still honour explicit line breaks even with
        -- the no-wrap flags, so keep every source on the data bar's single row.
        if ok and s then
            s = tostring(s):gsub('[\r\n\t]+', ' '):gsub('^%s+', ''):gsub('%s+$', '')
            if s ~= '' then out[#out + 1] = s end
        end
    end
    return table.concat(out, '    ')
end

local function databar()
    if opt('databar') ~= 1 then show('Top', false) return end
    local s = scale(); local sw = (n(select(2, AFCore.Scale())) or 1280 * s)
    place('Top', 0, 0, sw, 18 * s)
    -- The dialog spans the screen; its children do not resize with it. Give
    -- each text group its own third, with padding, instead of the overlapping
    -- fixed 1280px layout (left 8..428, centre 330..950, right 852..1272).
    local width = sw / s
    local column = width / 3
    place('TopLeft', 8 * s, 0, (column - 16) * s, 18 * s)
    place('TopCenter', (column + 8) * s, 0, (column - 16) * s, 18 * s)
    place('TopRight', (2 * column + 8) * s, 0, (column - 16) * s, 18 * s)
    local c = colours()
    settext('TopLeft', compose('dl'), c[1]); settext('TopCenter', compose('dc'), c[2]); settext('TopRight', compose('dr'), c[1])
    show('Top', true)
end

-- Status line at a fixed spot: the strip the layout keeps free under the chat input, above
-- the XP bar (reference 8,935 361x18, bottom-left anchored). It no longer follows the input.
local STATUS_X, STATUS_FROM_BOTTOM, STATUS_W, STATUS_H = 8, 960 - 935, 361, 18
local function chatpanel()
    if opt('chatline') ~= 1 then show('Chat', false) return end
    local s = scale(); local _, _, sh = AFCore.Scale(); sh = n(sh) or 960 * s
    place('Chat', STATUS_X * s, sh - STATUS_FROM_BOTTOM * s, STATUS_W * s, STATUS_H * s)
    local parts = {}
    for _, k in ipairs({'level', 'hp', 'xp'}) do local v = SOURCES[k](); if v then parts[#parts + 1] = v end end
    settext('ChatText', table.concat(parts, '    '), colours()[1])
    show('Chat', true)
end

local function minimapinfo()
    local x, y, wd, ht, visible = AFCore.Rect(D.radar or 0x0e, 'af_minimap_border', 'any')
    if opt('minimapinfo') ~= 1 or not (n(x) and visible) then show('Map', false) return end
    local s = scale()
    local labelY = y + ht + s -- coordinates belong only to the map mover
    place('Map', x, labelY, wd, 16 * s)
    local parts = {}
    for _, k in ipairs({'coords'}) do local v = SOURCES[k](); if v then parts[#parts + 1] = v end end
    settext('MapText', table.concat(parts, '  '), colours()[3])
    show('Map', true)
    -- Show can restore a plugin dialog's previous native position. Always
    -- anchor after it, including a stationary map following a world change.
    w('Map'):SetPosForced(x / s, labelY / s)
end

-- ---------- aura timers under native icons ----------
local function remaining(unit)
    local out = {}
    for i = 1, (AFCore.NumAuras(unit) or 0) do
        local skill, _, _, _, left, total = AFCore.Aura(unit, i)
        if skill and left then
            local prev = out[skill]
            if not prev or left < prev[1] then out[skill] = {left, total} end
        end
    end
    return out
end

local function auratimers()
    if POOL == 0 then return end
    local used = 0
    local player, target = opt('timers') == 1, opt('targettimers') == 1
    local lists = {player = player and remaining('player') or {}, target = target and remaining('target') or {}}
    local warn = opt('warn')
    -- A label goes only under an icon that is where its window is: inside the player's buff
    -- window, or around the target frame for target auras. Anything else is skipped (and
    -- counted for /afcore) rather than drawn at a wrong place.
    local s = scale()
    local function area(id, padx, pady)
        local x, y, wd, ht = AFCore.Rect(id)
        if not (n(x) and n(wd) and wd > 0 and ht > 0) then return end
        return x - padx, y - pady, x + wd + padx, y + ht + pady
    end
    local zones = {player = {}, target = {}}
    zones.player[D.buff_status] = {area(D.buff_status, 4 * s, 4 * s)}
    zones.player[D.debuff_status] = {area(D.debuff_status, 4 * s, 4 * s)}
    zones.target[D.target_buff] = {area(D.target, 260 * s, 160 * s)}
    H.skipped = 0
    local function slots(dialog, prefix, count, unit, debuff)
        local z = zones[unit][dialog]
        for i = 1, count do
            if used >= POOL then return end
            local kind, template = AFCore.Slot(dialog, i, prefix)
            if not template or template <= 0 then return end
            local x, y, wd, ht, visible = AFCore.Rect(dialog, prefix .. i, 'itemview')
            local t = lists[unit][template]
            local inside = n(x) and z and z[1] and x + wd / 2 >= z[1] and x + wd / 2 <= z[3] and y + ht / 2 >= z[2] and y + ht / 2 <= z[4]
            if visible and t and not inside then H.skipped = H.skipped + 1 end
            if visible and n(x) and t and inside then
                used = used + 1
                local id = 'T' .. used
                place(id, x - 4, y + ht, wd + 8, 12 * scale())
                settext(id .. 'Text', clock(t[1]), (debuff or t[1] < warn) and FALL or colours()[2])
                show(id, true)
            end
        end
    end
    if player then
        slots(D.buff_status, 'buff_item', 24, 'player', false)
        slots(D.debuff_status, 'debuff_item', 24, 'player', true)
    end
    if target then
        slots(D.target_buff, 'buff_item', 20, 'target', false)
        slots(D.target_buff, 'debuff_item', 20, 'target', true)
    end
    for i = used + 1, POOL do show('T' .. i, false) end
end

local function hideall()
    show('Top', false); show('Chat', false); show('Map', false)
    if w('Target') then show('Target', false) end
    for i = 1, POOL do show('T' .. i, false) end
end

-- Live style changes from the options window.
function AFHUD_Apply()
    local font = FONTS[opt('font')] or 'ui_default'
    for _, id in ipairs({'TopLeft', 'TopCenter', 'TopRight', 'ChatText', 'MapText'}) do
        pcall(function() w(id):SetFont(font) end)
        H.text[id .. '#'] = nil
    end
    -- Backdrop opacity only (text stays solid), as ElvUI's backdrop transparency.
    for _, id in ipairs({'Top', 'Chat', 'Map'}) do
        pcall(function()
            local group=id=='Chat' and 'chatalpha' or nil
            w(id):SetAlpha(AFUI and AFUI.BackgroundAlpha and AFUI.BackgroundAlpha(group) or opt('alpha')/100,false)
        end)
    end
    if H.on then AFHUD_Update() end
end

function AFHUD_Update()
    if not H.on then return end
    if AFUI and AFUI.ready ~= nil and (not AFUI.ready or not AFUI.settled) then return end
    local ok, why = pcall(function() databar(); chatpanel(); minimapinfo(); auratimers(); AFHUD_TargetDetails() end)
    if not ok then H.error = why end
end
function AFHUD_Invalidate() H.shown = {}; H.text = {} end

function AFHUD_OnEvent(event, ...)
    g_AddonName = 'RelicCalc'
    if event == 'ADDON_TIMER' then
        H.ticks = H.ticks + 1
        local name = call('UnitName', 'player')
        local connected = type(name) == 'string' and name ~= ''
        if H.ticks % 100 == 1 or H.character ~= name or (H.on and not connected) then
            H.character = name
            local on = connected and AFHUD_Active()
            if on ~= H.on then H.on = on; if on then AFHUD_Apply() else hideall() end end
        end
        if H.on and H.ticks % 5 == 0 then AFHUD_Update() end
    elseif event == 'PLAYER_ENTERING_WORLD' then
        H.ticks = 0; AFHUD_Invalidate()
        if w('Target') then show('Target', false) end
    elseif event == 'USER_CHANGED_TARGET' and H.on then
        AFHUD_TargetDetails()
    end
end

-- Keep the event in a function argument: native widget calls can synchronously
-- dispatch another event and replace the XML script's global `event`. Each
-- controller must still receive the original timer/world event, even if the
-- other controller raises an error while the game rebuilds its windows.
function AFHUD_Dispatch(event)
    local ok, why = pcall(AFUI_OnEvent, event)
    if not ok and AFUI then
        local message = tostring(why)
        if AFUI.eventError ~= message then
            g_AddonName = 'RelicCalc'
            DisplayChatMessage(0, 'Aetherfall controls: ' .. message, .91, .63, .56)
        end
        AFUI.eventError = message
    end
    local hudOK, hudWhy = pcall(AFHUD_OnEvent, event)
    if not hudOK then H.error = tostring(hudWhy) end
end

function AFHUD_OnLoad()
    g_AddonName = 'RelicCalc'
    for _, id in ipairs({'Top', 'Chat', 'Map'}) do w(id):Hide(); H.shown[id] = false end
    if w('Target') then
        w('Target'):Hide(); H.shown.Target = false
        w('TargetDPFill'):SetUIImage(0, 'Textures/UI/AetherfallMovers/target_dp.dds')
    end
    for i = 1, POOL do w('T' .. i):Hide(); H.shown['T' .. i] = false end
    for _, event in ipairs({'ADDON_TIMER', 'PLAYER_ENTERING_WORLD', 'USER_CHANGED_TARGET'}) do w('Top'):RegisterEvent(event) end
    -- RelicCalc's Wardrobe owns the shared .05 second timer. Never reset it.
end
