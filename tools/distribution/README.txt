AION AETHERFALL — INSTALLER & AUTO-UPDATER HUB

HOW TO INSTALL (1 CLICK)
1. Double-click "Aetherfall Setup.exe".
   Always get the current complete downloader ZIP from:
   https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/latest/download/Aetherfall-Downloader.zip
   If you already have an older downloader, click "Update Repack" before
   INSTALL or RESUME to update Setup first. Keep your existing settings and
   Downloads folder to retain progress; replace only the old Setup executable
   with the current one if updating it manually while Setup is closed.
2. Choose your install folder (or keep the default) and click "INSTALL".
   - If you already downloaded "Aetherfall-0.86.7z" from Google Drive or MEGA,
     Aetherfall Setup.exe detects it automatically in your Downloads folder (or
     you can click "Use Local .7z..." to select it).
   - Otherwise, Auto / Direct HTTPS downloads "Aetherfall-0.86.7z" from MEGA
     with Google Drive fallback. BitTorrent is a separate selectable mode.
     Closing and reopening Setup preserves completed download chunks.
3. After verifying the SHA-256 checksum and extracting the repack, the Hub copies
   itself into your installed repack folder as "Aetherfall Hub.exe" and switches
   the main button to "PLAY".

HOW TO PLAY
1. Open the "SERVER CONTROL" tab inside Aetherfall Setup.exe (or Aetherfall Hub.exe)
   and click "1. Start Database & Servers". Wait for the status dots on the left
   to turn green ("Database ready" and "LoginServer online").
2. Switch back to the main tab and click "PLAY" (which opens Aetherfall Launcher.exe).
3. Log in with any username and password to create your account automatically.
4. When finished playing, close Aion and click "Stop Database" on the Server Control tab.

HOW TO UPDATE LATER (1 CLICK)
1. Open "Aetherfall Setup.exe" (or "Aetherfall Hub.exe" inside your repack folder)
   and click "Update Repack".
2. Setup updates itself first when needed. After it restarts, click Update Repack
   again. It checks installed files and downloads only required update chunks.
   Unchanged files and matching chunks of large files are reused. Verified
   downloads are cached for retry. No single download approaches GitHub's limit.
   Live progress shows download percentage, speed, remaining size, ETA and package
   counts. File checking, preparation, verification and installation have their
   own progress; completion details remain visible.
3. Your database, accounts, characters, companions, and settings are retained.
   Older installations can update directly. Existing offline patch ZIPs still work;
   new offline updates need the patch JSON manifest and its chunk ZIPs together.

OPEN SOURCE TOOLS
aria2 1.37.0: GNU GPL v2 or later; bundled original COPYING and OpenSSL notices.
https://github.com/aria2/aria2/releases/tag/release-1.37.0
https://github.com/aria2/aria2/archive/refs/tags/release-1.37.0.tar.gz
7-Zip 26.04: GNU LGPL; bundled original license/readme.
https://www.7-zip.org/ and https://github.com/ip7z/7zip/releases/tag/26.04
