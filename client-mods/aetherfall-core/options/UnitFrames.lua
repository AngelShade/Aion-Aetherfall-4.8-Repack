-- Deferred pages over the one UnitFrame schema, with independent unit settings.
AF.Config:Register('UnitFrames',function()
    local fonts={'Shared','Default','Option','Value','Title','Large title','Window','Name','Button'}
    local fontValues={0,1,2,3,4,5,6,7,8}
    local pages={{id='Unit Frames',name='Unit Frames',category='Unit Frames',order=2,
        desc='Passive resource frames preserve the native targeting and menus. Move their native anchors with /moveui.',tabs={
        {id='General',name='General',groups={{name='Replacement frames',options={
            {kind='toggle',key='ufEnabled',label='Enable custom frames',order=1},
            {kind='range',key='ufInterval',label='Update interval',order=2,unit=' ticks',desc='One tick is 50 ms. Unit and damage events also refresh active frames.'},
            {kind='select',key='ufFont',label='Shared text font',order=3},
            {kind='description',label='Player, Target, Target of target and Party pages keep their own settings. Native visibility settings still control whether a frame is present.',width='full',order=4},
        }}}},
        {id='Colors',name='Shared colors',groups={{name='Shared colors',options={
            {kind='color',key='ufFixedColor',label='Fixed health',order=1},
            {kind='color',key='ufPowerColor',label='Power',order=2},
            {kind='color',key='ufBackdropColor',label='Backdrop',order=3},
            {kind='color',key='ufBorderColor',label='Border',order=4},
            {kind='color',key='ufTextColor',label='Text',order=5},
            {kind='color',key='ufDeadColor',label='Dead',order=6},
            {kind='color',key='ufOfflineColor',label='Unavailable data',order=7},
            {kind='description',label='Image colors use the nearest of 16 values per channel. Unavailable means native unit data is absent; it does not assert connectivity.',width='full',order=8},
        }}}},
    }}}
    for index,group in ipairs({'player','target','targettarget','party'}) do
        local unit=group
        local title=({player='Player',target='Target',targettarget='Target of target',party='Party'})[unit]
        local prefix=unit..'.'
        local function disabled() return not AF:Call('UnitFrames','IsEnabled',unit) end
        local function row(kind,key,label,order,extra)
            local option={kind=kind,key=prefix..key,label=label,order=order,disabled=disabled}
            for k,v in pairs(extra or {}) do option[k]=v end
            return option
        end
        local general={
            row('toggle','enabled','Enable '..title:lower(),1,{disabled=function() return not AF:Call('UnitFrames','IsEnabled') end}),
            row('select','font','Text font',2,{choices=fonts,map=fontValues}),
            row('range','nameLimit','Name character limit',3),row('toggle','showLevel','Show level',4),
            row('toggle','border','Show borders',5),row('range','backdropOpacity','Backdrop opacity',6,{unit='%'}),
        }
        local groups={{name=title,options=general}}
        if unit=='target' or unit=='party' then
            groups[2]={name='Bot label',options={
                row('toggle','showBot','Show bot label',1,{desc='Turns the whole bot label on or off.'}),
                row('toggle','showRarity','Show gear',2,{desc='"Equip: Good" in the gear colour.'}),
                row('toggle','showRank','Show rank stars',3),
            }}
        end
        if unit=='player' then
            general[#general+1]=row('toggle','showDP','Show DP',10)
            general[#general+1]=row('range','dpHeight','DP height',11,{unit=' px',desc='DP numbers need a height of 11 or more.'})
        end
        local text={}
        if unit=='player' then
            text[#text+1]=row('input','dpText','DP text',30,{width='full',desc='Tags: [dp:current], [dp:max], [dp:percent], [dp:current-max], [dp:deficit].'})
        end
        for resourceIndex,resource in ipairs({'health','power'}) do
            for slotIndex,slot in ipairs({'Left','Centre','Right'}) do
                text[#text+1]=row('input',resource..slot,resource:sub(1,1):upper()..resource:sub(2)..' '..slot:lower(),resourceIndex*10+slotIndex,
                    {width='full',desc='Tags: [name], [name:short], [level], [class], [state], [bot], [hp:current], [hp:percent], [hp:current-max], [hp:deficit] and matching mp tags.'})
            end
        end
        pages[#pages+1]={id='UnitFrame'..title:gsub(' ','') ,name=title,category='Unit Frames',parent='Unit Frames',order=2+index/100,
            desc='The native frame remains the targeting and menu owner. Use /moveui to move its anchor.',tabs={
                {id='General',name='General',groups=groups},
                {id='Bars',name='Bars',groups={{name='Resource geometry',options={
                    row('range','healthHeight','Health height',1,{unit=' px'}),row('range','powerHeight','Power height',2,{unit=' px'}),
                    row('range','spacing','Bar spacing',3,{unit=' px'}),
                    {kind='description',label='Heights fit within the existing native bounds. Target of target and unavailable power keep unsupported bars empty.',width='full',order=4},
                }}}},
                {id='Colors',name='Colors',groups={{name='Health and power',options={
                    row('select','healthMode','Health color mode',1,{choices={'Class','Fixed shared','Custom'},map={'class','fixed','custom'}}),
                    row('color','healthColor','Custom health',2),
                    row('select','powerMode','Power color mode',3,{choices={'Shared','Custom'},map={'shared','custom'}}),
                    row('color','powerColor','Custom power',4),
                    row('select','stateColors','State color mode',5,{choices={'Shared','Custom'},map={'shared','custom'}}),
                    row('color','deadColor','Custom dead',6),row('color','offlineColor','Custom unavailable data',7),
                    {kind='description',label='Class color uses only a class supplied by the stock unit API. Targets without class data use the shared fixed health color.',width='full',order=8},
                }}}},
                {id='Text',name='Text',groups={{name='Resource text slots',options=text}}},
            }}
    end
    return pages
end)
