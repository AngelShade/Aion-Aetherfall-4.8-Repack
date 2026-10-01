# Aion 4.8 global DXVK graphics profile

DXVK 3.1.1 replaces the client's Direct3D 9 rendering backend with Vulkan. Both
`bin32` and `bin64` receive the matching official `d3d9.dll`. The profile applies
to the entire game, including every zone, character and effect rendered through
this backend. It does not replace models or textures with new artwork.

## Quality profile

| Setting | Value | Purpose |
| --- | --- | --- |
| Texture anisotropy | 16x | Improve texture clarity at oblique angles |
| Native High Quality renderer | Enabled | Keep Aion's higher quality rendering path |
| Native shadow quality | 4 | Highest step exposed by the client's HQ shadow slider |
| Native ambient occlusion | Enabled | Add contact shading where the native renderer supports it |
| Native AO resolution option | 1 | Enable the native renderer's AO resolution option |
| Native sharpening | 0.12 | Subtle sharpening without a strong halo |
| Native UI cursor rendering | `r_3dvision_cursor = 1` plus the native cursor patch | Draw Aion's cursor through its UI path independently of stereo depth |
| Deferred surface creation | Enabled | Aion compatibility recommendation |
| Sampler type specialization | Enabled | Aion shader compatibility recommendation |
| Exclusive fullscreen | Disabled in DXVK | Retain reliable borderless switching and cursor behavior |

MSAA, vertical sync and FPS controls keep their existing defaults/game choices.
The obsolete `d3d9.cachedDynamicBuffers` setting is absent in this DXVK release
and is intentionally omitted.

## Installation and normal use

The installer verifies pinned hashes for the payload and the existing Beyond
Aion 1.5.1 compatibility DLLs. It saves replaced files and the launcher in
`DXVK-backups/<timestamp>` inside the client. Use `Aion Start.bat` normally;
it selects this config, writes DXVK logs to `DXVK/logs`, and applies the global
quality profile before starting the game. Native settings are applied with the
client closed, using its existing encoded config format. Only managed graphics
keys are changed.

The launcher also prefixes the Windows PowerShell module folder to its local
module path before setup scripts run. This fixes `Get-FileHash` discovery when
the launcher inherits a PowerShell 7 module path from a development shell.

To install a prepared payload:

```powershell
.\Install.ps1 -ClientPath 'C:\path\to\Aion 4.8 NA'
```

The binaries are generated from the exact official archive documented in
`manifest.json`. Ignored `payload/bin32` and `payload/bin64` folders must be
populated with their matching official `d3d9.dll` before installation.

## Verify and restore

With Aion open, run the installed `DXVK/VerifyActive.ps1`. It checks installed
hashes and the actual process's loaded `d3d9.dll`; it prints backend, device and
profile details from the game log. Process inspection may require elevation.

To restore, exit Aion normally and run the installed `DXVK/Restore.ps1`.
It restores/removes the three renderer payload files, removes its launcher
block and restores the original managed graphics options. Other options edited
later and existing launcher modifications are retained. The restore script
checks installed and backup hashes before replacing anything.

## Verification

Both x86 and x64 native Windows GPU smoke tests passed on the RTX 4060 with
driver 610.62 and Vulkan 1.4.341. Each created a DXVK device, drew a triangle,
verified exact GPU readback pixels and presented successfully. Logs confirmed
DXVK 3.1.1, the intended GPU, 16x filtering and sampler specialization.

`verify_install.ps1` passed a synthetic install/restore check covering a running
client, encoded settings, repeated application, and retention of later option
and launcher edits. These checks do not measure in-game FPS or prove visual
quality in every location. Actual game activation is checked separately through
`VerifyActive.ps1`.

On 2026-10-01 the actual 64-bit Aion client loaded the installed wrapper and
created a 1920x1080 Vulkan swapchain on the RTX 4060. The client was responsive,
its DXVK log confirmed version 3.1.1 and the selected profile, and no `err:`
entries were present. One `Unhandled render state 26` warning was recorded.
Native graphics keys persisted after startup and compatibility patch hashes
remained unchanged. No world tour, FPS comparison or screenshot comparison was
performed for this renderer installation.

### Cursor compatibility correction

The user reported a missing in-game cursor after DXVK installation. The global
Config-only attempts failed because Aion resets the depth mode to -1 during
initialization. `InstallNativeCursorPatch.ps1` therefore changes only two
two-byte conditional branches in the exact verified 64-bit `XRenderD3D9.dll`
and current custom `Game.dll`. The cursor drawing and D3D9 cursor visibility
functions can then use cursor mode 1 independently of stereo depth. Cursor
object, cursor mode and ordinary visibility checks remain in place; the depth
setting keeps its original behavior. All other existing client patches and
artwork are retained byte for byte.

The installer checks both complete input file hashes and original instruction
bytes, rejects pending `game.dll.patched` updates, backs up both native files
and helpers, and records post-install hashes. `Restore.ps1` verifies and restores
these exact originals along with the renderer. A synthetic test confirmed that
only four instruction bytes changed and that restoration rejects later native
file edits before changing anything. This patch targets the current 64-bit
client; it does not patch the 32-bit client's native binaries.

`VerifyCursor.ps1 -ClientPath <client>` reads the running native renderer without
modifying its memory, verifies cursor mode 1 and confirms both patched branches
are loaded. Cursor visibility and camera movement still require a player check.

On 2026-10-01 the running-client verifier confirmed cursor mode 1 and both native
instruction patches active in process 2408. The player confirmed the cursor
works. The helper remains under `DXVK/cursor-fix`; subsequent normal launches
verify the installed patch before applying the quality profile.

## Sources

- [Official DXVK 3.1.1 release](https://github.com/doitsujin/dxvk/releases/tag/v3.1.1)
- [DXVK 3.1.1 configuration](https://github.com/doitsujin/dxvk/blob/v3.1.1/dxvk.conf)
- [DXVK driver requirements](https://github.com/doitsujin/dxvk/wiki/Driver-support)
- [Beyond Aion client compatibility patch and DXVK guidance](https://github.com/beyond-aion/aion-version-dll)
## In-game renderer selection

The installed 64-bit client now has a **Use Vulkan (requires restart)**
checkbox under **Options → Graphics → Screen**. Press Apply or OK, exit
normally and reopen Aion Start.bat to use the selected backend. Vulkan off
uses Windows Direct3D 9 while retaining upgraded textures, native lighting
and the repaired cursor. Vulkan ReShade effects are inactive in that mode.
See [GRAPHICS_MENU.md](GRAPHICS_MENU.md) for implementation, validation and
restoration details. The native helper is loaded in the actual game;
visible checkbox behavior and game launches with the toggle off/on still
require live confirmation.

