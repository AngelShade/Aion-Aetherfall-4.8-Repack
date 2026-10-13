# Player Companions and Choose Your Journey HUD shortcuts

Two native34x32 buttons flank the existing Aetherfall Season Pass ticket above
Central Market/Shop. Player Companions is on the left; Choose Your Journey on the
right. They dispatch the existing `/companions` and `/journey` commands into
their current authenticated in-game windows. Additional Functions entries remain
and use the matching artwork; command handlers and journey eligibility stay native.

The design follows the user's earlier “Add special speech bubbles” conversation:
visible black contours,transparent background,English localization keys instead
of `???`,and normal/hover/pressed states. See ARTWORK.md for built-in imagegen
prompts and retained originals. Assets are companions-outlined.png and
journey-outlined.png under assets; production atlas conversion retains originals.

Maintained C++ in ../market-shortcut/market_shortcut.cpp builds the existing
AionMarketShortcut.dll through the native compiler. No Game.dll edit or new hook
is required. Existing Market/Shop/Season Pass dispatch and unknown-click
passthrough stay. Both HUD styles,four UI scales,all three Shop variants,missing
controls and idempotent layouts are executed in isolated native fixtures.

prepare.py stages from current installed archives into a new external directory.
It preserves unrelated skins/widgets/strings/archive entries and pet/party-bar
content,patches base and active English resources,and signs all three addon
packages together with an ephemeral Addon key. Pub.key remains unchanged. Active
graphics locale rollback metadata and its baseline are composed; other graphics,
cursor,login/browser hooks,Game.dll,Items and native icon index remain unchanged.

verify_package.py binds original and staged bytes,source/native proofs,the six
new states,tooltip identities,all unrelated archive entries and real signatures.
install.ps1 requires Aion closed,checks paths/current/source hashes and all
preserved mods,backs up every replacement externally,then copies and verifies.
Failure during installation rolls back. Recovery receipts are under external
archives/client/service-shortcuts-* and appear in installed-mod inventory.

The package never starts/stops/attaches to Aion or GameServer. Aion restart,
appearance and real hover/click/window acceptance remain user testing. Server
configuration/JARs/database/bot progress are outside this client-only feature.
