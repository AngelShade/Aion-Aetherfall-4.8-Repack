# Saendukal Strong Protection correction

## Diagnosis

The screenshot is Giant Saendukal (`211040`) with Strong Protection still
active after several minutes. Skill `16415` has a 500,000 ms duration cap
(8 minutes 20 seconds), and its native stack name is
`BNFI_SHIELDLONGTCOUNT10_SELF`. Although the XML also carries
`hitcount="10"`, the deployed `ShieldEffect` class has no field for that
attribute, and `AttackShieldObserver` had no hit-count handling. The runtime
therefore ignored the declared hit limit and relied on the long duration.

The previous live inspection in `target/saendukal-live-report4.txt` provides the
refresh path: Saendukal's loaded skill list still had both `16415` and
`16879` at 25%, and the last skill was `16879` (Self-Protection). Skill `16879`
has penalty skill `16415`, so that random cast reapplied Strong Protection.
Source and deployed `npc_skills.xml` now set both skills to probability 0 for
`211040`, `280338`, and night Saendukal `211701`; the daytime AI queues `16415`
once at its 25% phase. The already-running process had cached the old 25%
entries, so those XML changes were not active there.

## Fix installed on disk

- `AttackShieldObserver.checkShield` now passes every positive absorbed-damage
  event to `ShieldHitCountHelper`. The helper reads `COUNT10` from the native
  skill stack, counts hits in a synchronized weak-key map, and ends the effect
  on the tenth hit. The zero entry remains until the observer is collected, so
  an already-queued callback cannot restart the count.
- The observer retains the deployed field layout, constructors, and method
  signatures. No fields were added to a loaded class. `ShieldEffect` stays at
  its deployed schema and does not need to be replaced; its normal 7-argument
  observer construction is preserved in source.
- The two runtime classes are staged in
  `target/saendukal-strong-protection-20261005-v2/payload/libs/playerbot-recruitment-fix.jar`.
  The candidate preserves all 142 existing override entries byte-for-byte and
  adds only `AttackShieldObserver` and `ShieldHitCountHelper`. It reuses the
  installed enum-switch initializer, leaves the base server JAR and launcher
  unchanged, and omits `Skill.class` to preserve its separately installed
  combat hit-time method.
- The candidate is installed in `target-deploy/game-server/libs` with SHA-256
  `b6e6b536db8f016bb6d13c25ba6319e2ab4fb1df411f2628c62664d4a021d81f`.
  Rollback receipt:
  `target-deploy/game-server/backups/saendukal-strong-protection-20261005-025825/manifest.json`.

## Encounter skills and chest loot audit

Source and deployed data agree on the night encounter: NPC `211701` spawns at
the Eltnen throne from 21:00 to 04:00, yields if Giant Saendukal `211040` is
alive, shouts IDs `340406`/`340404` on engagement, and retreats at 50% with
shout `340407`. It spawns guards `212033`, `212022`, `212025`, and `211036`;
the chest `211861` is gated on all successfully spawned guards dying, including
the lethal-hit fallback. Shaman `211036` has level-31 Healing Energy (`16565`).

Chest `211861` has four independent custom-drop groups. Each group selects at
most one item by default, so one opening can award up to four items:

- 35%: Krall Grand Chieftain's Sword (`100000233`) or Spear (`101300150`).
- 30%: Saendukal's Anger (`100900153`).
- 30%: one of eight Anointed armor pieces.
- 30%: Saendukal's Mask (`125000955`).

The chest uses `custom_drop.xml` because ordinary global rules do not cover
chests. The four pools and their chances match between source and deployment.

## Verification and deployment status

- The focused observer/effect/helper sources compile with Java 25.
- Declaration checks confirm the observer matches the deployed field layout
  and method signatures; `ShieldEffect` is not included in the runtime overlay.
  The generated enum-switch initializer has the same disassembly as the
  installed copy.
- An offline concurrency check parsed the native stack as 10, delivered 64
  concurrent absorbed-hit events, reached zero at hit 10, and observed exactly
  one `endEffect` call. It also confirmed shields without a `COUNT` stack are
  unchanged.
- Source/deployed NPC skill XML checks confirm probabilities 0 for `16415` and
  `16879`; the throne schedule, four guard skill pools, and all chest drop groups
  were also checked in both trees.
- The installed-mod inventory passes 16 checks and records 31 client hashes,
  70 client receipts, and 83 server receipts. The base JAR and launcher hashes
  are unchanged, and all 142 earlier override entries match the rollback copy.
- A fresh process inventory found no GameServer JVM. It was not started,
  attached, or restarted. The updated JAR and NPC skill data will load on a
  future server start; actual in-game 10-hit expiry and absence of random
  recasts remain unverified.
