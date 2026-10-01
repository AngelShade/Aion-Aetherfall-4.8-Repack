# Quest NPC spawn repairs (2026-10-01)

The reported 44 groups were repaired through spawn restoration, corrected quest
targets/hand-ins, and detection of NPCs that existing handlers actually summon.
The analyzer continues to report missing triggers and ignores commented-out spawn
calls. Checking inactive calls also revealed and fixed the Asmodian Crucible
stage-7 prerequisite boss, which previously used the Elyos variant.

## Data and handler changes

- Thirteen KillSpawned groups are counted only when their triggering object is
  available. Only the first monster ID is summoned, matching KillSpawned behavior.
- Raksang's faction/route selection and Rentus walker spawns are recognized.
- Twelve academy hand-ins use the installed 4.8 client's reward NPC names and
  matching already-spawned templates.
- Terath Dredgion crew/officer objectives use the same-named 233xxx instance
  variants. Kunax objectives use the actual timer-spawned boss, 287249.
- Commander Pashid is restored at the Warfront bastion coordinates from the
  AionGermany 5.8 spawn reference (the position matches the local 4.8 geometry).
  His defeat finishes the battle with the existing boss reward path.
- Pinebeak appears in the existing Q46500 encounter sphere in Gelkmaros.
- Balaur Limbcleavers use the installed client_world_df4.xml positions.
- Leif appears under Asmodian control of Lepharist Territory, using the client
  position with a terrain height taken from neighboring server spawns.
- Missing direct-portal guards are restored beside the existing guards in
  Theobomos and Brusthonin. These are custom placements anchored to existing camps.
- Ominous Darkness is spawned at Beritra's defeated position and uses
  quest_use_item so players can obtain the registered quest drops. This timing is
  a custom repair; the object's original retail position was not available.
- Crucible progression now continues past stage 9 round 3 through Andre, Kamara,
  the stage-10 waves, and Vanktrist. Record keepers and the stage-10 arbiter connect
  the stages. Client record-keeper coordinates and the existing arbiter teleport
  anchor the arena. The new waves are a basic quest-access continuation, not a
  verified reconstruction of every retail mechanic or late-round score award.

## Approved legacy NPC placements

The user approved restoring legacy quest NPCs beside current instance entrances.
These placements do not claim to be retail 4.8 locations.

| Map | Location | NPCs |
| --- | --- | --- |
| Cygnea / Signia, 210070000 | Beside Rentus entrance, approximately 1145, 798, 562.75 | Ancanus 205842, Anarin 205947, Crispin 800165, Kurius 801541, Liponia 801543 |
| Enshar, 220080000 | Beside Rentus entrance, approximately 1978, 2021, 328.70 | Skafir 205864, Tepes 800170, Otur 801545, Maeve 801547 |
| Cygnea / Signia | Beside the Kaldor introduction giver at Aequis Headquarters | Saparinerk 800958 |
| Enshar | Beside the Kaldor introduction giver at Dragonrest Temple | Rubirinerk 800980 |

Client quest markers may still refer to legacy locations. Server relocation does
not rewrite the client quest archive.

## Verification and deployment

- Maven package and two quest-spawn regression tests passed.
- Full source static-data merge and XSD validation passed.
- Offline analysis registered 4,184 XML quest handlers and 5,033 quest NPC IDs,
  reporting no missing quest NPC spawns. This was repeated against the exact
  staged runtime JAR.
- All runtime Java handlers compiled against both the source build and the exact
  staged runtime JAR.
- The deployment package replaces only QuestSpawnAnalyzer.class and
  KillSpawnedData.class in a copy of the live JAR, preserving unrelated live class
  changes. Ten scoped handler/XML files are included. Hash checks guard against
  changes since staging; application backs up and verifies each installed file.
- The user approved a graceful restart. The old server logged successful data
  saving before exit; the package was applied and the new GameServer launched.
- Live startup loaded 5,219 quest handlers, validated the merged XML, and logged
  "Quest handler analysis finished in 3183 ms without errors" at 16:23:23.
  GameServer PID 23640 listens on port 7777 and connected to login/chat servers.
  Installed hashes match all ten payload files; comparison with the backup
  confirms only the two intended class replacements in the live JAR.

Deployment bundle: target-deploy/game-server/pending/quest-spawns-20261001.
Backup: target-deploy/game-server/backups/quest-spawns-20261001-162242.
Startup logs: target-deploy/game-server/quest-spawns-start-20261001.stdout.log and
the corresponding stderr log.

In-game terrain, dialogs, quest drops, Crucible progression and Warfront completion
still require gameplay verification. The analyzer checks registered availability;
it cannot prove all quests playable.
