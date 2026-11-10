AF.DataTexts:Register('group', {label = 'Group', text = function(context)
        local count = AF.Native.Number(AF.Native.Call('GetNumPartyMembers')); return count and count > 0 and ('Group ' .. count) or nil
end})
