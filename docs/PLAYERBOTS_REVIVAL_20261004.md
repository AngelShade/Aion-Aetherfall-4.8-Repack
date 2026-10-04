# Companion corpse animation after resurrection

Baby reported Templaru moving in a death animation and appearing unable to cast.
Read-only native reports are `target/playerbots-corpse/live-before.txt` and
`live-detail.txt`. Templaru was alive with ACTIVE stance, no flight/corpse state,
and valid movement/attack capability. A later sample recorded production skill
3024 against Raging Kraterr. The client screenshot and server state disagreed;
the report did not establish a permanent server casting failure.

Templaru had a native rebirth effect. Previously the companion accepted native
rebirth/skill resurrection immediately during its periodic death tick and could
resume movement the next tick. Client death/rebirth animation timing is a likely
cause of the stale corpse pose, but its exact packet timing was not captured.

`PlayerBotRevival` delays automatic acceptance for 2.5 seconds after observing
death and holds movement/actions for 2 seconds after any native resurrection.
The native `PlayerReviveService.revive` records recovery only for companions.
Recovery refreshes the already living actor's native observer information and
resurrection emotion; HP, MP, native resurrection eligibility and penalties remain
the responsibility of the existing revive service. Normal resting, flight,
looting and private-shop stances are preserved, including the native bit shared
by LOOTING and FLOATING_CORPSE. Dismissal removes recovery state.

Whole-party transfer remains ahead of the animation gate. Installed wipe/manual
recovery still runs and every native revive enters the same recovery interval.
The supplies/recovery receipts `...134823-288459` and `...135149-590713`, item-ID
repair and legacy-camp repair were refreshed and preserved during staging.

Installed receipts:

- `backups/playerbots-recruitment-20261004-135317-222406`: two session methods,
  the native revive method and new helper, installed live with agent revision 37,
  preloading, effective rollback, preserved sessions/preferences and one human
  connection before/after.
- `backups/playerbots-recruitment-20261004-135724-862874`: final helper stance
  correction installed with the server stopped.
  Future helper replacements explicitly scope ready/refresh for live redefinition.
- `backups/playerbots-recruitment-20261004-140017-509159`: final thread-visible
  death/recovery timestamps, also installed while stopped. This is the latest
  cumulative baseline; all earlier entries/methods are retained.

Validation:

- Full source/command compilation and existing companion suites passed in
  `target/playerbots-corpse/source-checks.txt`.
- Isolated native death, refusal without resurrection rights, authorized native
  skill revive, authorization consumption, corpse cleanup, recovery delay and
  unchanged refresh HP/MP passed in `native-check.txt`.
- That first fixture's final cast failed because Healing Wind requires party
  targets and the isolated actor had no party. Its earlier lifecycle checks passed;
  do not report its casting check as accepted.
- The corrected single-target Healing Light fixture is
  `PlayerBotRevivalCheckAgent2`. GameServer shut down externally before this test
  could attach, so post-revive native casting remains unverified.
- Ten actual native stance-mask cases pass in `stance-check.txt`, including
  ground/air looting, flight/glide, rest/shop, real death and stale corpse recovery.
- All 15 installed-mod preservation checks pass. Client files, base server JAR,
  launcher, media and unselected cumulative override members are retained.

GameServer is stopped. A restart was requested from the user before continuing
live verification, since the shutdown may have been intentional. Fresh client
visual acceptance, another real death/rebirth and broader Playerbots gameplay
remain pending. The corrected casting fixture must be run after startup; do not
equate installed hashes or the native lifecycle checks with client acceptance.
