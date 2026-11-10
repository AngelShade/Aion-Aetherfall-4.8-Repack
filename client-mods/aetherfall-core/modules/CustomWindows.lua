-- Fullscreen browser sizing and per-character movable custom window positions.
local M = AF:NewModule('CustomWindows', {needs = {'Lifecycle'}})
local fitted, webSize, custom = {}, {}, {}
local screen, scale, dialog = AF.Native.Screen, AF.Native.Scale, AF.Native.Dialog
local PLUGIN_FIRST = 0x20e

local FIT = {{'PrivateWarehouse', 'PrivateWarehouseBrowser', 25}, {'PrivateCashShop', 'PrivateCashShopBrowser', 25},
             {'PrivateJourney', 'PrivateJourneyBrowser', 0}, {'PrivateWardrobe', nil, 25}}
local function ownsWindowSize(f)
    return f[1] == 'PrivateCashShop' and type(PrivateCashShop_Fit) == 'function'
end
local function fitWindows()
    local sw, sh = screen(); local s = scale(); local W, H = sw / s, sh / s
    for _, f in ipairs(FIT) do
        local win = _G[f[1]]
        local open = win and win.IsVisible and win:IsVisible() == 1
        if not open or ownsWindowSize(f) then fitted[f[1]] = nil
        else
            local want = string.format('%.1fx%.1f', W, H)
            if fitted[f[1]] ~= want then
                win:SetRect(0, 0, W, H)
                if win.SetCloseOffset then pcall(win.SetCloseOffset, win, 4, 1) end
                local content = f[2] and _G[f[2]]
                if content then content:SetRect(0, 0, W, H - f[3]) end
                fitted[f[1]] = want
            end
        end
    end
end

local OPENS = {PrivateWarehouse_Open = 1, PrivateSeasonPass_Open = 1, PrivateCompanions_Open = 1, PrivateCashShop_Open = 2,
               PrivateJourney_Open = 3, PrivateWardrobe_Open = 4}
local function fitContent(f)
    if ownsWindowSize(f) then return end
    local content = f[2] and _G[f[2]]
    if not content then return end
    g_AddonName = 'RelicCalc'
    local sw, sh = screen(); local s = scale(); local W, H = sw / s, sh / s
    content:SetRect(0, 0, W, H - f[3])
    local size = string.format('%.0fx%.0f', W, H)
    if webSize[f[2]] ~= size then content:DestroyWebView(); content:CreateWebView(); webSize[f[2]] = size end
end

local CUSTOM_WINDOWS = {PrivateCompanions = 'PrivateCompanions_Open', PrivateAccount = 'PrivateAccount_Open',
                        PrivateCashShop = 'PrivateCashShop_Open'}
local CUSTOM_HOLD = 20  -- ticks
local function customDialog(name)
    local c = custom[name]; local d = c.id and dialog(c.id)
    if d and d.name == name then return d end
    for id = PLUGIN_FIRST, 0x221 do
        d = dialog(id)
        if d and d.name == name then c.id = id; return d end
    end
end
local function placeCustom(name, c, d)
    local s = scale()
    _G[name]:SetRect(c.x / s, c.y / s, d.w / s, d.h / s)
end
local function customOpened(name)
    local c = custom[name]
    if not AF:Call('Lifecycle', 'IsReady') or c.entered == AF:Call('Lifecycle', 'Entry') then return end
    local d = customDialog(name)
    if not d or d.w <= 0 or d.h <= 0 then return end
    c.entered = AF:Call('Lifecycle', 'Entry')
    local sw, sh = screen(); local p = AF.DB:GetWindow(name)
    local x, y = (sw - d.w) / 2, (sh - d.h) / 2
    if p then x, y = p[1] * sw, p[2] * sh end
    c.x, c.y = math.floor(math.max(0, math.min(sw - d.w, x))), math.floor(math.max(0, math.min(sh - d.h, y)))
    c.hold = AF:Call('Lifecycle', 'Ticks') + CUSTOM_HOLD
    g_AddonName = 'RelicCalc'
    placeCustom(name, c, d)
end

local function customWindowsTick()
    local cx, cy, down = AFCore.Cursor()
    local sw, sh = screen()
    for name, c in pairs(custom) do
        local d = c.id and dialog(c.id)
        if d and d.name == name and d.shown then
            if c.hold and (down or AF:Call('Lifecycle', 'Ticks') > c.hold) then c.hold = nil end
            if c.hold and (math.abs(d.x - c.x) > 1 or math.abs(d.y - c.y) > 1) then placeCustom(name, c, d) end
            if down and not c.down and cx and cx >= d.x and cy >= d.y and cx < d.x + d.w and cy < d.y + d.h then
                c.press = {d.x, d.y}
            elseif not down and c.press then
                if not AF:Call('Lifecycle', 'IsUnlocked') and (math.abs(d.x - c.press[1]) > 1 or math.abs(d.y - c.press[2]) > 1) then
                    AF.DB:SetWindow(name, {d.x / sw, d.y / sh}); AF.DB:Dirty()
                end
                c.press = nil
            end
        else
            c.press = nil
        end
        c.down = down
    end
end

for name in pairs(CUSTOM_WINDOWS) do custom[name] = {} end

function M:FitHooks() return AF.Copy(OPENS) end
function M:CustomHooks() return AF.Copy(CUSTOM_WINDOWS) end
function M:BeforeOpen(index)
    fitted = {}
    pcall(fitContent, FIT[index])
end
function M:Opened(name) pcall(customOpened, name) end
function M:Fit() fitWindows() end
function M:Tick() customWindowsTick() end

M:Listen('ResetLayoutCaches', function() fitted = {} end)
M:Phase('custom-windows', function(self)
    if AF:Call('Lifecycle', 'IsSettled') then self:Tick() end
end)
M:Phase('fit-windows', function(self)
    if AF:Call('Lifecycle', 'IsSettled') then self:Fit() end
end)
