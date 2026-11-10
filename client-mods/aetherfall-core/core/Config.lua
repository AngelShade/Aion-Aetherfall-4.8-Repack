-- An ordered option-tree registry. No window creation and no feature policy.
local C={}
AF.Config=C
local tabs,ordered={},{}
function C:Register(module,trees)
    local owner=type(module)=='table' and module.name or module
    for _,tree in ipairs(trees) do
        local tab=tabs[tree.name]
        if not tab then tab={tree.name,{},tree.hint,owner=tree.status and owner,order=tree.order}; tabs[tree.name]=tab; ordered[#ordered+1]=tab end
        if tree.hint then tab[3]=tree.hint end
        if tree.code then tab.code=true end
        if tree.status then tab.owner=owner; tab.status=tree.status end
        for _,row in ipairs(tree.rows or {}) do
            local spec=AF.Copy(row); spec.owner=owner
            tab[2][#tab[2]+1]={row.label,spec}
        end
        table.sort(tab[2],function(a,b) return a[2].order<b[2].order end)
    end
    table.sort(ordered,function(a,b) return a.order<b.order end)
end
function C:Trees() return AF.Copy(ordered) end
function C:Key(spec)
    return spec.base and spec.base..AF.DB:GetLegacy(spec.selector) or spec.k
end
function C:Get(spec) return AF.DB:GetLegacy(self:Key(spec)) end
function C:Choices(spec) return AF.DB:Rule(spec.owner,self:Key(spec))[3] end
function C:Status(tab) if tab.owner then return AF:Call(tab.owner,tab.status) end end
function C:Display(spec)
    if spec.format then return AF:Call(spec.owner,spec.format,spec) end
    local value=self:Get(spec)
    if spec.kind=='toggle' then return value==1 and 'On' or 'Off' end
    if spec.kind=='number' or spec.kind=='barnumber' then return tostring(value)..(spec.unit or '') end
    if spec.kind=='choice' then return self:Choices(spec)[value] end
    if spec.kind=='data' then return (spec.labels or {})[value] or value end
    if spec.kind=='profile' then return value end
    return ''
end
function C:Change(spec,part)
    local direction=part=='Less' and -1 or 1
    if spec.change then return AF:Call(spec.owner,spec.change,direction) end
    local key=self:Key(spec); local value=self:Get(spec)
    local rule=AF.DB:Rule(spec.owner,key)
    if spec.kind=='toggle' then value=1-value
    elseif spec.kind=='number' or spec.kind=='barnumber' then value=math.max(rule[3],math.min(rule[4],value+direction*rule[5]))
    elseif spec.kind=='choice' then value=(value-1+direction)%#rule[3]+1
    elseif spec.kind=='data' or spec.kind=='profile' then
        local result=rule[3][1]
        for i,v in ipairs(rule[3]) do if v==value then result=rule[3][(i-1+direction)%#rule[3]+1]; break end end
        value=result
    end
    return AF.DB:Set(spec.owner,key,value)
end
function C:Action(spec,confirming) return AF:Call(spec.owner,'OnAction',spec.action,spec.arg,confirming) end
