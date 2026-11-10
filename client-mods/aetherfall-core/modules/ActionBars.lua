-- Feature-local settings and native placement.
AF.DB:RegisterTemplate('ActionBar', {
    enabled = {1, 'toggle'}, rows = {12, 'int', 1, 12, 1},
    size = {40, 'int', 28, 56, 2}, opacity = {-1, 'int', -1, 100, 1},
})
local instances = {}
local legacyEnabled = {[3] = 'altbar', [4] = 'extrabar', [5] = 'sepbar'}
for i = 1, 6 do
    local legacy = {}
    if i <= 4 then legacy['abrow' .. i] = 'rows'; legacy['absize' .. i] = 'size' end
    if legacyEnabled[i] then legacy[legacyEnabled[i]] = 'enabled' end
    instances['bar' .. i] = {template = 'ActionBar', legacy = legacy,
                            defaults = {rows = i == 4 and 6 or 12},
                            only = i >= 5 and {enabled=true, opacity=true} or nil}
end
local M = AF:NewModule('ActionBars', {
    scope = 'profile', needs = {'Placement', 'Visibility'}, instances = instances,
    migrate = function(settings)
        -- The legacy separated-bars switch represented both native instances.
        if settings.sepbar ~= nil and settings['bar6.enabled'] == nil then
            settings['bar6.enabled'] = settings.sepbar
            return true
        end
        return false
    end,
})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function bar(i) return M.db['bar' .. i] end
local function key(id) return AF.Movers:Key(id) end
local function position(id,d) return AF.Movers:Get(id,d) end
local function design(id) return AF:Call('Placement','Design',id) end
local function designRef(id) return AF:Call('Placement','DesignRef',id) end
local BAR_LAYOUT = {{'shortcut','quickbar_dialog'}, {'shortcut_ctrl','quickbar_dialog_ctrl'},
                    {'shortcut_alt','quickbar_dialog_alt'}, {'shortcut_extra','quickbar_dialog_extra'}}
S.barFit = {}
local function barDialog(i, d)
    return d and BAR_LAYOUT[i] and d.name == BAR_LAYOUT[i][2]
end
function M:Managed(id, d)
    for i, b in ipairs(BAR_LAYOUT) do
        if D[b[1]] == id and barDialog(i, d) then
            return true
        end
    end
    return false
end
-- One geometry calculation for layout and the options' fit indicator. Keep the
-- requested row count in the profile; a smaller viewport can temporarily wrap
-- it into extra columns without shrinking buttons or losing any of the 12 slots.
function M:Metrics(i)
    local b = BAR_LAYOUT[i]; if not b then return end
    local layout=featureLayout(b[2],D[b[1]]);local first=featureWidget(layout,'shortcut1')
    local container=first and layout.paths[first.parent]
    if not container or #container.rect~=4 then return end
    local slots={}
    for k=1,12 do local node=featureWidget(layout,'shortcut'..k);if not node or node.parent~=container.path then return end;slots[k]=node.path end
    local s = n((AFCore.Scale())) or 1
    local sw, sh = screen()
    local requested, size = bar(i).rows, bar(i).size
    local pitch, gap = (size + 2) * s, 2 * s
    local padw, padh = i == 1 and 52 * s or 0, i == 1 and 8 * s or 0
    local maxcols = math.max(1, math.min(12, math.floor((sw - padw + gap) / pitch)))
    local maxrows = math.max(1, math.floor((sh - padh + gap) / pitch))
    local per = math.min(maxcols, math.max(math.ceil(12 / maxrows), requested))
    local rows = math.ceil(12 / per)
    local cw, ch = per * pitch - gap, rows * pitch - gap
    return {per = per, requested = requested, size = size * s, pitch = pitch, cw = cw, ch = ch,
            ox = container.rect[1]*s, oy = container.rect[2]*s, container=container.path,slots=slots,
            w = cw + padw, h = math.max(i == 1 and 48 * s or 0, ch + padh)}
end
function M:Hold(pinned)
    local s = n((AFCore.Scale())) or 1
    local sw, sh = screen()
    for i, b in ipairs(BAR_LAYOUT) do
        local id = D[b[1]]; local d = id and dialog(id)
        local m=barDialog(i,d) and M:Metrics(i)
        if m then
            local pitch, per, cw, ch, ox, oy, w0, h0 = m.pitch, m.per, m.cw, m.ch, m.ox, m.oy, m.w, m.h
            local p = position(id, d)
            local dx,dy=design(id);local ref=designRef(id)
            local x=p and p[1]*sw or (dx or d.x)+(ref and ref[5]=='center' and (ref[3]*s-w0)/2 or 0)
            local y=p and p[2]*sh or (dy or d.y)+(ref and ref[4]*s or d.h)-h0
            x, y = math.max(0, math.min(sw - w0, x)), math.max(0, math.min(sh - h0, y))
            AFCore.Pin(id, x, y, 0, true, w0, h0); pinned[id] = true
            AFCore.Pin(id, ox, oy, 0, false, cw, ch, m.container,'container'); pinned[id .. ':' .. m.container] = {id, m.container}
            for k = 1, 12 do
                local slot = m.slots[k]
                local c, r = (k - 1) % per, math.floor((k - 1) / per)
                AFCore.Pin(id, ox + c * pitch, oy + r * pitch, 0, false, m.size, m.size, slot, 'itemview')
                pinned[id .. ':' .. slot] = {id, slot}
            end
            if i == 1 then
                local px = ox + cw + 6 * s
                for _, e in ipairs({{'page_up', px, 3 * s, 'button'}, {'page_down', px, h0 - 18 * s, 'button'},
                                    {'page', px, h0 / 2 - 6 * s, 'static'}, {'lock', 1 * s, h0 / 2 - 10 * s, 'button'}}) do
                    AFCore.Pin(id, e[2], e[3], 0, false, 0, 0, e[1], e[4]); pinned[id .. ':' .. e[1]] = {id, e[1]}
                end
            end
            -- Refresh the existing box in place, retaining its selection and
            -- queued-widget ordering while dimensions change under an open editor.
            AF:Send('MoverGeometry',id,x,y,w0,h0,0,0,true)
            if S.barFit[i] ~= m.per then
                S.barFit[i] = m.per
                AF:Send('RefreshOptions','Bar' .. i)
            end
        end
    end
end
function M:IsEnabled(index) return bar(index).enabled end
function M:BackgroundAlpha(id, fallback)
    local names = {'shortcut', 'shortcut_ctrl', 'shortcut_alt', 'shortcut_extra', 'shortcut_sep', 'shortcut_sep2'}
    for i, name in ipairs(names) do
        if id == D[name] then
            local value = bar(i).opacity
            return value < 0 and fallback or value / 100
        end
    end
    return fallback
end
function M:Arrange(index)
    local names = {'shortcut', 'shortcut_ctrl', 'shortcut_alt', 'shortcut_extra', 'shortcut_sep', 'shortcut_sep2'}
    local id = D[names[index]]
    AF:Send('UnlockEditor', nil, id and {id} or nil)
end
for i, name in ipairs({'shortcut', 'shortcut_ctrl', 'shortcut_alt', 'shortcut_extra', 'shortcut_sep', 'shortcut_sep2'}) do
    local index = i
    AF:Call('Visibility', 'Register', 'bar' .. index, {
        names = {name}, enabled = function() return bar(index).enabled end,
        always = index <= 4, optional = index <= 2, layout = index >= 5 and 'quickbar_dialog_sep' or nil,
    })
end
function M:Diagnostics() return AF.Copy(S) end
