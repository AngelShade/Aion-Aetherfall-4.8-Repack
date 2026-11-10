-- Extracted from the 1.2.94 interface; client calls retain their original order.
local M = AF:NewModule('Tooltip', {
    scope = 'profile', defaults = {
        ttshow = {1, 'toggle'}, ttanchor = {2, 'choice', {'Mover', 'Cursor', 'Native'}},
        ttui = {3, 'choice', {'Native', 'Mover', 'Cursor'}},
        ttgrow = {1, 'choice', {'Bottom right', 'Bottom left', 'Top right', 'Top left'}},
        ttx = {0, 'int', -64, 64, 8}, tty = {0, 'int', -64, 64, 8},
        ttuix = {0, 'int', -64, 64, 8}, ttuiy = {0, 'int', -64, 64, 8},
        tipwidth = {240, 'int', 120, 480, 10}, tipheight = {100, 'int', 40, 240, 10},
        ttclamp = {1, 'toggle'}, tipcursor = {1, 'toggle'},
    },
    tags = {tipcursor = 'share-skip'}, fresh = {tipcursor = 1},
    migrate = function(settings)
        local changed = false
        if settings.ttuix == nil and settings.ttx ~= nil then settings.ttuix = settings.ttx; changed = true end
        if settings.ttuiy == nil and settings.tty ~= nil then settings.ttuiy = settings.tty; changed = true end
        if settings.tipcursor == 1 then return changed end
        if settings.ttanchor == 1 then settings.ttanchor = 2 end
        settings.tipcursor = 1
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
local TOOLTIP,TOOLTIP_UI=0x7f0,0x7f1
local GROW_MODE={3,2,1,0}
local PLUGIN_FIRST=0x20e
local function key(id) return AF.Movers:Key(id) end
local function tooltipKind(d)
    local name = tostring(d.name)
    if name:match('^tooltip_proto_skill') then return 'ui' end
    if name:match('^tooltip_proto') then return 'context' end
end
local function isTooltip(d) return tooltipKind(d) ~= nil end
local function tooltipBox() local s = scale(); return get('tipwidth') * s, get('tipheight') * s end
local function tooltipAnchor(which)
    local sw, sh = screen(); local bw, bh = tooltipBox()
    local p = AF.Movers:Get(which or TOOLTIP)
    if p then return p[1] * sw, p[2] * sh end
    if which == TOOLTIP_UI then return 16 * scale(), sh - bh - 380 * scale() end  -- above the chat
    return sw - bw - 16 * scale(), sh - bh - 240 * scale()  -- above the right end of the action bars
end
function M:Hold()
    local clamp = get('ttclamp') == 1
    local function anchor(kind)
        local choice = kind == 'ui' and ({'native', 'mover', 'cursor'})[get('ttui')] or ({'mover', 'cursor', 'native'})[get('ttanchor')]
        if get('ttshow') == 0 then return 5, 0, 0 end
        if choice == 'native' then return nil end
        if choice == 'cursor' then return 4, get(kind == 'ui' and 'ttuix' or 'ttx') * scale(), get(kind == 'ui' and 'ttuiy' or 'tty') * scale() end
        local ax, ay = tooltipAnchor(kind == 'ui' and TOOLTIP_UI or TOOLTIP); local bw, bh = tooltipBox()
        local m = GROW_MODE[get('ttgrow')] or 3
        return m, m % 2 == 1 and ax + bw or ax, m >= 2 and ay + bh or ay
    end
    -- Configure the verified live classes even before any named template exists.
    -- New accounts may initially create only the skill template, which must not
    -- overwrite the independent world/UI policy or be moved as a live tooltip.
    local wa, ua = {anchor('world')}, {anchor('ui')}
    S.tooltipReady = AFCore.TipPin(0, wa[1] or -1, wa[2] or 0, wa[3] or 0,
                                  ua[1] or -1, ua[2] or 0, ua[3] or 0, clamp)
    S.tooltipError = not S.tooltipReady and 'Live tooltip policy was refused' or nil
end
function M:Find()
    S.tooltips, S.checked = S.tooltips or {}, S.checked or {}
    for id = 1, PLUGIN_FIRST - 1 do
        local d = dialog(id)
        if d then
            if S.checked[id] ~= identity(d) then
                S.checked[id] = identity(d); S.tooltips[id] = nil
                local kind = tooltipKind(d)
                -- An unnamed window is the world tooltip only once it has been seen shown and small.
                if kind then S.tooltips[id] = kind end
                if tostring(d.name):match('^new_map') then AF:Send('WorldMapFound',id) end
            end
        else S.checked[id], S.tooltips[id] = nil, nil end
    end
end
function M:Kind(d) return tooltipKind(d) end
function M:IsTooltip(d) return isTooltip(d) end
function M:Each() return pairs(AF.Copy(S.tooltips or {})) end
function M:Candidates(emit)
    for _,t in ipairs({{TOOLTIP,get('ttanchor')==1,'Tooltip (world)'},{TOOLTIP_UI,get('ttui')==2,'Tooltip (UI)'}}) do
        if t[2] then local x,y=tooltipAnchor(t[1]); local width,height=tooltipBox(); emit({id=t[1],x=x,y=y,w=width,h=height,label=t[3]},true) end
    end
end
function M:Move(mv,x,y,sw,sh)
    AF.Movers:Remember(mv.id,nil,x,y,sw,sh)
end
function M:Reset(mv)
    local x,y=tooltipAnchor(mv.id); AF:Send('MoverGeometry',mv.id,x,y,mv.w,mv.h,nil,nil,false,true)
end
function M:OnAction()
    if get('ttanchor')~=1 and get('ttui')~=2 then return {note='Set World or UI to Mover first; Cursor and Native have no box to move.'} end
    AF:Send('UnlockEditor',nil,{TOOLTIP,TOOLTIP_UI}); return {stop=true}
end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('ResetLayoutCaches',function() S.checked={} end)
AF.Movers:Register('tooltip',{owner='Tooltip',order=20,candidates='Candidates',first=TOOLTIP,last=TOOLTIP_UI,category='HUD',tab='Tooltip',move='Move',reset='Reset'})
