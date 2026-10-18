# Statue teleport progress timing

## Current maintained behavior: server supplies the duration

`gameserver.hotspot.teleport.cast_time_millis` in the GameServer's
`config/main/custom.properties` is the single setting (default 1000 ms).
The server snapshots its effective value once per cast and uses it both in
the start message and in the scheduled completion. Values below 1 use 1 ms.
After the usual server configuration reload/restart, later timing changes
need no further client editing or rebuilding. Cooldown remains separate.

The verified x64 action-1 dispatcher ignores the packet's second D field
(the historical positive location ID). Updated servers encode negative
milliseconds there, preserving the existing nine-byte action-1 body. The
one-time client patch reads that duration and drives both native timer fields;
whole displayed seconds round up. Positive legacy IDs, zero and INT_MIN use
the previously installed one-second fallback. Action 2 cancellation and
action 3 location/cooldown layouts remain unchanged. Unmodified clients ignore
the field and retain their old fixed bar; they require this one-time update.

`prepare_dynamic.py` is the current native builder. It checks the complete
cast function and exact dispatcher, then places three small native hooks only
in the verified zero alignment tail of the existing writable executable icon
bridge section. File length, imports and other sections are retained. The
existing incremental graphics/cursor helper updates matching recovery images.
`install.py` verifies the prepared cohort and selected client closure, backs up
affected files externally and verifies copied hashes. `server_delivery.py`
prepares/copies the exact normal Maven JAR and appends only the missing timing
key, preserving existing owner settings. No runtime attachment is involved.

The normal GameServer build and isolated emitted-instruction check passed:
one millisecond, one second, fractional seconds, signed integer boundary,
legacy and malformed fallback; identity/register preservation. The artifact
comparison retained all 3836 unrelated JAR entries. No full suites, browser
tests, new test files or gameplay were run. Original-client/server delivery
is recorded in the installation inventory; repacks are separate. User
acceptance remains the statue bar, cancellation and actual teleport timing.

## Initial fixed-duration correction (superseded by server-driven timing)

The original server's hotspot teleport was reduced from 10,000 to 1,000 ms in
commit d271f5d01, while the native client still initialized its progress timer
with 10,000 ms and 10 seconds. The hotspot start packet has no duration field.

`prepare.py` updates only the two native timer immediates in the supported x64
hotspot-start function (RVA 0x16b050–0x16b2b5). Both active instructions and the
complete function are guarded against the verified original client. Timing is
checked against the maintained server delay; server cooldown and travel are
unchanged. This is specific to statue/hotspot casting, not map-loading progress,
spell casts, flight transport, ordinary teleporter animations or recall items.

Prepare through the maintained native builder with `--client` and a fresh external
`--output`. The existing `graphics_compat.prepare_incremental` helper preserves
current graphics/cursor ownership and patches the verified active, package and
restore images together. English UI, pet/Lua/signing keys and unrelated native
bytes remain. Install only with the selected original client closed; servers may
remain running. Backups and the installation receipt belong outside the client.

18 October: preparation and exact-byte/recovery checks precede original-only
delivery. No full suites, new test files or gameplay fixtures. User acceptance is
the bar reaching its end with the actual one-second teleport. Repacks are separate.
