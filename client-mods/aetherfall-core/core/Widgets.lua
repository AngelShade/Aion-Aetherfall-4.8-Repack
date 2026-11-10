-- Reusable option cells. The caller supplies widget lookup and owns navigation.
local W = {}
AF.Widgets = W
local C, Skin = AF.Config, AF.Skin
local PARTS = {'Label','Desc','MinMax','Check','Value','Less','More','Track','Fill','Thumb','Edit','Apply','Arrow','Color','Hex','HexApply','Red','RedLess','RedMore','Green','GreenLess','GreenMore','Blue','BlueLess','BlueMore','RedTrack','RedFill','RedThumb','GreenTrack','GreenFill','GreenThumb','BlueTrack','BlueFill','BlueThumb'}
local DROP_ROWS, ROW_HEIGHT = 8, 26
local function native(object, method, ...)
    if object and type(object[method]) == 'function' then return pcall(object[method], object, ...) end
end
local function clamp(value, low, high) return math.max(low,math.min(high,value)) end
function W.New(lookup)
    return setmetatable({lookup=lookup,cells={},expanded={},confirmation=nil}, {__index=W})
end
function W:Part(index, part) return self.lookup('Cell' .. index .. part) end
function W:MaxLength(widget, length)
    self.lengths = self.lengths or setmetatable({}, {__mode='k'})
    if widget and self.lengths[widget] ~= length then native(widget,'SetMaxLength',length); self.lengths[widget] = length end
end
function W:Hide(index)
    for _, part in ipairs(PARTS) do Skin.Hide(self:Part(index,part)) end
    self.cells[index] = nil
end
-- Descriptions live in the hint line and the tooltip, as in ElvUI; a cell is its label and control.
function W:Height(option)
    if option.kind == 'header' then return 24 end
    if option.kind == 'description' then return 22 end
    if option.kind == 'execute' and option.inline then return 50 end  -- on the control row of its neighbours
    if option.kind == 'toggle' or option.kind == 'execute' then return 26 end
    if option.kind == 'color' and self.expanded[option.id] then return self.compact and 132 or 212 end
    if option.kind == 'range' then return 76 end
    return 50
end
local function rgb(value)
    if type(value) == 'string' then
        local r,g,b = value:match('^#?(%x%x)(%x%x)(%x%x)$')
        if r then return {tonumber(r,16)/255,tonumber(g,16)/255,tonumber(b,16)/255} end
    end
    if type(value) ~= 'table' then return {1,1,1} end
    return {clamp(tonumber(value[1] or value.r) or 1,0,1),clamp(tonumber(value[2] or value.g) or 1,0,1),clamp(tonumber(value[3] or value.b) or 1,0,1)}
end
-- Renders a cell in place. Parts the cell uses are placed and shown; the rest are hidden
-- afterwards, so refreshing an unchanged cell sends nothing to the client and cannot flicker.
function W:Render(index, option, x, y, width)
    local disabled = C:Disabled(option)
    local cell = {option=option,x=x,y=y,width=width,disabled=disabled}
    self.cells[index] = cell
    local used = {}
    local function part(name, px, py, pw, ph, text, style)
        local widget=self:Part(index,name); if not widget then return end
        used[name]=true
        Skin.Place(widget,px,py,pw,ph)
        if text ~= nil then
            if style == 'field' then
                -- An edit box is rewritten only when its option's value changed, never while typing.
                local key=name..'#'..tostring(option.id)..'='..tostring(text)
                if cell.fields == nil then cell.fields = {} end
                if self.fieldText == nil then self.fieldText = {} end
                if self.fieldText[index..name] ~= key then widget:SetText(tostring(text)); self.fieldText[index..name] = key end
            else Skin.SetText(widget,text) end
        end
        Skin.Show(widget)
        if style == 'button' then Skin.Button(widget,pw,ph,false,disabled)
        elseif style == 'field' then Skin.Field(widget,pw,ph,disabled)
        elseif style ~= 'plain' then Skin.Text(widget,disabled and 'Disabled' or style or 'Ice') end
        if style == 'button' or style == 'field' then Skin.Enable(widget,not disabled) end
        if self.tips == nil then self.tips = {} end
        local tip = option.desc or ''
        if self.tips[index..name] ~= tip then native(widget,'SetTooltip',tip); self.tips[index..name] = tip end
        return widget
    end
    local value = C:Get(option)
    if option.kind == 'header' then
        part('Label',x,y,width,20,option.label,'Gold')
    elseif option.kind == 'description' then
        part('Desc',x,y,width,20,option.desc or option.label,'Mist')
    elseif option.kind == 'toggle' then
        local on = value == true or value == 1
        local check=part('Check',x,y+3,18,18,'','plain')
        Skin.Check(check,on,disabled); Skin.Enable(check,not disabled)
        part('Desc',x+26,y+2,width-26,20,option.label,disabled and 'Disabled' or 'Ice')
    elseif option.kind == 'execute' then
        local text = option.text and option.text ~= 'Run' and option.text or option.label
        part('Value',x,option.inline and y+22 or y,width,24,self.confirmation == option.id and 'Confirm: '..text or text,'button')
    else
        part('Label',x,y,width,20,option.label)
        local top = y+22
        if option.kind == 'input' then
            local edit=part('Edit',x,top,math.max(40,width-58),24,C:Display(option),'field')
            local rule=C:Rule(option) or {}
            self:MaxLength(edit,option.maxLength or rule.maxLength or tonumber(rule[3]) or 128)
            part('Apply',x+width-54,top,54,24,'Apply','button')
        elseif option.kind == 'select' then
            local choices = C:Choices(option)
            part('Value',x,top,width,24,choices[C:Index(option)] or 'Choose…','button')
            Skin.Image(part('Arrow',x+width-17,top+6,12,12,nil,'plain'),0,'arrow')
        elseif option.kind == 'range' then
            local low, high, step=C:Bounds(option)
            local trackWidth=math.max(30,width-10)
            local track=part('Track',x+5,top,trackWidth,24,'','plain'); Skin.Track(track,trackWidth,24); Skin.Enable(track,not disabled)
            local fraction=high>low and clamp(((tonumber(value) or low)-low)/(high-low),0,1) or 0
            local fill=part('Fill',x+6,top+8,math.max(1,(trackWidth-2)*fraction),8,nil,'plain'); Skin.Fill(fill)
            local thumb=part('Thumb',x+5+(trackWidth-10)*fraction,top+2,10,20,nil,'plain'); Skin.Thumb(thumb)
            cell.track={x+5,trackWidth,low,high,step,nil,top,24}
            part('MinMax',x,top+26,44,20,tostring(low),'Mist')
            part('Less',x+46,top+25,22,24,'-','button')
            part('Edit',x+70,top+25,math.max(32,width-186),24,tostring(value or low)..(option.unit or ''),'field')
            part('Apply',x+width-114,top+25,24,24,'OK','button')
            part('More',x+width-88,top+25,22,24,'+','button')
            part('Desc',x+width-64,top+26,64,20,tostring(high)..(option.unit or ''),'Mist')
        elseif option.kind == 'color' then
            part('Value',x,top,math.max(40,width-30),24,C:Display(option),'button')
            local swatch=part('Color',x+width-24,top,24,24,nil,'plain'); Skin.Track(swatch,24,24); Skin.Color(swatch,rgb(value))
            if self.expanded[option.id] then
                local channels=rgb(value)
                cell.channels={}
                if self.compact then
                    part('Label',x,y,width-92,20,option.label)
                    part('Value',x+width-88,y,88,20,'Collapse','button')
                    used.Color=nil
                end
                for channel,name in ipairs({'Red','Green','Blue'}) do
                    local cy=top+(self.compact and (channel-1)*26 or 28+(channel-1)*42)
                    local labelWidth=self.compact and 64 or width
                    part(name,x,cy,labelWidth,18,name .. ': ' .. math.floor(channels[channel]*255+.5))
                    local inset=self.compact and 96 or 28
                    local trackWidth=math.max(20,width-inset-28)
                    local buttonY=cy+(self.compact and 0 or 16)
                    local track=part(name..'Track',x+inset,buttonY,trackWidth,24,'','plain'); Skin.Track(track,trackWidth,24)
                    local fill=part(name..'Fill',x+inset+1,buttonY+8,math.max(1,(trackWidth-2)*channels[channel]),8,nil,'plain'); Skin.Fill(fill)
                    local thumb=part(name..'Thumb',x+inset+(trackWidth-10)*channels[channel],buttonY+2,10,20,nil,'plain'); Skin.Thumb(thumb)
                    part(name..'Less',x+(self.compact and 68 or 0),buttonY,24,24,'-','button')
                    part(name..'More',x+width-24,buttonY,24,24,'+','button')
                    cell.channels[name]={x+inset,trackWidth,0,255,1,channel,buttonY,24}
                end
                local hexY=top+(self.compact and 80 or 158)
                local hex=part('Hex',x,hexY,math.max(42,width-56),24,C:Display(option),'field')
                self:MaxLength(hex,7)
                part('HexApply',x+width-52,hexY,52,24,'Apply','button')
            end
        end
    end
    for _, name in ipairs(PARTS) do if not used[name] then Skin.Hide(self:Part(index,name)) end end
end
function W:CloseDrop() self.drop=nil end
function W:RenderDrop(windowHeight)
    local drop=self.drop
    local cell=drop and self.cells[drop.index]
    if not cell or cell.option.id~=drop.id or cell.disabled then
        self:CloseDrop()
        for i=1,DROP_ROWS do Skin.Hide(self.lookup('DropChoice'..i)) end
        for _, name in ipairs({'DropBackdrop','DropPrev','DropNext','DropPage'}) do Skin.Hide(self.lookup(name)) end
        return
    end
    local labels=C:Choices(cell.option)
    local pages=math.max(1,math.ceil(#labels/DROP_ROWS))
    drop.page=clamp(drop.page,1,pages)
    local first=(drop.page-1)*DROP_ROWS
    local rows=math.min(#labels-first,DROP_ROWS)
    local x,y,width=cell.x,cell.y+47,cell.width
    local height=rows*ROW_HEIGHT+4+(pages>1 and 26 or 0)
    if y+height>windowHeight-4 then y=math.max(4,cell.y+21-height) end
    drop.rect={x,y,width,height}
    local function show(name,px,py,pw,ph,label,selected)
        local control=self.lookup(name)
        Skin.Place(control,px,py,pw,ph)
        if label then Skin.SetText(control,label) end
        Skin.Show(control)
        if name~='DropPage' then Skin.Button(control,pw,ph,selected) end
    end
    show('DropBackdrop',x,y,width,height)
    local current=C:Index(cell.option)
    for i=1,DROP_ROWS do
        local name='DropChoice'..i
        if i<=rows then show(name,x+2,y+2+(i-1)*ROW_HEIGHT,width-4,ROW_HEIGHT,labels[first+i],first+i==current)
        else Skin.Hide(self.lookup(name)) end
    end
    if pages>1 then
        local footer=y+2+rows*ROW_HEIGHT
        show('DropPrev',x+2,footer,24,24,'<')
        show('DropNext',x+width-26,footer,24,24,'>')
        show('DropPage',x+27,footer,width-54,24,drop.page..' / '..pages)
    else
        for _, name in ipairs({'DropPrev','DropNext','DropPage'}) do Skin.Hide(self.lookup(name)) end
    end
end
function W:OverDrop(x,y)
    local r=self.drop and self.drop.rect
    return r and x>=r[1] and x<r[1]+r[3] and y>=r[2] and y<r[2]+r[4]
end
function W:DropClick(id)
    local drop=self.drop
    local cell=drop and self.cells[drop.index]
    if not cell or cell.option.id~=drop.id or cell.disabled then return end
    if id=='DropPrev' or id=='DropNext' then
        drop.page=drop.page+(id=='DropNext' and 1 or -1)
    else
        local row=tonumber(id:match('^DropChoice(%d+)$'))
        if not row then return end
        local option=cell.option
        local value=C:Values(option)[(drop.page-1)*DROP_ROWS+row]
        self:CloseDrop()
        if value~=nil and value~=C:Get(option) then return C:Set(option,value) end
    end
end
function W:Click(index, part)
    local cell=self.cells[index]
    if not cell or cell.disabled or C:Hidden(cell.option) then return end
    local option=cell.option
    if option.kind == 'execute' then
        local confirming=self.confirmation==option.id
        self.confirmation=nil
        local result=C:Action(option,confirming)
        if type(result)=='table' and result.confirm then self.confirmation=option.id end
        return result
    end
    self.confirmation=nil
    if option.kind == 'toggle' then return C:Change(option,'More') end
    if option.kind == 'select' and part=='Value' then
        if self.drop and self.drop.index==index then self:CloseDrop()
        else self.drop={index=index,id=option.id,page=math.ceil(C:Index(option)/DROP_ROWS)} end
        return
    end
    if option.kind == 'color' then
        if part=='Value' or part=='Color' then self.expanded[option.id]=not self.expanded[option.id]; return end
        if part=='HexApply' then
            local text=self:Part(index,'Hex'):GetText() or ''
            local r,g,b=text:match('^#?(%x%x)(%x%x)(%x%x)$')
            if not r then return {note='Enter a colour as #RRGGBB.'} end
            return C:Set(option,'#' .. r:lower() .. g:lower() .. b:lower())
        end
        local channel,direction=part:match('^(%a+)(Less)$')
        if not channel then channel,direction=part:match('^(%a+)(More)$') end
        local names={Red=1,Green=2,Blue=3}
        if names[channel] then
            local value=rgb(C:Get(option)); local i=names[channel]
            value[i]=clamp(value[i]+(direction=='Less' and -1 or 1)/255,0,1)
            return C:Set(option,string.format('#%02x%02x%02x',math.floor(value[1]*255+.5),math.floor(value[2]*255+.5),math.floor(value[3]*255+.5)))
        end
    elseif part=='Apply' then
        local text=self:Part(index,'Edit'):GetText()
        if option.kind=='input' then return C:Set(option,text or '') end
        local value=tonumber((tostring(text or ''):match('%-?%d+%.?%d*')))  -- the box shows the unit after the number
        local low,high,step=C:Bounds(option)
        if not value or value~=value or value<low or value>high then return {note=string.format('Enter a number from %s to %s.',low,high)} end
        value=clamp(low+math.floor((value-low)/step+.5)*step,low,high)
        return C:Set(option,value)
    elseif part=='Track' and option.kind=='range' then
        return false
    elseif part=='Less' or part=='More' then return C:Change(option,part) end
end
function W:ValueEvent(index, part)
    local cell=self.cells[index]; if not cell or cell.disabled then return end
    if part=='Edit' then return self:Click(index,'Apply')
    elseif part=='Hex' then return self:Click(index,'HexApply') end
end
function W:TrackClick(index, part, cursorX)
    local cell=self.cells[index]
    if not cell or cell.disabled then return end
    local channel=part:match('^(%a+)Track$')
    local track=channel and cell.channels and cell.channels[channel] or cell.track
    if not track then return end
    return self:SetTrack(cell.option,track,cursorX)
end
function W:SetTrack(option,track,cursorX)
    if not cursorX then return false end
    -- The pointer uses the same endpoint centres as the drawn thumb.
    local raw=track[3]+clamp((cursorX-track[1]-5)/(track[2]-10),0,1)*(track[4]-track[3])
    local value=clamp(track[3]+math.floor((raw-track[3])/track[5]+.5)*track[5],track[3],track[4])
    if track[6] then
        local channels=rgb(C:Get(option))
        channels[track[6]]=value/255
        value=string.format('#%02x%02x%02x',math.floor(channels[1]*255+.5),math.floor(channels[2]*255+.5),math.floor(channels[3]*255+.5))
    end
    if value~=C:Get(option) then C:Set(option,value); return true end
    return false
end
function W:BeginDrag(index, cursorX, part)
    local cell=self.cells[index]
    if not cell or cell.disabled then return false end
    local channel=(part or ''):match('^(%a+)Track$')
    local track=channel and cell.channels and cell.channels[channel] or cell.track
    if not track then return false end
    self.drag={index=index,track=AF.Copy(track),option=cell.option}
    return self:Drag(cursorX,true)
end
function W:Drag(cursorX, down)
    local drag=self.drag; if not drag then return false end
    if not down or not cursorX then self.drag=nil; return false end
    return self:SetTrack(drag.option,drag.track,cursorX)
end
function W:StopDrag() self.drag=nil end
-- Which slider track, if any, lies under a point given in window coordinates.
function W:TrackAt(px, py)
    for index, cell in pairs(self.cells) do
        if not cell.disabled then
            local track=cell.track
            if track and px>=track[1] and px<track[1]+track[2] and py>=track[7] and py<track[7]+track[8] then return index,'Track' end
            for name, channel in pairs(cell.channels or {}) do
                if px>=channel[1] and px<channel[1]+channel[2] and py>=channel[7] and py<channel[7]+channel[8] then return index,name..'Track' end
            end
        end
    end
end
function W:Dragging() return self.drag~=nil end
function W:ResetConfirmation() self.confirmation=nil end
