# Companion creation, supplies and recovery — 4 October 2026

Installed cumulative receipt: `backups/playerbots-recruitment-20261004-140949-271624`.
The server was stopped during concurrent revival work. The final refinement is
installed on disk; restart preference was requested and remains pending.

## Creation failure fixed first

Native `ItemFactory.newItem` now calls `PlayerBotItemIds`: each allocated item ID
is checked against persisted inventory and player IDs. A colliding persisted ID
stays reserved, and allocation continues. Database errors stop allocation rather
than risking custody. The existing companion transaction guard remains intact.

Live install: `backups/playerbots-recruitment-20261004-133452-341528`.
The effective native allocator skipped deliberately unreserved persisted item
191241. Its complete row remained unchanged. Bardoca's partial character was
finished under ID 106628, at Baby's level 39, with 15 saved items and 130 native
skills. The pending roster entry became ready only after the private inventory
and progress committed. Recovery backup:
`backups/playerbots-creation-recovery-20261004-bardoca`.

## Installed behavior

- Recovery supplies use native HP/MP instant, proc and ongoing healing effects,
  and cleansing. Thresholds are 90% HP / 85% MP before combat, 55% / 40% in combat.
- Positive self buffs are scored by weapon, class and role. Food/drink conflicts,
  effect slots, active buffs, class/level/race restrictions and native skill/item
  eligibility remain authoritative. Supplies also use normal learned class buffs.
- With Supplies enabled, dedicated Temporary actors replenish finite native
  stacks between fights: up to 20 recovery items and 10 per buff family, at most
  five buff families. Catalog selection uses real normal shops and crafting
  products; special/test/event items are excluded from automatic generation.
  Generated stock IDs enter the existing equipment provenance record.
- Owned alts receive no generated stock or build changes; they can use their own
  suitable consumables. Kinah and opt-in equipment/care purchase settings survive.
- Summon revives dead active companions at native 25% HP/MP with soul sickness,
  restores passives and relocates them. Explicit names address that companion.
  The native bar's `.bot summon all` and the party HTTP button select the owner's
  targeted bot; clearing the target addresses the whole companion party. A
  non-companion target rejects the operation with a useful message.
- When all companions are dead, automatic recovery waits for a living owner and
  ended combat. A full wipe waits for the owner's resurrection before regrouping.
  Other dead companions remain eligible for native learned resurrection spells.
- The concurrently installed `PlayerBotRevival` animation/observer repair remains
  intact. All 101 unselected entries in the final transaction were byte-identical,
  including its State class, `PlayerReviveService` and the item allocation fix.

## Verification and limits

Full source/companion regression checks pass (`target/playerbots-supplies-recovery/source-check.log`).
The final production XML catalog passed 45 class/level cases, including HP/MP,
ongoing recovery and ordinary build supplies; test/full-recovery/event exclusions
passed (`catalog-check-final.log`).

Live isolated native check revision 2 passed actual item HP healing, one-item
consumption and cooldown, selected-only revival, only-owner-alive recovery,
full-wipe recovery after owner revival, and combat rejection before any revival.
It also checked owned-alt stock preservation and finite/idempotent grants.
Receipt: `backups/playerbots-supplies-native-check2-20261004.txt`.
The final ordinary-catalog refinement and actual MP/buff casts still need the
revision-3 native check after startup. Client rendering/combat acceptance and the
broader class strategies remain unfinished.

The initial broad-template selection generated unwanted special/test supplies.
The stopped-server cleanup reverted 25 auto-generated soul-bound private stacks
on MagicDps, Healeru, Templaru and LeMuse, with zero saved effects to remove.
Original rows and restore SQL are backed up in
`backups/playerbots-supply-refinement-20261004`. Human/alt progress, equipment,
skills, quests and account/legion/market storage were unchanged.

All 15 refreshed installed-mod checks pass; client files, base server JAR and
launcher remain preserved. No complete source JAR replaced the installation.
