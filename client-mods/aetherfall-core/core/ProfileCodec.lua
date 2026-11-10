-- Profile snapshots and the version-one sharing wire format belong to DB.
local DB = AF.DB
local function profileSetting(key)
    local tag = DB:Tag(key)
    return tag ~= 'profile' and tag ~= 'camera'
end
local function shareSetting(key)
    local tag = DB:Tag(key)
    return tag ~= 'profile' and tag ~= 'share-skip'
end
local function checksum(body)
    local sum = 0
    for i = 1, #body do sum = (sum * 31 + body:byte(i)) % 65521 end
    return string.format('%04x', sum)
end

function DB:ProfileExport()
    local parts = {}
    for _, key in self:Settings() do
        if shareSetting(key) and self:GetLegacy(key) ~= self:Default(key) then
            parts[#parts + 1] = key .. '=' .. tostring(self:GetLegacy(key))
        end
    end
    local prefix = self:ProfileName() .. '.'
    for key, position in self:Positions() do
        if key:sub(1, #prefix) == prefix then
            local id = key:sub(#prefix + 1); local name = self:GetBinding(key)
            parts[#parts + 1] = 'p' .. id .. '=' .. string.format('%.4f,%.4f', position[1], position[2]) .. (name and (',' .. name) or '')
        end
    end
    table.sort(parts)
    local body = table.concat(parts, ';')
    return 'AFUI1!' .. #body .. '!' .. body .. '!' .. checksum(body)
end

function DB:ProfileClearPositions(profile)
    local prefix = profile .. '.'
    for key in self:Positions() do
        if key:sub(1, #prefix) == prefix then self:SetPosition(key, nil); self:SetBinding(key, nil) end
    end
end

function DB:ProfileImport(code)
    code = tostring(code or ''):gsub('%s+', '')
    local size, body, sum = code:match('^AFUI1!(%d+)!(.*)!(%x%x%x%x)$')
    if not size then return nil, code:match('^AFUI1!') and 'The code is incomplete; copy all of it.' or 'Not an Aetherfall Interface code.' end
    if #body ~= tonumber(size) or checksum(body) ~= sum:lower() then return nil, 'The code is incomplete or changed; copy all of it.' end
    local settings, positions = {}, {}
    for entry in body:gmatch('[^;]+') do
        local key, value = entry:match('^(%w+)=(.+)$')
        local id = key and tonumber(key:match('^p(%d+)$'))
        if id then
            local fx, fy, name = value:match('^([%-%d%.]+),([%-%d%.]+),?([%w_]*)$')
            fx, fy = tonumber(fx), tonumber(fy)
            if not (fx and fy and fx >= -1 and fy >= -1 and fx <= 1 and fy <= 1) then return nil, 'Bad frame position in the code.' end
            positions[id] = {fx, fy, name ~= '' and name or nil}
        elseif key and self:Default(key) ~= nil and shareSetting(key) then
            local parsed = self:Validate(key, value)
            if parsed == nil then return nil, 'Bad value for ' .. key .. ' in the code.' end
            settings[key] = parsed
        elseif key then
            return nil, 'Unknown setting ' .. key .. ' (made by a newer version?).'
        end
    end
    -- Validation completes before replacing any current settings or positions.
    for _, key in self:Settings() do if shareSetting(key) then self:ProfileValue(key, self:Default(key)) end end
    for key, value in pairs(settings) do self:ProfileValue(key, value) end
    local prefix = self:ProfileName() .. '.'
    self:ProfileClearPositions(self:ProfileName())
    for id, position in pairs(positions) do
        self:SetPosition(prefix .. id, {position[1], position[2]}); self:SetBinding(prefix .. id, position[3])
    end
    return true
end

function DB:ProfileSnapshot()
    local parts = {}
    for _, key in self:Settings() do
        if self:Default(key) ~= nil and profileSetting(key) then parts[#parts + 1] = key .. ':' .. tostring(self:GetLegacy(key)) end
    end
    return table.concat(parts, ';')
end

function DB:ProfileDefaults(preset)
    for _, key in self:Settings() do if profileSetting(key) then self:ProfileValue(key, self:Default(key)) end end
    for key, value in pairs(preset or {}) do self:ProfileValue(key, value) end
end

function DB:ProfileRestore(profile, preset)
    local settings = {}
    for key, value in (self:GetStored(profile) or ''):gmatch('(%w+):([^;]+)') do
        if (self:Default(key) ~= nil or key == 'alpha') and profileSetting(key) then
            -- The first wire format stored opacity under the retired alpha key.
            settings[key] = self:Validate(key == 'alpha' and 'windowsalpha' or key, value)
        end
    end
    if self:GetStored(profile) then self:Migrate(settings) end
    self:ProfileValue('alpha', nil)
    self:ProfileDefaults(preset)
    for key, value in pairs(settings) do if self:Default(key) ~= nil then self:ProfileValue(key, value) end end
end

function DB:ProfileRemember()
    self:SetStored(self:ProfileName(), self:ProfileSnapshot())
end

function DB:ProfileCopyPositions(source, destination)
    local prefix = source .. '.'; local copied, names = {}, {}
    for key, position in self:Positions() do
        if key:sub(1, #prefix) == prefix then
            local target = destination .. '.' .. key:sub(#prefix + 1)
            copied[target] = {position[1], position[2]}; names[target] = self:GetBinding(key)
        end
    end
    self:ProfileClearPositions(destination)
    for key, position in pairs(copied) do self:SetPosition(key, position); self:SetBinding(key, names[key]) end
end
