-- Feature-local settings and native placement.
local M = AF:NewModule('Opacity', {
    scope = 'profile', needs = {'Placement', 'Tooltip', 'Lifecycle', 'ActionBars'},
    defaults = {
        playeralpha = {-1, 'int', -1, 100, 1}, targetalpha = {-1, 'int', -1, 100, 1},
        assistalpha = {-1, 'int', -1, 100, 1}, partyalpha = {-1, 'int', -1, 100, 1},
        dataalpha = {-1, 'int', -1, 100, 1}, windowsalpha = {90, 'number', 0, 100, 10},
        chatalpha = {90, 'number', 0, 100, 10}, barsalpha = {90, 'number', 0, 100, 10},
        opacitydirect = {1, 'toggle'},
    },
    tags = {opacitydirect = 'share-skip'}, fresh = {opacitydirect = 1},
    migrate = function(settings)
        if settings.opacitydirect == 1 then settings.alpha = nil; return false end
        local master = settings.alpha or 90
        for key in pairs({windowsalpha=true, chatalpha=true, barsalpha=true}) do
            settings[key] = master * (settings[key] or 100) / 100
        end
        settings.alpha, settings.opacitydirect = nil, 1
        return true
    end,
})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
local function key(id) return AF.Movers:Key(id) end
local function position(id,d) return AF.Movers:Get(id,d) end
local function design(id) return AF:Call('Placement','Design',id) end
local function designRef(id) return AF:Call('Placement','DesignRef',id) end
local function backgroundAlpha(group)
    -- Each category has one direct 0-100 value. There is no live master or
    -- second window multiplier; the retired value is read only for migration.
    return get(group or 'windowsalpha') / 100
end
local function backgroundName(name)
    return name=='bg' or name=='background' or name=='backdrop' or name:match('^bg_')
        or name:match('_bg$') or name:match('_bg_') or name:match('_background$')
end
local backdropRegistry, backdropPresets
local function backdropPreset(name)
    if backdropRegistry ~= AFCore.UI or not backdropPresets then
        backdropRegistry, backdropPresets = AFCore.UI, {}
    end
    if not name or not backdropRegistry then return false end
    if backdropPresets[name] ~= nil then return backdropPresets[name] end
    local found, safe = false, true
    -- A tiled skin on a passive panel is a frame/background, including the
    -- unnamed v5_tabpage/container/listview panels in the native quest journal.
    -- Image sprites (portraits, decorative icons, gauges) are not admitted.
    for _,preset in ipairs(backdropRegistry.presets[name] or {}) do
        for _,ref in ipairs(preset.references or {}) do
            for _,skin in ipairs(backdropRegistry.skins[ref.name] or {}) do
                found = true
                if skin.attributes.type ~= 'tiled' then safe = false end
            end
        end
    end
    backdropPresets[name] = found and safe
    return backdropPresets[name]
end
local PLUGIN_OPACITY = {PlayerBotBar='windowsalpha', AFUIOptions='windowsalpha', AFHTop=false, AFHChat='chatalpha', AFHMap=false}
-- Native selections persist across hide/show (1.2.68), so a dialog instance
-- already prepared at its current value needs no further pass on every target
-- change or window opening. A sweep every five seconds still picks up widgets
-- the game adds to an existing window later and prunes retired selections.
-- AFCore.ClearBackdrops forgets these records with the native selections.
S.opacityDone = {}
function M:Apply()
    if not AFCore.Backdrop then return end
    if not S.opacitySwept or AF:Call('Lifecycle','Ticks') - S.opacitySwept >= 100 or AF:Call('Lifecycle','Ticks') < S.opacitySwept then
        S.opacitySwept, S.opacityDone = AF:Call('Lifecycle','Ticks'), {}
        if AFCore.PruneBackdrops then AFCore.PruneBackdrops() end
    end
    S.opacityApplied,S.opacityRefused=0,0
    local refused
    local function apply(id,alpha,path)
        if AFCore.Backdrop(id,alpha,path,'any',false) then S.opacityApplied=S.opacityApplied+1
        else S.opacityRefused=S.opacityRefused+1; refused=true end
    end
    -- A dialog is complete only when every selection succeeded; a widget the
    -- game has not created yet is retried on the next pass.
    local function prepared(id,d,alpha)
        local signature=identity(d)..'|'..alpha
        if S.opacityDone[id]==signature then return true end
        refused=nil
        return false,signature
    end
    for id = 1, 0x20e - 1 do
        local d = dialog(id)
        -- Native 1.2.68 retains a still-owned selection across hide/show. Prepare
        -- existing hidden dialogs too, so the first draw has the same policy as
        -- later draws; visibility remains entirely native.
        if d then
            local alpha,bar
            local frameKey = ({[D.basic_status or -1]='playeralpha',[D.target or -1]='targetalpha',
                [D.target_assist or -1]='assistalpha',[D.party or -1]='partyalpha'})[id]
            if frameKey and get(frameKey) >= 0 then alpha = get(frameKey) / 100
            elseif id == D.chat then alpha = backgroundAlpha('chatalpha')
            elseif id == D.shortcut or id == D.shortcut_ctrl or id == D.shortcut_alt or id == D.shortcut_extra
                or ((id == D.shortcut_sep or id == D.shortcut_sep2) and d.name == 'quickbar_dialog_sep') then
                alpha,bar = AF:Call('ActionBars','BackgroundAlpha',id,backgroundAlpha('barsalpha')),true
            elseif nativeMovable(d) and not AF:Call('Placement','IsHud',id) and not AF:Call('Tooltip','Kind',d) then alpha = backgroundAlpha('windowsalpha')
            elseif AF:Call('Placement','IsHud',id) and id ~= D.chat_input and id ~= D.start and id ~= D.radar
                and id ~= D.shortcut and id ~= D.shortcut_ctrl and id ~= D.shortcut_alt and id ~= D.shortcut_extra
                and id ~= D.shortcut_sep and id ~= D.shortcut_sep2 then
                alpha=backgroundAlpha()
            end
            local done,signature
            if alpha then done,signature=prepared(id,d,alpha) end
            if alpha and not done then
                apply(id,alpha)
                local layout=featureLayout(d.name,id)
                for _,node in pairs(layout and layout.paths or {})do
                    local kind=node.kind
                    -- Chat's opaque panel is a tabpage skin, not a bg-named
                    -- image. Select its exact preset; never fade chat_list_*.
                    local chatPage=id==D.chat and kind=='tabpage'
                        and node.attributes.preset=='v5_chat_tabpage'
                    local passivePanel = (kind=='image' or kind=='container' or kind=='static' or kind=='tabpage')
                        and backdropPreset(node.attributes.preset)
                    if chatPage or passivePanel or ((kind=='image' or kind=='container' or kind=='static' or kind=='gauge')
                        and backgroundName(node.attributes.name or '')) or (bar and kind=='itemview') then
                        apply(id,alpha,node.path)
                    end
                end
                S.opacityDone[id]=not refused and signature or nil
            end
        end
    end
    -- Resolve addon dialogs by their native names, as the companion bridge
    -- does. The guarded skin setter preserves title text and child controls;
    -- visibility and geometry still belong to the normal queued widget API.
    for id=0x20e,0x221 do
        local d=dialog(id)
        local group=d and PLUGIN_OPACITY[d.name]
        if d and group~=nil then
            local alpha = d.name == 'AFHTop' and get('dataalpha') >= 0 and get('dataalpha') / 100 or backgroundAlpha(group or nil)
            local done,signature=prepared(id,d,alpha)
            if not done then
                if d.name == 'AFUIOptions' then
                    apply(id,alpha,'AFUIOptionsBackdrop')
                    apply(id,alpha,'AFUIOptionsSidebar')
                    apply(id,alpha,'AFUIOptionsContent')
                else
                    apply(id,alpha)
                end
                S.opacityDone[id]=not refused and signature or nil
            end
        end
    end
end
-- Action bars (ElvUI): buttons per row and button size. The twelve slots are laid out in rows inside
-- their container, the container and the bar window take the new size (bottom edge kept), bar 1's
function M:BackgroundAlpha(group) return backgroundAlpha(group) end
function M:Reset() S.opacityDone={} end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('BackdropsCleared',M.Reset)

function M:Owns(key) return key=='windowsalpha' or key=='chatalpha' or key=='barsalpha' or key=='dataalpha' or key=='playeralpha' or key=='targetalpha' or key=='assistalpha' or key=='partyalpha' end
