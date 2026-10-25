AETHERFALL STANDALONE WEBSITE

This is a separate website application for the native Java/MariaDB Aetherfall
server. Keep this folder anywhere outside the game/repack installation.
Nothing is installed in the repack and no game component is started by it.
PHP and the built application dependencies are included.

Double-click Website.cmd to start the website and open it in your default
browser automatically. There is no menu. If it is already running, another
double-click opens the existing site without starting another server. Keep the
website console open; Ctrl+C stops it.

Double-click Admin.cmd to open the control panel on this computer. On a new
website it creates a separate Website Owner administrator and opens a single-use
local sign-in link that expires after five minutes. No default password is shipped.
Existing website administrators can still use their email and password at /admin.
This never changes game accounts or their access levels.

The first start also adds Aetherfall news, player guides, official download links,
FAQs and artwork. Existing custom articles and settings are kept. Player guides
are edited in News using the Guide category. Rates and character rules are in
Website Settings. The screenshot gallery uses actual client captures with display
crops; opening one shows the original image.

Use Website Tools.cmd for maintenance:
1. Open website (initializes its own CMS if needed)
2. Create/reset a separate website administrator
3. Edit website settings and its game-database connection
4. Apply the additive native website integration to configured game databases

Configure the connection first using option 3. Set AION_DB_HOST, AION_DB_PORT,
AION_DB_USERNAME, AION_DB_PASSWORD, AION_ACCOUNT_DATABASE and AION_WORLD_DATABASE.
Use this server's actual native databases. Repack installations commonly use
port 33306, aetherfall_ls and aetherfall_gs. Original installations may differ.
No repack folder, credentials.json or REPACK.json is required by the website.
Quote credentials correctly as Laravel environment values; never share this file.

Start the selected game database yourself. Option 4 verifies the native schema
and applies native-website.sql without replacing game tables or existing data.
It adds website profile metadata and records actual character login/logout and
playtime transitions. Earlier activity history cannot be reconstructed.
The website's own CMS data is a separate SQLite database in this folder.

Create a website administrator using Tools option 2, then double-click Website.cmd.
Open http://127.0.0.1:8090. The administration panel is at /admin.
Keep the website console open; Ctrl+C closes only the website.
The website binds only to this computer. Public hosting is a separate setup.

The original Zyvelle44 presentation, artwork, responsive layouts, news and hero
slide editors, downloads, support FAQs, donation links, website settings,
administrator roles, feature switches, plugin installer/hooks and optional
Gemini news assistant are retained. Native adapters cover game registration,
login, password changes, characters, classes, levels, legions, bans and statistics.
Existing native game accounts can sign in after connection and initialization.
Donation links/balances keep their original website purpose; automatic payments
or in-game currency conversion are not added. Set GEMINI_API_KEY in Settings if
using that optional assistant. External PTS-specific plugins need native adapters.

Preserve local/: it contains the website database, settings, generated key,
uploads, installed plugins, backups and sessions. It is never included in a
redistributable build. Updates replace only the archives and launcher; existing
state remains separate and old extracted application versions remain recoverable.

The portable runtime caches PHP code and checks for changed files on every
request. Images, styles and fonts load directly and can reuse browser-cached
bytes. Replacing an image at the same URL is detected through its content hash.
The original Cinzel and Inter fonts are included for offline use. A first page
after starting the website may take longer while PHP fills its code cache.

Original project: https://github.com/Zyvelle44/aion-pts-website
Pinned commit: d0dde5dd939f728f0b2c53e765f25188b15afc5c
The owner confirmed permission from the author to adapt and redistribute it.
