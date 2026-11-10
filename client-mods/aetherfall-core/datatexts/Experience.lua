AF.DataTexts:Register('xp', {label = 'Experience', text = function(context)
        local cur, maximum, recoverable, repose, reposeMax = AFCore.Exp()
        cur, maximum = AF.Native.Number(cur), AF.Native.Number(maximum)
        if not cur or not maximum or maximum <= 0 then return end
        local text = string.format('XP %.1f%%', 100 * cur / maximum)
        if AF.Native.Number(recoverable) and recoverable > 0 then text = text .. string.format('  (recoverable %.1f%%)', 100 * recoverable / maximum) end
        if AF.Native.Number(repose) and AF.Native.Number(reposeMax) and reposeMax > 0 and repose > 0 then
            text = text .. string.format('  repose %d%%', math.floor(100 * repose / reposeMax + .5))
        end
        return text
end})
