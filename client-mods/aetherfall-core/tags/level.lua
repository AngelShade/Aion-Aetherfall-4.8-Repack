AF.Tags:Register('level', function(context)
    if context.level ~= nil then return context.level end
    return AF.DataTexts:Text('level', context)
end)
