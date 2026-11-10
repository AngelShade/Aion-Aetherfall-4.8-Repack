-- Registered feature adapters keep the editor independent of frame types.
local M=AF:NewModule('Shortcuts',{scope='profile',needs={'Placement','HUD'},defaults={menusize={34,'int',24,48,2},menucols={7,'int',1,14,1},menugap={4,'int',0,16,2}}})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
local function key(id) return AF.Movers:Key(id) end
local function position(id,d) return AF.Movers:Get(id,d) end
local function remember(...) return AF.Movers:Remember(...) end
local function design(id) return AF:Call('Placement','Design',id) end
local function designRef(id) return AF:Call('Placement','DesignRef',id) end
local function defaultSpot(id,d) return AF:Call('Placement','DefaultSpot',id,d) end
local MENU_ID = 0x800
local MENU_GROUP = 0x81f
local MENU_BUTTONS = {
    {'fly_gauge', 'Flight', 'flygauge'}, {'start_button', 'Start', 'button'}, {'prophet_button', 'Influence', 'button'},
    {'btn_radar_map', 'Map', 'button'}, {'ingame_web', 'Web', 'button'}, {'companion_menu_button', 'Companions', 'button'},
    {'season_pass_button', 'Pass', 'button'}, {'journey_menu_button', 'Journey', 'button'},
    {'wardrobe_menu_button', 'Wardrobe', 'button'}, {'central_market_button', 'Market', 'button'},
    {'item_shop_container', 'Shop', 'container'},
    {'mail_notice', 'Mail', 'button', {'mail_full_notice', 'express_mail_notice', 'cash_mail_notice'}},
    -- The interface's own (the core runs "/say .broker" and "/afui" from the start menu's handler).
    {'aetherfall_broker_button', 'Broker', 'button'}, {'aetherfall_menu_button', 'Interface', 'button'},
}
local function menuManaged()
    local d = D.start and N:Resource('menu') and dialog(D.start)
    return d and d.name == 'start_dialog' and d or nil
end
-- Absolute spot of button k: its mover's, else its layout spot in the menu (where the old Menu
-- mover or the layout put the menu).
local function menuGrid(sw,sh)
    local s = n((AFCore.Scale())) or 1
    local size = get('menusize') * s
    local ox, oy
    local m = AF.Movers:Get(MENU_GROUP) or (D.start and AF.Movers:Get(D.start))
    if m then ox, oy = m[1] * sw, m[2] * sh else ox, oy = design(D.start) end
    if not ox then return end
    local pitch = size + get('menugap') * s
    local maxcols=math.max(1,math.floor((sw+get('menugap')*s)/pitch))
    local maxrows=math.max(1,math.floor((sh+get('menugap')*s)/pitch))
    local cols=math.min(maxcols,math.max(math.ceil(#MENU_BUTTONS/maxrows),get('menucols')))
    local width = math.min(cols, #MENU_BUTTONS) * pitch - get('menugap') * s
    local height = math.ceil(#MENU_BUTTONS / cols) * pitch - get('menugap') * s
    ox, oy = math.max(0, math.min(sw - width, ox)), math.max(0, math.min(sh - height, oy))
    return {x=ox,y=oy,size=size,pitch=pitch,cols=cols,w=width,h=height}
end
function M:Metrics()local sw,sh=screen();return menuGrid(sw,sh)end
local function buttonSpot(k, sw, sh)
    local layout=featureLayout('start_dialog',D.start)
    if not MENU_BUTTONS[k] or not featureWidget(layout,MENU_BUTTONS[k][1]) then return end
    local grid=menuGrid(sw,sh);if not grid then return end
    local p=AF.Movers:Get(MENU_ID+k)
    if p then return math.max(0,math.min(sw-grid.size,p[1]*sw)),math.max(0,math.min(sh-grid.size,p[2]*sh)),grid.size,grid.size end
    return grid.x+((k-1)%grid.cols)*grid.pitch,grid.y+math.floor((k-1)/grid.cols)*grid.pitch,grid.size,grid.size
end
local function buttonMoverRect(k,sw,sh)
    local x,y,width,height=buttonSpot(k,sw,sh)
    if not x then return end
    if MENU_BUTTONS[k][1]=='item_shop_container' then
        local ui=AFCore.UI
        for _,row in ipairs(ui and ui.skins and ui.skins.af_shortcut_mkt_shop_up or {})do
            local bounds=row.contentBounds
            if bounds and #bounds==4 then
                local ox,oy=width*bounds[1],height*bounds[2]
                return x+ox,y+oy,width*bounds[3],height*bounds[4],ox,oy
            end
        end
    end
    return x,y,width,height,0,0
end
local function menuBounds()
    local sw,sh=screen();local x0,y0,x1,y1=math.huge,math.huge,-math.huge,-math.huge
    for k in ipairs(MENU_BUTTONS) do
        local x,y,width,height=buttonSpot(k,sw,sh)
        if x then x0,y0,x1,y1=math.min(x0,x),math.min(y0,y),math.max(x1,x+width),math.max(y1,y+height) end
    end
    if x0<math.huge then return x0,y0,x1-x0,y1-y0 end
end
local function holdMenuPopup()
    if not menuManaged() or not AFCore.PopupOwner then return end
    -- UIPopupMenu::Init (536020) assigns 1e9 + depth: only 1e9 is the
    -- root. Hover submenus can retain the same command owner as the root;
    -- leave their native cascading placement at depths 1/2 untouched.
    local id=0x1e9
    if AFCore.PopupOwner(id)~=D.start then return end
    local d=dialog(id)
    if not d or not d.shown then return end
    local layout=featureLayout('start_dialog',D.start)
    local arrow=featureWidget(layout,'start_button')
    local owner={};for v in tostring(layout and layout.definition.attributes.frame or ''):gmatch('[^,]+')do owner[#owner+1]=tonumber(v)end
    if not arrow or arrow.parent~='' or #arrow.rect~=4 or #owner~=4 then return end
    local ax,ay,aw,ah,shown=AFCore.Rect(D.start,arrow.path,'button')
    if not ax or not shown then return end
    local s=scale();local sw,sh=screen()
    -- DlgStart::OpenMenu (8b6d10), original mode-5 placement for the root.
    local x,y=ax-(arrow.rect[1]+50)*s,ay-arrow.rect[2]*s
    if x+d.w>sw then x=x+owner[3]*s-d.w end
    y=y-d.h;if y<0 then y=ay-arrow.rect[2]*s+owner[4]*s end
    if math.abs(d.x-x)>.01 or math.abs(d.y-y)>.01 then AFCore.Move(id,x,y)end
end
-- Additional Functions menu: entries that have a button on the HUD while the interface is on are
-- left out (RelicCalc registers them from its OnLoad, after every plugin file has run).
function M:Hold(pinned,sw,sh,menu)
    if menu then
        local spots, x0, y0, x1, y1 = {}, math.huge, math.huge, -math.huge, -math.huge
        for k = 1, #MENU_BUTTONS do
            local x, y, bw, bh = buttonSpot(k, sw, sh)
            if x then
                spots[k] = {x, y, bw, bh}
                x0, y0, x1, y1 = math.min(x0, x), math.min(y0, y), math.max(x1, x + bw), math.max(y1, y + bh)
            end
        end
        if x0 < math.huge then
            if not S.menuGaps then S.menuGaps = AFCore.Gaps(D.start, true) end
            pinned[D.start] = true
            AFCore.Pin(D.start, x0, y0, 0, false, x1 - x0, y1 - y0)
            for k, b in ipairs(MENU_BUTTONS) do
                local sp = spots[k]
                for _, name in ipairs(sp and {b[1], unpack(b[4] or {})} or {}) do
                    AFCore.Pin(D.start, sp[1] - x0, sp[2] - y0, 0, false, sp[3], sp[4], name, b[3])
                    pinned[D.start .. ':' .. name] = {D.start, name}
                end
                if sp and b[1]=='item_shop_container' then
                    local layout=featureLayout('start_dialog',D.start);local parent=featureWidget(layout,b[1])
                    if parent then
                        for _,node in pairs(layout.paths) do
                            local r=node.rect
                            if node.parent==parent.path and #r==4 then
                                local factor=sp[3]/parent.rect[3]
                                -- Core widget pins are relative to the owner
                                -- dialog, including children inside containers.
                                AFCore.Pin(D.start,sp[1]-x0+r[1]*factor,sp[2]-y0+r[2]*factor,0,false,r[3]*factor,r[4]*factor,node.path,'any')
                                pinned[D.start..':'..node.path]={D.start,node.path}
                            end
                        end
                    end
                end
            end
        end
        local gx,gy,gw,gh=menuBounds()
        AF:Send('RefreshMoverRange',MENU_ID+1,MENU_ID+#MENU_BUTTONS,sw,sh,{id=MENU_GROUP,x=gx,y=gy,w=gw,h=gh})
    end
end
function M:Align(resetAnchor)
    for k in ipairs(MENU_BUTTONS) do
        AF.Movers:Forget(MENU_ID+k)
    end
    if resetAnchor then
        AF.Movers:Forget(MENU_GROUP)
        AF.Movers:Forget(D.start)
    end
    AF.DB:Dirty(); AF:Send('HoldLayout')
    AF:Send('RefreshMovers')
end
function M:ButtonRects()
    local d = menuManaged(); if not d then return {} end
    local sw, sh = screen(); local rects = {}
    for k in ipairs(MENU_BUTTONS) do
        local x, y, bw, bh = buttonSpot(k, sw, sh)
        if x then rects[#rects + 1] = {x, y, bw, bh} end
    end
    return rects, {d.x, d.y, d.w, d.h}
end
function M:Managed() return menuManaged() end
function M:HoldPopup() return holdMenuPopup() end
function M:ClearIndividual()
    for i in ipairs(MENU_BUTTONS) do AF.Movers:Forget(MENU_ID+i) end
end
function M:OnSetting() self:ClearIndividual() end
function M:OnAction() self:Align() end
function M:FormatOption(spec)
    local requested=get(spec.k);local m=spec.k=='menucols' and self:Metrics()
    return tostring(requested)..(spec.unit or '')..(m and m.cols~=requested and (' ('..m.cols..' fit)') or '')
end
function M:Candidates(emit,sw,sh)
    if menuManaged() then
        local x,y,width,height=menuBounds()
        if x then emit({id=MENU_GROUP,x=x,y=y,w=width,h=height,exact=true,label='Shortcuts (all)'},false) end
    end
    for k=1,menuManaged() and #MENU_BUTTONS or 0 do
        local x,y,bw,bh,ox,oy=buttonMoverRect(k,sw,sh)
        if x then emit({id=MENU_ID+k,x=x,y=y,w=bw,h=bh,ox=ox,oy=oy,exact=true,label=MENU_BUTTONS[k][2]},true) end
    end
end
function M:Bounds(id,sw,sh) return buttonMoverRect(id-MENU_ID,sw,sh) end
function M:Clamp(mv,x,y,sw,sh)
    if mv.id==MENU_GROUP then return math.max(0,math.min(sw-mv.w,x)),math.max(0,math.min(sh-mv.h,y)) end
    local grid=menuGrid(sw,sh);local size=grid and grid.size or mv.w
    local ox,oy=mv.ox or 0,mv.oy or 0
    return math.max(ox,math.min(sw-size+ox,x)),math.max(oy,math.min(sh-size+oy,y))
end
function M:Move(mv,x,y,sw,sh)
    if mv.id==MENU_GROUP then
        local dx,dy=x-mv.x,y-mv.y;local grid=menuGrid(sw,sh)
        if not grid then return false end
        for index in ipairs(MENU_BUTTONS) do
            if AF.Movers:Get(MENU_ID+index) then
                local bx,by=buttonSpot(index,sw,sh)
                if bx then remember(MENU_ID+index,nil,bx+dx,by+dy,sw,sh) end
            end
        end
        remember(MENU_GROUP,nil,grid.x+dx,grid.y+dy,sw,sh)
        AF:Send('HoldLayout');return true
    end
    remember(mv.id,nil,x-(mv.ox or 0),y-(mv.oy or 0),sw,sh);AF:Send('HoldLayout')
end
function M:Reset(mv,sw,sh)
    if mv.id==MENU_GROUP then return self:Align(true) end
    local x,y,w,h,ox,oy=buttonMoverRect(mv.id-MENU_ID,sw,sh)
    AF:Send('MoverGeometry',mv.id,x,y,w,h,ox,oy,false,true)
end
local ON_HUD={['/companions']=true,['/seasonpass']=true,['/journey']=true,['/wardrobe']=true,['/privatewarehouse']=true,['/say .broker']=true,['/afui']=true}
function M:AllowMenu(command)
    if ON_HUD[tostring(command)] and AF:Call('HUD','Active') then S.leftOut=(S.leftOut or 0)+1;return false end
    return true
end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('PinsReleased',function() S.menuGaps=nil end)
M:Listen('RecoverNativeWindows',function() S.menuGaps=nil end)
AF.Movers:Register('shortcuts',{owner='Shortcuts',order=40,first=MENU_ID+1,last=MENU_GROUP,candidates='Candidates',bounds='Bounds',clamp='Clamp',move='Move',reset='Reset',category='Menus',tab='Shortcuts',group=MENU_GROUP})
