AF.Config:Register('Tooltip', function()
    return {{id='Tooltip',name='Tooltip',category='Tooltip',order=6,desc='World and UI tooltips have independent anchor policies.',tabs={
        {id='General',name='General',order=1,groups={
            {name='Enable',order=1,options={{kind='toggle',key='ttshow',label='Tooltips'},{kind='toggle',key='ttclamp',label='Keep on screen'}}},
            {name='Anchors',order=2,options={
                {kind='select',key='ttanchor',label='World (NPCs, players)',order=1},
                {kind='select',key='ttui',label='UI (buttons, items)',order=2},
                {kind='select',key='ttgrow',label='Grow from (Mover)',order=3},
                {kind='execute',label='Tooltip mover',action='unlock',text='Unlock',order=4},
            }},
        }},
        {id='Offsets',name='Offsets',order=2,groups={
            {name='World cursor offset',order=1,options={
                {kind='range',key='ttx',label='X offset',unit=' px',order=1},
                {kind='range',key='tty',label='Y offset',unit=' px',order=2},
            }},
            {name='UI cursor offset',order=2,options={
                {kind='range',key='ttuix',label='X offset',unit=' px',order=1},
                {kind='range',key='ttuiy',label='Y offset',unit=' px',order=2},
            }},
            {name='Mover preview',order=3,options={
                {kind='range',key='tipwidth',label='Preview width',unit=' px',order=1},
                {kind='range',key='tipheight',label='Preview height',unit=' px',order=2},
            }},
        }},
    }}}
end)
