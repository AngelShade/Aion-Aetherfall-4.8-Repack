AF.Config:Register('Visibility', function()
    return {
        {id='General',name='General',category='General',order=1,tabs={{id='Visibility',name='Visibility',order=2,groups={
            {name='Native HUD',options={
                {kind='toggle',key='xpbar',label='Experience bar',order=1},
                {kind='toggle',key='showBuffs',label='Buffs',order=2},
                {kind='toggle',key='showDebuffs',label='Debuffs',order=3},
                {kind='toggle',key='showTargetAuras',label='Target auras',order=4},
            }},
        }}}},
        {id='Unit Frames',name='Unit Frames',category='Unit Frames',order=2,tabs={{id='Visibility',name='Visibility',order=1,groups={
            {name='Native frames',options={
                {kind='toggle',key='showPlayer',label='Player frame',order=1},
                {kind='toggle',key='tot',label='Target of target',order=2},
                {kind='toggle',key='party',label='Party frames',order=3},
            }},
        }}}},
        {id='Minimap',name='Minimap',category='Minimap',order=7,tabs={{id='General',name='General',order=1,groups={
            {name='Visibility',options={{kind='toggle',key='showRadar',label='Enable minimap'}}},
        }}}},
        {id='Shortcuts',name='Shortcuts',category='Shortcuts',order=8,tabs={{id='General',name='General',groups={
            {name='Visibility',options={{kind='toggle',key='startmenu',label='Shortcut menu'}}},
        }}}},
    }
end)
