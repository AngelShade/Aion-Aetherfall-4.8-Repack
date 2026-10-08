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

Existing cumulative override receipts are historical installation/recovery
evidence. The latest caster correction is still the previously installed package
`playerbots-recruitment-20261008-201132-984909`; recording this workflow did not
replace that package again. Before the first complete source-built replacement,
reconcile its installed behavior and the unfinished appearance source hooks in
the normal build. This is implementation work, not a new permission step or a
reason to continue method-by-method delivery.

The external Development Workspace is not a component runtime or a replacement
source checkout. The current GameServer runtime is
`C:/Git/aion-server/target-deploy/game-server`.
