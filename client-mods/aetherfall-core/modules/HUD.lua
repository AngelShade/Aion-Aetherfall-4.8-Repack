-- Data texts and passive HUD overlays. Feature state belongs to this module.
local DATA = {'zone', 'coords', 'time', 'xp', 'kinah', 'fps', 'group', 'legion', 'level', 'hp', 'off'}
local M = AF:NewModule('HUD', {
    needs = {'Lifecycle', 'Opacity', 'TargetDetails', 'Identity'},
    defaults = {
        databar = {1, 'toggle'}, chatline = {1, 'toggle'}, minimapinfo = {1, 'toggle'},
        timers = {1, 'toggle'}, targettimers = {1, 'toggle'}, warn = {10, 'int', 0, 60, 1},
        font = {1, 'choice', {'Default', 'Option', 'Value', 'Title', 'Large title', 'Window', 'Name', 'Button'}},
        theme = {1, 'choice', {'Aether', 'Gold', 'Ice', 'Ember', 'Jade'}},
        dl1 = {'zone', 'enum', DATA}, dl2 = {'coords', 'enum', DATA}, dl3 = {'time', 'enum', DATA},
        dc1 = {'xp', 'enum', DATA}, dc2 = {'kinah', 'enum', DATA}, dc3 = {'off', 'enum', DATA},
        dr1 = {'fps', 'enum', DATA}, dr2 = {'group', 'enum', DATA}, dr3 = {'legion', 'enum', DATA},
    },
})
local DATA_LABEL = {zone = 'Zone', coords = 'Coordinates', time = 'Time', xp = 'Experience', kinah = 'Kinah', fps = 'FPS',
                    group = 'Group', legion = 'Legion', level = 'Level', hp = 'Health', off = 'Off'}
AF.Config:Register(M, {
    {name = 'General', order = 1, hint = 'Opacity fades window and HUD frame backgrounds; text stays visible.', rows = {
        {label = 'Data text font', order = 1, k = 'font', kind = 'choice'},
        {label = 'Colour theme', order = 2, k = 'theme', kind = 'choice'},
    }},
    {name = 'Chat', order = 4, hint = 'Chat background opacity is independent; text stays solid.', rows = {
        {label = 'Status line', order = 1, k = 'chatline', kind = 'toggle'},
    }},
    {name = 'Data Texts', order = 5, rows = {
        {label = 'Top bar', order = 1, k = 'databar', kind = 'toggle'},
        {label = 'Left 1', order = 2, k = 'dl1', kind = 'data', labels = DATA_LABEL},
        {label = 'Left 2', order = 3, k = 'dl2', kind = 'data', labels = DATA_LABEL},
        {label = 'Left 3', order = 4, k = 'dl3', kind = 'data', labels = DATA_LABEL},
        {label = 'Centre 1', order = 5, k = 'dc1', kind = 'data', labels = DATA_LABEL},
        {label = 'Centre 2', order = 6, k = 'dc2', kind = 'data', labels = DATA_LABEL},
        {label = 'Centre 3', order = 7, k = 'dc3', kind = 'data', labels = DATA_LABEL},
        {label = 'Right 1', order = 8, k = 'dr1', kind = 'data', labels = DATA_LABEL},
        {label = 'Right 2', order = 9, k = 'dr2', kind = 'data', labels = DATA_LABEL},
        {label = 'Right 3', order = 10, k = 'dr3', kind = 'data', labels = DATA_LABEL},
    }},
    {name = 'Minimap', order = 7, hint = 'Move the map and its zoom, clock and zone name with /moveui.', rows = {
        {label = 'Coordinates', order = 1, k = 'minimapinfo', kind = 'toggle'},
    }},
})
local H = {ticks = 0, on = false, labels = {}, text = {}, shown = {}}
local D = AFCore and AFCore.DIALOG or {}
local POOL = 0 -- Native buff times avoid consuming additional plugin windows.
local THEME = {
    {'0.875,0.914,0.941,1', '0.561,0.890,0.925,1', '0.561,0.890,0.925,1'},
    {'0.945,0.863,0.659,1', '0.839,0.722,0.471,1', '0.839,0.722,0.471,1'},
    {'0.800,0.890,1.000,1', '1.000,1.000,1.000,1', '0.600,0.780,1.000,1'},
    {'1.000,0.820,0.640,1', '1.000,0.560,0.300,1', '1.000,0.560,0.300,1'},
    {'0.760,0.950,0.800,1', '0.420,0.880,0.560,1', '0.420,0.880,0.560,1'},
}
local FONTS = {'ui_default', 'v3_option', 'v3_value', 'ui_static_title', 'v3_option_title', 'window', 'name', 'button'}
local call, n, scale = AF.Native.Call, AF.Native.Number, AF.Native.Scale
local function opt(k) return M.db[k] end
local function w(id) return AF.Native.Widget('H', id) end
local function colours() return THEME[opt('theme')] or THEME[1] end

function M:Accent() return colours()[3] end
function M:IsOn() return H.on end
function M:HasMinimapInfo() return opt('minimapinfo') == 1 end
function M:Appearance() return opt('font'), opt('theme') end
function M:Diagnostics() return AF.Copy(H) end

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
-- Native rectangles are screen pixels; plugin rectangles use pixels / UI scale.
local function place(id, x, y, width, height)
    local s = scale(); local key = string.format('%d,%d,%d,%d', x, y, width, height)
    if H.text[id .. '@'] ~= key then w(id):SetRect(x / s, y / s, width / s, height / s); H.text[id .. '@'] = key end
end

function M:Active()
    if not (AFCore and AFCore.Loaded()) then return false end
    local s = AF.DB:ReadLayoutState()
    return type(s) == 'string' and s:find('"Applied"%s*:%s*"layout"') ~= nil
end

local function thousands(v)
    local s = string.format('%d', math.floor(v + .5))
    while true do local k; s, k = s:gsub('^(-?%d+)(%d%d%d)', '%1,%2'); if k == 0 then return s end end
end
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
        -- Stock time strings include line breaks; data texts occupy one row.
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
    local width = sw / s
    local column = width / 3
    place('TopLeft', 8 * s, 0, (column - 16) * s, 18 * s)
    place('TopCenter', (column + 8) * s, 0, (column - 16) * s, 18 * s)
    place('TopRight', (2 * column + 8) * s, 0, (column - 16) * s, 18 * s)
    local c = colours()
    settext('TopLeft', compose('dl'), c[1]); settext('TopCenter', compose('dc'), c[2]); settext('TopRight', compose('dr'), c[1])
    show('Top', true)
end

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
    local labelY = y + ht + s
    place('Map', x, labelY, wd, 16 * s)
    local parts = {}
    for _, k in ipairs({'coords'}) do local v = SOURCES[k](); if v then parts[#parts + 1] = v end end
    settext('MapText', table.concat(parts, '  '), colours()[3])
    show('Map', true)
    w('Map'):SetPosForced(x / s, labelY / s)
end

local function hideall()
    if AFCore.ClearBackdrops then AFCore.ClearBackdrops() end
    show('Top', false); show('Chat', false); show('Map', false)
    AF:Call('TargetDetails', 'Hide')
    AF:Call('Identity', 'Off')
    for i = 1, POOL do show('T' .. i, false) end
end

function M:Apply()
    local font = FONTS[opt('font')] or 'ui_default'
    for _, id in ipairs({'TopLeft', 'TopCenter', 'TopRight', 'ChatText', 'MapText'}) do
        pcall(function() w(id):SetFont(font) end)
        H.text[id .. '#'] = nil
    end
    AF:Call('Opacity', 'Apply')
    if H.on then self:Update() end
end

function M:Update()
    if not H.on then return end
    if not AF:Call('Lifecycle', 'IsReady') or not AF:Call('Lifecycle', 'IsSettled') then return end
    local ok, why = pcall(function()
        databar(); chatpanel(); minimapinfo()
        -- The original aura pool is zero: native icons already show their timers.
        AF:Call('TargetDetails', 'Update')
    end)
    if not ok then H.error = why end
    AF:Call('Identity', 'Update')
end
function M:Invalidate()
    H.shown = {}; H.text = {}
    AF:Call('TargetDetails', 'Invalidate')
end

function M:OnEvent(event, ...)
    g_AddonName = 'RelicCalc'
    if event == 'ADDON_TIMER' then
        H.ticks = H.ticks + 1
        local name = call('UnitName', 'player')
        local connected = type(name) == 'string' and name ~= ''
        if H.ticks % 100 == 1 or H.character ~= name or (H.on and not connected) then
            H.character = name
            local on = connected and self:Active()
            if on ~= H.on then
                H.on = on
                AF:Send('HudChanged', on)
                if on then self:Apply() else hideall() end
            end
        end
        if H.on and H.ticks % 5 == 0 then self:Update() end
    elseif event == 'PLAYER_ENTERING_WORLD' then
        H.ticks = 0; self:Invalidate()
        AF:Call('TargetDetails', 'Hide')
    elseif event == 'USER_CHANGED_TARGET' and H.on then
        AF:Call('TargetDetails', 'Update')
    end
end

function M:OnLoad()
    g_AddonName = 'RelicCalc'
    for _, id in ipairs({'Top', 'Chat', 'Map'}) do w(id):Hide(); H.shown[id] = false end
    AF:Call('TargetDetails', 'OnLoad')
    for i = 1, POOL do w('T' .. i):Hide(); H.shown['T' .. i] = false end
    for _, event in ipairs({'ADDON_TIMER', 'PLAYER_ENTERING_WORLD', 'USER_CHANGED_TARGET'}) do w('Top'):RegisterEvent(event) end
    -- Wardrobe owns the shared .05 second timer. Never reset it.
end
for _, event in ipairs({'ADDON_TIMER', 'PLAYER_ENTERING_WORLD', 'USER_CHANGED_TARGET'}) do M:On(event, M.OnEvent, 10) end
M:Listen('InvalidateHUD',M.Invalidate)
M:Listen('ApplyHUD',M.Apply)
