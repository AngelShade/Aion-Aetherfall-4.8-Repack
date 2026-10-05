# PB-REPAIR-SETTINGS-001 — transient Windows care-file locks

Installed live/on disk in `backups/playerbots-recruitment-20261005-042546-945113`.
Fresh agent 49 preloaded original classes, replaced only QuestSync.State.save and
retained five companion sessions including Tancul and one human connection. No
restart/client replacement/forced gameplay or DB/ID writes occurred.

The 04:15:05 AccessDeniedException was on temporary-file rename, after writing the
complete settings payload. The target is not read-only and saved normally at
04:15:08. A temporary Windows sharing denial is supported by this evidence;
the locking process is not identified. This is a native persistence adapter
repair, not a new upstream port. QuestSync's pinned AcceptQuestAction/QueryQuestAction
eligibility maps to native shared-quest acceptance; no eligibility, quest witness,
care consent, ownership or budget semantics were changed.

PlayerBotSettingsFiles retries AccessDeniedException with 25/50/100 ms delays
(175 ms total maximum, four attempts). Atomic replacement stays first; the existing
AtomicMoveNotSupported fallback remains. Other errors propagate immediately,
permanent denial remains visible, interruption restores the interrupt flag, and
the previous committed file is never truncated. Existing temp cleanup remains.
Only State.save is in runtime SCOPES; the helper/interfaces are new JAR entries.

The package is based on actual `005644-110252` plus later receipted Saendukal
`025825`. Staging now recognizes that exact hash/rollback/base/launcher/class
receipt chain rather than dropping the shield overlay or disabling guards.
Other cumulative methods/entries/media/launcher/client mods are retained.
Stager: stage_settings_files_update.py; package:
`target/playerbots-settings-files/package-v2`.

Full source compilation and 21 isolated settings/serialization policy checks
finished before the user's instruction to stop testing. No subsequent tests or
native/client acceptance runs were performed. The real Windows lock fixture was
prepared but not run. The installer performed required preservation/preloading
and rollback checks; no forced quest or save occurred. User tests actual quest
acceptance/settings persistence. Permanent file permissions or long locks remain
errors; this is a bounded transient-lock repair. Next attach update revision >49.
Broader next class slice remains PB-PORT-005B; full parity and existing acceptance,
latency, item-ID release and wipe/summon investigations remain open.
