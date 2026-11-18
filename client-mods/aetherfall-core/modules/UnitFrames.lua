-- Passive frames borrow native rectangles and keep every native input owner.
-- Text SIZE ids 9..15 {client fontshader,px}; saved keys ufFont/<unit>.font hold the id. Labels live in options.
-- Retired ids 1..8 (old 8-name list) stay valid in saves and are only normalized when read; saves are never rewritten.
local FONTS={[9]={'v3_shortcut',9},[10]={'ui_default',12},[11]={'v3_option',12},[12]={'large',13},
    [13]={'v3_chat_normal',14},[14]={'v3_quest_indicator_title',15},[15]={'v3_chat_large',16}}
local LEGACY_FONT={10,11,11,10,12,10,10,10}
local template = {
    enabled={1,'toggle'}, healthHeight={16,'int',8,40}, powerHeight={13,'int',4,24}, spacing={2,'int',0,8},
    healthMode={'class','enum',{'class','fixed','custom'}}, healthColor={'#4ca673','color'},
    powerMode={'shared','enum',{'shared','custom'}},stateColors={'shared','enum',{'shared','custom'}},
    powerColor={'#3d85c6','color'}, deadColor={'#666666','color'}, offlineColor={'#414953','color'},
    backdropOpacity={95,'int',0,100}, border={1,'toggle'}, font={0,'int',0,15},
    healthLeft={'[name:short]','string',128}, healthCentre={'','string',128}, healthRight={'[hp:percent]','string',128},
    powerLeft={'[mp:current]','string',128}, powerCentre={'','string',128}, powerRight={'[mp:percent]','string',128},
    showLevel={1,'toggle'}, nameLimit={24,'int',8,64}, showBot={1,'toggle'}, showRarity={1,'toggle'}, showRank={1,'toggle'},
    showDP={1,'toggle'}, dpHeight={13,'int',2,16}, dpText={'[dp:current-max]','string',128}, showPortrait={1,'toggle'},
    portraitOpacity={70,'int',0,100}, -- backdrop-coloured background behind the 3D portrait
    -- Percentages: whole native frame and individual bar area; 100 preserves existing profiles.
    frameWidth={100,'int',50,300}, frameHeight={100,'int',50,300},
    healthWidth={100,'int',10,100}, powerWidth={100,'int',10,100}, dpWidth={100,'int',10,100},
    showClass={1,'toggle'}, textMode={'shared','enum',{'shared','custom'}}, textColor={'#dfe9f0','color'},
    classIconX={0,'int',-200,200},classIconY={0,'int',-60,60},gearX={0,'int',-200,200},gearY={0,'int',-60,60},levelX={0,'int',-200,200},levelY={0,'int',-60,60},stateX={0,'int',-200,200},stateY={0,'int',-60,60},chestX={0,'int',-200,200},chestY={0,'int',-60,60},
}
AF.DB:RegisterTemplate('UnitFrame',template)
local instances = {}
for _, name in ipairs({'player','target','targettarget','party'}) do
    local aliases,only = {},{}
    for key in pairs(template) do
        local playerOnly=key=='showDP' or key=='dpHeight' or key=='dpText' or key=='dpWidth'
        local portrait=key=='showPortrait' or key=='portraitOpacity' -- player, target and party bot portraits
        local botOnly=key=='showBot' or key=='showRarity' or key=='showRank' or key=='gearX' or key=='gearY'
        if (name=='player' or not playerOnly) and (not botOnly or name=='target' or name=='party') and (not portrait or name~='targettarget') and (name=='target' or (key~='chestX' and key~='chestY')) then
            only[key]=true
            aliases['uf'..name:sub(1,1):upper()..name:sub(2)..key:sub(1,1):upper()..key:sub(2)]=key
        end
    end
    instances[name]={template='UnitFrame',legacy=aliases,only=only,
        defaults=name=='party' and {healthHeight=13,powerHeight=9,nameLimit=18,powerLeft='',powerRight=''} or nil}
end
local M=AF:NewModule('UnitFrames',{scope='profile',needs={'Lifecycle','Identity'},instances=instances,defaults={
    ufEnabled={1,'toggle'},ufInterval={4,'int',1,20},
    ufFont={10,'int',1,15},
    ufFixedColor={'#4ca673','color'},ufPowerColor={'#3d85c6','color'},ufBackdropColor={'#101820','color'},
    ufBorderColor={'#203448','color'},ufTextColor={'#dfe9f0','color'},ufDeadColor={'#666666','color'},
    ufOfflineColor={'#414953','color'},
}})
local S={frames={},elements={},order={},native={},identities={},geometry={},active=false,rev=0,paints=0}
-- IDs0..16: fallback name, native emblem (0xba28e0 via0x8bb600), class colour.
local CLASSES={[0]={'Warrior','warrior','#c69b6d'},{'Gladiator','fighter','#c69b6d'},{'Templar','knight','#c69b6d'},
    {'Scout','scout','#abd473'},{'Assassin','assassin','#fff569'},{'Ranger','ranger','#abd473'},{'Mage','mage','#69ccf0'},
    {'Sorcerer','wizard','#69ccf0'},{'Spiritmaster','elementalist','#9482c9'},{'Priest','cleric','#ffffff'},
    {'Cleric','priest','#ffffff'},{'Chanter','chanter','#f58cba'},{'Technist','engineer','#40c7b4'},
    {'Aethertech','rider','#0070de'},{'Gunner','gunner','#40c7b4'},{'Artist','artist','#00ff96'},{'Songweaver','bard','#00ff96'}}
function M:ClassInfo(id) return CLASSES[id] end
local N,K=AF.Native,AF.Skin
local D=AFCore and AFCore.DIALOG or {}
local PARTS={'Backdrop','HealthBG','HealthFill','PowerBG','PowerFill','DPBG','DPFill',
    'HealthTextLeft','HealthTextCentre','HealthTextRight','PowerTextLeft','PowerTextCentre','PowerTextRight',
    'Level','Identity','State','DPTextCentre','Star1','Star2','Star3','Star4','Star5','Crest','PortraitBG','PortraitBorder','ClassIcon'}
local EVENTS={'USER_CHANGED_TARGET','PARTY_MEMBERS_CHANGED','UNIT_ATTACK_DAMAGE','UNIT_SKILL_DAMAGE','GAME_UI_DIALOG_VISIBLE_CHANGED'}
local function bool(value) return value==true or value==1 end
local function localShown(widget) return widget and type(widget.flags)=='number' and math.floor(widget.flags)%2==1 end
local function same(frame,dialog) return dialog and frame.nativeIdentity==N.Identity(dialog) end
function M:RegisterElement(name,spec)
    assert(not S.elements[name] and type(spec)=='table','duplicate unit-frame element')
    S.elements[name]=spec;S.order[#S.order+1]=name
end
function M:RegisterNative(definitions) S.native=definitions end
function M:RegisterDump(fn) S.dump=fn end -- the frame report lives with the Indicators element
function M:Frames() return S.frames end
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
local function hide(frame, verify)
    for _,object in pairs(frame.widgets) do K.Hide(object, verify) end
    for _,name in ipairs(S.order) do
        local fn=S.elements[name].Disable
        if fn then fn(frame) end
    end
end
-- Parked widgets: the game re-shows some stock pieces on its own updates; a pin keeps them off screen.
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
    frame.capture,frame.nativeIdentity,frame.nativeDirty,frame.valueSignature,frame.rectIndex,frame.classCache,frame.cfgKey=nil,nil,nil,nil,nil,nil,nil
    hide(frame)
    if frame.unit=='target' or frame.unit=='targettarget' then
        -- Plugin visibility writes are queued. Check the real widgets after the
        -- queue has had a turn, while the player and party keep the canvas open.
        if frame.hideCheck then AF.Events:Cancel(frame.hideCheck) end
        frame.hideCheck=M:After(2,function()
            frame.hideCheck=nil
            if not frame.owned then hide(frame,true) end
        end)
    end
    announce(frame,false)
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
local function values(frame,d) -- d: this refresh's dialog snapshot
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
        if frame.group=='party' and not frame.native.matches(AFCore.Text(frame.id,frame.native.title),value.name) then return end
        value.level=N.Number(N.Call('UnitLevel',unit))
        value.hp,value.hpmax=N.Number(N.Call('UnitHP',unit)),N.Number(N.Call('UnitHPMax',unit))
        value.mp,value.mpmax=N.Number(N.Call('UnitMP',unit)),N.Number(N.Call('UnitMPMax',unit))
        value.class=N.Call('UnitClass',unit)
        if type(value.class)~='string' then value.class=nil end
        value.dead=bool(N.Call('UnitIsDead',unit)) or (value.hp~=nil and value.hp<=0)
    end
    value.level=N.Number(value.level) or ''
    value.unavailable=not value.hp or not value.hpmax or value.hpmax<=0
    if frame.group~='party' and value.unavailable then return end;value.state=value.unavailable and 'Unavailable' or value.dead and 'Dead' or ''
    -- Bot identity changes rarely: look it up when the name changes, then every five seconds.
    local tick=AF:Call('Lifecycle','Ticks') or 0
    local known=S.identities[value.name]
    if not known or tick-known.tick>=100 then
        known={tick=tick};known.rarity,known.rank,known.stars=AF:Call('Identity','Lookup',value.name)
        S.identities[value.name]=known
    end
    value.rarity,value.rank,value.rankStars=known.rarity,known.rank,known.stars
    -- Class belongs to this native unit, never the name-keyed rarity cache.
    local token=tostring(value.id or value.name)..'\31'..tostring(N.Identity(N.Dialog(frame.id)))
    local class=frame.classCache
    if not class or class.token~=token or tick>=class.retryAt then
        class={token=token,retryAt=tick+100}
        if AFCore.UnitClass then class.id,class.name=AFCore.UnitClass(frame.group=='party' and 'party' or unit,frame.group=='party' and value.name or nil) end
        if not CLASSES[N.Number(class.id)] then class.retryAt=tick+20 end
        frame.classCache=class
    end
    value.classId=N.Number(class.id);if CLASSES[value.classId] then value.class=nil end -- else stock UnitClass name
    local info=CLASSES[value.classId];if info and (type(value.class)~='string' or value.class=='') then value.class=type(class.name)=='string' and class.name~='' and class.name or info[1] end
    value.bot=value.rarity and 'Bot' or ''
    if unit=='player' then value.dp,value.dpmax=AFCore.Gauge(D.basic_status,'dp_gauge_text') end
    return value
end
-- Immutable UI-unit baselines per native instance prevent compounded sizing.
local function geometry(id,d,group)
    if not d or not d.instance then return end;local token,g=N.Identity(d),S.geometry[id]
    if g and g.token==token then return g end
    -- The interface's Lua restarts on a re-login while this dialog lives on, still at the size the last
    -- session gave it. Measured as it is, every re-login multiplied the frame by its percentage again.
    local restored=AFCore.PinRestore and N.Number(AFCore.PinRestore(id))
    if restored and restored>0 then d=N.Dialog(id);if not d or N.Identity(d)~=token then return end end
    local s=N.Scale();g={token=token,group=group,w=d.w/s,h=d.h/s,nodes={},paths={}}
    local function capture(path)
        local x,y,w,h=AFCore.Rect(id,path,'any')
        if N.Number(x) and N.Number(w) and w>0 and h>0 then g.nodes[path]={(x-d.x)/s,(y-d.y)/s,w/s,h/s};g.paths[#g.paths+1]=path end
    end
    if group=='party' then
        for i=1,6 do capture('ctn_member'..i);capture('ctn_member'..i..'/#1');capture('ctn_member'..i..'/fly_gauge') end
        local _,_,w,h=AFCore.Rect(id,'select','any')
        if N.Number(w) and N.Number(h) and w>0 and h>0 then g.selection={w/s,h/s} end
    else
        local native=S.native[group];if native.rect then capture(native.rect) end
        for _,path in ipairs(native.rects or {}) do capture(path) end
    end
    S.geometry[id]=g;return g end
local function dimensions(g)
    local settings,on=M.db[g.group],not S.restoring and S.active and S.hudOn and M:IsEnabled(g.group)
    return on and (settings.frameWidth or 100)/100 or 1,on and (settings.frameHeight or 100)/100 or 1 end
local function nodeRect(g,path,r,sx,sy)
    if g.group=='party' and (sx~=1 or sy~=1) then
        local flight=path:match('^(ctn_member%d+)/fly_gauge$')
        if flight then local row=g.nodes[flight];return row[1]*sx+math.max(0,200*sx-56),row[2]*sy+1,r[3],r[4] end
        local rowPath=path:match('^(ctn_member%d+)/#1$')
        if rowPath then
            local row=g.nodes[rowPath] -- Move only: preserve readable native voice input and 29x22 art.
            return row[1]*sx+200*sx+2,row[2]*sy+math.max(0,(row[4]*sy-22)/2),r[3],r[4] end
        return r[1]*sx,r[2]*sy,200*sx+2+29,r[4]*sy
    end
    return r[1]*sx,r[2]*sy,r[3]*sx,r[4]*sy end
function M:DialogSize(id,d)
    local g=S.geometry[id];if not g or g.token~=N.Identity(d) then return end;local sx,sy=dimensions(g)
    if sx==1 and sy==1 and not (g.held or g.partial) then return end
    if sx==1 and sy==1 then return g.w*N.Scale(),g.h*N.Scale() end
    local w,h=g.w*sx,g.h*sy;for path,r in pairs(g.nodes) do
        if not path:find('/',1,true) then
            local x,y,rw,rh=nodeRect(g,path,r,sx,sy);w=math.max(w,x+rw);h=math.max(h,y+rh)
        end end
    local s=N.Scale();return w*s,h*s end
-- MoveUI box: where Refresh draws the unit, relative to the dialog corner (party: the held dialog); nil keeps stock.
function M:MoverBounds(id,d)
    local g=S.geometry[id];if not d or not g or g.token~=N.Identity(d) or not M:IsEnabled(g.group) then return end
    if g.group=='party' then local w,h=self:DialogSize(id,d);if w then return 0,0,w,h end;return end
    local f,s=S.frames[g.group],N.Scale();local sx,sy=dimensions(g)
    local p=f and (f.native.rect or f.native.rects and f.native.rects[f.rectIndex or 1]);local r=p and g.nodes[p]
    if r then return r[1]*s*sx,r[2]*s*sy,r[3]*s*sx,r[4]*s*sy end;return 0,0,g.w*s*sx,g.h*s*sy end
function M:Hold(pinned,onlyGroup)
    for id,g in pairs(S.geometry) do
        local d=N.Dialog(id)
        if d and N.Identity(d)==g.token and (not onlyGroup or g.group==onlyGroup) then
            local sx,sy=dimensions(g);local resized=sx~=1 or sy~=1
            if resized or g.held or g.partial then
                local s=N.Scale();local stamp=tostring(s)..':'..tostring(sx)..':'..tostring(sy)
                local changed,complete=g.pinSignature~=stamp,true
                for _,path in ipairs(g.paths) do
                    local r,key=g.nodes[path],'uf:'..id..':'..path;local x,y,w,h=nodeRect(g,path,r,sx,sy)
                    if not changed or AFCore.Pin(id,x*s,y*s,0,false,w*s,h*s,path,'any') then
                        if resized then pinned[key]={id,path} else AFCore.Pin(id,nil,nil,nil,nil,nil,nil,path) end
                    else complete=false end
                end
                if g.selection then
                    local w,h=g.selection[1]*sx*s,g.selection[2]*sy*s
                    if not changed or AFCore.Pin(id,0,0,7,false,w,h,'select','any') then
                        if resized then pinned['uf:'..id..':select']={id,'select'} else AFCore.Pin(id,nil,nil,nil,nil,nil,nil,'select') end
                    else complete=false end
                end
                if not pinned[id] then
                    local w,h=self:DialogSize(id,d)
                    local ok=not changed and g.sizeOnly or AFCore.Pin(id,0,0,7,false,w,h);if ok and resized then pinned[id]=true elseif not ok then complete=false end
                    if not resized then AFCore.Pin(id) end
                    g.sizeOnly=resized and ok or nil
                else g.sizeOnly=nil end
                g.partial=resized and not complete;g.held,g.pinSignature=resized and complete,resized and complete and stamp or nil
                if g.group=='party' and changed and complete then g.auraPending=true end end
            -- One native aura layout after the batch; no unchanged-tick replay.
            if g.auraPending and S.partyAttached and AFCore.PartyFrames and AFCore.PartyFrames(true,true) then g.auraPending=nil end
        end end end
local function restoreGeometry(group) S.restoring=true;M:Hold({},group);S.restoring=nil end
local function rectangles(frame,x,y,width,height,value)
    local settings,s=frame.settings,N.Scale();x,y,width,height=x/s,y/s,width/s,height/s
    if frame.group=='party' then width=math.min(width,200*(settings.frameWidth or 100)/100) end
    local sy=(settings.frameHeight or 100)/100 -- Scale vertical geometry, retaining a readable header.
    local inset,header=5*sy,math.max(12,math.min(18*sy,height*.35))
    local gap,dp=settings.spacing*sy,frame.unit=='player' and settings.showDP==1 and math.min(settings.dpHeight*sy,height*.2) or 0
    local usable=math.max(4,height-header-inset-gap-(dp>0 and dp+gap or 0))
    local requested=(settings.healthHeight+settings.powerHeight)*sy
    local ratio=math.min(1,usable/requested);local hp,mp=settings.healthHeight*sy*ratio,settings.powerHeight*sy*ratio
    -- Square portrait once its body is ready: the player's own (player frame and own party row), target ID, or bot name.
    if S.me==nil then S.me=N.Call('UnitName','player') or false end -- once per refresh
    local source=(frame.unit=='player' or frame.group=='party' and value.name==S.me) and -1 or frame.group=='target' and value.id or value.name
    local portrait=frame.group~='targettarget' and settings.showPortrait==1 and not frame.portraitRefused
        and (frame.portraitStock or (frame.portraitReady~=nil and frame.portraitReady==source))
        and math.floor(math.min(height,width*.3)) or 0
    frame.portrait=portrait>0 and {x,y+(height-portrait)/2,portrait} or nil
    local shift=portrait>0 and portrait+gap or 0;local left,top,barwidth=x+inset+shift,y+header,math.max(1,width-inset*2-shift)
    frame.rect={x,y,width,height}
    -- The stock player view lies under the canvas: keep the backdrop beside it so it is never covered.
    frame.body=frame.unit=='player' and frame.portraitStock and portrait>0 and {x+portrait,y,math.max(1,width-portrait),height} or nil
    -- Bar widths are a share of the bar area, left-aligned; their text slots follow the bar.
    local function across(key) return math.max(4,barwidth*(settings[key] or 100)/100) end
    frame.bars={health={left,top,across('healthWidth'),hp},power={left,top+hp+gap,across('powerWidth'),mp},
        dp={left,top+hp+mp+gap*2,frame.unit=='player' and across('dpWidth') or barwidth,dp},header={left,y+1,barwidth,math.max(8,header-1)}}
    frame.reserve=frame.native.headerReserve or 32
end
local function render(frame,value)
    local id=frame.settings.font;if id==0 then id=M.db.ufFont end
    local face=FONTS[id] or FONTS[LEGACY_FONT[id] or 10]
    local a={font=face[1],fontPx=face[2],
        fixed=M.db.ufFixedColor,power=M.db.ufPowerColor,backdrop=M.db.ufBackdropColor,
        border=M.db.ufBorderColor,text=frame.settings.textMode=='custom' and frame.settings.textColor or M.db.ufTextColor,dead=M.db.ufDeadColor,offline=M.db.ufOfflineColor}
    frame.appearance=a
    -- Configure (fonts, text colours) only follows appearance; Update follows the dirty key.
    local look=S.rev..tostring(a.font)..tostring(a.text)
    local configure=frame.cfgKey~=look;frame.cfgKey=look
    for _,name in ipairs(S.order) do
        local element=S.elements[name]
        if configure and element.Configure then element.Configure(frame) end
        if element.Update then element.Update(frame) end
    end
end
local VALUE_KEYS={'id','name','level','hp','hpmax','mp','mpmax','class','classId','dead','unavailable','dp','dpmax','state','bot','rarity','rank','rankStars'}
-- Everything a paint depends on: the complete displayed values, native identity and container, effective
-- geometry/origin, scale and screen, the settings revision and each element's own live inputs.
local function signature(frame,value,identity,x,y,width,height,screen)
    local result={tostring(identity),tostring(frame.rectIndex),S.rev,screen,string.format('%.2f,%.2f,%.2f,%.2f',x,y,width,height),
        frame.origin and (frame.origin[1]..','..frame.origin[2]) or '-'}
    for _,key in ipairs(VALUE_KEYS) do result[#result+1]=tostring(value[key]) end
    for _,name in ipairs(S.order) do
        local key=S.elements[name].Key
        if key then result[#result+1]=key(frame) end
    end
    return table.concat(result,'\31')
end
local bind -- defined below, after the elements' helpers
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
    local targetShown,targetValue
    -- One snapshot per refresh (never kept across refreshes): scale, screen, tick, player name, party dialog.
    -- Read lazily, at the point the code first needs them, so the native call order is unchanged.
    local tick,scale,screen=AF:Call('Lifecycle','Ticks') or 0
    local function getScale() if not scale then scale=N.Scale() end return scale end
    local function getScreen() if not screen then screen={N.Screen()} end return screen end
    S.me=nil
    local party=N.Dialog(D.party)
    if S.partyCanvas and party and party.name=='party_dialog' and party.shown and M.db.party.enabled==1 then
        local token=N.Identity(party)
        if S.partyAttachToken~=token and settled then
            S.partyAttachToken=token
            S.partyAttached=AFCore.PartyFrames and AFCore.PartyFrames(true)
        end
        if not S.partyAttached then
            local s=getScale()
            K.Place(S.partyCanvas,party.x/s,party.y/s,party.w/s,party.h/s)
        end
    end
    for _,frame in ipairs(S.frames) do
        frame.settings=M.db[frame.group]
        local d
        if frame.group=='party' then d=party else d=N.Dialog(frame.id) end
        if frame.owned and not same(frame,d) then release(frame) end
        local x,y,width,height,shown
        if d and d.name==frame.native.layout then
            geometry(frame.id,d,frame.group)
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
        if frame.unit=='targettarget' and available then
            local id=targetShown and N.Number(targetValue and targetValue.id or AFCore.Unit('target'))
            available=id and id>0
        end
        local identity=d and N.Identity(d)
        local value=available and values(frame,d) or nil
        if frame.unit=='target' then
            targetShown=d and d.shown and d.name==frame.native.layout
            targetValue=value
        end
        -- A native frame that could not be taken over is retried when the game rebuilds it,
        -- or every five seconds, not on every refresh.
        local waiting=not frame.owned and frame.failedIdentity==identity and tick<(frame.retryAt or 0)
        local acquired=value and not waiting and (frame.owned or (settled and acquire(frame,d)))
        if value and settled and not frame.owned and not waiting and not acquired then frame.failedIdentity,frame.retryAt=identity,tick+100 end
        if acquired then
            -- Party rows are placed inside the party container; origin is that container's screen corner.
            frame.origin=frame.group=='party' and S.partyCanvas and {d.x,d.y} or nil
            -- Draw from the same immutable baseline used by Layout's native pins.
            local g=S.geometry[frame.id];local sx,sy=dimensions(g)
            local path=frame.native.rect or (frame.native.rects and frame.native.rects[frame.rectIndex or 1])
            local r=path and g.nodes[path];local scale=getScale()
            if r then x,y,width,height=d.x+r[1]*scale*sx,d.y+r[2]*scale*sy,r[3]*scale*sx,r[4]*scale*sy
            else width,height=g.w*scale*sx,g.h*scale*sy end
            if frame.group=='party' and S.partyCanvas then x,y=x-d.x,y-d.y end
            -- Native maintenance runs every refresh: the game re-shows captured widgets on its own updates.
            local forced=frame.nativeDirty
            suppress(frame);park(frame,d);frame.values=value
            -- Painting only when something it depends on changed (or an event/lifecycle marked it dirty).
            local stamp=signature(frame,value,identity,x,y,width,height,table.concat(getScreen(),'x')..'@'..tostring(scale))
            if forced or frame.valueSignature~=stamp then
                frame.valueSignature=stamp;S.paints=S.paints+1
                rectangles(frame,x,y,width,height,value);render(frame,value)
            end
            any=true
        else release(frame) end
    end
    if any then
        local sw,sh=unpack(getScreen());local s=getScale()
        K.Place(canvas,0,0,sw/s,sh/s);K.Show(canvas)
        local dump=S.dump or function() end
        if not S.dumped then
            S.dumpAt=S.dumpAt or tick+40
            if tick>=S.dumpAt then S.dumped=true;pcall(dump) end
        end
        -- A second report the first time a target is drawn: its size and container.
        local target=S.frames.target
        if target and target.owned and not S.dumpedTarget then S.dumpedTarget=true;pcall(dump,'afui-frames-target.cfg') end
        -- Once per session: the target portrait's outcome after it showed or failed three attempts.
        if target and target.owned and not S.dumpedPortrait and (target.portraitHeld or (target.portraitTries or 0)>=3) then
            S.dumpedPortrait=true;pcall(dump,'afui-frames-portrait.cfg')
        end
        local party=S.frames.party1
        if party and party.owned and not S.dumpedParty then S.dumpedParty=true;pcall(dump,'afui-frames-party.cfg') end
    else K.Hide(canvas) end
end
local function stop()
    if S.timer then AF.Events:Cancel(S.timer);S.timer=nil end
    if S.pending then AF.Events:Cancel(S.pending);S.pending=nil end
    S.lastRefreshTick=nil
    for _,frame in ipairs(S.frames) do release(frame) end
    restoreGeometry()
    if S.partyAttached then AFCore.PartyFrames(false) end
    S.partyAttachToken,S.partyAttached=nil,nil
    AF:Send('MarkPinsDirty')
    K.Hide(N.Widget('H','Frames'))
end
local function timedRefresh()
    local tick=AF:Call('Lifecycle','Ticks');if S.lastRefreshTick~=tick then S.lastRefreshTick=tick;M:Refresh() end end
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
        for _,frame in ipairs(S.frames) do frame.failedIdentity,frame.retryAt,frame.classCache=nil,nil,nil end
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
-- Normalized size id (0 stays Shared); the options select shows this, saved values are never rewritten.
function M:FontId(id) if id==0 then return 0 end return FONTS[id] and id or LEGACY_FONT[id] or 10 end
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
    S.partyCanvas=N.Widget('HF','Party')
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
-- Once the plugin's XML exists (Lifecycle binds commands), bind and hide the pieces before the first draw.
M:Listen('BindCommands',function() bind() end)
-- Layout released every pin (interface off, character change): park again on the next refresh.
M:Listen('PinsReleased',function() for _,frame in ipairs(S.frames) do frame.parked=nil end
    for _,g in pairs(S.geometry) do g.pinSignature,g.sizeOnly=nil,nil end end)
-- Profile select/copy/import/reset and mover/placement apply settings without DB:Set: invalidate the paint key.
M:Listen('ApplySettings',function() S.rev=S.rev+1 end)
M:Listen('ReleaseLayoutPins',function() restoreGeometry() end,-10)
function M:OnEnable() S.active=true;schedule() end;function M:OnDisable() S.active=false;stop() end;function M:OnProfile() S.rev=S.rev+1;stop();schedule() end
function M:OnSetting(key)
    S.rev=S.rev+1 -- settings revision: every setting is part of the paint key
    for _,frame in ipairs(S.frames) do frame.failedIdentity,frame.retryAt=nil,nil end
    if S.partyAttached and M.db.party.enabled~=1 then
        restoreGeometry('party')
        AFCore.PartyFrames(false);S.partyAttachToken,S.partyAttached=nil,nil
    end
    if key=='ufEnabled' or key=='ufInterval' then schedule() end
    AF:Send('MarkPinsDirty')
    self:Refresh()
end
function M:Diagnostics()
    local frames={}
    for _,frame in ipairs(S.frames) do frames[frame.unit]={owned=frame.owned==true,rect=AF.Copy(frame.rect),values=AF.Copy(frame.values)} end
    return {active=S.active,timer=S.timer~=nil,paints=S.paints,frames=frames}
end
M:Listen('HudChanged',function(_,on) S.hudOn=on and true or false;if S.hudOn then schedule() else stop() end end)
M:Listen('CharacterChanged',function() stop();S.identities={};S.geometry={};for _,frame in ipairs(S.frames) do frame.failedIdentity,frame.retryAt=nil,nil end end,-5)
M:Listen('BackdropsCleared',function()
    if not S.active or M.db.ufEnabled~=1 then return end
    for _,frame in ipairs(S.frames) do
        if frame.owned and frame.native.backdrop then AFCore.Backdrop(frame.id,0,frame.native.backdrop,0) end
    end
end)
for _,event in ipairs(EVENTS) do M:On(event,queueRefresh,20) end
M:On('ADDON_EXIT',function() M:OnDisable() end,20)
