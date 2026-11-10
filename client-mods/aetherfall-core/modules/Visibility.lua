-- Extracted from the 1.2.94 interface; client calls retain their original order.
local M=AF:NewModule('Visibility',{needs={'Lifecycle'},defaults={tot={1,'toggle'},party={1,'toggle'},xpbar={1,'toggle'},startmenu={1,'toggle'},altbar={1,'toggle'},extrabar={1,'toggle'},sepbar={1,'toggle'}}})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
local TOGGLES = {tot = 'target_assist', party = 'party', xpbar = 'exp_bar', startmenu = 'start',
                 altbar = 'shortcut_alt', extrabar = 'shortcut_extra', sepbar = {'shortcut_sep', 'shortcut_sep2'}}
-- Windows that are always up in the game: switching them back on shows them again. The
-- Conditional windows restore the instance we hid only while native data still
-- supports showing it; later instances retain their native visibility.
local ALWAYS_UP = {xpbar = true, startmenu = true, altbar = true, extrabar = true}

S.hidden={}
function M:Apply(restore)
    for setting, names in pairs(TOGGLES) do
        local off = get(setting) == 0
        local hidden = S.hidden[setting] or {}
        for _, name in ipairs(type(names) == 'table' and names or {names}) do
            local id = D[name]
            local d = id and dialog(id)
            -- The second separated-bar id is inferred; only its verified layout
            -- may be hidden/restored, never a different window using that slot.
            if setting == 'sepbar' and d and d.name ~= 'quickbar_dialog_sep' then d = nil end
            if d and off then
                if d.shown then hidden[id] = identity(d); AFCore.SetShown(id, false) end
            elseif restore ~= false and d and S.hidden[setting] and (ALWAYS_UP[setting] or hidden[id]==identity(d)) then
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
            if not radar.shown then AFCore.SetShown(D.radar, true) end
            S.initialRadar = nil
        end
    end
    if S.recoverShown then
        for id in pairs(RESTORE_HUD) do
            local d = dialog(id)
            if d and S.recoverShown[id] and not d.shown then AFCore.SetShown(id, true) end
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
AF.Config:Register(M,{{name='General',order=1,rows={{order=5,label='XP bar',k='xpbar',kind='toggle'}}},{name='Unit Frames',order=2,hint='Move frames with /moveui.',rows={{order=1,label='Target of target',k='tot',kind='toggle'},{order=2,label='Party frames',k='party',kind='toggle'}}},{name='Action Bars',order=3,rows={{order=1,label='Bar 3',k='altbar',kind='toggle'},{order=2,label='Bar 4',k='extrabar',kind='toggle'},{order=3,label='Bars 5 and 6',k='sepbar',kind='toggle'}}},{name='Shortcuts',order=8,rows={{order=1,label='Shortcut menu',k='startmenu',kind='toggle'}}}})
function M:Owns(key) return TOGGLES[key]~=nil end
