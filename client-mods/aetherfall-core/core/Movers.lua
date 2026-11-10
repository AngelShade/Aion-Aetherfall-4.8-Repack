-- Feature-owned mover adapters. The registry knows no frame IDs or UI widgets.
local R={}
AF.Movers=R
local registrations,providers={},{}
function R:Register(id,spec)
    assert(not registrations[id], 'duplicate mover registration: '..tostring(id))
    assert(spec.owner,'mover must name its owner')
    registrations[id]=AF.Copy(spec)
    if spec.candidates then
        providers[#providers+1]={id=id,order=assert(spec.order,'candidate provider needs explicit order')}
        table.sort(providers,function(a,b) return a.order<b.order end)
    end
end
local function specFor(id)
    if registrations[id] then return registrations[id] end
    for _,p in ipairs(providers) do
        local s=registrations[p.id]
        if s.first and id>=s.first and id<=s.last then return s end
    end
end
function R:Candidates()
    local list={}
    for _,p in ipairs(providers) do
        local s=registrations[p.id]
        for _,candidate in ipairs(AF:Call(s.owner,s.candidates) or {}) do list[#list+1]=candidate end
    end
    return list
end
function R:Build(emit,sw,sh)
    for _,p in ipairs(providers) do
        local s=registrations[p.id]
        AF:Call(s.owner,s.candidates,emit,sw,sh)
    end
end
function R:Invoke(id,method,...)
    local s=specFor(id); if not s or not s[method] then return end
    return AF:Call(s.owner,s[method],...)
end
function R:Category(mv)
    local s=specFor(mv.id)
    return s and (s.category or (s.classify and AF:Call(s.owner,s.classify,mv))) or 'Windows'
end
function R:SettingsTab(mv)
    local s=specFor(mv.id)
    return s and (s.tab or (s.settings and AF:Call(s.owner,s.settings,mv))) or 'Movers'
end
function R:Key(id) return AF.DB:ProfileName()..'.'..id end
function R:Get(id,d)
    if d and d.name=='' then return end
    local k=self:Key(id); local name=AF.DB:GetBinding(k)
    if d and name and name~=d.name then return end
    return AF.DB:GetPosition(k)
end
function R:Remember(id,d,x,y,sw,sh)
    if d and d.name=='' then return end
    local k=self:Key(id)
    AF.DB:SetPosition(k,{x/sw,y/sh})
    if d and d.name:match('^[%w_]+$') then AF.DB:SetBinding(k,d.name) end
    AF.DB:Dirty()
end
function R:Forget(id)
    local k=self:Key(id); AF.DB:SetPosition(k,nil); AF.DB:SetBinding(k,nil)
end
function R:GetKey(key) return AF.DB:GetPosition(key) end
function R:SetKey(key,p) AF.DB:SetPosition(key,p) end
function R:GetBinding(key) return AF.DB:GetBinding(key) end
function R:SetBinding(key,value) AF.DB:SetBinding(key,value) end
function R:Positions() return AF.DB:Positions() end
function R:Diagnostics() return AF.DB:Diagnostics().positions end

function R:SnapRelated(a,b)
    local sa,sb=specFor(a),specFor(b)
    return sa and sb and sa==sb and sa.group and (a==sa.group or b==sa.group) or false
end
