# Cursed Zirius (quest 3057)

Added to the held original-only teleport/statue package on 19 October 2026.
Maintained changes are the quest handler, one NPC-specific AI, NPC214576's AI
binding and the two complementary Zirius spawn schedules in Theobomos.

- Cursed Worg: 22:00–09:00 game time. Normal Zirius: 09:00–22:00.
- The existing spring centre (1691.41,219.09,72.62) and 30m distance check remain.
  The cure requires a living, spawned Worg on map210060000, low health and an
  active quest at step0 in the same instance. Killing him does not complete it.
- The NPC-specific AI checks spring arrival during native movement/think
  events, using the actual hated target/master. The quest also checks attack
  and target-player movement. Passers-by cannot claim someone else's lure by
  entering the area. Normal hostility, chase distances, aggro decay and resets
  remain; this is not an automatic friendly escort.
- A successful cure uses native human Zirius798213 at his current spring
  position for the existing five-minute temporary-NPC lifetime. The native
  quest state becomes REWARD and his existing reward dialogue works there.
  The Worg is removed without a kill/corpse/XP/drop, with native respawn
  scheduling retained. Progress, prerequisites and rewards are unchanged.
- A per-NPC completion lock prevents repeated arrival/attack notifications
  from producing duplicate cures. No shared Playerbots or global AI changes.

The supplied [quest walkthrough](https://aion.mmorpg-life.com/quest/cursed-zirius/12560/)
describes low-health luring and the 22:00/09:00 schedule. A contemporary
[Templar walkthrough](https://pdfcoffee.com/aion-templar-guide-asmo-elyos-pdf-free.html)
specifies 15–20% health. The implementation uses displayed HP <=20%; the exact
retail server AI threshold was not extracted, so this is a walkthrough-based
choice rather than a verified retail constant.

The first edit batch reported a context-match failure. Fresh file checks found
the quest and AI changes present but the NPC binding/spawn times absent; those
were applied separately. All four actual files were then verified. The normal
Maven build and focused compilation of the affected AI/quest against its normal
output passed. No full suites, new test files, runtime attachment or gameplay.

`client-mods/hotspot-timing/server_delivery.py prepare --with-statue --with-zirius`
stages exact normal builder resources. `extend_quest_package.py` retains the
checked native client bytes and verifies that the shared NPC source differs
from the earlier statue package only by Zirius's AI binding before rebinding
that source hash. Previous prepared packages are retained.

**Installed on the original client/GameServer on 19 October 2026**, with the
statue/timing package. Joined receipt: external
`archives/delivery/teleport-statue-zirius-20261019.json`; affected-file backups
and copied hashes verified. No repack changes or component starts.
User acceptance remains the actual day/night
transition, low-health lure, arrival cure, human reward dialogue and native
respawn behavior, alongside the statue/timing package acceptance.

19 October startup repair: the quest must not import `CursedZiriusAI` because
AI and quest scripts compile in separate contexts. The quest now checks the
same cure conditions directly against core server APIs. The earlier combined
AI/quest compile missed this boundary. Normal Maven packaging and isolated
quest compilation against installed libraries passed. Only the exact packaged
quest was installed on the original server. Backup/hash receipt:
`archives/server/backups/zirius-script-context-20261019/receipt.json`.
Server startup and gameplay remain for user acceptance.
