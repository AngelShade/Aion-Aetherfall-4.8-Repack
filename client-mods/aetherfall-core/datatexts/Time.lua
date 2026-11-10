AF.DataTexts:Register('time', {label = 'Time', text = function(context)
        local time = AF.Native.Call('GetTime')
        if not time then return end
        if (context.clockMode or 1) == 1 then return tostring(time) end
        local hours, minutes, seconds = tostring(time):match('(%d+):(%d+):?(%d*)')
        if not hours then return tostring(time) end
        hours, minutes, seconds = tonumber(hours), tonumber(minutes), tonumber(seconds)
        local suffix = ''
        if context.clockMode == 2 then
            suffix = hours >= 12 and ' PM' or ' AM'
            hours = hours % 12; if hours == 0 then hours = 12 end
        end
        return string.format('%02d:%02d', hours, minutes) ..
               (context.clockSeconds == 1 and seconds and string.format(':%02d', seconds) or '') .. suffix
end})
