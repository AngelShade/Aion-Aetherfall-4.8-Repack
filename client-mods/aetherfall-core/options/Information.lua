AF.Config:Register('Lifecycle', function()
    local _, bridge = AFCore.Version()
    local version = AF.Native:Resource('version') or '1.3.0'
    return {{id='Information',name='Information / About',category='Information',order=12,desc='Aetherfall Interface version ' .. version,tabs={
        {id='About',name='About',order=1,groups={
            {name='Aetherfall Interface',order=1,options={
                {kind='header',label='Aetherfall Interface ' .. version,width='full',order=1},
                {kind='description',label=bridge or 'Native bridge unavailable',width='full',order=2},
                {kind='description',label='Options and profiles are modular. /afui opens settings. /moveui opens the anchor editor.',width='full',order=3},
            }},
            {name='Text tags',order=2,options={
                {kind='description',label='Data texts use [zone], [coords], [time], [xp], [kinah], [fps], [group], [legion], [level], [hp].',width='full',order=1},
                {kind='description',label='Resource labels use [current], [max] and [percent]. Unknown tags remain visible so typos are easy to find.',width='full',order=2},
            }},
        }},
        {id='Capabilities',name='Capabilities',order=2,groups={{name='Native dependencies',options={
            {kind='description',label='Unit-frame replacement requires verified gauge construction, unit event subscriptions and secure native targeting.',width='full',order=1},
            {kind='description',label='Nameplates require an engine draw and world-unit bridge. Cooldown overlays require verified slot geometry and text ownership.',width='full',order=2},
            {kind='description',label='Aura rows require verified icon rendering and native expiration data. Bags and chat styling require native inventory and chat integration.',width='full',order=3},
        }}}},
    }}}
end)
