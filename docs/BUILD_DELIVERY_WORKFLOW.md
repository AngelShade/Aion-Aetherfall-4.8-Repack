# Source build and component delivery

## Canonical GameServer release — 8 October 2026

Use this maintained entry point for every subsequent GameServer release:

```powershell
python tools/release-game-server.py --scope <tracker-ID> --install
```

Without `--install`, it prepares and verifies the full release without replacing
runtime files. `--review <external-review-directory> --install` delivers an
already prepared release only if its source, artifacts, runtime and previous
receipt still match. A stale review must be rebuilt.

For an explicitly requested companion active/saved quota change, use
`tools/configure-companion-limit.py` with the expected previous values while
GameServer/client are off. Pass its exact backed-up external manifest as
`--config-review <manifest.json>` to the canonical release. Delivery verifies
only `max_per_owner`/`generated.max_per_account` changed, every other byte is
identical, the previous receipt matches the original hash and both recovery and
installed hashes match. It retains that review and recovery in the release
receipt. This does not bypass unrelated configuration or component guards.

For configuration documentation, `tools/document-playerbot-config.py` prepares
an external review from all maintained keys and defaults read from the installed
JAR without initialization. Its `--review <directory> --install` uses fresh
offline/process/source/JAR/config guards, backs up the file and preserves every
effective value. Release and inventory tools can recognize its exact verified
receipt for `config/main/playerbots.properties` only. They require review/recovery
hashes, current JAR/config binding and effective-value preservation; this does
not permit arbitrary value changes or bypass unrelated guards. Explicit user
value edits captured before the documentation snapshot remain authoritative.
See [the 40-setting documentation and scale delivery](PLAYERBOTS_CONFIG_SCALE_20261010.md).

The entry point holds an exclusive operating-system release lock from build
through delivery. A competing release is rejected before building or copying.
It performs this sequence:

1. Snapshot maintained source/resources and run the normal Maven reactor through
   `tools/build-components.ps1` in a fresh external output directory. Complete
   assembly/resource packaging is mandatory; reused build directories are rejected.
2. Bind every output JAR/ZIP to the unchanged source/resource snapshot. Reject
   source changes during or after the build and modified builder artifacts.
3. Compare the complete assembly's managed data with actual runtime. Automatically
   include every changed maintained resource, and reject runtime-only active
   Java/XML/schema files until their behavior is reconciled into maintained source.
   Preserve runtime geometry, configuration and databases.
4. Compile all maintained handler sources against the new GameServer and Commons
   outputs with `-Xlint:deprecation,unchecked -Werror`. Deprecated API usage and
   unchecked operations must be repaired in source before release. No handlers
   are executed or generated handler classes installed.
5. Compile the focused Playerbot/ShutdownHook fixtures and run every registered
   suite against this exact GameServer/Commons output. Run release-tool regressions.
   Record build/JAR/dependency, runner/fixture/compiled-class and report hashes plus
   individual exit statuses/counts. Both canonical review and the direct installer
   require current successful evidence before copying; the receipt archives it.
6. Load an isolated copy of actual runtime static data, overlaid with the exact
   assembly changes, using the production XML merge, full XSD validation and JAXB
   callbacks. Bind the successful report to the exact builder JAR and data hashes.
7. Compare the complete GameServer package with the latest installed receipt;
   preserve unrelated entries and installed methods, check full class linkage,
   and review any changed Commons dependency. Partial resource-list overrides
   are rejected.
   Existing bundled `playerbots/media/bots.html`, `bots.css` and `bots.js` may
   change only when builder bytes exactly match their maintained config source
   and source/artifact manifests still verify. Other arbitrary package resources
   remain rejected. Compiled output is never rewritten after the normal build.
8. Refresh successful process inspection, source/artifact/runtime/client hashes
   and the latest receipt before replacement. Back up every changed file and
   generated cache, copy complete builder files unchanged, invalidate affected
   caches, and roll back replacements if a preservation check fails.
9. Refresh `docs/INSTALLED_MODS.json` and record the external receipt. Report
   actual startup and gameplay as pending user testing.

The source is `C:/Git/aion-server`. Generated output and recovery receipts remain
under `D:/Proiecte/Project Restructure/Aion Development Workspace`, or a valid
external `AION_DEV_ROOT`. Runtime remains
`C:/Git/aion-server/target-deploy/game-server`.

Do not rewrite compiled classes, transplant individual methods, use old override
stagers for new releases, start/restart/attach to components, or alter databases
as part of delivery. Preserve client modifications and owned-alt builds; keep
unfinished appearance disabled in source.

The lower-level preparation/check/delivery tools support the canonical command.
Do not replace its complete release plan with a manually chosen subset. The
historical `verify-source-delivery.py` / `install-source-build-offline.py` apply
only to the retired override-to-source transition and reject the current runtime.

## Why this gate was required

PB-BUILD-001 transitioned to full source-built Commons/GameServer outputs but
omitted matching decomposable XML/schema/handlers. Class/linkage checks passed
without exercising static-data bootstrap. PB-BUILD-002 repaired that startup
failure. PB-BUILD-003 audits all maintained resources and makes the complete
release checks mandatory. See [audit and evidence](BUILD_DELIVERY_AUDIT_20261008.md)
and [static-data repair](SOURCE_BUILD_STATIC_DATA_20261008.md).

For future changed behavior, select and run relevant existing world-free checks
as authorized. Build success, handler compilation and static loading do not
establish gameplay acceptance or full Playerbots parity.

## Method-preservation locking modifiers - 9 October 2026

PB-REPAIR-WORLD-001 removes whole-population monitor locking while retaining the same JVM method names/descriptors. The canonical delivery comparison matches synchronized modifier changes separately, reports their changed locking and bytecode, and still rejects deleted overloads/methods and unrelated signature/package changes. `python tools/test-source-release-methods.py` verifies synchronization matching, overload deletion, parameter/return/static/visibility/name rejection. All existing source/artifact/resource/linkage/process/backup/rollback guards remain enabled.
