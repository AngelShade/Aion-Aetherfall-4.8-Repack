AETHERFALL STANDALONE WEBSITE

This is a separate companion website for the Aion Aetherfall 4.8 NA repack.
It connects to your chosen native Java/MariaDB game installation. Keep this folder anywhere outside the game/repack installation.
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
FAQs and artwork. Content updates refresh recognized, unedited shipped entries
once per content revision; owner-edited articles, downloads and settings are kept.
The Repack Features page describes the features included in the published repack. Player guides
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
Use your game installation’s actual native databases. Repack installations commonly use
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
Repack Tools & Codex adds an eleven-class explorer/comparison, a six-person party
planner with device-local saving and share links, a searchable native instance
atlas and a copyable companion-command reference. Open Player Hub -> Repack Tools
or /tools. The CMS Repack Tools & Codex module controls all five routes and links.
Party plans never recruit or modify characters. Atlas facts come from the included
repack data, refreshed during the normal website build; owner-customised entry
rules may differ. Commands are checked against maintained native help at build time.

Player Hub adds saved-game AP, GP, PvP-kill and level rankings, character lookup,
legion profiles, server status and a browser-local installation checklist. Generated
bots, deleted characters, inactive accounts and staff are omitted from public rosters.
No account identifiers, character locations or inventories are published. The native
game databases are read only by these features. Modules can be disabled in the CMS.
News supports category/search filters and an RSS feed at /feed.xml.

The weekly siege timetable is copied from maintained native source during packaging.
It is labelled as the Aetherpack default. To show your customised schedule instead,
set AION_SIEGE_SCHEDULE to the absolute path of your server's siege_schedule.xml in
this website's local/settings.env. Only fixed-time, named-weekday Quartz entries
are displayed; unsupported rules are reported. Set AION_SERVER_TIMEZONE to your
game server's IANA timezone (e.g. Europe/Bucharest) for next-start dates. Leave it
blank when unknown. These settings never change the game server's own schedule.

Pinned commit: d0dde5dd939f728f0b2c53e765f25188b15afc5c
The owner confirmed permission from the author to adapt and redistribute it.
