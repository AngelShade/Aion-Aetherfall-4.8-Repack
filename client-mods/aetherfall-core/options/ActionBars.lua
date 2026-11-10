-- One page per schema instance. No widget creation or feature state lives here.
AF.Config:Register('ActionBars', function()
    local pages = {{id = 'Action Bars', name = 'Action Bars', category = 'Action Bars', order = 3,
        desc = 'Six native action bars. Select a bar to change its own layout.',
        tabs = {{id = 'General', name = 'General', groups = {{name = 'Action bars', options = {
            {kind = 'description', label = 'Use the child pages for independent bars. Background opacity can inherit the group value.', width = 'full'},
        }}}}}}}
    for i = 1, 6 do
        local index = i
        local prefix = 'bar' .. i .. '.'
        local function disabled() return AF:Call('ActionBars', 'IsEnabled', index) == 0 end
        local controls = {
            {id = prefix .. 'enabled', kind = 'toggle', label = 'Enable', key = prefix .. 'enabled', order = 1,
             desc = 'Hide or restore this native action bar using the visibility controller.'},
            {id = prefix .. 'opacity', kind = 'range', label = 'Background opacity', key = prefix .. 'opacity',
             min = -1, max = 100, step = 1, unit = '%', order = 4, disabled = disabled,
             desc = '-1 uses the Action Bars group opacity. Text and skill icons stay solid.'},
            {id = prefix .. 'mover', kind = 'execute', label = 'Move bar', order = 5,
             execute = function() AF:Call('ActionBars', 'Arrange', index) end,
             desc = 'Open the mover editor with this bar selected.'},
        }
        if i <= 4 then
            controls[#controls + 1] = {id = prefix .. 'rows', kind = 'range', label = 'Buttons per row',
                key = prefix .. 'rows', min = 1, max = 12, step = 1, order = 2, disabled = disabled,
                desc = 'Wrap twelve buttons into rows. Narrow viewports can temporarily reduce the columns.'}
            controls[#controls + 1] = {id = prefix .. 'size', kind = 'range', label = 'Button size',
                key = prefix .. 'size', min = 28, max = 56, step = 2, unit = ' px', order = 3, disabled = disabled}
        else
            controls[#controls + 1] = {kind = 'description', order = 6, width = 'full',
                label = 'Rows and button size use native geometry for separated bars.'}
        end
        pages[#pages + 1] = {id = 'Bar' .. i, name = 'Bar ' .. i, category = 'Action Bars',
            parent = 'Action Bars', order = 3 + i / 100, tabs = {{id = 'General', name = 'General',
                groups = {{name = 'Bar ' .. i, options = controls}}}}}
    end
    return pages
end)
