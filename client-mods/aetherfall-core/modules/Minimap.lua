-- Feature-local settings and native placement.
local M=AF:NewModule('Minimap',{needs={'Placement','HUD'},defaults={mapsize={180,'int',120,320,10}}})
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
-- Independent widgets owned by native radar/subzone dialogs, not new windows.
local MAP_ZOOM, MAP_AREA, MAP_SWORD, MAP_CLOCK, MAP_VENDOR = 0x820, 0x821, 0x822, 0x823, 0x824
local MAP_LABELS = {[MAP_ZOOM]='Minimap Zoom', [MAP_CLOCK]='Minimap Clock', [MAP_VENDOR]='Vendor Notice',
                    [MAP_AREA]='Current Area', [MAP_SWORD]='Area Sword'}
local function minimapParts()
    local s = scale(); local sw, sh = screen()
    local size = math.min(get('mapsize')*s, sw, sh-18*s)
    local dx, dy = design(D.radar)
    local ref = designRef(D.radar)
    dx, dy = (dx or sw-188*s) + ((ref and ref[3] or 180)*s-size), dy or 22*s
    local saved=AF.Movers:Get(D.radar)
    if saved then dx,dy=saved[1]*sw,saved[2]*sh end
    dx,dy=math.max(0,math.min(sw-size,dx)),math.max(0,math.min(sh-size-18*s,dy))
    local info = AF:Call('HUD','HasMinimapInfo') and 18*s or 0
    local row = dy+size+info+2*s
    local result = {
        [D.radar]={dx,dy,size,size},
        [MAP_ZOOM]={dx+size-38*s,row,38*s,38*s},
        [MAP_CLOCK]={dx+size-75*s,row+4*s,31*s,31*s},
        [MAP_VENDOR]={dx+size-110*s,row+7*s,24*s,24*s},
        [MAP_AREA]={dx+32*s,row+42*s,math.max(86,get('mapsize')-34)*s,40*s},
        [MAP_SWORD]={dx,row+47*s,29*s,29*s},
    }
    for id, r in pairs(result) do
        local p = AF.Movers:Get(id)
        if p then r[1],r[2] = p[1]*sw,p[2]*sh end
        r[1] = math.max(0,math.min(sw-r[3],r[1]))
        r[2] = math.max(0,math.min(sh-r[4]-(id==D.radar and info or 0),r[2]))
    end
    return result
end
function M:Hold(pinned)
    local id = D.radar; local d = id and dialog(id)
    if not d or d.name ~= 'radar_dialog' then return end
    local s = scale(); local parts = minimapParts()
    local map = parts[id]
    -- Native children must remain inside their owner's canvas for dispatch and
    -- clipping. Its bounding canvas is invisible and only real widgets take hits.
    local x,y,right,bottom = map[1],map[2],map[1]+map[3],map[2]+map[4]
    for _,part in ipairs({MAP_ZOOM,MAP_CLOCK,MAP_VENDOR}) do
        local r=parts[part]
        x,y,right,bottom=math.min(x,r[1]),math.min(y,r[2]),math.max(right,r[1]+r[3]),math.max(bottom,r[2]+r[4])
    end
    AFCore.Gaps(id,true)
    AFCore.Pin(id,x,y,0,false,right-x,bottom-y); pinned[id]=true
    local function child(owner,name,cx,cy,cw,ch,mode)
        AFCore.Pin(owner,cx,cy,mode or 0,false,cw,ch,name,'any')
        pinned[owner..':'..name]={owner,name}
    end
    local mx,my = map[1]-x,map[2]-y
    child(id,'af_minimap_border',mx,my,map[3],map[4])
    child(id,'radar_wnd',mx+5*s,my+5*s,map[3]-10*s,map[4]-10*s)
    child(id,'radar_sight',mx+5*s,my+5*s,map[3]-10*s,map[4]-10*s)
    local radarLayout=featureLayout(d.name,id);local zoom=featureWidget(radarLayout,'btn_zoom')
    local zoomParent=zoom and radarLayout.paths[zoom.parent]
    for _,control in ipairs({{MAP_ZOOM,zoomParent and zoomParent.path},{MAP_CLOCK,'time_slot'},{MAP_VENDOR,'vendor_notice'}}) do
        local r=parts[control[1]]
        if control[2] then child(id,control[2],r[1]-x,r[2]-y,r[3],r[4]) end
    end
    local aid = D.subzone_name; local area = aid and dialog(aid)
    if area and area.name == 'subzone_name_dialog' then
        local name,sword = parts[MAP_AREA],parts[MAP_SWORD]
        local ax,ay = math.min(name[1],sword[1]),math.min(name[2],sword[2])
        local ar = math.max(name[1]+name[3],sword[1]+sword[3])
        local ab = math.max(name[2]+name[4],sword[2]+sword[4])
        AFCore.Gaps(aid,true)
        AFCore.Pin(aid,ax,ay,0,false,ar-ax,ab-ay); pinned[aid]=true
        child(aid,'neutral',sword[1]-ax,sword[2]-ay,sword[3],sword[4])
        child(aid,'text',name[1]-ax,name[2]-ay,name[3],18*s)
        child(aid,'server_name',name[1]-ax,name[2]-ay+20*s,name[3],18*s)
        child(aid,'bgimage',0,0,140*s,38*s,5)
    end
    local geometry={}
    for part,r in pairs(parts) do geometry[part]={r[1],r[2],r[3],r[4]+(part==id and AF:Call('HUD','HasMinimapInfo') and 18*s or 0)} end
    AF:Send('MoverGeometries',geometry)
end
function M:Align()
    for id in pairs(MAP_LABELS)do AF.Movers:Forget(id) end
    AF.DB:Dirty();AF:Send('HoldLayout');AF:Send('RefreshMovers')
end
function M:Parts() return minimapParts() end
function M:Candidates(emit)
    local radar=D.radar and dialog(D.radar)
    if radar and radar.name=='radar_dialog' then
        local parts=minimapParts()
        for _,id in ipairs({MAP_ZOOM,MAP_CLOCK,MAP_VENDOR,MAP_AREA,MAP_SWORD}) do
            local r=parts[id];emit({id=id,x=r[1],y=r[2],w=r[3],h=r[4],exact=true,label=MAP_LABELS[id]},true)
        end
    end
end
function M:Move(mv,x,y,sw,sh) AF.Movers:Remember(mv.id,nil,x,y,sw,sh);AF:Send('HoldLayout') end
function M:Reset() AF:Send('HoldLayout') end
function M:OnAction() self:Align() end
AF.Movers:Register('minimap',{owner='Minimap',order=50,first=MAP_ZOOM,last=MAP_VENDOR,candidates='Candidates',move='Move',reset='Reset',category='HUD',tab='Minimap'})
AF.Config:Register(M,{{name='Minimap',order=7,hint='Move the map and its zoom, clock and zone name with /moveui.',rows={
 {order=2,label='Minimap size',kind='number',k='mapsize',unit=' px'},
 {order=3,label='Align map controls',kind='action',action='alignmap',text='Align'}}}})
