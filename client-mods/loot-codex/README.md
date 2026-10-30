# Codex

The Codex is a read-only item and creature drop database. It currently opens from the
**Codex** tab of the Central Market (Warehouse) window: the tab navigates the same
browser to `/market/codex` with the market session. A single context-sensitive
return button goes **Back** through entries and then returns to **Central Market**.
No client file changes are needed for this version.

- Item search by name/ID, category, item type, quality, level range, faction,
  acquisition source and name/level ordering. Item pages list NPC loot, world
  pools, vendors, quests, recipes, containers and currently active event pools.
- Creature pages show level, rank, rating, race, base stats, configured loot,
  quest drops, matching world/event pools, ordinary spawn areas and indexed scripted
  locations with encounter or quest conditions.
- The Market web Bestiary filters by rating, rank, race, level range, region
  name/map ID and ordering. Its separate item/creature filter drafts survive
  section changes, and Back restores the query and page that opened an entry.
- Aetherfall field-archive visuals organise the browser into category gates,
  marked chapters, framed creature dossiers, vitality/combat stat cards and
  loot/source rows. The world-drop checkbox keeps the Market's existing on default.
- **My target** opens the creature the character has targeted.
- **View spawns on map**, per-creature **Spawn map**, and **Source locations on map**
  suspend the Market browser and open the native map Codex. **Market Codex** in
  the map pane shows the same browser document again: page, filters, history,
  scroll and unsubmitted form fields remain in memory. No account/session URL
  or personal browsing state is written to disk. Normal window close/reopen
  keeps its existing lifecycle; it is not a saved-session feature.
- **Map Codex** resumes the map pane's own last view. Native item/creature tabs
  keep separate queries and drafts, and Back restores page and unsubmitted text.
  Opening the Market Codex or handing off again clears only Codex markers and
  pending Codex pans. Personal pins and native map/legend choices are retained.
- Item icons and names in the Market browser use the same native item-tooltip
  URI as Central Market. The native map keeps its existing row-owned tooltips.
- The side panel shows the character's drop rate from the same calculation the
  loot roll uses (server rate, Boons, buffs, blessings); creature skills and level
  penalties are excluded.

The server implementation is `LootCodexService.java`, mounted at `/market/codex`
by the existing marketplace HTTP service, with media in `config/codex/media`.
`gameserver.html.codex.enabled` in `config/main/html.properties` turns it and the
market tab off. The static index is built once in a low-priority background thread
at startup and rebuilt only if the static data holders are replaced.

The normal `tools/build-components.ps1` server build generates
`config/codex/scripted-spawns.tsv` externally and packages it in the normal assembly.
Its JDK syntax-tree extractor reads instance, AI, quest and native server sources,
including inherited helpers, and the AI summon, NPC skill, skill summon, quest-object
and world-raid data. It does not execute scripts or add gameplay spawns. Fixed map
coordinates receive markers; positions chosen during gameplay have explanatory rows
without a map action. Conditions include verified death triggers and quest IDs;
other encounters retain a general conditional-spawn label. Ahbana's ten Warrior
Monuments are derived from the instance handler, without an NPC-specific override.

The generator and its inputs are part of the immutable build manifest. Deliver the
generated TSV with the matching server JAR and Codex views. The external build's
`codex-data/coverage.txt` and `unresolved-spawns.tsv` record coverage and expressions
that cannot be resolved statically, including runtime IDs and generic spawn APIs.
An unindexed location never means that a creature cannot spawn. This catalog is
reference data; scheduled encounters, random choices and quest progression still apply.

Rates distinguish a configured loot-group threshold, quest roll and world-pool
roll. Pool weights, competition and personal modifiers prevent interpreting every
displayed threshold as a final per-kill percentage. Scripted rewards, gatherables
and live sightings are outside the reference index; a spawn coordinate does not
guarantee a living mob.

`item_locations` maps recorded creature and merchant sources, including named
loot pools, quest collection drops and optional world/event pools. Scripted creature
locations and conditional rows use the same catalog as creature pages. It does not
invent spawn points for quest reward givers, recipes, containers
or loose items. Indirect acquisition stays on the item source page. At most 48
recorded points are sent for markers; every paged location can still be selected
and centred individually. This reference does not promise a live NPC or a drop.

## World map side panel and target-frame chest

The M map gets a small arrow at its right edge, matching the native legend toggle.
It opens a 270 px native Codex pane beside the map. The left legend and right Codex
can both stay open; each arrow controls its own side. Opening Codex adds 276 px to
both native frame widths while preserving the 590 x 590 map viewport and native
legend offsets. Closing it removes that extension and retains the current legend choice.
The pane is a child of DlgNewMap and shares its movement, opacity and skins through
the Aetherfall UI. It does not create another independent window.
Spawn areas first lists recorded locations. Selecting one queues the map update
on the existing native UI event pump, preserves its open window and zoom, and uses
the native pan limits before showing a temporary chest marker; another
selection replaces it, navigation clears it, and it expires after two minutes.
Closing the pane or map restores the native frame widths.
The 590 × 590 native map viewport is not resized. The Codex leaves an 8 px inset
at the top/right. Its bottom ends 4 px above the native dialog's 32 px footer frame;
the paging controls and result rows are contained above that edge.
The bright gold/teal chest at the start of each creature name area on the target frame
(every owned UI variant) opens the same panel at that creature (`target`
object id; the server resolves the visible NPC).

Native: `aetherfall-core/loot_codex.h` (Core 1.2.90 / Lua 1.2.91). It routes the DlgNewMap and
DlgTarget button-command slots through the existing class-slot owner, only when the
inspected native slot is unchanged, and otherwise leaves the class's other policies
in place. Reference HTTP runs off the UI thread, and `CodexNative.lua` renders the
response through the existing HUD timer into native controls. No browser is used
in the map pane. Map selection, expansion, layer lookup and panning reuse the native
calls and fields; the evidence is listed at the top of `loot_codex.h`.

Changed rows queue one image/tooltip initialization into the existing native UI
command phase. Images use the original factory with an explicit source rectangle
and normal widget ownership; unchanged paths retain their existing picture. There
are no readiness gates, timed texture reloads or per-frame image checks. The 32 px
rows use the catalogue's original 40 px base textures.

The original tooltip URI parser writes directly to each item row. Game.dll uses
the VC80 runtime's 40-byte wide-string ABI; temporary strings are constructed,
assigned and destroyed by that same runtime through the game's own function
slots. The bridge's C++ standard string is never passed as a native string object.
There is no helper-browser transport, guessed string-field reader, pending parser
queue or manual mouse scan. Native hover, delay and placement remain responsible.
Replacing the page clears row tooltip data and supersedes queued initialization.
Controller-only repairs can use
`--update --plugin-only --codex-controller-update` to retain installed native binaries
and map layouts, or `--retain-layout` when a matching native rebuild is needed.

Item filters: category, equipment type, quality, level range, faction, source and
name/level ordering. Creature filters: rating, rank, race, level range, area name or
map ID and ordering. Filters apply together and survive search edits. Explicit named
loot rules can establish a scripted boss's area without fabricating coordinates.

Each item row has a dedicated **N Sources** button. Counts represent distinct
creatures, merchants, quests, recipes and containers; an explicitly named pool is
expanded into its actual creatures. Duplicate loot entries from one source do not
inflate the count. General world-drop pools are excluded from that number and can
be included on item source and creature loot pages with **Include world drops**.
Creature pages opened from the target chest use the same default: direct loot,
quest drops and explicitly named pools remain visible, with general pools off.
World-only items
show **World drop** and open that view directly. The Central Market browser keeps
world pools visible by default for compatibility.

`layout.py` holds the layout/skin transforms and the composer used by
`aetherfall-core/prepare.py --update --retain-layout --codex-update`: target frame
and skin library in every owned Aetherfall UI variant, base and English map layouts
(now owned by the switcher in off/on/layout as well),
the Graphics package and a fresh restore baseline, plus
`Textures/UI/AetherfallCodex/loot.dds` (32 px export of `assets/loot-chest.png`).
The target uses the separate `loot-target.dds` export of `assets/loot-chest-bright.png`,
so existing map markers retain their artwork. Asset generation details are in `assets/README.md`.

The Central Market page uses WebKit 533-compatible block panes and prefixed
box sizing with room for the shared readable-text boost. Its JavaScript avoids
the identifier `native`, which Awesomium 1.6.3 rejects at parse time. Versioned
resources refresh browser caches; CSS/JS changes are served without a server restart.
Category gates give way to the result list when a search/filter is active.
The map handoff itself requires the matching Core, browser bridge, signed Lua and
map-layout cohort. The normal Core builder's `--codex-browser-update` (with
`--update --retain-layout --codex-update`) builds that cohort while guarding and
retaining the unchanged item/UI icon indexes. Browser commands are accepted only
from the exact local Codex route, queued to the existing native UI pump and
acknowledged to the live requesting view; destroyed views discard pending work.

Unverified until played: native input/focus in the map, marker position at every
zoom/scale, map opened from the target chest, behaviour across zone changes.
