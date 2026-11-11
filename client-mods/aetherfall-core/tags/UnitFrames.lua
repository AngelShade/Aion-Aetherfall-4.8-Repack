-- Unit-frame tags consume the caller's verified snapshot, never a second unit lookup.
local function number(value) return AF.Tags:Number(value,true) end
for _,resource in ipairs({'hp','mp'}) do
    local key=resource
    local function valid(context) return AF.Native.Number(context[key]),AF.Native.Number(context[key..'max']) end
    AF.Tags:Register(key..':current',function(context) local current=valid(context);return number(current) end)
    AF.Tags:Register(key..':max',function(context) local _,maximum=valid(context);return number(maximum) end)
    AF.Tags:Register(key..':percent',function(context)
        local current,maximum=valid(context)
        return current and maximum and maximum>0 and (math.floor(math.max(0,math.min(100,current/maximum*100))+.5)..'%') or ''
    end)
    AF.Tags:Register(key..':current-max',function(context)
        local current,maximum=valid(context)
        return current and maximum and maximum>0 and (number(current)..' / '..number(maximum)) or ''
    end)
    AF.Tags:Register(key..':deficit',function(context)
        local current,maximum=valid(context)
        return current and maximum and maximum>0 and number(math.max(0,maximum-current)) or ''
    end)
end
-- DP is shown exactly, like the stock readout.
local function dpValues(context) return AF.Native.Number(context.dp),AF.Native.Number(context.dpmax) end
AF.Tags:Register('dp:current',function(context) local c=dpValues(context);return AF.Tags:Number(c,false) end)
AF.Tags:Register('dp:max',function(context) local _,m=dpValues(context);return AF.Tags:Number(m,false) end)
AF.Tags:Register('dp:percent',function(context) local c,m=dpValues(context);return c and m and m>0 and (math.floor(math.max(0,math.min(100,c/m*100))+.5)..'%') or '' end)
AF.Tags:Register('dp:current-max',function(context) local c,m=dpValues(context);return c and m and m>0 and (AF.Tags:Number(c,false)..' / '..AF.Tags:Number(m,false)) or '' end)
AF.Tags:Register('dp:deficit',function(context) local c,m=dpValues(context);return c and m and m>0 and AF.Tags:Number(math.max(0,m-c),false) or '' end)
AF.Tags:Register('name:short',function(context)
    local name=tostring(context.name or '')
    local starts,limit={},context.nameLimit or 24
    for at in name:gmatch('()[%z\1-\127\194-\244]') do
        starts[#starts+1]=at
    end
    return #starts>limit and (name:sub(1,starts[math.max(1,limit-2)]-1)..'...') or name
end)
AF.Tags:Register('class',function(context) return context.class or '' end)
AF.Tags:Register('state',function(context) return context.state or '' end)
AF.Tags:Register('bot',function(context) return context.bot or '' end)
AF.Tags:Register('bot:rarity',function(context) return context.rarity or '' end)
AF.Tags:Register('bot:equip',function(context) return context.rarity and ('Equip: '..context.rarity) or '' end)
AF.Tags:Register('bot:rank',function(context) return context.rankStars or '' end)
