# Webpage flash investigation

Current installed state: diagnostic removed at the user's request on October 3,
2026. The exact pre-diagnostic Game.dll and graphics tracking were restored;
`AionBrowserProbe.dll` is absent. The graphics-menu launcher verification passes.
Historical logs and recovery backups are retained. No diagnostic is active.

The user sees the webpage disappear momentarily in Season Pass, Central Market
and Cash Shop. The native title bar and close button remain visible. This is a
shared browser content problem. The cause is not confirmed and no flash fix has
been installed.

The passive diagnostic was installed on October 3, 2026 after normal client
closure. The exact prior Game.dll is backed up under the client's
`BrowserFrame-backups/20261003-101358-680502` directory. The installer verified
the staged DLL hashes and preservation of every other installed DLL. A fresh
in-game reproduction is still required; isolated verification cannot confirm
the client's graphics draw path.

The graphics-menu launcher originally refused the new Game.dll hash. Its tracking
records and packaged Game.dll were rebased to the verified diagnostic; the active
Game.dll was not changed again. Diagnostic-aware graphics/cursor restore baselines
were generated so future graphics removal preserves the diagnostic and unrelated
menu patches. Removal was tested only against disposable copies. No graphics
features were removed from the installed client. The launcher still rejects
unknown later changes; its live `-VerifyOnly` check now passes. Tracking backup:
`BrowserFrame-backups/graphics-tracking-20261003-102108-928277`.

The installer fixture additionally tests installing and restoring graphics
metadata and newly created baseline files together with the diagnostic. A fresh
live log showed opaque sampled buffers and render caller RVA `0x138fdb`; that
observation does not establish whether a reported flash occurred during sampling.

The isolated publisher Awesomium browser passed a 36-second transparent-surface
test covering automatic Season Pass refresh: 4,240 buffers, no null buffers, no
fully transparent sampled frames, minimum sampled alpha 253. This test does not
exercise Aion's graphics texture upload or native drawing. Its receipt is
`output/season-pass/browser-alpha-refresh.json`.

The staged diagnostic hooks the exact stock render, copy and resize prologues,
calling each original once with unchanged arguments/results. It records buffer
counts, nine alpha samples, sizes, resize counts, timing and Game-relative call
addresses. It does not record URLs, page text, account data or pixel contents.
File writes occur at most every ten seconds on the native UI tick, for up to
30 minutes. Surface tracking is bounded to 32 records. Zero-alpha navigation
frames are observations, not automatically defective frames.

The existing installed Game.dll is the input. Only headers and the first five
bytes of its two existing initialization/tick gate jumps change. The new gates
preserve registers/stack and chain to the existing gates. All prior imports,
client patches, native icons, Wardrobe hooks, menu packages and signing stay
intact. AionBrowserProbe.dll is an additional dependency. Awesomium.dll and
AionIconBridge.dll are not replaced. This is diagnostic instrumentation, not a
rendering change or an engine replacement.

Stage and verify:

```powershell
python client-mods/browser-flash/stage.py --client '<Aion root>' --output output/browser-flash
python client-mods/browser-flash/verify.py --staged output/browser-flash --browser-bin '<Aion root>/bin64'
```

Installation requires normal client closure. The installer refuses a running
Aion, validates the original Game.dll hash, backs it up and verifies preservation
of other DLLs. After a fresh launch, use the affected windows and inspect
`<Aion root>/Logs/BrowserFrames.<PID>.log` together with the observed flash.
The diagnostic cannot see a graphics driver discarding a texture after a valid
copy; if buffers stay valid, continue tracing the native draw/upload boundary.

```powershell
python client-mods/browser-flash/install.py --staged output/browser-flash
python client-mods/browser-flash/install.py --staged output/browser-flash --restore '<backup directory>'
```
