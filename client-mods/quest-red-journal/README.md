# The Unfinished Entry (quest 3999)

Maintained client assets now belong to
`client-mods/custom-quests/Quests/3999-TheUnfinishedEntry`, delivered under the
single client `Mods/CustomQuests` entry. The launcher discovers and loads quest
packages from that folder before Play. These earlier feature-specific builders
retain their source references for the initial quest/server delivery; they do
not create additional top-level client mod folders.

A custom Aetherfall follow-up to **The Red Journal (3060)**. The installed
English ending has Nagrunerk stop at "Titirunerk took this along with all my..."
and pay the player to forget the journal. Existing quest 3059, **Someone
Superior**, already explains the stolen safe. This continuation resolves the
remaining lethal-trap story; its new events are custom writing, not recovered
retail dialogue.

After completing 3060, speak to Nagrunerk at Jamanok Inn. Existing characters
who already completed 3060 can start it too. Native prerequisites require an
Elyos character of level 48 or above. The sequence is:

1. Tell Rosina about the payment.
2. Question Nagrunerk's bodyguard about Titirunerk's theft.
3. Defeat the existing Titirunerk the Two Faced in Marla Cave. Recovery of the
   poison/trapping notes is recorded directly as quest progress, without adding
   an inventory item or changing the existing monster, spawn, loot or quest3059.
4. Confront Nagrunerk with the evidence.
5. Give the account to Rosina, who records his confession and secures the notes.

The one-time reward is 5,390,338 XP and 47,430 Kinah, matching 3060's reward
scale. An explicit completed-journal entry explains what was stolen, why
Nagrunerk paid for silence and what happened to the evidence. Progress and
completion use the native quest repository. Original quests and progress are
preserved; no SQL migration, balances or existing completion flags are edited.

The client builder adds one quest and title, its English native encrypted
dialogue, one scripted-monster guidance row and a native data-driven quest
registration. This registration links the new quest to Nagrunerk's menu/marker,
the five objective actors and Rosina's reward. The compiled retail quest classes
in ScriptDLL64 do not know custom quest3999: quest metadata and dialogue alone
were insufficient, as the owner's initial in-game test confirmed. No native
DLL changes are needed; the registration uses the installed Talk/Hunt format
(reference quest25032). `prepare_registration.py` stages the one-member repair
for clients that received the initial installation without this registration.
The initial dialogue builder checks a byte-exact
decrypt/encrypt roundtrip of installed quest3060 before using the filename-keyed
0x81 stream for the new dialogue. Codec reference:
[aion-pak-manager html-crypt.ts](https://github.com/giordanidev/aion-pak-manager/blob/main/backend/parse/html-crypt.ts).
Every prior quest/title record and untouched compressed PAK member is checked.
Quest3999 was free in both maintained server and original client data; its
dialogue uses the existing sub-10000 `dialogs/quest_q3999.html` convention.

Delivery is **original client/GameServer only**: exact normal builder resources
plus two native client archives. No GameServer JAR, native DLL, launcher,
configuration, database, repack or recovery-image changes are needed. Running
client/GameServer must close normally before delivery. Actual quest visibility,
dialogue layout, tracking, combat credit, saved resumption and reward acceptance
remain the user's in-game checks. No components are started by this workflow.

19 October follow-up: the owner's first test found no Nagrunerk choice or quest
markers. The missing native registration is now installed on the original
client only, with closure/backup/copied-hash verification. All 482 existing
data-driven registrations and the other five compressed archive members were
preserved. Receipt: external
`archives/client/red-journal-registration-20261019/receipt.json`.
Client restart and actual choice/marker/progression acceptance remain pending.
