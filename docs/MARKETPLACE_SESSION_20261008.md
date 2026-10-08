# Marketplace session route repair - 8 October 2026

Status: installed from maintained client builder and offline verified while
Aion/GameServer were closed. Actual in-game acceptance remains pending.

The installed browser bridge at `0x144d800` recognizes the exact `/shop` URL but
navigates directly without `session_id`. The other private windows call the
native token callback, which appends the first 16 native token bytes in hexadecimal.
`MarketplaceService.findPlayer` already accepts this representation. The shop's
loopback fallback expects exactly one world player, so it cannot identify the
human character once companions also occupy the world. The reported login
message is consistent with these verified installed code paths.

The maintained `client-mods/transmog-menu/patch_game_dll.py` builder now supports
authenticated Shop navigation. Its legacy helper defaults remain available for
receipt verification. New complete client builds include Shop authentication;
the bounded `prepare_marketplace_session.py` builder applies it over the current
cumulative client only when both old browser caves match exactly. A compact
suffix table preserves exact origin/route/NUL checks for all six private services
and fits in the existing 512-byte authentication cave (400 bytes). The browser
cave remains 668 bytes. No native hooks outside these two caves change.

Server authentication, catalog, Kinah balances and purchases are unchanged. No
server JAR replacement, class transplant, attach, component startup or gameplay
fixture execution is involved. All production output comes from maintained
client builders without subsequent compiled-code rewriting.

External package:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/marketplace-session-20261008-closed`.
The builder also stages the active graphics package/state, cursor tracking, and
both rebased restore baselines through `graphics_compat.prepare_incremental`.
The shared hash-guarded installer recognizes `marketplace-session-v1`, requires
Aion closed, backs up to external `archives/client/marketplace-session-*`, and
supports guarded rollback. All existing DLL imports, keys, signatures, archive
entries, menu actions and launcher files remain preserved.

Validation:

- Fresh installed inventory: 20 checks, 31 current client hashes; 73 client and
  106 server historical receipts. Both installed cave fingerprints matched.
- Complete six-route builder succeeds against the verified original DLL.
- 104 offline Capstone instruction interpretation checks cover browser entry and
  token callback paths, exact routes, three token patterns, missing tokens,
  foreign/short/query URLs, and invalid browser view. No native code is executed.
- Shared installer verify-only passes current, staged and preserved hashes.
- Disposable external fixture passes repeated graphics launcher verification,
  unexpected-DLL rejection, byte-exact graphics restore, cursor tracking, and
  all six routes in both restore baselines. Production client untouched.

Read-only elevated process inspection initially found GameServer and `aion.bin`
running. A later inspection confirmed both were closed. The first guarded install
refused before writing because Aion had saved `system.cfg` during shutdown; a
fresh inventory/build preserved the updated setting file. The successful guarded
transaction receipt is
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/client/marketplace-session-20261008-205654-357237`.
Post-install inventory passes 20 checks/31 client hashes and records 74 client and
106 server historical receipts; `marketplaceTokenRouteInstalled` is true.
Rollback verify-only and the installed graphics/cursor launcher preparation
scripts pass. The closed-client package's disposable restore fixture also passes.
Server JARs/configuration/data and unrelated client settings/resources were
preserved. Neither component was started/restarted or attached to.

User acceptance: log in with companions present and open Black Cloud Marketplace
from each Shop shortcut. Confirm the character's name/balance and normal browsing,
then verify Central Market, Wardrobe, Journey, Season Pass and Companions still
open. Native pet and Additional Functions acceptance remains pending.
