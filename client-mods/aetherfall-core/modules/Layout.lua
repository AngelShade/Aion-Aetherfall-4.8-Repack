-- The original hold -> enforce -> opacity order is an explicit coordinator.
local M=AF:NewModule('Layout',{scope='profile',needs={'Lifecycle','Placement','Tooltip','Shortcuts','ActionBars','Minimap','TargetPanels','WorldMap','Opacity','Visibility','Camera','HUD'},defaults={}})
local S={pins={}}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local dialog,screen,scale=N.Dialog,N.Screen,N.Scale
local function life(method,...) return AF:Call('Lifecycle',method,...) end
local function place(method,...) return AF:Call('Placement',method,...) end
local function releasePins()
    if next(S.pins) then AFCore.Pin();S.pins={};AF:Send('PinsChanged',S.pins) end
    AF:Send('PinsReleased')
end
local function applyPins()
    local sw,sh=screen();local prefix=AF.DB:ProfileName()..'.'
    local ids={}
    for k in AF.Movers:Positions() do
        if k:sub(1,#prefix)==prefix then
            local id=tonumber(k:sub(#prefix+1));local d=id and dialog(id)
            if d and (place('Owned',id,d) or life('IsUnlocked')) then ids[id]=true end
        end
    end
    for id in place('Reanchored') do ids[id]=true end
    AF:Call('Tooltip','Find')
    for id in AF:Call('Tooltip','Each') do ids[id]=nil end
    local menu=AF:Call('Shortcuts','Managed')
    if menu then ids[D.start]=nil end
    local pinned={}
    for id in pairs(ids) do
        local d=dialog(id);local p=d and AF.Movers:Get(id,d)
        local x,y
        if p then x,y=p[1]*sw,p[2]*sh
        elseif d and place('IsReanchored',id) and place('DesignRef',id) and d.name==place('DesignRef',id)[7]
            and (id~=D.pet or math.abs(d.w-place('DesignRef',id)[3]*scale())<=2) then x,y=place('Design',id) end
        if x and id~=D.radar and id~=D.subzone_name and not AF:Call('ActionBars','Managed',id,d) then
            if AFCore.Pin(id,x,y) then
                pinned[id]=true
                if d.shown and (math.abs(d.x-x)>1 or math.abs(d.y-y)>1) then AFCore.Move(id,x,y) end
            end
        end
    end
    AF:Call('Tooltip','Hold')
    AF:Call('Shortcuts','Hold',pinned,sw,sh,menu)
    AF:Call('ActionBars','Hold',pinned)
    AF:Call('Minimap','Hold',pinned)
    AF:Call('TargetPanels','Hold',pinned)
    AF:Call('Shortcuts','HoldPopup')
    for k,v in pairs(S.pins) do
        if not pinned[k] then
            if type(v)=='table' then AFCore.Pin(v[1],nil,nil,nil,nil,nil,nil,v[2]) else AFCore.Pin(k) end
        end
    end
    S.pins=pinned;AF:Send('PinsChanged',pinned)
    AF:Call('WorldMap','Hold')
end
function M:Hold()
    if S.holding then return end
    S.holding=true;local ok,why=pcall(applyPins);S.holding=false
    if not ok then S.pinError=tostring(why) end
end
function M:ApplyVisible()
    if S.applyingVisibleSettings then return end
    S.applyingVisibleSettings=true
    local ok,why=pcall(function() self:Hold();place('Enforce');AF:Call('Opacity','Apply') end)
    S.applyingVisibleSettings=nil
    if not ok then error(why) end
end
function M:QueueVisible()
    place('QueueVisible');AF:Call('WorldMap','QueueVisible');S.visiblePending=true
end
function M:ApplySettings(resetLayout,setting)
    if AF:Call('Camera','Owns',setting) then return end
    if AF:Call('Opacity','Owns',setting) then
        if life('IsHudOn') and life('IsSettled') then AF:Call('Opacity','Apply') end
        return
    end
    if resetLayout then AF:Send('ResetLayoutCaches') end
    if not setting or AF:Call('Visibility','Owns',setting) then AF:Call('Visibility','Apply',true) end
    if (not setting or setting=='botbar') and PlayerBotBar_ApplyVisibility then PlayerBotBar_ApplyVisibility() end
    if life('IsHudOn') and life('IsSettled') then self:ApplyVisible() end
    AF:Call('HUD','Apply')
    AF:Send('RefreshMovers')
end
function M:OnSetting(setting)
    self:ApplySettings(false,setting)
    if setting=='botbar' then AF.DB:Save() end
end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('HoldLayout',M.Hold)
M:Listen('ReleaseLayoutPins',releasePins)
M:Listen('ApplySettings',M.ApplySettings)
M:Listen('SettingChanged',M.OnSetting)
M:Listen('QueueVisibleSettings',M.QueueVisible)
M:Listen('MarkPinsDirty',function() S.pinsDirty=true end)
M:Listen('NativePinHeld',function(_,id) S.pins[id]=true;AF:Send('PinsChanged',S.pins) end)
M:Phase('holds',function(self)
    if not life('IsHudOn') then releasePins()
    elseif life('IsSettled') then
        AF:Call('Visibility','PrepareHolds')
        if S.pinsDirty or life('IsUnlocked') or life('Ticks')%4==0 then S.pinsDirty=nil;self:Hold()
        else AF:Call('Tooltip','Hold') end
    end
end)
M:Phase('visible-settings',function(self)
    local periodic=life('Ticks')%20==0
    if life('IsSettled') and life('IsHudOn') and (S.visiblePending or periodic) then
        S.visiblePending=nil
        if periodic then AF:Call('Visibility','Apply',false) end
        local ok,why=pcall(self.ApplyVisible,self);if not ok then S.enforceError=tostring(why) end
    end
end)
