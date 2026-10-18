# Aion Aetherfall development

## Working rules

- Make focused changes; preserve unrelated dirty work and every installed feature.
- Default to the original development client and server: maintained source, normal
  build, then affected-file installation in the original. Do not update full/test
  repacks, prepare patches or publish releases unless the user explicitly requests
  that destination. When asked to apply the original to a repack, transfer the
  current original's relevant maintained changes while preserving repack-specific settings.
- Carry authorized work through source repair, normal build and affected-file delivery.
  The user performs actual server/client/gameplay acceptance. Report what is still unverified.
- Use the current request and fresh evidence over historical notes. Do not reopen completed
  ports solely because gameplay acceptance is pending. Do not suppress warnings or invent
  native data to make logs quiet.
- Prefer targeted file reads and bounded log summaries. Do not read the entire history,
  inventory or repository before a small edit. Do not dump repeated stack traces.
- No full suites, browser tests, broad protected-file scans or new test files by default.
  Use the normal builder and only checks necessary for the changed behavior; investigate
  actual failures. Do not repeat a passing check without new changes or evidence.
- Do not start/stop/restart game components, attach diagnostic agents or run gameplay
  fixtures without authorization covering that action. Existing authorization persists.
  Before replacing running binaries, verify the affected original/repack processes closed
  normally; an inaccessible process list is not proof of closure. Never force-kill them.
- Keep source, original development installation, full repack, installed test repack and
  published release distinct. Deliver only to the destinations requested. Publishing is
  a separate action; the owner uses Publish Update when desired.
- Every requested repack update includes updating `CHANGELOG.md` and delivering the
  matching changelog with the repack and prepared patch. Describe actual player-facing
  changes, preserve earlier entries and distinguish local preparation from publication.
- Do not message other chats or delegate work without user authorization. If another chat
  is editing the same source, preserve its changes and use an isolated build or wait for
  a stable handoff. Never overwrite a newer runtime with an older prepared package.

## Build and delivery

- Maintained source -> normal Maven/native builder -> exact builder outputs -> backup,
  affected-file copy and copied-file hashes. Do not transplant/rewrite compiled JAR classes,
  restore override JARs or use runtime attachment as a deployment shortcut.
- Normal server builder: `tools/build-components.ps1`; choose a fresh external OutputRoot.
  Maven tests are skipped by default. Package complete normal resources and dependencies.
  Copy only affected outputs; coordinate dependency upgrades when applicable.
- Preserve owner configuration, credentials, database contents, characters, progress,
  maps, all client mods and recovery packages. Backups and receipts belong in the external
  workspace. Keep rollback possible. Do not delete backups as clutter.
- Refresh relevant current receipts and `docs/INSTALLED_MODS.json` before installation;
  it is a dated snapshot. Update affected inventory records after copying. Avoid a full
  installation rescan when only a few known files changed.
- Native client replacements require the affected client closed. Game.dll changes must
  preserve graphics/cursor compatibility, active and restore baselines, signing, native
  pet/Lua behavior and the active English locale. Use existing maintained compatibility
  helpers rather than bypassing hash guards.
- Keep generated build, extraction, diagnostics and recovery output outside source/runtime.
  Existing prepared bulk migrations remain deferred until explicitly requested; no moves,
  junction creation or backup retirement as part of unrelated work.

## Playerbots contracts

- Persistent bot identities, pools, membership, targets, timers and progress belong in the
  native SQL repository, not JSON/properties shadow databases. Configuration/static data
  may remain files. Preserve native IDs, custody and transactional commit/retry behavior.
- Owned alts must retain their class, level, earned items, appearance and builds unless
  explicitly changed. Dedicated generated bots and normal owned alts have different rules.
- Before claiming an upstream port, inspect the relevant pinned WoW Playerbots functions,
  dependencies and defaults; map them to native Aion systems and document deviations.
- For a new port slice, consult the relevant scope/tracker/validation entry and existing
  subsystem inventory, not the entire historical audit. Record the scope, behavior gained
  and remaining dependency. Full retail/Playerbots parity remains unfinished.
- Preserve native NPC behavior, combat/cast/movement admission, population lifecycle and
  bounded background work. Avoid whole-population locks or synchronous creation bursts.

## Find only the context needed

- Paths, current original/repack versions, latest receipts and unresolved items:
  `project-notes/current-state.md` (refresh actual files before delivery).
- Reusable short log diagnosis and build/delivery commands: `project-notes/workflow.md`.
- Prior feature/installation evidence: `project-notes/agent-history/INDEX.md`.
  Search that index, then read only the matching snapshot lines. Do not load the snapshot
  automatically or append new installation journals to this file.
- Installed client/server feature evidence: `docs/INSTALLED_MODS.md` and `.json`.
- Playerbots port work: relevant sections of `docs/PLAYERBOTS_PORT_SCOPE.md`,
  `docs/PLAYERBOTS_SUBSYSTEM_INVENTORY.md` and `docs/PLAYERBOTS_VALIDATION_20261004.md`.
- Missing native motions: `docs/MOTION_TIMES.md`; source/deployment resources must agree.
- Repack/setup/release work: maintained `tools/distribution` scripts and
  `releases/Maintenance/release-layout.json`; generated releases remain Git-ignored.

Keep this file short. Update the current-state note and feature-specific documents;
archive detailed receipts externally instead of repeating the full development history here.
