-- The /afui window: renders whatever option trees modules registered with
-- AF.Config into the 11-tab, 10-row layout. It holds no feature knowledge.
-- It also owns the two slash requests and the on-demand dialog builder, since
-- windows shown from inside chat dispatch never appear in this client.
local M = AF:NewModule('OptionsWindow', {needs = {'Lifecycle', 'HUD', 'MoverEditor'}})
local S = {tab = 'General', built = {}}
local N = AF.Native
local C = AF.Config
local ROWS, TAB_SLOTS = 10, 11
local function w(id) return N.Widget('UI', id) end
local function life(method, ...) return AF:Call('Lifecycle', method, ...) end
local function trees() return C:Trees() end
local function currentTab(tabs)
    for _, tab in ipairs(tabs) do if tab[1] == S.tab then return tab end end
    S.tab = tabs[1][1]; return tabs[1]
end
local function value(item)
    local spec = item[2]
    if spec.kind == 'action' then
        if S.confirmReset and S.confirmAction == spec.action then return 'Confirm' end
        if spec.state == 'editor' then return life('IsUnlocked') and 'Lock' or 'Unlock' end
        return spec.text
    end
    return C:Display(spec)
end

function M:Refresh()
    if not life('IsReady') or not _G.AFUIOptions then return end
    local tabs = trees()
    local current = currentTab(tabs)
    local accent = AF:Call('HUD', 'Accent') or AF.Skin.Gold
    for i = 1, TAB_SLOTS do
        local tab = tabs[i]
        if tab then
            w('Tab' .. i):SetText(tab[1]); w('Tab' .. i):SetAttr('textColor', tab == current and accent or AF.Skin.Mist); w('Tab' .. i):Show()
        else
            w('Tab' .. i):Hide()
        end
    end
    w('Header'):SetText(current[1]); w('Header'):SetAttr('textColor', accent)
    for i = 1, ROWS do
        local item = current[2][i]
        local label, val, less, more = w('Row' .. i .. 'Label'), w('Row' .. i .. 'Value'), w('Row' .. i .. 'Less'), w('Row' .. i .. 'More')
        if item then
            local kind = item[2].kind
            label:SetText(item[1]); label:Show()
            val:SetText(value(item)); val:Show()
            val:SetAttr('textColor', kind == 'toggle' and (C:Get(item[2]) == 1 and AF.Skin.Aether or AF.Skin.Mist) or AF.Skin.Ice)
            if kind == 'number' or kind == 'barnumber' or kind == 'choice' or kind == 'data' or kind == 'profile' then
                local num = kind == 'number' or kind == 'barnumber'
                less:SetText(num and '-' or '<'); more:SetText(num and '+' or '>')
                less:Show(); more:Show()
            else
                less:Hide(); more:Hide()
            end
        else
            label:Hide(); val:Hide(); less:Hide(); more:Hide()
        end
    end
    if _G.AFUICode then if current.code then w('Code'):Show() else w('Code'):Hide() end end
    w('Status'):SetText(AF.DB:Error() or C:Status(current) or S.note or current[3] or '')
end
-- A module may ask for a refresh only while its own tab (and selector value) is showing.
function M:RefreshFor(tab, key, selected)
    if tab and not (S.open and S.tab == tab and (not key or AF.DB:GetLegacy(key) == selected)) then return end
    self:Refresh()
end
function M:SetCode(text) if _G.AFUICode then w('Code'):SetText(text) end end
function M:GetCode() return _G.AFUICode and w('Code'):GetText() or '' end

function M:Click(what)
    g_AddonName = 'RelicCalc'
    S.clicks, S.lastClick = (S.clicks or 0) + 1, tostring(what)
    if what == 'Close' then S.open, S.note, S.confirmReset = false, nil, nil; if _G.AFUIOptions then w('Options'):Hide() end; return end
    if not life('IsReady') then return end
    -- A pending Reset confirmation and its note last only until the next click.
    local confirming = S.confirmReset
    S.note, S.confirmReset = nil, nil
    local tabs = trees()
    local tabIndex = tonumber(tostring(what):match('^Tab(%d+)$'))
    if tabIndex and tabs[tabIndex] then S.tab = tabs[tabIndex][1]; self:Refresh(); return end
    local row, part = tostring(what):match('^Row(%d+)(%a+)$'); row = tonumber(row)
    local item = row and currentTab(tabs)[2][row]
    if not item then return end
    local spec = item[2]
    if spec.kind == 'action' then
        local result = C:Action(spec, confirming)
        if result then
            S.note, S.confirmReset = result.note, result.confirm
            S.confirmAction = result.confirm and spec.action or nil
            if result.stop then return end
        end
    else
        C:Change(spec, part)
    end
    self:Refresh()
end

function M:Open(tab)
    g_AddonName = 'RelicCalc'
    if AF:Call('MoverEditor', 'BlocksSettings') then return end
    if tab then S.tab = tab end
    if not life('IsReady') then life('Enter') end
    if not _G.AFUIOptions then
        DisplayChatMessage(0, 'Aetherfall Interface: options window did not load (plugin XML). Type /afcore for details.', 0.91, 0.63, 0.56)
        return
    end
    if not life('IsReady') then
        DisplayChatMessage(0, 'Aetherfall Interface: not ready yet (character not loaded).', 0.91, 0.63, 0.56)
        return
    end
    -- Toggle on our own state: hidden plugin dialogs can report stale visibility
    -- (the close button hides it natively, so both must agree before it closes).
    S.opened = (S.opened or 0) + 1
    local _, native = N.Panel('Options')
    local visible = native and native.shown or (not native and w('Options'):IsVisible() == 1)
    if S.open and visible then S.open = false; N.ShowPanel('Options', false) else
        local sw, sh = N.Screen(); local s = N.Scale()
        S.open = true
        w('Options'):SetRect(math.max(0, (sw / s - 640) / 2), math.max(0, (sh / s - 460) / 2), 640, 460)
        N.ShowPanel('Options', true); self:Refresh()
    end
end
function M:Hide()
    if _G.AFUIOptions then w('Options'):Hide() end
    S.open = false
end

-- Slash handlers only queue the request; the next ADDON_TIMER tick performs it and
-- reports failures in chat instead of failing silently.
local LABELS = {open = '/afui', move = '/moveui'}
function M:Request(what, arg)
    if LABELS[what] then
        S.pending, S.pendingArg = what, arg; S.requests = (S.requests or 0) + 1
        -- Explicit requests can also wake a lost shared timer. The existing
        -- owner replaces its chain; controls never add another loop.
        if WardrobeNative_ResumeTimer then WardrobeNative_ResumeTimer() end
    end
end
local function slashOpen() M:Request('open') end
local function slashMove(arg) M:Request('move', arg) end
function M:BindCommands()
    if not SlashCmdList then return end
    SlashCmdList.AFOPTIONS = slashOpen
    SLASH_AFOPTIONS1, SLASH_AFOPTIONS2, SLASH_AFOPTIONS3, SLASH_AFOPTIONS4 = '/afui', '/aetherfall', '/aui', '/ec'
    SlashCmdList.AETHERFALLMOVE = slashMove; SLASH_AETHERFALLMOVE1 = '/moveui'
end

-- The options window and movers are built on first use with the client's own
-- loader, a few dialogs per tick: built during the plugin's one-pass load they
-- lost their own setup in game (rect 0, Show ignored). A request waits until its
-- dialogs exist and the engine has had two ticks to take them.
local BUILD_PER_TICK = {open = 1, move = 4}
local function build(lazy, i)
    local entry = lazy[i]
    if not entry or S.built[i] then return end
    g_AddonName = 'RelicCalc'
    local doc = lxp.lom.parse(entry[2])
    if type(doc) ~= 'table' then error('could not parse ' .. entry[1]) end
    LoadXMLWidget(doc)
    S.built[i] = life('Ticks')
end
function M:Built(what)
    local lazy = N:Resource('lazy')
    if not lazy or #lazy == 0 then return true end  -- everything is built with the plugin
    local first, last = 1, 1
    if what == 'move' then first, last = 2, #lazy end
    local budget, done = BUILD_PER_TICK[what], true
    for i = first, last do
        if not S.built[i] then
            done = false
            if budget > 0 then build(lazy, i); budget = budget - 1 end
        elseif life('Ticks') - S.built[i] < 2 then
            done = false
        end
    end
    return done
end
local function runPending()
    local what = S.pending; if not what then return end
    local ok, ready = pcall(M.Built, M, what)
    if ok and not ready then return end  -- keep building next tick
    S.pending = nil
    if not ok then
        S.slashError = LABELS[what] .. ' (building): ' .. tostring(ready)
        DisplayChatMessage(0, 'Aetherfall Interface ' .. S.slashError, 0.91, 0.63, 0.56)
        return
    end
    if what == 'open' then M:Open() else
        local arg = S.pendingArg; S.pendingArg = nil
        AF:Call('MoverEditor', 'Unlock', arg)
    end
end

function M:SlashHandlers() return slashOpen, slashMove end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('OpenOptions', M.Open)
M:Listen('HideOptions', M.Hide)
M:Listen('RefreshOptions', M.RefreshFor)
M:Listen('BindCommands', M.BindCommands)
M:Phase('requests', function() if life('IsSettled') then runPending() end end, 10)
