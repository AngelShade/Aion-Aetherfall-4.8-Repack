---
name: aion-native-client
description: Repair or extend Aion Aetherfall 4.8 native English client UI, PAK/XML/Lua resources, launcher and DLL integrations while preserving installed mods and recovery images.
---

# Native Aion client

Use current `AGENTS.md`, the original-client path in `project-notes/current-state.md`,
and the affected entry in `docs/INSTALLED_MODS.json`. Historical Downloads paths have
changed; refresh the actual path and relevant receipt before preparing an edit.
Default to the original only; do not silently synchronize the repack.

Start from the maintained affected `client-mods/<feature>` builder. Prefer native
resource/code changes that fit existing behavior. Resolve labels through native
English string-table keys: a literal `text` or `tooltip` value may display as ???.
Patch the active English locale and base layout when the feature uses both.

For a layout change, preserve unrelated widgets and exact compressed bytes of other
PAK members. For Game.dll work preserve supported input guards, signing, pet/Lua
behavior, existing imports and graphics/cursor compatibility. Use maintained
`client-mods/transmog-menu/graphics_compat.py` ownership helpers; update active,
package and restore images together. Do not bypass guards or mix signature/key cohorts.

Build/prepare externally with the normal native tooling. If a known already-patched
image is valid, retain it; a label repair does not require changing executable code.
Inspect the changed resource or instruction and the relevant preservation checks.
Avoid browser tests and full client scans. Verify the affected client closed normally
before installation; servers can remain running for a client-only replacement.

Back up and hash only the affected cohort, deliver matching recovery metadata, and
refresh the feature receipt and inventory. Preserve credentials, settings and every
unrelated mod. User-visible appearance and gameplay remain acceptance checks until
the user actually confirms them.
