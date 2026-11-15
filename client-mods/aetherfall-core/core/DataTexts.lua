-- Passive sources are evaluated only by a subscribing display module.
local D = {}
AF.DataTexts = D
local sources = {}
function D:Register(name, spec)
    assert(type(name) == 'string' and not sources[name], 'duplicate data text: ' .. tostring(name))
    assert(type(spec.text) == 'function', 'data text needs a formatter')
    sources[name] = AF.Copy(spec)
end
-- One update cycle (HUD-owned) samples each source once for its context, so
-- columns and tags that repeat a source share the result. Cleared at cycle end.
local memo, memoContext
function D:BeginCycle(context) memo, memoContext = {}, context end
function D:EndCycle() memo, memoContext = nil, nil end
function D:Text(name, context)
    local source = sources[name]
    if not source then return end
    if memo and context == memoContext then
        local hit = memo[name]
        if hit then return hit[1] end
    end
    local ok, text = pcall(source.text, context or {})
    if not ok then text = nil end
    if memo and context == memoContext then memo[name] = {text} end
    return text
end
function D:Entries()
    local entries = {}
    for name, spec in pairs(sources) do entries[#entries + 1] = {name = name, label = spec.label or name} end
    table.sort(entries, function(a, b) return a.name < b.name end)
    return entries
end
