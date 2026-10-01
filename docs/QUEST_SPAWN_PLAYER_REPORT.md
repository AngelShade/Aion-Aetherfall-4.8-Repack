# What to expect in the game after the quest-spawn repairs

The changes were deployed on 1 October 2026. The GameServer completed a graceful
save and restart. Its live startup loaded 5,219 quest handlers and reported no
missing quest NPC spawns. The behavior below is expected from the deployed code
and data; the new locations and instance progression have not been playtested.

## 1. Restored quest NPCs in Signia/Cygnea and Enshar

These are custom relocations approved for this server. Their quests still keep
their existing faction, level, prerequisite and repeat restrictions.

Coordinates below are server world X/Y, not percentages on the client map.

| Where to look | NPCs you should see | Why you would talk to them |
| --- | --- | --- |
| Signia/Cygnea, beside the Rentus Base entrance, around X 1145, Y 798 | Ancanus | Terath Dredgion and Rentus quest contacts, including the Unending Assault hand-in (30604). |
| Same Signia entrance area | Anarin | Offers Seal Their Doom (30511), when eligible. |
| Same Signia entrance area | Crispin | Starts Cache-ing in on Turmoil (30061), To the Unstable Abyssal Splinter (30276), and Jump In Brusthonin (39000). |
| Same Signia entrance area | Kurius | Starts Greater Than the Whole (16930), involving Infinity Shard. |
| Same Signia entrance area | Liponia | Starts Strengthen the Defense (16990), involving Illuminary Obelisk. |
| Enshar, beside the Rentus Base entrance, around X 1978, Y 2021 | Skafir | Terath Dredgion and Rentus quest contacts, including the Wrath of Terath hand-in (30614). |
| Same Enshar entrance area | Tepes | Starts Hexway Hideout (30161), Abyss of a Problem (30376), and A New Battlefront Opens (49000). |
| Same Enshar entrance area | Otur | Starts Doomsday Weapon (26930), involving Infinity Shard. |
| Same Enshar entrance area | Maeve | Starts Protection Tower Researcher Kamaz (26990), involving Illuminary Obelisk. |
| Signia, Aequis Expedition Headquarters, beside the existing Kaldor introduction giver, around X 2944, Y 907 | Saparinerk | Supplies the missing conversation in New Lands to Behold (13800). |
| Enshar, Dragonrest Temple, beside the existing Kaldor introduction giver, around X 432, Y 2155 | Rubirinerk | Supplies the missing conversation in A Full New World (23800). |

Kurius, Liponia, Otur and Maeve are quest contacts at the relocated hub. Talking to
them does not add new Infinity Shard or Illuminary Obelisk entrances there; you
still travel to the existing entrances for those instances.

## 2. Correct hand-in NPCs for twelve academy introduction quests

The following quests now use the appropriate existing 4.8 NPC. You should be able
to finish the introduction by speaking to the NPC below when the quest is ready
for that step. These NPCs were already present; the change is the hand-in mapping.

| Faction | Quest | Hand in to | Existing location |
| --- | --- | --- | --- |
| Elyos | Hasten to Hariken (19615) | Nimrod | Signia, near Aturam Sky Fortress; X 2414, Y 637 |
| Elyos | Refuge no More (19617) | Rosalee | Signia, near Tiamat Stronghold; X 98, Y 1473 |
| Elyos | Reinforcing Rentus (19618) | Garnon | Signia, near Rentus Base; X 1140, Y 805 |
| Elyos | Steal the Siel (19619) | Rosalee | Signia, near Tiamat Stronghold; X 98, Y 1473 |
| Elyos | Fight on Ophidan (19626) | Theana | Signia, near Ophidan Bridge; X 2789, Y 2997 |
| Elyos | Sanctuary Sanctioning (19627) | Supri | Signia, near Danuar Sanctuary; X 2092, Y 2287 |
| Asmodian | Dredgion Dismantling (29615) | Nineveh | Enshar, near Aturam Sky Fortress; X 648, Y 2858 |
| Asmodian | A Time to Kill (29617) | Ginie | Enshar, near Tiamat Stronghold; X 2822, Y 1675 |
| Asmodian | Recovering from Betrayal (29618) | Ekios | Enshar, near Rentus Base; X 1987, Y 2036 |
| Asmodian | Reclaiming Relics (29619) | Ginie | Enshar, near Tiamat Stronghold; X 2822, Y 1675 |
| Asmodian | A Game of Bridge (29626) | Rohellein | Enshar, near Ophidan Bridge; X 458, Y 119 |
| Asmodian | Come Back with Friends (29627) | Primenk | Enshar, near Danuar Sanctuary; X 1671, Y 566 |

## 3. Empyrean Crucible now reaches the missing quest bosses

The previous implementation finished the run at stage 9, round 3. The expected
sequence now is:

1. Finish both stage-9 round-3 opponents.
2. Andre appears in round 4, followed by Kamara in round 5.
3. Defeating Kamara should count for A Giant Illusion (18204) or Everything's
   Bigger in Asmodae (28204), if the appropriate quest is active.
4. A record keeper appears. Speak to that NPC to move to stage 10, then speak to
   the stage-10 record keeper to start its first round.
5. Stage 10 proceeds through two Anuhart waves, Marabata of Strength, Tahabata
   Pyrelord, and finally Vanktrist. The delayed enemy spawns generally occur about
   six seconds after the previous round ends.
6. Defeating Vanktrist should count for Vanquish the Vanktrist (18205) or From the
   Reaches of Nothingness (28205), when active. The normal training reward routine
   runs and a final record keeper appears.

Asmodian stage-7 runs also now use Traufnir, Sigyn, Sif, Freyr and Aud. Killing Aud
should satisfy The Student Becomes the Master (28203), allowing the prerequisite
chain toward the later boss quests. Elyos stage 7 retains its existing sequence.

The added late rounds provide a basic progression path to the quest bosses.
Retail fight mechanics and late-round score awards have not been fully
reconstructed. The new enemies have no added score cases in this repair, so do
not expect a complete retail score/insignia increase for clearing those rounds.

## 4. Commander Pashid in the Eternal Bastion Warfront

Commander Pashid should be present at the existing bastion area, around X 744,
Y 294 inside the Warfront instance.

His defeat during an active match should:

- Count for Reclaim the Battle (16961) or Capture the Bastion (26961), when active.
- Award 30,000 match points to the damaging player's faction and finish the match
  through the existing boss-kill reward path.
- Give the winning faction its existing Eternal Bastion Warfront Reward Chest,
  nine Fragmented Ceramium, and the existing additional 3,850 base AP for a boss
  kill, before configured AP rate scaling and other score-based rewards.

The existing end-of-match behavior revives players after about ten seconds and
moves them out after about one minute. The rewards are awarded by the match
system; the chest is not a new object placed beside the corpse.

## 5. Drakenspire Depths: a usable Ominous Darkness object

After Beritra is defeated, Ominous Darkness should appear at the defeated boss's
position. With Mind Your Business (18953) or Essence of Darkness (28953) active,
using the object should open the quest interaction and allow the registered quest
items to be looted. Group/alliance quest-drop handling uses the existing system.

The placement and timing are custom for this repair. The original retail object
position was not verified. The repair does not implement the missing hard-mode
Beritra fight; the playable boss modes still use their existing implementation.

## 6. Missing targets elsewhere in the world

| Map and location | Expected sight or interaction | Relevant quest |
| --- | --- | --- |
| Gelkmaros, X 124, Y 2403 | Pinebeak is present in the existing quest encounter area. Kill it for the objective. | Pinebeak's Greed (46500), a faction daily with level 50–52 eligibility |
| Gelkmaros, around X 2414–2416 or X 2528, Y 1609–1619 | Four Balaur Limbcleaver object positions are restored. Use them for the quest action when eligible. | The Right to Bear Arms (21248) |
| Heiron, Lepharist Territory, X 423, Y 927 | Leif appears while the Asmodians control that base. He should supply the missing quest contact. | Lepharist's Mark (26904) |
| Theobomos, near Jahmanok at X 435, Y 1237 | Gastak is restored beside the existing direct-portal guard. | A Grim Warning to Others (49002) |
| Brusthonin, near Surt at X 1391–1395, Y 1920 | Giel and Dineos are restored beside the existing direct-portal guard. | The Threat From Within (39002) |

The invasion guards are combat targets. They use their existing templates and
normal respawn settings.

## 7. Kills should count against the instance enemies that actually spawn

- In Terath Dredgion, Crew Cut (30601) and Alluna's Crew Cuts (30611) now count
  kills of the current crew variants.
- Terrors of the Terath (30603) and Officer's Mess (30613) now count the current
  officer/captain variants at their existing positions. Quest counters should
  advance while fighting those enemies.
- Destroying the Destroyer (18941) and Destroy the Destroyer (28941) now count
  the actual timer-spawned Destroyer Kunax variant. The existing boss timer is
  retained.

These fixes use the enemies already belonging to those instances. They do not
add another copy of each old target next to the current enemies.

## 8. Fifteen warning groups were detection fixes

These changes correct the startup analysis without changing the corresponding
existing gameplay:

- Seven Elyos daily quest targets: quests 36504, 36505, 36506, 36510, 36511,
  36512 and 36516.
- Six Asmodian daily quest targets: quests 46504, 46505, 46510, 46511, 46516 and
  46517.
- Raksang faction/route NPCs used by quests 28737, 28738, 28742 and 28743.
- Rentus walker targets used by quests 18033 and 28033.

The daily targets still appear when you use their existing quest trigger or
lure. Raksang still chooses its NPC by faction and route. Rentus still uses its
existing walker-spawn behavior. No additional permanent copies were added for
these fifteen groups.

## What is verified, and what to check while playing

The build, regression tests, full XML validation, runtime handler compilation,
deployment file hashes and live startup analysis passed. Gameplay verification
has not been performed.

The most useful in-game checks are:

1. Visit the two Rentus entrance areas and confirm that the restored contacts are
   visible, standing on the terrain and offering the appropriate quest dialogs.
2. Finish an academy introduction and confirm the new hand-in works.
3. Watch the Terath and Kunax quest counters advance after the correct kills.
4. Clear Crucible through Kamara and Vanktrist, checking stage transitions and
   quest credit.
5. Defeat Pashid and confirm both the quest objective and match rewards.
6. Defeat Beritra, interact with Ominous Darkness and collect its quest items.

The client quest archive was not edited. Some legacy map markers or directions
may still point at the old locations; use this report for relocated NPCs.
