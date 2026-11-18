# Dynamic Events

World events built on Guild Wars 2's dynamic event system, as the official Guild Wars 2 wiki
describes it (pages Dynamic event, Meta event, Secrets in the Swamp, Defeat the shadow
behemoth, World boss). A Road Warden on its beat calls for help, the event grows through
several parts, and a won chain ends with a boss fight. Failure is a branch, not an end: a
lost part opens a recovery event, and a lost chain leaves the raiders holding the road.
Everyone nearby (players and world bots) takes part without signing up, and rewards follow
each person's contribution.

## How it follows Guild Wars 2

| Guild Wars 2 | Here |
| --- | --- |
| Events start when players come near, from NPCs, or on timers | A Warden that sees a player asks for help; a held road starts its retake when a player comes near; a chain can also have a timer (`schedule`) |
| "Once an event has triggered, it will develop whether or not a player attends it" | Events never give up for lack of players; the Warden and bots fight on, and the outcome stands |
| Outcomes change the area (raiders occupy a fort until players retake it), and can last until a server reset | A lost chain leaves guards on the road; its Warden falls back, merchants turn before it, and "Retake the Road" waits for players; a restart clears it |
| Scaling at once with everyone around: more enemies, higher counts, veterans and elites; most events scale for up to 10 players, group events and bosses much higher | Live rescaling every wave and every 10 s for the boss; Elites join from 5 participants; normal parts cap at 10, Group Events and bosses at 40 |
| [Group Event] label for harder events | Camp stages and every boss fight read "[Group Event]" |
| Shadow Behemoth: untargetable until its portals (5, 9, 13) fall, at 75/50/25% | The leader is shielded at each phase until its reinforcements fall; each phase brings more |
| Everyone who participates gets 100% of the XP and their own loot ("as if they were the only participant") | Event monsters pay every attacker the whole kill; other helpers roll personal loot by the native drop calculation (custom, global and event rules; `DropRegistrationService.personalDrop`) |
| Credit from damage, healing, reviving and objectives | Damage, plus half of the threat drawn beyond it (healing, tanking), plus walking with the Warden on an escort |
| Rewards per event by medal: Gold 100%, Silver 85%, Bronze 75%; a failed event pays half; 7% of a level at the event's level; 10+ levels away earns nothing | The same, for every part including recovery parts |
| World boss chest once per character per day, bonus chest with guaranteed rare gear once per account per day | Leader chest once per character per chain per day (Kinah, Recruitment Ticket); bonus chest once per account per day with Superior-or-better gear rolled from the leader's native drop (Recruitment Tickets when 40 rolls find none) |
| "New Event Nearby" when entering an event's range; event assistant; map markers | Centre-screen "New Event Nearby" on entering; the World Event journal and tracker entry; Locate on the map |
| After an event, NPCs talk, move on, or open temporary shops | The Warden and bots speak and cheer; a native merchant of the faction keeps a stall at the event for a while |

Not copied: Guild Wars 2 lowers stronger players to the event's level (Aion has no such
system, so the reward level gap applies instead), and draws the event area on the map
(Aion has no such marker; Locate points at the event).

## Module layout

The feature is its own module. It reads the Warden and bot systems through one bridge and
changes nothing in them beyond single-line hooks.

| Part | File | Role |
| --- | --- | --- |
| Settings | `config/dynamic-events/dynamic-events.properties`, `configs/main/DynamicEventConfig.java` | Switches, ranges, timers, scaling, rewards |
| Chains | `config/dynamic-events/chains/*.json` | Data-driven event chains: stages, branches, narration |
| Catalogue | `services/dynamicevents/EventCatalogue.java`, `EventChain.java` | Loads and validates chain files |
| Engine | `services/dynamicevents/DynamicEventService.java`, `DynamicEvent.java` | Start, tick, stages, branches, scaling, shields, credit, loot, schedules, clean-up |
| World state | `services/dynamicevents/EventWorld.java` | Roads the raiders hold after a lost chain, and their retake |
| Daily chests | `dao/DynamicEventDAO.java` | Once-a-day claims in SQL (`dynamic_event_chests`, `dynamic_event_bonus`) |
| Spawns | `services/dynamicevents/EventSpawns.java` | Picks native monsters of the map, spawns waves, scales the boss |
| Narration | `services/dynamicevents/EventNarration.java` | Warden and boss speech, centre-screen notices, emotes |
| Rewards | `services/dynamicevents/EventRewards.java` | Gold/Silver/Bronze tiers, per-part XP and Kinah, leader and bonus chests |
| Chat lines | `services/dynamicevents/EventTracker.java` | Headline and live objective in chat |
| Journal | `services/dynamicevents/EventJournal.java` | The event's quest-journal and tracker entry (wrapper quest 14979) |
| Bot bridge | `services/dynamicevents/EventBots.java` (contract), `services/playerbot/PlayerBotDynamicEvents.java` | Warden trigger, bot recruitment, travel and targeting |

Hooks outside the module (one line each): `GameServer` start-up, `NpcController.onDie`,
`PlayerBotWardens.observe`, `PlayerBotRoadDuty.travel`, `PlayerBotSession` enemy selection
and its world-travel strategy, `Config` loading of the settings file, the card route in
`PlayerBotHttpService`, native Abandon in `CM_DELETE_QUEST`, full kill credit in
`NpcController.doReward`, `Npc.isInvulnerable` (an NPC shown invulnerable takes no server damage),
merchants avoiding a held road in `PlayerBotRoadDuty.travel`, and its read-only `all()` for the
schedule's Warden choice.

Other files of the feature: the wrapper quest's server template (`quest_data.xml`, quest 14979)
and handler (`data/handlers/quest/aetherfall/_14979DynamicEvent.java`), its client package
(`client-mods/custom-quests/Quests/14979-DynamicEvent`), the fourth card slot in Aetherfall
Core 1.3.30 (`client-mods/aetherfall-core/help_quest.h`) and the GM shortcut
(`data/handlers/admincommands/DynEvent.java`).

## How a chain runs

1. **Trigger.** A public Road Warden on a field beat sees a player of its faction. With the
   configured chance, and when the beat, map and server are under their limits and off
   cooldown, the Warden starts a chain whose level band fits the beat (`trigger: warden`).
   A chain with a `schedule` also starts at its times, on each map where a player is out on
   the roads, with the free Warden nearest to them. A held road starts a `trigger: occupied`
   chain when a player of its faction comes near.
2. **Stages.** Each stage has a type:
   - `KILL`: defeat a number of attackers that arrive in waves. With `protect`, the Warden
     must survive.
   - `ESCORT`: the Warden walks to the reported camp of the nearest native elite; ambushes
     spring on the way.
   - `BOSS`: the chain's leader appears at the camp. At each health phase it speaks, shields
     itself and calls reinforcements; the shield breaks when they fall.
3. **Branches.** Every stage names its `success` and `failure` stage. A failure stage can lead
   back into the chain (retake the road), or end it. A chain with `occupyOnFailure` that ends
   in failure leaves the raiders holding the road.
4. **Scaling.** Counts and boss health follow the participants around right now (players
   count 1, bots count `bot_weight`), up to the normal or group cap.
5. **Rewards.** Every part pays its participants when it ends, by tier, and half if it failed.
6. **End.** Spawned monsters are removed, the Warden returns to its beat, and the beat cools
   down. A won chain opens a merchant's stall.

Running events and held roads are world state, not saved progress: a restart clears them, as
a Guild Wars 2 server reset does. Rewards are paid when earned; Recruitment Tickets go through
the ticket ledger with once-only keys, and the daily chests are claimed in SQL.

## Monsters

Monsters come from native spawn data of the event's map: hostile to the Warden's race, not
aerial, and close to the beat's level. Raiders share the tribe of the camp's elite where the
map allows it, so one event looks like one enemy faction. The boss is the strongest native
template of that tribe in the level band (Hero or Legendary first), with extra health per
participant on top of the native group scaling.

## Writing a chain

A chain file holds `id`, `title`, `minLevel`, `maxLevel`, `weight`, `trigger` (`warden`, the
default, or `occupied`), `occupyOnFailure`, an optional `schedule` (`everyMinutes` dividing a
day, `atMinute` below it), `first` (stage id) and `stages`. A stage holds `id`, `type`,
`title`, `objective`, `count`, `perParticipant`, `wave`, `seconds`, `protect`, `setback`,
`group`, `success`, `failure`, `rating` (`NORMAL`, `ELITE` or `BOSS`), `phases` (boss only:
health percents; `count` is the first phase's reinforcements) and `say` (narration lines). Narration lines are
`speaker|text`, where the speaker is `warden`, `boss`, `notice` or `bots`. Placeholders:
`{warden}`, `{road}`, `{boss}`, `{enemy}`, `{place}`, `{count}`. Use `end` as a stage id
to finish the chain with success, `fail` to finish without one.

The loader rejects a chain with unknown stage links, unreachable stages, a loop without an
exit, or narration with an unknown speaker; the server log names the file and reason.

## What players see

- "New Event Nearby" in the centre of the screen when you walk into a running event.
- A centre-screen notice and chat narration when the event starts, at each stage and at the end.
- The leader's shield going up and breaking, in the centre of the screen.
- Raiders standing guard on a road that was lost, and its Warden waiting at the far end.
- The Warden and the boss speaking in chat bubbles and shouts, and the Warden pointing,
  saluting and cheering.
- World bots nearby answering the call in chat and joining the fight.
- Stage progress in chat at each quarter, and a final contribution tier.
- The boss's native target frame with its scaled health.
- A **World Event** entry in the quest journal and tracker while you are in the area: the
  current objective with its count, the time left in minutes, finished parts greyed out,
  your contribution tier so far, and Locate and Track buttons. It shows the result for
  20 seconds after the event ends, then leaves. Moving away for 30 seconds removes it;
  coming back adds it again. Abandon removes it for that event only; your help still counts.

## Testing with a GM character

`//dynevent start` makes your targeted Road Warden (on duty, on the road, out of combat)
start a fitting chain at once, skipping the chance roll, the beat cooldown and the caps.
`//dynevent start road-ambush` picks a chain. `//dynevent stop` ends the event around you
without rewards; `//dynevent status` lists the loaded chains and running events. Access
level 9 (`config/administration/commands.properties`). Normal play never needs it.
