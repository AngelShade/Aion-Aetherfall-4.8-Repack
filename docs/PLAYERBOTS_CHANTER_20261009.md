# PB-PORT-005D: Chanter native mantra maintenance

## Confirmed missing behavior

This is a new bounded class strategy with a necessary Chanter-only combat
admission repair. Existing Aion code already classified aura/mantra toggles,
cast them through native SkillEngine, enforced buff conflicts/slot capacities,
and supported Chanter melee, healing, cleanse, resurrection and interrupts.
Those paths are retained. No new skill/build/equipment or owned-alt replacement
is performed.

The missing behavior was in `PlayerBotCombatBuffs.useful`: its unconditional
`skill.isToggle()` veto blocked every missing mantra in combat. Noncombat
mantras shared generic buff priority, so native support purpose was not used to
choose among learned available mantras. `PlayerBotSession.recipient/priority`
and final CastAction gates were reviewed before adding this slice.

## Exact upstream and native mapping

Pinned WotLK Playerbots revision:
`037c01418b5d01506917a3db9b44fd56ac5f965c`. Existing cached source hashes match
`third-party/playerbots/SHA256SUMS`:

| Source | Reviewed purpose | SHA-256 |
| --- | --- | --- |
| `src/Ai/Class/Shaman/Strategy/EnhancementShamanStrategy.cpp` | Combat support setup, learned proc/damage and melee fallback remain distinct | `966232ebc4727fcb6aa9bf810cdd0a2a470028c9d80045a9eb9a0335206b1354` |
| `src/Ai/Class/Shaman/Strategy/GenericShamanStrategy.cpp` | Learned support alternatives, cure/interrupt/purge/boost purposes | `7ad192cbdcb36190800a057af6f5e7092479ab9f81103ef92ec5ef2c0c3c7d9d` |
| `src/Ai/Class/Shaman/ShamanTriggers.cpp` | Missing aura/support detection and available empty-slot restoration | `815bd0fdffb52bb29c9ea261968adaa7ffa3ca058eda126d04e42deb94c059c0` |
| `src/Ai/Class/Shaman/Strategy/TotemsShamanStrategy.cpp` | Restore missing learned combat support: physical, defensive, mana, healing and caster support, with learned lower-level fallbacks | `4486343c55b377291d2379cf97441130aff706ad4a1e4ba3914dcb7e23679bc8` |

The fourth reference was imported for this bounded dependency from the already
inventoried untruncated pinned tree. Git blob
`8066d57b8cfb960db1b9141310f9dfcb4bcf1d91` and SHA-256 were verified before caching
the exact source under `third-party/playerbots/upstream`; the existing inventory
was not restarted.

Aion's mobile native mantra AuraEffect replaces the party-support purpose.
WoW totem objects, elemental slots, spell IDs, summon management and proc systems
are not copied. Native Chanter has three stable no-show mantra slots; existing
`PlayerBotBuffs.canAdd` prevents the controller's native eviction behavior from
replacing a held aura. Filling a missing slot uses only learned available skills.

## Implementation and preservation

`PlayerBotChanter` names the class strategy `chanter mantras`. It applies only
to CHANTER and wraps the existing Cleric/Spiritmaster/Sorcerer strategy mapping.
The same combat/noncombat composition and PASSIVE-order gate are retained.

The combat admission exception requires native TOGGLE + CHANT + NOSHOW metadata,
a self FRIEND/ONLYONE target and an AuraEffect. Existing caster HP/MP and cast
duration preparation guards remain. Other classes, toggles and non-mantra buffs
retain their earlier policy.

Read-only AuraEffect getters expose its linked skill ID and horizontal/vertical
range. All aura execution, periodic scheduling, party applications and packets
remain native and unchanged. The planner reads the linked payload and considers
alive/spawned native group members in the same map/instance and aura range,
including the native BOOST_MANTRA_RANGE stat.

Party-need ranking is an **Aion adaptation**, rather than a claim that WoW uses
the same weights: HP/MP recovery is preferred when nearby members need it;
physical/caster effects use native class composition; defensive effects respond
to injuries; walking support gains noncombat travel preference. A purely flight
mantra gets no new-slot priority for an entirely grounded eligible party.
Existing active mantras are never turned off, swapped or forcibly refreshed.

Combat setup is normalized to bands 26–32, below Aion interrupts, urgent recovery
and encounters. Noncombat setup uses bands 10–16. Resources, cooldowns, learned
skills, chain feasibility, target validity, stacking and slot capacity still pass
through the actual Session recipient and final CastAction gates. Missing native
payloads receive zero priority; unavailable candidates preserve normal fallback.

Melee rotation, native chains, offense, healing/cleanse/resurrection/reservations,
gear/build configuration, saves, quests, travel and all earlier installed mods
are unchanged by this bounded slice. Full Chanter class strategies, configurable
mantra replacement/assignment and full Playerbots/world parity remain open.

## Offline validation and delivery

`PlayerBotChanterCheck` compiles with the companion suite and exercises actual
Session recipients/priorities, final CastAction admission and engine arbitration
on world-free actors. 89 checks cover combat/noncombat admission, party HP/MP,
emergency-heal ordering, exact active-set retention, three slots, native conflicts,
flight/ground/travel, native range boosts/altitude/map/instance, costs/cooldowns,
class isolation and all six real mantra template/payload mappings. No native
casts, aura tasks/effects, world registration, database or ID operations run.

All 16 world-free suites pass (1,994 checks). Fresh complete Maven output,
complete assembly/resource audit, all 1,757 handler compilations, full production
XML/XSD/JAXB loading and 3,285-class / 172,096-member-reference linkage pass.
The review retains 3,543 prior JAR entries byte-identically, and no managed data
resources differ. External review:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/release-20261009-050410-563609`.

Delivery uses the canonical `tools/release-game-server.py --scope PB-PORT-005D`
workflow and fresh offline process/source/artifact/runtime/client checks, backups
and rollback. Guarded delivery succeeded; latest external receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-source-build-20261009-051146-587613`.
Installed GameServer SHA:
`e23b0c3d33bf54bf7f13a802f1199a1b19af7b5a9db2388790a028a579d98ba2`.
Commons SHA remains
`b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
The installed JAR is byte-identical to the normal builder output. Only this JAR
was copied; affected generated handler caches were archived/invalidated by the
canonical gate. All 20 postinstall mod checks/31 client hashes pass; 74 client/112
server historical receipts are recorded. Server/client remain off.
**PB-VAL-014 actual client mantra activation, party benefit and combat acceptance
remain pending user testing.**

Next separate class review is **PB-PORT-005E Templar**. Compare existing native
`PlayerBotTank` hate/opening/taunt coordination, defenses, positioning and chains
against pinned TankWarrior before selecting a missing behavior. Its paired
Gladiator strategy remains separately unfinished. Preserve all installed slices
while the full class/world and item-ID release investigation continue.
