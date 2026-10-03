# Aion browser engine replacement

Replace the HTML renderer while preserving the installed UI, server routes,
authentication, native item artwork, character previews, and other client patches.
No replacement is installed or ready to install yet. This directory currently
contains the read-only ABI audit, not a browser adapter.

## Candidate and observed constraints

Ultralight is the candidate to evaluate first for the existing Aion 4.8 NA x64
client. Its pixel-buffer rendering can fit the existing game texture upload path.
GPU rendering would require additional renderer integration; it is not an automatic
benefit of an engine swap. Performance improvement must be measured with the same
panels and interactions before claiming a gain.

The installed Awesomium.dll has SHA256
`618b62df03032a94cdef5800f6de92a1f0b297234efc54ccb6a82fe5f086ceff`.
Its PE export timestamp is October 23, 2012 and it exports 356 symbols, including
the older `awe_*` C API. Ultralight exposes a different API: copying its DLL over
Awesomium.dll cannot replace it.

Game.dll's static import table exposes only `awe_webview_destroy`; the protected
binary does not disclose a complete browser contract through ordinary PE imports.
The live read-only scan found two exact export pointers, not a complete call list.
Neither the import table nor the export count is sufficient evidence to construct
a complete drop-in adapter. The callback and input/rendering contracts still need
to be mapped and verified.

The installed AionIconBridge depends on exact Awesomium function prologues for
create/destroy/resource callbacks, on the resource callback at view offset `0xe8`,
and on JS callback registration hooks. The resource callback currently supplies
native item icons at the existing HTTP image URLs. Those URLs deliberately have
no server-side PNG fallback. This functionality must survive the migration.
The Wardrobe bridge additionally registers ItemPreview, WardrobePreview,
WardrobeControl, WardrobePoll, and JourneyVisibility methods on AionObject.

## SDK availability checked October 3, 2026

The current [official SDK download](https://ultralig.ht/download) requires sign-in.
The publicly linked SDK from the
[official GitHub repository](https://github.com/ultralight-ux/Ultralight) downloaded
as version **1.3.0**, WebKit **610.4.3.1.4**, with 2023 headers. Its archive SHA256
is `4fa7aadd1e4ba4a7dc04d17b1d82b37b141c6e4e7196501150486fa6ac1635c5`.
It is suitable for preliminary investigation, not evidence that the current SDK
passes our requirements. In particular, its public headers expose FileSystem
callbacks for file URLs, but no equivalent to the existing HTTP resource-request
callback was found. That contract needs checking against the current SDK before
selecting an implementation.

[Current pricing](https://ultralig.ht/pricing) distinguishes a free tier with
limited performance/features from Pro with full performance/features. A performance
comparison must record the SDK version and edition rather than assuming all
Ultralight builds have the same capabilities. No license was purchased and no
account was created.

## Read-only inspection

```powershell
python client-mods/browser-replacement/audit.py `
  --client '<Aion 4.8 NA client root>' `
  --output output/browser-replacement/audit.json
```

Optionally provide `--pid <Aion process ID>` to inspect only loaded Game.dll and
Awesomium.dll image pages. It never writes process memory or client files.
Runtime scans report exact pointer matches and do not prove call coverage.

## Acceptance before installation

1. Map all browser API calls used by the actual client, including protected
   dispatch paths, value ownership, callbacks, initialization, and shutdown.
2. Compile an adapter against a specified current Windows x64 Ultralight SDK and
   edition. Keep the existing BGRA texture and input contracts.
3. Verify unchanged fixture pages: Market, Cash Shop, Journey, Season Pass, and
   any stock browser panels. Exercise authentication, POST requests, redirects,
   repeated open/close, resizing, transparency, keyboard, mouse, wheel and focus.
4. Verify original client icons without HTTP icon downloads, and preserve stock
   ItemPreview plus Wardrobe/Journey callback behavior and the game-thread queue.
5. Measure input latency, frame time and memory against the same Awesomium
   fixtures. A smaller renderer does not alone prove a faster Aion UI.
6. Prepare an incremental, hash-verified engine package with backups. Preserve
   existing XML, Lua, HTML/CSS/JS, locale archives, pet signing, and unrelated DLL
   hooks. Install only with Aion closed; verify in-game after a fresh launch.

CEF was investigated and its official SDK downloaded into ignored task output as
an alternative. No CEF adapter was built and no client files were changed. The
engine choice has not been finalized by a working integration.
