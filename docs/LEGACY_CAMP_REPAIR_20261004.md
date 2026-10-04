# Legacy home-zone camp research and repair

Installed on 4 October 2026 in cumulative receipt
`backups/playerbots-recruitment-20261004-132704-143251`. This retains the complete
companion override, including the preceding flag-target repair. No client files,
base server JAR, launcher, account permissions or character balances changed.

## Publisher and matching-client evidence

The [NCSOFT 4.0 notes, Base Battles, page 33](https://static.ncsoft.com/aion/store/PatchNotes/AION_Patch_Notes_062613.pdf)
describe capturable camps in Eltnen, Heiron, Morheim and Beluslan. Defeating a
delayed boss changes ownership; opposing NPC factions infiltrate the camp, and
friendly services appear after capture. The
[Gameforge 4.0 notes, Garrison Battle, page 13](https://cmsstatic.aion.gameforge.com/Patchnotes_4_0_EN.pdf)
corroborate the capture rule and NPC invasions. Consequently, an authored enemy
raider inside a camp is legitimate; ordinary outdoor spawns overlapping the
camp's center are a separate data problem.

The [Gameforge 4.8 notes](https://cmsstatic.aion.gameforge.com/AION_4_8v_Patch%20Notes_EN_30092015.pdf)
were also reviewed. These sources do not establish every old camp's precise
4.8 spawn timer or retail invasion route. Later 4.9 timer changes were not
backported. Existing captain, service, invasion and despawn timers remain intact;
their precise retail parity is still unresolved.

Read-only extraction from the installed 4.8 NA `Data/Npcs/Npcs.pak` confirms the
camp NPC IDs and native tribe definitions. `LDF_v_Guard_Light` and
`LDF_v_Guard_Dark` explicitly oppose Krall, Lephar and Lycan, while also having a
broad `Monster` friendship. Camp chiefs oppose the three camp killer tribes.
The emulator combined inherited friendship with exact hostility, then refused
combat whenever either friendship result remained true. This suppressed the
guards' more specific hostile relation.

## Installed corrections

| Zone | Camp IDs | Coverage |
| --- | --- | --- |
| Eltnen | 2120, 2121 | Native factions, assault and terrain/spawn audit |
| Heiron | 2140, 2141 | Native factions, assault and terrain/spawn audit |
| Morheim | 2220, 2221 | Native factions, assault and terrain/spawn audit |
| Beluslan | 2240, 2241 | Native factions, assault and terrain/spawn audit |

- Grounded 47 authored spots whose height differed from native collision terrain
  by more than 0.4 metres. Some guards were almost four metres above ground.
  All 229 loaded camp spots now differ by at most 0.253 metres from the audit's
  ground reference. Repairs affect three camp XML files and preserve roles,
  positions in the horizontal plane, timers and routes.
- Removed exactly four ordinary outdoor spots within 12 metres of camp centers:
  Kaidan Scout `212010` at base 2120, two Crawling Clodworm `212458` spots at
  base 2220, and Hunter Arachna `211567` at base 2221. Other spots for these
  NPCs remain. This is a bounded emulator overlap correction inferred from
  native positions, rather than a claim that publisher notes specify these IDs.
- Added `LegacyCampBattle.specificEnemy`: exact native hostility wins over
  inherited friendship only for combat NPCs of these camps. Friendly faction
  NPCs remain friendly; neutral wildlife is not globally made hostile.
- Added native raider progression toward the opposing captain of the same camp.
  It submits four-metre collision-checked movement steps, respects active regions,
  AI combat/casting/movement state, and acquires native hate when in range and
  line of sight. Tasks end on death/despawn or base replacement. Authored raider
  spawn locations remain, including intentional ambush positions.
- Capture attribution uses a same-base NPC's authored occupier before falling
  back to generic creature race, preserving Krall/Lephar/Lycan invasion ownership.
  Player attribution and existing capture/service scheduling remain intact.

## Validation and activation

Agent revision 32 preloaded every original override class before the guarded
replacement. Only three existing methods in two classes were transplanted;
The two reviewed native classes and `LegacyCampBattle` were appended to the
override. Every prior cumulative override entry remains byte-identical. Source
and deployment share the five hash-verified XML changes.
The subsequent `...133452-341528` item-ID continuation also retains every camp
package entry byte-identically. All 15 installed-mod preservation checks pass;
31 current file hashes and both
client/server receipt histories were refreshed in `INSTALLED_MODS.json`.

The live data agent preflighted each original spot and native terrain result,
then updated 47 loaded templates and removed four exact ordinary templates and
actors. It repositioned seven idle authored camp actors and found no moving or
fighting actors needing deferral. It changed no HP, ownership, quests, inventory,
or human characters. Subsequent scheduled assaults use the installed helper.

`BaseCampRuntimeCheckAgent6` passed **166 assertions**: all eight camps' faction
roles, opposing captain eligibility, friendly guard protection, same-base NPC
capture attribution, actual collision-checked native raider movement, hate
acquisition and guard damage against a native Krall actor without a player hit.
Fixtures used a separate Eltnen instance, cloned templates and non-production
base IDs, with exact native cleanup and no database/human writes. Earlier check
revisions failed because of fixture instance/home/async-aggro setup; revision 6
corrects those fixtures and includes the movement branch.

Evidence is saved beside the install receipt as `camp-data-runtime.txt`,
`camp-runtime-check.txt` and `camp-after.tsv`. The exact 51-change plan and
original/installed XML hashes are in `target/base-camps/package/camp-data-changes.json`.
An earlier attempted attach found that the former server PID had exited; its
disk changes were rolled back and its receipt carries `failed-manifest.json`.
The successful install and checks used the new process; no server restart was
initiated by the installer.

Actual client rendering, each camp's complete live raid/capture cycle and exact
retail route/timer parity remain gameplay/research limits. These native checks
do not establish acceptance of every camp or the unfinished full Playerbots scope.
Future incremental agents must use a fresh revision above **32** and preserve
the loaded helper methods if subsequently modified.

## Reusable tools

- `tools/base-camps/research.py`: read matching client NPC/tribe data and world overlaps.
- `tools/base-camps/prepare_data.py`: prepare repairs from the original terrain audit.
- `tools/base-camps/apply_source_data.py`: mirror only hash-verified deployed repairs.
- `client-mods/diagnostics/stage_base_camps.py`: cumulative incremental method/data staging.
- `game-server/tools/BaseCampInspectAgent.java`: read-only native terrain/faction audit.
- `game-server/tools/BaseCampDataApplyAgent.java`: exact guarded loaded-data activation.
- `game-server/tools/BaseCampRuntimeCheckAgent.java`: isolated native faction/movement/combat checks.
