AF.DataTexts:Register('zone', {label = 'Zone', text = function(context)
        local zone = AF.Native.Call('GetZoneText'); return zone and tostring(zone)
end})
