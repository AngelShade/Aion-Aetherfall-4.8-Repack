# Stigma controls in Profile

Open Profile and select Stigma. Equip from your inventory or drag a Stigma to an
available slot; remove equipped stones through the native slot controls. The
small **Upgrade** button beside **Help** opens Aion's existing Stigma charging
window. Help and the normal NPC services remain available.
The label and tooltip use English client string-table entries; literal text in
these UI fields is treated as an unresolved string key and displayed as ???.

Class, level, unlocked slots, Kinah costs, materials, upgrade chances and failure
results still use the existing client/server rules. This does not grant items,
unlock slots or change upgrade success rates. The maintained server already
accepts these native equip/remove and charging requests without an NPC.

`prepare.py --client <original-client> --output <fresh-external-folder>` prepares
the existing x64 Game.dll, base and English profile layouts, and matching native
graphics/cursor restore images. Only known bytes of the supported client are
accepted. Five Stigma-only NPC admission branches change; initial construction
and mode resets retain interactive native normal/greater/major slot classes.
This works on the first profile opening without an earlier NPC visit.
Profile initialization/reset and both NPC-service close paths retain those
interactive slot kinds. They no longer turn unlocked slots into display-only
controls, which silently prevented double-click and drag/drop equipping.
The builder checks this in the active, graphics package and restore images.
For an installed Stigma build, the repair starts from the current DLL and
rejects changes outside those three instructions. Later click bridges, imports
and other native mods remain byte-for-byte intact; no older image is restored.
The button routes only its exact name to
native local dialog 431, without fabricating an NPC or a server interaction.

The small native bridges occupy verified unused bytes at 0x144daa0 and
0x144db30, after the existing browser bridge and before its preview bridge.
Game.dll size and existing imports are unchanged. The preparation checks all
unrelated compressed archive members and profile widgets for preservation.
Installation requires the original client closed normally, affected-file
backups and copied hashes. No GameServer replacement is needed.

Gameplay acceptance remains the player's check after installation: equip with
the usual cost, remove to inventory, open Upgrade, and perform a normal charge.
