AF.Config:Register('WorldMap', function()
    return {{id='Minimap',name='Minimap',category='Minimap',order=7,tabs={{id='WorldMap',name='World map',order=3,groups={
        {name='Saved position',options={
            {kind='range',key='mapx',label='Horizontal position',min=-1,max=1,step=.01,order=1,desc='Fraction of screen width. -1 leaves native placement in charge.'},
            {kind='range',key='mapy',label='Vertical position',min=-1,max=1,step=.01,order=2,desc='Fraction of screen height. -1 leaves native placement in charge.'},
            {kind='description',label='Both coordinates must be at least zero to restore this position. Native dragging continues to remember the map position.',width='full',order=3},
        }},
    }}}}}
end)
