-- Data texts and passive HUD overlays. Feature state belongs to this module.
local DATA = {'zone', 'coords', 'time', 'xp', 'kinah', 'fps', 'group', 'legion', 'level', 'hp', 'off'}
local knownSources = {}
for _, name in ipairs(DATA) do knownSources[name] = true end
for _, entry in ipairs(AF.DataTexts:Entries()) do
    if not knownSources[entry.name] then DATA[#DATA + 1] = entry.name end
end
local M = AF:NewModule('HUD',{scope='profile',
    needs = {'Lifecycle', 'Opacity', 'TargetDetails', 'Identity'},
    defaults = {
        dlsep1={'','string',16}, dlsep2={'','string',16}, dlsep3={'','string',16},
        dcsep1={'','string',16}, dcsep2={'','string',16}, dcsep3={'','string',16},
        drsep1={'','string',16}, drsep2={'','string',16}, drsep3={'','string',16},
        dataPosition = {1, 'choice', {'Top', 'Bottom'}}, dataHeight = {18, 'int', 14, 32, 1},
        dataWidth = {0, 'int', 0, 3840, 10}, separator = {'    ', 'string', 16},
        coordDecimals = {0, 'int', 0, 3, 1}, abbreviate = {0, 'toggle'},
        clockMode = {1, 'choice', {'Native', '12 hour', '24 hour'}}, clockSeconds = {0, 'toggle'},
        statusFormat = {'[level]    [hp]    [xp]', 'string', 256},
        mapFormat = {'[coords]', 'string', 256}, mapPosition = {1, 'choice', {'Below', 'Above'}},
        statusWidth = {361, 'int', 160, 1000, 10}, statusHeight = {18, 'int', 14, 32, 1},
        statusX = {8, 'int', 0, 1280, 1}, statusBottom = {25, 'int', 0, 720, 1},
        customAccent = {'#8fe3ec', 'color'},
        databar = {1, 'toggle'}, chatline = {1, 'toggle'}, minimapinfo = {1, 'toggle'},
        timers = {1, 'toggle'}, targettimers = {1, 'toggle'}, warn = {10, 'int', 0, 60, 1},
        font = {1, 'choice', {'Default', 'Option', 'Value', 'Title', 'Large title', 'Window', 'Name', 'Button'}},
        theme = {1, 'choice', {'Aether', 'Gold', 'Ice', 'Ember', 'Jade', 'Custom'}},
        dl1 = {'zone', 'enum', DATA}, dl2 = {'coords', 'enum', DATA}, dl3 = {'time', 'enum', DATA},
        dc1 = {'xp', 'enum', DATA}, dc2 = {'kinah', 'enum', DATA}, dc3 = {'off', 'enum', DATA},
        dr1 = {'fps', 'enum', DATA}, dr2 = {'group', 'enum', DATA}, dr3 = {'legion', 'enum', DATA},
    },
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
local function colours()
    if opt('theme') == 6 then
        local hex = opt('customAccent')
        local r, g, b = hex:match('^#(%x%x)(%x%x)(%x%x)$')
        local accent = string.format('%.3f,%.3f,%.3f,1', tonumber(r,16)/255, tonumber(g,16)/255, tonumber(b,16)/255)
        return {THEME[1][1], accent, accent}
    end
    return THEME[opt('theme')] or THEME[1]
end
local function context()
    return {coordDecimals=opt('coordDecimals'), abbreviate=opt('abbreviate'),
            clockMode=opt('clockMode'), clockSeconds=opt('clockSeconds')}
end
local function source(name) return AF.DataTexts:Text(name, context()) end

function M:Accent() return colours()[3] end
function M:IsOn() return H.on end
function M:HasMinimapInfo() return opt('minimapinfo') == 1 end
function M:Setting(key) return M.db[key] end
-- Picking a custom accent is choosing the Custom theme.
function M:OnSetting(key)
    if key == 'customAccent' and opt('theme') ~= 6 then AF.DB:Set(M, 'theme', 6) end
end
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

local function compose(prefix)
    local out, separator, trailing = {}, nil, nil
    for i = 1, 3 do
        local ok, s = pcall(source, opt(prefix .. i))
        -- Stock time strings include line breaks; data texts occupy one row.
        if ok and s then
            s = tostring(s):gsub('[\r\n\t]+', ' '):gsub('^%s+', ''):gsub('%s+$', '')
            if s ~= '' then
                if separator then out[#out + 1] = separator end
                out[#out + 1] = s
                local custom = opt(prefix .. 'sep' .. i)
                separator = custom ~= '' and custom or opt('separator')
                trailing = custom ~= '' and custom or nil
            end
        end
    end
    if trailing then out[#out + 1] = trailing end
    return table.concat(out)
end

local function databar()
    if opt('databar') ~= 1 then show('Top', false) return end
    local s = scale(); local _, sw, sh = AFCore.Scale(); sw = n(sw) or 1280 * s; sh = n(sh) or 960 * s
    local height = opt('dataHeight')
    local width = opt('dataWidth') == 0 and sw / s or math.min(sw / s, math.max(120, opt('dataWidth')))
    place('Top', (sw - width * s) / 2, opt('dataPosition') == 2 and sh - height * s or 0, width * s, height * s)
    local column = width / 3
    place('TopLeft', 8 * s, 0, (column - 16) * s, height * s)
    place('TopCenter', (column + 8) * s, 0, (column - 16) * s, height * s)
    place('TopRight', (2 * column + 8) * s, 0, (column - 16) * s, height * s)
    local c = colours()
    settext('TopLeft', compose('dl'), c[1]); settext('TopCenter', compose('dc'), c[2]); settext('TopRight', compose('dr'), c[1])
    show('Top', true)
end

local function chatpanel()
    if opt('chatline') ~= 1 then show('Chat', false) return end
    local s = scale(); local _, _, sh = AFCore.Scale(); sh = n(sh) or 960 * s
    place('Chat', opt('statusX') * s, sh - opt('statusBottom') * s, opt('statusWidth') * s, opt('statusHeight') * s)
    settext('ChatText', AF.Tags:Format(opt('statusFormat'), context()), colours()[1])
    show('Chat', true)
end

local function minimapinfo()
    local x, y, wd, ht, visible = AFCore.Rect(D.radar or 0x0e, 'af_minimap_border', 'any')
    if opt('minimapinfo') ~= 1 or not (n(x) and visible) then show('Map', false) return end
    local s = scale()
    local labelY = opt('mapPosition') == 2 and y - 17 * s or y + ht + s
    place('Map', x, labelY, wd, 16 * s)
    settext('MapText', AF.Tags:Format(opt('mapFormat'), context()), colours()[3])
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
    AF.Skin.SetAccent(colours()[3])
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
