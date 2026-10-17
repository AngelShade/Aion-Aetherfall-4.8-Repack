AION AETHERFALL DOWNLOADER

Double-click Start-Download.cmd. Downloads resume when you start it again.
The included aria2 engine downloads the archive parts, and 7-Zip extracts them
after every part passes its recorded SHA-256 check. The torrent also has native
piece verification. The game, database and servers are never started for you.

settings.json controls the download/install folders and Auto/Torrent/Http mode.
Relative folders are beside this downloader; absolute folders are also accepted.
Allow room for both archive parts and the extracted repack. Existing installed
folders are not overwritten. Interrupted extraction can be restarted.

Torrent mode needs an online seed. It uploads pieces while downloading, limited
to 1 MiB/s by default, and stops seeding when the download finishes. For continued
seeding use the owner's Start-Seeding.cmd or your preferred torrent application.
HTTPS webseeds can supply the same torrent data alongside peers. Direct per-file
mirrors are a separate HTTP mode, with up to 16 connections per server.

mirrors.json starts empty until the owner uploads the files. A public HTTPS
MirrorsUrl in settings.json lets the owner replace URLs without changing the
release's file names, lengths or hashes. Each release keeps its own manifest.
Google Drive / MEGA browser share pages are manual mirrors, not HTTP webseeds.
MEGA requires its own client/decryption; aria2 cannot download MEGA share pages.
If you downloaded all parts manually, place them in Downloads/<torrentFolder>
matching release-manifest.json and use the downloader or 7-Zip to extract .001.

Open the extracted README.txt before first starting the database or game.

OPEN SOURCE TOOLS
aria2 1.37.0: GNU GPL v2 or later; bundled original COPYING and OpenSSL notices.
Official binaries and corresponding source:
https://github.com/aria2/aria2/releases/tag/release-1.37.0
https://github.com/aria2/aria2/archive/refs/tags/release-1.37.0.tar.gz
7-Zip 26.04: GNU LGPL; bundled original license/readme.
https://www.7-zip.org/ and https://github.com/ip7z/7zip/releases/tag/26.04
Tool licenses and source availability terms remain applicable when distributing.
