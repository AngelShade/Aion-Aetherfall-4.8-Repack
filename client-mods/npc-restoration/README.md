# Protectorate merchants and Ceramium exchanges

Custom Aetherfall restoration for the original English 4.8 client and server.
Retail inventories for the three dormant Balaur merchants were not recovered;
their stock and placement below are explicitly custom. All items already exist
in the native 4.8 client. No new item IDs, accounts, shadow databases or progress
storage are introduced.

| NPC | Behavior |
| --- | --- |
| Kyrshaka 800371 | Noble Garden; 13 Protectorate weapons, native price of 12 Protectorate Coins each. |
| Yurshaka 800372 | Noble Garden; 20 Protectorate Recruit armor pieces: chest 8, legs 6, gloves/shoulders/boots 4 coins each. Custom prices. |
| Norshaka 800417 | Noble Garden; 20 native level-60 Commando armor pieces per faction, at their existing AP/Mithril prices. Only the buyer's faction is displayed and accepted. Custom stock assignment. |
| Inharet 801537 | Relocated beside the existing Enshar fragment exchanger; native quest 23360. |
| Bekasis 801538 | Relocated beside the existing Cygnea fragment exchanger; native quest 13360. |
| 801494 / 801495 | Compatible aliases for the matching native quest. No duplicate hub spawns. Obsolete Trade In function replaced by the working quest service. |

The merchants and exchanges require level 60. Balaur purchases require the
existing statue's Drakan transformation (skill 300), inside Tiamat's Stronghold.
Checks cover dialogs, purchase packets and the shared purchase service used by
internal callers. Distance uses the native talk-range helper, including object
bounding radii. Norshaka also validates faction stock on purchase, independently
of which tab the client requests.

Both Ceramium quests consume ten Fragmented Ceramium. They use the existing
native MEDAL level-6 reward table: 33% one Ceramium Medal, 67% one Rusted Medal.
This reuses the installed 4.8 rule; it is not a claim about recovered retail odds.
The server's normal collection/reward path owns payment, inventory handling,
completion, repeats and SQL persistence. An unsupported SET_SUCCEED request
cannot bypass collection. Decline closes the native dialog.

The installed native quest metadata declares no preceding or subsequent quests
for either exchange. Both remain standalone repeatables. Existing 15210/25210
Shugo exchanges and other regional quests remain unchanged.

## Maintained files and preparation

`catalog.json` binds custom client tab IDs 60010–60013 to the corresponding
server goods lists. The server has matching mappings, item acquisition prices,
native quest registrations, spawn records and a narrowly scoped admission helper.
The English NPC/quest dialogs state the costs and both outcomes. The client quest
metadata enables the original quests at level 60 in the current hub regions.

1. Build the server with `tools/build-components.ps1`, selecting `game-server`
   and a fresh external output directory. No test or gameplay process is started.
2. Run `prepare.py --client CLIENT --output EXTERNAL_STAGE`. It changes only the
   four known native archives and verifies unaffected records/compressed members.
   A dialogue-only correction may use `--reuse PREVIOUS_STAGE` to retain verified,
   unaffected NPC/item/quest archives.
3. Run `graphics.py --client CLIENT --stage EXTERNAL_STAGE`. It uses the existing
   Graphics Menu ownership resolver and stages a fresh restore baseline, package
   payload and ownership metadata. Previous backups remain untouched; Game.dll,
   renderer/cursor code, signatures and UI layout resources are unchanged.
4. Run `delivery.py prepare-server --build NORMAL_BUILD --output SERVER_STAGE`.
   It extracts exact normal builder outputs and rejects unrelated runtime XML,
   class or dependency changes. Incremental repairs accept the existing admission
   helper and preserve unchanged outputs with hash guards instead of copying them.
5. Install each component separately with `delivery.py install --component server`
   or `client`, its `--stage`, a fresh external `--backup`, and `--client CLIENT`
   for the client component. Process enumeration must succeed and the affected
   component must be closed normally. A complete backup precedes any copy; input
   hashes and the server build manifest are checked again and every copied file
   is verified.

When another chat is changing the client, wait for its installation to finish.
Reprepare against the latest actual client and rebase the Graphics Menu records;
never install an older prepared English archive over that work.

## Validation and rollback

Normal compilation, the affected XML schemas, all 73 inventory rows, native
quest/reward contracts, unrelated archive-record preservation and exact delivery
hashes are offline checks. They do not establish live UI or gameplay acceptance.
The code audit passed 431 assertions against actual JAXB/data/state classes and
21 against the compiled admission helper and native position code. An independent
decode through the installed launcher's C# code passed all four changed binary XML
documents and nine English dialogs, including native shop controls. The resulting
server correction fixes the talk-range boundary and shared purchase admission;
only the exact normal-builder game-server JAR was replaced. Audit evidence is in
external `diagnostics/npc-restoration-code-audit-20261021`; the correction receipt
is `archives/server/backups/npc-restoration-code-audit-20261022/receipt.json`.
The owner verifies placement, shop opening, transformation expiry, faction stock,
costs, insufficient inventory/materials, decline, completion and repeat/relog.

The external receipt records every destination and its prior/new hash. With the
affected component closed normally, restore the backed-up affected cohort. The
two newly installed server XML files have no prior hash and must be removed only
as part of that explicit rollback. Keep every previous recovery package.
No full/test repack, prepared patch, release or launcher executable is updated.
