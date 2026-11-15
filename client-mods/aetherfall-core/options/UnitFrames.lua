-- Deferred pages over the one UnitFrame schema, with independent unit settings.
AF.Config:Register('UnitFrames',function()
    -- Labels must match FONTS in modules/UnitFrames.lua (0 = Shared). The saved keys
    -- ufFont and <unit>.font hold the text SIZE choice.
    -- Size ids 9..15; legacy saved 1..8 are shown through UnitFrames:FontId (never rewritten).
    local sizes={'Small','Standard','Standard (no shadow)','Medium','Large','Larger','Extra large'}
    local sizeValues={9,10,11,12,13,14,15}
    local fonts,fontValues={'Shared'},{0}
    for i,label in ipairs(sizes) do fonts[i+1],fontValues[i+1]=label,sizeValues[i] end
    local function fontGet(key) return function() return AF:Call('UnitFrames','FontId',AF.DB:Get('UnitFrames',key)) end end
    local pages={{id='Unit Frames',name='Unit Frames',category='Unit Frames',order=2,
        desc='Customize your resource frames while keeping native targeting and menus. Use Toggle anchors to move them.',tabs={
        {id='General',name='General',groups={{name='Replacement frames',options={
            {kind='toggle',key='ufEnabled',label='Enable custom frames',order=1},
            {kind='range',key='ufInterval',label='Update interval',order=2,unit=' ticks',desc='One tick is 50 ms. Unit and damage events also refresh active frames.'},
            {kind='select',key='ufFont',label='Shared text size',order=3,choices=sizes,map=sizeValues,get=fontGet('ufFont')},
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
            row('select','font','Text size',2,{choices=fonts,map=fontValues,get=fontGet(prefix..'font')}),
            row('range','nameLimit','Name character limit',3),row('toggle','showLevel','Show level',4),
            row('toggle','border','Show borders',5),row('range','backdropOpacity','Backdrop opacity',6,{unit='%'}),
            row('toggle','showClass','Show class icon',7,{desc='The game\'s class emblem at the left of the header, for characters. [class] in a text slot writes the class name.'}),
        }
        local groups={{name=title,options=general}}
        if unit~='targettarget' then
            general[#general+1]=row('range','portraitOpacity','Portrait background',9,{unit='%',desc='Opacity of the backdrop-coloured background behind the 3D portrait. 0% shows the world behind it. Backdrop opacity does not affect the portrait.'})
        end
        if unit=='target' or unit=='party' then
            general[#general+1]=row('toggle','showPortrait','Show portrait',8,{desc=unit=='target' and 'The selected character or creature\'s live 3D portrait, left of the bars. Bots keep their gear-quality border.' or 'A bot\'s live 3D portrait in a border of its gear quality, left of the bars.'})
            groups[2]={name='Bot label',options={
                row('toggle','showBot','Show bot label',1,{desc='Turns the whole bot label on or off.'}),
                row('toggle','showRarity','Show gear',2,{desc='"Equip: Good" in the gear colour.'}),
                row('toggle','showRank','Show rank stars',3),
            }}
        end
        if unit=='player' then
            general[#general+1]=row('toggle','showDP','Show DP',10)
            general[#general+1]=row('toggle','showPortrait','Show portrait',12,{desc='Your character\'s live 3D portrait in a painted border, left of the bars.'})
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
            desc='The native frame keeps targeting and menus. Use Toggle anchors to move it.',tabs={
                {id='General',name='General',groups=groups},
                {id='Bars',name='Bars',groups={{name='Frame size',options={
                    row('range','frameWidth','Frame width',1,{unit='%',desc='Width of the whole frame, as a share of the native frame. It grows to the right of the native anchor.'}),
                    row('range','frameHeight','Frame height',2,{unit='%',desc='Height of the whole frame, as a share of the native frame. It grows downwards from the native anchor.'}),
                    {kind='description',label='100% keeps the original frame size. Targeting and menus follow the resized frame. Use Toggle anchors to move it.',width='full',order=3},
                }},{name='Resource geometry',options=(function()
                    local bars={
                        row('range','healthWidth','HP width',1,{unit='%',desc='Share of the bar area, from the left.'}),
                        row('range','healthHeight','HP height',2,{unit=' px'}),
                        row('range','powerWidth','MP width',3,{unit='%',desc='Share of the bar area, from the left.'}),
                        row('range','powerHeight','MP height',4,{unit=' px'}),
                    }
                    if unit=='player' then
                        bars[#bars+1]=row('range','dpWidth','DP width',5,{unit='%',desc='Share of the bar area, from the left.'})
                        bars[#bars+1]=row('range','dpHeight','DP height',6,{unit=' px',desc='DP numbers need a height of 11 or more at 100% frame height.'})
                    end
                    bars[#bars+1]=row('range','spacing','Bar spacing',7,{unit=' px'})
                    bars[#bars+1]={kind='description',label='Bar heights and spacing scale with Frame height and fit inside the frame. Target of target and unavailable power keep unsupported bars empty.',width='full',order=8}
                    return bars
                end)()}}},
                {id='Colors',name='Colors',groups={{name='Health and power',options={
                    row('select','healthMode','Health color mode',1,{choices={'Class','Fixed shared','Custom'},map={'class','fixed','custom'}}),
                    row('color','healthColor','Custom health',2),
                    row('select','powerMode','Power color mode',3,{choices={'Shared','Custom'},map={'shared','custom'}}),
                    row('color','powerColor','Custom power',4),
                    row('select','stateColors','State color mode',5,{choices={'Shared','Custom'},map={'shared','custom'}}),
                    row('color','deadColor','Custom dead',6),row('color','offlineColor','Custom unavailable data',7),
                    row('select','textMode','Text color mode',8,{choices={'Shared','Custom'},map={'shared','custom'},desc='Shared uses the Text color on the Shared colors page.'}),
                    row('color','textColor','Custom text',9,{desc='Names, numbers and level on this frame.'}),
                    {kind='description',label='Class color uses the character\'s class from the game. NPCs and targets without class data use the shared fixed health color.',width='full',order=10},
                }}}},
                {id='Text',name='Text',groups={{name='Resource text slots',options=text}}},
                {id='Header',name='Header',groups={{name='Header layout',options=(function()
                    -- Each item keeps its automatic place in the top row and moves by its own offset.
                    local items={{'classIcon','Class icon'},{'level','Level'},{'state','Dead / unavailable text'}}
                    if unit=='target' or unit=='party' then table.insert(items,2,{'gear','Gear label and rank stars'}) end
                    if unit=='target' then items[#items+1]={'chest','Loot chest (Codex)'} end
                    local rows={}
                    for i,item in ipairs(items) do
                        rows[#rows+1]=row('range',item[1]..'X',item[2]..' left/right',i*2-1,{unit=' px',desc='Negative moves left, positive right. 0 is the automatic place.'})
                        rows[#rows+1]=row('range',item[1]..'Y',item[2]..' up/down',i*2,{unit=' px',desc='Negative moves up, positive down. 0 is the automatic place.'})
                    end
                    rows[#rows+1]={kind='description',label='All offsets at 0 keep the automatic layout. The header text starts after the loot chest while the chest sits at its start.',width='full',order=#rows+1}
                    return rows
                end)()}}},
            }}
    end
    return pages
end)
