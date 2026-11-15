AF.DataTexts:Register('kinah', {label = 'Kinah', text = function(context)
        local money = AF.Native.Number(AFCore.Money())
        return money and string.format(AFTranslate and AFTranslate('%s Kinah') or '%s Kinah', AF.Tags:Number(money, context.abbreviate == 1))
end})
