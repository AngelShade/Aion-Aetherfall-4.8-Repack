-- The AFUI1 wire format is deliberately retained, including its historical
-- settings iteration order. Schema declaration order must not change save bytes.
require('sha1')
local DB={}
AF.DB=DB
local schemas,owners,defaults,legacy,tagged={},{},{},{},{}
local values,positions,bindings,windows,stored={},{},{},{},{}
local ticks,sequence,saveAt,filename,path,saveError,draft,committing=0,0
local profileKey
local wire={'abrow1','warn','camdist','camfov','minimapinfo','botbarmode','extrabar','botbar','profile','ttshow','dr3','mapy',
 'targettimers','dr2','mapsize','xpbar','absize2','dc3','dc2','dc1','databar','snapframes','sepbar','dl3','chatalpha','dl1',
 'absize1','snap','menucols','ttui','timers','guidesize','showgrid','abrow4','menusize','ttanchor','grid','ttgrow','party',
 'barsalpha','chatline','opacitydirect','dl2','theme','abrow2','startmenu','dr1','mapx','tipcursor','absize3','ttclamp','menugap',
 'abrow3','abbar','windowsalpha','tty','tot','ttx','absize4','font','altbar'}
local wireKnown={}; for _,key in ipairs(wire) do wireKnown[key]=true end
function DB:Declare(owner,spec)
    schemas[owner]=spec
    local names={}
    for old,key in pairs(spec.legacy or {}) do names[key]=old end
    for key,rule in pairs(spec.defaults or {}) do
        local old=names[key] or key
        assert(not owners[old], 'duplicate persisted setting: '..old)
        owners[old]={owner,key}; defaults[old]=rule[1]; legacy[owner..'.'..key]=old
        if not wireKnown[old] then wireKnown[old]=true; wire[#wire+1]=old end
        if spec.tags and spec.tags[key] then tagged[old]=spec.tags[key] end
    end
    return setmetatable({}, {__index=function(_,key)
        local old=assert(legacy[owner..'.'..key],owner..' does not own '..key)
        local v=values[old]; if v==nil then v=defaults[old] end; return v
    end,__newindex=function() error('settings must be changed through DB:Set') end})
end
function DB:Validate(old,v)
    local owner=owners[old]; if not owner then return end
    local r=schemas[owner[1]].defaults[owner[2]]; local kind=r[2]
    if kind=='int' or kind=='number' then
        local x=tonumber(v)
        if x and x>=r[3] and x<=r[4] and (kind=='number' or x==math.floor(x)) then return x end
    elseif kind=='choice' then
        local x=tonumber(v); if x and x==math.floor(x) and x>=1 and x<=#r[3] then return x end
    elseif kind=='enum' then
        for _,x in ipairs(r[3]) do if x==v then return v end end
    elseif kind=='toggle' then
        if v=='0' or v=='1' or v==0 or v==1 then return tonumber(v) end
    elseif kind=='bool' then
        if v==true or v=='true' then return true elseif v==false or v=='false' then return false end
    end
end
function DB:GetLegacy(key) local v=values[key]; if v==nil then v=defaults[key] end; return v end
function DB:Owner(key) local owner=owners[key]; if owner then return owner[1],owner[2] end end
function DB:Rule(owner,key) return AF.Copy(schemas[owner].defaults[key]) end
function DB:Set(owner,key,value,quiet)
    if type(owner)=='table' then owner=owner.name end
    local old=assert(legacy[owner..'.'..key],owner..' does not own '..key)
    local validated=self:Validate(old,value)
    assert(validated~=nil,'invalid setting: '..old)
    if not quiet and AF:Call(owner,'BeforeSetting',key,validated)==false then return false end
    values[old]=validated; self:Dirty()
    if not quiet then AF:Call(owner,'OnSetting',key,validated); AF:Send('SettingChanged',old,owner,key,validated) end
    return true
end
function DB:Clock(value) ticks=value end
function DB:Dirty() saveAt=ticks+20 end
function DB:SaveAt() return saveAt end
function DB:SetSaveAt(value) saveAt=value end
function DB:Error() return saveError end
function DB:Read(name) return AFCore.ReadFile and AFCore.ReadFile(name) end
function DB:ReadLayoutState()
    local s=self:Read('state.json')
    if not s and io and io.open then
        local f=io.open(AFCore.ModPath('state.json'),'r')
        if f then s=f:read('*a'); f:close() end
    end
    return s
end
function DB:Write(name,bytes) return AFCore.SaveFile and AFCore.SaveFile(name,bytes) end
local function readFile(name)
    local s=AFCore.ReadFile(name)
    if not s or #s>32768 or not s:match('\nEND\n$') then return end
    local seq=tonumber(s:match('^AFUI1 (%d+)\n')); if not seq then return end
    return seq,s
end
function DB:Reset() values,positions,stored,bindings,windows={},{},{},{},{} end
function DB:Migrate(settings)
    local changed=false
    for _,owner in ipairs(AF:Names()) do
        local spec=schemas[owner]
        if spec and spec.migrate then changed=spec.migrate(settings) or changed end
    end
    return changed
end
function DB:Load(identity)
    self:Reset()
    filename='ui-'..sha1.sha1((assert(identity,'character is not connected')))
    path=AFCore.ModPath(filename)
    local best,body=0,nil
    for slot=0,1 do local seq,s=readFile(filename..'.'..slot..'.cfg'); if seq and seq>best then best,body=seq,s end end
    sequence=best
    if not body then
        for _,owner in ipairs(AF:Names()) do
            for key,value in pairs((schemas[owner] or {}).fresh or {}) do values[legacy[owner..'.'..key]]=value end
        end
        return
    end
    for k,v in body:gmatch('\n([%w%.]+)=([^\n]+)') do
        local binding=k:match('^bind%.(%w+%.%d+)$')
        local p,key=k:match('^(%w+)%.(%w+)$')
        if binding and v:match('^[%w_]+$') then bindings[binding]=v
        elseif p=='set' and (defaults[key]~=nil or key=='alpha') then
            values[key]=self:Validate(key=='alpha' and 'windowsalpha' or key,v)
        elseif p and key and tonumber(key) then
            local fx,fy=v:match('^([%-%d%.]+),([%-%d%.]+)$'); fx,fy=tonumber(fx),tonumber(fy)
            if fx and fy and fx>=-1 and fy>=-1 and fx<=1 and fy<=1 then positions[p..'.'..key]={fx,fy} end
        elseif p=='win' and key then
            local fx,fy=v:match('^([%-%d%.]+),([%-%d%.]+)$'); fx,fy=tonumber(fx),tonumber(fy)
            if fx and fy and fx>=0 and fy>=0 and fx<=1 and fy<=1 then windows[key]={fx,fy} end
        elseif p=='store' and key then stored[key]=v:sub(1,2048) end
    end
    if self:Migrate(values) then saveAt=ticks+20 end
end
function DB:Save()
    if draft and not committing then return true end
    if not path then saveError='Settings are not ready to save.'; return false end
    local seq=(sequence or 0)+1; local lines={'AFUI1 '..seq}
    for _,k in ipairs(wire) do if values[k]~=nil then lines[#lines+1]='set.'..k..'='..tostring(values[k]) end end
    for k,p in pairs(positions) do lines[#lines+1]=k..'='..string.format('%.5f,%.5f',p[1],p[2]) end
    for k,name in pairs(bindings) do if positions[k] then lines[#lines+1]='bind.'..k..'='..name end end
    for k,p in pairs(windows) do lines[#lines+1]='win.'..k..'='..string.format('%.5f,%.5f',p[1],p[2]) end
    for k,v in pairs(stored) do lines[#lines+1]='store.'..k..'='..v end
    local slot='.'..(seq%2)..'.cfg'
    local ok=pcall(function()
        assert(AFCore.SaveFile(filename..slot,table.concat(lines,'\n')..'\nEND\n'))
        assert(readFile(filename..slot)==seq)
    end)
    saveAt=ok and nil or ticks+100
    if ok then saveAt=nil; sequence=seq; saveError=nil
    else saveError='Could not save settings. Your layout is still open; try locking again.' end
    return ok
end
function DB:BeginDraft()
    draft={db=AF.Copy(values),positions=AF.Copy(positions),bindings=AF.Copy(bindings),stored=AF.Copy(stored),saveAt=saveAt}
end
function DB:HasDraft() return draft~=nil end
function DB:CommitDraft() committing=true; local ok=self:Save(); committing=nil; return ok end
function DB:CancelDraft()
    if not draft then return end
    values,positions,bindings,stored=draft.db,draft.positions,draft.bindings,draft.stored
    saveAt,saveError=draft.saveAt,nil
end
function DB:EndDraft() draft=nil end
function DB:ProfileKey(owner,key) profileKey=assert(legacy[owner..'.'..key]) end
-- Positions are stored per profile; without a profile owner there is one profile.
function DB:ProfileName() return profileKey and self:GetLegacy(profileKey) or 'DPS' end
function DB:GetPosition(key) return AF.Copy(positions[key]) end
function DB:SetPosition(key,p) positions[key]=AF.Copy(p) end
function DB:Positions()
    return function(_,key)
        local nextKey,value=next(positions,key)
        return nextKey,AF.Copy(value)
    end,nil,nil
end
function DB:GetBinding(key) return bindings[key] end
function DB:SetBinding(key,name) bindings[key]=name end
function DB:GetWindow(name) return AF.Copy(windows[name]) end
function DB:SetWindow(name,p) windows[name]=AF.Copy(p) end
function DB:GetStored(name) return stored[name] end
function DB:SetStored(name,value) stored[name]=value end
function DB:Settings() return ipairs(wire) end
function DB:Default(key) return defaults[key] end
function DB:Tag(key) return tagged[key] end
function DB:ProfileValue(key,value) values[key]=value end
function DB:Diagnostics()
    return {db=AF.Copy(values),positions=AF.Copy(positions),bindings=AF.Copy(bindings),windows=AF.Copy(windows),
            stored=AF.Copy(stored),file=filename,path=path,sequence=sequence,saveAt=saveAt,saveError=saveError}
end
