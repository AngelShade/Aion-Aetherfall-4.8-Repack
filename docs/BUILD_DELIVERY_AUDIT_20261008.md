# Build and delivery workflow audit — 8 October 2026

## Cause

**PB-BUILD-003** is a delivery-workflow repair, independent of the next Playerbots
class port. Maven compiled the maintained source correctly. The earlier delivery
process allowed assembly/resource packaging to be optional and manually selected
which runtime files to copy. The override-to-source transition verified prior
classes and focused behavior, but did not load all runtime static data or compile
all runtime handlers against the new core. Consequently a valid complete JAR
could accompany obsolete XML/schema/handler files. NAV-004 retained that same
incomplete resource baseline. Cross-chat continuity documented hashes but did not
require one complete release gate.

PB-BUILD-002 fixed the decomposable startup failure. This broader audit found ten
additional byte differences between the complete maintained assembly and runtime.
There were no runtime-only active Java/XML/schema files in the audited managed
handler/static/script folders. The five quest/skill/command resources below were
already committed on 1 October in `411009f3d00bf47a1eea1ced9b0234a886dd5f1d`
(Backport upstream skill and quest fixes), but had not been copied to runtime.

## Resources delivered

| Resource | Maintained source behavior delivered |
| --- | --- |
| `data/handlers/admincommands/Zone.java` | Current ZoneTemplate/ZoneAttributes API, replacing obsolete ZoneInstance methods |
| `data/handlers/ai/portals/HouseGateAI.java` | Current world-map NO_RETURN_BATTLE attribute API |
| `data/handlers/zone/pvpZones/PvPAreaZone.java` | Current ZoneHandlerArea annotation |
| `data/handlers/zone/_1012SensoryArea.java` | Current ZoneHandlerArea and quest registration |
| `data/handlers/consolecommands/Deleteskill.java` | Correct deleteskill command syntax from 1 October |
| `data/handlers/quest/altgard/_2209TheScribbler.java` | Missing NPC talk registration from 1 October |
| `data/handlers/quest/eltnen/_1354PraticalAerobatics.java` | Progress-safe ordered ring handling from 1 October |
| `data/static_data/fly_rings/fly_rings.xml` | Matching repaired ring geometry from 1 October |
| `data/static_data/skill_tree/skill_tree.xsd` | Optional skillLevel attribute/default from 1 October |
| `data/static_data/npcs/npc_templates.xml` | Source formatting/comments; parsed NPC IDs and values were identical |

## Required workflow and evidence

Use [the canonical release workflow](BUILD_DELIVERY_WORKFLOW.md). It requires
fresh normal Maven output, complete assembly resources, immutable source/artifact
hashes, automatic complete resource review, all-handler compilation, full isolated
production static-data loading and guarded offline delivery. An operating-system
lock covers the canonical build/review/copy transaction across chats.

Verified in this audit:

- Fresh normal Maven reactor build succeeds; manifest binds 5,558 source/resource
  inputs and four normal JAR/ZIP artifacts. Compiled outputs were not rewritten.
- Complete assembly review covers 2,533 managed data files, excluding installed
  geometry assets. All 1,757 handler Java sources compile against the new core
  and Commons; no handlers were executed or compiled handler classes delivered.
- Fourteen existing world-free bot regression suites pass: 1,818 checks.
- Production XML merge, full XSD validation and JAXB callbacks pass against the
  exact proposed runtime: 102,012 items, 4,091 decomposable definitions, 470 overrides.
- The complete production static-data check was repeated against actual installed
  files after delivery and passed with the same counts.
- Full package linkage: 3,281 classes and 171,808 executable member references.
- Negative checks reject a competing canonical release, changed source manifest,
  altered artifact hash, and incomplete resource selection before any copy.
- Successful fresh shutdown inspection, verified backups and exact builder-output
  copies completed. Thirteen affected generated XML/handler cache files were
  archived and invalidated so native loaders rebuild them from the delivered data.
- All 3,549 non-manifest JAR entries remain byte-identical. No compiled gameplay
  changes were introduced by this resource/workflow repair. Commons, launcher,
  geometry, configuration, owned-alt settings, database and client are preserved.
- Refreshed installed inventory passes 20 checks and all 31 current client hashes;
  74 client and 110 server historical receipts are recorded.

Receipt: `playerbots-source-build-20261008-225416-105523`, under external
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups`.
GameServer SHA-256:
`3a7d8d17547f08ecaabccbb44a3cb0550e4b45b07808261a16b77a7655d6b654`.
Diagnostics: external `diagnostics/delivery-workflow-audit-20261008`.
Build/review: external `staging/target` and `staging/output`,
`release-20261008-224135-897113`.

## What the two item IDs meant

The earlier PB-BUILD-002 additions are **188054343 — Vasharti Legionnaire's Weapon
Box** and **188054344 — Vasharti Brigade General's Armor Box**. The maintained
PR200 decomposable tables already referred to these parent boxes; the runtime
item-template catalog lacked their definitions. These are catalog additions,
not item grants. All 102,010 existing item definitions were unchanged. See
[the static-data repair](SOURCE_BUILD_STATIC_DATA_20261008.md).

No server/client startup, restart, attach, database update or gameplay fixture was
performed. Actual startup, bot movement, quest behavior and box opening remain
pending user testing. Next independent class review remains PB-PORT-005C Cleric;
full Playerbots parity and the item-ID release investigation remain unfinished.
