# World Playerbots in native Find Group

19 October 2026. Installed on the original development GameServer. Normal build,
130 inert checks, normal closure, backup and both copied hashes passed. Receipt:
external `archives/server/backups/playerbots-findgroup-20261019/receipt.json`.
Exact build/delivery pointers are maintained in `project-notes/current-state.md`.
Latest cooperative-group/instance-organizer update is installed on the original.
Normal build, 170 focused native-model/packet checks, normal closure, backup and
copied hash passed. Receipt: external
`archives/server/backups/playerbots-cooperative-findgroup-20261019/receipt.json`.
The empty-window follow-up described below is installed (138 inert checks,
normal closure, backup and copied hash passed). Latest receipt: external
`archives/server/backups/playerbots-findgroup-visibility-20261019/receipt.json`.
Actual owner posting visibility and subsequent group/chat acceptance remain open.

Latest 19 October publication/invitation repair is installed on original GameServer
and ChatServer. Normal two-component build and 27 focused inert checks passed;
both processes were closed, both original JARs backed up and copied hashes verified.
Receipt: external
`archives/server/backups/playerbots-lfg-publication-invites-20261019/receipt.json`.
Native LFG channel display and live invitation acceptance remain owner checks.

`PlayerBotFindGroup` is a separate core social service. It registers real resident
characters in the existing `FindGroupService` maps and uses unmodified native
Find Group packets, chat messages, invitation question windows and party events.
It does not replace movement, combat, population, owned-alt or companion policies.

| Tab | Bot behavior |
| --- | --- |
| Recruit Group Members | An organizer seeks company for an unfinished local quest objective or real leveling need. Compatible world bots join a native party, whose real ID/member count appears in the post. Whisper `invite`/`join` for a normal invitation. |
| Apply for Group | A solo bot posts its actual class, level, role and objective, seeking an invitation. Invite it through the normal client controls; existing world-bot acceptance/control rules apply. |
| Instance Groups | An eligible organizer plans a trip to a verified permanent entrance on its current map, walks there through the existing navigator and registers a real instance mask. Compatible bots join it while gathering. Native Apply invites a human into the actual party. |

Each bot makes one registration. Defaults allow up to 12 posts per faction/tab,
with at most four current instance organizers per faction. The selector favors
eligible instance trips on human-occupied maps, then divides field
characters between recruiting and applying. A tab can be empty when no current
resident has a qualifying need: it never creates characters, grants quests, resets
entry counts or invents a dungeon need merely to fill a tab.

Visible offers also announce in the real race-specific ChatServer LFG channel.
The authenticated GameServer bridge carries the resident's native ID, name,
faction and current offer; ChatServer sends its existing channel-message packet
only to actual subscribers. No chat account, socket or second saved bot roster
is created. Recruitment and instance text includes actual native member counts.
The social lane waits ten seconds after creation, sends at most one offer per
faction per twenty seconds, rotates older announcements first and renews each
bot's offer after three to five minutes. Withdrawn, completed and unavailable
offers cannot announce. This is separate from populating the three native lists;
opening/refreshing those lists continues to use their normal packets.

Human applications and invitations follow native party admission. The bot-only
combat timer rejection and shared invite cooldown have been removed in both
directions. Recent damage does not force a player to apply again; separate
applicants can each receive the native question window. Native death, faction,
party leadership/capacity and automatic-instance restrictions still apply.

Field posts prefer the selected activity, then a genuine unfinished local quest
objective, then an honest leveling offer for a character below level 65. The
leveling offer does not name an unowned quest or invent a monster objective.
Posting does not require a movement destination or awaken an otherwise dormant
actor. Finished/changed objectives,
death, departure, control handoff, expiration and feature disabling retire ads.
Instances use the existing AutoGroup/portal/instance-limit data, faction, level,
quest, title, rank, item and Kinah requirements and unexpired entry limits.
With the existing `gameserver.instance_group.form_anywhere=false`, creation
requires an actual permanent entrance within 30 m, or already being inside the
instance. An organizer now chooses a real same-map entrance and travels there
before registering. Physical arrival, native portal eligibility and map dormancy
remain authoritative. The owner's form-anywhere setting is preserved.

## Bots helping each other

The social service matches available world residents to real existing offers,
including offers still gathering at an entrance. Same faction, map/instance,
compatible levels and useful earned-XP/unfinished-quest work are required. Native
invitations run under the existing per-actor work admission with both current
sessions rechecked. Bots retain autonomous ownership, native identities, gear
and existing world decision workers. Party membership is the actual native group;
there is no parallel saved membership store.

Field groups gather up to three bots. Instance groups gather up to five, bounded
by the content's capacity, always leaving at least one human place. Matching
avoids duplicate tanks/healers and reserves remaining places for missing roles.
Three-person instances can leave one required role for the joining human.
Applying bots withdraw their individual posts on joining. Recruit listings switch
from a solo object to the actual native team ID and real member count. Instance
member lists already read the same native roster.

Bot followers gather behind the organizer through the existing ground navigator.
All native party members participate in the existing combat, healing, threat and
loot logic; followers do not independently wander toward conflicting goals.
Combat, support, hazards and ongoing native item/quest actions outrank optional
gathering. Instance organizers stay at the entrance to recruit and explain the
run, rather than resuming unrelated roaming and losing their listings. No
transport graph or teleport shortcut is used for these same-map trips.

The first human accepts a normal invitation into this group. Existing native
controller selection then hands every resident to the current human controller;
the organizer retains the chat conversation described below. An invitation
callback treats an already-completed decision-worker handoff as success, preventing
a valid joining human from being removed by that race. Bot-only groups
expire after 15 minutes or an invalid/completed objective, releasing native
membership through per-actor admission outside combat/casts/trade/loot. Native
logout, team removal and normal character saving remain authoritative. Instance
entry still follows the existing local-party portal flow after the player starts.

After joining, the bot greets the player through native party chat and asks
whether to start, wait or explain the objective. It answers `help`, `where`,
`what do you need`, `ready`/`start`/`yes`, `wait`/`not ready`/`no`, `lead` and
`leave` in whispers or party chat. Quest guidance names its real objective/map;
instance guidance explains the run and normal entrance. `wait` uses the existing
passive-follow command outside combat/actions; `start` restores normal Follow.
Only the current human controller can change the bot's order, pass leadership
or dismiss it. Ordinary greetings/help from other party members remain social.
One spokesman answers an unaddressed party line; a named bot answers its own line.
Replies respect faction, block lists and whisper privacy. Legion chat requests
continue through the existing independent legion-invitation handler.

The first human accepting a bot invitation becomes its controller through the
existing world-bot session handoff. A field recruiter passes native leadership
immediately. An instance recruiter keeps native leadership while gathering more
players, then passes it when the controller says `start` or requests `lead`.
Existing invitations of advertised bots into human-led groups remain supported.
Native leave/logout/controller replacement returns bots through the existing
independent-world lifecycle, retaining their native characters and earned items.

The social lane has its own executor, a 128-request bounded inbox, at most eight
chat requests and 16 actor/registration observations per pass. It does not use
AI/terrain/companion/SQL workers or acquire a population-wide lock. Native
registration and conversation state expires with the login, like human listings;
quest progress, identities and membership remain in their existing native
repositories. There is no JSON/properties shadow database or new SQL migration.

## Scope and acceptance

This is an Aion-specific extension, not a claim that WoW LFG has been ported.
Pinned `RandomPlayerbotMgr::CheckLfgQueue` and `randomBotJoinLfg` defaults were
consulted for the independent social scheduling boundary. Aion's three native
registration types, real quest needs and native party admissions replace WoW's
role matchmaking/queue semantics.

The existing server implements Instance Groups as local native parties, as its
`FindGroupService` already documents. This extension retains that behavior;
cross-server matchmaking and a retail automatic ready-check/teleport sequence
are not implemented. Players lead/enter through normal existing instance entry;
chat `start` does not bypass entry checks or transport the party. This slice
supports parties of up to six, not alliance/league recruitment. Unmodeled portal
handlers/siege gates fail closed, and max-level XP-only runs are excluded unless
an actual active instance quest supplies a need. No fabricated loot-upgrade claim.

Validation: normal Maven build, inert intent/level/native-registry checks and
serialized packet checks for all three tabs. Fixtures run outside source/runtime
with inert models, without login, SQL, world actor creation or server startup.
User acceptance still needs native UI visibility, whisper/invite/acceptance,
multi-player instance recruitment, chat choices, departure/relog and actual
quest/dungeon play, plus responsiveness with the normal world population.

Feature keys: `gameserver.playerbots.social.findgroup=true` and
`gameserver.playerbots.social.findgroup.posts_per_tab=12`, documented with the
existing switches in `config/playerbots/features.properties`.

19 October empty-window repair: the owner saw no posts after starting the first
build. Installed JAR/config were correct, with ~484 world residents, ~275 dormant,
~180 traveling to quests and only two selected grind trips in the bounded log.
The first selector required a selected hunt/collection movement goal, excluding
town/quest-preparation/dormant residents. An inert town-bot/no-goal regression
fails against that exact installed build. Selection now reads real quest/XP needs
independently; max-level/invalid characters cannot invent an XP need. The social
worker logs startup and a bounded 30-second observed/available/no-need/published
and current three-tab count summary. Native instance entrance rules are retained.
Exact new build, receipt and acceptance state: `project-notes/current-state.md`.

19 October cooperation follow-up: owner saw solo organizers and an empty instance
tab. Source had no bot-to-bot joining path and only posted instances when an
unrelated world trip happened to pass an entrance. The new native grouping and
explicit entrance-trip behavior above address those missing paths. The bounded
social summary now includes joins, gathering organizers, native parties and actual
party-member counts. Source/build/offline verification do not establish owner
acceptance of physical gathering, native UI visibility, party combat or the full
instance run. Exact final package/receipt is in current-state.
