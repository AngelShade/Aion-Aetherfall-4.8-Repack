AF.Tags:Register('max', function(context)
    return AF.Tags:Number(context.maximum, context.abbreviate == 1)
end)
