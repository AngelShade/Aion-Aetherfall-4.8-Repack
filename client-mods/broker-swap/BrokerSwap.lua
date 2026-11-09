-- Broker Swap: follows the server's gameserver.broker.enabled.
-- While the native Broker is retired, the HUD Broker button stays hidden and the
-- Central Market opens whenever the server asks (a Broker NPC or the .broker command).
-- Self-contained: it only calls the AetherfallCore bridge (native core 1.3.8 or later)
-- and the Central Market's own opener. With an older core, no bridge, or before the
-- plugin's other files exist, it changes nothing and the client behaves as before.
BrokerSwap = BrokerSwap or {}
local B = BrokerSwap
local BUTTON = 'aetherfall_broker_button'
local RECHECK = 20  -- ticks (about one second) between re-applying the button state

local function core(...)
    if type(AetherfallCore) == 'function' then return AetherfallCore(...) end
end

local function startDialog()
    local D = AFCore and AFCore.DIALOG
    return D and D.start or nil
end

local function openMarket()
    local market = _G.PrivateWarehouse
    if type(PrivateWarehouse_Open) == 'function' and not (market and market.IsVisible and market:IsVisible() == 1) then
        PrivateWarehouse_Open()
    end
end

function B.Tick()
    -- 1 enabled, 0 retired, -1 not received yet; nil from a core without the command.
    local state = tonumber((core('brokerstate')))
    if state == nil then return end
    if core('takemarket') == true then openMarket() end
    -- Hidden until the server says the Broker is enabled, so it is never shown and then hidden.
    local show = state == 1
    B.wait = (B.wait or 0) - 1
    if B.shown == show and B.wait > 0 then return end
    B.wait = RECHECK
    local id = startDialog()
    if id and core('widgetshown', id, BUTTON, show) == true then B.shown = show end
end

function BrokerSwap_OnLoad()
    g_AddonName = 'RelicCalc'
    BrokerSwapTimer:RegisterEvent('ADDON_TIMER')
end

function BrokerSwap_OnEvent(event)
    if event ~= 'ADDON_TIMER' then return end
    g_AddonName = 'RelicCalc'
    local ok, why = pcall(B.Tick)
    if not ok then B.error = tostring(why) end
end
