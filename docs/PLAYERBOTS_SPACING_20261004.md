# Owner-controlled ranged spacing — 4 October 2026

**Installed live and persisted on disk. Actual client spacing, terrain and casting
acceptance remain PB-VAL-005.** The user clarified that RangeDps was shooting again;
the remaining request was compact, adjustable owner/target spacing and an end to
the ranged retreat/tank chase loop. Read-only attack snapshots found a living,
able-to-attack Ranger with a bow, between fights and without an eligible target.
No attack/resources/weapon guards were bypassed to address that transient report.

Latest cumulative receipt:
`backups/playerbots-recruitment-20261004-220541-625450`, retaining the main spacing
receipt `...215853-061213`, concurrent engine composition `...213711-193912`, earlier
tank/range repair `...211935-035574` and the complete installed baseline.
Override SHA-256: `d79e24d982db9190c1badfd78d537932408c1a213a8c4853202f532a3fc215ac`.
The retained explicit live-update approval covers this continuation. GameServer
PID 24908 stayed running; no server lifecycle or client archive/DLL change occurred.

## Player controls and combat behavior

Reopen Companions, select a ranged companion and use **Overview → Ranged spacing**:

- Follow spread around you: **2–12 m**, default **4 m**. Circle/Spread radius and
  Box bearings follow this setting. Line wings scale together so inner slots stay
  inside the outer radius and same-side companions do not overlap. The tank keeps
  its forward formation slot. Each ranged character has its own value.
- Distance from enemy while attacking: **4–18 m**, default **10 m**. Native usable
  learned offensive/weapon reach still caps the configured distance. Values are
  native range allowances, with normal actor body-radius geometry.
- Save writes account/character-checked
  `config/playerbots/spacing-character-<id>.properties` atomically. Values reload
  after dismissal/recruitment or restart and apply to owned-alt AI without changing
  class, level, skills, Stigmas, equipment or builds. Melee roles and support
  Chanters retain their normal positioning.

The old retreat chased the full 23–28 m spell-distance shell whenever a pursuer
entered roughly 60% of that range. The new policy allows **one at-most-2 m retreat**
when an enemy gets within 2.5 m plus native body bounds. It consumes that retreat
even if geometry or the owner leash prevents the step, then lets normal attacks
win. Movement, target switches and brief target gaps cannot renew it. Five seconds
without an eligible enemy reset the engagement; dismissal clears the state.
Retreat cannot stretch the party past owner spread plus configured attack distance.
Hazards, legal movement, native cast/animation/resource gates and friendly recovery
remain authoritative. Short hostile skills cannot pull ranged builds into melee;
usable intermediate ranged skills remain eligible at the compact distance.
This is a bounded combat policy, not completion of full class/world/PvP strategies.

## Installation and evidence

The main update changes ten selected method bodies in five existing classes and
adds `PlayerBotSpacing` with its two state/value classes. It preserves original
schemas, synthetic members and enum mappings. The final Line correction changes
only existing helper `formation` and its explanatory UI text. That helper has an
explicit runtime SCOPES entry; HELPERS alone is not sufficient. Fresh agents
41/42 preload all original override classes before copying the JAR and retain
effective rollback. Both runtime receipts preserve five spawned companions, their
existing preferences and one human connection.

- `target/playerbots-range-attack/source-spacing-checks.txt`: full source and
  companion suite passed, including existing engine composition and offense/quest/
  revival behavior. Final small helper/test refinements were recompiled separately.
- `effective-line-final.txt`: **35 production spacing checks** against the effective
  cumulative package, including bounds/NaN, account ownership, save/reload, actual
  HTTP action routing, native range cap, distinct Line slots, finite retreat,
  owner leash, no reset through target movement/gaps and reset after quiet combat.
  Fixtures skip world registration, DB, native casts and ID allocation/release;
  only verified private fixture configuration paths are written and cleaned up.
- Main effective package also passed **38 position/final cast-prerequisite**,
  **44 offense gate**, **35 quest objective** and **49 engine composition** checks.
- `spacing-browser-line-final.txt`: actual offscreen Aion WebKit desktop/narrow
  rendering, value editing, tab/refresh draft retention, invalid-value rejection
  and save passed, alongside 27 authenticated fixture actions and existing roster/
  preset/equipment/quest/owned-alt controls. This is an isolated fixture, not human
  in-game browser acceptance. Screenshots are under `output/playwright/playerbots/`.
- Main receipt `live-spacing-check.txt`: **22 read-only actual-companion policy/
  geometry checks**. MagicDps, RangeDps, Healeru and LeMuse use 10 m combat range and
  expose 4/10 settings; Tanku retains 1.5 m melee range and first formation slot.
- `spacing-runtime-final/`: a null observation transformer captured eight actual
  loaded definitions without replacing bytecode or executing AI. **57 selected
  loaded methods/lambdas match the reviewed installed override**.
- `spacing-postinstall-audit-final/report.json`: **51 pinned reference hashes and
  135 effective methods/lambdas match source**; legacy rune veto remains inactive.
- `spacing-preservation-after.json`: **126 earlier entries** remain byte-identical
  through both installs, including the newer engine helper work; the final
  one-method correction retains **133 entries**. All 31 client hashes, base JAR,
  launcher and preferences survive. Forty property/JSON settings files were checked;
  five generated-item provenance lists grew during normal gameplay, with preference
  values unchanged. All 15 mod checks pass; inventory lists 70 client/77 server receipts.

Use `stage_spacing_update.py` for bounded follow-up work. Include changed existing
helper methods in runtime SCOPES and keep fresh agents, preloading, hash guards and
rollback. Next attach update revision must exceed **42**. Preserve both spacing
receipts and the newer engine-composition baseline. PB-VAL-005 actual following,
terrain/LoS, moving-pursuer combat and client shots, PB-VAL-009 native strategy/
chain acceptance, post-revive client acceptance, the broader port and the persisted
item-ID release investigation remain unfinished. The canonical tracker and full
subsystem inventory remain authoritative; next broad implementation is PB-PORT-005.
