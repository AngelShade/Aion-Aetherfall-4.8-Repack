-- Deferred options owned by Minimap.
AF.Config:Register('Minimap', function()
    return {{name='Minimap',order=7,hint='Move the map and its zoom, clock and zone name with /moveui.',rows={
 {order=2,label='Minimap size',kind='number',k='mapsize',unit=' px'},
 {order=3,label='Align map controls',kind='action',action='alignmap',text='Align'}}}}
end)
