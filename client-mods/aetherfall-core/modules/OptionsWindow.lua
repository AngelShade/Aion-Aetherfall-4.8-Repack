-- A generic on-demand options browser. Feature knowledge belongs to providers.
local M = AF:NewModule('OptionsWindow', {needs={'Lifecycle','MoverEditor','CustomWindows'}})
local N, C, Skin = AF.Native, AF.Config, AF.Skin
local S = {page=nil,tab='General',offset=1,categoryOffset=1,tabOffset=1,built={},expanded={},query=''}
local function w(id) return N.Widget('UI','Options'..id) end
local function life(method,...) return AF:Call('Lifecycle',method,...) end
local cells = AF.Widgets.New(w)
local MAX_CELLS, MAX_CATEGORIES, MAX_TABS = 16,14,8
local function paint(id,x,y,width,height,text,style,selected)
    local widget=w(id); if not widget then return end
    Skin.Place(widget,x,y,width,height)
    if text~=nil then Skin.SetText(widget,text) end
    Skin.Show(widget)
    if style=='button' then Skin.Button(widget,width,height,selected)
    elseif style=='field' then Skin.Field(widget,width,height)
    elseif style=='panel' then Skin.Panel(widget,width,height)
    elseif style=='window' then Skin.Panel(widget,width,height,'Abyss')
    else Skin.Text(widget,style or 'Ice') end
end
local function selected()
    local pages=C:Pages(); local page
    for _, candidate in ipairs(pages) do if candidate.id==S.page then page=candidate; break end end
    page=page or pages[1]
    if not page then return pages end
    S.page=page.id
    local tab
    for _, candidate in ipairs(page.tabs) do if candidate.id==S.tab then tab=candidate; break end end
    tab=tab or page.tabs[1]
    S.tab=tab and tab.id
    return pages,page,tab
end
local function dimensions()
    local sw,sh=N.Screen(); local scale=N.Scale()
    S.width=math.max(440,math.min(1000,sw/scale-12))
    S.height=math.max(300,math.min(700,sh/scale-12))
    S.sidebar=S.width<760 and 148 or 190
    S.columns=S.width<760 and 2 or 4
    S.compact=S.height<450
    S.lift=(not S.compact and S.width>=900) and 32 or 0
    cells.compact=S.compact
end
local function chrome(page,tab)
    local width,height,side=S.width,S.height,S.sidebar
    paint('Backdrop',0,0,width,height,nil,'window')
    local paneY=(S.compact and 88 or 106)-S.lift
    local paneHeight=height-(S.compact and 138 or 154)+S.lift
    paint('Sidebar',10,paneY,side-8,paneHeight,nil,'panel')
    paint('Content',side+12,paneY,width-side-22,paneHeight,nil,'panel')
    Skin.Hide(w('Title'))
    S.title={10,4,width-20,S.compact and 23 or 34}
    paint('TitleDrag',S.title[1],S.title[2],S.title[3],S.title[4],'Aetherfall Interface   |   Drag to move','button')
    paint('Version',width-154,S.compact and 5 or 11,140,20,'Version: ' .. (S.version or ''), 'Aether')
    local actions={{'Anchors','Toggle anchors',120},{'ResetAnchors',S.resetConfirm and 'Confirm reset' or 'Reset anchors',120},{'Reposition','Reposition window',140},{'Install','Install',72}}
    local x=14
    for _, action in ipairs(actions) do
        local aw=S.width<600 and (action[1]=='Install' and 52 or 100) or action[3]
        paint(action[1],x,S.compact and 28 or 40,aw,24,action[2],'button'); x=x+aw+6
    end
    local inline = width >= 900
    local searchX = inline and x + 8 or 14
    local searchY = inline and 40 or (S.compact and 56 or 74)
    local searchWidth = width - searchX - 164
    paint('Search',searchX,searchY,searchWidth,24,nil,'field')
    paint('SearchApply',width-158,searchY,68,24,'Search','button')
    paint('SearchClear',width-84,searchY,70,24,'Clear','button')
    paint('Header',side+24,(S.compact and 92 or 114)-S.lift,width-side-48,24,page and page.name or 'Options','Aether')
    paint('Description',side+24,138-S.lift,width-side-48,20,page and page.desc or '', 'Mist')
    Skin.Hide(w('Path'))
    if S.compact then Skin.Hide(w('Description')) end
    local navY=height-(S.compact and 48 or 80)
    local bottomY=height-(S.compact and 24 or 38)
    if S.pageCount>1 then
        paint('Previous',side+24,navY,28,24,'<','button')
        paint('Next',side+58,navY,28,24,'>','button')
        paint('Page',side+94,navY+2,160,20,'Page '..S.offset..' / '..S.pageCount,'Mist')
    else
        Skin.Hide(w('Previous')); Skin.Hide(w('Next')); Skin.Hide(w('Page'))
    end
    paint('Close',width-94,bottomY,80,24,'Close','button')
    paint('Status',14,bottomY,width-120,24,AF.DB:Error() or S.note or (page and C:Status(page)) or 'Select a category. Changes apply immediately.', 'Mist')
end
local function categoryRows(pages)
    local groups,byName={},{}
    for _,page in ipairs(pages) do
        local category=page.category or page.name
        if not byName[category] then byName[category]={name=category,pages={}}; groups[#groups+1]=byName[category] end
        byName[category].pages[#byName[category].pages+1]=page
    end
    local rows={}
    for _,group in ipairs(groups) do
        if #group.pages==1 then
            -- A category with one page opens it directly; only real groups expand.
            rows[#rows+1]={page=group.pages[1].id,label=group.name}
        else
        rows[#rows+1]={category=group.name,label=(S.expanded[group.name] and '- ' or '+ ')..group.name}
        if S.expanded[group.name] then
            for _,page in ipairs(group.pages) do
                rows[#rows+1]={page=page.id,label=(page.parent and '    ' or '  ')..page.name}
            end
        end
        end
    end
    return rows
end
local function sidebar(pages)
    local rows=categoryRows(pages)
    local available=math.max(1,math.min(MAX_CATEGORIES,math.floor((S.height-(S.compact and 160 or 202)+S.lift)/(S.compact and 24 or 28))))
    local count=math.max(1,math.ceil(#rows/available))
    S.categoryOffset=math.max(1,math.min(count,S.categoryOffset))
    S.categories={}
    for i=1,MAX_CATEGORIES do
        local row=i<=available and rows[(S.categoryOffset-1)*available+i]
        if row then S.categories[i]=row; paint('Category'..i,18,(S.compact and 98 or 116)-S.lift+(i-1)*(S.compact and 24 or 28),S.sidebar-24,24,row.label,'button',row.page==S.page)
        else Skin.Hide(w('Category'..i)) end
    end
    if count>1 then
        paint('CategoryPrev',18,S.height-(S.compact and 48 or 80),28,24,'<','button')
        paint('CategoryNext',S.sidebar-38,S.height-(S.compact and 48 or 80),28,24,'>','button')
        paint('CategoryPage',52,S.height-(S.compact and 46 or 78),S.sidebar-96,20,S.categoryOffset..' / '..count,'Mist')
    else
        Skin.Hide(w('CategoryPrev')); Skin.Hide(w('CategoryNext')); Skin.Hide(w('CategoryPage'))
    end
    S.categoryPages=count
end
local function tabs(page)
    local offset=math.max(1,math.min(S.tabOffset,math.max(1,#page.tabs-MAX_TABS+1)))
    S.tabOffset,S.tabs=offset,{}
    local width=math.floor((S.width-S.sidebar-48)/math.min(MAX_TABS,math.max(1,#page.tabs)))
    for i=1,MAX_TABS do
        local tab=page.tabs[offset+i-1]
        if tab then S.tabs[i]=tab.id; paint('Tab'..i,S.sidebar+24+(i-1)*width,(S.compact and 116 or 162)-S.lift,width-4,24,tab.name,'button',tab.id==S.tab)
        else Skin.Hide(w('Tab'..i)) end
    end
end
local function chunks(tab)
    local pages={{cells={},groups={}}}; local current=pages[1]
    local x0,y0,width=S.sidebar+24,(S.compact and 140 or 196)-S.lift,S.width-S.sidebar-48
    local unit=(width-(S.columns-1)*10)/S.columns
    local maxY=S.height-(S.compact and 54 or 90)
    local groupHeader=S.compact and 20 or 26
    local y,used,rowHeight=0,0,0
    local function newPage()
        current={cells={},groups={}}; pages[#pages+1]=current
        y,used,rowHeight=0,0,0
    end
    for _,group in ipairs(tab and tab.groups or {}) do
        local options={}
        for _,option in ipairs(group.options or {}) do if not C:Hidden(option) then options[#options+1]=option end end
        if #options>0 then
            if used>0 then y=y+rowHeight+8; used,rowHeight=0,0 end
            if y0+y+84>maxY then newPage() end
            local box={name=group.name or '',x=x0-6,y=y0+y,width=width+12,height=groupHeader}
            current.groups[#current.groups+1]=box; y=y+groupHeader
            for _,option in ipairs(options) do
                local span=option.width=='full' and S.columns or option.width=='half' and S.columns/2 or option.width=='third' and S.columns/3 or math.min(S.columns,tonumber(option.width) or 1)
                if option.kind=='header' or option.kind=='description' or (S.compact and option.kind=='color' and cells.expanded[option.id]) then span=S.columns end
                span=math.max(S.columns/4,span)
                local height=cells:Height(option)
                if used+span>S.columns then y=y+rowHeight+8; used,rowHeight=0,0 end
                if y0+y+height>maxY or #current.cells>=MAX_CELLS then
                    box.height=math.max(28,y0+y-box.y)
                    newPage()
                    box={name=group.name or '',x=x0-6,y=y0,width=width+12,height=groupHeader}
                    current.groups[#current.groups+1]=box; y=groupHeader
                end
                current.cells[#current.cells+1]={option=option,x=x0+used*(unit+10),y=y0+y,width=unit*span+(span-1)*10}
                used,rowHeight=used+span,math.max(rowHeight,height)
                box.height=math.max(box.height,y0+y+rowHeight-box.y+6)
                if used>=S.columns then y=y+rowHeight+8; used,rowHeight=0,0 end
            end
            if used>0 then y=y+rowHeight+8; used,rowHeight=0,0 end
            box.height=math.max(28,y0+y-box.y); y=y+8
        end
    end
    return pages
end
function M:Refresh()
    if not S.open or not life('IsReady') or not w('') then return end
    local pages,page,tab=selected(); if not page then return end
    dimensions()
    local activeTab=tab
    if S.query~='' then
        local options={}
        for i,result in ipairs(C:Search(S.query)) do
            local target=result
            options[#options+1]={id='SearchResult'..i,label=result.path,desc=result.desc,kind='execute',text='Open page',owner=self.name,width='full',execute=function() M:Select(target.page,target.tab,target.option); M:Search('') end}
        end
        activeTab={groups={{name='Search results ('..#options..')',options=options}}}
    end
    local sheets=chunks(activeTab)
    S.pageCount=#sheets; S.offset=math.max(1,math.min(S.offset,#sheets))
    local sheet=sheets[S.offset]
    chrome(page,tab); sidebar(pages); tabs(page)
    S.cells={}
    for i=1,16 do
        local group=sheet.groups[i]
        if group then
            paint('Group'..i,group.x,group.y+8,group.width,group.height-8,nil,'panel')
            paint('Group'..i..'Caption',group.x+10,group.y-2,group.width-20,20,group.name,'Aether')
        else Skin.Hide(w('Group'..i)); Skin.Hide(w('Group'..i..'Caption')) end
        local cell=sheet.cells[i]
        if cell then
            S.cells[i]={id=cell.option.id,kind=cell.option.kind,owner=cell.option.owner}
            cells:Render(i,cell.option,cell.x,cell.y,cell.width)
        else cells:Hide(i) end
    end
    local code=page.code and S.query==''
    if code then paint('Code',S.sidebar+24,S.height-116,S.width-S.sidebar-48,24,S.code or '', 'field')
    else Skin.Hide(w('Code')) end
    cells:RenderDrop(S.height)
end
function M:RefreshFor(page,key,value)
    if page and (not S.open or (S.page~=page and S.tab~=page) or (key and AF.DB:GetLegacy(key)~=value)) then return end
    self:Refresh()
end
function M:SetCode(text)
    S.code=tostring(text or '')
    if w('Code') then w('Code'):SetText(S.code) end
end
function M:GetCode() return w('Code') and w('Code'):GetText() or S.code or '' end
function M:Select(page,tab,option)
    cells:CloseDrop()
    local found,foundTab,foundOption=C:Find(page)
    S.page,S.tab,S.offset=found or page,tab or foundTab or 'General',1
    S.query=''; if w('Search') and (w('Search'):GetText() or '')~='' then w('Search'):SetText('') end
    local _,selectedPage,selectedTab=selected()
    if selectedPage then S.expanded[selectedPage.category]=true end
    local target=option or foundOption
    if target and selectedTab then
        local sheets=chunks(selectedTab)
        for i,sheet in ipairs(sheets) do for _,cell in ipairs(sheet.cells) do if cell.option.id==target then S.offset=i end end end
    end
    cells:ResetConfirmation(); self:Refresh()
end
function M:Search(text)
    cells:CloseDrop()
    text=tostring(text or '')
    if text~='' and S.query=='' then S.previous={S.page,S.tab,S.offset} end
    if text=='' and S.query~='' and S.previous then
        S.page,S.tab,S.offset=unpack(S.previous); S.previous=nil
    elseif text~=S.query then S.offset=1 end
    S.query=text
    if w('Search') and (w('Search'):GetText() or '')~=text then w('Search'):SetText(text) end
    self:Refresh()
end
function M:Hide()
    self:EndDrag()
    cells:CloseDrop()
    if w('') then w(''):Hide() end
    S.open,S.drag=false,nil; cells:StopDrag(); cells:ResetConfirmation()
end
function M:Open(target,tab)
    g_AddonName='RelicCalc'
    if target == '' then target = nil end
    if not w('') then self:Request('open',target,tab); return end
    if AF:Call('MoverEditor','BlocksSettings') then return end
    if not life('IsReady') then life('Enter') end
    if not life('IsReady') then return end
    if S.open and not target then self:Hide(); return end
    dimensions()
    S.x,S.y=AF:Call('CustomWindows','Restore','AFUIOptions',S.width,S.height)
    S.x,S.y=S.x or 0,S.y or 0
    w(''):SetRect(S.x,S.y,S.width,S.height); w(''):Show()
    -- The client draws its own corner button on a plugin dialog (the up/down arrows beside the
    -- version); it does nothing here, so it is kept off the window.
    if not S.cornerMoved then S.cornerMoved=true; pcall(function() w(''):SetCloseOffset(4000,4000) end) end
    S.open=true; S.opened=(S.opened or 0)+1; S.wasDown=true
    S.inspectAt=life('Ticks')+10
    if target and target~='' then self:Select(target,tab) else
        local _,page=selected(); if page then S.expanded[page.category]=true end
        self:Refresh()
    end
end
-- Maps a mouse position (screen pixels) into the window's layout units. Plugin widgets
-- report their rectangle in interface units, so the dialog's real pixel rectangle comes
-- from the native dialog table; the game also parks and clamps windows on its own.
local function windowPoint(x,y)
    local d=S.dialogId and N.Dialog(S.dialogId)
    if not d or d.name~='AFUIOptions' then
        local id,found=N.Panel('Options'); S.dialogId,d=id,found
    end
    local sx,sy,rx,ry
    if d and d.w and d.h and d.w>0 and d.h>0 then rx,ry,sx,sy=d.x,d.y,d.w/S.width,d.h/S.height
    else sx=N.Scale(); sy=sx; rx,ry=S.x*sx,S.y*sy end
    return (x-rx)/sx,(y-ry)/sy,sx,sy
end
-- One bounded snapshot of the window's live children, written the first time it opens in a
-- session (Mods/AetherfallUI/afui-inspect.cfg), to identify anything the client itself draws.
local function inspect()
    if S.inspected or not AFCore.Children then return end
    S.inspected=true
    local id,d=N.Panel('Options'); if not id then return end
    local lines={'AFUI inspect '..tostring(S.version),string.format('dialog 0x%x %s x=%s y=%s w=%s h=%s flags=%s scale=%s',id,d.name,d.x,d.y,d.w,d.h,tostring(d.flags),tostring(N.Scale()))}
    local kinds,first,total={},1,0
    for _=1,60 do
        local ok,nodes,nextIndex=pcall(AFCore.Children,id,nil,first,64)
        if not ok or type(nodes)~='table' then break end
        for _,node in ipairs(nodes) do
            total=total+1
            kinds[tostring(node.type)]=(kinds[tostring(node.type)] or 0)+1
            local r=node.rect or {}
            local rx,ry=tonumber(r[1] or r.x),tonumber(r[2] or r.y)
            -- everything in the top band, plus anything that is not one of ours
            if #lines<150 and ((rx and ry and ry<d.y+60*(d.h/S.height)) or not tostring(node.name):match('^AFUIOptions')) then
                lines[#lines+1]=string.format('%s type=%s shown=%s rect=%s,%s,%s,%s',tostring(node.name),tostring(node.type),tostring(node.shown),tostring(rx),tostring(ry),tostring(r[3] or r.w),tostring(r[4] or r.h))
            end
        end
        if not nextIndex then break end
        first=nextIndex
    end
    local summary={}
    for kind,count in pairs(kinds) do summary[#summary+1]=kind..'='..count end
    table.sort(summary)
    lines[#lines+1]='children '..total..' types '..table.concat(summary,' ')
    AF.DB:Write('afui-inspect.cfg',table.concat(lines,'\n')..'\n')
end
function M:Click(id)
    g_AddonName='RelicCalc'
    id=tostring(id or '')
    S.clicks,S.lastClick=(S.clicks or 0)+1,tostring(id)
    if id=='Close' then self:Hide(); return end
    if not S.open or not life('IsReady') then return end
    if id:match('^Drop') then
        local result=cells:DropClick(id)
        S.note=type(result)=='table' and result.note or nil
        self:Refresh()
        return
    end
    if cells.drop and id~='Cell'..cells.drop.index..'Value' then cells:CloseDrop() end
    local confirming=S.resetConfirm; S.resetConfirm=nil; S.note=nil
    if not tostring(id):match('^Cell%d+Value$') then cells:ResetConfirmation() end
    if id=='Anchors' then self:Request('move'); return
    elseif id=='ResetAnchors' then
        if confirming then AF:Send('ResetAllPositions'); S.note='Profile anchors reset.' else S.resetConfirm=true; S.note='Click Confirm reset to reset this profile\'s anchors.' end
    elseif id=='Reposition' then
        S.x,S.y=AF:Call('CustomWindows','Center','AFUIOptions',S.width,S.height)
        w(''):SetRect(S.x or 0,S.y or 0,S.width,S.height)
    elseif id=='Install' then AF:Send('InstallInterface'); return
    elseif id=='SearchApply' then self:Search(w('Search'):GetText()); return
    elseif id=='SearchClear' then self:Search(''); return
    elseif id=='Previous' then S.offset=math.max(1,S.offset-1)
    elseif id=='Next' then S.offset=math.min(S.pageCount or 1,S.offset+1)
    elseif id=='CategoryPrev' then S.categoryOffset=math.max(1,S.categoryOffset-1)
    elseif id=='CategoryNext' then S.categoryOffset=math.min(S.categoryPages or 1,S.categoryOffset+1)
    else
        local index=tonumber(tostring(id):match('^Category(%d+)$'))
        if index and S.categories[index] then
            local row=S.categories[index]
            if row.page then self:Select(row.page); return end
            S.expanded[row.category]=not S.expanded[row.category]
        end
        index=tonumber(tostring(id):match('^Tab(%d+)$'))
        if index and S.tabs[index] then S.tab,S.offset=S.tabs[index],1; cells:ResetConfirmation() end
        local cell,part=tostring(id):match('^Cell(%d+)(%a+)$')
        if cell then
            local result
            if part:match('Track$') then
                local x,y=AFCore.Cursor()
                result=cells:TrackClick(tonumber(cell),part,x and windowPoint(x,y))
            else result=cells:Click(tonumber(cell),part) end
            if type(result)=='table' then S.note=result.note; if result.stop then return end end
        end
    end
    self:Refresh()
end
function M:Event(event,name,value,...)
    if not S.open then return end
    local id=type(name)=='table' and name.name or tostring(name or '')
    id=id:gsub('^AFUIOptions','')
    if event=='GAME_UI_CANCEL_KEY_PRESSED' then
        if cells.drop then cells:CloseDrop(); self:Refresh() else self:Hide() end
        return
    end
    if event=='GAME_UI_DIALOG_VISIBLE_CHANGED' and id=='' and w(''):IsVisible()~=1 then self:Hide(); return end
    if event=='MOUSE_ENTER' or event=='GAME_UI_MOUSE_ENTER' then
        local index=tonumber(id:match('^Cell(%d+)'))
        local cell=index and cells.cells[index]
        if cell then S.note=cell.option.desc or cell.option.label; paint('Status',14,S.height-(S.compact and 24 or 38),S.width-120,24,S.note,'Mist') end
        return
    end
    if event=='GAME_UI_EDITBOX_CHANGED' and id=='Search' then S.searchDirty=true
    elseif event=='GAME_UI_EDITBOX_ENTER' or event=='EDITBOX_RETURN' or event=='ENTER_PRESSED' then
        if id=='Search' then self:Search(w('Search'):GetText()) else
            local cell,part=id:match('^Cell(%d+)(%a+)$')
            if cell then local result=cells:ValueEvent(tonumber(cell),part); if type(result)=='table' then S.note=result.note end; self:Refresh() end
        end
    end
end
function M:EndDrag()
    cells:StopDrag()
    if S.drag and S.drag.moved then AF:Call('CustomWindows','SavePosition','AFUIOptions',S.x,S.y) end
    S.drag=nil
end
function M:Tick()
    if not S.open then return end
    if S.inspectAt and life('Ticks')>=S.inspectAt then S.inspectAt=nil; pcall(inspect) end
    if S.searchDirty then
        -- Writing the box raises its own change event; only a different text is a new search.
        S.searchDirty=nil; local text=w('Search'):GetText() or ''
        if text~=S.query then self:Search(text) end
    end
    -- The client's press and release events are not delivered reliably to plugin widgets, so a
    -- drag starts from the mouse itself: a press on the title strip moves the window, a press on
    -- a slider track drags its value. Polled only while the window is open.
    local x,y,down=AFCore.Cursor()
    if not x then self:EndDrag(); S.wasDown=true; return end
    if down and not S.wasDown and not S.drag and not cells:Dragging() then
        local px,py,sx,sy=windowPoint(x,y)
        local title=S.title
        if title and px>=title[1] and px<title[1]+title[3] and py>=title[2] and py<title[2]+title[4] then
            cells:CloseDrop()
            cells:RenderDrop(S.height)
            S.drag={mouseX=x,mouseY=y,x=S.x,y=S.y,sx=sx,sy=sy}
        elseif not cells:OverDrop(px,py) then
            local index,part=cells:TrackAt(px,py)
            if index then
                cells:CloseDrop()
                cells:BeginDrag(index,px,part)
                self:Refresh()
            end
        end
    end
    S.wasDown=down
    if not S.drag and not cells:Dragging() then return end
    if S.drag then
        local sw,sh=N.Screen()
        local d=S.drag
        local tx=math.floor(math.max(0,math.min(sw/d.sx-S.width,d.x+(x-d.mouseX)/d.sx)))
        local ty=math.floor(math.max(0,math.min(sh/d.sy-S.height,d.y+(y-d.mouseY)/d.sy)))
        if tx~=math.floor(S.x) or ty~=math.floor(S.y) then
            S.x,S.y,d.moved=tx,ty,true
            w(''):SetRect(tx,ty,S.width,S.height)
        end
    elseif cells:Drag(windowPoint(x,y),true) then self:Refresh() end
    if not down then self:EndDrag() end
end
function M:OnLoad()
    local lazy=N:Resource('lazy')
    S.version=(lazy and lazy.version) or S.version
    if w('Search') and w('Search').SetMaxLength then w('Search'):SetMaxLength(100) end
    if w('Code') and w('Code').SetMaxLength then w('Code'):SetMaxLength(32768) end
end
-- Slash callbacks only queue work: the native loader must run on the shared timer.
local LABELS={open='/afui',move='/moveui'}
function M:Request(what,arg,tab)
    if not LABELS[what] then return end
    S.pending,S.pendingArg,S.pendingTab=what,arg,tab; S.requests=(S.requests or 0)+1
    if WardrobeNative_ResumeTimer then WardrobeNative_ResumeTimer() end
end
local function slashOpen(arg) M:Request('open',arg) end
local function slashMove(arg) M:Request('move',arg) end
function M:BindCommands()
    if not SlashCmdList then return end
    SlashCmdList.AFOPTIONS=slashOpen
    SLASH_AFOPTIONS1,SLASH_AFOPTIONS2,SLASH_AFOPTIONS3,SLASH_AFOPTIONS4='/afui','/aetherfall','/aui','/ec'
    SlashCmdList.AETHERFALLMOVE=slashMove; SLASH_AETHERFALLMOVE1='/moveui'
end
local function build(lazy,index)
    if not lazy[index] or S.built[index] then return end
    g_AddonName='RelicCalc'
    local document=lxp.lom.parse(lazy[index][2]); assert(type(document)=='table','could not parse '..lazy[index][1])
    LoadXMLWidget(document); S.built[index]=life('Ticks')
end
function M:Built(what)
    local lazy=N:Resource('lazy'); if not lazy or #lazy==0 then return true end
    local first,last=1,1; if what=='move' then first,last=2,#lazy end
    local budget,done=what=='move' and 4 or 1,true
    for i=first,last do
        if not S.built[i] then done=false; if budget>0 then build(lazy,i); budget=budget-1 end
        elseif life('Ticks')-S.built[i]<2 then done=false end
    end
    return done
end
local function runPending()
    local what=S.pending; if not what then return end
    local ok,ready=pcall(M.Built,M,what); if ok and not ready then return end
    local arg,tab=S.pendingArg,S.pendingTab
    S.pending,S.pendingArg,S.pendingTab=nil,nil,nil
    if not ok then
        S.slashError=LABELS[what]..' (building): '..tostring(ready)
        DisplayChatMessage(0,'Aetherfall Interface '..S.slashError,.91,.63,.56); return
    end
    if what=='open' then M:Open(arg,tab) else AF:Call('MoverEditor','Unlock',arg) end
end
function M:SlashHandlers() return slashOpen,slashMove end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('OpenOptions',M.Open)
M:Listen('HideOptions',M.Hide)
M:Listen('RefreshOptions',M.RefreshFor)
M:Listen('OptionsCode',M.SetCode)
M:Listen('GetOptionsCode',function() AF:Send('OptionsCodeValue',M:GetCode()) end)
M:Listen('BindCommands',M.BindCommands)
M:Listen('ClearEditorDrag',function(self, resized)
    self:EndDrag()
    if resized and S.open then
        dimensions()
        S.x,S.y=AF:Call('CustomWindows','Restore','AFUIOptions',S.width,S.height)
        w(''):SetRect(S.x or 0,S.y or 0,S.width,S.height)
        self:Refresh()
    end
end)
M:Phase('requests',function() if life('IsSettled') then runPending(); M:Tick() end end,10)
