-- Deferred options owned by Shortcuts.
AF.Config:Register('Shortcuts', function()
    return {{name='Shortcuts',order=8,hint='Size, rows and spacing rebuild the grid. Move Shortcuts (all) as a group.',rows={
 {order=2,label='Shortcut size',kind='number',k='menusize',unit=' px',format='FormatOption'},
 {order=3,label='Shortcuts per row',kind='number',k='menucols',format='FormatOption'},
 {order=4,label='Shortcut spacing',kind='number',k='menugap',unit=' px',format='FormatOption'},
 {order=5,label='Align shortcuts',kind='action',action='alignmenu',text='Align'}}}}
end)
