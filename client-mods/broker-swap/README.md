# Broker Swap

A self-contained client module that follows the server's `gameserver.broker.enabled`
(`config/main/custom.properties`). While the native Broker is retired:

- the HUD start-menu **Broker** button (`aetherfall_broker_button`) stays hidden, and the
  **Central Market** button next to it is the way in;
- the Central Market window opens whenever the server asks, which it does instead of opening
  the native Broker window (a Broker NPC's Broker choice, or the `.broker` command).

With the Broker enabled the button is shown and nothing else changes.

## How it works

The server sends two AFW1 version 2 commands on the web-session opcode (`SM_WEB_COMMAND`):
command 2 carries the Broker state with every web-session message (world entry and the
bridge's own reconnect request), and command 1 asks for the Central Market. The icon bridge
(`AionIconBridge.dll`) consumes both before the native token reader, and exports
`AionBrokerState` and `AionTakeMarketOpen`. Native AetherfallCore 1.3.8 exposes them to Lua as
`brokerstate` and `takemarket`, plus `widgetshown` for one widget's visibility.

`BrokerSwap.lua` runs from its own hidden window (`BrokerSwapTimer`, one plugin window slot)
on the shared addon timer. It hides rather than removes the button, so the start-menu button
order and every saved button position are unchanged. Until the server has sent the state the
button stays hidden, so it is never shown and then hidden. With an older core or bridge the
module does nothing.

The Additional Functions menu (Aetherfall Interface off) is built when the client loads,
before the server can send anything, so its Broker entry stays; it opens the Central Market.

## Build and install

Install the icon bridge and AetherfallCore 1.3.8 first (their own builders), then:

```
python client-mods/broker-swap/prepare.py --client <client> --output <fresh external stage>
python client-mods/broker-swap/install.py --stage <stage> --client <client> --backup <fresh external backup>
```

`prepare.py` adds `BrokerSwap.lua` and `BrokerSwap.xml` to `RelicCalc.pak`, lists them last in
`RelicCalc.toc` and re-signs the addon archive cohort; every other member stays byte-identical
and the build stops if anything else changed. A later revision replaces exactly those members.
`install.py` uses the core installer's guards and checks and writes a receipt. Restore from the
backup with Aion closed.
