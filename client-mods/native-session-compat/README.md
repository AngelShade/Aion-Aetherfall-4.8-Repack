# Native session compatibility

The supported NA 4.8 client can deliberately crash at Game.dll RVA `2c30a3`
when a player is added after a delayed server-address check failed. The check
is evaluated from opcode 8 (`S_EXP`, consumer `2cadf0`), parses an IPv4 address
and compares it with decoded address ranges (`383420`), then latches bit 0 at
`1326dec`. A later `S_PUT_USER` reaches `2c3030` and writes to address zero.
The crash can surface while a cinematic is playing.

The normal menu builder includes this compatibility repair. The incremental
builder here applies the same repair to the current cumulative installation.
At RVA `2c30a1` it changes `JE +7` to `JMP +7`, bypassing only the delayed
address-failure trap. The preceding zero object-ID branch continues to reach
the original trap. EXP fields, cinematic assets and other native instructions
are preserved. This does not establish the cause of separate physics errors.

Set `AION_DEV_ROOT` to the external development workspace, then run
`prepare.py --client <original-client> --output <fresh-external-output>`.
Install using `../transmog-menu/install_window_queue.py --package <output>
--client <original-client>`; add `--verify-only` for a read-only preflight.
It requires all Aion clients closed, checks source/payload/preservation hashes,
backs up externally and composes the graphics package and both restore images.
Rollback uses the same arguments plus `--restore <external-receipt>`.
The user verifies the Mosqua approach cinematic and normal play afterwards.
