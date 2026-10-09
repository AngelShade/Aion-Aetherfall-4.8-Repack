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
   outputs. No handlers are executed or generated handler classes installed.
5. Load an isolated copy of actual runtime static data, overlaid with the exact
   assembly changes, using the production XML merge, full XSD validation and JAXB
   callbacks. Bind the successful report to the exact builder JAR and data hashes.
6. Compare the complete GameServer package with the latest installed receipt;
   preserve unrelated entries and installed methods, check full class linkage,
   and review any changed Commons dependency. Partial resource-list overrides
   are rejected.
7. Refresh successful process inspection, source/artifact/runtime/client hashes
   and the latest receipt before replacement. Back up every changed file and
   generated cache, copy complete builder files unchanged, invalidate affected
   caches, and roll back replacements if a preservation check fails.
8. Refresh `docs/INSTALLED_MODS.json` and record the external receipt. Report
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
