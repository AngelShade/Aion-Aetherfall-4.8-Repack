# The Unbroken Field

Sixteen custom quests continuing **The Writing on the Wall (1198)** across
Verteron. Start **The Letter Left Open (14980)** with Spatalos after completing
1198. The final quest, **The Unbroken Field (14995)**, closes the investigation
with him. The chain follows the existing letter's threat against the Observatory
and the Aetheric Field. This is original Aetherfall story content.

| Quests | Route | Story |
| --- | --- | --- |
| 14980–14982 | Citadel, Belbua's Farm, Northern Tolbas, Tolbas Village | Follow the concealed cargo and copied escort requests. |
| 14983–14985 | Tolbas, Dukaki Settlement and Mine | Expose the copied seal, trace the ore road and break the diversion. |
| 14986–14989 | Geolus Campsite, Frillneck Woods, Contaminated Swamp, Pilgrims Respite, Miraju's Holy Ground | Aid the people caught in the diversion and follow the cargo east. |
| 14990–14991 | Tursin Outpost and supply grounds | Discover the transfer point and prepared Odium. |
| 14992–14994 | Cantas Coast and Mysterious Shipwreck | Stop Kinsgreen's collection party and secure the staged delivery. |
| 14995 | Verteron Citadel | Correct the watch records and close the case with Spatalos. |

Minimum levels rise from 12 to 20. The Tursin grounds and named enemies retain
their existing difficulty; bring help if needed. The quests use normal experience
and Kinah rewards, normal quest slots, sequential prerequisites and native saved
quest progress. Players who previously completed 1198 can start normally.

`arc.json` contains the authored story and objectives. `bindings.json` maps its
44 existing actors and objects to native client keys, current English labels and
existing dictionary links. Where no dictionary entry exists, the journal uses the
native English label; no imaginary dictionary key is introduced. Meliodas uses
the existing `STR_DIC_LA82` link because `STR_DIC_N_Meliodas` resolves to Roseino.

Run `python client-mods/custom-quests/writing-on-wall/generate.py` after editing
these sources. It produces the 16 standard packages in `../Quests`, the server
templates in a marked block of `game-server/data/static_data/quest_data/quest_data.xml`, and
16 independent scripts under `game-server/data/handlers/quest/verteron`.

The existing launcher discovers all packages inside **Custom Quests**. The
existing native quest engine loads the scripts. There is no new engine, core
override, launcher change, DLL patch or progress database.
Quest IDs 14980–14995 and title string IDs 1801365–1801380 are reserved for this
chain. Server templates use the existing quest data table.

Object admission is limited to the active step. Hunts count only their declared
targets in Verteron, with the existing saved stage and counter variables. Reward
and completion use normal server authority. The chain neither completes the
original campaigns nor changes native NPCs, spawns or other quests' items.
An Honest Seal (14983) uses **Copied Seal Impression (182299980)**, authored in
`loot-item.json`. The stolen Tolbas goods remain native loot containers. The
impression drops at 100% while needed, using the existing quest-drop system;
opening the crate never sends a quest dialogue. Pickup advances the saved 0/1
stage, and Santenius requires and takes the actual impression when reporting.
Native quest cleanup removes it on abandonment. Relog reconciles the stage with
the inventory, including recovery if a player discards the impression. Existing
accepted quests continue without abandoning or losing their prior chain progress.
The new item clones the native Tolbas seal's restrictions and artwork, with its
own name and description; neither Q1155's supplies nor Q1158's seal is reused.

`update_loot.py prepare --client <original> --build <normal-output> --output
<external-stage>` merges the changed package through the existing loader,
creates the item and English strings, rebuilds the matching native icon index,
and updates the Graphics menu's active/package/recovery resources together.
Only the three affected server resources are taken from the normal assembly;
the existing server JAR and quest-container AI are retained. After normal client,
launcher and GameServer closure, use `update_loot.py install --stage <stage>
--backup <external-backup>` for guarded delivery and copied-file verification.

Delivery requires the affected original client and GameServer closed normally.
Use the normal server builder and its exact resource outputs. Load the client
packages through the existing Custom Quests loader, preserving its ownership
records and other installed mods. Prepare backups and copied-file receipts
externally. Repack delivery and publication are separate requests.

In-game acceptance must cover the start after an already completed 1198, quest
markers and links, object interactions, combined hunt counts, the two consecutive
hunts in 14993, relog with partial counts, abandon/reaccept, normal reward claims,
and native quests concurrently using the same objects. Native campaigns and
open-world respawns continue independently of this character's investigation.

Native objective/update text must remain compact inside each HTML paragraph.
The authoring tool emits inline rich text with explicitly left-aligned objective
paragraphs; pretty-printing nested font tags introduces padding into automatic
objective notices. Update installed dialogue with `update_dialogs.py prepare`,
then `update_dialogs.py install` after normal client/launcher closure. This uses
the existing Custom Quests loader and preserves the Core UI, server and progress.

Hunt summaries use the classic live counter parameter `[%3*stage+2]` and the
authored quota, so the journal/tracker shows current/required progress. Native
guidance conditions test `SECTION_0` (stage) and `SECTION_1` (kill count) separately;
`Progress(stage)` alone matches the entire packed state and loses the location
after the first counted kill. The CSV `num` column counts following NPC keys,
not required kills. Collection objectives retain `[%collectitem]` with the
authored item/count and use the native `dropMonster` source format supported by
the same existing Custom Quests CSV loader.

For a counters/map-guidance correction, run `update_dialogs.py prepare --tracking
--client <original> --output <external-stage>`, then its `install --stage <stage>
--backup <external-backup>` command after normal client/launcher closure. This
updates only changed dialogue/guidance packages, the two native quest archives,
ownership stamps and Graphics recovery metadata. GameServer may stay running;
quest scripts, saved progress, item assets/index and the quest engine stay intact.

Completion confirmations remain handled after the native reward service marks
the quest complete. A declined follow-up dialogue or late/repeated reward click
returns to the NPC quest list; it never invokes rewards again or falls through
to treating action 23 as a missing HTML page. This is local to the 16 independent
quest handlers and does not alter the shared engine. Prepare these scripts with
`update_completion.py prepare --build <normal-output> --output <external-stage>`,
then `update_completion.py install --stage <stage> --backup <external-backup>`
after normal GameServer closure. Client resources need no update. The native
script cache refreshes from the newer source timestamps on the next normal start.
