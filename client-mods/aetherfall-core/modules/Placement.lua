-- Native interface policy; no state is shared with other modules.
local M=AF:NewModule('Placement',{scope='profile',needs={'Lifecycle','WorldMap'},defaults={}})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
local PLUGIN_FIRST=0x20e
local function key(id) return AF.Movers:Key(id) end
local position=function(id,d) return AF.Movers:Get(id,d) end
local remember=function(...) return AF.Movers:Remember(...) end
S.defaults,S.designed,S.observed,S.restoredWindows,S.pins={},{},{},{},{}
local NOTIFICATION_DESIGN = {
    [0x11f] = {950, 845, 166, 137, 'right', 'bottom', 'gm_indicator_dialog'},
    [0x120] = {360, 800, 505, 47, 'left', 'bottom', 'help_indicator_dialog'},
    -- The classic layout's own spot, which leaves the Pet Auto-attack toggle clear of Action Bar 1.
    [0xd5] = {112, 766, 284, 74, 'center', 'bottom', 'pet_dialog'},
}
local function designRef(id) return (N:Resource('design') and N:Resource('design')[id]) or NOTIFICATION_DESIGN[id] end
local function design(id)
    local d = designRef(id); if not d then return end
    local s = n((AFCore.Scale())) or 1
    local sw, sh = screen()
    local x = d[5] == 'center' and sw / 2 + (d[1] - 640) * s or d[5] == 'right' and sw - (1280 - d[1]) * s or d[1] * s
    local y = d[6] == 'bottom' and sh - (960 - d[2]) * s or d[6] == 'middle' and sh / 2 + (d[2] - 480) * s or d[2] * s
    return math.max(0, math.min(sw - d[3] * s, x)), math.max(0, math.min(sh - d[4] * s, y))
end
local defaultSpot
-- Once per login and per screen size, native HUD windows without a user position
-- go to the Aetherfall design position (the game may restore older saved ones).
-- Frames the game re-anchors each time they appear (target, target of target, cast bars,
-- target auras): held at their design spot while shown. Others are placed once, so a
local REANCHORED = {}
for _, name in ipairs({'target', 'target_assist', 'target_delay', 'target_assist_delay', 'skill_delay', 'target_buff', 'help_indicator', 'gm_indicator', 'pet'}) do
    if D[name] then REANCHORED[D[name]] = true end
end
local HUD_IDS = {}
for _, name in ipairs({'basic_status', 'target', 'target_assist', 'party', 'radar', 'start', 'shortcut', 'shortcut_ctrl',
                      'shortcut_alt', 'shortcut_extra', 'shortcut_sep', 'shortcut_sep2', 'chat', 'chat_input', 'exp_bar',
                      'buff_status', 'debuff_status', 'target_buff', 'skill_delay', 'target_delay', 'timer'}) do
    if D[name] then HUD_IDS[D[name]] = true end
end
-- Ordinary windows retain their own title-bar dragging after the editor closes.
local function owned(id, d) return REANCHORED[id] or (HUD_IDS[id] and not nativeMovable(d)) end
local function applyDesign()
    local sw, sh = screen(); local size = math.floor(sw) .. 'x' .. math.floor(sh)
    if S.designSize ~= size then S.designSize = size; S.designed = {} end
    for id in pairs(N:Resource('design') or {}) do
        -- Windows the pins hold (resized bars, the start menu, tooltips) are placed by holdPinned.
        if not AF.Movers:Get(id) and not (S.pins and S.pins[id]) then
            local x, y = design(id)
            local d = dialog(id)
            -- Only the window the design was made for (e.g. the second separated bar's id is inferred).
            if d and N:Resource('design')[id][7] and d.name ~= N:Resource('design')[id][7] then d = nil end
            if x and d and (d.shown or REANCHORED[id]) then
                S.defaults[id] = {x / sw, y / sh}
                if S.designed[id] ~= identity(d) or (REANCHORED[id] and d.shown and (math.abs(d.x - x) > 1 or math.abs(d.y - y) > 1)) then
                    AFCore.Move(id, x, y)
                end
                S.designed[id] = identity(d)
            end
        end
    end
end
-- Show/hide the native windows the options switch (off: kept hidden; back on: shown again).
local ATTACHED = {{184, 'quest_dialog', 185, 'quest_summary_dialog'}}
local function besideOwner(owner, child, sw)
    local x = owner.x + owner.w
    if x + child.w > sw then x = owner.x - child.w end
    return math.max(0, x), owner.y
end
local function followOwners()
    if AF:Call('Lifecycle','IsUnlocked') then return end
    local sw = screen()
    for _, pair in ipairs(ATTACHED) do
        local owner, child = dialog(pair[1]), dialog(pair[3])
        if owner and child and owner.name == pair[2] and child.name == pair[4] and owner.shown and child.shown then
            local x, y = besideOwner(owner, child, sw)
            if math.abs(child.x - x) > .5 or math.abs(child.y - y) > .5 then AFCore.Move(pair[3], x, y) end
        end
    end
end
-- Apply saved positions and visibility to native windows (engine may re-anchor them).
function M:Enforce()
    applyDesign()
    local sw, sh = screen()
    local size = tostring(sw) .. ':' .. tostring(sh)
    if S.positionSize ~= size then S.positionSize, S.observed, S.restoredWindows = size, {}, {} end
    local held = S.pins or {}
    S.observed = S.observed or {}
    S.restoredWindows = S.restoredWindows or {}
    for id = 1, PLUGIN_FIRST - 1 do
        if HUD_IDS[id] or AF.Movers:Get(id) then
            local d = dialog(id); local p = d and position(id, d)
            local prev = S.observed[id]
            if d and (p or HUD_IDS[id]) then
                if not S.defaults[id] then S.defaults[id] = defaultSpot(id, d) end
                local fresh = not prev or prev.token ~= identity(d) or prev.profile ~= AF.DB:ProfileName() or not prev.shown
                -- Ordinary dialogs already remember their position natively.
                -- Restore the profile once, then let their opener/dragging own
                -- close/reopen. Replaying an older addon coordinate on every
                -- Show races the native inventory/attached-window layout.
                local restoreKey = AF.DB:ProfileName() .. ':' .. d.name
                if not HUD_IDS[id] then fresh = S.restoredWindows[id] ~= restoreKey end
                -- Existing ordinary windows are already constructed while hidden.
                -- Restore their profile now, before the opener can paint them,
                -- rather than replaying that restore on the first visible tick.
                -- Conditional HUD placement still waits for its native visibility.
                if p and (d.shown or not HUD_IDS[id]) and not held[id] and fresh then
                    local x = math.max(0, math.min(sw - d.w, p[1] * sw))
                    local y = math.max(0, math.min(sh - d.h, p[2] * sh))
                    if math.abs(d.x-x) > .5 or math.abs(d.y-y) > .5 then
                        if AFCore.Move(id, x, y) then
                            -- SetRect can run the dialog's alignment code.
                            -- Observe its actual result, not the requested rect.
                            d = dialog(id) or d
                            if not HUD_IDS[id] then S.restoredWindows[id] = restoreKey end
                        end
                    elseif not HUD_IDS[id] then
                        S.restoredWindows[id] = restoreKey
                    end

                end
                S.observed[id] = {token = identity(d), shown = d.shown, x = d.x, y = d.y, profile = AF.DB:ProfileName()}
            else
                S.observed[id] = nil
            end
        end
    end
    followOwners()
end

local function nativeDragHit(id, d, x, y)
    if not d or d.name == '' or not d.shown or x < d.x or y < d.y or x >= d.x+d.w or y >= d.y+d.h then return false end
    if id == D.chat and d.name == 'chat_dialog' then
        -- The chat is a held HUD frame, but it has its own native move handle.
        local gx, gy, gw, gh, shown = AFCore.Rect(id, 'move_trigger', 'image')
        return shown and gx and x >= gx and y >= gy and x < gx+gw and y < gy+gh
    end
    return nativeMovable(d) and not owned(id,d)
end

-- Save native title dragging only after a foreground mouse press inside the
-- window. Engine layout/focus changes are never user-position edits.
function M:NativeDragTick()
    local cx, cy, down = AFCore.Cursor()
    if not AF:Call('Lifecycle','IsSettled') or AF:Call('Lifecycle','IsUnlocked') or not cx then S.nativeDrags=nil; S.nativeDown=down; return end
    if down and not S.nativeDown then
        S.nativeDrags={}
        for id=1,PLUGIN_FIRST-1 do
            local d=dialog(id)
            if nativeDragHit(id,d,cx,cy) then
                S.nativeDrags[id]={token=identity(d),x=d.x,y=d.y,w=d.w,h=d.h,cx=cx,cy=cy,savedX=d.x,savedY=d.y}
            end
        end
    end
    local sw,sh=screen()
    for id,prev in pairs(S.nativeDrags or {}) do
        local d=dialog(id)
        if d and d.shown and identity(d)==prev.token then
            local dx,dy=cx-prev.cx,cy-prev.cy
            local moved=math.abs(dx)>2 or math.abs(dy)>2
            -- A native opening animation can move/resize every overlapping
            -- frame under a stationary press. Only a frame following actual
            -- cursor displacement is a native user drag. The game stops a window
            -- at the screen edge, so on each axis it may lag the cursor, never lead it.
            local wx,wy=d.x-prev.x,d.y-prev.y
            local follows=math.abs(wx)<=math.abs(dx)+2 and math.abs(wy)<=math.abs(dy)+2
                and wx*dx>=0 and wy*dy>=0
            local changed=math.abs(d.x-prev.savedX)>1 or math.abs(d.y-prev.savedY)>1
            if moved and follows and changed and math.abs(d.w-prev.w)<.5 and math.abs(d.h-prev.h)<.5 then
                remember(id,d,d.x,d.y,sw,sh)
                if owned(id,d) then AF:Send('MarkPinsDirty') end
                if id==AF:Call('WorldMap','Id') then AF:Call('WorldMap','Remember',d.x/sw,d.y/sh) end
                prev.savedX,prev.savedY=d.x,d.y
            end
        end
    end
    if not down then S.nativeDrags=nil end
    S.nativeDown=down
end
local FRAME_CATALOG = AFCore.FrameCatalog and AFCore.FrameCatalog() or {}
-- Where Reset puts a frame: its Aetherfall design spot, else its native layout
-- spot from the catalogue. Only a window known to neither keeps its current spot
-- (previously every ordinary window "reset" to wherever MoveUI first found it).
defaultSpot = function(id, d)
    local sw, sh = screen()
    local dx, dy = design(id)
    if dx then return {dx / sw, dy / sh} end
    local ref = FRAME_CATALOG[id]
    if ref and d and d.name == ref[7] then
        local s = scale()
        local x = ref[5] == 'center' and sw/2 + (ref[1]-640)*s or ref[5] == 'right' and sw-(1280-ref[1])*s or ref[1]*s
        local y = ref[6] == 'bottom' and sh-(960-ref[2])*s or ref[6] == 'middle' and sh/2+(ref[2]-480)*s or ref[2]*s
        return {math.max(0, math.min(sw-ref[3]*s, x)) / sw, math.max(0, math.min(sh-ref[4]*s, y)) / sh}
    end
    return d and {d.x / sw, d.y / sh}
end
function M:Design(id) return design(id) end
function M:DesignRef(id) return AF.Copy(designRef(id)) end
function M:Owned(id,d) return owned(id,d) end
function M:IsHud(id) return HUD_IDS[id] end
function M:IsReanchored(id) return REANCHORED[id] end
function M:Reanchored() return function(_,id) return next(REANCHORED,id) end,nil,nil end
function M:CatalogRef(id) return AF.Copy(FRAME_CATALOG[id]) end
function M:DefaultSpot(id,d) return defaultSpot(id,d) end
function M:Default(id,d)
    if not S.defaults[id] and d then S.defaults[id]=defaultSpot(id,d) end
    return AF.Copy(S.defaults[id])
end
function M:QueueVisible()
    for id,previous in pairs(S.observed or {}) do
        local d=dialog(id)
        if not d or not d.shown or identity(d)~=previous.token then S.observed[id]=nil end
    end
end
function M:RecordLocked()
    for id=1,PLUGIN_FIRST-1 do
        local d=AF.Movers:Get(id) and dialog(id)
        if d and not owned(id,d) then
            S.observed=S.observed or {}
            S.observed[id]={token=identity(d),shown=d.shown,x=d.x,y=d.y,profile=AF.DB:ProfileName()}
        end
    end
end
function M:ResetAll()
    local sw,sh=screen();local prefix=AF.DB:ProfileName()..'.'
    for k in AF.Movers:Positions() do
        if k:sub(1,#prefix)==prefix then
            local id=tonumber(k:sub(#prefix+1));local def=id and S.defaults[id]
            if def then AFCore.Move(id,def[1]*sw,def[2]*sh) end
            AF.Movers:SetKey(k,nil);AF.Movers:SetBinding(k,nil)
        end
    end
    AF.DB:Dirty();AF:Send('ApplySettings',true)
end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('PinsChanged',function(_,pins) S.pins=AF.Copy(pins) end)
M:Listen('ResetLayoutCaches',function(_,full) S.designed,S.observed={},{};if full then S.designSize=nil end end)
M:Listen('CharacterChanged',function() S.defaults,S.designed,S.restoredWindows,S.designSize={},{},{},nil end)
M:Listen('EditorLocked',M.RecordLocked)
M:Listen('ResetObservations',function() S.observed={} end)
M:Listen('ResetAllPositions',M.ResetAll)
M:Phase('native-drag',M.NativeDragTick)
