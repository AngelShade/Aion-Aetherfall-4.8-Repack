local K=AF.Skin
local function configure(frame)
    for _,resource in ipairs({'Health','Power'}) do
        for _,slot in ipairs({'Left','Centre','Right'}) do
            local object=frame.widgets[resource..'Text'..slot]
            K.Font(object,frame.appearance.font);K.Text(object,frame.appearance.text)
        end
    end
end
local function update(frame)
    for _,resource in ipairs({'Health','Power'}) do
        local key=resource:lower()
        local r=frame.bars[key]
        local texts,active={},0
        for _,slot in ipairs({'Left','Centre','Right'}) do
            local value=AF.Tags:Format(frame.settings[key..slot],frame.values)
            if resource=='Power' and (not frame.values.mpmax or frame.values.mpmax<=0) then value='' end
            texts[slot]=value
            if value~='' then active=active+1 end
        end
        local width=math.max(1,(r[3]-6)/math.max(1,active))
        local position=0
        for _,slot in ipairs({'Left','Centre','Right'}) do
            local object=frame.widgets[resource..'Text'..slot]
            local text=texts[slot]
            if text~='' then
                -- A label shorter than the font clips the letters; centre a full line on the bar.
                local px=frame.appearance.fontPx or 0
                local h=math.max(r[4],14,px>13 and px+3 or 0)
                K.Place(object,r[1]+3+position*width,r[2]+(r[4]-h)/2,width,h);K.SetText(object,text);K.Show(object)
                position=position+1
            else K.SetText(object,'');K.Hide(object) end
        end
    end
end
-- Dirty input: the formatted strings themselves, so a user format with a live tag such as [time] still repaints.
local SLOTS={'healthLeft','healthCentre','healthRight','powerLeft','powerCentre','powerRight'}
local function texts(frame)
    local out,s={},frame.settings
    for _,key in ipairs(SLOTS) do out[#out+1]=AF.Tags:Format(s[key],frame.values) end
    if frame.unit=='player' then out[#out+1]=AF.Tags:Format(s.dpText or '',frame.values) end
    return table.concat(out,'\31')
end
AF:Call('UnitFrames','RegisterElement','Name',{Configure=configure,Update=update,Key=texts})
