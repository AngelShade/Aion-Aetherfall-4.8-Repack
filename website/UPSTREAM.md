# Aetherfall compatibility port

Based on https://github.com/Zyvelle44/aion-pts-website at
`d0dde5dd939f728f0b2c53e765f25188b15afc5c` (19 October 2026).
The owner confirmed permission from the author to modify and redistribute this
project on 24 October 2026. Original attribution and notices are retained.

This is the original Laravel/Filament application with its original presentation,
CMS resources, plugins, permissions and optional Gemini integration. The native
adapter supplies its existing view contracts from Aetherfall's MariaDB schemas.
SQL Server configuration and the upstream implementation remain available when
`AION_BACKEND` is not `aetherfall`.

CMS data stays in a separate, installation-specific SQLite database. Native
accounts/passwords stay in `account_data`. Additive, idempotent SQL migrations
create native website profile metadata and character activity tracking; they
never rebuild game tables or import player data. Playtime and exact login/logout
history start when tracking is installed; earlier history is not fabricated.

Donation options retain the upstream configurable payment links. Donation Coins
are a website balance, as upstream, and are not an automatic payment gateway or
an in-game currency conversion. Gemini still needs the owner's API key. The
plugin installer, hooks, routes, migrations, registry and admin resources remain
available; external plugin packages that contain their own PTS queries also need
native adapters and are not bundled by upstream.

Portable runtime and dependency outputs are built externally. Never distribute
`.env`, live CMS databases, uploaded/private files, logs, sessions or generated keys.

Standalone delivery: keep the website outside the repack. Its own `local/` holds
all state and connection settings. No repack layout or credential files are
required. Native SQL is applied only by the explicit Initialize action to the
configured databases.

The native mappings were checked against this core's AccountUtils password
encoder, PlayerExperienceTable and experience XML, PlayerClass enum, account
penalty/expiration handling, player punishments and world-map data. Connections
use UTC for native timestamps. An unavailable game database is reported as
unavailable; it does not erase the account identity or pretend there are zero
connected players.

The compatibility audit also repaired inherited CMS behavior: rich-text alignment,
image sizing and table spans survive publication; plugin changes invalidate the
active navigation/view caches; footer hooks are validated before activation;
feature dependencies are checked transitively, including cycles; and the legacy
administrator-reset command invokes the working administrator workflow. Original
artwork, CSS, JavaScript and Blade style blocks are retained. Starter branding uses
Aetherfall, with the game version on the server-information page. Customized owner
settings and slides are preserved.

The public repository names no paid-only feature set. Its Discord invitation is
not a specification for additional modules. This port includes the public feature
set; separately supplied extensions require their own compatibility review.

Aetherfall additions include first-run content, searchable player guides and a
gallery of actual client screenshots. A new hero illustration is separate from
the original upstream art. The local Admin.cmd launcher can open the control panel
without a shipped default password: CLI-created links are hashed, expire after
five minutes, are consumed once, and are accepted only on the standalone loopback
connection. Normal email/password login and roles remain available.

The portable runtime enables OPcache with timestamp validation, so plugin/code
updates are checked on every request. Shared view data is scoped to the request
and invalidated by settings, feature and plugin saves. Public images/styles/fonts
can be delivered before Laravel boots, with content ETags and modification dates;
same-URL replacements are revalidated rather than hidden by a long cache lifetime.
Private storage and normal authenticated pages keep their existing boundaries.
Cinzel and Inter are served locally with their original font licenses. The portal
stylesheet is linked separately, preserving the upstream inline design rules.

Website branding uses unchanged assets from the maintained Aetherfall launcher:
the ice-silver wordmark, winged Atreia banner, ornamental frame and crest icon.
The inline SVG wordmark uses the launcher's exact crop (0,146,2048,460), keeping
the original lettering and transparency. Public custom logo/favicon settings
still override their defaults. Motion uses transforms/opacity, respects reduced
motion, pauses in hidden tabs and provides a hero pause control. Content remains
visible without JavaScript; the visual script has no idle frame loop.
Each standalone installation also has its own Windows OPcache identity and a
private file-cache fallback, avoiding shared-memory/ASLR startup conflicts when
another PHP website runs under the same Windows user.
