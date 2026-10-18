# Original installation Hub

The normal `build-setup.ps1` builder produces the same maintained Hub executable.
An `OriginalHub.json` file beside it enables original-installation mode; without
that file, the existing repack behavior remains in effect. Invalid profiles fail
explicitly before launch. No repack metadata, links or copied server/client trees
are needed.

The local profile names `ServerRoot` (containing the three component folders),
`ClientRoot`, `JavaHome`, `ReleaseFile`, `DatabaseExecutable`, `DatabaseConfig`,
`DatabasePort` and `LoginPort`. Paths can be absolute or relative to the Hub.
It contains no database password. Keep this machine-specific profile outside Git.

PLAY opens the original client's existing launcher. Server Control starts the
original database with its existing configuration, or starts the three existing
`start.bat` scripts with the configured JDK. Existing matching server processes
are retained. Consoles remain available for normal administration. Server stop
buttons reuse the maintained graceful shutdown and finished-console cleanup;
database shutdown stays in its own console. Installer/update controls are hidden
in this mode, and the version is read from `ReleaseFile`.

18 October 2026 delivery: executable and profile at `C:/Git/aion-server`.
Normal compilation, built-profile path validation and copied hashes passed.
No components were launched/stopped by the agent; user button/launch acceptance
remains pending. Repacks and published releases were not replaced.
