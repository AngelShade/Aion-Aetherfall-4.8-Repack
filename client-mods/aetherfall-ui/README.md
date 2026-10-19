# Aetherfall Interface

Switchable Aetherfall skin for the whole Aion 4.8 interface. Plan:
`docs/AETHERFALL_UI_MOD_PLAN.md`; style: `docs/AETHERFALL_UI_STYLE.md`.

## What it changes

- **Skin:** UI sheets in `Textures/ui/ui.pak` are recoloured per skin role from the
  client's own skin library (`ui_preload.xml`): windows, frames, controls, slots and
  gauge housings. Only near-neutral pixels (Aion's glass and steel) and Aion's blue
  accents change; icons, emblems, maps, numbers, gauge fills and other mods' art are
  untouched. Window frames get a gold top rule (tooltips: aether). Sizes, formats,
  alpha and every unrelated archive entry stay byte-identical.
- **Text colours:** common neutral/green/yellow text colours in the dialog XML
  (English override and base copies) map to ice, mist, aether and gold.
- **Native Wardrobe:** its loose surface textures use the same palette.
- **Layout preset (optional):** the target frame, target-of-target and target buffs
  move from the top edge to just above the action bars (bottom aligned).

## Files and switching

`Mods/AetherfallUI/` holds gzip payloads named by SHA-256 for every addon-owned
archive entry and loose file in the off/on/layout states, `manifest.json` and
`state.json` (desired/applied state and file stamps). The launcher
(`AetherfallInterface.cs`) applies the desired state before Play while Aion is
closed, rewriting only those entries; other mods' entries (Custom Quests, Wardrobe,
inventory, shortcuts) stay byte-identical. An entry whose content matches no
recorded state was changed by another mod: switching stops and asks for a rebuild.
Unchanged state with matching stamps costs no archive reads.

The Graphics menu records exact hashes of `game.pak` and `data.pak` and keeps
installed copies in `Mods/GraphicsMenu/package`. After a switch, when those archives
differ from its record only in addon entries, the launcher updates the package copy
and the installed hash in its manifest and state (restore baseline unchanged: it holds
the addon entries in their off state). Any other difference is left to its guard.

## Build and install

```
python client-mods/aetherfall-ui/build.py --client <client> --output <fresh external stage>
python client-mods/aetherfall-ui/install.py --stage <stage> --client <client> --launcher <built launcher exe> --backup <fresh external backup> --state on
```

`review/` in the stage holds before/after contact sheets of every recoloured sheet.
Rebuild after any other mod changes these archives.

## In-game HUD (original installed 19 October 2026)

`/aui` opens options (General, Unit Frames, Data Texts, Movers, Profiles).
First login opens the setup options. Choose DPS, Healer or Minimal, then use
`/moveui` to drag the title strips; `/moveui` again locks and saves. The native
HUD remains visible: native replacements/layout changes are the separate step 6.
Toggle duplicate addon frames off in Unit Frames if desired.

Player, target and five other party members display health/mana; member names
target on click. Data panels show date/time, zone, map coordinates and total
party/legion member counts. Combat activity records observed damage events,
ending after five seconds of inactivity; it is not an authoritative combat state.
The player cast strip shows skill name and elapsed time, then clears on success
or cancellation. The event has no duration, so there is no invented countdown.
Damage events lack adequate source/heal attribution: DPS/HPS are explicitly unavailable.

Settings are per server/character, in two alternating bounded scalar files
`Mods/AetherfallUI/profile-<identity hash>.0.cfg` and `.1.cfg`. Only complete
generations load; neither file is executed. Each preset retains its own positions.
The native SaveValue API is a social-key whitelist and is not used for settings.
Skin rebuild installation preserves these files. The skin switch and HUD enable
toggle are separate: use General > Addon in `/aui` to disable the HUD.

Build and delivery use the current installed RelicCalc archive, retaining the
exact compressed bytes of every unrelated member. The normal project signer
produces the addon archive, its signature, Addon.key and the two companion
archive signatures. Pub.key and all native binaries remain untouched.

```
python client-mods/aetherfall-ui/build_plugin.py --client <original client> --output <fresh external stage>
python client-mods/aetherfall-ui/install_plugin.py --stage <stage> --client <original client> --backup <fresh external backup>
```

Installation requires a successful full CIM process check and normal client/
launcher closure. Run from a session that can enumerate all Windows processes.
The installer backs up the five-file cohort, checks Graphics ownership read-only,
runs the installed Custom Quests/interface loaders on an external client copy,
and verifies copied hashes/signatures. Never install the superseded `v1` stage.
Rollback: with the client/launcher closed, copy the receipt's entire `files/`
cohort back over the original client so all signatures use the same key.

Offline validation used the actual installed Lua 5.1 Widget/Gauge/Button/SlashCmd
and SHA-1 modules, at UI scales 0.8, 1, 1.125 and 1.5. Native game-data calls were
simulated. Frames/visuals, native targeting, actual Lua file writes and relog
persistence still require the owner's game test. Also check a summoned pet,
Additional Functions, Wardrobe and Companions after this signing update.
Receipt: external `archives/client/aetherfall-hud-20261019/receipt.json`.
API evidence and checks: external `diagnostics/aetherfall-ui-plugin-20261019/`.
