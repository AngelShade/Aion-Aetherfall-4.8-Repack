# Optional Poeta journey

New Elyos starting-class characters see a full-screen **Choose your journey**
menu on entering Poeta. It uses the original client's Poeta and Sanctum loading
artwork and follows the current game viewport, including resolution changes.

- **Play Poeta** saves the choice and starts the original prologue. Quests and
  rewards stay in the normal story flow.
- **Ascend to Sanctum** asks for the advanced class and a final confirmation.
  It sets level 10, completes the 41 ordinary Poeta and Ascension quests,
  advances A Ceremony in Sanctum past Pernos to Leah in Sanctum,
  mails fixed and alternative rewards only for those skipped quests, includes quest Kinah, titles
  and quest cube expansion, binds the character in Sanctum, and starts its
  class-specific **Dispatch to Verteron** quest. Complete the ceremony to earn its
  selected weapon, fixed items, Kinah and XP, then speak to Polyidus to continue.

Players can close the window and decide later, or reopen it through
**Additional Functions > Choose Your Journey** or `/journey`. Choosing Play
suppresses future automatic prompts but still permits changing to Skip while
eligible. Characters must be Elyos, in Poeta, level 1Ã¢â‚¬â€œ9, standing safely, and
still in a starting class; transferred characters are excluded. Characters
already ascended cannot claim the skip. Previously completed quests do not
award another set of items.

The reward bundle includes all item choices, including weapons for other class
branches. Identical alternatives within a quest are awarded once; rewards
from different quests are added together. The skip grants level 10 rather than
adding scaled quest experience. Event, restricted, unused and repeatable quests
are excluded. Dispatch quests 1913Ã¢â‚¬â€œ1916, 19070 and 19071 remain active, so the
next journey is played normally.

## Configuration and persistence

`gameserver.poeta.journey.enable = true` enables the choice. Keep
`gameserver.simple.secondclass.enable = false` to retain the original Ascension
story for Play Poeta. The local marketplace must be enabled and bound to
`127.0.0.1:8091`. The journey route is intentionally available only on loopback,
uses the existing native browser account token, and accepts only one online
player's connection-bound, expiring, single-use confirmation token.

Startup creates `poeta_journey` from `config/journey/schema.sql` and validates
transactional storage and reward templates. Quest states, reward attachments,
mail, titles, character destination and the decision commit in one transaction.
A full mailbox rejects the entire skip. An interrupted application of committed
progress closes the connection and restores the saved progression on login
without remailing items. The Play path changes only the decision receipt.

## Client and deployment

`client-mods/transmog-menu/prepare_journey.py` builds a focused package from the
installed client. It verifies the existing Lua and browser machine code before
adding the menu and exact authenticated URL. The browser routing and full-screen layout caves in
the existing Game.dll are changed; other DLL patches are preserved. The native
bridge adds game-thread show/hide and viewport sizing. Existing item archives,
inventory and warehouse UI archives, icon index, and pet archive retain hashes.
The stock model public key is preserved and all three signed archives are
verified and signed together using the isolated addon key.

The installer backs up and checks all replacement and preserved files and
requires Aion to be fully closed. GameServer must stop through its normal save
path before replacing the JAR; LoginServer and ChatServer do not need restarting.
This checkout's staged server JAR overlays the compiled changed classes onto
the current deployed JAR, preserving existing unrelated server changes. The
normal Maven build was unavailable because its Git plugin could not be fetched;
the changed Java sources compiled independently against the deployed libraries.

## Verification

- `PoetaJourneyRulesCheck` loads the actual quest XML: 41 skipped quests,
  reward alternatives, ceremony reward exclusion and all six onward quests.
- `PoetaJourneyDatabaseCheck` exercises the actual commit and recovery methods
  in an isolated temporary database: completion, mail attachments and currency,
  titles, bind point, duplicate rejection, atomic rollback, mailbox capacity,
  and one-shot recovery without duplicate rewards. Live characters are untouched.
- `tests/verify_journey_browser.py` uses the client's actual Awesomium/WebKit:
  both choices, class confirmation, double-click suppression, Play dismissal,
  and no automatic opening for completed characters at 1024Ãƒâ€”768, 1920Ãƒâ€”1080
  and 3440Ãƒâ€”1440. Screenshots are saved under `target/journey-*.png`.
- The native widget fixture checks local-page restrictions, destroyed views,
  game-thread full-screen sizing, show/hide, and resolution changes, alongside
  the existing Wardrobe mouse/camera checks.
- The native browser machine-code fixture checks the journey account token,
  exact-URL matching and fallback without sending any game packets.

In-game acceptance remains necessary: create an Elyos character and test Play;
use another character to choose Skip, collect reward letters, and continue
Dispatch to Verteron through Polyidus. Also verify a summoned pet and existing
Additional Functions after the signing update. Browser and database fixtures
do not substitute for those live gameplay checks.

## Installed on 2026-10-01

Nine client files were backed up, installed and hash-verified; all six preserved
archives/index retained their hashes. The previous GameServer completed its
normal shutdown and logged `Data successfully saved`. Ten staged server files
were installed with verified backups. The new GameServer started successfully
and logged `Poeta journey ready: 42 quests, optional level-10 skip and mailed
rewards`, with LoginServer and ChatServer still running. Live media matched the
source bytes, and unauthenticated actions, an incorrect Host, and non-public
files were rejected. The installed native bridge also passed the actual browser
callback registration and original item-icon regressions.

The initial focused installer omitted the Graphics-menu tracking update, causing
the launcher to reject the new Game.dll. The repair rebased the graphics package,
cursor tracking and both restore baselines onto the verified Poeta browser edits.
The installed DLL, UI archives, keys and signatures remained byte-identical.
Both launch-time Graphics and native-cursor checks passed afterward. A disposable
client fixture also verified tamper rejection, byte-exact Graphics removal, and
retention of the Poeta routes in both Graphics and DXVK restore baselines.
Future focused preparations use this incremental graphics compatibility step.

The first restarted GameServer ended abruptly at 18:49, with no Java exception,
normal shutdown, or JVM crash dump recorded. Its temporary launch session may
have ended the child process; the logs do not establish the exact cause. At
19:03 it was relaunched as a persistent hidden process outside that temporary
session. It started successfully in 40 seconds, loaded the 42-quest journey,
and reconnected to the unchanged LoginServer and ChatServer. The deployed JAR
and configuration hashes still matched the validated package.

## Journey corrections

The native full-screen sizing hook now recognizes PrivateJourney and its browser,
using screen pixels with no title inset. The native publisher SetRect fixture
passes 100 resolution/UI-scale cases, including journey child updates, and the
actual client WebKit fixture clicks visible button centers at three resolutions.

After the skip, completed and active quest lists are refreshed using the same
packets as login. The ceremony remains START at step 1 (Leah in Sanctum); all
ceremony alternatives are already mailed, so completing the Sanctum dialogue
marks the quest complete without granting items or Kinah a second time. A
versioned startup migration repairs earlier skips once, without mailing again,
and preserves subsequent ceremony completion across restarts.

A durable welcome_pending receipt reopens the Sanctum welcome after map-entry
reloads. Only the player's Enter the world acknowledgement clears it. The menu
can also be reopened manually after dismissal. The revised client package
rebases Graphics/cursor tracking and both restore baselines onto this exact
layout edit, preserving their launch and restore checks.

The corrections were installed on 2026-10-01 at 19:38. GameServer completed its
normal save before the four server files were replaced, then started in 37
seconds as persistent PID 27208. All 19 client files and preserved inputs matched
the staged hashes, and both Graphics and native-cursor launch checks passed.
The isolated database check passed 29 assertions, including adding the columns
to the old schema and preserving a subsequently finished ceremony on restart.
Babe now has 41 completed quests and ceremony START at step 1 with no completion
count; her offline position was returned from Poeta to Sanctum with the old
position backed up. No additional reward letters were created by the repair.
Live HTML/JS matched source and unauthenticated state requests returned 403.
In-game acceptance of the corrected menu and ceremony still belongs to the player.

## Ceremony rewards after completion

New skips exclude quest 1007 from both the skipped quest roster and its mailed
reward receipt. The transaction starts A Ceremony in Sanctum at Leah separately,
and crash recovery restores that active quest without mailing ceremony rewards.
The ordinary quest turn-in grants the selected class weapon, fixed items, Kinah
and XP. Existing completed ceremonies are preserved.

Older skips retain their original receipt containing 1007, so the legacy duplicate
check still protects rewards already mailed. No player inventory or Kinah is
removed. The journey menu distinguishes new turn-in rewards from an older mailed
bundle. Compilation, the XML rules check and 32 isolated database checks passed.

The ceremony reward fix was installed on 2026-10-01 at 20:11 with verified
backups after the previous GameServer logged `Data successfully saved`. The
replacement started in 56 seconds as PID 27420, logged `Poeta journey ready:
41 quests`, listened on port 7777 and connected to LoginServer and ChatServer.
All three deployed files matched the staged hashes; live HTML/JS matched the
source and unauthenticated state remained HTTP 403. The actual Aion WebKit
fixture passed at 1024x768, 1920x1080 and 3440x1440, including the new and
legacy reward explanations. A fresh character skip and ceremony turn-in still
need in-game acceptance. Existing characters and reward receipts were retained.
