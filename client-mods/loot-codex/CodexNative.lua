-- Native children of the M map. HTTP and widget ownership stay in AetherfallCore.
AFCodex={query={view='items',page=0},history={},sequence=0,expanded=false,draft={},menuPage=0}
local C=AFCodex
C.browseState={};C.section='items'
local function api(...) return AetherfallCore('codex',...) end
local function L(value) return AFTranslate and AFTranslate(value) or value end
local function write(name,value) api('text',name,tostring(value or '')) end
local function text(name,value) write(name,L(value or '')) end
local function show(name,value) api('show',name,value and 1 or 0) end
local function rect(name,x,y,w,h) api('rect',name,x,y,w,h) end
local function copy(t) local out={};for k,v in pairs(t) do out[k]=v end;return out end
local function human(s) return tostring(s or ''):lower():gsub('_',' '):gsub('%f[%a]%a',string.upper) end
local function url(s) return tostring(s):gsub('[^%w%-_%.~]',function(c)return string.format('%%%02X',c:byte())end) end
local function list(...) local out={{'','Any'}};for _,v in ipairs({...})do out[#out+1]={v,human(v)}end;return out end
local category={{'','All categories'},{'Weapons','Weapons'},{'Armour','Armour'},{'Accessories','Accessories'},{'Materials','Materials'},{'Recipes','Recipes'},{'Enhancement','Enhancement'},{'Quest','Quest'},{'Other','Other'}}
local grade={{'','Any quality'},{'COMMON','Common'},{'RARE','Superior'},{'LEGEND','Heroic'},{'UNIQUE','Fabled'},{'EPIC','Eternal'},{'MYTHIC','Mythic'}}
local function quality(value) for _,entry in ipairs(grade)do if entry[1]==value then return entry[2]end end;return human(value)end
local itemRace={{'','Any faction'},{'ELYOS','Elyos'},{'ASMODIANS','Asmodian'}}
local source={{'','Any source'},{'npc','Creature loot'},{'rule','World loot pools'},{'vendor','Merchants'},{'quest','Quests'},{'recipe','Craft / morph'},{'item','Containers'},{'event','Active events'}}
local order={{'name','Name: A to Z'},{'level_asc','Level: low to high'},{'level_desc','Level: high to low'}}
local rating=list('JUNK','NORMAL','ELITE','HERO','LEGENDARY')
local rank=list('NOVICE','DISCIPLINED','SEASONED','EXPERT','VETERAN','MASTER')
local npcRace=list('ELYOS','ASMODIANS','BEAST','UNDEAD','DEMIHUMANOID','CONSTRUCT','DRAKAN','NAGA','DRAGON','SHULACK','KRALL','BROWNIE','LYCAN','ELEMENTAL','MAGICALMONSTER','GOBLIN','ORC','RATMAN','LIZARDMAN','OUTSIDER','DRAGONET','NPC')
local names={GUN='Pistol',CANNON='Aethercannon',KEYBLADE='Cipher-blade',HEAD='Headwear',TORSO='Chest',GLOVE='Gloves',PANTS='Legs',SHOULDER='Shoulders',SHOES='Boots',WING='Wings'}
local function choices(tag)
    local mobs=C.query.view=='npcs'
    if tag=='category' then return mobs and rating or category end
    if tag=='grade' then return mobs and rank or grade end
    if tag=='race' then return mobs and npcRace or itemRace end
    if tag=='source' then return source end
    if tag=='sort' then return order end
    local result={{'','Any equipment type'}}
    for _,g in ipairs(C.groups or {})do
        if not C.draft.category or C.draft.category=='' or C.draft.category==g.category then result[#result+1]={g.value,names[g.value] or human(g.value)}end
    end
    return result
end
local function key(tag)
    if C.query.view=='npcs' then if tag=='category'then return 'rating' elseif tag=='grade'then return 'rank'end end
    return tag
end
local function caption(tag)
    local value=C.draft[key(tag)] or (tag=='sort' and 'name' or '')
    for _,v in ipairs(choices(tag))do if v[1]==value then return v[2]end end
    return human(value)
end
local function dismiss() C.menu=nil;show('menu',false);show('dismiss',false) end
local function labels()
    local mobs=C.query.view=='npcs'
    text('category_label',mobs and 'Rating' or 'Category');text('grade_label',mobs and 'Rank' or 'Quality')
    text('group_label',mobs and 'Area name or map ID' or 'Type');text('race_label',mobs and 'Creature race' or 'Faction')
    text('level_label','Level range  (blank = any)');text('source_label','Obtained from');text('sort_label','Order')
    for _,name in ipairs({'category','grade','group','race','source','sort'})do text(name,caption(name))end
    show('area',mobs);show('group',not mobs);show('source',not mobs);show('source_label',not mobs)
    text('filters_toggle',C.expanded and 'Filters  -' or 'Filters  +')
end
local function layout()
    local y=C.expanded and 326 or 129
    show('filters',C.expanded);rect('heading',8,y,144,20);rect('view_action',156,y-3,106,24)
    rect('notice',8,y+24,254,C.expanded and 26 or 50)
    for i=1,7 do rect('row_'..i,8,(C.expanded and 384 or 210)+(i-1)*56,254,54)end
end
local function fetch()
    C.sequence=C.sequence+1;C.busy=true;dismiss()
    local q=copy(C.query);q.native=1;q.pageSize=C.expanded and 4 or 7
    local fields={};for k,v in pairs(q)do if v~=nil and v~=''then fields[#fields+1]=url(k)..'='..url(v)end end
    if api('request',C.sequence,table.concat(fields,'&'))==false then
        C.busy=false;text('count','Search is too long.');return
    end
    text('count','Searching...')
end
local function snapshot()
    return {query=copy(C.query),draft=copy(C.draft),expanded=C.expanded,search=api('read','search')or '',low=api('read','level_min')or '',high=api('read','level_max')or '',area=api('read','area')or ''}
end
local function remember_browse()
    if C.query.view=='items'or C.query.view=='npcs'then C.browseState[C.query.view]=snapshot()end
end
local filters
local function go(q,remember)
    remember_browse()
    api('clear')
    if remember~=false then C.history[#C.history+1]=snapshot()end
    C.query=q;C.query.page=q.page or 0;C.selected=nil;C.expanded=false
    if q.view=='items'or q.view=='npcs'then C.section=q.view end
    write('search',q.q or '');filters();layout();fetch()
end
filters=function(keep)
    if not keep or not C.draft.view then C.draft=copy(C.query)end;labels()
    write('level_min',C.draft.minLevel or '');write('level_max',C.draft.maxLevel or '');write('area',C.draft.area or '')
end
local function restore(saved)
    remember_browse();api('clear');C.query=copy(saved.query);C.draft=copy(saved.draft);C.expanded=saved.expanded;C.selected=nil
    C.section=C.query.view=='npcs'and 'npcs'or C.query.view=='items'and 'items'or C.section
    write('search',saved.search);filters(true);write('level_min',saved.low);write('level_max',saved.high);write('area',saved.area);layout();fetch()
end
local function menu()
    local options=choices(C.menu);local from=C.menuPage*12
    for i=1,12 do local value=options[from+i];show('choice_'..i,value~=nil);if value then text('choice_'..i,value[2])end end
    show('menu_previous',C.menuPage>0);show('menu_next',from+12<#options)
    text('menu_page',(C.menuPage+1)..' / '..math.max(1,math.ceil(#options/12)));show('dismiss',true);show('menu',true)
end
local function quantity(row)
    if row.min==nil then return ''end
    return string.format(L('Qty %s'),tostring(row.min)..(row.max~=row.min and ('-'..row.max)or ''))
end
local function recipeTitle(value)
    local id=value:match('^Recipe (%d+)$')
    if id then return string.format(L('Recipe %s'),id) end
    local action, recipe=value:match('^(%a+) recipe (%d+)$')
    if action=='Crafting' or action=='Morphing' then
        return string.format(L(action=='Crafting' and 'Crafting recipe %s' or 'Morphing recipe %s'),recipe)
    end
    return value
end
local function spawnCondition(value, nested)
    local prefix, rest=value:match('^([^;]+); (.+)$')
    if not nested and (prefix=='Creature source' or prefix=='Merchant') then
        return string.format(L('%s; %s'),L(prefix),spawnCondition(rest,true))
    end
    local source, health=value:match('^Summoned by (.+) at (%d+)%% HP$')
    if source then return string.format(L('Summoned by %s at %s%% HP'),source,health) end
    local format
    source=value:match('^Summoned during the encounter with (.+)$')
    if source then format='Summoned during the encounter with %s' end
    if not source then
        source=value:match('^During the encounter with (.+)$')
        format='During the encounter with %s'
    end
    if not source then
        source=value:match('^After defeating (.+)$')
        format='After defeating %s'
    end
    if source then
        if source:match('^%s*$') then
            local caption=L('During the encounter')
            return caption~='During the encounter' and caption or value
        end
        return string.format(L(format),source)
    end
    local id, quest=value:match('^Quest (%d+): (.+)$')
    if id then return string.format(L('Quest %s: %s'),id,quest) end
    return L(value)
end
local function detail(value, nested)
    local translated=L(value)
    if translated~=value then return translated end
    local level, source=value:match('^Lv (%d+) | (.+)$')
    if level and (source=='boss loot' or source=='world drop') then
        return string.format(L('Lv %d'),tonumber(level))..' | '..L(source)
    end
    local id, name, step=value:match('^Quest (%d+): (.*); active (%a+) step required$')
    if step=='collecting' or step=='collection' then
        return string.format(L(step=='collecting' and 'Quest %s: %s; active collecting step required'
            or 'Quest %s: %s; active collection step required'),id,name)
    end
    local skill, proficiency, faction=value:match('^Skill (%d+); proficiency (%d+); (.+)$')
    if skill then return string.format(L('Skill %s; proficiency %s; %s'),skill,proficiency,L(faction)) end
    id, faction=value:match('^Quest (%d+); (.+)%. Prerequisites and reward choices apply%.$')
    if id then return string.format(L('Quest %s; %s. Prerequisites and reward choices apply.'),id,L(faction)) end
    local action, suffix=value:match('^(%a+); (.+)$')
    if action=='Crafting' or action=='Morphing' then
        local critical
        skill, proficiency, faction, critical=suffix:match('^skill (%d+), proficiency (%d+); ([^;]+)(.*)$')
        if skill and (critical=='' or critical=='; critical result') then
            return string.format(L('%s; skill %s, proficiency %s; %s'),L(action),skill,proficiency,L(faction))
                ..L(critical)
        end
    end
    local prefix, rest=value:match('^([^;]+); (.+)$')
    if not nested and (prefix=='Named loot' or prefix=='Active event' or prefix=='Creature source' or prefix=='Merchant') then
        return string.format(L('%s; %s'),L(prefix),detail(rest,true))
    elseif prefix=='Quest reward' or prefix=='Choice of quest reward' then
        return string.format(L('%s; %s'),L(prefix),L(rest))
    end
    local group, limit, terms=value:match('^Loot group: (.*); up to (%d+) items; (.+)$')
    if group then
        local result=string.format(L('Loot group: %s; up to %s items'),group=='NPC loot' and L(group) or group,limit)
        for term in terms:gmatch('[^;]+') do result=string.format(L('%s; %s'),result,L(term:match('^%s*(.*)$'))) end
        return result
    end
    local pool, maximum, conditions=value:match('^([^;]+); rule roll, then weighted selection of up to (%d+) items(.*)$')
    if pool=='World drop pool' or pool=='Named loot pool' then
        local result=string.format(L('%s; rule roll, then weighted selection of up to %s items'),L(pool),maximum)
        for condition in conditions:gmatch('; ([^;]+)') do
            local maps=condition:match('^maps: (.+)$')
            result=string.format(L('%s; %s'),result,maps and string.format(L('maps: %s'),maps) or L(condition))
        end
        return result
    end
    return value
end
local function render()
    local data=C.data;layout();if not data then return end
    api('rows')
    local view=data.view;local browse=view=='items'or view=='npcs'
    show('filters_toggle',browse);show('reset',browse);show('back',#C.history>0)
    show('world_drops',view=='item'or view=='npc'or view=='item_locations');text('world_drops',data.includeWorld and 'World drops: shown' or 'World drops: hidden')
    show('market_return',C.marketLinked);text('market_return','Market Codex');rect('count',8,608,C.marketLinked and 146 or 254,18)
    if view=='item_locations' and data.item and data.title==data.item.name..' — source locations' then
        write('heading',string.format(L('%s — source locations'),data.item.name))
    elseif view=='recipe' then
        write('heading',recipeTitle(data.title))
    elseif data.item or data.npc or view=='locations' or view=='quest' or view=='rule' then
        write('heading',data.title)
    else
        text('heading',data.title)
    end
    local notice='Choose an item to view its sources.'
    if view=='npcs' then notice='Select a creature for its stats and loot.'
    elseif view=='locations'or view=='item_locations'then notice=data.notice or 'Select a spawn area to centre on it. Up to 48 points are marked.\nSpawn areas are not live sightings.'
    elseif data.item then
        notice=L(quality(data.item.quality))..' | '..string.format(L('Lv %d'),data.item.level)..' | '..L(data.item.race)
        local stats={};for i,m in ipairs(data.modifiers or {})do if i<=4 then stats[#stats+1]=L(human(m.name))..' '..m.value end end
        if #stats>0 then notice=notice..'\n'..table.concat(stats,' | ')end
        if data.sources~=nil then notice=notice..'\n'..string.format(L(data.sources==1 and '%d specific source' or '%d specific sources'),data.sources)end
    elseif data.npc then
        notice=string.format(L('Lv %d'),data.npc.level)..' | '..L(human(data.npc.rating))..' | '..L(human(data.npc.rank))
        local s=data.stats or {};notice=notice..'\n'..string.format(L('HP %s | Attack %s | Defence %s'),tostring(s.HP or '?'),tostring(s.Attack or '?'),tostring(s['Physical defence']or '?'))
    elseif data.notice then notice=data.notice end
    write('notice',detail(notice));show('view_action',view=='npc'or view=='locations'or view=='item'or view=='item_locations');text('view_action',view=='npc'and 'Spawn areas'or view=='locations'and 'Creature info'or view=='item'and 'Source map'or 'Item info')
    local limit=C.expanded and 4 or 7
    for i=1,7 do
        local row=data.rows[i];local visible=row~=nil and i<=limit;show('row_'..i,visible)
        if visible then
            if row.kind=='item' then api('hover','row_'..i,row.id)end
            local image=row.kind=='item'and WardrobeIcons and WardrobeIcons[row.id]
            -- The maintained catalogue selects _64 only when the original
            -- 40 px base also exists. These 32 px rows use that native base.
            if image then api('icon','icon_'..i,image:gsub('_64$',''),40,row.id)else show('icon_'..i,false)end
            local x,w=image and 44 or 8,image and 204 or 240
            local sourceButton=view=='items'and row.sources~=nil
            show('sources_'..i,sourceButton)
            rect('name_'..i,x,3,w,25)
            rect('detail_'..i,x,28,sourceButton and 112 or w,13)
            rect('rate_'..i,x,41,sourceButton and 112 or w,12)
            rect('sources_'..i,164,31,84,20)
            local name=row.name
            if row.kind=='recipe' then name=recipeTitle(name) end
            if row.kind=='spawn_hint' and name=='Location chosen during gameplay' then name=L(name) end
            write('name_'..i,(C.selected==i and '> 'or '')..name)
            local description=row.detail and detail(row.detail) or (row.kind=='item'and (string.format(L('Lv %d'),row.level)..' | '..L(quality(row.quality)))or row.kind=='npc'and (string.format(L('Lv %d'),row.level)..' | '..L(human(row.rating)))or '')
            if row.kind=='spawn_hint' and row.condition then description=spawnCondition(row.detail or row.condition) end
            if row.kind=='location'then description=string.format('X %.0f, Y %.0f, Z %.0f',row.x,row.y,row.z)end
            write('detail_'..i,description)
            local rate=quantity(row)
            if row.chance and row.chance>=0 then rate=string.format(row.chance<1 and '%.3f' or '%.2f',row.chance):gsub('%.?0+$','')..'% '..L(row.rateKind or(row.weight and 'pool roll'or 'roll threshold'))..' | '..rate end
            if sourceButton then
                text('sources_'..i,row.sources==0 and row.worldDrop and 'World drop' or string.format(L(row.sources==1 and '%d Source' or '%d Sources'),row.sources))
                rate=row.worldDrop and 'World loot too' or ''
            elseif row.kind=='location'then rate=row.condition and spawnCondition(row.condition) or detail(row.detail or '')
            elseif row.kind=='spawn_hint'then rate=row.positionNote or 'No fixed map point'
            elseif row.methods then rate=string.format(L('%d loot entries'),row.methods)end
            text('rate_'..i,rate)
        end
    end
    if #data.rows==0 then text('notice',data.emptyNotice or(view=='item_locations'and 'No creature or merchant location is indexed.\nInspect the item for other sources.'or view=='locations'and 'No location is indexed for this creature.\nThis does not mean it never spawns.'or view=='item'and 'No specific source recorded.\nEnable world drops to check general loot pools.'or view=='npc'and 'No creature-specific loot recorded.\nEnable world drops to check general loot pools.'or 'No matches. Try different filters or search text.'))end
    text('count',string.format(L('%d entries'),data.total));text('page',(data.page+1)..' / '..data.pages)
    show('previous',data.page>0);show('next',data.page+1<data.pages);C.query.page=data.page
end
local function apply()
    local low,high=api('read','level_min')or '',api('read','level_max')or ''
    local min,max=low~=''and tonumber(low)or 0,high~=''and tonumber(high)or 255
    if not min or not max or min<0 or min>max or max>255 or min%1~=0 or max%1~=0 then text('count','Enter a valid level range (0-255).');return end
    C.query=copy(C.draft);C.query.minLevel=min;C.query.maxLevel=max;C.query.area=C.query.view=='npcs'and api('read','area')or nil
    C.query.q=api('read','search')or '';C.query.page=0;C.expanded=false;layout();fetch()
end
function C.Click(name)
    if name=='market_return'then remember_browse();local saved=snapshot();if api('market')then C.open=false;C.suspended=saved end;return end
    if name=='items'or name=='npcs'then remember_browse();local saved=C.browseState[name];if saved then C.history[#C.history+1]=snapshot();restore(saved)else go({view=name,page=0})end;return end
    if name=='search_go'then local q=copy(C.query.view==C.section and C.query or(C.browseState[C.section]and C.browseState[C.section].query or{view=C.section}));q.q=api('read','search')or '';q.page=0;go(q);return end
    if name=='back'then if #C.history>0 then restore(table.remove(C.history))end;return end
    if name=='previous'or name=='next'then if C.data then C.query.page=math.max(0,math.min(C.data.pages-1,(C.data.page or 0)+(name=='next'and 1 or -1)));fetch()end;return end
    if name=='filters_toggle'then C.draft.minLevel=api('read','level_min')or '';C.draft.maxLevel=api('read','level_max')or '';C.draft.area=api('read','area')or '';C.expanded=not C.expanded;filters(true);layout();C.query.page=0;fetch();return end
    if name=='reset'then C.query={view=C.query.view=='npcs'and 'npcs'or 'items',page=0};text('search','');filters();fetch();return end
    if name=='apply'then apply();return end
    if name=='world_drops'then C.query.world=C.data and C.data.includeWorld and 0 or 1;C.query.page=0;fetch();return end
    if name=='dismiss'then dismiss();return end
    if name=='menu_previous'or name=='menu_next'then C.menuPage=math.max(0,C.menuPage+(name=='menu_next'and 1 or -1));menu();return end
    local choice=tonumber(name:match('^choice_(%d+)$'))
    if choice and C.menu then local value=choices(C.menu)[C.menuPage*12+choice];if value then C.draft[key(C.menu)]=value[1];if C.menu=='category'and C.query.view=='items'then C.draft.group=''end end;dismiss();labels();return end
    for _,tag in ipairs({'category','grade','group','race','source','sort'})do
        if name==tag then C.menu=tag;C.menuPage=0;local y=tag=='category'or tag=='grade';y=y and 164 or (tag=='group'or tag=='race')and 207 or 294;rect('menu',8,y,254,298);menu();return end
    end
    if name=='view_action'and C.data then local views={npc='locations',locations='npc',item='item_locations',item_locations='item'};go({view=views[C.data.view],id=C.data.id,world=C.query.world});return end
    local sourceIndex=tonumber(name:match('^sources_(%d+)$'))
    if sourceIndex and C.data then local row=C.data.rows[sourceIndex];if row then go({view='item',id=row.id,world=row.sources==0 and row.worldDrop and 1 or 0})end;return end
    local i=tonumber(name:match('^row_(%d+)$'));local row=i and C.data and C.data.rows[i];if not row then return end
    if row.kind=='location'then C.selected=i;api('locate',row.map,row.x,row.y,row.z);render()
    elseif row.kind=='npc'or row.kind=='vendor'then go({view=C.data.view=='items'and 'npc'or C.data.view=='npcs'and 'npc'or 'locations',id=row.id,auto=C.data.view~='npcs'and 1 or nil})
    elseif row.kind=='rule'then go({view='rule',id=row.id,item=C.data.item and C.data.item.id or C.query.item})
    elseif row.kind~='event'and row.kind~='spawn_hint'then go({view=row.kind,id=row.id})end
end
function C.OnEvent(event)
    if event~='ADDON_TIMER'or not AetherfallCore then return end
    local open,action,sequence,body,scale,market=api('poll');C.marketLinked=market;if not open then C.open=false;return end
    if scale~=C.scale then C.scale=scale;render()end
    if not C.open then
        C.open=true;for name,label in pairs({brand='AETHERFALL  |  CODEX',back='Back',items='Items',npcs='Creatures',search_go='Search',reset='Reset filters',apply='Apply filters',level_dash='to',previous='Previous',next='Next',menu_previous='Previous',menu_next='Next'})do text(name,label)end
        filters(true);layout()
        if C.suspended then local s=C.suspended;write('search',s.search);write('level_min',s.low);write('level_max',s.high);write('area',s.area);C.suspended=nil end
    end
    if action then
        local kind,id,world=action:match('^market:(%d+):(%d+):(%d+)$')
        local target=action:match('^open:(%d+)$')
        if kind then C.section=kind=='1'and 'npcs'or 'items';go({view=kind=='1'and 'locations'or 'item_locations',id=tonumber(id),world=tonumber(world),auto=1})
        elseif action=='clear-markers'then C.selected=nil;render()
        elseif target then
            if tonumber(target)>0 then C.history={};go({view='target',target=tonumber(target)},false)else fetch()end
        else C.Click(action)end
    end
    if body and sequence==C.sequence then
        C.busy=false;local ok,data=pcall(WardrobeJSON.decode,body)
        if not ok or type(data)~='table' or data.error then
            local message=ok and type(data)=='table' and data.error
            text('notice',type(message)=='string' and message or 'The Codex response could not be read.')
            text('count','Use Search to retry.');return
        end
        C.data=data;C.groups=data.filterGroups or C.groups
        if C.query.view=='target'then C.query={view=data.view,id=data.id,page=0,world=C.query.world}end
        render()
        -- Mark every recorded spawn on the map at once; selecting a row still centres on it.
        if (data.view=='locations'or data.view=='item_locations')and data.points then
            local marks={};for _,p in ipairs(data.points)do marks[#marks+1]=string.format('%d,%.1f,%.1f,%.1f',p[1],p[2],p[3],p[4])end
            api('marks',table.concat(marks,';'))
        end
        if C.query.auto then C.query.auto=nil;for i,row in ipairs(data.rows)do if row.kind=='location'then C.Click('row_'..i);break end end end
    end
end
