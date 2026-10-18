---
name: aion-repack
description: Update an explicitly requested Aion Aetherfall repack from the current original, maintain its changelog and cumulative patch, or diagnose Setup and release delivery.
---

# Repack and release delivery

Run only for an explicit repack, Setup or release task. An earlier repack update is
not standing permission for future ones. Read `releases/Maintenance/release-layout.json`
and the maintained `tools/distribution` flow; refresh current receipts and versions.
Keep source, original, full uninitialized repack, initialized test copy and published
channel distinct. Preserve the original during a repack-only update.

Use the current original's recorded normal builder output and affected client cohort.
Do not rebuild unchanged passing outputs or overwrite a newer runtime with an older
package. Keep portable connection values, owner configuration, accounts, database
contents, progress, mods and recovery files. Exclude personal DB rows and credentials.
Fresh bootstrap scripts and initialized-database migrations are different operations.
Retain the maintained population and broker initializers when applicable.

Rebase client ownership and graphics/shader paths through maintained portability
helpers. Hash guards, native signing and active/restore resources must agree.
Before runtime replacement verify affected repack processes closed normally; no
force-killing. Store backups/receipts externally and verify copied hashes.

Every requested repack update includes chronological `CHANGELOG.md` entries and the
same changelog in full repack, requested installed copy and prepared patch. Preserve
earlier entries. Describe actual player-visible changes, not internal implementation.

Use maintained prepared publication tooling with `--prepared --no-upload` for local
patch preparation. Preserve the full 0.86 base archive. Verify cumulative coverage
against its baseline FILES.json, not the preceding patch: every changed/added file
and required deletion must be represented. Setup's separate build version and its
Hub payload must agree. Check current host limits before proposing large uploads.

Publishing requires its own explicit request. For publication-status questions,
check GitHub releases and the live update manifest; local receipts cannot establish
public status. Never rewrite already-published assets merely to correct old notes.
State preparation, installation, publication and gameplay acceptance separately.
