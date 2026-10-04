# Party inventory checkpoint failures: read-only diagnosis

Status: **PB-REPAIR-INV-001 installed live/on disk at 22:49 local time** in receipt
`backups/playerbots-recruitment-20261004-224922-627052`. The diagnosis below is the
pre-repair evidence; no new item IDs or foreign-row recovery were required.
This is a repair diagnosis, not a new upstream port slice. PB-PORT-005 remains
the next broad companion implementation. Existing item-ID release investigation
remains open; this finding does not establish its historical root cause.

## Evidence

Two read-only live snapshots of GameServer PID 24908 at 19:27:46 and 19:29:27 UTC
inspected all five companion sessions, private storage, equipment and retained
deleted-item queues. The second uses object identity so an obsolete item and a
new item sharing a recycled numeric ID are both represented. SELECT queries only;
no save, ID allocation/release, item mutation, forced tick, method redefinition,
client installation or server lifecycle operation occurred.

| Bot | Obsolete queue entry | Current persisted item |
| --- | --- | --- |
| MagicDps | ID 16204, template 164000119, count 0, UPDATED | Owner Baby 9403, template 182004100 in first snapshot; row gone in second |
| Healeru | ID 27593, template 164000064, count 0, UPDATED | Owner Baby 9403, template 121000328 |
| Healeru | ID 190912, template 164000134, count 0, UPDATED | Owner Baby 9403, template 113100300 |
| LeMuse | ID 30337, template 162000009, count 0, UPDATED | Owner Tanku 106596, template 162000009, count 5 |
| Tanku | ID 106916, template 164000073, count 0, UPDATED | Owner Tanku 106596, different template 162000043, count 15 |

RangeDps had no custody mismatch in either snapshot. Current server errors also
confirm Tanku failures at 22:22:55, 22:24:55 and 22:26:55 local time. Snapshot
differences show ordinary ongoing gameplay; absence of a conflicting row at one
instant does not mean the obsolete record has been removed. Reported LeMuse ID
34125 had no persisted row during these snapshots and is not independently
classified here.

Evidence artifacts: `target/playerbots-custody-diagnostic/party-snapshot.txt`,
`target/playerbots-custody-diagnostic/party-snapshot-v2.txt`, and
`target-deploy/game-server/log/server_errors.log`. Diagnostic Java sources/JARs
are in the same target directory; they do not patch loaded methods.

## Cause and effect

1. `Storage.delete` appends consumed/deleted items to its deletion queue.
2. A successful inventory transaction changes committed item states to UPDATED
   and releases the IDs of deleted items after commit. The old objects remain in
   the queue until storage reset.
3. Later ordinary item allocation can legitimately reuse these freed IDs.
4. `Player.getDirtyItemsToUpdate` includes the whole deletion queue whenever
   storage requires another update, including these already-committed objects.
5. `InventoryDAO.storeCompanionInventory` checks custody for every collected
   object, while its actual SQL operations filter to NEW, CHANGED and DELETED.
   An obsolete UPDATED record therefore triggers a false custody failure despite
   being excluded from all database writes.
6. `PlayerBotPersistence.save` rolls back the complete checkpoint and marks the
   inventory dirty. The two-minute periodic retry encounters the obsolete record
   again. Native progress in that checkpoint also remains unsaved until a
   successful retry.

The captured mismatches are **obsolete committed deletion records**, not active
equipment that requires reissuing. Do not run the earlier Tanku collision repair
agent, change foreign rows, release these reused IDs, or weaken custody checks.

## Bounded correction and required verification

- Build the companion transaction's pending set from NEW, CHANGED and DELETED
  states before shared-storage/custody checks, and use that same set for writes
  and post-commit bookkeeping. UPDATED records must not become write candidates.
- If purging committed deletion queues, remove only exact committed object
  identities after successful commit. Preserve genuine pending deletions and
  rollback retries; do not remove another item's record by recycled ID alone.
- Test obsolete UPDATED foreign-owner and different-template records alongside
  real pending changes. Verify they cannot block or modify the foreign row.
- Verify real NEW/CHANGED/DELETED foreign custody and shared-storage writes still
  fail before any SQL mutation; retain full checkpoint rollback and dirty retry.
- Verify successful deletion releases its ID once, and a later save cannot
  delete or release the new owner of that reused ID.
- After an authorized bounded cumulative install, confirm all five normal
  periodic inventory/progress saves, preserving owner inventory, native party,
  alt builds, latest spacing/engine helpers and all installed mods.

The pasted Tanku wipe/summon exception is a separate recovery-path issue:
`PlayerBotRecovery.tick` invokes `summonAll`, and `PlayerBotTravel.summon` throws
when `relocate` returns false after revival. The exact rejection at the historical
instant was not captured. It must not be marked fixed by an inventory repair or
attributed to an animation gate without reproducing/capturing the native state.

## Installed correction and verification

`InventoryDAO.storeCompanionInventory` now filters native dirty collection to
NEW, UPDATE_REQUIRED and DELETED before shared-storage/custody checks, SQL writes
and returned post-commit bookkeeping. UPDATED/NOACTION records are excluded.
Genuine pending custody errors still fail before writes and mark storage for
retry. The post-commit method and ID-release logic remain unchanged; the pending
set prevents an obsolete committed deletion from participating a second time.
Deletion queues are retained; no risky removal by recycled numeric identity.

- **85 offline production-method checks** pass against compiled source and the
  effective cumulative package. Recording JDBC covers ignored committed foreign
  IDs, same-ID distinct old/new objects, owner/template/shared-storage guards for
  all pending states, delete/insert/update routing, caller-owned transactions,
  failed SQL retry and unchanged states before caller commit. No DB/world/ID
  allocation/release. The logged `fixture batch failure` is deliberate.
- One existing method/class changed; **133 other override entries byte-identical**.
  All command/media/launcher files in the cumulative package are unchanged.
- Fresh update agent **43** preloaded original cumulative classes before JAR
  copying, then applied one definition atomically with effective rollback.
  Five companion sessions and one human connection remained active; all 44
  captured settings files were unchanged after install.
- A null-returning observation transformer captured loaded InventoryDAO;
  **all 31 methods match the installed package**. No native save/tick was forced.
- Installed mod inventory passes **15 checks**, preserving **31 client hashes**,
  base JAR and launcher; it records 70 client/78 server historical receipts.
- Override SHA-256:
  `b94e8d41b0a4acb24430694f768548f4d523a750ce84a67beefc749c3aa3b4b2`.

Artifacts: `target/playerbots-custody-repair/offline-checks.txt`,
`package/method-review.json`, `loaded/effective-loaded.jar`,
`preservation-before.json`, `preservation-after.json`, plus receipt preflight/
runtime/install records. Source test `PlayerBotCustodyCheck` is included in the
standard companion check script. Use `stage_custody_update.py` for bounded
follow-up staging; future attach update revisions must exceed **43**.

**User performs in-game testing.** This installation does not claim normal
periodic native checkpoint/dismiss/resummon acceptance or a fix for the separate
Tanku wipe/summon error. Verify the next normal saves stop custody errors and
progress survives dismiss/resummon. Do not clear custody guards if a genuine new
pending mismatch appears. Historical release-path investigation remains open;
PB-PORT-005 remains the next broad port slice.
