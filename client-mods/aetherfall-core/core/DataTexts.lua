-- Passive sources are evaluated only by a subscribing display module.
local D = {}
AF.DataTexts = D
local sources = {}
function D:Register(name, spec)
    assert(type(name) == 'string' and not sources[name], 'duplicate data text: ' .. tostring(name))
    assert(type(spec.text) == 'function', 'data text needs a formatter')
    sources[name] = AF.Copy(spec)
end
function D:Text(name, context)
    local source = sources[name]
    if not source then return end
    local ok, text = pcall(source.text, context or {})
    if ok then return text end
end
function D:Entries()
    local entries = {}
    for name, spec in pairs(sources) do entries[#entries + 1] = {name = name, label = spec.label or name} end
    table.sort(entries, function(a, b) return a.name < b.name end)
    return entries
end
