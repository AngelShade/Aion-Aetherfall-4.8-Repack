-- Aetherfall HUD for the installed Aion 4.8 Lua 5.1 runtime.
-- Native event contracts and intentional limits: docs/AETHERFALL_UI_ELVUI_MAP.md.
require('sha1')
Aetherfall = {clock=0, frames={}, settings={}, positions={}, text={}, ready=false, unlocked=false, tab='General'}
local A=Aetherfall
local ids={'Player','Target','Party1','Party2','Party3','Party4','Party5','Top','Bottom','Cast'}
local defaults={enabled=1,player=1,target=1,party=1,data=1,cast=1,percent=0,snap=1,profile='DPS',setup=0}
local profiles={DPS=true,Healer=true,Minimal=true}
local function w(id) return _G['AUI'..id] end
local function visible(id,on) if on then w(id):Show() else w(id):Hide() end end
local function text(id,s)
    s=tostring(s or '')
    if A.text[id]~=s then w(id):SetText(s);A.text[id]=s end
end
local function call(name,...)
    local f=_G[name];if type(f)~='function' then return nil end
    local ok,a,b,c=pcall(f,...);if ok then return a,b,c end
end
local function number(n) n=tonumber(n);if n and n==n and math.abs(n)<1000000000 then return n end end
local function scale() return math.max(.5,math.min(2,number(call('GetUIScale')) or 1)) end
local function viewport()
    local meta=PlayerBotBarMeta and PlayerBotBarMeta:GetText() or ''
    local x,y=meta:match('^(%d+)|(%d+)|')
    return math.max(640,tonumber(x) or 1280)/scale(),math.max(480,tonumber(y) or 960)/scale()
end
local function positionKey(id) return A.settings.profile..'.'..id end
local function clamp(id,x,y)
    local sw,sh=viewport();local f=A.frames[id]
    return math.max(0,math.min(x,sw-f[1])),math.max(0,math.min(y,sh-f[2]))
end
local function defaultPosition(id)
    local sw,sh=viewport()
    if id=='Player' then return sw/2-270,sh-270 end
    if id=='Target' then return sw/2+10,sh-270 end
    if id=='Top' then return sw/2-280,4 end
    if id=='Bottom' then return sw/2-280,sh-72 end
    if id=='Cast' then return sw/2-160,sh-165 end
    local i=tonumber(id:match('Party(%d)')) or 1
    if A.settings.profile=='Healer' then return sw/2-350+((i-1)%3)*234,sh-480+math.floor((i-1)/3)*96 end
    return 12,sh/2-240+(i-1)*96
end
local function dirty() A.saveAt=A.clock+1 end
-- SaveValue/LoadValue are social-setting whitelists, not addon persistence.
-- Store only validated scalar data. Never execute a settings file as Lua.
local function readSettings(path)
    if not io or not io.open then return end
    local f=io.open(path,'r');if not f then return end
    local s=f:read(16385);f:close()
    if not s or #s>16384 or not s:match('\nEND\n$') then return end
    local seq=tonumber(s:match('^AUI1 (%d+)\n'));if not seq then return end
    return seq,s
end
local function loadSettings()
    A.settings={};A.positions={};for k,v in pairs(defaults) do A.settings[k]=v end
    -- The client's bundled SHA-1 keeps Unicode identities within legacy path limits.
    local identity=tostring(call('GetServerName') or '')..':'..tostring(call('UnitName','player') or '')
    A.path='Mods/AetherfallUI/profile-'..sha1.sha1(identity)
    local best,body=0,nil
    for slot=0,1 do local seq,s=readSettings(A.path..'.'..slot..'.cfg');if seq and seq>best then best,body=seq,s end end
    A.sequence=best
    if body then
        for k,v in body:gmatch('\n([%w%.]+)=([^\n]+)') do
            if k=='profile' and profiles[v] then A.settings[k]=v
            elseif defaults[k]~=nil and (v=='0' or v=='1') then A.settings[k]=tonumber(v)
            else
                local p,id=k:match('^(%w+)%.(%w+)$');local x,y=v:match('^(%d+),(%d+)$')
                if profiles[p] and A.frames[id] and x and tonumber(x)<32768 and tonumber(y)<32768 then A.positions[k]={tonumber(x),tonumber(y)} end
            end
        end
    end
end
function Aetherfall_Save()
    if not A.path then return end
    local seq=(A.sequence or 0)+1;local lines={'AUI1 '..seq}
    for k in pairs(defaults) do lines[#lines+1]=k..'='..tostring(A.settings[k]) end
    for k,p in pairs(A.positions) do lines[#lines+1]=k..'='..math.floor(p[1])..','..math.floor(p[2]) end
    local path=A.path..'.'..seq%2 ..'.cfg'
    local ok,err=pcall(function()
        assert(io and io.open,'Settings storage unavailable')
        local f=assert(io.open(path,'w'));local written,why=f:write(table.concat(lines,'\n')..'\nEND\n');local closed=f:close()
        assert(written,why);assert(closed,'Settings close failed')
        local saved=readSettings(path);assert(saved==seq,'Settings verification failed')
    end)
    A.saveAt=nil
    if ok then A.sequence=seq;A.saveError=nil else A.saveError='Could not save layout. Check client folder write access.' end
    Aetherfall_OptionsRefresh()
end
function Aetherfall_Layout()
    for _,id in ipairs(ids) do
        local p=A.positions[positionKey(id)];local x,y
        if p then x,y=p[1],p[2] else x,y=defaultPosition(id) end
        x,y=clamp(id,x,y);local f=A.frames[id];w(id):SetRect(x,y,f[1],f[2])
        w(id):SetFlag(A.unlocked and 'title' or 'title;not_movable')
    end
    local sw,sh=viewport();w('Options'):SetRect(math.max(0,(sw-600)/2),math.max(0,(sh-400)/2),600,400)
    Aetherfall_Update()
end
local function capturePositions(snap)
    local changed=false;local s=scale()
    for _,id in ipairs(ids) do
        local x,y=w(id):GetRect();x,y=(number(x) or 0)/s,(number(y) or 0)/s
        if snap and A.settings.snap==1 then x,y=math.floor(x/8+.5)*8,math.floor(y/8+.5)*8 end
        x,y=clamp(id,x,y);x,y=math.floor(x+.5),math.floor(y+.5)
        local k=positionKey(id);local p=A.positions[k]
        if not p or p[1]~=x or p[2]~=y then A.positions[k]={x,y};changed=true end
    end
    if changed then dirty() end
end
function Aetherfall_Unlock()
    g_AddonName='RelicCalc'
    if not A.ready then return end
    if A.unlocked then capturePositions(true) end
    A.unlocked=not A.unlocked;Aetherfall_Layout();Aetherfall_OptionsRefresh()
end
local function gauge(id,value,maximum)
    value,maximum=number(value),number(maximum)
    w(id):SetMaxValue(math.max(1,maximum or 1));w(id):SetValue(math.max(0,math.min(value or 0,maximum or 0)))
    if not value or not maximum or maximum<=0 then return '--' end
    if A.settings.percent==1 then return string.format('%.0f%%',100*value/maximum) end
    return string.format('%.0f / %.0f',value,maximum)
end
local classColours={WARRIOR='0.84,0.72,0.47,1',GLADIATOR='0.84,0.72,0.47,1',TEMPLAR='0.84,0.72,0.47,1',SCOUT='0.56,0.85,0.71,1',ASSASSIN='0.56,0.85,0.71,1',RANGER='0.56,0.85,0.71,1',MAGE='0.56,0.89,0.93,1',SORCERER='0.56,0.89,0.93,1',SPIRIT_MASTER='0.56,0.89,0.93,1',PRIEST='0.96,0.91,0.78,1',CLERIC='0.96,0.91,0.78,1',CHANTER='0.96,0.91,0.78,1'}
local function unit(id,token,enabled)
    local name=call('UnitName',token);local exists=type(name)=='string' and name~=''
    visible(id,A.ready and A.settings.enabled==1 and enabled and (exists or A.unlocked))
    text(id,A.unlocked and ('Move '..id) or id)
    text(id..'Name',exists and (tostring(call('UnitLevel',token) or '?')..'  '..name) or (A.unlocked and id..' preview' or ''))
    local cls=call('UnitClass',token);w(id..'Name'):SetAttr('textColor',classColours[cls] or '0.875,0.914,0.941,1')
    local hp=gauge(id..'HP',call('UnitHP',token),call('UnitHPMax',token))
    local mp=gauge(id..'MP',call('UnitMP',token),call('UnitMPMax',token))
    local dead=call('UnitIsDead',token)
    text(id..'Health',(dead==true or dead==1) and 'Dead' or ('HP  '..hp));text(id..'Power','MP  '..mp)
    A.tokens[id]=exists and token or nil
end
function Aetherfall_Target(id)
    g_AddonName='RelicCalc'
    if not A.unlocked and A.tokens[id] then call('TargetUnit',A.tokens[id]) end
end
function Aetherfall_Update()
    if not A.ready then return end
    unit('Player','player',A.settings.player==1);unit('Target','target',A.settings.target==1)
    -- Party tokens include the player on this client; compact five other members.
    local slot=0;local own=call('UnitName','player')
    for i=1,6 do
        local token='party'..i;local name=call('UnitName',token)
        if type(name)=='string' and name~='' and name~=own and slot<5 then slot=slot+1;unit('Party'..slot,token,A.settings.party==1) end
    end
    for i=slot+1,5 do unit('Party'..i,'',A.settings.party==1) end
    local show=A.settings.enabled==1 and A.settings.data==1
    visible('Top',show);visible('Bottom',show)
    local x,y=call('GetPlayerMapPosition','player')
    local coord=number(x) and number(y) and string.format('%.1f, %.1f',x,y) or '--'
    text('TopText',tostring(call('GetZoneText') or 'Aetherfall')..'  |  '..coord..'\n'..tostring(call('GetTime') or ''):gsub('[\r\n]',''))
    local elapsed=A.combatStart and math.max(0,(A.combatEnd or A.clock)-A.combatStart) or 0
    text('BottomText','Party '..tostring(call('GetNumPartyMembers') or 0)..'  |  Legion '..tostring(call('GetNumLegionMembers') or 0)..'  |  Activity '..string.format('%.0fs',elapsed)..'\nDPS/HPS: unavailable')
    visible('Cast',A.settings.enabled==1 and A.settings.cast==1 and (A.cast~=nil or A.unlocked))
    text('Cast',A.unlocked and 'Move Cast' or 'Player cast')
    text('CastText',A.cast and (A.cast.name..'  '..string.format('%.1fs elapsed',A.clock-A.cast.start)) or 'Cast preview')
    -- No fake duration/percentage: the native cast event carries only unit/name.
    w('CastGauge'):SetMaxValue(1);w('CastGauge'):SetValue(A.cast and 1 or 0)
end
local tabs={'General','UnitFrames','DataTexts','Movers','Profiles'}
local controls={General={'Enabled','CastEnabled','Finish'},UnitFrames={'PlayerEnabled','TargetEnabled','PartyEnabled','Percent'},DataTexts={'DataEnabled','ResetCombat'},Movers={'Unlock','Snap','ResetPositions'},Profiles={'DPS','Healer','Minimal'}}
function Aetherfall_OptionsRefresh()
    if not A.settings.enabled then return end
    local labels={Enabled='Addon: '..(A.settings.enabled==1 and 'On' or 'Off'),CastEnabled='Player cast: '..(A.settings.cast==1 and 'On' or 'Off'),Finish='Save and close',PlayerEnabled='Player: '..(A.settings.player==1 and 'On' or 'Off'),TargetEnabled='Target: '..(A.settings.target==1 and 'On' or 'Off'),PartyEnabled='Party: '..(A.settings.party==1 and 'On' or 'Off'),Percent='Health text: '..(A.settings.percent==1 and 'Percent' or 'Values'),DataEnabled='Data panels: '..(A.settings.data==1 and 'On' or 'Off'),ResetCombat='Reset combat activity',Unlock=A.unlocked and 'Lock and save positions' or 'Unlock frames',Snap='Snap on lock: '..(A.settings.snap==1 and 'On' or 'Off'),ResetPositions='Reset this layout',DPS='DPS layout',Healer='Healer layout',Minimal='Minimal layout'}
    for tab,list in pairs(controls) do for _,id in ipairs(list) do visible(id,A.tab==tab);text(id,labels[id]) end end
    local hints={General='Welcome to Aetherfall. Choose a layout in Profiles, then unlock and drag\nthe title strips in Movers. /aui opens options; /moveui locks or unlocks.\nNative HUD remains available. Skin switching stays in the launcher.',UnitFrames='Player, target and five party members. Click a member name to target.\nHealth and mana update five times a second. Native auras remain visible.',DataTexts='Zone, map coordinates, local date/time, party and legion member counts.\nActivity ends after five seconds without observed damage.\nDamage events lack reliable source/healing attribution: DPS/HPS unavailable.',Movers='Drag a frame by its title strip. Lock to save; grid snap applies on lock.\nEach profile keeps its own positions. Reset repairs an off-screen layout.',Profiles='DPS: central player and target; party at the left.\nHealer: party near the centre. Minimal: player, target and cast only.\nEach character and server has separate saved settings.'}
    text('OptionsInfo',hints[A.tab]);text('OptionsStatus',A.saveError or ('Layout: '..A.settings.profile..(A.unlocked and '  |  Frames unlocked' or '  |  Frames locked')))
end
function Aetherfall_Click(id)
    g_AddonName='RelicCalc';if not A.ready then return end
    if id:sub(1,3)=='Tab' then A.tab=id:sub(4)
    elseif profiles[id] then
        if A.unlocked then capturePositions(true) end
        A.settings.profile=id;A.settings.party=id=='Minimal' and 0 or 1;A.settings.data=id=='Minimal' and 0 or 1
        A.unlocked=false;dirty();Aetherfall_Layout()
    elseif id=='Unlock' then Aetherfall_Unlock()
    elseif id=='ResetPositions' then for _,name in ipairs(ids) do A.positions[positionKey(name)]=nil end;dirty();Aetherfall_Layout()
    elseif id=='ResetCombat' then A.combatStart=nil;A.combatEnd=nil;A.lastDamage=nil
    elseif id=='Finish' then
        if A.unlocked then Aetherfall_Unlock() end
        A.settings.setup=1;Aetherfall_Save();if not A.saveError then w('Options'):Hide() end
    else
        local key=({Enabled='enabled',CastEnabled='cast',PlayerEnabled='player',TargetEnabled='target',PartyEnabled='party',DataEnabled='data',Percent='percent',Snap='snap'})[id]
        if key then A.settings[key]=1-A.settings[key];dirty() end
    end
    Aetherfall_OptionsRefresh();Aetherfall_Update()
end
function Aetherfall_Open()
    g_AddonName='RelicCalc';if not A.ready then return end
    if w('Options'):IsVisible()==1 then w('Options'):Hide() else w('Options'):Show();Aetherfall_OptionsRefresh() end
end
local function enter()
    local name=call('UnitName','player');if type(name)~='string' or name=='' then return end
    local identity=tostring(call('GetServerName') or '')..':'..name
    if A.identity~=identity then
        if A.ready then Aetherfall_Save() end
        A.identity=identity;loadSettings();A.unlocked=false;A.cast=nil;A.combatStart=nil;A.combatEnd=nil;A.lastDamage=nil
    end
    A.ready=true;Aetherfall_Layout();Aetherfall_OptionsRefresh()
    visible('Options',A.settings.setup==0)
end
function Aetherfall_OnEvent(event,...)
    g_AddonName='RelicCalc'
    if event=='PLAYER_ENTERING_WORLD' then enter();return end
    if event=='ADDON_EXIT' then
        if A.unlocked then capturePositions(true) end
        Aetherfall_Save();A.ready=false;return
    end
    if event=='ADDON_TIMER' then
        A.clock=A.clock+.05;A.ticks=A.ticks+1
        if not A.ready then if A.ticks%20==0 then enter() end;return end
        if A.lastDamage and A.clock-A.lastDamage>5 and not A.combatEnd then A.combatEnd=A.lastDamage end
        if A.cast and A.clock-A.cast.start>120 then A.cast=nil end
        if A.ticks%4==0 then Aetherfall_Update() end
        if A.ticks%20==0 then
            local name=call('UnitName','player')
            if type(name)~='string' or name=='' then
                if A.unlocked then capturePositions(true) end
                Aetherfall_Save();A.ready=false;A.cast=nil
                for _,id in ipairs(ids) do w(id):Hide() end
                w('Options'):Hide();return
            end
            if A.unlocked then capturePositions(false) end
            local sw,sh=viewport();local sz=sw..':'..sh
            if A.viewport~=sz then A.viewport=sz;Aetherfall_Layout() end
        end
        if A.saveAt and A.clock>=A.saveAt then Aetherfall_Save() end
        return
    end
    if not A.ready then return end
    local a,b=...
    if event=='SKILL_CAST' and a=='player' and type(b)=='string' then A.cast={name=b,start=A.clock}
    elseif (event=='SKILL_SUCCEED' or event=='SKILL_CANCEL') and a=='player' then A.cast=nil
    elseif event=='UNIT_ATTACK_DAMAGE' or event=='UNIT_SKILL_DAMAGE' then
        if number(b) and tonumber(b)>0 then
            if not A.combatStart or A.combatEnd then A.combatStart=A.clock;A.combatEnd=nil end
            A.lastDamage=A.clock
        end
    end
    Aetherfall_Update()
end
function Aetherfall_OnLoad()
    g_AddonName='RelicCalc';A.ticks=0;A.tokens={}
    for _,id in ipairs(ids) do
        A.frames[id]=id:match('Party') and {230,92} or (id=='Top' or id=='Bottom') and {560,64} or id=='Cast' and {320,70} or {260,98}
        w(id):SetText(id=='Top' and 'Aetherfall | World' or id=='Bottom' and 'Aetherfall | Activity' or id)
        w(id):SetAttr('textColor','0.957,0.910,0.784,1');w(id):Hide()
    end
    w('Options'):Hide()
    for _,event in ipairs({'PLAYER_ENTERING_WORLD','ADDON_TIMER','ADDON_EXIT','USER_CHANGED_TARGET','PARTY_MEMBERS_CHANGED','SKILL_CAST','SKILL_SUCCEED','SKILL_CANCEL','UNIT_ATTACK_DAMAGE','UNIT_SKILL_DAMAGE'}) do w('Options'):RegisterEvent(event) end
    SlashCmdList.AETHERFALL=Aetherfall_Open;SLASH_AETHERFALL1='/aui'
    SlashCmdList.AETHERFALLMOVE=Aetherfall_Unlock;SLASH_AETHERFALLMOVE1='/moveui'
    -- RelicCalc's Wardrobe owns the shared .05 second timer. Never reset it.
end
