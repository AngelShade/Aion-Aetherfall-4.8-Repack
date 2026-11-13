-- Lua 5.1. The registry and implementation objects never leave this closure.
AF = {}
local modules, order, started, errors = {}, {}, false, {}
local unpack = unpack
local FAILURES = 3
local function pack(...) return {n=select('#', ...), ...} end
local function copy(value)
    if type(value) ~= 'table' then return value end
    local result = {}; for k,v in pairs(value) do result[k]=copy(v) end; return result
end
AF.Copy = copy
function AF:NewModule(name, spec)
    assert(not started, 'module registered after startup: '..name)
    assert(not modules[name], 'duplicate module: '..name)
    spec = spec or {}
    local m = {name=name, spec=spec}
    modules[name] = {implementation=m, spec=spec}
    order[#order+1] = name
    if self.DB then m.db=self.DB:Declare(name, spec) end
    function m:On(event, fn, priority) AF.Events:On(name,event,fn,priority) end
    function m:Listen(message, fn, priority) AF.Events:Listen(name,message,fn,priority) end
    function m:Every(ticks, fn) return AF.Events:Every(name,ticks,fn) end
    function m:After(ticks, fn) return AF.Events:After(name,ticks,fn) end
    function m:Phase(phase, fn, priority) AF.Events:Phase(name,phase,fn,priority) end
    return m
end
function AF:IsFailed(name) return modules[name] and modules[name].failed or false end
function AF:Error(name) return modules[name] and modules[name].error end
-- Chat scrolls away: the session's errors are also kept in Mods/AetherfallUI/afui-errors.cfg.
local function record(self, line)
    errors[#errors+1]=line:sub(1,300)
    if #errors>40 then table.remove(errors,1) end
    if self.DB and self.DB.Write then pcall(self.DB.Write,self.DB,'afui-errors.cfg',table.concat(errors,'\n')..'\n') end
end
function AF:Call(name, method, ...)
    local entry=assert(modules[name], 'unknown module: '..tostring(name))
    if entry.failed then return end
    local m=entry.implementation
    local f=type(method)=='function' and method or m[method]
    if not f then return end
    local result=pack(pcall(f,m,...))
    if not result[1] then
        -- A native window being rebuilt can fail one tick and work the next, so a
        -- module is switched off only after FAILURES errors in a row. Each distinct
        -- message is reported once.
        local message=tostring(result[2])
        entry.error,entry.failures=message,(entry.failures or 0)+1
        if entry.reported~=message then
            entry.reported=message
            g_AddonName='RelicCalc'
            DisplayChatMessage(0,'Aetherfall '..name..': '..message,.91,.63,.56)
            record(self,name..': '..message)
        end
        if entry.failures>=FAILURES then
            entry.failed=true
            record(self,name..' switched off after '..FAILURES..' errors in a row')
            if self.Events then self.Events:Remove(name) end
            -- ARCHITECTURE.md: OnDisable also runs when the module was switched off. Nothing
            -- reaches it again, so this is its one chance to take down what it drew.
            g_AddonName='RelicCalc'
            if type(m.OnDisable)=='function' then pcall(m.OnDisable,m) end
        end
        return
    end
    entry.failures=nil
    return unpack(result,2,result.n)
end
function AF:GetModule(name)
    assert(modules[name], 'unknown module: '..tostring(name))
    -- Only callable methods are exported. Feature state is private to its owner.
    return setmetatable({}, {__index=function(_,method)
        assert(type(modules[name].implementation[method])=='function',name..'.'..method..' is not a method')
        return function(_,...) return AF:Call(name,method,...) end
    end, __newindex=function() error('module interfaces are read-only') end})
end
function AF:Names() return copy(order) end
function AF:Spec(name) return copy(assert(modules[name]).spec) end
function AF:Start()
    if started then return end
    local visiting,done,sorted={},{},{}
    local function visit(name)
        assert(modules[name], 'missing dependency: '..tostring(name))
        assert(not visiting[name], 'module dependency cycle at '..name)
        if done[name] then return end
        visiting[name]=true
        for _,dependency in ipairs(modules[name].spec.needs or {}) do visit(dependency) end
        visiting[name]=nil; done[name]=true; sorted[#sorted+1]=name
    end
    for _,name in ipairs(order) do visit(name) end
    order=sorted; started=true
    for _,name in ipairs(order) do self:Call(name,'OnInitialize') end
end
function AF:Enable()
    for _,name in ipairs(order) do self:Call(name,'OnEnable') end
end
function AF:Disable()
    for i=#order,1,-1 do self:Call(order[i],'OnDisable') end
end
function AF:Send(message, ...) return self.Events:Send(message,...) end
