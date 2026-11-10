-- A safe plain-text formatter. Formats are data and are never executed as Lua.
local T = {}
AF.Tags = T
local tags = {}
function T:Register(name, formatter)
    assert(not tags[name] and type(formatter) == 'function', 'invalid or duplicate text tag')
    tags[name] = formatter
end
function T:Format(format, context)
    if type(format) ~= 'string' then return '' end
    return (format:sub(1, 512):gsub('%[([%w:_-]+)%]', function(name)
        local formatter = tags[name]
        if not formatter then return '[' .. name .. ']' end
        local ok, value = pcall(formatter, context or {})
        return ok and tostring(value or '') or ''
    end))
end
function T:Number(value, abbreviated)
    value = AF.Native.Number(value)
    if not value then return '' end
    if abbreviated and math.abs(value) >= 1000000 then return string.format('%.1fm', value / 1000000) end
    if abbreviated and math.abs(value) >= 1000 then return string.format('%.1fk', value / 1000) end
    local text = string.format('%d', math.floor(value + .5))
    while true do
        local changed
        text, changed = text:gsub('^(-?%d+)(%d%d%d)', '%1,%2')
        if changed == 0 then return text end
    end
end
