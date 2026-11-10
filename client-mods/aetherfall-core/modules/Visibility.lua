-- Extracted from the 1.2.94 interface; client calls retain their original order.
local M = AF:NewModule('Visibility', {
    scope = 'profile', needs = {'Lifecycle'},
    defaults = {
        tot = {1, 'toggle'}, party = {1, 'toggle'}, xpbar = {1, 'toggle'}, startmenu = {1, 'toggle'},
        showPlayer = {1, 'toggle'}, showRadar = {1, 'toggle'}, showBuffs = {1, 'toggle'},
        showDebuffs = {1, 'toggle'}, showTargetAuras = {1, 'toggle'},
    },
})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
local TOGGLES = {tot = 'target_assist', party = 'party', xpbar = 'exp_bar', startmenu = 'start',
}
-- Windows that are always up in the game: switching them back on shows them again. The
-- Conditional windows restore the instance we hid only while native data still
-- supports showing it; later instances retain their native visibility.
local ALWAYS_UP = {xpbar = true, startmenu = true}
local optional = {showPlayer='basic_status',showRadar='radar',showBuffs='buff_status',showDebuffs='debuff_status',showTargetAuras='target_buff'}
local policies = {}
function M:Register(name, spec) policies[name] = spec end

S.hidden={}
S.requested={}
function M:Apply(restore)
    local all = AF.Copy(TOGGLES)
    for setting, name in pairs(optional) do
        if get(setting) == 0 or S.hidden[setting] then all[setting] = name end
    end
    for setting, spec in pairs(policies) do
        if not spec.optional or spec.enabled() == 0 or S.hidden[setting] or S.requested[setting] then all[setting] = spec.names end
    end
    for setting, name in pairs(optional) do if S.requested[setting] then all[setting] = name end end
    for setting, names in pairs(all) do
        local policy = policies[setting]
        local off = (policy and policy.enabled() or get(setting)) == 0
        local hidden = S.hidden[setting] or {}
        for _, name in ipairs(type(names) == 'table' and names or {names}) do
            local id = D[name]
            local d = id and dialog(id)
            -- The second separated-bar id is inferred; only its verified layout
            -- may be hidden/restored, never a different window using that slot.
            if policy and policy.layout and d and d.name ~= policy.layout then d = nil end
            if d and off then
                if d.shown then hidden[id] = identity(d); AFCore.SetShown(id, false) end
            elseif d and (S.requested[setting] or (restore ~= false and S.hidden[setting] and (ALWAYS_UP[setting] or (policy and policy.always) or hidden[id]==identity(d)))) then
                -- S.requested: the player just switched this on, so show it whoever hid it and whenever.
                -- Restore the instance we hid. A new conditional window retains
                -- native visibility instead of inheriting an old instance's state.
                local available=true
                if setting=='party' then available=(n(call('GetNumPartyMembers'))or 0)>0 end
                if setting=='tot' then local target=AFCore.Unit('targettarget');available=n(target) and target>0 end
                if available and not d.shown then AFCore.SetShown(id, true) end
            end
        end
        S.hidden[setting] = off and hidden or nil
    end
    S.requested = {}
end
local RESTORE_HUD = {}
for _, name in ipairs({'radar', 'start', 'exp_bar', 'shortcut', 'shortcut_ctrl', 'shortcut_alt', 'shortcut_extra'}) do
    if D[name] then RESTORE_HUD[D[name]] = true end
end
-- Runs at the head of the hold pass while the layout is applied and settled.
function M:PrepareHolds()
    if S.initialRadar and D.radar then
        local radar = dialog(D.radar)
        if radar and radar.name == 'radar_dialog' then
            if get('showRadar') == 1 and not radar.shown then AFCore.SetShown(D.radar, true) end
            S.initialRadar = nil
        end
    end
    if S.recoverShown then
        for id in pairs(RESTORE_HUD) do
            local d = dialog(id)
            local enabled = true
            for setting, name in pairs(optional) do
                if D[name] == id and get(setting) == 0 then enabled = false end
            end
            for _, policy in pairs(policies) do
                for _, name in ipairs(policy.names) do
                    if D[name] == id and policy.enabled() == 0 then enabled = false end
                end
            end
            if enabled and d and S.recoverShown[id] and not d.shown then AFCore.SetShown(id, true) end
        end
        S.recoverShown = nil
    end
end
M:Listen('RecoverShownSnapshot',function() S.recoverShown = S.lastHudShown or {} end)
M:Phase('remember-shown',function()
    if AF:Call('Lifecycle','Ticks') % 20 == 0 and AF:Call('Lifecycle','IsHudOn') and AF:Call('Lifecycle','IsSettled') then
        S.lastHudShown = {}
        for id in pairs(RESTORE_HUD) do local d = dialog(id); if d then S.lastHudShown[id] = d.shown end end
    end
end)
-- The active Aetherfall layout includes a visible minimap.
M:Listen('CharacterChanged',function() S.hidden={}; S.lastHudShown=nil; S.initialRadar=true end)
function M:Diagnostics() return AF.Copy(S) end

-- Runs before Layout applies the change, so the same pass shows the window.
M:Listen('SettingChanged', function(_, old, owner, key, value)
    if value ~= 1 and value ~= true then return end
    local policy = tostring(old):match('^(bar[1-6])%.enabled$') or tostring(key):match('^(bar[1-6])%.enabled$')
    if policy and policies[policy] then S.requested[policy] = true
    elseif TOGGLES[key] or optional[key] then S.requested[key] = true end
end, -10)
function M:Owns(key) return TOGGLES[key]~=nil or optional[key]~=nil or key=='altbar' or key=='extrabar' or key=='sepbar' or policies[key]~=nil or tostring(key):match('^bar[1-6]%.enabled$')~=nil end
