-- Interaction controller. Geometry and rendering belong to registered adapters.
local M=AF:NewModule('MoverEditor',{needs={'Lifecycle','EditorRender'},defaults={}})
local S={movers={},unlocked=false}
local N=AF.Native
local n,scale,screen=N.Number,N.Scale,N.Screen
local dialog=N.Dialog
local panel,showPanel=N.Panel,N.ShowPanel
local PLUGIN_FIRST,MOVERS=0x20e,56
local function w(id) return N.Widget('UI',id) end
local function ticks() return AF:Call('Lifecycle','Ticks') end
local function get(key) if key=='profile' then return AF.DB:ProfileName() end;return AF:Call('EditorRender','Setting',key) end
local function dirty() AF.DB:Dirty() end
local function key(id) return AF.Movers:Key(id) end
local function boxSize(mv) return mv.w,mv.h end
local function render(method,...)
    local view=AF:Call('EditorRender',method,S,...)
    if not view then return end
    S.editorX,S.editorY,S.guideStep=view.editorX,view.editorY,view.guideStep
    for i,row in ipairs(view.movers) do
        if S.movers[i] then S.movers[i].slot,S.movers[i].tag,S.movers[i].editorHidden=row.slot,row.tag,row.editorHidden end
    end
end
local function placeBox(i) render('PlaceBox',i) end
local function hideNudge() render('HideNudge') end
local function placeEditor() render('PlaceEditor') end
local function editorBoxes() render('Boxes') end
local function snap(value) return AF:Call('EditorRender','Snap',value) end
local function editorCategory(mv) return AF.Movers:Category(mv) end
local function editorStatus(text)
    S.editorStatus=text
    if w('EditorStatus') then w('EditorStatus'):SetText(text or 'Save before travel. Cancel restores your layout.') end
end
function M:ShowNudge() render('ShowNudge') end
function M:EditorLayout() render('Layout') end
function M:BlocksSettings()
    if S.edit then editorStatus('Save / Lock or Cancel before opening settings.'); return true end
    return false
end
local function layoutMovers()
    local sw,sh=screen();local s=scale()
    local selectedId=S.selected and S.movers[S.selected] and S.movers[S.selected].id
    S.movers={}
    w('Movers'):SetRect(0,0,sw/s,sh/s)
    AF.Movers:Build(function(row,place)
        S.movers[#S.movers+1]=row
        if place then placeBox(#S.movers) end
    end,sw,sh)
    for i=#S.movers+1,MOVERS do w('Move'..i):Hide() end
    S.selected=nil
    for i,mv in ipairs(S.movers) do if mv.id==selectedId then S.selected=i end end
    if S.edit then M:CaptureEditFrames() end
    M:EditorLayout()
    showPanel('Movers',true)
end
local function snapToFrames(i, tx, ty)
    if get('snapframes') ~= 1 then return tx, ty end
    local mv = S.movers[i]; local bw, bh = boxSize(mv); local near = 6 * scale()
    local bx, by = math.huge, math.huge
    for j, o in ipairs(S.movers) do
        local sameMenu=AF.Movers:SnapRelated(mv.id,o.id)
        if j ~= i and not o.editorHidden and not sameMenu then
            local ow, oh = boxSize(o)
            for _, c in ipairs({{o.x, 0}, {o.x + ow, 0}, {o.x, bw}, {o.x + ow, bw}, {o.x + ow / 2, bw / 2}}) do
                local d = math.abs(tx + c[2] - c[1]); if d < near and d < bx then bx = d; mv.snapx = c[1] - c[2] end
            end
            for _, c in ipairs({{o.y, 0}, {o.y + oh, 0}, {o.y, bh}, {o.y + oh, bh}, {o.y + oh / 2, bh / 2}}) do
                local d = math.abs(ty + c[2] - c[1]); if d < near and d < by then by = d; mv.snapy = c[1] - c[2] end
            end
        end
    end
    return bx < math.huge and mv.snapx or tx, by < math.huge and mv.snapy or ty
end
local function moveMover(i,tx,ty)
    local mv=S.movers[i];local sw,sh=screen()
    local x,y=AF.Movers:Invoke(mv.id,'clamp',mv,tx,ty,sw,sh)
    if x then tx,ty=x,y else tx,ty=math.max(0,math.min(sw-mv.w,tx)),math.max(0,math.min(sh-mv.h,ty)) end
    if math.abs(tx-mv.x)<.5 and math.abs(ty-mv.y)<.5 then return end
    if AF.Movers:Invoke(mv.id,'move',AF.Copy(mv),tx,ty,sw,sh)==false then return false end
    mv.x,mv.y=tx,ty;placeBox(i)
    AF.Movers:Invoke(mv.id,'afterMove',AF.Copy(mv))
    if S.selected==i then M:ShowNudge() end
end
function M:MoveMover(...) return moveMover(...) end
local ORIGINS = {{'Top left',0,0}, {'Top',.5,0}, {'Top right',1,0}, {'Left',0,.5}, {'Center',.5,.5},
                 {'Right',1,.5}, {'Bottom left',0,1}, {'Bottom',.5,1}, {'Bottom right',1,1}}
local FILTERS = {'All frames', 'HUD', 'Action bars', 'Menus', 'Windows'}
local function onNudge(cx, cy)
    local s = scale()
    return w('EditorPanel') and cx >= (S.editorX or 0) * s and cx <= ((S.editorX or 0) + 380) * s
        and cy >= (S.editorY or 0) * s and cy <= ((S.editorY or 0) + 278) * s
end
function M:CaptureEditFrames()
    if not S.edit then return end
    local sw, sh = screen()
    for _, mv in ipairs(S.movers) do
        local d = mv.id < PLUGIN_FIRST and dialog(mv.id)
        if d and not S.edit.frames[mv.id] then S.edit.frames[mv.id] = {d.name, d.x / sw, d.y / sh} end
    end
end
function M:EndEdit(save, quiet, defer)
    if not S.edit then return true end
    if save then
        local ok=AF.DB:CommitDraft()
        if not ok then editorStatus('Save failed. Layout stays open; try Save again.'); return false end
    else
        local e = S.edit
        AF:Send('ReleaseLayoutPins')
        AF.DB:CancelDraft()
        S.restoreEdit = {identity=AF:Call('Lifecycle','Identity'), frames=e.frames}
        if not defer then M:RestoreEditFrames() end
        AF:Send('ResetLayoutCaches',true)
    end
    S.edit, S.unlocked, S.drag, S.panelDrag, S.selected = nil, false, nil, nil, nil
    AF.DB:EndDraft();AF:Send('EditorStateChanged',false)
    S.hiddenMovers, S.editorStatus, S.resetConfirm, S.refreshMovers = {}, nil, nil, nil
    showPanel('Movers', false); S.movers = {}
    if not defer then AF:Send('ApplySettings',true) end
    AF:Send('EditorLocked')
    if not quiet then DisplayChatMessage(0, save and 'Aetherfall layout saved and locked.' or 'Aetherfall layout changes cancelled.', .56, .89, .93) end
    AF:Send('RefreshOptions'); return true
end
function M:RestoreEditFrames()
    local restore = S.restoreEdit; if not restore then return end
    if restore.identity == AF:Call('Lifecycle','Identity') then
        local sw, sh = screen()
        for id, p in pairs(restore.frames) do
            local d = dialog(id)
            if d and d.name == p[1] then AFCore.Move(id, p[2] * sw, p[3] * sh) end
        end
    end
    S.restoreEdit = nil
end
function M:Editor(action)
    g_AddonName = 'RelicCalc'
    if not S.unlocked or not S.edit or not AF:Call('Lifecycle','IsSettled') or AFCore.Cursor() == nil then return end
    S.drag, S.wasDown, S.panelDrag = nil, true, nil
    if action == 'Save' or action == 'Cancel' then M:EndEdit(action == 'Save'); return end
    if action ~= 'ResetAll' then S.resetConfirm = nil; editorStatus() end
    local mv = S.selected and S.movers[S.selected]
    if action == 'Grid' or action == 'Snap' or action == 'Sticky' then
        local k = ({Grid='showgrid', Snap='snap', Sticky='snapframes'})[action]; AF:Call('EditorRender','Change',k,1-get(k))
    elseif action == 'More' or action == 'Less' then
        local size = get('guidesize') + (action == 'More' and 16 or -16)
        AF:Call('EditorRender','Change','guidesize',math.max(16,math.min(128,size)))
    elseif action == 'Step' then
        local _, _, _, shift = AFCore.Cursor()
        AF:Call('EditorRender','Change','grid',math.max(2,math.min(32,get('grid')+(shift and -2 or 2))))
    elseif action == 'Filter' then S.editorFilter = (S.editorFilter or 1) % #FILTERS + 1; S.selected=nil
    elseif action == 'Hide' and mv then S.hiddenMovers[mv.id] = true; S.selected = nil
    elseif action == 'Restore' then S.hiddenMovers = {}; S.editorFilter = 1
    elseif action == 'Previous' or action == 'Next' then
        local dir = action == 'Next' and 1 or -1
        for step = 1, #S.movers do
            local i = ((S.selected or (dir == 1 and 0 or 1)) - 1 + step * dir) % #S.movers + 1
            if not S.movers[i].editorHidden then S.selected = i; break end
        end
    elseif action == 'Origin' then S.editorOrigin = (S.editorOrigin or 1) % #ORIGINS + 1
    elseif action == 'Apply' and mv then
        local x, y = n(w('EditorX'):GetText()), n(w('EditorY'):GetText())
        if not x or not y then editorStatus('Enter a number for both X and Y.'); return end
        local sw, sh = screen(); local o = ORIGINS[S.editorOrigin or 1]
        moveMover(S.selected, x + (sw - mv.w) * o[2], y + (sh - mv.h) * o[3])
    elseif (action == 'CenterH' or action == 'CenterV') and mv then
        local sw, sh = screen(); moveMover(S.selected, action == 'CenterH' and (sw - mv.w) / 2 or mv.x, action == 'CenterV' and (sh - mv.h) / 2 or mv.y)
    elseif action == 'ResetAll' then
        if S.resetConfirm and ticks() <= S.resetConfirm then M:ResetAll(); S.resetConfirm = nil; editorStatus('All positions reset. Save to keep; Cancel to undo.')
        else S.resetConfirm = ticks() + 100; editorStatus('Reset every position? Click Reset all again.') end
    elseif action == 'Settings' then
        local tab=mv and AF.Movers:SettingsTab(mv) or 'Movers'
        if M:EndEdit(true) then AF:Send('OpenOptions',tab) end;return
    elseif action == 'Reset' or action == 'Left' or action == 'Up' or action == 'Down' or action == 'Right' then M:Nudge(action)
    end
    M:EditorLayout()
end
function M:Nudge(dir)
    g_AddonName = 'RelicCalc'
    local i = S.selected; local mv = i and S.movers[i]; if not mv then return end
    if dir == 'Reset' then M:ResetMover(i); M:ShowNudge(); return end
    local _, _, _, shift = AFCore.Cursor()
    local step = shift and 10 or 1
    local dx = dir == 'Left' and -step or dir == 'Right' and step or 0
    local dy = dir == 'Up' and -step or dir == 'Down' and step or 0
    moveMover(i, mv.x + dx, mv.y + dy)
end
-- Each tick while unlocked: start, follow or end a drag from the core's mouse reading.
local function dragMovers()
    local cx, cy, down, shift = AFCore.Cursor()
    cx, cy = n(cx), n(cy)
    if not cx then S.drag, S.panelDrag = nil, nil; return end
    if S.panelDrag then
        if down then S.editorX, S.editorY = cx / scale() - S.panelDrag.dx, cy / scale() - S.panelDrag.dy; placeEditor()
        else S.panelDrag = nil end
        S.wasDown = down and true or false; return
    end
    if down and not S.wasDown and onNudge(cx, cy) then
        local x, y = cx / scale() - S.editorX, cy / scale() - S.editorY
        if x < 170 and y < 28 then S.panelDrag = {dx=x,dy=y} end
    end
    local drag = S.drag
    if down and not drag and not S.wasDown and not onNudge(cx, cy) then
        for i = #S.movers, 1, -1 do  -- topmost box first
            local mv = S.movers[i]; local bw, bh = boxSize(mv)
            if mv.slot and not mv.editorHidden and cx >= mv.x and cx <= mv.x + bw and cy >= mv.y and cy <= mv.y + bh then
                S.selected = i
                if shift then M:ResetMover(i) else S.drag = {i = i, dx = cx - mv.x, dy = cy - mv.y} end
                M:ShowNudge()
                editorBoxes()
                break
            end
        end
    elseif down and drag then
        local tx, ty = snapToFrames(drag.i, snap(cx - drag.dx), snap(cy - drag.dy))
        moveMover(drag.i, tx, ty)
    elseif not down then
        if drag then
            local tx, ty = snapToFrames(drag.i, snap(cx - drag.dx), snap(cy - drag.dy))
            moveMover(drag.i, tx, ty)
        end
        S.drag = nil
    end
    S.wasDown = down and true or false
end
function M:Unlock(arg)
    g_AddonName = 'RelicCalc'
    S.unlocks = (S.unlocks or 0) + 1
    if not AF:Call('Lifecycle','IsReady') then AF:Call('Lifecycle','Enter') end
    if not AF:Call('Lifecycle','IsReady') or not _G.AFUIMovers then
        DisplayChatMessage(0, 'Aetherfall Interface: movers unavailable. Type /afcore for details.', 0.91, 0.63, 0.56)
        return
    end
    arg=tostring(arg or ''):lower()
    local _, native = panel('Movers')
    if S.unlocked and native and not native.shown then S.unlocked, S.drag = false, nil end
    if not S.unlocked and arg ~= 'lock' then
        if S.edit then M:EndEdit(false, true) end
        AF.DB:BeginDraft(); S.edit={frames={}}
        AF:Send('HideOptions')
        S.hiddenMovers, S.editorFilter, S.editorOrigin = {}, 1, 1
        S.editorOpenedAt = ticks()
        S.unlocked, S.drag, S.wasDown = true, nil, true; AF:Send('EditorStateChanged',true)  -- a press must start on a box
        layoutMovers()
        DisplayChatMessage(0, 'Aetherfall MoveUI: drag boxes, then Save / Lock. Cancel restores your starting layout. Drag the panel header to move it.', 0.56, 0.89, 0.93)
    else
        if not M:EndEdit(true) then return end
    end
    AF:Send('RefreshOptions')
end
function M:ResetMover(i)
    g_AddonName='RelicCalc'
    local mv=S.movers[i];if not mv then return end
    local sw,sh=screen()
    AF.Movers:Forget(mv.id);dirty()
    AF.Movers:Invoke(mv.id,'reset',AF.Copy(mv),sw,sh)
end
function M:ResetAll() AF:Send('ResetAllPositions') end
function M:RefreshMoverRects()
    if S.drag then return end
    for i, mv in ipairs(S.movers) do
        local d = mv.id < PLUGIN_FIRST and dialog(mv.id)
        if d then
            mv.virtual, mv.shown = nil, d.shown
            local x, y, wd, ht, ox, oy = AF.Movers:Invoke(mv.id,'bounds',mv.id,d)
            local changed = x ~= mv.x or y ~= mv.y or wd ~= mv.w or ht ~= mv.h
            mv.x, mv.y, mv.w, mv.h, mv.ox, mv.oy = x, y, wd, ht, ox, oy
            placeBox(i)
            if changed and S.selected == i then M:ShowNudge() end
        end
    end
end
function M:Refresh() if S.unlocked then layoutMovers() end end
function M:Status(text) editorStatus(text) end
function M:IsUnlocked() return S.unlocked end
function M:ClearDrag(refresh)
    S.drag,S.wasDown,S.panelDrag=nil,true,nil
    if refresh then S.refreshMovers=S.unlocked end
end
function M:Recover(keepEditor)
    self:ClearDrag()
    if not keepEditor then
        if S.unlocked and w('Movers') then w('Movers'):Hide() end
        S.unlocked,S.movers=false,{};AF:Send('EditorStateChanged',false)
    else S.refreshMovers=S.unlocked end
end
function M:Disconnect()
    S.drag,S.wasDown=nil,true
    S.unlocked,S.movers=false,{};AF:Send('EditorStateChanged',false)
    if w('Movers') then w('Movers'):Hide() end
end
function M:PrepareRecover(keepEditor)
    if not keepEditor and S.edit then self:EndEdit(false,true,true) end
end
function M:Discard() if S.edit then self:EndEdit(false,true,true) end end
function M:Restore() if S.restoreEdit then self:RestoreEditFrames() end end
function M:CharacterChanged() S.unlocked,S.restoreEdit=false,nil;AF:Send('EditorStateChanged',false) end
function M:Geometry(id,x,y,width,height,ox,oy,nudge,force)
    for i,mv in ipairs(S.movers) do
        if mv.id==id and (force or mv.x~=x or mv.y~=y or mv.w~=width or mv.h~=height) then
            mv.x,mv.y,mv.w,mv.h=x,y,width,height
            if ox~=nil then mv.ox,mv.oy=ox,oy end
            placeBox(i);if nudge and S.selected==i then self:ShowNudge() end
        end
    end
end
function M:Geometries(geometry)
    for _,mv in ipairs(S.movers) do local r=geometry[mv.id];if r then self:Geometry(mv.id,unpack(r)) end end
end
function M:RefreshRange(first,last,sw,sh,group)
    for i,mv in ipairs(S.movers) do
        local x,y,width,height
        if group and mv.id==group.id then x,y,width,height=group.x,group.y,group.w,group.h
        elseif mv.id>=first and mv.id<=last then
            local ox,oy;x,y,width,height,ox,oy=AF.Movers:Invoke(mv.id,'bounds',mv.id,sw,sh);mv.ox,mv.oy=ox,oy
        end
        if x and (mv.x~=x or mv.y~=y or mv.w~=width or mv.h~=height) then mv.x,mv.y,mv.w,mv.h=x,y,width,height;placeBox(i) end
    end
end
function M:UnlockSelected(arg,ids)
    self:Unlock(arg)
    if ids and S.unlocked then
        for i,mv in ipairs(S.movers) do
            local found=false;for _,id in ipairs(ids) do if mv.id==id then found=true end end
            if found then S.selected=i;break end
        end
        self:EditorLayout()
    end
end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('UnlockEditor',M.UnlockSelected)
M:Listen('RefreshMovers',M.Refresh)
M:Listen('MoverGeometry',M.Geometry)
M:Listen('MoverGeometries',M.Geometries)
M:Listen('RefreshMoverRange',M.RefreshRange)
M:Listen('CharacterChanged',M.CharacterChanged)
M:Phase('requests',function()
    if AF:Call('Lifecycle','IsSettled') then
        if S.refreshMovers then S.refreshMovers=nil;if S.unlocked then layoutMovers() end end
    end
end,20)
M:Phase('editor',function(self)
    if S.unlocked and AF:Call('Lifecycle','IsSettled') and ticks()-(S.editorOpenedAt or 0)>3 then
        local _,ed=panel('Movers');if ed and not ed.shown then self:EndEdit(false,true) end
    end
    if S.unlocked and AF:Call('Lifecycle','IsSettled') then
        dragMovers();if ticks()%5==0 then self:RefreshMoverRects() end
    end
end)
M:Listen('PrepareRecoverEditor',M.PrepareRecover)
M:Listen('RecoverEditor',M.Recover)
M:Listen('DisconnectEditor',M.Disconnect)
M:Listen('DiscardEditor',M.Discard)
M:Listen('RestoreEditor',M.Restore)
M:Listen('ClearEditorDrag',M.ClearDrag)
