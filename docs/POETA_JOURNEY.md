# Optional Poeta journey

New Elyos starting-class characters see a full-screen **Choose your journey**
menu on entering Poeta. It uses the original client's Poeta and Sanctum loading
artwork and follows the current game viewport, including resolution changes.

- **Play Poeta** saves the choice and starts the original prologue. Quests and
  rewards stay in the normal story flow.
- **Ascend to Sanctum** asks for the advanced class and a final confirmation.
  It sets level 10, completes the 42 ordinary Poeta and Ascension quests,
  mails all fixed and alternative reward items, includes quest Kinah, titles
  and quest cube expansion, binds the character in Sanctum, and starts its
  class-specific **Dispatch to Verteron** quest. Speak to Polyidus to continue.

Players can close the window and decide later, or reopen it through
**Additional Functions > Choose Your Journey** or `/journey`. Choosing Play
suppresses future automatic prompts but still permits changing to Skip while
eligible. Characters must be Elyos, in Poeta, level 1–9, standing safely, and
still in a starting class; transferred characters are excluded. Characters
already ascended cannot claim the skip. Previously completed quests do not
award another set of items.

The reward bundle includes all item choices, including weapons for other class
branches. Identical alternatives within a quest are awarded once; rewards
from different quests are added together. The skip grants level 10 rather than
adding scaled quest experience. Event, restricted, unused and repeatable quests
are excluded. Dispatch quests 1913–1916, 19070 and 19071 remain active, so the
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
adding the menu and exact authenticated URL. Only the browser routing caves in
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

- `PoetaJourneyRulesCheck` loads the actual quest XML: 42 eligible quests,
  reward alternatives, deduplicated ceremony bundles and all six onward quests.
- `PoetaJourneyDatabaseCheck` exercises the actual commit and recovery methods
  in an isolated temporary database: completion, mail attachments and currency,
  titles, bind point, duplicate rejection, atomic rollback, mailbox capacity,
  and one-shot recovery without duplicate rewards. Live characters are untouched.
- `tests/verify_journey_browser.py` uses the client's actual Awesomium/WebKit:
  both choices, class confirmation, double-click suppression, Play dismissal,
  and no automatic opening for completed characters at 1024×768, 1920×1080
  and 3440×1440. Screenshots are saved under `target/journey-*.png`.
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
