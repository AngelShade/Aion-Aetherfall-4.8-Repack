-- Feature-local settings and native placement.
local M=AF:NewModule('TargetPanels',{scope='profile',needs={'Placement'},defaults={}})
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
local TARGET_DETAILS, ASSIST_DETAILS = 0x825, 0x826
local TARGET_PARTS = {
    [TARGET_DETAILS]={D.target,'target_dialog','Target Altitude / Distance'},
    [ASSIST_DETAILS]={D.target_assist,'target_assist_dialog','Target of Target Altitude / Distance'},
}
local function targetDetailRect(id)
    local spec=TARGET_PARTS[id];local d=spec and dialog(spec[1])
    if not d or d.name~=spec[2] then return end
    local layout=featureLayout(d.name,spec[1]);local node=featureWidget(layout,'dist_info')
    -- Offer only panels actually declared by this native layout. The shipped
    -- target-assist layout has no distance panel; do not invent one.
    if not node or node.kind~='container' or node.parent~='' or #node.rect~=4 then return end
    local s=scale();local sw,sh=screen();local p=AF.Movers:Get(id)
    local x,y=d.x+node.rect[1]*s,d.y+node.rect[2]*s
    if p then x,y=p[1]*sw,p[2]*sh end
    local width,height=node.rect[3]*s,node.rect[4]*s
    return math.max(0,math.min(sw-width,x)),math.max(0,math.min(sh-height,y)),width,height,node.path,spec[1]
end
function M:Hold(pinned)
    for _,id in ipairs({TARGET_DETAILS,ASSIST_DETAILS})do
        local x,y,width,height,path,owner=targetDetailRect(id)
        local d=owner and dialog(owner)
        if x and d then
            AFCore.Pin(owner,x-d.x,y-d.y,0,false,width,height,path,'any')
            pinned[owner..':'..path]={owner,path}
            AF:Send('MoverGeometry',id,x,y,width,height)
        end
    end
end
function M:Candidates(emit)
    for _,id in ipairs({TARGET_DETAILS,ASSIST_DETAILS}) do
        local x,y,width,height=targetDetailRect(id)
        if x then emit({id=id,x=x,y=y,w=width,h=height,exact=true,label=TARGET_PARTS[id][3]},false) end
    end
end
function M:Move(mv,x,y,sw,sh) AF.Movers:Remember(mv.id,nil,x,y,sw,sh);AF:Send('HoldLayout') end
function M:Reset() AF:Send('HoldLayout') end
AF.Movers:Register('target-panels',{owner='TargetPanels',order=30,first=TARGET_DETAILS,last=ASSIST_DETAILS,candidates='Candidates',move='Move',reset='Reset',category='HUD',tab='Movers'})
