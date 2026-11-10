AF.Tags:Register('current', function(context)
    return AF.Tags:Number(context.current, context.abbreviate == 1)
end)
