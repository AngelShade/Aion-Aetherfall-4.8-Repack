# Aetherfall standalone compatibility port

This directory maintains the original Laravel/Filament website, adapted to the
native Java Aion 4.8 server. It is a separate website project, outside the repack.
The upstream README documents the original PTS configuration; use the standalone
README shipped by `tools/website` for Aetherfall.

Build through `tools/website/build_website.py`, using the locked Composer/Vite
dependencies and a verified PHP runtime. Outputs belong in a fresh external
workspace. The resulting standalone folder contains `Website.cmd`, `Website.ps1`,
`Website Tools.cmd`, `app.zip`, `runtime.zip`, `component.json`,
`native-website.sql` and `README.txt`.

Double-click `Website.cmd` to start the local site and open the browser when it
is ready. Repeated clicks reuse the running website. Maintenance actions remain
in `Website Tools.cmd`.

The website has independent database connection settings. No repack resources or
credentials are bundled. Its own CMS, uploads, plugins, secrets and sessions stay
in its `local/` folder and must never enter a shared archive.

The native adapter preserves all existing view contracts. Visual assets/scripts
and original style blocks are retained. SQL is additive and explicit; existing
native account hashes, character records and gameplay configuration stay native.
See `UPSTREAM.md` for the pinned donor, attribution and scope.
