-- Passive frames borrow native rectangles and keep every native input owner.
local template = {
    enabled={1,'toggle'}, healthHeight={16,'int',8,40}, powerHeight={13,'int',4,24}, spacing={2,'int',0,8},
    healthMode={'class','enum',{'class','fixed','custom'}}, healthColor={'#4ca673','color'},
    powerMode={'shared','enum',{'shared','custom'}},stateColors={'shared','enum',{'shared','custom'}},
    powerColor={'#3d85c6','color'}, deadColor={'#666666','color'}, offlineColor={'#414953','color'},
    backdropOpacity={95,'int',0,100}, border={1,'toggle'}, font={0,'int',0,8},
    healthLeft={'[name:short]','string',128}, healthCentre={'','string',128}, healthRight={'[hp:percent]','string',128},
    powerLeft={'[mp:current]','string',128}, powerCentre={'','string',128}, powerRight={'[mp:percent]','string',128},
    showLevel={1,'toggle'}, nameLimit={24,'int',8,64}, showBot={1,'toggle'}, showRarity={1,'toggle'}, showRank={1,'toggle'},
    showDP={1,'toggle'}, dpHeight={13,'int',2,16}, dpText={'[dp:current-max]','string',128},
}
AF.DB:RegisterTemplate('UnitFrame',template)
local instances = {}
for _, name in ipairs({'player','target','targettarget','party'}) do
    local aliases,only = {},{}
    for key in pairs(template) do
        local playerOnly=key=='showDP' or key=='dpHeight' or key=='dpText'
        local botOnly=key=='showBot' or key=='showRarity' or key=='showRank'
        if (name=='player' or not playerOnly) and (not botOnly or name=='target' or name=='party') then
            only[key]=true
            aliases['uf'..name:sub(1,1):upper()..name:sub(2)..key:sub(1,1):upper()..key:sub(2)]=key
        end
    end
    instances[name]={template='UnitFrame',legacy=aliases,only=only,
        defaults=name=='party' and {healthHeight=13,powerHeight=9,nameLimit=18,powerLeft='',powerRight=''} or nil}
end
local M=AF:NewModule('UnitFrames',{scope='profile',needs={'Lifecycle','Identity'},instances=instances,defaults={
    ufEnabled={1,'toggle'},ufInterval={4,'int',1,20},
    ufFont={8,'choice',{'Default','Option','Value','Title','Large title','Window','Name','Button'}},
    ufFixedColor={'#4ca673','color'},ufPowerColor={'#3d85c6','color'},ufBackdropColor={'#101820','color'},
    ufBorderColor={'#203448','color'},ufTextColor={'#dfe9f0','color'},ufDeadColor={'#666666','color'},
    ufOfflineColor={'#414953','color'},
}})
local S={frames={},elements={},order={},native={},identities={},active=false}
local N,K=AF.Native,AF.Skin
local D=AFCore and AFCore.DIALOG or {}
local FONTS={'ui_default','v3_option','v3_value','ui_static_title','v3_option_title','window','name','button'}
local PARTS={'Backdrop','HealthBG','HealthFill','PowerBG','PowerFill','DPBG','DPFill',
    'HealthTextLeft','HealthTextCentre','HealthTextRight','PowerTextLeft','PowerTextCentre','PowerTextRight',
    'Level','Identity','State','DPTextCentre','Star1','Star2','Star3','Star4','Star5'}
local EVENTS={'USER_CHANGED_TARGET','PARTY_MEMBERS_CHANGED','UNIT_ATTACK_DAMAGE','UNIT_SKILL_DAMAGE','GAME_UI_DIALOG_VISIBLE_CHANGED'}
local function bool(value) return value==true or value==1 end
local function localShown(widget) return widget and type(widget.flags)=='number' and math.floor(widget.flags)%2==1 end
local function same(frame,dialog) return dialog and frame.nativeIdentity==N.Identity(dialog) end
function M:RegisterElement(name,spec)
    assert(not S.elements[name] and type(spec)=='table','duplicate unit-frame element')
    S.elements[name]=spec;S.order[#S.order+1]=name
end
function M:RegisterNative(definitions) S.native=definitions end
function M:IsReplacing(unit)
    if unit=='party' then
        for i=1,6 do if S.frames['party'..i] and S.frames['party'..i].owned then return true end end
        return false
    end
    return S.frames[unit] and S.frames[unit].owned==true or false
end
function M:IsEnabled(group) return M.db.ufEnabled==1 and (not group or M.db[group].enabled==1) end
-- Whether the stock bot labels show gear and rank, following this unit's toggles.
function M:IdentityParts(group)
    if not M:IsEnabled(group) then return true,true end
    local s=M.db[group];return s.showRarity==1,s.showRank==1
end
local function announce(frame,on)
    local partyBefore=M:IsReplacing('party')
    frame.owned=on
    AF:Send('UnitFramesChanged',frame.unit,on)
    local partyAfter=M:IsReplacing('party')
    if partyBefore~=partyAfter then AF:Send('UnitFramesChanged','party',partyAfter) end
end
local function hide(frame)
    for _,object in pairs(frame.widgets or {}) do K.Hide(object) end
    for _,name in ipairs(S.order) do
        local fn=S.elements[name].Disable
        if fn then fn(frame) end
    end
end
-- Parked widgets: the game re-shows some stock party pieces (HP/MP numbers, class icon) on its
-- own updates, so hiding alone flickered. A pin keeps them off screen; release puts them back.
local function park(frame,d)
    local paths=frame.native.offscreen
    if not paths or not AFCore.Pin or not d then return end
    frame.parked=frame.parked or {}
    for _,path in ipairs(paths) do
        if not frame.parked[path] then
            local widget=AFCore.Widget(frame.id,path)
            local r=widget and widget.rect
            if r and N.Number(r[1]) and AFCore.Pin(frame.id,0,0,5,false,0,0,path,'any') then
                frame.parked[path]={r[1]-d.x,r[2]-d.y,r[3],r[4]}
            end
        end
    end
end
local function unpark(frame,restore)
    for path,r in pairs(frame.parked or {}) do
        if restore then AFCore.Pin(frame.id,r[1],r[2],0,false,r[3],r[4],path,'any') end
        AFCore.Pin(frame.id,nil,nil,nil,nil,nil,nil,path)
    end
    frame.parked=nil
end
local function release(frame)
    if not frame.owned then hide(frame);return end
    local d=N.Dialog(frame.id)
    unpark(frame,same(frame,d))
    if same(frame,d) then
        for _,entry in ipairs(frame.capture or {}) do
            if entry.backdrop then AFCore.Backdrop(frame.id,1,entry.path,entry.kind)
            else
                local current=AFCore.Widget(frame.id,entry.path)
                if current and current.type==entry.kind and localShown(current)~=entry.shown then
                    AFCore.WidgetShown(frame.id,entry.path,entry.shown)
                end
            end
        end
    end
    frame.capture,frame.nativeIdentity,frame.nativeDirty,frame.valueSignature,frame.rectIndex=nil,nil,nil,nil,nil
    hide(frame);announce(frame,false)
end
local function inspect(frame,d)
    if not d or d.name~=frame.native.layout or not d.instance or not AFCore.Widget or not AFCore.WidgetShown or not AFCore.Backdrop then return end
    local selected={}
    for _,entry in ipairs(frame.native.visuals) do
        local widget=AFCore.Widget(frame.id,entry.path)
        if not widget and not entry.optional then return end
        if widget then
            if widget.type~=entry.kind or type(widget.flags)~='number' then return end
            selected[#selected+1]={path=entry.path,kind=entry.kind,shown=localShown(widget)}
        end
    end
    return selected
end
local function acquire(frame,d)
    local selected=inspect(frame,d);if not selected then return false end
    -- Existing text owners restore their own local hides before our snapshot.
    announce(frame,true)
    selected=inspect(frame,d)
    if not selected then announce(frame,false);return false end
    frame.nativeIdentity=N.Identity(d);frame.capture=selected;frame.nativeDirty=true
    if frame.native.backdrop then
        if not AFCore.Backdrop(frame.id,0,frame.native.backdrop,0) then release(frame);return false end
        selected[#selected+1]={path=frame.native.backdrop,kind=0,backdrop=true}
    end
    return true
end
local function suppress(frame)
    -- Every refresh, not only when our values change: the game re-shows its own party gauges
    -- (with their HP/MP numbers) on its own updates, and they then drew over the frame.
    for _,entry in ipairs(frame.capture or {}) do
        if not entry.backdrop then
            local widget=AFCore.Widget(frame.id,entry.path)
            if widget and widget.type==entry.kind and localShown(widget) then AFCore.WidgetShown(frame.id,entry.path,false) end
        end
    end
    frame.nativeDirty=false
end
local function values(frame)
    local unit=frame.unit
    local value={unit=unit,nameLimit=frame.settings.nameLimit}
    if unit=='target' or unit=='targettarget' then
        local id,name,level,hp,hpmax,mp,mpmax=AFCore.Unit(unit)
        if not N.Number(id) or id<=0 or type(name)~='string' or name=='' then return end
        value.id,value.name,value.level,value.hp,value.hpmax,value.mp,value.mpmax=id,name,level,N.Number(hp),N.Number(hpmax),N.Number(mp),N.Number(mpmax)
        -- The target bridge has no class/dead/connectivity fields.
        value.dead=value.hp~=nil and value.hp<=0
    else
        value.name=N.Call('UnitName',unit)
        if type(value.name)~='string' or value.name=='' then return end
        if frame.group=='party' and AFCore.Text(frame.id,frame.native.title)~=value.name then return end
        value.level=N.Number(N.Call('UnitLevel',unit))
        value.hp,value.hpmax=N.Number(N.Call('UnitHP',unit)),N.Number(N.Call('UnitHPMax',unit))
        value.mp,value.mpmax=N.Number(N.Call('UnitMP',unit)),N.Number(N.Call('UnitMPMax',unit))
        value.class=N.Call('UnitClass',unit)
        if type(value.class)~='string' then value.class=nil end
        value.dead=bool(N.Call('UnitIsDead',unit)) or (value.hp~=nil and value.hp<=0)
    end
    value.level=N.Number(value.level) or ''
    value.unavailable=not value.hp or not value.hpmax or value.hpmax<=0
    if frame.group~='party' and value.unavailable then return end
    value.state=value.unavailable and 'Unavailable' or value.dead and 'Dead' or ''
    -- Bot identity changes rarely: look it up when the name changes, then every five seconds.
    local tick=AF:Call('Lifecycle','Ticks') or 0
    local known=S.identities[value.name]
    if not known or tick-known.tick>=100 then
        known={tick=tick};known.rarity,known.rank,known.stars=AF:Call('Identity','Lookup',value.name)
        S.identities[value.name]=known
    end
    value.rarity,value.rank,value.rankStars=known.rarity,known.rank,known.stars
    value.bot=value.rarity and 'Bot' or ''
    if unit=='player' then value.dp,value.dpmax=AFCore.Gauge(D.basic_status,'dp_gauge_text') end
    return value
end
local function rectangles(frame,x,y,width,height)
    local settings,s=frame.settings,N.Scale()
    -- All native bounds are physical pixels; only the plugin placement divides by scale.
    x,y,width,height=x/s,y/s,width/s,height/s
    if frame.group=='party' then width=math.min(width,200) end -- stock voice-chat input starts at 202
    -- The header holds one line of text (about 14 units); smaller boxes clipped the letters.
    local inset,header=5,math.min(18,math.max(12,height*.35))
    local gap=settings.spacing
    local dp=frame.unit=='player' and settings.showDP==1 and math.min(settings.dpHeight,height*.2) or 0
    local usable=math.max(4,height-header-inset-gap-(dp>0 and dp+gap or 0))
    local requested=settings.healthHeight+settings.powerHeight
    local ratio=math.min(1,usable/requested)
    local hp,mp=settings.healthHeight*ratio,settings.powerHeight*ratio
    local left,top,barwidth=x+inset,y+header,math.max(1,width-inset*2)
    frame.rect={x,y,width,height}
    frame.bars={health={left,top,barwidth,hp},power={left,top+hp+gap,barwidth,mp},
        dp={left,top+hp+mp+gap*2,barwidth,dp},header={left,y+1,barwidth,math.max(8,header-1)}}
    frame.reserve=frame.native.headerReserve or 32
end
local function render(frame,value)
    frame.values=value
    frame.appearance={font=FONTS[frame.settings.font>0 and frame.settings.font or M.db.ufFont],
        fixed=M.db.ufFixedColor,power=M.db.ufPowerColor,backdrop=M.db.ufBackdropColor,
        border=M.db.ufBorderColor,text=M.db.ufTextColor,dead=M.db.ufDeadColor,offline=M.db.ufOfflineColor}
    for _,name in ipairs(S.order) do
        local element=S.elements[name]
        if element.Configure then element.Configure(frame) end
        if element.Update then element.Update(frame) end
    end
end
local function signature(value)
    local result={}
    for _,key in ipairs({'id','name','level','hp','hpmax','mp','mpmax','class','dead','unavailable','dp','dpmax'}) do
        result[#result+1]=tostring(value[key])
    end
    return table.concat(result,'\31')
end
local bind -- defined below, after the elements' helpers
-- One bounded snapshot, written two seconds after frames first draw in a session
-- (Mods/AetherfallUI/afui-frames.cfg): where the client really put the canvas and each part.
local function dump(file)
    local lines={'AFUI frames '..tostring((N:Resource('lazy') or {}).version)..' scale='..tostring(N.Scale())}
    local canvasId
    for id=0x20e,0x221 do
        local name,x,y,w,h,shown,class,instance,flags=AFCore.Dialog(id)
        if name then lines[#lines+1]=string.format('slot 0x%x %s x=%s y=%s w=%s h=%s shown=%s flags=%s',id,name,tostring(x),tostring(y),tostring(w),tostring(h),tostring(shown),tostring(flags)) end
        if name=='AFHFrames' then canvasId=id end
    end
    local canvas=N.Widget('H','Frames')
    if canvas then lines[#lines+1]='canvas lua visible='..tostring(canvas:IsVisible())..' rect='..table.concat({tostring((canvas:GetRect()))},',') end
    for _,frame in ipairs(S.frames) do
        lines[#lines+1]=string.format('%s owned=%s rect=%s values=%s',frame.unit,tostring(frame.owned),
            frame.rect and table.concat(frame.rect,',') or '-',frame.values and tostring(frame.values.name) or '-')
        if frame.unit=='target' or frame.unit=='targettarget' then
            local d=N.Dialog(frame.id)
            if d then lines[#lines+1]=string.format('  native dialog=%s,%s,%s,%s shown=%s container=%s',tostring(d.x),tostring(d.y),tostring(d.w),tostring(d.h),tostring(d.shown),tostring(frame.rectIndex)) end
        end
        if frame.values then lines[#lines+1]='  dp='..tostring(frame.values.dp)..'/'..tostring(frame.values.dpmax)..' identity='..tostring(frame.values.rankStars) end
        if frame.owned then
            local hidden={}
            for _,entry in ipairs(frame.capture or {}) do
                if not entry.backdrop then hidden[#hidden+1]=entry.path..'='..tostring(localShown(AFCore.Widget(frame.id,entry.path))) end
            end
            lines[#lines+1]='  native shown: '..table.concat(hidden,' ')
        end
        if frame.owned and canvasId then
            for _,part in ipairs({'Backdrop','HealthBG','HealthFill','HealthTextLeft','DPFill','DPTextCentre','Identity','Star1'}) do
                local node=AFCore.Widget(canvasId,'AFHF'..(frame.unit=='targettarget' and 'TargetTarget' or frame.unit:sub(1,1):upper()..frame.unit:sub(2))..part)
                local r=node and node.rect or {}
                lines[#lines+1]=string.format('  %s found=%s shown=%s flags=%s rect=%s,%s,%s,%s',part,tostring(node~=nil),tostring(node and node.shown),tostring(node and node.flags),tostring(r[1]),tostring(r[2]),tostring(r[3]),tostring(r[4]))
            end
        end
    end
    AF.DB:Write(file or 'afui-frames.cfg',table.concat(lines,'\n')..'\n')
end
function M:Refresh()
    if not S.active or M.db.ufEnabled~=1 then return end
    if not bind() then return end
    local canvas=N.Widget('H','Frames')
    if not canvas then return end
    -- Loading/focus settling retains native visual ownership without acquiring
    -- new native instances. Character changes release snapshots in their message.
    -- A lost or different character hides everything. While the interface settles (after an
    -- alt-tab, a loading screen or a zone change) owned frames keep drawing; hiding them left
    -- the spot empty because the native bars stay hidden.
    if not AF:Call('Lifecycle','IsReady') or N.PlayerIdentity()~=AF:Call('Lifecycle','Identity') then
        K.Hide(canvas);return
    end
    local settled=AF:Call('Lifecycle','IsSettled')
    local any=false
    for _,frame in ipairs(S.frames) do
        frame.settings=M.db[frame.group]
        local d=N.Dialog(frame.id)
        if frame.owned and not same(frame,d) then release(frame) end
        local x,y,width,height,shown
        if d and d.name==frame.native.layout then
            -- 'any': a bare widget name is looked up as an item view by the bridge, so the
            -- party rows (containers) were never found and never taken over.
            if frame.native.rect then x,y,width,height,shown=AFCore.Rect(frame.id,frame.native.rect,'any')
            elseif frame.native.rects then
                local paths,first=frame.native.rects,frame.rectIndex or 1
                for step=0,#paths-1 do
                    local i=(first-1+step)%#paths+1
                    x,y,width,height,shown=AFCore.Rect(frame.id,paths[i],'any')
                    if shown and N.Number(width) and width>0 then frame.rectIndex=i;break end
                end
            else x,y,width,height,shown=d.x,d.y,d.w,d.h,d.shown end
        end
        local available=S.hudOn and frame.settings.enabled==1 and shown and N.Number(width) and N.Number(height) and width>0 and height>0
        local value=available and values(frame) or nil
        -- A native frame that could not be taken over is retried when the game rebuilds it,
        -- or every five seconds, not on every refresh.
        local tick=AF:Call('Lifecycle','Ticks') or 0
        local identity=d and N.Identity(d)
        local waiting=not frame.owned and frame.failedIdentity==identity and tick<(frame.retryAt or 0)
        local acquired=value and not waiting and (frame.owned or (settled and acquire(frame,d)))
        if value and settled and not frame.owned and not waiting and not acquired then frame.failedIdentity,frame.retryAt=identity,tick+100 end
        if acquired then
            local stamp=signature(value)
            if frame.valueSignature~=stamp then frame.valueSignature=stamp;frame.nativeDirty=true end
            suppress(frame);park(frame,d);rectangles(frame,x,y,width,height);render(frame,value);any=true
        else release(frame) end
    end
    if any then
        local sw,sh=N.Screen();local scale=N.Scale()
        K.Place(canvas,0,0,sw/scale,sh/scale);K.Show(canvas)
        if not S.dumped then
            local tick=AF:Call('Lifecycle','Ticks') or 0
            S.dumpAt=S.dumpAt or tick+40
            if tick>=S.dumpAt then S.dumped=true;pcall(dump) end
        end
        -- A second report the first time a target is drawn: its size and container.
        local target=S.frames.target
        if target and target.owned and not S.dumpedTarget then S.dumpedTarget=true;pcall(dump,'afui-frames-target.cfg') end
        local party=S.frames.party1
        if party and party.owned and not S.dumpedParty then S.dumpedParty=true;pcall(dump,'afui-frames-party.cfg') end
    else K.Hide(canvas) end
end
local function stop()
    if S.timer then AF.Events:Cancel(S.timer);S.timer=nil end
    if S.pending then AF.Events:Cancel(S.pending);S.pending=nil end
    S.lastRefreshTick=nil
    for _,frame in ipairs(S.frames) do release(frame) end
    K.Hide(N.Widget('H','Frames'))
end
local function timedRefresh()
    local tick=AF:Call('Lifecycle','Ticks')
    if S.lastRefreshTick==tick then return end
    S.lastRefreshTick=tick
    M:Refresh()
end
local function queueRefresh(_,event)
    if not S.active or M.db.ufEnabled~=1 then return end
    for _,frame in ipairs(S.frames) do
        if (event=='USER_CHANGED_TARGET' and (frame.group=='target' or frame.group=='targettarget'))
            or (event=='PARTY_MEMBERS_CHANGED' and frame.group=='party')
            or (event~='USER_CHANGED_TARGET' and event~='PARTY_MEMBERS_CHANGED') then frame.nativeDirty=true end
    end
    -- A party or target change can hire or release a bot: read identities afresh.
    -- It can also rebuild a native frame, so failed takeovers are retried at once.
    if event=='PARTY_MEMBERS_CHANGED' or event=='USER_CHANGED_TARGET' then
        S.identities={}
        for _,frame in ipairs(S.frames) do frame.failedIdentity,frame.retryAt=nil,nil end
    end
    -- The first event in a tick redraws at once; further events in the same tick (damage
    -- arrives in bursts) are coalesced into one refresh on the next tick.
    local tick=AF:Call('Lifecycle','Ticks')
    if S.eventTick~=tick then
        S.eventTick=tick
        if S.pending then AF.Events:Cancel(S.pending);S.pending=nil end
        M:Refresh();return
    end
    if S.pending then return end
    S.pending=M:After(1,function() S.pending=nil;timedRefresh() end)
end
local function schedule()
    if S.timer then AF.Events:Cancel(S.timer);S.timer=nil end
    if S.active and M.db.ufEnabled==1 then S.timer=M:Every(M.db.ufInterval,timedRefresh) else stop() end
end
function M:OnInitialize()
    for _,unit in ipairs({'player','target','targettarget','party1','party2','party3','party4','party5','party6'}) do
        local group=unit:match('^party') and 'party' or unit
        local native=S.native[unit]
        if native then
            local prefix='AFHF'..(unit=='targettarget' and 'TargetTarget' or unit:sub(1,1):upper()..unit:sub(2))
            local frame={unit=unit,group=group,native=native,id=D[native.dialog],widgets={},prefix=prefix}
            S.frames[#S.frames+1]=frame;S.frames[unit]=frame
        end
    end
end
-- In game the plugin's Lua loads before its XML, so the canvas, its pieces and the data bar
-- do not exist yet at OnInitialize. Bind them on first use instead; until every piece exists
-- nothing is drawn or taken over.
bind=function()
    if S.bound then return true end
    local top,canvas=N.Widget('H','Top'),N.Widget('H','Frames')
    if not top or not canvas then return false end
    for _,frame in ipairs(S.frames) do
        for _,part in ipairs(PARTS) do
            local widget=_G[frame.prefix..part]
            if not widget then return false end
            frame.widgets[part]=widget
        end
    end
    for _,event in ipairs(EVENTS) do top:RegisterEvent(event) end
    for _,frame in ipairs(S.frames) do
        for _,name in ipairs(S.order) do if S.elements[name].Construct then S.elements[name].Construct(frame) end end
        -- Start hidden explicitly, as the HUD does with its own widgets at load.
        hide(frame)
    end
    K.Hide(canvas)
    S.bound=true
    return true
end
M.Bind=bind
-- The data bar's OnLoad runs once the plugin's XML exists (Lifecycle then binds commands):
-- bind and hide the pieces there, so nothing waits for the first draw.
M:Listen('BindCommands',function() bind() end)
-- Layout released every pin (interface off, character change): park again on the next refresh.
M:Listen('PinsReleased',function() for _,frame in ipairs(S.frames) do frame.parked=nil end end)
function M:OnEnable() S.active=true;schedule() end
function M:OnDisable() S.active=false;stop() end
function M:OnProfile() stop();schedule() end
function M:OnSetting(key)
    for _,frame in ipairs(S.frames) do frame.failedIdentity,frame.retryAt=nil,nil end
    if key=='ufEnabled' or key=='ufInterval' then schedule() end
    self:Refresh()
end
function M:Diagnostics()
    local frames={}
    for _,frame in ipairs(S.frames) do frames[frame.unit]={owned=frame.owned==true,rect=AF.Copy(frame.rect),values=AF.Copy(frame.values)} end
    return {active=S.active,timer=S.timer~=nil,frames=frames}
end
M:Listen('HudChanged',function(_,on) S.hudOn=on and true or false;if S.hudOn then schedule() else stop() end end)
M:Listen('CharacterChanged',function() stop();S.identities={};for _,frame in ipairs(S.frames) do frame.failedIdentity,frame.retryAt=nil,nil end end,-5)
M:Listen('BackdropsCleared',function()
    if not S.active or M.db.ufEnabled~=1 then return end
    for _,frame in ipairs(S.frames) do
        if frame.owned and frame.native.backdrop then AFCore.Backdrop(frame.id,0,frame.native.backdrop,0) end
    end
end)
for _,event in ipairs(EVENTS) do
    M:On(event,queueRefresh,20)
end
M:On('ADDON_EXIT',function() M:OnDisable() end,20)
