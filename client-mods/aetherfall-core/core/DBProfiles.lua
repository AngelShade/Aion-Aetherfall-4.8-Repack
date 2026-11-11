-- Named profile operations. Character and account settings survive layout switches.
local DB, I = AF.DB, AF.DB._internal
local FIXED = {'DPS', 'Healer', 'Minimal', 'User1', 'User2', 'User3'}
I.presets = {}
local function profileSetting(key)
    local owner, owned = DB:Owner(key)
    return owner and DB:Scope(owner, owned) == 'profile' and DB:Tag(key) ~= 'profile' and DB:Tag(key) ~= 'camera'
end
I.profileSetting = profileSetting
local function profileEligible(key)
    local owner = I.owners[key]
    if not owner then return false end
    local declared = I.rules[key].scope or I.schemas[owner[1]].scope
    return declared == 'profile' and DB:Tag(key) ~= 'profile' and DB:Tag(key) ~= 'camera'
end
function DB:ProfileLimit() return 24 end
function DB:ValidProfileName(name)
    return type(name) == 'string' and #name >= 1 and #name <= 24 and name:match('^[A-Za-z0-9 _%-]+$') and name:find('[^ ]') ~= nil
end
function DB:RegisterProfilePreset(name, settings) I.presets[name] = AF.Copy(settings) end
function DB:ProfileList()
    local result, seen = {}, {}
    local active = self:ProfileName()
    for _, name in ipairs(FIXED) do
        if I.stored[name] ~= nil or name == active then result[#result + 1] = name; seen[name] = true end
    end
    local extra = {}
    for name in pairs(I.stored) do if not seen[name] then extra[#extra + 1] = name; seen[name] = true end end
    if not seen[active] then extra[#extra + 1] = active end
    table.sort(extra)
    for _, name in ipairs(extra) do result[#result + 1] = name end
    return result
end
function DB:ProfileExists(name)
    return I.stored[name] ~= nil or name == self:ProfileName()
end
function DB:ProfileSeed(catalog)
    catalog = catalog or I.stored
    for _, name in ipairs(FIXED) do if catalog[name] == nil then catalog[name] = 'S2;' end end
end
function DB:SnapshotParse(snapshot, legacy)
    if type(snapshot) ~= 'string' or #snapshot > 32768 then return end
    local settings, seen = {}, {}
    local modern = snapshot:sub(1, 3) == 'S2;'
    if modern then snapshot = snapshot:sub(4) end
    if snapshot == '' then return settings end
    for entry in (snapshot .. ';'):gmatch('(.-);') do
        if entry == '' then return end
        local key, value = entry:match('^([^:]+):(.*)$')
        if not key then return end
        if modern then key, value = I.unescape(key), I.unescape(value) end
        if key == nil or value == nil or seen[key] then return end
        seen[key] = true
        local canonical = self:Canonical(key == 'alpha' and 'windowsalpha' or key)
        if canonical then
            local parsed = self:Validate(canonical, value)
            if parsed == nil then return end
            settings[key == 'alpha' and 'alpha' or canonical] = parsed
        elseif key ~= 'abbar' or not (legacy or not modern) then return end
    end
    if not modern then self:Migrate(settings) end
    return settings
end
-- A stored profile keeps only what differs from the defaults: restoring always starts from
-- the defaults (plus the profile's built-in layout), so this is equivalent and keeps a
-- character's profiles inside the 32 KiB file. Keys a built-in layout sets are always kept,
-- since a snapshot may be stored under a profile whose layout differs.
local function layoutKey(key)
    for _, preset in pairs(I.presets or {}) do
        for name in pairs(preset) do if DB:Canonical(name) == key then return true end end
    end
    return false
end
function DB:SnapshotEncode(settings)
    local parts = {}
    for _, key in ipairs(I.wire) do
        local value = settings[key]
        if value ~= nil and (value ~= I.defaults[key] or layoutKey(key)) then
            parts[#parts + 1] = I.escape(key) .. ':' .. I.escape(value)
        end
    end
    return 'S2;' .. table.concat(parts, ';')
end
function DB:ProfileSnapshot()
    local settings = {}
    for _, key in ipairs(I.wire) do
        if profileEligible(key) then
            settings[key] = I.values[key]
            if settings[key] == nil then settings[key] = I.defaults[key] end
        end
    end
    return self:SnapshotEncode(settings)
end
function DB:ProfileDefaults(preset)
    for _, key in ipairs(I.wire) do if profileEligible(key) then I.values[key] = I.defaults[key] end end
    for key, value in pairs(preset or {}) do
        local canonical = self:Canonical(key)
        if canonical and profileEligible(canonical) then
            local parsed = self:Validate(canonical, value)
            if parsed ~= nil then I.values[canonical] = parsed end
        end
    end
end
function DB:ProfileRestore(name, preset)
    local settings = I.stored[name] and self:SnapshotParse(I.stored[name]) or {}
    if not settings then return nil, 'The saved profile is damaged.' end
    self:ProfileDefaults(preset or I.presets[name])
    for key, value in pairs(settings) do if profileEligible(key) then I.values[key] = value end end
    return true
end
function DB:ProfileRemember() I.stored[self:ProfileName()] = self:ProfileSnapshot() end
function DB:ProfileSettings(name)
    if name == self:ProfileName() then return self:SnapshotParse(self:ProfileSnapshot()) end
    if not self:ProfileExists(name) then return end
    local settings = {}
    for _, key in ipairs(I.wire) do if profileEligible(key) then settings[key] = I.defaults[key] end end
    for key, value in pairs(I.presets[name] or {}) do
        local canonical = self:Canonical(key)
        if canonical and profileEligible(canonical) then settings[canonical] = self:Validate(canonical, value) end
    end
    if I.stored[name] then
        local parsed = self:SnapshotParse(I.stored[name]); if not parsed then return end
        for key, value in pairs(parsed) do if profileEligible(key) then settings[key] = value end end
    end
    return settings
end
function DB:ProfileCopy(source, destination)
    destination = destination or self:ProfileName()
    if not self:ProfileExists(destination) then return nil, 'Choose an existing destination profile.' end
    local settings = self:ProfileSettings(source)
    if not settings then return nil, 'Choose an existing source profile.' end
    return self:ProfileTransaction(function()
        I.stored[destination] = self:SnapshotEncode(settings)
        self:ProfileCopyPositions(source, destination)
        if destination == self:ProfileName() then return self:ProfileRestore(destination) end
        return true
    end)
end
function DB:ProfileClearPositions(name)
    local prefix = name .. '.'
    for key in pairs(I.positions) do
        if key:sub(1, #prefix) == prefix then I.positions[key], I.bindings[key] = nil, nil end
    end
end
function DB:ProfileCopyPositions(source, destination)
    local prefix, copied, names = source .. '.', {}, {}
    for key, position in pairs(I.positions) do
        if key:sub(1, #prefix) == prefix then
            local target = destination .. '.' .. key:sub(#prefix + 1)
            copied[target], names[target] = AF.Copy(position), I.bindings[key]
        end
    end
    self:ProfileClearPositions(destination)
    for key, position in pairs(copied) do I.positions[key], I.bindings[key] = position, names[key] end
end
local mutable = {'values','character','account','scopes','positions','bindings','windows','stored','saveAt','accountDirty'}
function DB:ProfileTransaction(operation)
    local before = {}
    for _, key in ipairs(mutable) do before[key] = AF.Copy(I[key]) end
    local ok, result, why = pcall(operation)
    if ok and result then result, why = self:StorageFits() end
    if not ok or not result then
        for _, key in ipairs(mutable) do I[key] = before[key] end
        return nil, ok and why or tostring(result)
    end
    self:Dirty(); return true
end
function DB:ProfileCreate(name)
    if not self:ValidProfileName(name) then return nil, 'Use 1-24 letters, numbers, spaces, underscores or hyphens.' end
    if self:ProfileExists(name) then return nil, 'A profile with that name already exists.' end
    if #self:ProfileList() >= self:ProfileLimit() then return nil, 'profile storage full' end
    return self:ProfileTransaction(function()
        self:ProfileRemember(); I.stored[name] = self:ProfileSnapshot()
        self:ProfileCopyPositions(self:ProfileName(), name)
        self:ProfileValue(I.profileKey, name)
        return true
    end)
end
function DB:ProfileSelect(name, preset)
    if not self:ValidProfileName(name) or not self:ProfileExists(name) then return nil, 'Choose an existing profile.' end
    if name == self:ProfileName() then return true end
    return self:ProfileTransaction(function()
        self:ProfileRemember()
        local ok, why = self:ProfileRestore(name, preset); if not ok then return nil, why end
        self:ProfileValue(I.profileKey, name); return true
    end)
end
function DB:ProfileRename(source, destination)
    if not self:ProfileExists(source) then return nil, 'Choose an existing profile.' end
    if not self:ValidProfileName(destination) then return nil, 'Use 1-24 letters, numbers, spaces, underscores or hyphens.' end
    if self:ProfileExists(destination) then return nil, 'A profile with that name already exists.' end
    return self:ProfileTransaction(function()
        local settings = self:ProfileSettings(source)
        if not settings then return nil, 'The saved profile is damaged.' end
        I.stored[destination], I.stored[source] = self:SnapshotEncode(settings), nil
        self:ProfileCopyPositions(source, destination); self:ProfileClearPositions(source)
        if source == self:ProfileName() then self:ProfileValue(I.profileKey, destination) end
        return true
    end)
end
function DB:ProfileDelete(name)
    if name == self:ProfileName() then return nil, 'Select another profile before deleting this one.' end
    if not self:ProfileExists(name) then return nil, 'Choose an existing profile.' end
    return self:ProfileTransaction(function()
        I.stored[name] = nil; self:ProfileClearPositions(name); return true
    end)
end
function DB:ProfileModules(includeCamera)
    local result = {}
    for _, owner in ipairs(AF:Names()) do
        local has = false
        for key, rule in pairs(I.schemas[owner].defaults) do
            local old = I.legacy[owner .. '.' .. key]
            if (rule.scope or I.schemas[owner].scope) == 'profile' and self:Tag(old) ~= 'profile' and
                (includeCamera or (self:Tag(old) ~= 'camera' and profileSetting(old))) then has = true end
        end
        if has then result[#result + 1] = owner end
    end
    return result
end
function DB:ProfileCopyModule(source, owner, destination)
    destination = destination or self:ProfileName()
    if not self:ProfileExists(source) or not self:ProfileExists(destination) or not I.schemas[owner] then return nil, 'Choose a profile and module.' end
    local copyable = false
    for key in pairs(I.schemas[owner].defaults) do
        if profileSetting(I.legacy[owner .. '.' .. key]) then copyable = true; break end
    end
    if not copyable then return nil, 'This module has no profile settings to copy. Choose a profile-scoped module.' end
    local from, to = self:ProfileSettings(source), self:ProfileSettings(destination)
    if not from or not to then return nil, 'The saved profile is damaged.' end
    return self:ProfileTransaction(function()
        for key in pairs(I.schemas[owner].defaults) do
            local old = I.legacy[owner .. '.' .. key]
            if profileSetting(old) then to[old] = from[old]; if to[old] == nil then to[old] = I.defaults[old] end end
        end
        I.stored[destination] = self:SnapshotEncode(to)
        if destination == self:ProfileName() then return self:ProfileRestore(destination) end
        return true
    end)
end
function DB:ProfileAccountList()
    local result = {}; for name in pairs(I.account.profiles) do result[#result + 1] = name end
    table.sort(result); return result
end
function DB:ProfileSaveAccount(name)
    name = name or self:ProfileName()
    if not self:ValidProfileName(name) then return nil, 'Choose a valid account profile name.' end
    if not I.account.profiles[name] and #self:ProfileAccountList() >= self:ProfileLimit() then return nil, 'profile storage full' end
    return self:ProfileTransaction(function()
        local code, why = self:ProfileExport('profile', name)
        if not code then return nil, why end
        I.account.profiles[name] = code
        I.accountDirty = true; return true
    end)
end
function DB:ProfileUseAccount(name)
    local code = I.account.profiles[name]
    if not code then return nil, 'Choose an account profile.' end
    return self:ProfileImport(code, {preserveName=true, onlyScope='profile'})
end
function DB:ProfileCopyCharacter(file, name)
    local allowed = false
    for _, entry in ipairs(self:ProfileCharacters()) do if entry.file == file then allowed = true end end
    if not allowed then return nil, 'Choose a character from the saved index.' end
    local _, state = self:StorageReadPair(file, 'character')
    if not state then return nil, 'The character settings could not be read.' end
    local sourceName = state.character[I.profileKey] or state.values[I.profileKey] or 'DPS'
    name = name or sourceName
    local snapshot = name == sourceName and self:SnapshotEncode(state.values) or state.stored[name]
    local parsed = snapshot and self:SnapshotParse(snapshot)
    if not parsed then return nil, 'The source profile could not be read.' end
    return self:ProfileTransaction(function()
        self:ProfileDefaults()
        for key, value in pairs(parsed) do if profileSetting(key) then I.values[key] = value end end
        self:ProfileClearPositions(self:ProfileName())
        local prefix, destination = name .. '.', self:ProfileName() .. '.'
        for key, position in pairs(state.positions) do
            if key:sub(1, #prefix) == prefix then
                local target = destination .. key:sub(#prefix + 1)
                I.positions[target], I.bindings[target] = AF.Copy(position), state.bindings[key]
            end
        end
        self:ProfileRemember(); return true
    end)
end
