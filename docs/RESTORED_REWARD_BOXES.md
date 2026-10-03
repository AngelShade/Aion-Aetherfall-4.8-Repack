# Restored native reward boxes

Source update on 3 October 2026: the PR #200 backport now stores these sixteen
definitions in `local_decomposable_items.xml` using weighted `set` alternatives
and branch-level class/faction conditions. Their contents are preserved. The
implementation and delivery history below describes the earlier restoration;
current migration checks and deployment instructions are in
[DECOMPOSABLE_PR_REVIEW.md](DECOMPOSABLE_PR_REVIEW.md).

Research and source restoration completed on 3 October 2026. All sixteen
original IDs were filled in `decomposable_items.xml`. No replacement IDs,
selection dialogs, new items or new stats were introduced. The sixteen restored
definitions are now active on the running server for the explicitly requested
mail test. The full Season Pass was subsequently installed after the user confirmed Aion was closed.

## Contents and opening behavior

| Original ID | Contents per opening | Behavior |
| --- | --- | --- |
| 188052187 Blessed Daeva's Composite Manastone Pouch | One composite manastone from 36 outcomes, levels 30/40/50 | Weighted random; double-click |
| 188052555 Greater Composite Manastone Chest | One level-60 composite manastone from 36 outcomes | Weighted random; double-click |
| 188053068 Elite Greater Composite Manastone Chest | One level-60 manastone type from 11 outcomes; outcome quantity is 1, 3, 4 or 5 | Weighted random; double-click |
| 188053975 Stigma Box | One normal stigma bundle matching the character's advanced class | Automatic class match; open the awarded bundle for its random stigma |
| 188053976 Greater Stigma Box | One greater stigma bundle matching the character's advanced class | Automatic class match; open the awarded bundle for its random stigma |
| 188053979 Gladiator Stigma Bundle | One chargeable stigma for Gladiator, from 17 outcomes | Weighted random across normal/greater/major grades |
| 188053980 Templar Stigma Bundle | One chargeable Templar stigma, 17 outcomes | Same opening behavior |
| 188053981 Assassin Stigma Bundle | One chargeable Assassin stigma, 17 outcomes | Same opening behavior |
| 188053982 Ranger Stigma Bundle | One chargeable Ranger stigma, 17 outcomes per faction | Preserves the faction's skill variants |
| 188053983 Sorcerer Stigma Bundle | One chargeable Sorcerer stigma, 17 outcomes per faction | Preserves faction variants |
| 188053984 Spiritmaster Stigma Bundle | One chargeable Spiritmaster stigma, 17 outcomes per faction | Preserves faction variants |
| 188053985 Cleric Stigma Bundle | One chargeable Cleric stigma, 17 outcomes per faction | Preserves faction variants |
| 188053986 Chanter Stigma Bundle | One chargeable Chanter stigma, 17 outcomes | Weighted random across grades |
| 188053987 Gunslinger Stigma Bundle | One chargeable Gunslinger stigma, 17 outcomes | Weighted random across grades |
| 188053988 Aethertech Stigma Bundle | One chargeable Aethertech stigma, 17 outcomes | Weighted random across grades |
| 188053989 Songweaver Stigma Bundle | One chargeable Songweaver stigma, 17 outcomes | Weighted random across grades |

Every item retains its existing native `decompose` action, level requirement,
casting delay and cooldown. None is a choose-one box. The matching English 4.8
client describes double-click opening; the generic stigma boxes also explicitly
mention right-click. Using the normal client item-use action invokes the existing
server opening handler. No key or Kinah payment is added.

The cash class bundles' published grade totals are 30% normal, 50% greater and
20% major; individual stigmas have different weights. Each gives exactly one
stigma. The named class is fixed by that bundle's ID. The two generic boxes
automatically give the opener's advanced-class bundle. Base classes have no
eligible generic-box outcome and cannot consume it to receive an arbitrary
class's item.

## Evidence and its limits

The version-specific [4.8 pouch page](https://aioncodex.com/48/item/188052187/),
[4.8 Greater chest page](https://aioncodex.com/48/item/188052555/), and
[4.8 Elite chest page](https://aioncodex.com/48/item/188053068/) match the installed
client definitions and opening descriptions. The archived 4.8 pages do **not**
publish their contents tables.

Exact outcomes and weights were read from each original ID's Codex US page and
the public contents-table endpoint linked by that page: [pouch](https://aioncodex.com/us/item/188052187/),
[Greater chest](https://aioncodex.com/us/item/188052555/),
[Elite chest](https://aioncodex.com/us/item/188053068/),
[Stigma Box](https://aioncodex.com/us/item/188053975/),
[Greater Stigma Box](https://aioncodex.com/us/item/188053976/), and
[Gladiator cash bundle](https://aioncodex.com/us/item/188053979/), with the analogous
exact-ID page for each of the remaining ten classes.

All returned IDs exist in the matching server/client catalog. All returned
stigmas are the 4.8 chargeable templates, and all faction/class restrictions
were checked. Published weights total 100% for every eligible distribution and
are copied without rebalancing. The Elite chest's quantities are fixed per
outcome, not an invented range.

Codex is a third-party extracted-data database, not NCsoft. The detailed tables
come from its US dataset, while compatibility/opening behavior come from the
archived 4.8 and installed original client. This supports the implemented native
contents but does not independently certify that every published weight is
identical to NCsoft's historical 4.8 server odds. That limitation is retained in
the checked-in source manifest; no claim of publisher-certified probabilities
is made.

`client-mods/season-pass/box-content-sources.json` records all sixteen source
pages, content-table URLs, download SHA-256 values, item IDs, quantities, weights
and class/faction conditions. Raw page/JSON evidence is saved under
`output/season-pass/box-research`. The original English tooltip keys and archive
entry hashes are in `output/season-pass/empty-box-client-descriptions.json`.

## Native implementation

The schema already supports per-outcome weights and per-item class/faction
conditions. The generic boxes use one collection with eleven class-filtered
children. This grants exactly one eligible class bundle without randomly
selecting an ineligible class collection. Cash bundles use seventeen weighted
collections; faction-specific versions share the same outcome weight with
mutually exclusive race filters. The existing `DecomposeAction` grants only the
eligible child. Thus a faction never rolls the other faction's skill or an empty
outcome. No shared opening code was changed.

All unrelated box definitions were compared and preserved. The resulting file
SHA-256 is `e31f0b80342f348bc7da0af9baedd6b33d3775c58f55f3dae683f3f4f2b62762`.
The Ascendant Dawn reward slots and existing reward pools remain unchanged;
these restored native IDs are now available for their intended acquisitions
and future catalog configuration.

## Checks and deployment

`restore_empty_boxes.py` reproduces/verifies the sixteen definitions and refuses
to overwrite other nonempty content. `verify_restored_boxes.py` checks exact
published outcomes exhaustively for both factions and all relevant classes,
including fixed quantities and native icons. It writes small fixtures copied
from the real XML for the production JAXB check.

```powershell
python client-mods/season-pass/restore_empty_boxes.py
python client-mods/season-pass/verify_restored_boxes.py
javac -encoding UTF-8 -cp "game-server/target/classes;target-deploy/game-server/libs/*" -d output/season-pass/check-classes game-server/test/com/aionemu/gameserver/services/SeasonPassBoxCheck.java
java -cp "output/season-pass/check-classes;game-server/target/classes;target-deploy/game-server/libs/*" com.aionemu.gameserver.services.SeasonPassBoxCheck
```

Passed: 7,792 exhaustive restoration checks, 13,439 native schema/JAXB/weighted
selection checks, and the existing 4,289 season reward checks. The checks use
isolated fixtures. No live boxes were automatically opened.

The server-v9 bundle includes the exact restored `decomposable_items.xml` and
the existing pass build. Its installer validates the deployed original hash,
backs up the box file before replacement, and restores it if installation fails.
Item and pet templates are dependencies, not copied replacements. Older server
bundles are superseded. Installation, backup, dependency/later-file protection
and injected-failure rollback passed on disposable deployment copies using
`verify_server_install.ps1`. Live in-game opening remains the player's test.

## Authorized Baby test mail

On 3 October 2026 the user requested one of each original box in Baby's mail.
The running server resolved Baby as character 9403, level-37 Gladiator, offline
with an empty mailbox. All sixteen templates and available mailbox capacity
were verified before delivery. The previously empty definitions were loaded
with the production schema/JAXB mechanism used by `reload decomposables`, after
comparing every unrelated live bundle and selectable bundle with the new data.
Only the sixteen requested definitions changed. The deployed XML was backed up
to `target-deploy/game-server/backups/box-test-1790998928608/decomposable_items.xml`.
GameServer was not restarted and its JAR was not replaced.

`game-server/tools/SeasonPassBoxMailAgent.java` sent sixteen Black Cloud mails,
each with one box, through `SystemMailService`. Every resulting persisted mail
and inventory attachment was queried and verified: item ID, count 1, owner 9403,
mailbox location 127 and unread state 1. Delivery receipt:
`output/season-pass/baby-box-delivery-20261003.txt`. The request marker and item
IDs prevent resending already recorded test mail; any inconsistent or collected
attachment requires inspection rather than blind retry. The delivered boxes
retain their original item requirements, and received stigmas remain specific
to the named class. No Kinah or access level was changed.
