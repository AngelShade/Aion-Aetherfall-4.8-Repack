-- Bounded, checksummed data records. No executable Lua is loaded from disk.
local DB, I = AF.DB, AF.DB._internal
local LIMIT = 32768
local function escape(value)
    return (tostring(value):gsub('[^%w _%.%-]', function(char) return string.format('%%%02X', char:byte()) end))
end
local function unescape(value)
    if value:gsub('%%%x%x', ''):find('%%') then return end
    local result = value:gsub('%%(%x%x)', function(hex) return string.char(tonumber(hex, 16)) end)
    if result:find('[%z\1-\31\127]') then return end
    return result
end
local function checksum(body)
    local sum = 0
    for index = 1, #body do sum = (sum * 31 + body:byte(index)) % 65521 end
    return string.format('%04x', sum)
end
I.escape, I.unescape, I.checksum = escape, unescape, checksum
local function record(lines, ...)
    local fields = {...}
    for index, value in ipairs(fields) do fields[index] = escape(value) end
    lines[#lines + 1] = table.concat(fields, '|')
end
local function envelope(lines, sequence, kind)
    local body = table.concat(lines, '\n')
    local data = 'AFUI2 ' .. sequence .. ' ' .. kind .. '\n' .. body .. '\nCHECK ' .. checksum(body) .. '\nEND\n'
    if #data > LIMIT then return nil, 'profile storage full' end
    return data
end
local function profileCapacity(state, kind)
    local names, count = {}, 0
    if kind == 'character' then
        local active = state.character[I.profileKey] or state.values[I.profileKey]
        if active and not names[active] then names[active] = true; count = count + 1 end
    end
    for name in pairs(kind == 'account' and state.profiles or state.stored) do
        if not DB:ValidProfileName(name) then return nil, 'Invalid saved profile name.' end
        if not names[name] then names[name] = true; count = count + 1 end
    end
    if count > DB:ProfileLimit() then return nil, 'profile storage full' end
    return true
end
function DB:StorageEncode(state, sequence, kind)
    local valid, why = profileCapacity(state, kind)
    if not valid then return nil, why end
    local lines = {}
    local function settings(scope, values)
        for _, key in ipairs(I.wire) do
            if values[key] ~= nil then record(lines, 's', scope, key, values[key]) end
        end
    end
    if kind == 'account' then
        settings('account', state.values)
        for name, value in pairs(state.profiles) do record(lines, 'r', name, value) end
    else
        settings('profile', state.values); settings('character', state.character)
        for owner, scope in pairs(state.scopes) do record(lines, 'm', owner, scope) end
        for key, position in pairs(state.positions) do
            record(lines, 'p', key, string.format('%.5f', position[1]), string.format('%.5f', position[2]), state.bindings[key] or '')
        end
        for name, position in pairs(state.windows) do
            record(lines, 'w', name, string.format('%.5f', position[1]), string.format('%.5f', position[2]))
        end
        for name, value in pairs(state.stored) do record(lines, 'r', name, value) end
    end
    table.sort(lines)
    return envelope(lines, sequence, kind)
end
local function fields(line)
    local result = {}
    for value in (line .. '|'):gmatch('(.-)|') do
        local decoded = unescape(value); if decoded == nil then return end
        result[#result + 1] = decoded
    end
    return result
end
local function coordinate(value, low)
    local number = tonumber(value)
    if number and number == number and number >= low and number <= 1 then return number end
end
local function positionKey(key)
    local profile, id = key:match('^(.*)%.(%d+)$')
    return profile and DB:ValidProfileName(profile) and tonumber(id) <= 65535
end
local function stateNew() return {values={}, character={}, scopes={}, positions={}, bindings={}, windows={}, stored={}} end
local function decodeV2(data, expected)
    local sequence, kind, body, sum = data:match('^AFUI2 (%d+) (%w+)\n(.*)\nCHECK (%x%x%x%x)\nEND\n$')
    sequence = tonumber(sequence)
    if not sequence or sequence > 9007199254740990 or kind ~= expected or checksum(body) ~= sum:lower() then return end
    local state = kind == 'account' and {values={}, profiles={}} or stateNew()
    local seen, count = {}, 0
    for line in body:gmatch('[^\n]+') do
        local row = fields(line); if not row then return end
        local unique = (row[1] or '') .. '|' .. (row[2] or '') .. '|' .. (row[1] == 's' and row[3] or '')
        if seen[unique] then return end
        seen[unique] = true
        if row[1] == 's' and #row == 4 then
            local canonical = DB:Canonical(row[3])
            if not canonical then return end
            local value = DB:Validate(canonical, row[4]); if value == nil then return end
            if kind == 'account' then
                if row[2] ~= 'account' then return end
                state.values[canonical] = value
            elseif row[2] == 'profile' then state.values[canonical] = value
            elseif row[2] == 'character' then state.character[canonical] = value
            else return end
        elseif row[1] == 'm' and #row == 3 and kind == 'character' then
            if not I.schemas[row[2]] or (row[3] ~= 'profile' and row[3] ~= 'character' and row[3] ~= 'account') then return end
            state.scopes[row[2]] = row[3]
        elseif row[1] == 'p' and #row == 5 and kind == 'character' then
            local x, y = coordinate(row[3], -1), coordinate(row[4], -1)
            if not x or not y or not positionKey(row[2]) or (row[5] ~= '' and not row[5]:match('^[%w_]+$')) then return end
            state.positions[row[2]] = {x, y}; state.bindings[row[2]] = row[5] ~= '' and row[5] or nil
        elseif row[1] == 'w' and #row == 4 and kind == 'character' then
            local x, y = coordinate(row[3], 0), coordinate(row[4], 0)
            if not x or not y or not row[2]:match('^[%w_]+$') then return end
            state.windows[row[2]] = {x, y}
        elseif row[1] == 'r' and #row == 3 then
            if not DB:ValidProfileName(row[2]) then return end
            local parsed
            if kind == 'account' then parsed = DB:ProfileDecode(row[3]) else parsed = DB:SnapshotParse(row[3]) end
            if not parsed then return end
            count = count + 1; if count > DB:ProfileLimit() then return end
            if kind == 'account' then state.profiles[row[2]] = row[3] else state.stored[row[2]] = row[3] end
        else return end
    end
    if not profileCapacity(state, kind) then return end
    return sequence, state
end
local function decodeLegacy(data)
    local sequence, body = data:match('^AFUI1 (%d+)\n(.*)END\n$')
    sequence = tonumber(sequence)
    if not sequence or sequence > 9007199254740990 then return end
    local state, settings, seen = stateNew(), {}, {}
    for line in body:gmatch('[^\n]+') do
        local key, value = line:match('^([^=]+)=(.*)$')
        if not key or seen[key] then return end
        seen[key] = true
        local setting = key:match('^set%.(.+)$')
        local binding = key:match('^bind%.(.+)$')
        local window = key:match('^win%.([%w_]+)$')
        local profile = key:match('^store%.(.+)$')
        if setting then
            local canonical = DB:Canonical(setting == 'alpha' and 'windowsalpha' or setting)
            if canonical then
                local validated = DB:Validate(canonical, value); if validated == nil then return end
                settings[setting == 'alpha' and 'alpha' or canonical] = validated
            elseif setting ~= 'abbar' then return end
        elseif binding then
            if not positionKey(binding) or not value:match('^[%w_]+$') then return end
            state.bindings[binding] = value
        elseif profile then
            if not DB:ValidProfileName(profile) or not DB:SnapshotParse(value, true) then return end
            state.stored[profile] = value
        elseif window or positionKey(key) then
            local x, y = value:match('^([^,]+),([^,]+)$')
            x, y = coordinate(x, window and 0 or -1), coordinate(y, window and 0 or -1)
            if not x or not y then return end
            if window then state.windows[window] = {x, y} else state.positions[key] = {x, y} end
        else return end
    end
    DB:Migrate(settings)
    for key, value in pairs(settings) do
        local owner, owned = DB:Owner(key)
        local scope = DB:Scope(owner, owned)
        if scope == 'account' then state.accountValues = state.accountValues or {}; state.accountValues[key] = value
        elseif scope == 'character' then state.character[key] = value else state.values[key] = value end
    end
    DB:ProfileSeed(state.stored)
    if not profileCapacity(state, 'character') then return end
    state.legacy = true
    return sequence, state
end
function DB:StorageDecode(data, kind)
    if type(data) ~= 'string' or #data > LIMIT or #data == 0 then return end
    if data:sub(1, 6) == 'AFUI1 ' and kind == 'character' then return decodeLegacy(data) end
    return decodeV2(data, kind)
end
function DB:StorageReadPair(base, kind)
    local best, state = -1, nil
    for slot = 0, 1 do
        local sequence, candidate = self:StorageDecode(self:Read(base .. '.' .. slot .. '.cfg'), kind)
        if sequence and sequence > best then best, state = sequence, candidate end
    end
    return best < 0 and 0 or best, state
end
function DB:StorageFits()
    local character, why = self:StorageEncode(I, I.sequence + 1, 'character')
    if not character then return nil, why end
    local account, error = self:StorageEncode(I.account, I.accountSequence + 1, 'account')
    return account and true or nil, error
end
local function writePair(base, sequence, data, kind)
    local name = base .. '.' .. (sequence % 2) .. '.cfg'
    if not DB:Write(name, data) then return false end
    local saved = DB:Read(name)
    local savedSequence = DB:StorageDecode(saved, kind)
    return savedSequence == sequence and saved == data
end
local function indexRead()
    local data = DB:Read('afui-characters.cfg')
    if not data or #data > LIMIT then return {} end
    local body, sum = data:match('^AFUI2 INDEX\n(.*)\nCHECK (%x%x%x%x)\nEND\n$')
    if not body or checksum(body) ~= sum:lower() then return {} end
    local entries = {}
    for line in body:gmatch('[^\n]+') do
        local row = fields(line)
        if not row or #row ~= 3 or row[1] ~= 'c' or not row[2]:match('^ui%-%x+$') or #row[2] ~= 43 or #row[3] > 128 then return {} end
        if entries[row[2]] then return {} end
        entries[row[2]] = row[3]
    end
    return entries
end
function DB:ProfileCharacters()
    local result = {}
    for file, label in pairs(indexRead()) do result[#result + 1] = {file=file, label=label} end
    table.sort(result, function(a, b) return a.label < b.label end)
    return result
end
local function indexSave(identity)
    local entries, lines, files = indexRead(), {}, {}
    entries[I.filename] = identity:sub(1, 128):gsub('[%z\1-\31\127]', '')
    record(lines, 'c', I.filename, entries[I.filename])
    for file in pairs(entries) do if file ~= I.filename then files[#files + 1] = file end end
    table.sort(files)
    for index = 1, math.min(99, #files) do record(lines, 'c', files[index], entries[files[index]]) end
    table.sort(lines)
    local body = table.concat(lines, '\n')
    local data = 'AFUI2 INDEX\n' .. body .. '\nCHECK ' .. checksum(body) .. '\nEND\n'
    if #data <= LIMIT then DB:Write('afui-characters.cfg', data) end
end
function DB:Load(identity)
    self:Reset()
    assert(type(identity) == 'string' and identity ~= '', 'character is not connected')
    I.filename = 'ui-' .. sha1.sha1(identity); I.path = AFCore.ModPath(I.filename)
    I.accountSequence, I.account = self:StorageReadPair('afui-account', 'account')
    I.account = I.account or {values={}, profiles={}}
    local state; I.sequence, state = self:StorageReadPair(I.filename, 'character')
    if state then
        for _, key in ipairs({'values','character','scopes','positions','bindings','windows','stored'}) do I[key] = state[key] end
        for key, value in pairs(state.accountValues or {}) do
            if I.account.values[key] == nil then I.account.values[key] = value; I.accountDirty = true end
        end
        if state.legacy then self:ProfileSeed() end
    else
        self:ProfileSeed()
        for _, owner in ipairs(AF:Names()) do
            for key, value in pairs((I.schemas[owner] or {}).fresh or {}) do
                local old = I.legacy[owner .. '.' .. key]; if old then I.bucket(old)[old] = value end
            end
        end
    end
    indexSave(identity)
end
function DB:Save()
    if I.draft and not I.committing then return true end
    if not I.filename then I.saveError = 'Settings are not ready to save.'; return false end
    local data, why = self:StorageEncode(I, I.sequence + 1, 'character')
    local account, error = self:StorageEncode(I.account, I.accountSequence + 1, 'account')
    if not data or not account then I.saveError = why or error; I.saveAt = I.ticks + 100; return false end
    local ok, saved = pcall(function()
        if I.accountDirty then
            if not writePair('afui-account', I.accountSequence + 1, account, 'account') then return false end
            I.accountSequence = I.accountSequence + 1; I.accountDirty = nil
        end
        if not writePair(I.filename, I.sequence + 1, data, 'character') then return false end
        I.sequence = I.sequence + 1; return true
    end)
    ok = ok and saved
    if ok then
        I.saveAt, I.saveError = nil, nil
    else
        I.saveAt = I.ticks + 100
        I.saveError = 'Could not save settings. Your layout is still open; try locking again.'
    end
    return ok
end
