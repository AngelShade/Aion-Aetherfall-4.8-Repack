-- Declarative options, evaluated on demand. No feature settings or widgets live here.
local C = {}
AF.Config = C
local providers, pages, legacyTrees = {}, nil, nil
local function ownerName(owner) return type(owner) == 'table' and owner.name or owner end
local function sort(items)
    table.sort(items, function(a, b)
        if (a.order or 100) == (b.order or 100) then return tostring(a.label or a.name or a.id) < tostring(b.label or b.name or b.id) end
        return (a.order or 100) < (b.order or 100)
    end)
end
local function invoke(owner, fn, ...)
    if type(fn) ~= 'function' then return fn end
    return AF:Call(owner, function(_, ...) return fn(...) end, ...)
end
local function copyOption(owner, row, index, page)
    local option = AF.Copy(row)
    option.owner, option.sourceKind = owner, row.kind
    option.order = option.order or index
    option.id = option.id or (page .. '.' .. owner .. '.' .. tostring(row.k or row.key or row.action or index))
    option.label = option.label or option.name or option.id
    option.key = option.key or option.k
    option.kind = ({number='range', barnumber='range', choice='select', data='select', profile='select', action='execute'})[row.kind] or row.kind
    return option
end
local function normalize(owner, tree, index)
    local page = AF.Copy(tree)
    page.id, page.name = tree.id or tree.name or (owner .. index), tree.name or tree.id
    page.category, page.owner = tree.category or tree.name or owner, owner
    page.desc = tree.desc or tree.hint or ''
    if tree.rows then
        local options = {}
        for i, row in ipairs(tree.rows) do options[i] = copyOption(owner, row, i, page.id) end
        page.tabs = {{id='General', name='General', groups={{name=tree.name, options=options}}}}
        page.legacy = true
    else
        page.tabs = page.tabs or {{id='General', name='General', groups=page.groups or {}}}
        for t, tab in ipairs(page.tabs) do
            tab.id, tab.name = tab.id or tab.name or tostring(t), tab.name or tab.id or tostring(t)
            tab.order = tab.order or t
            for g, group in ipairs(tab.groups or {}) do
                group.order = group.order or g
                local options = {}
                for i, option in ipairs(group.options or {}) do options[i] = copyOption(owner, option, i, page.id .. '.' .. tab.id) end
                sort(options); group.options = options
            end
            sort(tab.groups or {})
        end
    end
    for _, tab in ipairs(page.tabs) do
        for _, group in ipairs(tab.groups or {}) do sort(group.options) end
    end
    sort(page.tabs)
    return page
end
function C:Register(owner, trees)
    providers[#providers + 1] = {owner=ownerName(owner), source=trees}
    pages, legacyTrees = nil, nil
end
local function evaluate()
    if pages then return end
    -- Built aside and published at the end: an error part-way leaves no half-built cache.
    local built, legacy, byId, byLegacy = {}, {}, {}, {}
    for _, provider in ipairs(providers) do
        if not provider.evaluated then
            -- A factory that failed, or whose module is switched off, offers nothing this time;
            -- it is asked again when the registry is next rebuilt (C:Register).
            local trees = invoke(provider.owner, provider.source)
            provider.trees, provider.evaluated = type(trees) == 'table' and trees or nil, type(trees) == 'table'
        end
        local source = provider.trees
        for i, tree in ipairs(source or {}) do
            local page = normalize(provider.owner, tree, i)
            local existing = byId[page.id]
            if existing then
                for _, tab in ipairs(page.tabs) do
                    local target
                    for _, old in ipairs(existing.tabs) do if old.id == tab.id then target = old; break end end
                    if target then
                        for _, group in ipairs(tab.groups or {}) do target.groups[#target.groups+1] = group end
                        sort(target.groups)
                    else existing.tabs[#existing.tabs+1] = tab end
                end
                sort(existing.tabs)
                if page.desc ~= '' then existing.desc = page.desc end
            else
                byId[page.id], built[#built + 1] = page, page
            end
            if tree.rows then
                local old = byLegacy[tree.name]
                if not old then
                    old = {tree.name, {}, tree.hint, order=tree.order}
                    byLegacy[tree.name], legacy[#legacy + 1] = old, old
                end
                if tree.hint then old[3] = tree.hint end
                if tree.code then old.code = true end
                if tree.status then old.owner, old.status = provider.owner, tree.status end
                for r, row in ipairs(tree.rows) do
                    local option = copyOption(provider.owner, row, r, page.id)
                    old[2][#old[2] + 1] = {option.label, option}
                end
                table.sort(old[2], function(a,b) return (a[2].order or 100) < (b[2].order or 100) end)
            end
        end
    end
    sort(built); sort(legacy)
    pages, legacyTrees = built, legacy
end
function C:Pages() evaluate(); return AF.Copy(pages) end
function C:Trees() evaluate(); return AF.Copy(legacyTrees) end
function C:Key(option)
    if option.base then return option.base .. tostring(AF.DB:GetLegacy(option.selector)) end
    return option.key or option.k
end
function C:Rule(option)
    local key = self:Key(option)
    return key and AF.DB:Rule(option.owner, key) or nil
end
function C:Get(option)
    if option.get then return invoke(option.owner, option.get) end
    local key = self:Key(option)
    if not key then return end
    if option.k or option.base then return AF.DB:GetLegacy(key) end
    if AF.DB.Get then return AF.DB:Get(option.owner, key) end
    return AF.DB:GetLegacy(key)
end
function C:Set(option, value)
    if self:Disabled(option) or self:Hidden(option) then return false end
    if option.set then
        local result = invoke(option.owner, option.set, value)
        if result == nil then return true end
        return result
    end
    local ok, result = pcall(AF.DB.Set, AF.DB, option.owner, self:Key(option), value)
    if not ok then return {note='This value is not valid for ' .. option.label .. '.'} end
    return result
end
function C:Hidden(option) return not not invoke(option.owner, option.hidden or false) end
function C:Disabled(option) return not not invoke(option.owner, option.disabled or false) end
function C:Bounds(option)
    local rule = self:Rule(option) or {}
    return option.min or rule[3] or 0, option.max or rule[4] or 100, option.step or rule[5] or 1
end
function C:Choices(option)
    if option.choices then return invoke(option.owner, option.choices) or {} end
    local rule = self:Rule(option) or {}
    if option.labels then
        local labels = {}
        for i, value in ipairs(rule[3] or {}) do labels[i] = option.labels[value] or option.labels[i] or tostring(value) end
        return labels
    end
    return rule[3] or {}
end
function C:Values(option)
    if option.map then return AF.Copy(option.map) end
    local choices, values = self:Choices(option), {}
    local source = option.sourceKind or option.kind
    if source == 'data' or source == 'profile' then
        local rule = self:Rule(option) or {}
        return AF.Copy(rule[3] or choices)
    end
    local stringValues = type(self:Get(option)) == 'string'
    for i, label in ipairs(choices) do values[i] = stringValues and label or i end
    return values
end
function C:Index(option)
    local value = self:Get(option)
    for i, item in ipairs(self:Values(option)) do if item == value then return i end end
    return 1
end
function C:Display(option)
    if option.format then return AF:Call(option.owner, option.format, option) end
    local value = self:Get(option)
    if option.kind == 'toggle' then return (value == true or value == 1) and 'On' or 'Off' end
    if option.kind == 'range' then return tostring(value or '') .. (option.unit or '') end
    if option.kind == 'select' then return tostring(self:Choices(option)[self:Index(option)] or value or '') end
    if option.kind == 'execute' then return option.text or 'Run' end
    if option.kind == 'color' then
        if type(value) == 'string' then return value end
        local color = type(value) == 'table' and value or {1,1,1}
        return string.format('#%02X%02X%02X', math.floor((color[1] or color.r or 1)*255+.5), math.floor((color[2] or color.g or 1)*255+.5), math.floor((color[3] or color.b or 1)*255+.5))
    end
    return tostring(value or '')
end
function C:Change(option, part)
    local direction = part == 'Less' and -1 or 1
    if self:Disabled(option) or self:Hidden(option) then return false end
    if option.change then return AF:Call(option.owner, option.change, direction) end
    local value = self:Get(option)
    if option.kind == 'toggle' then
        if type(value) == 'boolean' then value = not value else value = value == 1 and 0 or 1 end
    elseif option.kind == 'range' then
        local low, high, step = self:Bounds(option)
        value = math.max(low, math.min(high, (tonumber(value) or low) + direction * step))
    elseif option.kind == 'select' then
        local values = self:Values(option)
        if #values == 0 then return false end
        value = values[(self:Index(option) - 1 + direction) % #values + 1]
    else return false end
    return self:Set(option, value)
end
function C:Action(option, confirming)
    if self:Disabled(option) or self:Hidden(option) then return end
    if option.confirm and not confirming then return {confirm=true, note='Click Confirm to continue.'} end
    if option.execute then return invoke(option.owner, option.execute) end
    if option.action then return AF:Call(option.owner, 'OnAction', option.action, option.arg, confirming) end
end
function C:Status(page)
    if page.owner and page.status then return AF:Call(page.owner, page.status) end
end
local function contains(value, query) return tostring(value or ''):lower():find(query, 1, true) ~= nil end
function C:Search(text)
    evaluate()
    local query, results = tostring(text or ''):lower(), {}
    if query == '' then return results end
    for _, page in ipairs(pages) do
        for _, tab in ipairs(page.tabs) do
            local path = page.category .. ' / ' .. page.name .. ' / ' .. tab.name
            local matched = contains(page.id, query) or contains(page.name, query) or contains(page.category, query) or contains(page.desc, query) or contains(tab.id, query) or contains(tab.name, query)
            local count = #results
            for _, group in ipairs(tab.groups or {}) do
                local groupMatch = contains(group.name, query) or contains(group.desc, query)
                for _, option in ipairs(group.options or {}) do
                    if not self:Hidden(option) and (groupMatch or contains(option.id, query) or contains(option.label, query) or contains(option.desc, query)) then
                        results[#results + 1] = {page=page.id, tab=tab.id, option=option.id, path=path .. ' / ' .. option.label, desc=option.desc or page.desc}
                    end
                end
            end
            if matched and count == #results then results[#results + 1] = {page=page.id, tab=tab.id, path=path, desc=page.desc} end
        end
    end
    return results
end
function C:Find(text)
    evaluate()
    local query = tostring(text or ''):lower()
    for _, page in ipairs(pages) do
        if page.id:lower() == query or page.name:lower() == query then return page.id, page.tabs[1] and page.tabs[1].id end
        for _, tab in ipairs(page.tabs) do
            if (page.id .. ' ' .. tab.id):lower() == query or (page.name .. ' ' .. tab.name):lower() == query then return page.id, tab.id end
        end
    end
    local first = self:Search(text)[1]
    if first then return first.page, first.tab, first.option end
end
