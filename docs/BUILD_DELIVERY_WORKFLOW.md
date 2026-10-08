# Source build and component delivery

User-selected workflow, 8 October 2026:

1. Change the actual maintained source and required build resources in
   `C:/Git/aion-server`.
2. Build with the project's normal builder (the repository is a Maven reactor).
   Keep generated output in the selected external Aion Development Workspace.
3. While the relevant components are off, back up and copy the resulting changed
   component files into their existing runtime directories under
   `C:/Git/aion-server/target-deploy`.
4. Report build/copy completion. The user performs actual server/client and
   gameplay testing; no startup, restart or live attach is implied.

Do not modify the builder's class files or merge individual methods into a runtime
JAR after the build. Installed feature behavior belongs in source/build resources.
Runtime settings, databases, installed client modifications and unrelated dirty
source changes must survive delivery.

The complete source-build transition is installed in receipt
`playerbots-source-build-20261008-213553-086764`. Normal Maven GameServer and
Commons outputs were copied unchanged; the cumulative override was archived and
removed from the active classpath. Its installed behavior was compared with the
builder output and all bounded regressions passed. The unfinished appearance
feature is disabled explicitly in source. Older override receipts remain recovery
evidence, not an active delivery mechanism.

Use `tools/build-components.ps1` for normal component packaging. The
`aion.build.root` Maven property places module build output externally; it does
not modify compiled methods. The current transition's guarded verification/copy
scripts and exact validation record are in
`docs/PLAYERBOTS_SPIRITMASTER_20261008.md`. Future copies must use the latest actual
source-build receipt and current runtime/client state as their baseline.

The external Development Workspace is not a component runtime or a replacement
source checkout. The current GameServer runtime is
`C:/Git/aion-server/target-deploy/game-server`.
