# Cancellable summon and owner-distance recall - 8 October 2026

PB-REPAIR-SUMMON-001 repairs the screenshot-confirmed blanket summon rejection.
PB-CUSTOM-RECALL-001 implements the user's new instruction: a distant companion
must teleport to its owner regardless of its current activity and resume Follow.
Installed offline in external recovery receipt
`playerbots-recruitment-20261008-195139-913545`. Actual native/client acceptance
is pending the user. The source repository and installed cumulative JAR are
updated; GameServer and client remain off.

## Evidence and behavior

The current SummonPolicy preflight rejects the entire party for any member's
cast, loot, item/NPC channel, combat timer or aggro, and for party combat. That
matches the reported repeated chat message. PartyBehavior.catchUp only handles
FOLLOW/PASSIVE and excludes casts, immobilization and the bot's own combat/hate.
Session.tick processes trade and map transfer before catch-up. A remote bot can
therefore remain stranded indefinitely while busy or assigned Stay/Guard/mission.

Manual Summon now requires enabled module/summon controls, a live spawned owner,
native owner instance, active owned bot and the same party, and the owner's own
combat timer to be clear. Other companions' combat or activity does not veto the
request. Native exchange cancellation retains the established custody guards and
returns offers; native skill/item-use/move observers cancel current activities,
and native DropService closes looting. A custody-held/dismissing session remains
protected. Native resurrection remains available to explicit Summon and wipe
recovery; no living bot's HP, MP, gear, skills, Stigmas or level is replaced.

Automatic recall runs at the start of the living owned companion's Session.tick,
before trade, map transfer, movement failure, revival and strategy selection.
Beyond the existing 60m distance, or after map/instance separation, it cancels
those activities, clears mission/attack/quest targets and engine continuation,
sets FOLLOW, and uses native party/map/geodata relocation. It ignores bot combat,
casting, looting, channels, immobilization and Stay/Guard orders. Owner combat
also does not prevent automatic distance recall. Dead companions retain native
resurrection/wipe handling; a dead or unspawned owner is not a valid destination.

Scoped recall authorization bypasses the ordinary same-map relocation combat
veto only for the exact eligible session/actor. Success and exceptions clear the
scope. A failed mover resets only in this explicit recall scope; ordinary abort
continues to expose movement failures. Transfer errors retain the session and
retry with backoff, including failures after its world position changed. Closing
sessions release transient retry state. No map-specific threshold or geometry
special case is added; the existing floor/door navigation repair survives.

## Source and native mapping

This is a repair plus explicitly requested custom recovery policy. Pinned
mod-playerbots commit is 037c01418b5d01506917a3db9b44fd56ac5f965c.
FollowActions.cpp::FollowAction and MovementActions.cpp::MovementAction::Follow
provide the persistent owner-follow purpose. The teleport/CombatStop block in
MovementActions.cpp is commented out: unconditional distance recall is the
user's local policy, not an upstream parity claim. Aion maps it to native
ExchangeService, PlayerController cancellation, DropService, World/InstanceService
relocation and existing PlayerBotSession order/engine/movement/formation state.

New helper: PlayerBotRecall. Existing reviewed scopes: Session.tick/order,
Transfers.relocate, Recovery.ready, SummonPolicy.preflight/regroup,
PartyBehavior.close and MoveController.abortMove. Eight methods in six definitions
change; all other members and 171 prior JAR entries remain unchanged. The earlier
appearance linkage correction remains effective. This new recall policy does not
reinstall the rejected historical PlayerBotFollowRecovery package or its incidental UI.

## Offline evidence

All 2,424 server source files and 53 standalone Playerbots checks compile under
the external development root. The effective staged package passes 165 new recall
checks covering all busy-state combinations, exact ownership/party/lifecycle
eligibility, distance boundaries, scoped exception cleanup, and actual failed-mover
reset. The final tick audit proves recall precedes trade/transfer/failure/revival,
and the native cancellation/FOLLOW/relocate references are present.

Earlier effective checks pass: travel/config/summon/geometry 679, cast execution
72, engine 99, composition 49, offense 44, speed 6, trail 17, ground 57 and
formation 314. Runtime linkage passes 175 classes / 24,476 executable member
references against installed dependencies, with full-source output excluded.
These isolated checks do not perform native world relocation, exchange/loot
transactions, resurrection, DB/ID writes or actual client acceptance.

Package: external staging/output/playerbots-recall-20261008.
Payload SHA-256: 0524e84d93eae1aba6b0e966e47475e54b82f4f07362dc080e1f031b8b11b7ff.
Baseline SHA-256: 6f72603b8a385f5f9f39c3bdd9b9a8783c06ab17b549e648195e915b9503c5c4.
Maintained stage_recall_update.py, verify_recall_package.py and
install_recall_offline.py keep hash guards, external recovery and actual process
inspection. The source repository remains C:/Git/aion-server; migration is deferred.

PB-VAL-005 still needs the user's visible Summon, far-bot recall and following
tests, including casts, combat, channels, trading and previously stuck movement.
Independent next class port stays PB-PORT-005B Spiritmaster; broader parity and
the item-ID release investigation remain open.

## Installed preservation record

Only libs/playerbot-recruitment-fix.jar changed in the installation. Every other
receipted server file, base JAR, launcher, geodata, scripts/settings and client
files remain as currently installed. The first install attempt refused stale
client graphics-preference hashes saved during client exit, before any runtime
replacement. Only system.cfg/SystemOptionGraphics.cfg differed; all current mod
checks passed. The fresh preference hashes were recorded and preserved without
replacing those files. Inventory passes 20 checks, 31 current client hashes,
73 client and 105 server historical receipts. Postinstall native dependency
inspection passes against the actual installed override. No DB/world/ID or
real-client fixture execution occurred; gameplay remains the user's acceptance.
