AF.Config:Register('Companions', function()
    return {{id='Shortcuts',name='Shortcuts',category='Shortcuts',order=8,tabs={{id='Companions',name='Companions',order=2,groups={
        {name='Companion commands',options={
            {kind='toggle',key='botbar',label='Enable companion bar',order=1,desc='The server and companion controller still determine when commands are available.'},
            {kind='description',label='Expand, collapse and icon mode are controlled by the native companion bar buttons and remembered automatically.',width='full',order=2},
        }},
    }}}}}
end)
