AF.Config:Register('TargetDetails', function()
    local function unavailable() return not AF:Call('TargetDetails', 'Available') end
    return {{id='Unit Frames',name='Unit Frames',category='Unit Frames',order=2,tabs={
        {id='Text',name='Text',order=3,groups={
            {name='Resource formats',options={
                {kind='input',key='healthFormat',label='Target health',width=2,disabled=unavailable,order=1},
                {kind='input',key='manaFormat',label='Target mana',width=2,disabled=unavailable,order=2},
                {kind='input',key='dpFormat',label='Target DP',width=2,disabled=unavailable,order=3},
                {kind='input',key='assistFormat',label='Target of target health',width=2,disabled=unavailable,order=4},
                {kind='toggle',key='targetAbbreviate',label='Abbreviate numbers',disabled=unavailable,order=5},
                {kind='description',label='Tags [current], [max] and [percent] format the existing native resource labels. Requires the installed target readout surface.',width='full',order=6},
            }},
        }},
    }}}
end)
