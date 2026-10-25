CUSTOM QUESTS

All custom quests belong under Mods/CustomQuests/Quests. The launcher displays
one Custom Quests entry and loads every quest package in these subfolders
before Play. Adding another quest does not add another entry to the Mods list.

The Unfinished Entry (3999) starts at Nagrunerk in Jamanok Inn after The Red
Journal (3060). Its server handler must also be installed on the game server.

The Unbroken Field (14980-14995) is a sixteen-quest continuation of The Writing
on the Wall (1198), starting with Spatalos in Verteron Citadel. It follows the
letter's conspiracy across Verteron and returns to Spatalos for its conclusion.
The original server must also have the matching quest templates and handlers.

Each quest folder contains quest.json, quest.xml, registration.xml, strings.xml,
dialog.html and quest_script_monster.csv. These hold the native client quest,
NPC/objective registration, English text/dialogue and optional monster guidance.
The CSV can be empty for a quest with no monster objective. IDs must be unique.
Put complete maintained quest packages here, not server scripts or executables.

The launcher merges changed packages into Aion's normal Quest.pak and English
data.pak. Aion reads those fixed archive locations; it does not read arbitrary
quest folders directly. Unchanged packages are reused without rewriting files.
Close the game normally before loading changed packages. Additional clients can
reuse unchanged quests. Existing native quests and unrelated archive members
are preserved. Conflicting IDs or externally changed owned records stop loading
with an error instead of overwriting them.

Backups and receipts are written outside the client, at BackupRoot in
settings.json. installed.json records the current owned records and archive
state. Do not delete it to bypass a conflict. Removing an installed quest's
source folder is rejected; restoring its folder keeps progress safe.

Client packages do not install server handlers or change character progress.
Server support is required for each quest. The in-game quest menu, markers and
gameplay remain acceptance checks after the client has restarted.
