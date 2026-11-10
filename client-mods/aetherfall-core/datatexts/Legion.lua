AF.DataTexts:Register('legion', {label = 'Legion', text = function(context)
        local count = AF.Native.Number(AF.Native.Call('GetNumLegionMembers')); return count and count > 0 and ('Legion ' .. count) or nil
end})
