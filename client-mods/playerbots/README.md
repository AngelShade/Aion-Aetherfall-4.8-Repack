# Player Companions client window

`prepare_companions_window.py` updates the existing movable/resizable Companions
plugin dialog over the current original client. Use `--window-only` for a sizing
repair: it preserves the installed command bar and every other plugin member.

```powershell
python client-mods/playerbots/prepare_companions_window.py --client '<original-client>' --output '<fresh-external-stage>' --window-only
python client-mods/account-window/install.py --prepared '<fresh-external-stage>' --backup '<fresh-external-backup>'
```

The client must be closed normally before installing the signed plugin cohort.
The existing signer retains the model key and signs all three addon archives with
the matching addon key. No Core or other native DLL replacement is needed.

The command bar and its collapsed icon share the player's dragged position.
Changing between the icon, compact bar and expanded commands uses native
`SetSize`, preserving the origin without reading and rescaling it. The native
bridge continues to save the position and keep the control reachable after
resolution changes. Saved settings need no migration.

For a command-bar controller repair, use the normal signed plugin builder:

```powershell
python client-mods/aetherfall-core/prepare.py --client '<original-client>' --output '<fresh-external-stage>' --update --plugin-only --companion-bar-update
python client-mods/aetherfall-core/install.py --client '<original-client>' --stage '<fresh-external-stage>' --backup '<fresh-external-backup>'
```

This retains the installed command-bar XML, other plugin members and native DLLs.

The browser uses the publisher's `noscale` widget flag, so the engine does not
render a larger page and shrink its rasterized text at UI scales below one.
The controller uses the existing `GetUIScale()` API to round the browser's physical
width and height to whole pixels. Creation and painting then use the same extent,
including after a settled resize. The minimum dialog remains 760 by 520 UI units.
Native proportional child resizing is omitted; the existing debounced controller
fits the browser once resizing settles. Font families, text sizes, server actions
and the separate Companions command bar are retained.

Offline checks should cover the actual client Lua wrappers, native browser sizing,
resize settling, preservation of other menu bytes and compressed members, and the
signed delivery cohort. Final text sharpness and window behavior are owner in-game
acceptance, not proof supplied by an isolated renderer or a successful build.
