-- Resource labels belong to native target frames; only passive DP uses a plugin.
local M = AF:NewModule('TargetDetails', {scope='profile', defaults={
    healthFormat={'[current]/[max]','string',128}, manaFormat={'[current]/[max]','string',128},
    dpFormat={'[current]/[max]','string',128}, assistFormat={'[current]/[max]','string',128},
    targetAbbreviate={0,'toggle'},
}})
local S = {text = {}, shown = {}}
local replacing = {}
local D = AFCore and AFCore.DIALOG or {}
local n, scale = AF.Native.Number, AF.Native.Scale
local function w(id) return AF.Native.Widget('H', id) end
local function show(id, on)
    if S.shown[id] == on and (w(id):IsVisible() == 1) == on then return end
    S.shown[id] = on
    if on then w(id):Show() else w(id):Hide() end
end
local function settext(id, s)
    s = tostring(s or '')
    if S.text[id] ~= s then w(id):SetText(s); S.text[id] = s end
end
local function resourceValue(cur, maximum, format)
    cur, maximum = n(cur), n(maximum)
    return cur and maximum and maximum > 0 and AF.Tags:Format(format or M.db.healthFormat,
        {current=cur, maximum=maximum, abbreviate=M.db.targetAbbreviate}) or ''
end
local TARGET_PANELS = {'container1', 'container2', 'container3', 'container4', 'container7'}

function M:Available(unit) return w('Target') ~= nil and AFCore.SetText ~= nil and not replacing[unit or 'target'] end
function M:Update()
    if not w('Target') or not AFCore.SetText then return end
    local assistName, _, _, _, _, assistShown = AFCore.Dialog(D.target_assist)
    local assistId, _, _, assistHP, assistMax = AFCore.Unit('targettarget')
    local assistActive = assistShown and assistName == 'target_assist_dialog' and assistId and assistId > 0
    if not replacing.targettarget then
        AFCore.SetText(D.target_assist, 'container/af_resource_hp', assistActive and resourceValue(assistHP, assistMax, M.db.assistFormat) or '')
    end
    if replacing.target then show('Target', false); return end
    settext('TargetHP', '')
    settext('TargetMP', '')
    settext('TargetDP', '')
    if S.targetWidget ~= w('TargetDPFill') then
        w('TargetDPFill'):SetUIImage(0, 'Textures/UI/AetherfallMovers/target_dp.dds')
        S.targetWidget = w('TargetDPFill')
    end
    local name, x, y, width, height, shown = AFCore.Dialog(D.target)
    local id, unit, level, hp, hpmax, mp, mpmax = AFCore.Unit('target')
    local active = shown and id and id > 0 and name == 'target_dialog'
    if not active then show('Target', false); return end
    local s = scale()
    w('Target'):SetRect(x / s, y / s, width / s, height / s)
    show('Target', true)
    w('Target'):SetPosForced(x / s, y / s)
    local dp, dpmax
    if AFCore.PlayerId and id == AFCore.PlayerId() then dp, dpmax = AFCore.Gauge(D.basic_status, 'dp_gauge_text') end
    dp, dpmax = n(dp), n(dpmax)
    local available = dp and dpmax and dpmax > 0
    local hpText, mpText = resourceValue(hp, hpmax), resourceValue(mp, mpmax, M.db.manaFormat)
    for _, panel in ipairs(TARGET_PANELS) do
        AFCore.SetText(D.target, panel .. '/af_resource_hp', hpText)
    end
    AFCore.SetText(D.target, 'container7/af_resource_mp', mpText)
    AFCore.SetText(D.target, 'container7/af_resource_dp', available and resourceValue(dp, dpmax, M.db.dpFormat) or '')
    if available and dp > 0 then
        w('TargetDPFill'):SetRect(6, 55, 239 * math.max(0, math.min(1, dp / dpmax)), 13)
        w('TargetDPFill'):Show()
    else w('TargetDPFill'):Hide() end
end

function M:Hide() if w('Target') then show('Target', false) end end
M:Listen('UnitFramesChanged', function(self, unit, on)
    replacing[unit] = on or nil
    if unit == 'target' and on then self:Hide() end
end)
function M:Invalidate() S.shown = {}; S.text = {} end
function M:OnLoad()
    if w('Target') then
        w('Target'):Hide(); S.shown.Target = false
        w('TargetDPFill'):SetUIImage(0, 'Textures/UI/AetherfallMovers/target_dp.dds')
    end
end
function M:Diagnostics()
    return {text = AF.Copy(S.text), shown = AF.Copy(S.shown)}
end
