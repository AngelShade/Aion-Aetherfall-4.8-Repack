# Companion outfit transmog — PB-CUSTOM-APPEARANCE-001

## Integration boundary correction - 8 October 2026

PB-REPAIR-PACKAGING-001 removes the uninstalled appearance tick hook from main
PlayerBotSession source and deployed code. An unrelated whole-method transplant
had imported it without the appearance helper, aborting every bot tick. The
appearance implementation and its other source-only hooks remain preserved.
Before installing this unfinished feature, restore its tick integration as part
of the complete reviewed feature package and pass the shared runtime linkage gate.
Historical staged appearance packages are not the current installation baseline.
See [root-cause record](PLAYERBOTS_LINKAGE_REPAIR_20261008.md).


The Companion Equipment tab now has a separate **Use as transmog** action for
permanent, compatible appearance items held in that companion's cube. The selected
appearance is applied to equipped combat gear, so its stats, enchantments, stones,
and item identity stay on the combat item. The appearance source remains in the
cube. **Restore original look** removes the saved selection for that slot.

Appearance selections are account/character-checked in
`config/playerbots/appearance-character-<character-id>.properties`. The normal
native appearance packet is broadcast after application, and the session reapplies
the saved look if gear in that slot changes. Temporary/expired, incompatible, or
race/gender-restricted sources are refused. Costume pieces are excluded from
automatic equipment upgrades only when they have no template stat modifiers;
stat-bearing CLOTHES/ALL_ARMOR equipment remains eligible. Manual controls remain.

The implementation reuses `WardrobeRules` and Aion's item-skin, inventory-update,
and `SM_UPDATE_PLAYER_APPEARANCE` behavior. This custom extension does not claim
that upstream Playerbots `OutfitAction.cpp` is ported.

The source compiles and the cumulative incremental package is staged at
`target/playerbots-appearance/package-reviewed-20261007`. It supersedes package-v2,
which had incorrect item persistence and overbroad costume filtering and must not
be installed. Its parent baseline is the current `042546-945113` cumulative
Playerbots receipt, which retains gifts `005644-110252` and native shield `025825`.
The staged method list is in that package's
`methods.tsv`; it changes only the companion snapshot/tick/close methods, gear
eligibility and authenticated panel action, and adds `PlayerBotAppearance`.
The deployed launcher order, base JAR and client files are preserved in the
staging manifest. Installed-mod inventory was refreshed on 6 October: 16 checks,
31 current client hashes, 70 client receipts and 84 server receipts pass.

**Deployment status: source/staged only.** Per the user's no-live instruction, this
package was not attached to a running process or copied into the deployed server.
Native model appearance, panel use, and selection persistence after restart remain
unverified in-game. The next broad upstream slice remains PB-PORT-005B.

## Diff review correction — 7 October 2026

The original implementation was not fully correct. Skin application/reset called
`setPersistentState(UPDATED)` after the native setter. `InventoryDAO` saves NEW,
UPDATE_REQUIRED and DELETED items, so this suppressed skin updates and could
discard the pending insert for newly generated gear. Both paths now use only
`Item.setItemSkinTemplate`, preserving NEW and marking stored items UPDATE_REQUIRED.

The filter also treated every wardrobe Costumes category item as appearance-only.
The actual template 110000018 Forest Denku's Frillycoat is CL_TORSO but has 234
physical defense and other stats. Only costumes with no nonzero template modifiers
are excluded now, and stat-bearing clothing can receive transmog. The panel and
action share target eligibility, restore is shown only for supported gear, and
instance-expiring source items are rejected alongside temporary templates.

Full offline Maven reactor compilation passed for Commons and 2,410 GameServer
source files. Sixteen focused native item checks pass, covering persistence states,
NEW inserts, original-item/enchantment/slot preservation, restore, compatibility,
stat-bearing clothing, and expiring/equipped sources. The reviewed package's
`verification.json` records unchanged earlier entries and selected-method checks;
the same item checks run against its effective JAR. No live cast, movement, packet,
database, client replacement, deployment, or server lifecycle operation occurred.
GameServer and the game remain off per the user's current instruction; the user
performs gameplay acceptance after each port.

7 October continuity: gear save retry PB-REPAIR-SETTINGS-002 is now installed
offline in `081522-429924`; the care retry is retained. Existing uninstalled
packages must be restaged against that baseline. See
`PLAYERBOTS_SETTINGS_FILES_20261007.md`; no server/client startup or attach.
