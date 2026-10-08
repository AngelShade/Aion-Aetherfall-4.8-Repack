# PB-PORT-005B — Spiritmaster single-target strategy

Installed offline from complete normal Maven builder output on 8 October 2026.
User gameplay acceptance is pending; GameServer and Aion stayed off throughout.

## Why this slice needed a port

The pinned upstream is `mod-playerbots` at
`037c01418b5d01506917a3db9b44fd56ac5f965c`. All three cached Warlock references
match `third-party/playerbots/SHA256SUMS`. The existing Aion source was reviewed
before implementation; only the missing ordering was ported.

| Upstream purpose/source | Existing Aion behavior | Decision |
| --- | --- | --- |
| Affliction corruption/unstable affliction upkeep and filler order, `Strategy/AfflictionWarlockStrategy.cpp` | `PlayerBotOffense.refresh/useful` already prevents clipping and stronger-effect replacement; generic Spiritmaster periodic band 18 exists | Keep native refresh gates; add bounded class ordering around them |
| Life Tap resource recovery and health awareness, `Strategy/GenericWarlockStrategy.cpp`, `WarlockTriggers.cpp::LifeTapTrigger` | Generic `PlayerBotRotation.score` notices drain when injured but never gives native MP-restoring damage a distinct recovery band | Confirmed missing purpose; adapt to learned native Backdraft HP/MP recovery rather than copy Life Tap HP spending |
| Shadow Trance/proc and Drain Soul finishing priority, `AfflictionWarlockStrategy.cpp` | Native chain availability/continuers and direct-damage usefulness already exist; generic fallback is not a complete class order | Prefer admitted native follow-ups and legal direct recovery finishing; no invented execute multiplier or proc |
| Spell lock/devour purge/cleanse, `GenericWarlockStrategy.cpp` | `PlayerBotEnemyUtility`, `PlayerBotHealing`, actual pet-order template mapping and final casting gates already implement native equivalents | No duplicate port |
| Warlock pet triggers/boost strategy | The pinned GenericWarlock pet/boost functions are placeholders; Aion `PlayerBotPets` already drives native learned commands, hazards, threat holds, movement and attack timing | Preserve existing implementation; do not invent a pet rewrite from an empty upstream function |
| Soul shards, demon identity or glyph mechanics | Aion has different native resources/pets/builds | No copied WoW items, spell IDs, glyphs, pet replacement or build mutation |

Paths above are relative to `third-party/playerbots/upstream/src/Ai/Class/Warlock`.
No new upstream fetch/inventory pass was needed; this uses the existing pinned,
hash-verified source content. Dedicated pet/utility/AoE/escape strategies remain
broader work; installed native pet coordination is not missing merely because
there was no separately named Spiritmaster class helper.

## Behavior gained

`PlayerBotSpiritmaster` is active only for Spiritmaster ONLYONE offensive skills.
Its named group participates in the existing Session engine. An admitted learned
native follow-up takes priority; when HP is below 65% or MP below 40%, a learned
instant drain with a positive matching native recovery percentage outranks
periodic upkeep/filler. Otherwise missing vulnerability, needed periodic refresh
and available direct fillers share a stable class order. On a target below 25%,
legal direct restorative damage precedes a filler, while pure DoT setup remains
subject to the existing native usefulness gate.

`SpellAtkDrainInstantEffect` exposes read-only HP/MP metadata. Backdraft 3640 and
Vengeful Backdraft 3625 both contain actual recovery values. No effect is applied
by planning; native learned rank, chain, cooldown, cost, target, area, threat,
interrupt and control checks remain authoritative. A drain tag with zero recovery
does not receive restorative priority. Unaffordable/disabled skills retain native
weapon fallback. Sorcerer, Cleric and other class groups retain their paths;
pet handling and owned-alt builds remain untouched.

## Necessary build prerequisite: PB-BUILD-001

The user selected source -> normal builder -> runtime copy. Complete maintained
source and UI resources are now built by Maven; the output JARs are copied unchanged.
The cumulative override's effective behavior was compared with source-built
methods across all 176 deployed definitions. Earlier dead private synthetics and
one obsolete anonymous class are retired by normal compilation; active runtime
references pass full linkage without the old base/override JARs as fallback.

The unfinished outfit feature is explicitly disabled in source configuration.
Its inventory wrapper returns the installed inventory shape without reading
appearance files, HTTP configure rejects before actor access, and costume
filtering is inactive. UI outfit hints are shown only when the feature is enabled.
The unfinished implementation remains preserved in source for a separate release.
This prevents accidentally enabling appearance behavior through an unrelated build.

`tools/build-components.ps1` runs the normal Maven reactor, with output directed
externally by the `aion-development` profile. It does not change built classes.
`verify-source-delivery.py`, `check-source-build.py` and
`install-source-build-offline.py` gate this complete source-build transition and
copy the two affected component outputs with offline process/hash/backup guards.
The old first-on-classpath override is externally archived and removed; GameServer
uses the standard `libs/*` classpath. No database, config, handler cache, geodata,
client mod or owned-alt build was replaced. Client-exit preference changes in
system.cfg/SystemOptionGraphics.cfg were independently re-inventoried and preserved.

## Validation and installation

- Normal offline Maven package succeeds for Commons and all 2,426 GameServer
  source files. All 55 companion check sources compile separately against builder
  outputs. Maven's native/live test execution was not requested or performed.
- 59 new real offense/resource/chain/engine/native-template/release-gate checks
  pass without native casts, world registration, DB writes, allocated item IDs or
  scheduler startup. They distinguish the old low-MP ordering from the new one.
- Fourteen source-builder suites pass: Spiritmaster 59, Sorcerer 54, item/cast
  condition 147, recall 165, travel/config/formation 679, core casting 72, trade
  custody 56, engine 99, composition 49, final offense 44, speed 6, breadcrumbs 17,
  ground navigation 57, formation 314.
- Full GameServer linkage passes: 3,281 classes / 171,718 executable member refs,
  excluding both old GameServer and override from verification dependencies.
- Installed GameServer and Commons hashes exactly match the builder outputs:
  `edb3a8a2622407f49a036457a88b50b9d6aabd91b3ed59f53fa9e220bcde6d44`,
  `b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
- Current installed inventory passes all 20 checks / 31 client hashes and records
  74 client / 107 server historical receipts, including the other chat's latest
  Marketplace session repair.

Recovery receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-source-build-20261008-213553-086764`.
Build and verification:
`staging/target/playerbots-spiritmaster-20261008` and
`staging/output/playerbots-spiritmaster-20261008` below that external workspace.

PB-VAL-012 user acceptance: learned Spiritmaster spells, ordinary mob and boss
combat, low MP/HP recovery, chain continuation, DoT uptime and existing pet commands.
This is an installed bounded class slice, not full Spiritmaster or Playerbots parity.
Next class review: PB-PORT-005C Cleric, comparing current group/pet healing, cleanse,
resurrection, reservations and native class support before choosing any missing port.
World bots, broader pet/class/AoE/encounter coverage and item-ID release-path
investigation remain separate unfinished tracks.
