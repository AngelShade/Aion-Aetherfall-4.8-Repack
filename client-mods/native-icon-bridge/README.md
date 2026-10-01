# Original client icons and Wardrobe preview

This optional bridge serves the existing HTML item-image requests directly from
the original client's `Data/Items/Items.pak`. It is specific to the verified
**Aion 4.8 NA x64** client and Awesomium DLL. It uses Awesomium's native resource
callback; the browser still displays PNG images, generated in memory from the
client DDS textures. It does not use the broker's UI drawing API.

No extracted artwork is installed. The generated index contains item IDs,
texture offsets and decoding metadata. The bridge verifies the original archive
hash at startup, validates decompressed size/CRC, and caches at most 512 unique
textures in memory. Items sharing a texture share a cache entry.

Only `http://127.0.0.1:<port>/shop/media/icons/<item-id>.png` and
`/market/media/icons/<item-id>.png` are intercepted. Missing icons and unmatched
requests continue through the previous callback and normal HTTP loading.
There are no item PNGs on the server. An unmatched/missing item image reaches
HTTP and returns 404; a matching native bridge is required for item images.
The market's item-ID catalog metadata is still required.
There are no server protocol or database changes.

Wardrobe also uses this bridge to embed the existing 4.8 character preview.
It temporarily moves the native character, zoom and Aethertech views into the
Wardrobe dialog, retaining the publisher's model controller and camera. Dialog
hide and destruction hooks restore their ownership and layout before the stock
preview is reused or freed. Wardrobe callbacks are accepted only from its local
page. Browser callbacks copy their validated arguments into a bounded queue;
the native browser event pump at `Game.dll+0x131ef0` updates the model on the
game thread. `WardrobePoll` returns completion on the browser thread, with a
request number so a late result cannot overwrite a newer preview. Destroying
a browser cancels its pending commands. Other browser callbacks are forwarded.
The native preview receives left-drag rotation, right-drag positioning and wheel
zoom through the client's window-message chain. Only input over the visible
preview is consumed; lists, confirmations and other windows retain their input.
Camera changes are applied by the game event pump and restored when Wardrobe
closes. Reset Preview also restores the initial camera. Camera baselines are
taken after the stock equipment/model update, because an unopened native view
has a zero camera position. Position changes rebuild the native view and
frustum data. Try On retains the camera controls; a missing entity or invalid
camera triggers a bounded retry through the stock model/framing routines.

## Prepare and install

Use the combined `../transmog-menu/build_package.py` builder with
`"nativeIcons": true` in `../transmog-menu/menus.json`. Existing Inventory,
warehouse and private-menu settings are included in the same build.

Requirements: Python 3.12, the existing archive codec directory, Java for the
existing archive signer, and Visual Studio 2022 C++ Build Tools with the Windows
SDK. `build_bridge.py` uses `/MT` so the bridge needs no separate MSVC runtime
installation. The client must have its verified `bin64/game.dll.orig`.

```powershell
python client-mods/transmog-menu/build_package.py `
  --codec-directory '<existing codec directory>' `
  --client-path '<Aion 4.8 NA root>' `
  --java '<java.exe>' `
  --output '<new stage outside client>'

# Close all Aion clients, then install the entire combined package.
& client-mods/transmog-menu/Install.ps1 `
  -ClientPath '<Aion 4.8 NA root>' -PreparedPath '<stage>'
```

The builder creates `bin64/AionIconBridge.dll`, `bin64/AionIconBridge.index`,
and a Game.dll import/initialization hook that runs before Lua queues browser
creation. The installer checks both original inputs and staged hashes, and backs
up the current client files. Compiler intermediates stay outside the payload.
Do not copy only the Game.dll; it requires its matching bridge DLL.

Restore with the existing `../transmog-menu/Restore.ps1`, using the backup path
printed by the installer. It restores the previous Game.dll and removes bridge
files that were newly added by that installation.

## Verify

The isolated test uses the client's actual Awesomium browser. Prepare a disposable
root containing `bin64/AionIconBridge.dll`, `bin64/AionIconBridge.index`, and an
unchanged copy of `Data/Items/Items.pak`, then run:

```powershell
python client-mods/native-icon-bridge/verify_bridge.py `
  --root '<disposable root>' --browser-bin '<original client root>/bin64'
```

It requires Pillow. It decodes every indexed texture, checks both shop routes
without server image downloads, verifies missing-image fallback, and tests
callback replacement plus view creation/destruction.
It also checks Wardrobe callback registration order and forwarding of the stock
ItemPreview callback. `verify_wardrobe_patch.py` executes nine lifecycle hook
cases, including argument preservation, destructor vtable relocation and
rejection of an unsupported client. Actual model drawing still needs an in-game
check after installing the complete package.

`verify_wardrobe_browser.py --browser-bin <client>/bin64` checks confirmations,
tabs, the unlock request and preview control sizing in the actual legacy WebKit.
Wardrobe toggles the `hidden` attribute directly and uses WebKit sizing/transform
prefixes; this browser does not implement `HTMLElement.hidden`.
`verify_wardrobe_native.cpp` is an isolated native widget contract fixture. It
checks the observed Browser type `0x2012`, model reparenting and restoration,
multiple appearances, modal visibility, and thread/bounds guards. Compile with
MSVC `/std:c++17 /EHsc`, link `user32.lib`, and run the resulting executable
outside the client with the matching original `bin64/game.dll.orig` path as its
argument. It maps the original rotation and camera/frustum CPU routines into the
fixture, without loading the game DLL or accessing a running client. Equipment
loading and entity framing are fixture adapters; actual model rendering and
input delivery still require an in-game check. The test covers Win32 drag and
wheel events, yaw wrapping, camera-plane changes, Try On and invalid-camera
recovery. The first rotation, positioning and wheel input received by the live
game thread are recorded in `Logs/NativeIcons.<process-id>.log`.

For the installed client, restart Aion and open both Cash Shop and Central
Market. Check icon appearance, search, paging and native hover tooltips.
`Logs/NativeIcons.<process-id>.log` records initialization, browser attachment,
and the first successful native image for each shop. These lines confirm the
actual game browser used the bridge; isolated tests alone do not prove that.
Inventory and both warehouse grids should retain their previous slots and
Search/Clear controls. This bridge does not expand storage.

## Sharing

Share these source files with the existing combined client patch source and
instructions. Recipients build the index from their own matching original
client. Do not distribute the client archive or extracted artwork. A changed
client/Awesomium build needs fresh native hook verification before enabling the
bridge; unsupported builds need a verified bridge port before item images work.

The server URLs use `v=native-3` to invalidate earlier padded image caches.
Legacy DDS textures use the native client's top-left 40x40 sprite rectangle;
the `_64` variants use 64x64. Unused pixels can be opaque gray, so alpha bounds
alone are insufficient. Index version `AICON002` records the sprite rectangle.
The bridge clips that rectangle before removing transparent margins and
resizing in memory. Install the matching DLL and index together.
The isolated test compares Fresh Umblia and all ten legacy padding exceptions
with independently decoded 40x40 client artwork.

A complete original-texture audit found ten legacy padding exceptions shared
by 43 item IDs. The browser regression checks Fresh Umblia and Cash Shop item
IDs 164000074, 164000075 and 164000076 through their actual native routes,
with zero corresponding HTTP image downloads. The sprite correction is shared
by both shops and applies to every alias of each affected texture.
