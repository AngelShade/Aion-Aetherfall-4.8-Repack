# Native loot rolls

Compact, vertically stacked native item panels with item icons/tooltips, a
countdown, Need / Greed / Pass and the player's submitted choice. Five native
roll slots are reused; no browser or additional network endpoint is involved.
The server owns eligibility, one response per player/item, random rolls,
Need-over-Greed priority, timeouts and normal inventory delivery. Native bidding
retains its existing flow.

`prepare.py --client <original-client> --output <fresh-external-stage>` compiles
the native bridge and stages the exact active and graphics/cursor recovery
cohort. It does not install, start or attach to Aion. The bridge preserves the
native item descriptor and packet writer; decision 0 is Pass, 1 Need, 2 Greed.
The matching server and client must be installed together with Aion/GameServer
closed. Inspect the staged manifest for copied-file hashes and recovery paths.

For an already-installed bridge, add `--layout-only`. This stages the larger
128px outer frame in both native layouts and the matching bridge; content,
buttons, strings and every other compressed archive member stay unchanged.
Affected graphics package/recovery archives and their hashes are staged too;
known moved-client `Mods/Backups` paths are rebased only after hash validation.
Game.dll, cursor recovery, release metadata and shared inventory are not staged.
The 18px title and lower frame now fit outside the countdown/gauge content.

The appearance follows the supplied compact loot-roll reference using Aion's
own item artwork, quality colours, controls and frame. It does not add the WoW
bonus-loot currency mechanism. Build and static package inspection do not prove
the live visual result; gameplay acceptance belongs to the user.
