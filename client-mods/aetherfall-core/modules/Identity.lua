-- Bot identity labels inside the native target and party frames.
local M = AF:NewModule('Identity')
local D = AFCore and AFCore.DIALOG or {}
local RARITIES = {'Poor', 'Ordinary', 'Good', 'Great', 'Excellent'}
local TIER = {Poor = 1, Ordinary = 2, Good = 3, Great = 4, Excellent = 5}
local hiddenNames = {}
local function stars(rank)
    local n = TIER[rank]
    if not n then return '' end
    return string.rep('★', n) .. string.rep('☆', 5 - n)
end
local function lookup(name)
    if name and name ~= '' then
        local rarity, rank = AFCore.Identity(name)
        if TIER[rarity] then return rarity, rank end
    end
end
local function fill(dialog, prefix, rarity, text)
    local ok = true
    for _, r in ipairs(RARITIES) do
        ok = AFCore.SetText(dialog, prefix .. string.lower(r), r == rarity and text or '') and ok
    end
    return ok
end
local function nativeName(path, on)
    if not AFCore.WidgetShown or (on and not hiddenNames[path]) then return end
    if AFCore.WidgetShown(D.party, path, on) then hiddenNames[path] = not on or nil end
end

function M:Update()
    if not AFCore.Identity or not AFCore.SetText then return end
    local tname, _, _, _, _, tshown = AFCore.Dialog(D.target)
    local id, unit = AFCore.Unit('target')
    local rarity, rank
    if tshown and tname == 'target_dialog' and id and id > 0 and not (AFCore.PlayerId and id == AFCore.PlayerId()) then rarity, rank = lookup(unit) end
    fill(D.target, 'container7/af_identity_gear_', rarity, rarity and ('◆ ' .. rarity .. ' gear') or '')
    AFCore.SetText(D.target, 'container7/af_identity_rank', rarity and ('Rank ' .. stars(rank)) or '')
    for i = 1, 6 do
        local row = 'ctn_member' .. i
        local name = AFCore.Text(D.party, row .. '/title')
        local r, k = lookup(name)
        local placed = fill(D.party, row .. '/af_identity_name_', r, r and name or '')
        AFCore.SetText(D.party, row .. '/af_identity_rank', r and stars(k) or '')
        nativeName(row .. '/title', not (r and placed))
    end
end
function M:Off()
    if not AFCore.SetText then return end
    fill(D.target, 'container7/af_identity_gear_', nil, '')
    AFCore.SetText(D.target, 'container7/af_identity_rank', '')
    for i = 1, 6 do
        local row = 'ctn_member' .. i
        fill(D.party, row .. '/af_identity_name_', nil, '')
        AFCore.SetText(D.party, row .. '/af_identity_rank', '')
        nativeName(row .. '/title', true)
    end
end
