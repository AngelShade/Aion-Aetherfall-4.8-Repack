-- Native value/geometry conversions. No feature policies or live pointers.
local N={}
AF.Native=N
local resources={}
function N.Call(name,...)
    local f=_G[name]; if type(f)~='function' then return nil end
    local ok,a,b,c=pcall(f,...); if ok then return a,b,c end
end
function N.Number(v)
    v=tonumber(v); if v and v==v and math.abs(v)<1e15 then return v end
end
function N.Scale() return math.max(.5,math.min(2,N.Number(N.Call('GetUIScale')) or 1)) end
function N.Screen()
    local _,sw,sh=AFCore.Scale()
    return N.Number(sw) or 1280*N.Scale(), N.Number(sh) or 960*N.Scale()
end
function N.Widget(prefix,name) return _G['AF'..prefix..name] end
function N.Dialog(id)
    local name,x,y,w,h,shown,class,instance,flags=AFCore.Dialog(id)
    if not name then return end
    return {name=name,x=x,y=y,w=w,h=h,shown=shown,class=class,instance=instance,flags=flags}
end
function N.Movable(d) return d and d.flags and math.floor(d.flags/8)%2==1 end
function N.Identity(d) return d and (d.name..':'..tostring(d.instance or d.class or '')) end
function N.PlayerIdentity()
    local name=N.Call('UnitName','player')
    if type(name)~='string' or name=='' then return end
    return tostring(N.Call('GetServerName') or '')..':'..name
end
function N:SetResources(design,menu,lazy) resources={design=design,menu=menu,lazy=lazy} end
function N:Resource(name) return resources[name] end
function N.Panel(name)
    for id=0x20e,0x221 do
        local d=N.Dialog(id)
        if d and d.name=='AF'..'UI'..name then return id,d end
    end
end
function N.ShowPanel(name,on)
    if on then N.Widget('UI',name):Show() else N.Widget('UI',name):Hide() end
end
function N.BesideOwner(owner,child,sw)
    local x=owner.x+owner.w
    if x+child.w>sw then x=owner.x-child.w end
    return math.max(0,x),owner.y
end
local layoutRegistry,layoutCache
function N.Layout(name,id)
    if layoutRegistry~=AFCore.UI or not layoutCache then layoutRegistry,layoutCache=AFCore.UI,{} end
    local instance
    if id then local a,b,c,d,e,f,g,value=AFCore.Dialog(id); instance=value end
    local token=instance and tostring(instance) or 'closed'
    if layoutCache[name] and layoutCache[name].token==token then return layoutCache[name].layout end
    local chosen,rank
    for _,record in ipairs(AFCore.Layouts(name)) do
        local source=record.definition.source
        local score=(source:find('L10N/',1,true)==1 and 4 or 0)+(source:find('game_hud_s1/',1,true) and 2 or 0)
        if #record.definition.widgets>0 then
            if instance then
                for _,node in ipairs(record.definition.widgets) do
                    local expected=node.attributes.name
                    if expected and expected~='' then
                        local live=AFCore.Widget(id,node.path)
                        score=score+(live and live.name==expected and 10 or -100)
                    end
                end
            end
            if not rank or score>rank then chosen,rank=record.definition,score end
        end
    end
    if not chosen then return end
    local result={definition=chosen,paths={},names={}}
    for _,node in ipairs(chosen.widgets) do
        local bounds={}; for value in (node.attributes.frame or ''):gmatch('[^,]+') do bounds[#bounds+1]=tonumber(value) end
        local row={path=node.path,parent=node.parent,kind=node.attributes.type,rect=bounds,attributes=node.attributes}
        result.paths[node.path]=row
        local name=node.attributes.name
        if name and name~='' then
            result.names[name]=result.names[name] or {}; local rows=result.names[name]; rows[#rows+1]=row
        end
    end
    layoutCache[name]={token=token,layout=result}; return result
end
function N.LayoutWidget(layout,name)
    local rows=layout and layout.names[name]
    return rows and #rows==1 and rows[1] or nil
end
