# Custom window opening delay: investigation, 7 October 2026

Status: native queue batching repair installed and offline verified.
Actual opening-time acceptance pending; nothing published to GitHub.

The user initially chose to keep both programs off and leave the capture
prepared. They later said "Im opening aion now you can start", authorizing the
read-only reproduction. The user started Aion and the servers; Codex did not
start, restart, suspend, inject into, or modify either running program.

The user initially described every custom opening as delayed. During the live
reproduction they clarified: the first opening after login takes approximately
30 seconds, and subsequent openings work normally. The same delay reproduced
after returning to character selection and entering the character again. Stock
Aion windows open normally. The earlier Journey authentication change did not
resolve it; this is shared by custom menus and is not Asmodian-only.

## Verified offline

- An elevated, read-only process query found no Aion/Java processes. Neither
  Aion nor GameServer was started or attached to. Current lifecycle policy stays
  in force.
- The installed inventory passes 16 checks, with 31 client hashes, 70 client
  receipts and 93 server receipts. The snapshot is in the external development
  workspace at `diagnostics/window-startup-before.json`.
- The installed RelicCalc archive contains the exact six configured local
  routes. The installed native browser and authentication hooks match current
  source. The rejected BrowserFrame probe remains absent.
- The native Lua binding enqueues Show/Hide and browser navigation as addon UI
  commands. `SetWidgetVisible` is bound at `0x5fc9d0` (command 12),
  `LoadUrlWithWebAuth` at `0x6058c0`; both feed `0x629050`. The consumer is
  `0x629190`, dispatching through `0x60daf0`. Slash commands additionally pass
  through the per-addon event queue (`0x621f30`, worker `0x623790`). Thus page
  navigation is downstream of the common custom UI command path.
- The browser navigation producer `0x12cc80` queues browser command 3.
  The existing bridge tick at `0x131ef0` consumes browser-to-game events; it is
  not the Lua command queue consumer.
- Native queue counts are referenced by unchanged stock instructions at
  `0x629069` (UI), `0x12cd21` (browser commands), `0x131fe0` (browser events).
  The addon map used by the native update is referenced at `0x626bd5`.
- The publisher missing-key path still contains a 30,000ms callback deadline.
  Its presence alone does **not** prove that it causes this reported delay.
  Journey already bypasses that path when its native key is absent.
- Previous browser fixtures loaded pages directly and the machine-code fixture
  supplied a synthetic browser wrapper. They did not test the complete native
  menu click -> addon event -> UI command -> browser command lifecycle. Their
  passing results therefore do not establish timely opening inside Aion.

## Prepared read-only capture

`client-mods/diagnostics/window_timing.py` prepares a current-file hash and loaded
code guards without opening any process. Its separate capture command samples
the three queue depths, the RelicCalc event queue, visible addon dialog slots,
and a boolean indicating whether a native session key exists. It never writes
process memory, injects a DLL, calls a native game function, changes client files,
or starts either program. It does not store token values or page URLs.

Eight fake-memory tests pass. Preparation additionally verifies native queue
reference instructions against the supported original client. No native fixture
has been executed. Authorized read-only captures have now run. The manifest is
`D:/Proiecte/Project Restructure/Aion Development Workspace/diagnostics/window-timing-manifest.json`.

Only after explicit authorization for a live reproduction and after the user
starts the game, refresh the process ID and run:

```powershell
python client-mods/diagnostics/window_timing.py --capture 'D:/Proiecte/Project Restructure/Aion Development Workspace/diagnostics/window-timing-manifest.json' --pid <AionPID> --seconds 45 --output 'D:/Proiecte/Project Restructure/Aion Development Workspace/diagnostics/window-timing-trace.json'
```

The user opens one affected window while the capture runs. Capture is bounded
to at most 120 seconds and refuses changed on-disk/loaded code. Queue samples
can locate backlog, but zero depth does not prove that a handler is idle: it may
already be executing. If all queues are empty during the reported delay, further
authorized timing of dispatch/page-load boundaries is needed. Do not turn that
outcome into another unsupported authentication or renderer diagnosis.

## Authorized live evidence and staged repair

External diagnostics: `window-timing-trace-20261007.json`,
`window-timing-detail-20261007.json`, `window-timing-login-20261007.json`, and
`window-http-timing-20261007.json` under the development workspace diagnostics.
The later style capture did not contain a relog and establishes no additional
login evidence. Do not count it as another reproduction.

- Initial startup: native key present at 37.625 seconds; UI commands built to
  3,490; addon dialogs first visible at 63.875 seconds. This capture includes
  loading, so the backlog alone does not distinguish loading from a defect.
- Re-entering the character: state 14 resumed at 57.282 seconds. Commands built
  to the native 4,001 limit, with head opcodes 18/21. Market and the companion
  bar first became visible at 91.704 seconds. Key remained present throughout.
  Browser queues did not have a sustained backlog during that wait.
- The user confirmed Market finally appeared after about 30 seconds and later
  windows opened normally. Read-only localhost probes completed in under 200ms
  for Journey shell, JS asset and unauthenticated state rejection. This does
  not measure authenticated server work or every route.
- Stock native dispatch maps opcode 18 to `SetWidgetAttr` and 21 to
  `SetWidgetUIImage`. Those branches call their native setters, then fall
  through to `0x60ffa5`'s zero return. Consumer `0x629190` removes the processed
  command but ends its drain on that zero. Wardrobe applies hundreds of color
  and image operations at OnLoad; companion timers keep adding Enable commands.
  This establishes a concrete queue-yield defect consistent with the timing.
  Successful in-game latency correction still requires the staged patch test.

`window_queue_patch.py` patches only the zero-return tail and guarded unused
main-section padding `0x144e700..0x144e7fb`. It continues only opcodes 18/21 with
the exact inline `RelicCalc` name, yielding once every 64. All native setters,
missing-widget branches, unrelated command/addon returns and existing methods
remain untouched. The entire original dispatcher is checked before patching.
Generated-code interpretation verifies two full batches, counter overflow,
all other opcodes, malformed arguments and nonmatching addon names. This is
Python interpretation, not execution of native game code or a live fixture.

Package: `staging/output/window-queue-20261007-v2` under the external development
workspace. Eleven files include Game.dll, active graphics payload/state, cursor
tracking and both incremental restore baselines; 28 current feature resources
are hash-preserved. The first staging attempt refused appended section padding
absent from restore baselines; no guards were relaxed. The reviewed patch uses
identical stock padding present in every baseline.

Current inventory passed 17 checks / 31 client file hashes / 70 client and 94
server receipts (`diagnostics/window-queue-before.json`). The disposable
file-only restore fixture passes launcher acceptance, deliberate later-edit
rejection, graphics restore and preservation of the fix in the cursor baseline.
The hash-guarded installer verify-only check passes. Installer tests additionally
prove denied process inspection refuses installation and a mid-transaction file
replacement failure restores every written original hash, retaining verified
external recovery copies. No real client files were
changed, no server replacement/restart occurred, and nothing was pushed.

## Installation after user closed Aion

The user said "alright i closed it". Elevated CIM confirmed no Aion process.
The v2 preflight refused a changed `system.cfg`; that was the only changed
preserved resource, and all transaction/mod files still matched. Fresh current
inventory and package v3 retained the current settings. Its native Game.dll was
byte-identical to the reviewed v2 patch. The v3 disposable restore check and
installer verify-only check passed before replacement.

Installed with `install_window_queue.py` from external package
`staging/output/window-queue-20261007-v3`. Recovery receipt:
`archives/client/window-queue-20261007-221447-067110` beneath the development root.
All eleven transaction hashes and 28 preserved resources matched immediately
after installation. Game.dll SHA256:
`5e27d0e2e7622d382867461286475ec70e711416e3dee0d8f062ac7c30af7ab1`.

The installed launcher preparation scripts all passed without launching Aion:
ApplyGraphicsMenu, SelectRenderer, InstallNativeCursorPatch, QualityProfile
Startup, Enable-FreeFlight and ApplyGlobal. Vulkan stayed selected. Normal
launcher preparation updated `system.cfg` and `SystemOptionGraphics.cfg`;
all transaction hashes remained intact afterward. No server restart, attach
or replacement occurred. Inventory now passes 18 checks / 31 client hashes /
71 client and 94 server receipts and explicitly verifies the native batch hook.

Next: the user reopens Aion and tests first-login Journey/Market latency,
later openings, Wardrobe visuals, companion bar and pet/menu coexistence.
Installation and offline results do not establish that gameplay acceptance.
