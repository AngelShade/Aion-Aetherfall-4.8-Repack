-- The only legacy surface. Every global name that the generated plugin XML, the
-- /afcore diagnostics in AetherfallCore.lua, PlayerBotBar.lua or the offline
-- tests used before the modular split lives here as a thin forward or a
-- read-only view. New code never calls these; see ARCHITECTURE.md.
AF.Native:SetResources(AFUI_DESIGN, AFUI_MENU, AFUI_LAZY)
AF:Start()

local function call(module, method) return function(...) return AF:Call(module, method, ...) end end
local function diagnostics(module, field)
    return function() local state = AF:Call(module, 'Diagnostics'); return state and state[field] end
end
local function method(module, name) return function() return AF:Call(module, name) end end

-- Read-only state that other files used to read straight from the shared table.
local VIEW = {
    ready = method('Lifecycle', 'IsReady'), connected = method('Lifecycle', 'IsConnected'),
    settled = method('Lifecycle', 'IsSettled'), identity = method('Lifecycle', 'Identity'),
    ticks = method('Lifecycle', 'Ticks'), unlocked = method('Lifecycle', 'IsUnlocked'),
    suspended = diagnostics('Lifecycle', 'suspended'), focused = diagnostics('Lifecycle', 'focused'),
    loadError = diagnostics('Lifecycle', 'loadError'),
    movers = diagnostics('MoverEditor', 'movers'), drag = diagnostics('MoverEditor', 'drag'),
    unlocks = diagnostics('MoverEditor', 'unlocks'),
    open = diagnostics('OptionsWindow', 'open'), opened = diagnostics('OptionsWindow', 'opened'),
    clicks = diagnostics('OptionsWindow', 'clicks'), lastClick = diagnostics('OptionsWindow', 'lastClick'),
    tab = diagnostics('OptionsWindow', 'tab'), requests = diagnostics('OptionsWindow', 'requests'),
    built = diagnostics('OptionsWindow', 'built'), slashError = diagnostics('OptionsWindow', 'slashError'),
    pins = diagnostics('Layout', 'pins'), pinError = diagnostics('Layout', 'pinError'),
    enforceError = diagnostics('Layout', 'enforceError'),
    tooltips = diagnostics('Tooltip', 'tooltips'), mapId = diagnostics('WorldMap', 'mapId'),
    watchSeen = diagnostics('Watch', 'seen'), watchUntil = diagnostics('Watch', 'deadline'),
    saveError = function() return AF.DB:Error() end,
    eventError = function() return AF:Error('Lifecycle') end,
    db = function() return AF.DB:Diagnostics().db end,
    positions = function() return AF.DB:Diagnostics().positions end,
    stored = function() return AF.DB:Diagnostics().stored end,
}
local slashOpen, slashMove = AF:Call('OptionsWindow', 'SlashHandlers')
local API = {
    Get = function(key) return AF.DB:GetLegacy(key) end,
    Click = call('OptionsWindow', 'Click'), Open = call('OptionsWindow', 'Open'),
    Refresh = call('OptionsWindow', 'Refresh'), Request = call('OptionsWindow', 'Request'),
    Built = call('OptionsWindow', 'Built'), BindCommands = call('OptionsWindow', 'BindCommands'),
    SlashOpen = slashOpen, SlashMove = slashMove,
    Editor = call('MoverEditor', 'Editor'), Unlock = call('MoverEditor', 'Unlock'),
    Enter = call('Lifecycle', 'Enter'), Save = function() return AF.DB:Save() end,
    ShowCompanionBar = call('Companions', 'ShowCompanionBar'),
    RememberCompanionMode = call('Companions', 'RememberCompanionMode'),
    ButtonRects = call('Shortcuts', 'ButtonRects'),
}
-- The two fields /afcore writes: forget prepared backdrops, start a window watch.
local WRITE = {
    opacityDone = function() AF:Send('BackdropsCleared') end,
    watchUntil = function(value) AF:Call('Watch', 'Start', value) end,
    watchSeen = function() end,
}
AFUI = setmetatable({}, {
    __index = function(_, key)
        if API[key] then return API[key] end
        if VIEW[key] then return VIEW[key]() end
    end,
    __newindex = function(_, key, value)
        if WRITE[key] then return WRITE[key](value) end
        error('AFUI.' .. tostring(key) .. ' is read-only; use the AF module API')
    end,
})
AFUI_BesideOwner = AF.Native.BesideOwner

local hud = {}
AFHUD = setmetatable({}, {
    __index = function(_, key)
        if key == 'error' then local state = AF:Call('HUD', 'Diagnostics'); return state and state.error or AF:Error('HUD') end
        if key == 'on' then return AF:Call('HUD', 'IsOn') end
        if key == 'codexError' then return hud.codexError end
        local state = AF:Call('HUD', 'Diagnostics'); return state and state[key]
    end,
    __newindex = function() error('AFHUD is read-only; use the AF module API') end,
})
AFHUD_Active = call('HUD', 'Active')
AFHUD_Accent = call('HUD', 'Accent')
AFHUD_Apply = call('HUD', 'Apply')
AFHUD_Update = call('HUD', 'Update')
AFHUD_Invalidate = call('HUD', 'Invalidate')

-- Keep the event in a function argument: native widget calls can synchronously
-- dispatch another event and replace the XML script's global `event`.
function AFHUD_Dispatch(event, ...)
    if AFCodex then
        local ok, why = pcall(AFCodex.OnEvent, event, ...)
        if not ok then
            local message = tostring(why)
            if hud.codexError ~= message then DisplayChatMessage(0, 'Codex: ' .. message, .91, .63, .56) end
            hud.codexError = message
        else hud.codexError = nil end
    end
    AF.Events:Client(event, ...)
end
function AFUI_OnEvent(event) AF:Call('Lifecycle', 'OnEvent', event) end
function AFHUD_OnEvent(event) AF:Call('HUD', 'OnEvent', event) end
-- Called from the data bar's OnLoad (AFHTop): 'AFHUD_OnLoad(); AFUI_OnLoad();'.
function AFHUD_OnLoad() AF:Call('HUD', 'OnLoad') end
function AFUI_OnLoad() AF:Call('Lifecycle', 'OnLoad') end

-- Global functions of other plugin files that the interface wraps. The policy is
-- in the owning module; only the replacement of the global happens here.
-- 1. Start-menu entries that already have a HUD button are left out of the menu.
if type(RegisterMenu) == 'function' then
    local native = RegisterMenu
    RegisterMenu = function(label, command, icon, ...)
        if AF:Call('Shortcuts', 'AllowMenu', command) == false then return end
        return native(label, command, icon, ...)
    end
end
-- 2. Web windows are fitted to the screen before they open ...
for name, index in pairs(AF:Call('CustomWindows', 'FitHooks') or {}) do
    local open = _G[name]
    if type(open) == 'function' then
        _G[name] = function(...) AF:Call('CustomWindows', 'BeforeOpen', index); return open(...) end
    end
end
-- 3. ... and movable ones return to their saved spot after they open.
for name, opener in pairs(AF:Call('CustomWindows', 'CustomHooks') or {}) do
    local open = _G[opener]
    if type(open) == 'function' then
        _G[opener] = function(...)
            local result = open(...)
            AF:Call('CustomWindows', 'Opened', name)
            return result
        end
    end
end

-- Registered when this file loads, so the commands exist even if the plugin XML did not.
AF:Send('BindCommands')
