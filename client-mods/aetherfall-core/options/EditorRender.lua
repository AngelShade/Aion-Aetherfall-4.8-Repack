-- Evaluated only when the options registry is first requested.
AF.Config:Register('EditorRender', function()
    return {{id='Movers', name='Movers', category='Movers', order=10,
        desc='Move native frames, keep exact positions and tune editor guides.', tabs={
        {id='General', name='General', groups={
            {name='Frame editor', options={
                {id='openMovers', label='Movers', kind='execute', execute=function() AF:Send('UnlockEditor') end, text='Unlock', order=1},
                {id='resetMovers', label='All positions', kind='execute', execute=function() AF:Send('ResetAllPositions') end, text='Reset', confirm=true, order=2},
                {id='moverLabels', label='Frame labels', kind='toggle', key='moverLabels', order=3},
            }},
            {name='Grid guides', options={
                {id='showgrid',label='Show grid',kind='toggle',key='showgrid',order=1},
                {id='guidesize',label='Guide spacing',kind='range',key='guidesize',min=16,max=128,step=16,unit=' px',order=2},
                {id='gridOpacity',label='Grid opacity',kind='range',key='gridOpacity',min=0,max=100,step=1,unit='%',order=3},
            }},
            {name='Snapping', options={
                {id='snap',label='Snap to grid',kind='toggle',key='snap',order=1},
                {id='snapframes',label='Snap to frames',kind='toggle',key='snapframes',order=2},
                {id='grid',label='Snap step',kind='range',key='grid',min=2,max=32,step=2,unit=' px',order=3},
                {id='snapDistance',label='Frame snap distance',kind='range',key='snapDistance',min=1,max=24,step=1,unit=' px',order=4},
            }},
        }}},
    }}
end)
