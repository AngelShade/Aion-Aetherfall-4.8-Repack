-- Settings schemas and runtime views. Persistence and profile codecs extend DB.
require('sha1')
local DB = {}
AF.DB = DB
local I = {schemas={}, owners={}, defaults={}, legacy={}, rules={}, tags={}, templates={},
    values={}, character={}, account={values={}, profiles={}}, scopes={}, positions={}, bindings={},
    windows={}, stored={}, ticks=0, sequence=0, accountSequence=0}
DB._internal = I
local wire = {'abrow1','warn','camdist','camfov','minimapinfo','botbarmode','extrabar','botbar','profile','ttshow','dr3','mapy',
 'targettimers','dr2','mapsize','xpbar','absize2','dc3','dc2','dc1','databar','snapframes','sepbar','dl3','chatalpha','dl1',
 'absize1','snap','menucols','ttui','timers','guidesize','showgrid','abrow4','menusize','ttanchor','grid','ttgrow','party',
 'barsalpha','chatline','opacitydirect','dl2','theme','abrow2','startmenu','dr1','mapx','tipcursor','absize3','ttclamp','menugap',
 'abrow3','abbar','windowsalpha','tty','tot','ttx','absize4','font','altbar'}
I.wire = wire
local known = {}; for _, key in ipairs(wire) do known[key] = true end
local function validScope(scope) return scope == 'profile' or scope == 'character' or scope == 'account' end
local function ownerName(owner) return type(owner) == 'table' and owner.name or owner end
local function ruleCopy(rule)
    assert(type(rule) == 'table' and rule[1] ~= nil, 'invalid setting schema')
    return AF.Copy(rule)
end
function DB:RegisterTemplate(name, schema)
    assert(type(name) == 'string' and not I.templates[name], 'duplicate setting template: ' .. tostring(name))
    I.templates[name] = AF.Copy(schema)
end
local function readonly(owner, prefix, children)
    return setmetatable({}, {__index=function(_, key)
        if children and children[key] then return children[key] end
        return DB:Get(owner, prefix .. tostring(key))
    end, __newindex=function() error('settings must be changed through DB:Set') end})
end
function DB:Declare(owner, spec)
    assert(not I.schemas[owner], 'duplicate setting owner: ' .. owner)
    local scope = spec.scope or 'profile'
    assert(validScope(scope), 'invalid settings scope')
    local expanded, aliases, children = {}, AF.Copy(spec.legacy or {}), {}
    for key, rule in pairs(spec.defaults or {}) do expanded[key] = ruleCopy(rule) end
    for instance, definition in pairs(spec.instances or {}) do
        local template = assert(I.templates[definition.template], 'unknown setting template: ' .. tostring(definition.template))
        for key, rule in pairs(template) do
            if not definition.only or definition.only[key] then
                local copy = ruleCopy(rule)
                if definition.defaults and definition.defaults[key] ~= nil then copy[1] = definition.defaults[key] end
                copy.scope = copy.scope or definition.scope
                expanded[instance .. '.' .. key] = copy
            end
        end
        for old, key in pairs(definition.legacy or {}) do aliases[old] = instance .. '.' .. key end
        children[instance] = readonly(owner, instance .. '.')
    end
    local names = {}
    for old, key in pairs(aliases) do
        assert(expanded[key], 'legacy setting has no schema: ' .. old)
        if not names[key] or old < names[key] then names[key] = old end
    end
    I.schemas[owner] = {defaults=expanded, scope=scope, migrate=spec.migrate, fresh=spec.fresh}
    for key, rule in pairs(expanded) do
        local old = names[key] or key
        assert(not I.owners[old], 'duplicate persisted setting: ' .. old)
        assert(validScope(rule.scope or scope), 'invalid setting scope: ' .. key)
        I.owners[old], I.defaults[old], I.rules[old] = {owner, key}, rule[1], rule
        I.legacy[owner .. '.' .. key] = old
        if not known[old] then known[old] = true; wire[#wire + 1] = old end
        if spec.tags then I.tags[old] = spec.tags[key] end
    end
    for old, key in pairs(aliases) do
        local canonical = I.legacy[owner .. '.' .. key]
        if old ~= canonical then
            assert(not I.owners[old], 'duplicate persisted alias: ' .. old)
            I.owners[old], I.defaults[old], I.rules[old] = I.owners[canonical], I.defaults[canonical], I.rules[canonical]
        end
    end
    return readonly(owner, '', children)
end
function DB:Canonical(key)
    local owner = I.owners[key]
    return owner and I.legacy[owner[1] .. '.' .. owner[2]]
end
function DB:Scope(owner, key)
    owner = ownerName(owner)
    local old = assert(I.legacy[owner .. '.' .. key], owner .. ' does not own ' .. key)
    local rule = I.rules[old]
    if rule.scope then return rule.scope end
    local declared = I.schemas[owner].scope
    if declared ~= 'profile' then return declared end
    return I.scopes[owner] or 'profile'
end
local function bucket(old)
    local owner = I.owners[old]
    local scope = DB:Scope(owner[1], owner[2])
    if scope == 'account' then return I.account.values end
    if scope == 'character' then return I.character end
    return I.values
end
I.bucket = bucket
function DB:Validate(old, value)
    local rule = I.rules[old]; if not rule then return end
    local kind = rule[2]
    if kind == 'int' or kind == 'number' or kind == 'choice' then
        local number = tonumber(value)
        local minimum, maximum = rule[3], rule[4]
        if kind == 'choice' then minimum, maximum = 1, #rule[3] end
        if number and number == number and number ~= math.huge and number ~= -math.huge and
            number >= minimum and number <= maximum and (kind == 'number' or number == math.floor(number)) then return number end
    elseif kind == 'enum' then
        for _, candidate in ipairs(rule[3]) do if candidate == value then return value end end
    elseif kind == 'toggle' then
        if value == '0' or value == '1' or value == 0 or value == 1 then return tonumber(value) end
    elseif kind == 'bool' then
        if value == true or value == 'true' then return true end
        if value == false or value == 'false' then return false end
    elseif kind == 'color' then
        if type(value) == 'string' and value:match('^#%x%x%x%x%x%x$') then return value:lower() end
    elseif kind == 'string' or kind == 'profile-name' then
        if type(value) ~= 'string' then return end
        local maximum = rule.maxLength or rule[3] or 128
        if #value > maximum or value:find('[%z\1-\31\127]') then return end
        local pattern = rule.pattern or rule[4]
        if pattern and not value:match(pattern) then return end
        if kind == 'profile-name' and not self:ValidProfileName(value) then return end
        return value
    end
end
function DB:GetLegacy(key)
    local canonical = self:Canonical(key); if not canonical then return end
    local value = bucket(canonical)[canonical]
    if value == nil then value = I.defaults[canonical] end
    return value
end
function DB:Get(owner, key)
    owner = ownerName(owner)
    return self:GetLegacy(assert(I.legacy[owner .. '.' .. key], owner .. ' does not own ' .. key))
end
function DB:Owner(key)
    local owner = I.owners[key]; if owner then return owner[1], owner[2] end
end
function DB:Rule(owner, key)
    owner = ownerName(owner)
    local old = I.legacy[owner .. '.' .. key]
    if not old then return end
    local rule = AF.Copy(I.rules[old]); rule.scope = self:Scope(owner, key)
    return rule
end
function DB:Set(owner, key, value, quiet)
    owner = ownerName(owner)
    local old = assert(I.legacy[owner .. '.' .. key], owner .. ' does not own ' .. key)
    local validated = self:Validate(old, value)
    assert(validated ~= nil, 'invalid setting: ' .. old)
    if not quiet and AF:Call(owner, 'BeforeSetting', key, validated) == false then return false end
    bucket(old)[old] = validated
    if self:Scope(owner, key) == 'account' then I.accountDirty = true end
    self:Dirty()
    if not quiet then AF:Call(owner, 'OnSetting', key, validated); AF:Send('SettingChanged', old, owner, key, validated) end
    return true
end
function DB:ModuleScope(owner) return I.scopes[ownerName(owner)] or 'profile' end
function DB:SetModuleScope(owner, scope)
    owner = ownerName(owner)
    if not I.schemas[owner] or not validScope(scope) then return nil, 'Invalid module scope.' end
    local captured = {}
    for key in pairs(I.schemas[owner].defaults) do
        local old = I.legacy[owner .. '.' .. key]; captured[old] = self:GetLegacy(old)
    end
    I.scopes[owner] = scope
    for old, value in pairs(captured) do
        local target = bucket(old)
        if target[old] == nil then target[old] = value end
    end
    if scope == 'account' then I.accountDirty = true end
    self:Dirty(); return true
end
function DB:Clock(value) I.ticks = value end
function DB:Dirty() I.saveAt = I.ticks + 20 end
function DB:SaveAt() return I.saveAt end
function DB:SetSaveAt(value) I.saveAt = value end
function DB:Error() return I.saveError end
function DB:Read(name) return AFCore.ReadFile and AFCore.ReadFile(name) end
function DB:Write(name, bytes) return AFCore.SaveFile and AFCore.SaveFile(name, bytes) end
function DB:ReadLayoutState()
    local data = self:Read('state.json')
    if not data and io and io.open then
        local file = io.open(AFCore.ModPath('state.json'), 'r')
        if file then data = file:read(32769); file:close() end
    end
    return data and #data <= 32768 and data or nil
end
function DB:Reset()
    I.values, I.character, I.positions, I.bindings, I.windows, I.stored, I.scopes = {}, {}, {}, {}, {}, {}, {}
    I.account, I.sequence, I.accountSequence, I.accountDirty = {values={}, profiles={}}, 0, 0, nil
    I.saveAt, I.saveError, I.draft, I.filename, I.path = nil, nil, nil, nil, nil
end
function DB:Migrate(settings)
    local changed = false
    for _, owner in ipairs(AF:Names()) do
        local spec = I.schemas[owner]
        if spec and spec.migrate then changed = spec.migrate(settings) or changed end
    end
    return changed
end
function DB:BeginDraft()
    I.draft = AF.Copy({values=I.values, character=I.character, account=I.account, positions=I.positions,
        bindings=I.bindings, windows=I.windows, stored=I.stored, scopes=I.scopes, saveAt=I.saveAt, accountDirty=I.accountDirty})
end
function DB:HasDraft() return I.draft ~= nil end
function DB:CommitDraft() I.committing = true; local ok = self:Save(); I.committing = nil; return ok end
function DB:CancelDraft()
    if not I.draft then return end
    for key, value in pairs(I.draft) do I[key] = AF.Copy(value) end
    I.saveAt, I.accountDirty, I.saveError = I.draft.saveAt, I.draft.accountDirty, nil
end
function DB:EndDraft() I.draft = nil end
function DB:ProfileKey(owner, key) I.profileKey = assert(I.legacy[owner .. '.' .. key]) end
function DB:ProfileName() return I.profileKey and self:GetLegacy(I.profileKey) or 'DPS' end
function DB:GetPosition(key) return AF.Copy(I.positions[key]) end
function DB:SetPosition(key, value) I.positions[key] = AF.Copy(value) end
function DB:Positions()
    return function(_, key) local nextKey, value = next(I.positions, key); return nextKey, AF.Copy(value) end, nil, nil
end
function DB:GetBinding(key) return I.bindings[key] end
function DB:SetBinding(key, value) I.bindings[key] = value end
function DB:GetWindow(name) return AF.Copy(I.windows[name]) end
function DB:SetWindow(name, value) I.windows[name] = AF.Copy(value) end
function DB:GetStored(name) return I.stored[name] end
function DB:SetStored(name, value) I.stored[name] = value end
function DB:Settings() return ipairs(wire) end
function DB:Default(key) return I.defaults[key] end
function DB:Tag(key) return I.tags[key] end
function DB:ProfileValue(key, value)
    local canonical = self:Canonical(key)
    if canonical then bucket(canonical)[canonical] = value end
end
function DB:Diagnostics()
    local effective = {}
    for _, key in ipairs(wire) do
        if I.owners[key] then local value = bucket(key)[key]; if value ~= nil then effective[key] = value end end
    end
    return {db=effective, positions=AF.Copy(I.positions), bindings=AF.Copy(I.bindings), windows=AF.Copy(I.windows),
        stored=AF.Copy(I.stored), file=I.filename, path=I.path, sequence=I.sequence, saveAt=I.saveAt,
        saveError=I.saveError, scopes=AF.Copy(I.scopes), account=AF.Copy(I.account), character=AF.Copy(I.character)}
end
