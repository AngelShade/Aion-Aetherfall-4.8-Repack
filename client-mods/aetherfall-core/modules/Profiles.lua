-- Profile actions and installation presets. DB owns all persisted data.
local PRESET = {
    DPS = {},
    Healer = {party=1, tot=1, timers=1, targettimers=1},
    Minimal = {databar=0, chatline=0, minimapinfo=0, targettimers=0, altbar=0, extrabar=0, sepbar=0, ['bar6.enabled']=0, xpbar=0},
}
local D = AFCore and AFCore.DIALOG or {}
local L = AFCore and AFCore.Translate or function(text) return text end
local SHARE_FILE = 'afui-export.cfg'
local M = AF:NewModule('Profiles', {
    needs={'OptionsWindow'}, scope='character',
    defaults={profile={'DPS', 'profile-name', maxLength=24}}, tags={profile='profile'},
})
AF.DB:ProfileKey('Profiles', 'profile')
for name, settings in pairs(PRESET) do AF.DB:RegisterProfilePreset(name, settings) end
local state = {name='', rename='', deleteTarget='', source='DPS', module='', scopeModule='', account='', character='',
    characterProfile='', sharing='profile', code='', note=''}
local function changed(camera)
    AF:Send('ApplySettings', true)
    if camera then AF:Send('ApplyCamera') end
    AF:Send('RefreshOptions')
end
local function result(ok, why, success, camera)
    state.note = ok and (success or 'Profile updated.') or (why or 'The profile could not be changed.')
    if ok then changed(camera) end
    return {note=state.note}
end
function M:Get(field)
    if field == 'profile' then return self.db.profile end
    if field == 'deleteTarget' then
        local names = self:Choices('deleteTarget')
        for _, name in ipairs(names) do if name == state.deleteTarget then return name end end
        return names[1] or ''
    end
    if field == 'moduleScope' then return AF.DB:ModuleScope(self:Get('scopeModule')) end
    if field == 'module' or field == 'scopeModule' or field == 'source' or field == 'account' then
        local available = self:Choices(field)
        for _, name in ipairs(available) do if name == state[field] then return name end end
        return available[1] or ''
    elseif field == 'character' then
        local entries = AF.DB:ProfileCharacters()
        for _, entry in ipairs(entries) do if entry.file == state.character then return entry.label end end
        return entries[1] and entries[1].label or ''
    elseif field == 'code' then return AF:Call('OptionsWindow', 'GetCode') or state.code end
    return state[field]
end
function M:Set(field, value)
    if field == 'profile' then return self:Choose(value) end
    if field == 'moduleScope' then
        local ok, why = AF.DB:SetModuleScope(self:Get('scopeModule'), value)
        return result(ok, why, 'Module storage scope changed.', true)
    end
    if state[field] == nil then return {note='Unknown profile control.'} end
    if field == 'character' then
        for _, entry in ipairs(AF.DB:ProfileCharacters()) do
            if entry.label == value then state.character = entry.file; return {note=state.note} end
        end
        return {note='Choose a character from the index.'}
    end
    state[field] = tostring(value or '')
    if field == 'code' then AF:Call('OptionsWindow', 'SetCode', state[field]); AF:Send('OptionsCode', state[field]) end
    return {note=state.note}
end
function M:Choices(field)
    if field == 'deleteTarget' then
        local result = {}
        for _, name in ipairs(AF.DB:ProfileList()) do
            if name ~= self.db.profile then result[#result + 1] = name end
        end
        return result
    end
    if field == 'profile' or field == 'source' then return AF.DB:ProfileList() end
    if field == 'module' or field == 'scopeModule' then return AF.DB:ProfileModules(field == 'scopeModule') end
    if field == 'account' then return AF.DB:ProfileAccountList() end
    if field == 'character' then
        local result = {}; for _, entry in ipairs(AF.DB:ProfileCharacters()) do result[#result + 1] = entry.label end
        return result
    end
    return {}
end
function M:Choose(name)
    local ok, why = AF.DB:ProfileSelect(name, PRESET[name])
    return result(ok, why, string.format(L('Selected profile %s.'),tostring(name)))
end
function M:SelectProfile(direction)
    local profiles, index = AF.DB:ProfileList(), 1
    for offset, name in ipairs(profiles) do if name == self.db.profile then index = offset end end
    return self:Choose(profiles[(index - 1 + direction) % #profiles + 1])
end
function M:Create()
    local ok, why = AF.DB:ProfileCreate(state.name)
    return result(ok, why, string.format(L('Created profile %s.'),state.name))
end
function M:Rename()
    local ok, why = AF.DB:ProfileRename(self.db.profile, state.rename)
    return result(ok, why, string.format(L('Renamed profile to %s.'),state.rename))
end
function M:Delete()
    local name = self:Get('deleteTarget')
    local ok, why = AF.DB:ProfileDelete(name)
    if ok then state.deleteTarget = '' end
    return result(ok, why, string.format(L('Deleted profile %s.'),name))
end
function M:CopyModule()
    local ok, why = AF.DB:ProfileCopyModule(self:Get('source'), self:Get('module'), self.db.profile)
    return result(ok, why, string.format(L('Copied module settings from %s.'),self:Get('source')))
end
function M:CopyProfile()
    local ok, why = AF.DB:ProfileCopy(self:Get('source'), self.db.profile)
    return result(ok, why, string.format(L('Copied profile %s into %s.'),self:Get('source'),self.db.profile))
end
function M:SaveAccount()
    local ok, why = AF.DB:ProfileSaveAccount(self.db.profile)
    if ok then ok = AF.DB:Save(); why = AF.DB:Error() end
    return result(ok, why, string.format(L('Saved %s to the account.'),self.db.profile))
end
function M:UseAccount()
    local ok, why = AF.DB:ProfileUseAccount(self:Get('account'))
    return result(ok, why, string.format(L('Applied the account profile to %s.'),self.db.profile), true)
end
function M:CopyCharacter()
    local name = state.characterProfile ~= '' and state.characterProfile or nil
    local file = state.character
    if file == '' then local entries = AF.DB:ProfileCharacters(); file = entries[1] and entries[1].file end
    local ok, why = AF.DB:ProfileCopyCharacter(file, name)
    return result(ok, why, string.format(L('Copied the character profile into %s.'),self.db.profile))
end
function M:Export()
    local code, why = AF.DB:ProfileExport(state.sharing)
    if not code then return result(nil, why) end
    state.code = code
    AF:Call('OptionsWindow', 'SetCode', code); AF:Send('OptionsCode', code)
    local saved = AF.DB:Write(SHARE_FILE, code .. '\n')
    state.note = saved and string.format(L('Code in the box. Select it and copy. Also saved to Mods/AetherfallUI/%s.'),SHARE_FILE)
        or L('Code in the box. Select it and copy.')
    return {note=state.note}
end
function M:Import()
    local code = self:Get('code') or ''
    local fromFile = code:match('^%s*$') ~= nil
    if fromFile then code = AF.DB:Read(SHARE_FILE) or '' end
    local ok, why = AF.DB:ProfileImport(code)
    if ok then ok = AF.DB:Save(); why = AF.DB:Error() end
    local message = fromFile and string.format(L('Imported into profile %s from %s.'),self.db.profile,SHARE_FILE)
        or string.format(L('Imported into profile %s.'),self.db.profile)
    return result(ok, why, message, true)
end
function M:Preset(name)
    if not PRESET[name] then return result(nil, 'Choose an installation layout.') end
    local ok, why = AF.DB:ProfileTransaction(function()
        AF.DB:ProfileRemember(); AF.DB:ProfileDefaults(PRESET[name]); AF.DB:ProfileValue('profile', name)
        AF.DB:ProfileClearPositions(name)
        if name == 'Healer' then
            local id = D.party; local dialog = id and AF.Native.Dialog(id)
            local sw, sh = AF.Native.Screen()
            if dialog and dialog.w > 0 and dialog.h > 0 then
                local key = name .. '.' .. id
                AF.DB:SetPosition(key, {math.max(0, .5 - dialog.w / sw / 2), math.max(0, .72 - dialog.h / sh)})
                AF.DB:SetBinding(key, dialog.name)
            end
        end
        AF.DB:ProfileRemember(); return true
    end)
    return result(ok, why, string.format(L('Applied %s layout.'),L(name)))
end
function M:Install(name) return self:Preset(name or 'DPS') end
function M:ResetCurrent()
    local ok, why = AF.DB:ProfileTransaction(function()
        AF.DB:ProfileDefaults(PRESET[self.db.profile]); AF.DB:ProfileClearPositions(self.db.profile)
        AF.DB:ProfileRemember(); return true
    end)
    return result(ok, why, 'Reset the current profile.')
end
function M:OnAction(action, argument)
    if action == 'export' then return self:Export() end
    if action == 'import' then return self:Import() end
    if action == 'preset' then return self:Preset(argument) end
    if action == 'store' then
        local ok, why = AF.DB:ProfileTransaction(function()
            AF.DB:ProfileRemember(); AF.DB:ProfileCopyPositions(self.db.profile, argument)
            AF.DB:SetStored(argument, AF.DB:ProfileSnapshot()); AF.DB:ProfileValue('profile', argument); return true
        end)
        if ok then ok = AF.DB:Save(); why = AF.DB:Error() end
        return result(ok, why, string.format(L('Saved profile %s.'),tostring(argument)))
    end
end

M:Listen('InstallInterface',function() AF:Send('OpenOptions','Profiles','Layouts') end)
