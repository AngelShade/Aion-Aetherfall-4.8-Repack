-- Shareable data-only profiles. Every entry is validated before any mutation.
local DB, I = AF.DB, AF.DB._internal
local function shareSetting(key)
    return DB:Owner(key) ~= nil and DB:Tag(key) ~= 'profile' and DB:Tag(key) ~= 'share-skip'
end
local function settingScope(key)
    local owner, owned = DB:Owner(key)
    return DB:Scope(owner, owned)
end
local function envelope(version, body)
    local code = version .. '!' .. #body .. '!' .. body .. '!' .. I.checksum(body)
    if #code > 32768 then return nil, 'profile storage full' end
    return code
end
function DB:ProfileExport(scope, name)
    scope = scope or 'profile'
    if scope ~= 'all' and scope ~= 'profile' and scope ~= 'character' and scope ~= 'account' then return nil, 'Choose a sharing scope.' end
    local parts = {'n,' .. I.escape(name or self:ProfileName()), 'h,' .. scope}
    if scope == 'character' or scope == 'all' then
        for owner, moduleScope in pairs(I.scopes) do parts[#parts + 1] = 'm,' .. I.escape(owner) .. ',' .. moduleScope end
    end
    for _, key in self:Settings() do
        if shareSetting(key) then
            local keyScope = settingScope(key)
            if scope == 'all' or scope == keyScope then
                parts[#parts + 1] = 's,' .. keyScope .. ',' .. I.escape(key) .. ',' .. I.escape(self:GetLegacy(key))
            end
        end
    end
    if scope == 'all' or scope == 'profile' then
        local prefix = self:ProfileName() .. '.'
        for key, position in self:Positions() do
            if key:sub(1, #prefix) == prefix then
                local id = key:sub(#prefix + 1)
                parts[#parts + 1] = 'p,' .. id .. ',' .. string.format('%.4f,%.4f', position[1], position[2]) .. ',' .. (self:GetBinding(key) or '')
            end
        end
    end
    table.sort(parts)
    return envelope('AFUI2', table.concat(parts, ';'))
end
local function position(x, y, name)
    x, y = tonumber(x), tonumber(y)
    if not x or not y or x ~= x or y ~= y or x < -1 or y < -1 or x > 1 or y > 1 then return end
    if name ~= '' and not name:match('^[%w_]+$') then return end
    return {x, y, name ~= '' and name or nil}
end
local function parseLegacy(body)
    local result, seen = {settings={}, positions={}, legacy=true, name='DPS'}, {}
    if body == '' then return result end
    for entry in (body .. ';'):gmatch('(.-);') do
        local key, value = entry:match('^(%w+)=(.+)$')
        if not key or seen[key] then return nil, 'Bad or repeated entry in the code.' end
        seen[key] = true
        local id = key:match('^p(%d+)$')
        if id then
            if tonumber(id) > 65535 then return nil, 'Bad frame position in the code.' end
            local x, y, binding = value:match('^([^,]+),([^,]+),?([%w_]*)$')
            local parsed = position(x, y, binding or '')
            if not parsed then return nil, 'Bad frame position in the code.' end
            result.positions[id] = parsed
        elseif key == 'abbar' then
            if not tonumber(value) then return nil, 'Bad retired selector in the code.' end
        else
            local canonical = DB:Canonical(key == 'alpha' and 'windowsalpha' or key)
            if not canonical or not shareSetting(canonical) then return nil, 'Unknown setting ' .. key .. ' (made by a newer version?).' end
            local parsed = DB:Validate(canonical, value)
            if parsed == nil then return nil, 'Bad value for ' .. key .. ' in the code.' end
            result.settings[key == 'alpha' and 'alpha' or canonical] = {value=parsed, scope=settingScope(canonical)}
        end
    end
    local migrated = {}; for key, entry in pairs(result.settings) do migrated[key] = entry.value end
    DB:Migrate(migrated)
    result.settings.alpha = nil
    for key, value in pairs(migrated) do
        local canonical = DB:Canonical(key)
        if canonical and shareSetting(canonical) then result.settings[canonical] = {value=value, scope=settingScope(canonical)} end
    end
    return result
end
local function parseModern(body)
    local result, seen, hasName = {settings={}, positions={}, scopes={}}, {}, false
    for entry in (body .. ';'):gmatch('(.-);') do
        local row = {}
        for value in (entry .. ','):gmatch('(.-),') do
            local decoded = I.unescape(value)
            if decoded == nil then return nil, 'Bad encoded text in the code.' end
            row[#row + 1] = decoded
        end
        if row[1] == 'n' and #row == 2 and not hasName and DB:ValidProfileName(row[2]) then
            result.name, hasName = row[2], true
        elseif row[1] == 'h' and #row == 2 and not result.include and
            (row[2] == 'all' or row[2] == 'profile' or row[2] == 'character' or row[2] == 'account') then
            result.include = row[2]
        elseif row[1] == 'm' and #row == 3 and I.schemas[row[2]] and not result.scopes[row[2]] and
            (row[3] == 'profile' or row[3] == 'character' or row[3] == 'account') then
            result.scopes[row[2]] = row[3]
        elseif row[1] == 's' and #row == 4 then
            local canonical = DB:Canonical(row[3])
            if not canonical or not shareSetting(canonical) then return nil, 'Unknown setting ' .. row[3] .. ' (made by a newer version?).' end
            if seen['s' .. canonical] then return nil, 'Repeated setting in the code.' end
            seen['s' .. canonical] = true
            if row[2] ~= 'profile' and row[2] ~= 'character' and row[2] ~= 'account' then return nil, 'Bad setting scope in the code.' end
            local owner = I.owners[canonical]
            local fixedScope = I.rules[canonical].scope
            if not fixedScope and I.schemas[owner[1]].scope ~= 'profile' then fixedScope = I.schemas[owner[1]].scope end
            if fixedScope and row[2] ~= fixedScope then
                return nil, 'Setting scope differs from the schema for ' .. canonical .. '.'
            end
            local parsed = DB:Validate(canonical, row[4])
            if parsed == nil then return nil, 'Bad value for ' .. canonical .. ' in the code.' end
            result.settings[canonical] = {scope=row[2], value=parsed}
        elseif row[1] == 'p' and #row == 5 and row[2]:match('^%d+$') and tonumber(row[2]) <= 65535 then
            if seen['p' .. row[2]] then return nil, 'Repeated frame position in the code.' end
            seen['p' .. row[2]] = true
            local parsed = position(row[3], row[4], row[5])
            if not parsed then return nil, 'Bad frame position in the code.' end
            result.positions[row[2]] = parsed
        else return nil, 'Bad profile entry in the code.' end
    end
    if not hasName then return nil, 'The code has no profile name.' end
    if next(result.scopes) and result.include ~= 'all' and result.include ~= 'character' then return nil, 'Module scope data needs character scope.' end
    for _, entry in pairs(result.settings) do
        if result.include and result.include ~= 'all' and result.include ~= entry.scope then return nil, 'The code contains an undeclared scope.' end
    end
    if next(result.positions) and result.include and result.include ~= 'all' and result.include ~= 'profile' then return nil, 'Positions need profile scope.' end
    return result
end
function DB:ProfileDecode(code)
    if type(code) ~= 'string' or #code > 32768 then return nil, 'The profile code is too large.' end
    code = code:match('^%s*(.-)%s*$')
    if code:match('^AFUI1!') then code = code:gsub('%s+', '') end
    local version, size, body, sum = code:match('^(AFUI[12])!(%d+)!(.*)!(%x%x%x%x)$')
    if not version then return nil, 'Not a complete Aetherfall Interface code.' end
    if #body ~= tonumber(size) or I.checksum(body) ~= sum:lower() then return nil, 'The code is incomplete or changed; copy all of it.' end
    if version == 'AFUI1' then return parseLegacy(body) end
    return parseModern(body)
end
function DB:ProfileImport(code, options)
    options = options or {}
    local parsed, why = self:ProfileDecode(code)
    if not parsed then return nil, why end
    local included = {}
    if parsed.include == 'all' then included.profile, included.character, included.account = true, true, true
    elseif parsed.include then included[parsed.include] = true end
    for _, entry in pairs(parsed.settings) do
        if not options.onlyScope or entry.scope == options.onlyScope then included[entry.scope] = true end
    end
    if parsed.legacy then
        for _, key in ipairs(I.wire) do if shareSetting(key) then included[settingScope(key)] = true end end
    end
    return self:ProfileTransaction(function()
        if not parsed.legacy and not options.preserveName then
            if not self:ProfileExists(parsed.name) and #self:ProfileList() >= self:ProfileLimit() then return nil, 'profile storage full' end
            if parsed.name ~= self:ProfileName() then
                self:ProfileRemember()
                local ok, error = self:ProfileRestore(parsed.name)
                if not ok then return nil, error end
                self:ProfileValue(I.profileKey, parsed.name)
            end
        end
        if not options.onlyScope or options.onlyScope == 'character' then
            for owner, scope in pairs(parsed.scopes or {}) do
                local ok, error = self:SetModuleScope(owner, scope); if not ok then return nil, error end
            end
        end
        for _, key in ipairs(I.wire) do
            if shareSetting(key) then
                local scope = settingScope(key)
                if included[scope] and (not options.onlyScope or scope == options.onlyScope) then
                    I.bucket(key)[key] = I.defaults[key]
                    if scope == 'account' then I.accountDirty = true end
                end
            end
        end
        for key, entry in pairs(parsed.settings) do
            if not options.onlyScope or entry.scope == options.onlyScope then
                local target = entry.scope == 'account' and I.account.values or entry.scope == 'character' and I.character or I.values
                target[key] = entry.value
                if entry.scope == 'account' then I.accountDirty = true end
            end
        end
        if included.profile or next(parsed.positions) then
            self:ProfileClearPositions(self:ProfileName())
            for id, saved in pairs(parsed.positions) do
                local key = self:ProfileName() .. '.' .. id
                self:SetPosition(key, {saved[1], saved[2]}); self:SetBinding(key, saved[3])
            end
        end
        self:ProfileRemember(); return true
    end)
end
