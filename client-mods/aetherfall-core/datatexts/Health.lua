AF.DataTexts:Register('hp', {label = 'Health', text = function(context)
        local hp = AF.Native.Number(AF.Native.Call('UnitHP', 'player'))
        local maximum = AF.Native.Number(AF.Native.Call('UnitHPMax', 'player'))
        return hp and maximum and maximum > 0 and string.format('HP %s / %s', AF.Tags:Number(hp, context.abbreviate == 1), AF.Tags:Number(maximum, context.abbreviate == 1)) or nil
end})
