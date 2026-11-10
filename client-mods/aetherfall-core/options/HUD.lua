-- Deferred HUD option trees. Every key is owned by HUD.
AF.Config:Register('HUD', function()
    local labels, values = {}, {}
    for _, entry in ipairs(AF.DataTexts:Entries()) do
        labels[#labels + 1], values[#values + 1] = entry.label, entry.name
    end
    labels[#labels + 1], values[#values + 1] = 'Off', 'off'
    local slots = {}
    for index, key in ipairs({'dl1','dl2','dl3','dc1','dc2','dc3','dr1','dr2','dr3'}) do
        slots[#slots + 1] = {kind='select', key=key, label=({'Left','Centre','Right'})[math.ceil(index/3)] .. ' ' .. ((index-1)%3+1),
            choices=labels, map=values, order=index, desc='Choose the registered source shown in this data text slot.'}
    end
    for index, key in ipairs({'dlsep1','dlsep2','dlsep3','dcsep1','dcsep2','dcsep3','drsep1','drsep2','drsep3'}) do
        slots[#slots + 1] = {kind='input', key=key, label=({'Left','Centre','Right'})[math.ceil(index/3)] .. ' ' .. ((index-1)%3+1) .. ' separator',
            order=index + .5, desc='Text after this occupied slot, including at the end. Empty inherits the panel separator between occupied slots.'}
    end
    return {
        {id='General', name='General', category='General', order=1, tabs={
            {id='Appearance', name='Appearance', order=1, groups={
                {name='Fonts', order=1, options={{kind='select', key='font', label='Data text font', order=1}}},
                {name='Colors', order=2, options={
                    {kind='select', key='theme', label='Colour theme', order=1},
                    {kind='color', key='customAccent', label='Custom accent', order=2,
                     desc='Pick your own accent colour. Choosing one switches the colour theme to Custom.'},
                }},
            }},
        }},
        {id='Chat', name='Chat', category='Chat', order=4, tabs={
            {id='Status', name='Status line', order=1, groups={
                {name='Status line', order=1, options={
                    {kind='toggle', key='chatline', label='Enable status line', order=1},
                    {kind='input', key='statusFormat', label='Text format', width='full', order=2,
                     desc='Tags [level], [hp], [xp], [zone], [coords], [time], [kinah], [fps], [group], [legion].'},
                }},
                {name='Geometry', order=2, options={
                    {kind='range', key='statusWidth', label='Width', unit=' px', order=1},
                    {kind='range', key='statusHeight', label='Height', unit=' px', order=2},
                    {kind='range', key='statusX', label='Left offset', unit=' px', order=3},
                    {kind='range', key='statusBottom', label='Bottom offset', unit=' px', order=4},
                }},
            }},
        }},
        {id='Data Texts', name='Data Texts', category='Data Texts', order=5, tabs={
            {id='Panel', name='Panel', order=1, groups={
                {name='Data panel', order=1, options={
                    {kind='toggle', key='databar', label='Enable panel', order=1},
                    {kind='select', key='dataPosition', label='Position', order=2},
                    {kind='range', key='dataHeight', label='Height', unit=' px', order=3},
                    {kind='range', key='dataWidth', label='Width', unit=' px', order=4,
                     desc='0 fills the screen. A custom width is centred and fitted between 120 pixels and the screen width.'},
                    {kind='input', key='separator', label='Slot separator', order=5, desc='Plain text inserted between occupied slots.'},
                }},
            }},
            {id='Slots', name='Slots', order=2, groups={{name='Data text sources', options=slots}}},
            {id='Formats', name='Formats', order=3, groups={
                {name='Clock', order=1, options={
                    {kind='select', key='clockMode', label='Time format', order=1},
                    {kind='toggle', key='clockSeconds', label='Show supplied seconds', order=2,
                     desc='Includes seconds when the stock time source supplies them. Native preserves the game time string.'},
                }},
                {name='Numbers', order=2, options={
                    {kind='range', key='coordDecimals', label='Coordinate decimals', order=1},
                    {kind='toggle', key='abbreviate', label='Abbreviate numbers', order=2,
                     desc='Use k and m for health and Kinah in data texts.'},
                }},
            }},
        }},
        {id='Minimap', name='Minimap', category='Minimap', order=7, tabs={
            {id='Information', name='Information', order=2, groups={{name='Information label', options={
                {kind='toggle', key='minimapinfo', label='Enable label', order=1},
                {kind='select', key='mapPosition', label='Position', order=2},
                {kind='input', key='mapFormat', label='Text format', width='full', order=3,
                 desc='Use data text tags such as [coords], [zone] and [time].'},
            }}}},
        }},
    }
end)
