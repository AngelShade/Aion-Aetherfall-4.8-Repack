# PB-PORT-005A — native Sorcerer single-target strategy

Status: **installed live and on disk** in cumulative receipt
`backups/playerbots-recruitment-20261004-234841-695964`. Retains tank position
`232048-163778`, custody `224922-627052`, spacing, engine composition and all prior
mods. This is one bounded **new port** slice of PB-PORT-005, not completion of all
classes or the whole Mage strategy family. User requested porting/installation
and will perform in-game testing. No server lifecycle or client replacement.

## Source and native mapping

Pinned upstream revision `037c01418b5d01506917a3db9b44fd56ac5f965c`; cached Mage
reference hashes verified. Sources under `third-party/playerbots/upstream`:

- `src/Ai/Class/Mage/Strategy/FireMageStrategy.cpp`: default fireball 5.3,
  frostbolt 5.2, moving fire blast 5.1, shoot 5; improved scorch 19, living bomb
  18.5, Hot Streak pyroblast 25.
- `src/Ai/Class/Mage/Strategy/GenericMageStrategy.cpp`: spell fallback nodes,
  low-MP Evocation 90, Fire/Frostfire offensive boost ordering 18/17.5.
- `src/Ai/Class/Mage/MageTriggers.cpp`: missing vulnerability/effect eligibility,
  including mutually exclusive Improved Scorch effects.

| Upstream purpose | Native Aion behavior installed |
| --- | --- |
| Consume an available proc before ordinary fillers | Learned, available native follow-up chains receive band 25; final native chain count/expiry/MP/cooldown checks remain authoritative. No Hot Streak aura is manufactured. |
| Establish useful magical vulnerability | Learned STATDOWN reducing magical or elemental resistance receives band 19 while its native family is absent. Unrelated stat reductions are excluded. Native duplicate-debuff/stack guards still decide final usefulness. |
| Maintain useful periodic damage | Native refresh/stronger-effect/final-tick policy receives band 18.5, then yields to direct fillers while the effect is healthy. Pure DoTs remain suppressed below existing 25% target-health boundary. |
| Maintain a usable filler/fallback sequence | Cast fillers precede instants while stationary; native instant attacks precede stop-to-cast fillers while moving, and quick direct damage finishes low-health targets. Only small bounded native damage-efficiency tie weights apply. Unavailable/unaffordable spells fall through the actual engine to other learned spells and weapon fallback. |
| Use caster offensive boosts | Native BOOST_SPELL_ATTACK, BOOST_MAGICAL_SKILL and positive BOOST_CASTING_TIME_SKILL/ATTACK changes are recognized. Vaizel's Wisdom, Boon of Quickness and Aetherblaze work through existing learned-buff recipient, health/MP, stacking and casting gates. Damage boosts use 18; haste-only boosts 17.5. |
| Recover mana before getting stranded | Existing self-recovery eligibility below 40% MP is retained. In combat the ranged caster gets priority 59, **normalized from WoW's 90** to stay below Aion encounter protection/escape at 60+. Critical health keeps the older band 21 so native survival wins. Other recipients, roles and noncombat recovery retain their installed paths. |

Aion has no direct WoW Fire specialization, Fire-immune filler rule, Hot Streak,
conjured mana gem, or execute-spell substitute in this slice. Its own learned
skills, native chain triggers and cost/condition checks preserve those decisions'
purpose. No foreign skill IDs, school-immunity assumptions or stat/resource writes.
Starter Mage shares this strategy; Spiritmaster and other classes retain theirs.
Owned alts are evaluated only through their existing learned skills/builds.

## Integration and preservation

New `PlayerBotSorcerer` is connected to `PlayerBotOffense.routine`, combat buff
eligibility, and Session support priorities. Session names the strategy
`sorcerer single target`, retaining PASSIVE and other order gates. Single-target
damage mapping is limited to native ONLYONE templates; existing AoE selection,
area toggle and pack-admission checks remain unchanged. Installed defenses,
interrupt/purge, threat hold, hazards, formation, targeting and class continuers
still surround the new routine.

Four existing methods in three classes transplanted; one new helper added.
CombatBuffs gains a cumulative class override over its existing base-JAR class.
**133 earlier override entries** remain byte-identical. Tick bytecode was separately
verified to retain every earlier operation apart from its two strategy-name
constants/calls and corresponding branch relocation. No enum switch mappings
were changed. Fresh agent **45** preloaded original classes, applied three
definitions atomically and retained five companion sessions and one human
connection. Existing settings/builds/inventories were not rewritten.

## Verification and remaining work

- Full server/command source compilation and offline companion suite passed.
  Final single-target/encounter-priority refinements were compiled into that
  source cohort and rerun against the final effective package.
- **54 production/engine/native-template checks** pass: chain admission,
  periodic/vulnerability upkeep, moving/stationary/finishing selection, actual
  learned-skill/MP planning, cooldown fallback, native boost admission and Session
  support priorities. Old installed code fails the filler-order regression.
- Effective regressions: final offense gates **44**, state strategies/continuers
  **49**, combat position **38**, encounter policy **35**, custody **85**.
- Null-returning observation captures six loaded classes; **193 methods match**
  installed disk, including the preserved tank and inventory repairs.
- Postinstall source audit verifies **51 pinned hashes and 163 effective methods**;
  the old contradictory rune gate remains inactive.
- All **46 settings files**, **31 client hashes**, base JAR, launcher and media
  preserved. Mod inventory passes **15 checks**, records 70 client/80 server
  receipts. Override SHA-256:
  `4d17853599c40b60ec215f02442d90edf66d288640e320c6feec23f98dd7607f`.

Artifacts under `target/playerbots-sorcerer`: `full-source-checks.txt`,
`effective-checks-final.txt`, `effective-PlayerBot*Check.txt`,
`old-baseline-regression.txt`, `tick-preservation-check.txt`, final package,
`loaded/effective-loaded.jar`, preservation JSON and postinstall audit. Receipt
holds hash-guarded preflight/runtime/rollback/install records. Reproduce bounded
staging with `stage_sorcerer_update.py`; tests are in the normal check script.
Edited existing helpers must be in runtime SCOPES on future updates; next fresh
attach update revision must exceed **45**.

**PB-VAL-009 actual class casts/MP/chain transitions and client acceptance remain
pending with the user.** No native tick, movement or cast was forced. Complete
Sorcerer AoE/CC/escape strategy and every remaining class are still open, as are
world bots, invitations, trade, travel and dungeon parity. Next concrete class
slice: **PB-PORT-005B, Spiritmaster learned single-target/pet strategy**, grounded
in cached GenericWarlock/AfflictionWarlock sources and native pet-order gates;
necessary pet coordination belongs to PB-PORT-008. Do not re-port 005A merely
because user testing is pending. Historical item-ID release investigation and
the separate wipe/summon rejection remain independent repairs.
