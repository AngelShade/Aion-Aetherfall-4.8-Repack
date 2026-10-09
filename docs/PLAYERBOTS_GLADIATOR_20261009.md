# PB-PORT-005F: Gladiator native counter opportunity

## Confirmed gap and scope

This is a bounded new reactive-attack ordering port. Native chain prerequisites,
drain fitness bonuses, interrupts, threat holds, periodic upkeep, AoE safety and
ordinary damage ranking already exist. They were reviewed before selecting this
slice and remain preserved. Full Gladiator rotation, AoE strategy, armor-debuff
assignment, movement, execution/stance and class/world parity remain partial.

The missing distinction was a native counter event. `PlayerBotSkills.canPlan`
already checks the exact five-second counter window, but `PlayerBotOffense.routine`
gave a counter-only attack the ordinary filler band. A larger ordinary attack or
available chain could displace it until the real counter opportunity expired.
The installed baseline was reproduced on world-free actors through actual
Session priorities, final CastAction gates and fresh engine selection: the native
counter was useful/possible, but ordinary filler won. All three baseline checks
passed against the actual installed Templar-era JAR before source changes.
External evidence: `diagnostics/playerbots-gladiator-20261009-baseline/baseline.txt`
under the selected Aion Development Workspace.

## Exact upstream and native mapping

All sources are at pinned revision `037c01418b5d01506917a3db9b44fd56ac5f965c`.

| Exact upstream | Reviewed purpose | SHA-256 |
| --- | --- | --- |
| `src/Ai/Class/Warrior/Strategy/ArmsWarriorStrategy.cpp` | Available Overpower/Taste for Blood and Victory Rush have explicit reactive priorities alongside normal strikes/melee and recovery | `73f39c63c695a3fc1093bbec3da326f92ae66fda4798cb322c45b7c9bb272603` |
| `src/Ai/Class/Warrior/Strategy/FuryWarriorStrategy.cpp` | Available instant Slam/Victory Rush and ordinary Bloodthirst/Whirlwind/melee priorities are distinct; keep interrupts and native fallback | `de232c28bb1bca55f2798da7aba1484ac5cac8a2a9eeef701978167a21ee5456` |
| `src/Ai/Class/Warrior/WarriorTriggers.h` | Overpower/VictoryRush are actual cast-availability triggers, not fabricated procs | `4e7b1de80f4bc9cca2079d6c73cd6800be992a6b13122fd4f0b48b44b774991a` |

The two strategy files were imported from the existing complete pinned tree.
Git blobs `70b87117b7c91b5d2ce62fac9b8e48953e51b60e` and
`f6f853e3cea6c2ce1e78601fc3c7d9e25f5ba0c2` and their exact SHA-256 hashes were
verified before caching. This preserves the completed subsystem inventory and
GPL attribution; it is not a repeated inventory or a claim of complete algorithm
parity from template coverage.

Aion Spite Strike 584–589 and Counter Leech 759–760 require the player's actual
PARRY event and are direct single-target physical damage actions. That native
event maps the **reactive opportunity purpose**, not the literal WoW proc condition,
spell, aura, rage or talent. Existing drain fitness still selects Counter Leech
when its learned recovery purpose is useful. No skill, Stigma, equipment or build
is granted or replaced; owned-alt builds remain protected.

## Source implementation

`PlayerBotGladiator.counter` only applies to GLADIATOR DAMAGE entries with a native
counter event, direct physical payload, ENEMY relation and ONLYONE target.
Charges/passives and unrelated skills/classes retain their installed path.
An expired event returns zero priority. The window is read from the same native
counter timestamp used by the unchanged native canPlan/Skill gates; it is never
created, extended or consumed during planning.

The **Aion adaptation** ranks a live opportunity at 31 plus a bounded existing
fitness tie (0–0.07). Ordinary installed chains can reach 30; reactive opportunity
therefore wins before that window is lost, while interrupt, tank recovery and
critical healing remain higher. The native final CastAction rechecks costs,
cooldowns, chain prerequisites and the event after priorities/prerequisites have
been resolved. No arbitrary counter probability or invented execute multiplier.

The helper is connected to actual `PlayerBotOffense.routine`. Session names its
existing class strategy `gladiator counters`, preserving all earlier class names,
state composition and PASSIVE-order gate. Final CastAction, native skill execution,
parry tracking, damage/healing, MP, chain consumption, AoE/target admission,
threat policy and movement stay unchanged. Unavailable counters retain affordable
learned attacks and normal weapon fallback; an AoE counter flag gains no new band
and cannot bypass existing AoE safety.

## Offline verification and delivery

All 66 new `PlayerBotGladiatorCheck` production priority/final admission checks
pass against the complete normal builder output, including a native window that
expires after scoring. The 17 existing world-free suites also pass: 18 suites /
2,131 checks total. All 59 companion test sources compile. No native cast/effect
application, world registration, database or ID operation was performed.

Complete normal Maven JAR/ZIP output, unchanged source/artifact snapshot, assembly/
resource audit, all 1,757 handler compilations, full production XML/schema/JAXB
loading (102,012 items / 4,091 contained-item definitions / 470 overrides) and
3,288-class / 172,218-reference linkage pass. 3,552 prior JAR entries remain
byte-identical. Only Offense.routine and Session.tick change existing behavior;
all 23 other Offense methods, its original switch method, all 100 other Session
methods and native counter/skill/effect execution are preserved. No managed data
resources differ; installed Commons remains unchanged.

Fresh external review:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/release-20261009-054657-699316`.
Canonical guarded delivery completed in external receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-source-build-20261009-055725-016513`.
Installed GameServer SHA:
`8fd18d49937f696015098638397de80ac529802740b35da753249ac7429b30c1`.
Installed Commons remains
`b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
Only the GameServer JAR was copied, unchanged from normal Maven output. Initial
process/client-setting hash guards refused before copying; fresh authorized
process inspection and a full passing inventory refresh preserved the two changed
settings files. The same source/artifact-bound review then passed guarded delivery.
All prior mods, client/configuration/managed resources/geometry/launcher remain
preserved. Affected generated caches were archived/invalidated by the canonical
gate. All 20 postinstall mod checks / 31 client hashes pass; 74 client/114 server
historical receipts are recorded. Server/client remain off.

**PB-VAL-016 native Gladiator counter activation, costs/cooldowns, recovery and
combat acceptance remain pending user testing.** Keep GameServer/client off.

Next separate review: **PB-PORT-005G Assassin**. Compare existing native rune
builders/finishers, chains, poison upkeep, utility and defense with the pinned
Rogue strategy before selecting missing behavior. Do not re-port the installed
PB-PORT-001/002 final gates solely because gameplay acceptance is pending.
Full class/world and item-ID release investigation remain unfinished.
