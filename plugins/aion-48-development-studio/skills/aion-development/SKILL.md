---
name: aion-development
description: Diagnose and repair Aion Aetherfall 4.8 server errors and implement server features through normal build and original installation. Use for this Aion project, not unrelated game projects or plugin authoring.
---

# Aion development

Find the repository from the current workspace or the user's explicit path. Read its
`AGENTS.md` and relevant current-state lines; current user instructions take precedence.
Use the repo's `project-notes/workflow.md` for build/delivery commands. This plugin
does not grant lifecycle, SQL, messaging or deployment permissions by itself.

Default to **original development client/server only**. Source, original, full repack,
installed test repack and published release are separate destinations. Require an
explicit repack request before changing repacks or preparing patches. Publishing is
separate. Keep original-to-repack transfers pointed at the latest installed build.

For logs, use `tools/summarize-server-log.py` with its bounded defaults, then inspect
the source of the concrete failure. Distinguish repeated events from distinct bugs.
Do not quiet logs by hiding warnings or inventing data. A file hash proves delivery,
not that the reported behavior works in-game.

Repair maintained source, preserving unrelated dirty work. Reuse an existing exact
normal output if still current; otherwise build once with `tools/build-components.ps1`
and a fresh external OutputRoot. Maven tests are skipped by default. No broad suites,
browser tests, new tests or full preservation scans for routine delivery.

Before copying affected binaries, verify the corresponding processes closed normally.
Do not force-kill them or interpret an inaccessible process list as closure. Existing
authorization persists; ask only for missing scope or an actual required closure.
Back up externally, copy exact normal outputs, verify affected hashes and refresh
current receipts/inventory. Never patch compiled JAR classes or restore override JARs.

Use the sibling native-client, Playerbots or repack skill only when that task needs it.
Read historical evidence via the history index only when current source/receipts leave
a concrete question unresolved. Report outcome, installed destination and remaining
gameplay uncertainty concisely.
