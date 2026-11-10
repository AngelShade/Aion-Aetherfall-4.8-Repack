AF.Tags:Register('percent', function(context)
    return context.maximum and context.maximum > 0 and string.format('%.1f%%', 100 * (context.current or 0) / context.maximum) or ''
end)
