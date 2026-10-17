# Aetherfall login branding

`brand_login.py` changes `ui/loginnotice.xml` and server ID 1's display name in
the English client archive. Both builders use `notice.html` for the announcement
about companions, journeys, dungeons, trading, Wardrobe, and expanded storage.

The builder stages outside the client and verifies every archive entry, allowing
only the two intended XML payloads to change. Install with Aion closed and keep a
backup. Rebuild from the current installed archive to preserve other UI changes.

`prepare_experience.py --client <root> --output <fresh-stage>` stages the native
Aetherfall image subtitle beneath the unchanged original AION image, `notice.html` in the
native news loader, and original Elyos/Asmodian artwork with an adventure guide
in the character-selection promotion panel. Login controls, actions, remembered
credentials, character slots, and executables are untouched. The original named
browser object remains hidden for its native controller; the visible panel uses
native widgets and needs no website. Both base/English layouts and the current
graphics package/restore baseline are composed together. Every unrelated archive
entry is checked byte-for-byte. The subtitle is a DDS image registered in both
native skin libraries; it has no text label or font lookup. The ten adventure
panel labels use registered English localization keys (IDs 990100101-990100110).
The earlier unused subtitle string is preserved when already present. The builder
upgrades the earlier panel as well as clean clients. The generated PNG, native DDS,
and generation prompt/UV information are maintained under `assets/`. Both image
frames are maintained there as well; Aetherfall is displayed larger than AION.
Back up and install the staged files with Aion
closed. Actual layout acceptance requires the user's fresh client launch.

`install_experience.py --stage <stage> --backup <fresh-external-backup>` installs
that revision only after the selected client is closed and every source hash
matches. It preserves originals, verifies unchanged native binaries, and updates
an existing repack's checksums without changing unrelated manifest entries.

Installed announcement v2 on 2026-09-30. Backup:
`game-server/target/aetherfall-login-v2/data.pak.before-v2`.
