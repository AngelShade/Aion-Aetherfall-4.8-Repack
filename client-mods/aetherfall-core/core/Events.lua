-- Consumers share Wardrobe's timer. This layer never starts a second chain.
local E={}
AF.Events=E
local events,messages,phases,timers={},{},{},{}
local tick,serial=0,0
E.phases={'native-drag','custom-windows','requests','editor','holds','fit-windows',
          'watch','discover','visible-settings','remember-shown','save'}
local known={}; for _,name in ipairs(E.phases) do known[name]=true end
local function add(registry,owner,key,fn,priority)
    local list=registry[key] or {}; registry[key]=list
    serial=serial+1; list[#list+1]={owner=owner,fn=fn,priority=priority or 0,serial=serial}
    table.sort(list,function(a,b) return a.priority==b.priority and a.serial<b.serial or a.priority<b.priority end)
end
local function dispatch(registry,key,...)
    local list=registry[key]; if not list then return end
    -- Removal during a protected callback does not skip the next subscriber.
    local current={}; for i,v in ipairs(list) do current[i]=v end
    for _,s in ipairs(current) do AF:Call(s.owner,s.fn,...) end
end
function E:On(owner,event,fn,priority) add(events,owner,event,fn,priority) end
function E:Listen(owner,message,fn,priority) add(messages,owner,message,fn,priority) end
function E:Phase(owner,phase,fn,priority)
    assert(known[phase], 'unknown timer phase: '..tostring(phase))
    add(phases,owner,phase,fn,priority)
end
function E:Client(event,...) dispatch(events,event,event,...) end
function E:Send(message,...) dispatch(messages,message,...) end
function E:RunPhase(phase,...) dispatch(phases,phase,...) end
function E:Every(owner,ticks,fn)
    assert(ticks>=1 and ticks==math.floor(ticks),'invalid interval')
    local t={owner=owner,at=tick+ticks,interval=ticks,fn=fn}; timers[#timers+1]=t; return t
end
function E:After(owner,ticks,fn)
    local t=self:Every(owner,ticks,fn); t.interval=nil; return t
end
function E:Cancel(token)
    for i=#timers,1,-1 do if timers[i]==token then table.remove(timers,i) end end
end
function E:Advance(value)
    tick=value
    if #timers==0 then return end
    local due={}
    for i=#timers,1,-1 do
        local t=timers[i]
        if tick>=t.at then
            due[#due+1]=t
            if t.interval then t.at=tick+t.interval else table.remove(timers,i) end
        end
    end
    for i=#due,1,-1 do AF:Call(due[i].owner,due[i].fn) end
end
function E:Remove(owner)
    for _,registry in ipairs({events,messages,phases}) do
        for key,list in pairs(registry) do
            for i=#list,1,-1 do if list[i].owner==owner then table.remove(list,i) end end
            if #list==0 then registry[key]=nil end
        end
    end
    for i=#timers,1,-1 do if timers[i].owner==owner then table.remove(timers,i) end end
end
