# Aetherfall login branding

`brand_login.py` changes `ui/loginnotice.xml` and server ID 1's display name in
the English client archive. The announcement describes the deployed Kinah shop,
Central Market, expanded inventory with search, and service menu shortcuts.

The builder stages outside the client and verifies every archive entry, allowing
only the two intended XML payloads to change. Install with Aion closed and keep a
backup. Rebuild from the current installed archive to preserve other UI changes.

Installed announcement v2 on 2026-09-30. Backup:
`game-server/target/aetherfall-login-v2/data.pak.before-v2`.
