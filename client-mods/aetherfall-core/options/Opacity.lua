AF.Config:Register('Opacity', function()
    return {
        {id='General',name='General',category='General',order=1,tabs={{id='Appearance',name='Appearance',groups={
            {name='Backdrops',order=3,options={
                {kind='range',key='windowsalpha',label='Window and HUD opacity',unit='%',desc='Backdrop opacity. Text and item icons retain their own opacity.'},
            }},
        }}}},
        {id='Action Bars',name='Action Bars',category='Action Bars',order=3,tabs={{id='General',name='General',groups={
            {name='Backdrops',order=2,options={{kind='range',key='barsalpha',label='Group background opacity',unit='%'}}},
        }}}},
        {id='Chat',name='Chat',category='Chat',order=4,tabs={{id='Appearance',name='Appearance',order=2,groups={
            {name='Backdrops',options={{kind='range',key='chatalpha',label='Background opacity',unit='%'}}},
        }}}},
        {id='Data Texts',name='Data Texts',category='Data Texts',order=5,tabs={{id='Panel',name='Panel',groups={
            {name='Backdrops',order=2,options={{kind='range',key='dataalpha',label='Background opacity',unit='%',desc='-1 inherits the window and HUD opacity.'}}},
        }}}},
        {id='Unit Frames',name='Unit Frames',category='Unit Frames',order=2,tabs={{id='Backdrops',name='Backdrops',order=2,groups={
            {name='Native fallback backdrops',options={
                {kind='range',key='playeralpha',label='Player',unit='%',order=1,desc='-1 inherits the window and HUD opacity.'},
                {kind='range',key='targetalpha',label='Target',unit='%',order=2,desc='-1 inherits the window and HUD opacity.'},
                {kind='range',key='assistalpha',label='Target of target',unit='%',order=3,desc='-1 inherits the window and HUD opacity.'},
                {kind='range',key='partyalpha',label='Party',unit='%',order=4,desc='-1 inherits the window and HUD opacity.'},
                {kind='description',label='These settings style native frames when custom unit frames are disabled. Custom backdrops are configured on each unit page.',width='full',order=5},
            }},
        }}}},
    }
end)
