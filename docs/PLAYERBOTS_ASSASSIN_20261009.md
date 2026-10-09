# PB-PORT-005G: Assassin rune-finisher and ordinary-chain ordering

## Confirmed gap

This bounded class port completes a missing ordering relationship from the
pinned Assassination Rogue strategy. Existing native rune builders, mature and
expiring finishers, poison/bleed upkeep, chain admission, interrupts, defenses,
target preference and melee fallback were reviewed first and remain preserved.
Full Assassin rotation, opener/stealth, poison weapon preparation, threat escape,
movement and class/world parity remain partial.

Before source edits, the actual installed Gladiator-era JAR reproduced the gap
through Session priorities, final CastAction admission and a fresh engine:
a useful and possible five-rune finisher scored 25, but an available ordinary
chain builder scored higher and won. Three baseline checks passed. Evidence is
`diagnostics/playerbots-assassin-20261009-baseline/baseline.txt` under the selected
external Aion Development Workspace. Installed PB-PORT-001/002 usefulness gates
were already correct; this change adds the missing relative ordering.

## Exact upstream and native mapping

Pinned revision: `037c01418b5d01506917a3db9b44fd56ac5f965c`.
Existing cached sources were checked against both the complete pinned Git tree
and SHA256SUMS; no repeated subsystem inventory or new source import was needed.

| Exact upstream | Purpose | SHA-256 |
| --- | --- | --- |
| `src/Ai/Class/Rogue/Strategy/AssassinationRogueStrategy.cpp` | Envenom at four combo points / nearly-dead target precedes ordinary Mutilate; fallback nodes, Kick and health defenses remain distinct | `4dc680b49fbd35aa6b33f15a10321e9c0b0e09165ef34745d2c2c389415d3c58` |
| `src/Ai/Class/Rogue/Strategy/GenericRogueStrategy.cpp` | Weapon poison preparation is separate support behavior; not literal native Aion periodic poison upkeep | `3576628d0d464571c0a61d5490811366696dd8e69f1f4d02a1558e47c486dc3e` |

Git blobs are `9c6c7b31c1eea91f1132c90e3e96d5a08f700e09` and
`fec3c3c51f624601f9d68c0a35a92797155bf697`. Upstream priority purpose is
adapted to Aion observed SIGNET stacks and learned native CARVESIGNET /
SIGNETBURST skills; no WoW combo points, energy, spells or aura mutations.
Native Rune Burst, Pain Rune, Blood Rune, Signet Silence and Explosive Rebranding
are examples of the existing native payload mapping. Actual SignetBurstEffect
and CarveSignetEffect execution/consumption remain unchanged.

## Maintained source implementation

`PlayerBotAssassin.chain` scopes the ordinary single-target hostile chain band
to ASSASSIN. Its base remains 23; finite existing fitness is bounded to 0..7 and
used as a tie of 0..0.875. The existing ready finisher band 25 and urgent band 24
therefore win. Low-health evasion at 29, critical recovery and interrupts retain
their higher priorities. Charges, passives, unrelated classes, area attacks and
ordinary fillers retain the installed path.

`PlayerBotOffense.routine` consults this helper only after existing rune-finisher
and periodic decisions. Existing Offense.finisher, useful and all final
CastAction rules stay unchanged: immature runes with an available builder still
build, zero-rune/no-builder and immature/no-builder fallbacks remain 8/12,
four-plus runes remain 25 and expiring/nearly-dead decisions remain 24.
Only the ordinary-chain fitness tie stops crossing those policy bands.

Session names the class strategy `assassin runes`; all earlier class strategy
names, composition and PASSIVE-order gate are preserved. Native chain/cost/
cooldown/target/equipment/AoE/threat admission, poison stronger-rank/refresh
rules, healing reservations and owned-alt builds remain authoritative. Planning
never grants skills, changes build/equipment or consumes native resources.

## Verification and delivery

All 123 new production Session/final-admission checks pass, including mature,
expiring and nearly-dead runes, immature/zero-rune/no-builder fallbacks, rune loss
after scoring, chain/cost/cooldown restrictions, poison upkeep/stronger-rank
protection, interrupts, critical healing, low-health evasion, threat hold, AoE
safety and class isolation. The 18 previous suites pass: 19 suites / 2,254 checks.
All 60 companion sources compile; no native cast/effect execution occurred.

Complete normal Maven JAR/ZIP build and source/artifact binding pass. The final
fresh build supersedes the preliminary output rejected after source refinement.
Complete assembly audit, 1,757 handler compilations, full production XML/schema/
JAXB load (102,012 items / 4,091 contained-item definitions / 470 overrides) and
3,289-class / 172,235-reference linkage pass. Only Offense.routine and Session.tick
change prior code; their 23/100 other methods and all native execution remain.
3,554 prior JAR entries are byte-identical; no managed resources changed.

Final review:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/release-20261009-061026-886623`.
Installed canonical guarded receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-source-build-20261009-061641-083362`.
GameServer SHA: `95955312ddaafed4eccf1ccd34ca3aa017d3c5d1b982b6d2a6913585f76fefe2`.
Commons remains `b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
The complete GameServer JAR is unchanged normal Maven output and was the only
copied file. Configuration/geometry/launcher/client/prior mods are preserved.
Initial sandbox CIM inspection could not complete; authorized retry passed fresh
shutdown and all source/artifact/runtime/client/backup/copy/rollback guards.
The already-retired override remains absent; this release removes no JAR entries.
Postinstall inventory: 20 mod checks / 31 client hashes, 74 client / 115 server
historical receipts. Server/client remain off.

**PB-VAL-017 actual native rune/chain/poison/defense combat acceptance remains
pending user testing.**
Keep GameServer/client off; no startup, attach, native cast/effect application,
world registration, database or ID operation is part of this iteration.

Next separate class review: **PB-PORT-005H Ranger**. Compare existing native ranged
positioning, chains, control, buffs and recovery with pinned Hunter strategies
before selecting a confirmed missing behavior. Broader class/world and item-ID
release-path work remain unfinished.
