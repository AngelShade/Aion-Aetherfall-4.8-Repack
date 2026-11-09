# Player Companions client window

`prepare_companions_window.py` updates the existing movable/resizable Companions
plugin dialog over the current original client. Use `--window-only` for a sizing
repair: it preserves the installed command bar and every other plugin member.

```powershell
python client-mods/playerbots/prepare_companions_window.py --client '<original-client>' --output '<fresh-external-stage>' --window-only
python client-mods/account-window/install.py --prepared '<fresh-external-stage>' --backup '<fresh-external-backup>'
```

The client must be closed normally before installing the signed plugin cohort.
The existing signer retains the model key and signs all three addon archives with
the matching addon key. No Core or other native DLL replacement is needed.

The command bar and its collapsed icon share the player's dragged position.
Changing between the icon, compact bar and expanded commands uses native
`SetSize`, preserving the origin without reading and rescaling it. The native
bridge continues to save the position and keep the control reachable after
resolution changes. Saved settings need no migration.

For a command-bar controller repair, use the normal signed plugin builder:

```powershell
python client-mods/aetherfall-core/prepare.py --client '<original-client>' --output '<fresh-external-stage>' --update --plugin-only --companion-bar-update
python client-mods/aetherfall-core/install.py --client '<original-client>' --stage '<fresh-external-stage>' --backup '<fresh-external-backup>'
```

This retains the installed command-bar XML, other plugin members and native DLLs.

The browser uses the publisher's `noscale` widget flag, so the engine does not
render a larger page and shrink its rasterized text at UI scales below one.
The controller uses the existing `GetUIScale()` API to round the browser's physical
width and height to whole pixels. Creation and painting then use the same extent,
including after a settled resize. The minimum dialog remains 760 by 520 UI units.
Native proportional child resizing is omitted; the existing debounced controller
fits the browser once resizing settles. Font families, text sizes, server actions
and the separate Companions command bar are retained.

Offline checks should cover the actual client Lua wrappers, native browser sizing,
resize settling, preservation of other menu bytes and compressed members, and the
signed delivery cohort. Final text sharpness and window behavior are owner in-game
acceptance, not proof supplied by an isolated renderer or a successful build.

## Recruitment texture delivery

The native recruitment reveal loads the complete texture cohort declared in
`Mods/PlayerBotBar/assets.json`. Keep this manifest and every declared DDS file
with the client; an incremental InstalledMods receipt may list only a later clip.
Prepare the manifest from a complete installed render cohort with
`python tools/distribution/client_assets.py --client <client> --output <external>/assets.json`.
Original-to-repack sync and patch preparation validate these dependencies.
`verify_bar_lua.py --client <client> --pull-only` exercises the installed addon
through the shipped Lua wrappers and rejects unresolved recruitment textures.

### Result-scaled recruitment VFX

The native Lua owner selects presentation by `max(gear, rank)`; it never rolls,
awards or changes either saved result. Poor/Ordinary retains all original DDS
bytes and timing. The first 18 charge frames (0.9 s) are shared by every tier.
Only the active stage/overlay frames are submitted at 20 Hz; there is no bulk
preload, browser animation, runtime particle simulation or added sound.

| Best roll | Accent | Charge | Burst | Panel starts / settles* | Choreography |
| --- | --- | --- | --- | --- | --- |
| Poor/Ordinary | Original | 38 frames | 33 | 3.65 / 4.50 s | Existing scene |
| Good | `#8cf0a8` | 44 | 33 | 3.95 / 4.80 s | Inward emerald streams, brighter rune bloom and burst |
| Great | `#79c8ff` | 56 | 36 | 4.70 / 5.55 s | Arcing lightning, charged rings and expanding floor shockwave |
| Excellent | `#ffc96b` | 70 | 38 | 5.50 / 6.35 s | Pillar, rising golden rays, broad burst and dense looping embers |

*From slash dispatch, including the existing two hidden preparation ticks. The
panel entrance remains 18 frames; its first frame coincides with hold frame 0.
Double Excellent adds an eight-frame counter-rotating solar seal after the
arrival wash, held for two ticks/frame (0.8 s), without extending the timeline.
Shared hold plates remain behind the native companion model. The model still
appears at burst frame 3, covered by the arrival flash.

Panel ornament/title use the presentation accent, while Auto-equip and Combat
rank stay separately labeled with their original value plates. All contents
enter together. After settling, two 12-frame clear-centre stamp/glow effects
play Gear first, then Rank 0.7 s later. Their size and colour depend on their
own roll; Continue is already usable. Stamps, entrance and double flourish
terminate; only the existing hold/ember loops continue. Close/disconnect and
restart clear all effect state. Native modal input and button rectangles are
unchanged.

To render only additions, using the installed cohort read-only:

```powershell
python client-mods/playerbots/render_summon.py --tiers-only --stage game-server/config/playerbots/media/cards/summon-stage.jpg --baseline <client>/Textures/UI/AetherfallSummon --output <stage>/delivery/Textures/UI/AetherfallSummon
python client-mods/playerbots/render_pull_ui.py --tiers-only --baseline <client>/Textures/UI/AetherfallSummon --output <stage>/delivery/Textures/UI/AetherfallSummon
python client-mods/playerbots/verify_bar_lua.py --client <client> --delivery <stage>/delivery --trace-output <stage>/playback.json --json-output <stage>/source-lua.json
```

`--delivery` overlays new staged files on the read-only installed textures and
loads maintained Lua through the installed Lua 5.1 widget/Core wrappers. The
check exercises every ordered roll pair, clip order, all new assets, shared
lead-in, reveal/panel timing, separate finite stamps and the exclusive flourish;
it also interrupts/restarts every phase. Without `--delivery`, `--pull-only`
continues to check the installed plugin. Traces record actual widget geometry,
visibility, images and group alpha for composited preview generation; offline
previews omit the real native 3D model. Engine rendering, modal mouse/Escape
dispatch and cold-load performance require in-game acceptance.

The normal signed-plugin preparation command is:

```powershell
python client-mods/aetherfall-core/prepare.py --client <client> --output <fresh-external-stage> --update --plugin-only --companion-bar-update
```

That preparer refuses outputs inside the source repository; use a fresh external
directory when preparing for installation. Install the complete matching signed
cohort from its manifest with the client normally closed, plus only the new DDS
files beside the retained baseline. Regenerate `Mods/PlayerBotBar/assets.json`
using `client_assets.py` against the resulting complete texture cohort. Never
ship a new-assets-only manifest or an unsigned manually repacked plugin.
