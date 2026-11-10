AF.DataTexts:Register('level', {label = 'Level', text = function(context)
        local level = AF.Native.Number(AF.Native.Call('UnitLevel', 'player')); return level and ('Level ' .. level)
end})
