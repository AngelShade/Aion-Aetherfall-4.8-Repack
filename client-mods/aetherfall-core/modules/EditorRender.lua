-- Draws editor snapshots. The controller retains all mover interaction state.
local M = AF:NewModule('EditorRender', {
    scope = 'profile',
    needs = {},
    defaults = {
        snap = {1, 'toggle'}, grid = {8, 'int', 2, 32, 2}, showgrid = {1, 'toggle'},
        guidesize = {64, 'int', 16, 128, 16}, snapframes = {1, 'toggle'},
        gridOpacity = {38, 'int', 0, 100, 1}, moverLabels = {1, 'toggle'},
        snapDistance = {6, 'int', 1, 24, 1},
    },
})
local MOVERS = 56
local scale, screen = AF.Native.Scale, AF.Native.Screen
local guideReady
local function w(id) return AF.Native.Widget('UI', id) end
local function boxSize(mv) return mv.w, mv.h end

function M:Setting(key) return self.db[key] end
function M:Change(key, value) return AF.DB:Set(self, key, value, true) end
function M:StickyEnabled() return self.db.snapframes == 1 end
function M:Snap(value)
    if self.db.snap ~= 1 then return value end
    local g = self.db.grid * scale()
    return math.floor(value / g + .5) * g
end
function M:OnAction(action, arg, confirming)
    if action == 'unlock' then
        AF:Send('UnlockEditor')
        return {stop = true}
    elseif action == 'resetall' then
        if confirming then
            AF:Send('ResetAllPositions')
            return {note = 'All positions of this profile reset to the layout.'}
        end
        return {confirm = true, note = 'Reset every position of this profile? Click Confirm.'}
    end
end

local function placeBox(view, i)
    local mv = view.movers[i]; local s = scale(); local bw, bh = boxSize(mv)
    if mv.slot then
        w('Move' .. mv.slot):SetRect(mv.x / s, mv.y / s, bw / s, bh / s)
        AF.Skin.Remember(w('Move' .. mv.slot), mv.x / s, mv.y / s, bw / s, bh / s)
        AF.Skin.Border(w('Move' .. mv.slot), nil, nil, view.selected == i and 'Aether' or 'Gold')
    end
    if mv.tag then
        local sw, sh = screen(); local t = mv.tag
        local x = math.max(0, math.min(sw - t[3], mv.x + t[1]))
        local y = math.max(0, math.min(sh - t[4], mv.y + t[2]))
        w('MoveLabel' .. mv.slot):SetRect(x / s, y / s, t[3] / s, t[4] / s)
        local cx, cy = mv.x + bw / 2, mv.y + bh / 2
        local lx, ly = math.max(x, math.min(x + t[3], cx)), math.max(y, math.min(y + t[4], cy))
        -- Each tag is above/below or beside its box, so one straight tether suffices.
        if t[5] == 'Y' then lx = cx else ly = cy end
        local line = w('MoveLeader' .. mv.slot)
        line:SetRect(math.min(cx,lx)/s, math.min(cy,ly)/s,
                     math.max(1, math.ceil(math.abs(lx-cx)/s)), math.max(1, math.ceil(math.abs(ly-cy)/s)))
    end
end

local EDITOR_PARTS = {
    EditorPanel={0,0,380,278}, EditorTitle={10,4,158,22}, EditorSave={174,3,116,24}, EditorCancel={296,3,74,24},
    EditorProfile={10,31,194,20}, EditorStep={212,29,158,24}, EditorGrid={10,55,74,24}, EditorLess={88,55,24,24}, EditorSpacing={114,55,52,24},
    EditorMore={168,55,24,24}, EditorSnap={198,55,80,24}, EditorSticky={284,55,86,24},
    EditorFilter={10,83,100,24}, EditorHide={114,83,62,24}, EditorRestore={180,83,98,24}, EditorResetAll={282,83,88,24},
    EditorPrevious={10,113,24,24}, EditorNext={298,113,24,24}, EditorSettings={326,113,44,24}, NudgeName={40,115,254,22},
    EditorOrigin={10,143,136,24}, EditorXLabel={150,145,14,20}, EditorX={166,143,60,24},
    EditorYLabel={230,145,14,20}, EditorY={246,143,60,24}, EditorApply={312,143,58,24},
    NudgeLeft={10,173,24,24}, NudgeUp={38,173,24,24}, NudgeDown={66,173,24,24}, NudgeRight={94,173,24,24},
    EditorCenterH={124,173,76,24}, EditorCenterV={206,173,76,24}, NudgeReset={288,173,82,24},
    NudgeXY={10,203,360,20}, EditorStatus={10,226,360,20}, EditorHelp={10,250,360,20},
}
local ORIGINS = {{'Top left',0,0}, {'Top',.5,0}, {'Top right',1,0}, {'Left',0,.5}, {'Center',.5,.5},
                 {'Right',1,.5}, {'Bottom left',0,1}, {'Bottom',.5,1}, {'Bottom right',1,1}}
local FILTERS = {'All frames', 'HUD', 'Action bars', 'Menus', 'Windows'}

local function editorStatus(view, text)
    view.editorStatus = text
    if w('EditorStatus') then w('EditorStatus'):SetText(text or 'Save before travel. Cancel restores your layout.') end
end
local function guide(name, x, y, wd, ht, alpha)
    local widget = w(name); if not widget then return end
    guideReady = guideReady or {}
    if not guideReady[name] then AF.Skin.LineImage(widget); widget:SetAlpha(alpha); guideReady[name] = alpha
    elseif guideReady[name] ~= alpha then widget:SetAlpha(alpha); guideReady[name] = alpha end
    -- SetWidgetRect opcode 6 truncates to integers before applying UI scale.
    -- A one-physical-pixel line divided by scale > 1 otherwise becomes zero.
    local s = scale(); widget:SetRect(x / s, y / s, math.max(1, math.ceil(wd / s)), math.max(1, math.ceil(ht / s))); widget:Show()
end
local function editorGrid(view)
    if not w('GridX1') then return end
    local sw, sh = screen(); local s = scale()
    local step = M.db.guidesize * s
    -- At unusually wide viewports display whole multiples of the chosen cell,
    -- keeping the bounded pool complete instead of dropping lines at an edge.
    step = step * math.max(1, math.ceil(sw / (65 * step)), math.ceil(sh / (41 * step)))
    view.guideStep = step
    for _, axis in ipairs({{'X',64,sw}, {'Y',40,sh}}) do
        for i = 1, axis[2] do
            local pos = math.floor(i * step + .5)
            if M.db.showgrid == 1 and pos < axis[3] then
                guide('Grid' .. axis[1] .. i, axis[1] == 'X' and pos or 0, axis[1] == 'Y' and pos or 0,
                      axis[1] == 'X' and 1 or sw, axis[1] == 'Y' and 1 or sh, M.db.gridOpacity/100)
            else w('Grid' .. axis[1] .. i):Hide() end
        end
    end
    if M.db.showgrid == 1 then
        guide('GuideCenterX', math.floor(sw / 2), 0, 2, sh, math.min(1,.65*M.db.gridOpacity/38))
        guide('GuideCenterY', 0, math.floor(sh / 2), sw, 2, math.min(1,.65*M.db.gridOpacity/38))
    else w('GuideCenterX'):Hide(); w('GuideCenterY'):Hide() end
end

local function editorBoxes(view)
    local filter = FILTERS[view.editorFilter or 1]
    view.hiddenMovers = view.hiddenMovers or {}
    local eligible, selected = {}, 1
    for i, mv in ipairs(view.movers) do
        mv.slot, mv.tag = nil, nil
        mv.editorHidden = view.hiddenMovers[mv.id] or (filter ~= 'All frames' and AF.Movers:Category(mv) ~= filter)
        -- Keep every mapped frame in the selector. Closed ordinary windows
        -- get a preview box when selected, without covering the HUD in boxes.
        local category=AF.Movers:Category(mv)
        if not mv.editorHidden and (category ~= 'Windows' or mv.shown or view.selected == i) then
            eligible[#eligible+1] = i
            if view.selected == i then selected = #eligible end
        end
    end
    -- Reuse the bounded widget pool. The selector visits every mapped frame;
    -- selecting one on another page brings that page's boxes into the overlay.
    local first = math.floor((selected-1)/MOVERS)*MOVERS+1
    local s = scale(); local sw, sh = screen(); local occupied, tags = {}, {}
    local function overlaps(a,b)
        return a[1] < b[1]+b[3]+2*s and b[1] < a[1]+a[3]+2*s
           and a[2] < b[2]+b[4]+2*s and b[2] < a[2]+a[4]+2*s
    end
    occupied[1] = {(view.editorX or 0)*s, (view.editorY or 0)*s, 380*s, 278*s}
    for slot = 1, MOVERS do
        if w('MoveLabel'..slot) then w('MoveLabel'..slot):Hide(); w('MoveLeader'..slot):Hide() end
        local i = eligible[first+slot-1]
        if i then
            local mv=view.movers[i]; mv.slot=slot; mv.tag=nil; placeBox(view, i)
            w('Move'..slot):SetText(M.db.moverLabels==1 and mv.label or '')
            local tw, th = (#mv.label*8+16)*s, 20*s
            if M.db.moverLabels==1 and w('MoveLabel'..slot) and (mv.h < th or mv.w < tw) then
                w('Move'..slot):SetText(''); tags[#tags+1] = {i,math.min(tw,sw),th}
            else occupied[#occupied+1] = {mv.x+(mv.w-tw)/2,mv.y+(mv.h-th)/2,tw,th} end
            w('Move'..slot):SetTooltip(mv.label .. (mv.virtual and ' (not opened)' or '') .. ' - select for position arrows')
            AF.Skin.Mover(w('Move'..slot),view.selected==i)
            w('Move'..slot):SetAlpha(view.selected==i and .9 or .55); w('Move'..slot):Show()
        else w('Move'..slot):Hide(); AF.Skin.HideEdges(w('Move'..slot)) end
    end
    for _, entry in ipairs(tags) do
        local i, tw, th = unpack(entry); local mv = view.movers[i]; local chosen
        -- Search near the box first, reserving full names against other captions.
        for row = 0, math.ceil(sh/(th+4*s)) do
            local gap = (4+row*24)*s
            local cx,cy = mv.x+mv.w/2,mv.y+mv.h/2
            local candidates = {{cx-tw/2,mv.y-th-gap,tw,th,'Y'}, {cx-tw/2,mv.y+mv.h+gap,tw,th,'Y'},
                                {mv.x-tw-gap,cy-th/2,tw,th,'X'}, {mv.x+mv.w+gap,cy-th/2,tw,th,'X'}}
            for _, r in ipairs(candidates) do
                if r[1]>=0 and r[2]>=0 and r[1]+tw<=sw and r[2]+th<=sh then
                    local free = true; for _, other in ipairs(occupied) do if overlaps(r,other) then free=false; break end end
                    if free then chosen=r; break end
                end
            end
            if chosen then break end
        end
        chosen = chosen or {math.max(0,math.min(sw-tw,mv.x)),math.max(0,math.min(sh-th,mv.y-th)),tw,th,'Y'}
        occupied[#occupied+1]=chosen
        mv.tag={chosen[1]-mv.x,chosen[2]-mv.y,tw,th,chosen[5]}
        local label=w('MoveLabel'..mv.slot); label:SetText(mv.label); AF.Skin.Panel(label,tw/s,th/s); AF.Skin.Text(label,'Ice'); label:Show()
        guide('MoveLeader'..mv.slot,0,0,1,1,.65); placeBox(view, i)
    end
end

local function skinEditor()
    AF.Skin.Image(w('EditorPanel'),0,'editor-panel')
    for name,rect in pairs(EDITOR_PARTS) do
        if name~='EditorPanel' then
            if name=='EditorX' or name=='EditorY' then AF.Skin.Field(w(name),rect[3],rect[4])
            elseif name:match('^Editor') and not ({EditorTitle=true,EditorProfile=true,EditorXLabel=true,EditorYLabel=true,EditorStatus=true,EditorHelp=true})[name]
                or name:match('^Nudge') and not ({NudgeName=true,NudgeXY=true})[name] then
                AF.Skin.Button(w(name),rect[3],rect[4])
            end
        end
    end
end
local function placeEditor(view)
    if not w('EditorPanel') then return end
    local sw, sh = screen(); local s = scale()
    local x = math.max(0, math.min(sw / s - 380, view.editorX or (sw / s - 380) / 2))
    local y = math.max(0, math.min(sh / s - 278, view.editorY or 24))
    view.editorX, view.editorY = x, y
    for name, r in pairs(EDITOR_PARTS) do w(name):SetRect(x + r[1], y + r[2], r[3], r[4]); w(name):Show() end
    AF.Skin.Text(w('EditorTitle'),'Gold'); AF.Skin.Text(w('EditorSave'),'Gold')
    w('EditorTitle'):SetText('MOVE UI | drag header')
    w('EditorXLabel'):SetText('X'); w('EditorYLabel'):SetText('Y')
    w('EditorHelp'):SetText('Drag a box. Arrows: 1 px; Shift: 10 px.')
    AF.Skin.Text(w('NudgeName'),'Aether')
end

local function showNudge(view)
    local mv = view.selected and view.movers[view.selected]
    if not w('EditorPanel') then return end
    w('NudgeName'):SetText(mv and tostring(mv.label) or 'Select a frame')
    local o = ORIGINS[view.editorOrigin or 1]; local sw, sh = screen()
    w('EditorOrigin'):SetText('From: ' .. o[1])
    w('EditorX'):SetText(mv and tostring(math.floor(mv.x + mv.w * o[2] - sw * o[2] + .5)) or '')
    w('EditorY'):SetText(mv and tostring(math.floor(mv.y + mv.h * o[3] - sh * o[3] + .5)) or '')
    w('NudgeXY'):SetText(mv and string.format('Position %d, %d   |   Size %d x %d px', mv.x, mv.y, mv.w, mv.h) or 'Select a box, or use the frame selector above.')
    for i = 1, 4 do w('Selection' .. i):Hide() end
    if mv and not mv.editorHidden then
        guide('Selection1', mv.x, mv.y, mv.w, 2, 1); guide('Selection2', mv.x, mv.y + mv.h - 2, mv.w, 2, 1)
        guide('Selection3', mv.x, mv.y, 2, mv.h, 1); guide('Selection4', mv.x + mv.w - 2, mv.y, 2, mv.h, 1)
    end
end
local function hideNudge(view)
    for i = 1, 4 do if w('Selection' .. i) then w('Selection' .. i):Hide() end end
end
local function layout(view)
    skinEditor(); placeEditor(view); editorGrid(view); editorBoxes(view); showNudge(view)
    w('EditorProfile'):SetText('Profile: ' .. AF.DB:ProfileName())
    w('EditorStep'):SetText('Snap step: ' .. M.db.grid .. ' px')
    w('EditorStep'):SetTooltip('Snap step in UI pixels: click +2, Shift+click -2 (2 to 32). The visible grid has its own spacing.')
    for name, setting in pairs({Grid='showgrid', Snap='snap', Sticky='snapframes'}) do
        w('Editor' .. name):SetText(name .. ': ' .. (M.db[setting] == 1 and 'On' or 'Off'))
    end
    w('EditorSpacing'):SetText(string.format('%d px', view.guideStep / scale()))
    w('EditorSpacing'):SetTooltip('Visible grid spacing in UI pixels. The center lines mark the screen center.')
    w('EditorSnap'):SetTooltip('Snap dragged boxes to the ' .. M.db.grid .. ' UI-pixel grid. Change the step in Movers settings.')
    w('EditorSticky'):SetTooltip('Snap to other frames\' edges and centers.')
    w('EditorFilter'):SetText(FILTERS[view.editorFilter or 1])
    w('EditorOrigin'):SetTooltip('Coordinate reference only. X/Y are physical pixel offsets from this point of the screen to the same point of the box.')
    w('EditorSettings'):SetTooltip('Save / Lock, then open the selected frame\'s settings.')
    editorStatus(view, view.editorStatus)
end

function M:Layout(input)
    local view = AF.Copy(input)
    layout(view)
    return view
end
function M:PlaceBox(input, i)
    local view = AF.Copy(input)
    placeBox(view, i)
    return view
end
function M:PlaceEditor(input)
    local view = AF.Copy(input)
    placeEditor(view)
    return view
end
function M:ShowNudge(input)
    local view = AF.Copy(input)
    showNudge(view)
    return view
end
function M:HideNudge(input)
    local view = AF.Copy(input)
    hideNudge(view)
    return view
end
function M:Boxes(input)
    local view = AF.Copy(input)
    editorBoxes(view)
    return view
end
