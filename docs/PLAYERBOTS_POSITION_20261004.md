# Tank formation and ranged combat movement — 4 October 2026

**Installed live and persisted on disk. Actual client movement/casting acceptance
PB-VAL-005 remains pending.** The server was already running as PID 24908 when
installation was attempted. The offline installer refused without changing files.
Automatic approval review rejected the first live attempt because of the earlier
keep-server-stopped instruction. The user then explicitly approved applying this
verified live update. No server start, stop or restart was performed.

Latest cumulative receipt:
`backups/playerbots-recruitment-20261004-211935-035574`, retaining the main repair
`...211220-016216` and complete quest arbitration/offense baseline
`...203902-131350` / `...193448-980138` plus every earlier installed modification.
Override SHA-256: `5acf1014eadac288b2e1e577e46457cd9496f964690dad34d19cb28c4b32247c`.

## Reported behavior and causes

The user showed the tank outside the front slot in every formation and ranged
companions approaching enemies into melee. Formation slots were sorted by
character ID rather than combat role. Circle/Spread started at a side position,
Line lacked a forward slot, and formation heading only updated during movement.
Combat approach destinations were the enemy's center; movement-tolerant attack
range checks could treat the actor as ready before its route finished. Short
hostile spells could request another approach into melee. Healer/Support roles
also used a fixed short combat distance.

## Installed behavior

- Tank, then melee, then other roles determine stable formation order; IDs break
  ties. Slot zero is forward in Circle, Spread and Line, and a forward corner in
  Box. Stationary owner turns update the damped heading too. Formations still
  govern travel/regrouping; combat and explicit orders retain their own logic.
- Ranged/Healer/non-Chanter Support roles use learned offensive range and native
  weapon range. The most common long offensive range wins, with longer-range
  ties; a rare specialty spell cannot strand the ordinary rotation. Heal range
  is a fallback when the build has no long offensive range. Melee tanks and
  support Chanters keep their melee build.
- Approach routes aim at native casting distance plus actor body radii, with a
  small margin. Cast/reach gates check physical range. Ranged obstructed-sight
  routes probe lateral points at spell distance; native collision/pathfinding,
  hazards and cast sight checks remain authoritative.
- Short hostile spells remain available when an enemy comes close but cannot
  pull a ranged build into melee. Friendly heal/resurrection approaches remain
  legal. Close enemy retreat applies to ranged support/healers as well as DPS;
  explicit non-Follow orders retain their behavior.

Main repair: 11 changed existing method bodies across six class definitions,
one new `PlayerBotCombatPosition` helper and one existing base-JAR ReachAction
override. Original schemas/synthetic members/enum mappings survive. Final
common-range correction changes only existing helper `desired`; its explicit
runtime SCOPES entry is required. Live agents 38/39 preload original classes
before JAR replacement and retain effective rollback definitions.

## Evidence and limits

- Full source compilation and companion suite:
  `target/playerbots-position/final-source-checks.txt`. The final scalar refinement
  was recompiled with three additional focused cases; 38 production position/
  range/cast-prerequisite checks pass against the effective cumulative package.
- Existing final-action offense checks (44), quest arbitration checks (35) and
  formation geometry/settings checks (1,200) passed against the staged repair.
- `target/playerbots-position/postinstall-audit-final/report.json`: 51 pinned
  upstream hashes and 115 effective methods/lambdas match the compiled snapshot.
  Legacy rune veto remains inactive. Disk verification is not live casting proof.
- Both receipt `runtime-preflight.txt` files confirm complete override preloading.
  Runtime verification retains five spawned companions, their existing preferences,
  native singleton/session/group state, roster serialization and one human connection.
- Final receipt `live-position-check.txt`: 22 read-only real-companion policy/
  geometry checks. Tanku gets the first slot; MagicDps, Healeru and LeMuse use
  23.5m native range, RangeDps 24.75m; Tanku retains 1.5m melee range. These values
  exclude the additional native actor body-radius allowance.
- `target/playerbots-position/postinstall-preservation.json`: 114 earlier entries
  remain byte-identical through both installs; the one-method follow-up retains
  120 entries. All 31 client hashes, base JAR and launcher survive. Forty settings
  files were checked; two generated-item provenance lists legitimately grew during
  gameplay, with preference values preserved. All 15 inventory checks pass;
  inventory records 70 client and 74 server receipts.

Native movement through actual terrain/corners, moving enemies, post-revive
casting and final client appearance still require gameplay acceptance. Offline
fixtures do not allocate/release native IDs, register world actors, write DB rows
or cast spells. Live read checks do not move actors or modify characters.
The broader port, PB-VAL-005, class/world/dungeon scope and persisted item-ID
release-path investigation remain unfinished. Next update agent revision >39.
Use `stage_position_update.py`; `install_position_offline.py` requires a stopped
server, while an explicitly authorized live install uses the guarded companion
installer with `--preload-override` and a fresh equipment agent.
