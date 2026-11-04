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
128px movable outer frame in both native layouts and the matching bridge; content,
buttons, strings and every other compressed archive member stay unchanged.
Affected graphics package/recovery archives and their hashes are staged too;
known moved-client `Mods/Backups` paths are rebased only after hash validation.
Game.dll, cursor recovery, release metadata and shared inventory are not staged.
The 18px title and lower frame now fit outside the countdown/gauge content.

Drag any visible roll by its title bar to move the whole group. Each item keeps
its own Need / Greed / Pass controls and countdown. Rows compact when a roll
closes; reused slots keep the shared anchor. On short viewports, the group wraps
into adjacent columns so simultaneous rolls remain separate. MoveUI's existing
single-window draft pin is respected and moves the peer rows with it. Native
bidding, five pending roll slots and the server's remaining-item queue are retained.


The appearance follows the supplied compact loot-roll reference using Aion's
own item artwork, quality colours, controls and frame. It does not add the WoW
bonus-loot currency mechanism. Build and static package inspection do not prove
the live visual result; gameplay acceptance belongs to the user.

The server-side Need/Greed behavior is controlled by `gameserver.group.loot.need_greed` in `game-server/config/main/group.properties`.
