# PB-PORT-005E: Templar native pressure protection

## Confirmed gap and bounded scope

This is a new bounded class port with a necessary hybrid-skill integration.
Current `PlayerBotTank` already maintains native hate priorities, opening waits,
loose-target taunt recovery and multi-tank coordination. Existing melee/chains,
defensive panic, group/pet healing, cleanses, reservations and final native skill
conditions are retained. Those installed slices were compared before porting.

The missing behavior is proactive Templar protection. `PlayerBotDefense.policy`
previously offered Templar defenses only below 35% HP. A missing learned block
defense could not be prepared under physical pressure at healthy HP, and a
low-health shield was not selected before the generic panic threshold.
Iron Skin and Empyrean Shield are correctly classified CLEANSE, but their shield
purpose was discarded whenever the recipient had no removable debuff.

This slice adds physical block preparation and low-health native shield use,
including a threatened party member when the skill's native targeting allows it.
It does not implement complete Templar rotations, stance/replacement policies,
reflection, damage-redirection protection, configurable tank assignment, pull
readiness, Gladiator or full Playerbots parity.

## Exact upstream and native mapping

Pinned revision: `037c01418b5d01506917a3db9b44fd56ac5f965c`.

| Exact upstream source | Purpose read | SHA-256 |
| --- | --- | --- |
| `src/Ai/Class/Warrior/Strategy/TankWarriorStrategy.cpp` | Shield block at INTERRUPT+1, low-health shield wall, critical recovery, party protection; learned alternatives retain melee/reach fallback | `27dd0a730cf2897ee92de1588767d0c705918342d9c998612a53a0d306b99805` |
| `src/Ai/Class/Warrior/WarriorTriggers.h` | ShieldBlock is a missing-buff trigger; native cast availability and existing buff gates remain distinct | `4e7b1de80f4bc9cca2079d6c73cd6800be992a6b13122fd4f0b48b44b774991a` |
| `src/Ai/Class/Warrior/Strategy/GenericWarriorStrategy.cpp` | Retain reach-melee and emergency control recovery alongside class defenses | `df3902b0a4bbb156325d5e1d2e00abb790eaf3cd1836b1d2582c56a72353064e` |

The two additional sources came from the already inventoried complete pinned
tree; Git blobs `4396bc59d4277d0caf11ef41ff8f016c2d57d44d` and
`5d7427d1b32aaec8e39097fe40c83faae410cc3d` were checked before caching the exact
bytes and recording SHA-256. This is a bounded dependency review, not a repeated
subsystem inventory. GPL attribution remains in `third-party/playerbots`.

Native examples: Shield of Faith 2974 uses finite AlwaysBlock charges and a real
offhand-shield condition; Panoply of Protection 3069 protects a legal native party
target; Empyrean Providence 2922 has native group recipients; Iron Skin 3127 is
cleanse plus all-hit shield; Empyrean Shield 3168 is cleanse plus physical-only
shield and requires native DP. Their existing effect execution remains untouched.
Empyrean Armor remains a native healing action. Aion reflectors deal reflected
damage and are not assumed to be WoW spell reflection; that mapping is deferred.

## Implementation and preservation

`PlayerBotTemplar` names `templar protection`, wrapping all earlier class strategy
names. It applies only to TEMPLAR active non-toggle DEFENSE or CLEANSE entries with
native SHIELD or ALWAYSBLOCK and no healing payload. The shared installed defense
policy remains unchanged; the new score is combined with it for DEFENSE entries.

Native pressure is read from visible allowed attackers assigned to the affected
recipient. Dead/unspawned, other-map/instance and farther-than-40m actors are
excluded. Casting NPCs must actually direct a hostile native cast at the target;
their native physical/magical skill type determines coverage. Ordinary attacks
use the NPC's native attack type. Physical block charges are not newly spent on
magical pressure. Read-only `ShieldEffect.getHitType` distinguishes EVERYHIT,
PHHIT and MAHIT. All three existing shield execution methods remain unchanged.
Conditional normal/back/skill/controlled-hit shields require a separate adapter.

The **Aion adaptation** uses proactive block band 41, low-health shield band 75
below 50% HP, and the existing emergency band 91 below 35% HP. The new policy needs
actual pressure and combat; existing noncombat emergency DEFENSE behavior stays
unchanged. Loose-target taunt recovery remains above proactive block, and critical
native heals retain precedence. No WoW spells, rage, equipment or build mutations
are introduced; only learned skills can reach the unchanged final cast gates.

Hybrid integration combines the existing heal/cleanse score with native defensive
usefulness in `PlayerBotHealing.priority`. Existing recipient selection and final
`Healing.useful` therefore retain a shield's purpose without reclassifying it or
removing its cleanse/reservation behavior. Existing stacking/effect-slot checks
prevent replacement of an active shield. Resources, cooldowns, equipment, DP,
chains, target validity, reservations and actual skill execution remain native.
No new effects, damage, healing, costs or reservations occur during planning.

## Validation and delivery

All 71 new `PlayerBotTemplarCheck` production recipient/priority/final admission
checks pass against the complete builder output. The 16 existing suites also pass:
17 suites / 2,065 checks. Existing defense and healing checks separately pass all
64 assertions. 58 companion test sources compile. No casts/effect application,
world registration, database or ID operations were performed.

Complete normal Maven JAR/ZIP output, source snapshot, assembly/resources, all
1,757 handlers, full production XML/schema/JAXB load (102,012 items / 4,091
contained-item definitions / 470 overrides), and 3,287-class / 172,193-reference
linkage pass. 3,549 prior JAR entries remain byte-identical. Only Defense.priority,
Healing.priority and Session.tick change existing behavior; ShieldEffect adds
one getter and preserves all four existing methods, including the constructor.
No managed runtime resources differ. Source and builder outputs remain bound by
the fresh release manifest.

Review:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/release-20261009-053215-901590`.
Canonical guarded installation completed in receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-source-build-20261009-053655-481400`.
Installed GameServer SHA:
`8dda6e9598f8027915613f1a93cfc610dbbdeb6ec8c2181a2b395ee9fadf650b`.
Installed Commons remains
`b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
Only GameServer was copied, byte-identical to the unchanged normal builder output;
Commons build metadata alone differs, with every other entry identical, so its
runtime file was preserved. All earlier mods, client files/settings, configuration,
managed data and geometry remain preserved. Affected generated caches were
archived/invalidated by the canonical gate. Postinstall inventory passes all 20
mod checks / 31 client hashes; 74 client/113 server historical receipts recorded.

**PB-VAL-015 actual Templar block/shield activation, native costs, party benefit
and combat acceptance remain pending user testing.** Keep GameServer/client off.

Next separate class review is **PB-PORT-005F Gladiator**, comparing current native
offense, chains, drains, AoE safety and defense with the pinned Warrior damage
strategies before selecting an actual missing behavior. The other class/world
tracks and item-ID release investigation remain unfinished.
