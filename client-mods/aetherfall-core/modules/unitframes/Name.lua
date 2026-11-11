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
                local h=math.max(r[4],14)
                K.Place(object,r[1]+3+position*width,r[2]+(r[4]-h)/2,width,h);K.SetText(object,text);K.Show(object)
                position=position+1
            else K.SetText(object,'');K.Hide(object) end
        end
    end
end
AF:Call('UnitFrames','RegisterElement','Name',{Configure=configure,Update=update})
