-- Presets and player-facing profile actions; persistence stays in DB.
local PROFILES = {'DPS', 'Healer', 'Minimal', 'User1', 'User2', 'User3'}
local PRESET_SPOTS = {Healer = {{'party', .5, .72}}}
local PRESET = {
    DPS = {},
    Healer = {party = 1, tot = 1, timers = 1, targettimers = 1},
    Minimal = {databar = 0, chatline = 0, minimapinfo = 0, targettimers = 0, altbar = 0, extrabar = 0, sepbar = 0, xpbar = 0},
}
local M = AF:NewModule('Profiles', {
    needs = {'OptionsWindow'}, defaults = {profile = {'DPS', 'enum', PROFILES}}, tags = {profile = 'profile'},
})
AF.DB:ProfileKey('Profiles', 'profile')
AF.Config:Register(M, {{name = 'Profiles', order = 11, code = true,
    hint = 'Presets reset the profile they name. Save copies this layout and switches.', rows = {
        {label = 'Profile', order = 1, k = 'profile', kind = 'profile', change = 'SelectProfile'},
        {label = 'DPS preset', order = 2, kind = 'action', action = 'preset', arg = 'DPS', text = 'Apply'},
        {label = 'Healer preset', order = 3, kind = 'action', action = 'preset', arg = 'Healer', text = 'Apply'},
        {label = 'Minimal preset', order = 4, kind = 'action', action = 'preset', arg = 'Minimal', text = 'Apply'},
        {label = 'Save as User1', order = 5, kind = 'action', action = 'store', arg = 'User1', text = 'Save'},
        {label = 'Save as User2', order = 6, kind = 'action', action = 'store', arg = 'User2', text = 'Save'},
        {label = 'Save as User3', order = 7, kind = 'action', action = 'store', arg = 'User3', text = 'Save'},
        {label = 'Export code', order = 8, kind = 'action', action = 'export', text = 'Export'},
        {label = 'Import code', order = 9, kind = 'action', action = 'import', text = 'Import'},
    },
}})
local D = AFCore and AFCore.DIALOG or {}
local SHARE_FILE = 'afui-export.cfg'

function M:SelectProfile(direction)
    local nextProfile = PROFILES[1]
    for i, profile in ipairs(PROFILES) do
        if profile == self.db.profile then nextProfile = PROFILES[(i - 1 + direction) % #PROFILES + 1]; break end
    end
    AF.DB:ProfileRemember()
    AF.DB:ProfileValue('profile', nextProfile)
    AF.DB:ProfileRestore(nextProfile, PRESET[nextProfile])
    AF.DB:Dirty(); AF:Send('ApplySettings', true)
end

function M:OnAction(action, arg)
    if action == 'export' then
        local code = AF.DB:ProfileExport()
        AF:Call('OptionsWindow', 'SetCode', code)
        local saved = AF.DB:Write(SHARE_FILE, code .. '\n')
        return {note = 'Code in the box: select it and copy.' .. (saved and ' Also saved to Mods/AetherfallUI/' .. SHARE_FILE .. '.' or '')}
    elseif action == 'import' then
        local code = AF:Call('OptionsWindow', 'GetCode') or ''
        local fromFile = code:gsub('%s+', '') == ''
        if fromFile then code = AF.DB:Read(SHARE_FILE) or '' end
        local ok, why = AF.DB:ProfileImport(code)
        if ok then
            AF.DB:SetStored(self.db.profile, AF.DB:ProfileSnapshot()); AF.DB:Dirty()
            AF:Send('ApplySettings', true); AF:Send('ApplyCamera'); AF.DB:Save()
            return {note = 'Imported into profile ' .. self.db.profile .. (fromFile and ' from ' .. SHARE_FILE or '') .. '.'}
        end
        return {note = fromFile and code == '' and 'Paste a code in the box first.' or why}
    elseif action == 'preset' then
        AF.DB:ProfileRemember()
        AF.DB:ProfileDefaults(PRESET[arg])
        AF.DB:ProfileValue('profile', arg)
        AF.DB:ProfileClearPositions(arg)
        local sw, sh = AF.Native.Screen()
        for _, spot in ipairs(PRESET_SPOTS[arg] or {}) do
            local id = D[spot[1]]; local d = id and AF.Native.Dialog(id)
            if d and d.w > 0 and d.h > 0 then
                local key = arg .. '.' .. id
                AF.DB:SetPosition(key, {math.max(0, spot[2] - d.w / sw / 2), math.max(0, spot[3] - d.h / sh)})
                AF.DB:SetBinding(key, d.name)
            end
        end
        AF.DB:SetStored(arg, AF.DB:ProfileSnapshot()); AF.DB:Dirty(); AF:Send('ApplySettings', true)
    elseif action == 'store' then
        AF.DB:ProfileRemember()
        AF.DB:ProfileCopyPositions(self.db.profile, arg)
        AF.DB:SetStored(arg, AF.DB:ProfileSnapshot()); AF.DB:ProfileValue('profile', arg); AF.DB:Dirty()
        AF:Send('ApplySettings', true)
        AF.DB:Save()
    end
end
