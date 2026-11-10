AF.DataTexts:Register('kinah', {label = 'Kinah', text = function(context)
        local money = AF.Native.Number(AFCore.Money()); return money and (AF.Tags:Number(money, context.abbreviate == 1) .. ' Kinah')
end})
