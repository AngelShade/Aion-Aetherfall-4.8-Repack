AF.Config:Register('EventAssistant', function()
    return {{id='General', name='General', category='General', order=1, tabs={{id='World Events', name='World Events', order=9, groups={
        {name='World Event assistant', options={
            {kind='toggle', key='enabled', label='Show the World Event assistant', order=1,
             desc='A panel with the world event around you: its part, objective, progress, time left and your medal so far.'},
            {kind='range', key='offsetX', label='Left/right', unit=' px', order=2, desc='Negative moves left, positive right. 0 is the automatic place.'},
            {kind='range', key='offsetY', label='Up/down', unit=' px', order=3, desc='Negative moves up, positive down. 0 is the automatic place.'},
            {kind='description', label='The automatic place is the top right, below the minimap. The event also stays in your quest journal and tracker.', width='full', order=4},
        }},
    }}}}}
end)
