# Focused Aion development workflow

Read this only for diagnostics, build or delivery work. No extra plugin is required.

## Start with the concrete failure

1. Establish which installation and component the report concerns.
2. Read a bounded log summary and the source for the affected path.
3. Explain the cause from evidence; distinguish repeated errors from different defects.
4. Repair maintained source, preserving existing mods and unrelated work.
5. Build once normally. Investigate failures; repeat only the affected checks when needed.
6. For authorized installation, verify closure, back up affected files, copy exact builder
   outputs, verify copied hashes and record the receipt. User owns actual gameplay testing.

Do not add exhaustive audits, full protected-file scans or browser tests to a simple copy.
For a substantial behavior change, choose a small meaningful check that could catch a real
failure; do not create tests that merely mirror implementation or run gameplay fixtures.

## Short log summaries

Original GameServer:

```powershell
python tools/summarize-server-log.py target-deploy/game-server/log/server_console.log
```

Installed downloaded test repack:

```powershell
python tools/summarize-server-log.py releases/Aetherfall-Downloader/Games/Aetherfall-0.86/Server/game-server/log/server_console.log
```

The tool reads at most the last 8 MiB / 30,000 lines, counts WARN/ERROR events separately
from stack frames, and shows up to eight groups with one example and three frames each.
Counts describe only the captured tail, not all historical logs. Numeric values are
normalized for grouping; the original example and deepest captured cause remain visible.
It also supports the component's `server_errors.log` and `server_warnings.log` formats.
It does not attach to Java, query SQL, change logs or alter game state.

Useful bounded variations:

```powershell
python tools/summarize-server-log.py PATH --limit 12 --frames 5
python tools/summarize-server-log.py PATH --max-mib 2 --tail-lines 5000 --json
```

Read the exact source/error excerpt after choosing a group. Increase the window only when
needed. Do not paste thousands of stack frames into context or repeatedly print unchanged
build progress. A quiet log tail is not proof of successful gameplay.

## Normal build

```powershell
tools/build-components.ps1 -Modules game-server -OutputRoot "D:/Proiecte/Project Restructure/Aion Development Workspace/staging/target/NEW-SCOPE"
```

Choose a fresh scope directory. The normal builder skips tests and records source/artifact
hashes. Read the end of `maven.log`; expand only around a failure. Do not rebuild a passing
unchanged product. Use corresponding maintained native builders for client/Setup changes.

If another chat is editing source, use a stable agreed baseline or isolated worktree.
Do not silently include unfinished edits. Preserve the latest installed behavior and
check that the runtime did not change while the package was being prepared.

## Context and handoffs

For Playerbots, begin with `docs/PLAYERBOTS_CURRENT_STATUS.md`. Record the affected
movement/interaction owner and preserved transitions before editing a shared
handoff. Keep party-follow policy distinct from independent world activity while
reusing native movement. Retain queued request age and physical arrival authority;
do not turn a formation preference into a recruitment refusal. Select only the
focused check appropriate to the changed path; this adds no full-suite requirement.

- AGENTS.md holds standing rules and pointers; keep it short.
- `current-state.md` holds current original/repack versions and receipt pointers.
- Feature documents hold behavior contracts and unresolved details.
- External receipts hold copy hashes, recovery data and build evidence.
- History index locates older evidence; load only the relevant section.
- Handoffs state scope, changed files, current build/receipt, remaining issue and acceptance
  limits. Avoid copying an entire chat into the next task.
- Use independent agents only when authorized and useful. Do not duplicate the same audit
  in several chats or rerun completed work solely because gameplay acceptance is pending.
