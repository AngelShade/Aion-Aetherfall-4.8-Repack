# Lannok and Triroan boss classification

Lord Lannok (214696) and Unstable Triroan (214669) use `HERO / VETERAN`, gauge 21, in the maintained server templates. The matching client patch changes only their native `hpgauge_level` from 14 to 21. This selects the existing wide silver Hero frame and Hero Grade 2 emblem; no global UI skins, models or textures change.

The server classification invokes native boss control immunity, rating-based derived combat stats, rewards and pursuit rules. Explicit HP remains 292,976 for Lannok and 837,110 for Triroan, before owner-configured instance scaling. Encounter skills, phases, summons, loot, native races and tribes remain intact. There is no immunity-only AI override.

This is the requested coherent boss classification. Historical templates support Hero/Veteran, while the original matching 4.8 client uses gauge 14. The change does not certify exact retail 4.8 classification, combat numbers or encounter timing. An already transformed live actor does not identify the spell that transformed it.

## Prepare

Run `prepare.py` with `--client` pointing to the existing Aion client, `--expected-client-sha256` set to its independently recorded `Data/Npcs/Npcs.pak` hash, and `--output` pointing to a new external staging directory. The tool requires the matching server classification, verifies all other NPC fields and archive payloads, and writes a single staged archive plus `classification.json`. It never installs or edits the original client.

Deliver the maintained server templates and staged client archive together through the normal cumulative release and guarded offline workflow. Keep GameServer and Aion closed for replacement, back up the original archive and invalidate only reviewed server static-data caches. Preserve every unrelated mod and advance the intentional client archive guard in the cumulative receipt. Do not deploy the old immunity-only candidate or use this preparer as a live installer.

After the user's restart, check both bosses' Hero frames, native resistance to hostile silence/fear/deform/stun, pursuit and encounter skills, and loot. These gameplay checks remain user-owned.

## Paired offline delivery

After the complete canonical release review passes and GameServer and Aion are both closed, run `install.py --stage <prepared-directory> --review <canonical-review-directory> --backup <new-external-archives-directory>`. It verifies the normal builder, regression/native-browser/static-data evidence, exact paired NPC resource, staged archive semantics and existing installed-mod client guards. It backs up the original archive and inventory, atomically replaces only `Data/Npcs/Npcs.pak`, and updates only that entry in `docs/INSTALLED_MODS.json`. Other inventory fields and prior immutable server receipts stay intact. A failure restores the original archive and inventory; if components start or concurrent edits prevent safe rollback, the failure receipt identifies the retained recovery backup.

**Keep both components closed between the client copy and server copy.** Immediately run the canonical server release with its matching `--review --install`, then complete the independent installed verification and inventory refresh. The client installer receipt explicitly remains `CLIENT_INSTALLED_AWAITING_CANONICAL_SERVER_COPY`; it does not certify the server delivery or gameplay. The installer never starts, stops or attaches to either component.
