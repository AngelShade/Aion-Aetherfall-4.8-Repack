# Steel Rake encounters and companion tactics — 7 October 2026

## PB-SCOPE-007A / PB-ENCOUNTER-STEELRAKE-001

This is the requested native encounter repair plus a dungeon-strategy slice.
The scope follows PB-SCOPE-007 in the pinned 037c01418b5d01506917a3db9b44fd56ac5f965c
inventory (`src/Ai/Dungeon`, `src/Ai/Raid`): encounter actions enter the installed
state strategy/priority engine. Steel Rake mechanics are taken from the maintained
Aion `ai/instance/rakes` handlers, native `spawn_helpers.xml`, `bombs.xml`, NPC skill
lists and skill templates. WoW encounter coordinates/spells were not imported.
This slice needs the installed hazard/coordination/navigation engine, not world
travel or unrelated unfinished class strategies.

| Encounter | Native mechanics and repairs | Companion behavior |
| --- | --- | --- |
| Engineer Lahulahu | 95/25% native buffs, random steam-pipe activation; phase callback ownership, death/reset/despawn cancellation, retain combat target after self buff | Existing cast-shape hazard navigation reads visible steam casts |
| Chief Gunner Koakoa | Native HP thresholds and three bomb patterns; tracked bombs, shield/pause/resume, reset/death cleanup | Bomb AI templates supply warning skill/radius before detonation; avoid hazards and withhold offense during Bulletproof Armor |
| Brass Eye Grogget | Native wave thresholds/coordinates/timers; tracked add cleanup, native hate on current living victim, phase pause/resume, stale callbacks cancelled | Prioritize already engaged captain adds during Captain's Pride; no extra room pulls |
| Golden Eye Mantutu / Anikiki | Native hunger/thirst and food/water devices; validate interaction start/completion, one active feeder, tracked timers, reset supplies, missing/blocked feeding movement recovers combat and supply | One FOLLOW non-tank operator, healer last; approach and complete the native cancellable ACTION_ITEM_NPC channel; danger cancels it; protect-master offense withheld |

Ordinary minibosses retain their native AggressiveNpcAI/skill lists and existing
bot combat/cleanse/interrupt/visible-cast handling. The initial slice omitted the
captain's tower/amplifier/staircase choreography; PB-SCOPE-007A-R1 below completes
those behaviors. Exact retail parity is not claimed.
The rejected companion teleport recovery remains removed. These encounter
changes contain no room/map movement exception.

## Installation and checks

Installed offline in external receipt
`playerbots-recruitment-20261007-223400-856173`.
The guarded installer refreshed host CIM state after the user closed both
GameServer and client, checked exact current hashes, archived originals, installed
only the bounded cumulative methods/new helper and seven handler source/class
pairs, and verified base JAR/geodata/settings preservation. No start/restart or
live attach occurred. The initial package preserved all 164 unrelated cumulative JAR entries, bundled UI and
uninstalled appearance work are preserved. The latter remains source/staged only;
its Session tick/close hooks were excluded from this package.

Full 2,419-source core compilation and all 13 Steel Rake handlers compile to the
external development workspace. 28 policy and 11 callback ownership checks pass.
Effective staged Session contains no uninstalled appearance dependency; all
unselected installed members retain their original schema/body. Native actor
combat/device/cast timing and actual client acceptance remain pending with user.

## Continuation

User confirms the earlier opened-door correction works. New Central Engine Room
stall is separate PB-REPAIR-NAV-003: four bots blocked, one moving. Exact report:
map 300100000 / instance 3, MagicDps 106468 at (538.78186,444.04572,882.08905),
owner 9403 at (548.76917,447.96683,882.0684). The encounter slice was installed first; the general floor correction was then
installed as requested. Production movement stays generic.
PB-PORT-005B remains the separate next class slice; full Playerbots parity and
other recorded acceptance/release-path investigations remain unfinished.

## Final native phase-target correction and cumulative state

Final source review added explicit native self-target application when Captain's
Pride / Bulletproof Armor is absent at an individual HP phase. SummonerAI's generic
phase cast uses the current target, which can be an enemy player; the friendly
protection must belong to the boss. Both scripts use the original native skill
IDs/levels and do not modify their shared templates. The bounded correction
changes only these two source/class pairs; all 171 JAR entries are identical.
Installed offline receipt `playerbots-recruitment-20261007-225401-230689` retains
both the first encounter receipt and shared-floor receipt `224940-884362`.
Current override SHA-256:
`8653514fdf8956c3ffcf3e311c84ccd6341a8525e6832cf45e760fde8e4662e0`.
GameServer/client remain closed; gameplay acceptance remains pending.

## PB-SCOPE-007A-R1 — original encounter TODO behavior, 8 October 2026

The user's diff review correctly identified removed comments whose encounter
behaviors had not been completed. This repair restores all seven original comment
texts alongside the implementations and records the remaining capture evidence
gap. It builds on the installed PB-SCOPE-007A native encounter slice; it does not
reopen the installed generic navigation repair or the separate class backlog.

| Original comment | Implementation / evidence |
| --- | --- |
| `todo 4 towers in the room center and fix coordinates of monsters` | Four protected native display variants 281191–281194 around the native amplifier anchor; floor-derived Z for displays and all add slots. Two checked lanes admit all eight native add families 281181–281188. Companion targeting excludes the amplifier/displays and prioritizes engaged casters/healers before melee adds. |
| `need snif` | Original comment retained. The native assets, walker and collision mesh provide usable implementation evidence; a retail packet capture is unavailable. The placement/cadence adaptation described below is not asserted as captured retail behavior. |
| `to do move boss to initial position and set pause move and atack` | Captain retreats along the first five native staircase walker nodes in reverse, protected by self-targeted 18191. Forced movement and paused native attack state last until actual arrival. Native wave thresholds 80/55/30/5 are processed sequentially even after burst damage. |
| `to do move boss in the room center and remove pause` | Captain returns along the same authored staircase. Completed movement enters activation; completed 18192/18203 restores native combat target/state. No timer resumes attacks during the staircase traversal. |
| `to do some skill boss use` | Initial amplifier activation 18192, Despair 18190, queued/completion-acknowledged Curse of Roots 18200 below 50%, one enhancement 18203 below 25%, sequenced amplifier Divine Grasp 18202 followed by captain Precision Cut 18195. Native 18193/18194/18196 remain ordinary combat skills. |
| `to do pause boss` | Koakoa cancels casting/movement, enters native IDLE and clears CAST substate; already queued normal attacks and delayed skill actions are blocked during the bomb phase. |
| `to do remove pause` | Owned 21-second phase callback removes Bulletproof Armor, restores FIGHT/NONE and resumes native attack selection. Reset/death/despawn cancel callbacks. |

### Native contracts and adaptation limits

The authoritative Aion inputs are `spawn_helpers.xml` HP phases, existing captain
wave code, NPC skill/template data, spawn anchor and walker
`055B73AA897B0E07D287848D3AD6EBCABB7DD93D`. Captain movement uses native forced
movement, running speed and arrival events. The amplifier's native FRIEND activation
skills are placeholders containing defense modifiers; the new amplifier handler
supplies the missing activation lifecycle and uses unchanged native area skills.
Only the amplifier AI assignment, its ground spawn and the captain's four
script-owned random skill probabilities change in static data. Shared skill
templates and collision meshes remain unchanged.

Display offsets of ±6m, the final phase's additional native add composition,
amplifier initial 4-second delay, ordinary/enhanced 12/8-second cadence and
30-second pull/blast cadence are authored adaptations from the native assets.
The earlier 9/35/21-second wave timing is retained. Exact retail placement,
wave composition and timing require capture evidence and remain unverified.
Steel Rake Cabin retains its existing native spawn/encounter subset; the captain
is not added to the Cabin. Existing Lahulahu, Mantutu, Anikiki, bomb and ordinary
boss skill handlers are preserved across both instances.

Root requests survive enhancement and retreat/reactivation until native cast
completion. Enhanced mode survives later wave returns. Encounter epochs and
per-combo sequence tokens reject stale/duplicate amplifier callbacks. The task
helper serializes callbacks/reset on the owning AI monitor; amplifier responses
are dispatched outside its monitor to avoid an inverse actor lock order. Native
`Skill.endCast` owns post-cast attack scheduling, preventing duplicate resumption.
No native actors, world IDs, GameServer startup or database writes were used in
this iteration's checks.

### Current installation and acceptance

Installed offline in external receipt `playerbots-recruitment-20261008-061243-633233`,
retaining the concurrent engine correction `054856-494763`. Three effective bot
methods/two definitions change, one tactic helper is added, and 169 unrelated
cumulative JAR entries remain byte-identical. Bundled UI, source-only appearance
hooks, owned alt builds, native repositories, launcher, base JAR, geodata and
client installation are preserved. Runtime `config/playerbots` remains empty.

All 2,423 current core sources and 15 Steel Rake handlers compile externally.
479 isolated checks pass: 86 phase-plan, 11 callback ownership, 44 encounter
policy/tactics, 74 navigation/trail and 264 existing cast/engine/composition/offense
regressions. Read-only native mesh checks find floors under every tower/add slot
and unobstructed add lanes to the amplifier center. Package checks verify exact
selected methods, unchanged members, all seven preserved comments and bounded
static-data changes. Inventory passes 20 mod checks/31 client hashes and records
73 client/100 server historical receipts.

Maintained staging/install/verification scripts are
`stage_steel_rake_completion_update.py`, `install_steel_rake_offline.py` and
`verify_steel_rake_completion.py`. Final package and diagnostics live under the
external development workspace. GameServer/client stayed off; actual encounter
casts, movement, device use, wipes and client presentation remain user acceptance.
PB-PORT-005B stays the independent next class slice; broader encounter/class/world
parity and the other recorded investigations remain unfinished.
