AF.DataTexts:Register('coords', {label = 'Coordinates', text = function(context)
        local x, y = AF.Native.Call('GetPlayerMapPosition', 'player')
        if AF.Native.Number(x) and AF.Native.Number(y) then
            local decimals = context.coordDecimals or 0
            if decimals == 0 then return string.format('%d, %d', math.floor(x + .5), math.floor(y + .5)) end
            return string.format('%.' .. decimals .. 'f, %.' .. decimals .. 'f', x, y)
        end
end})
