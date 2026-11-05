# Quest journal descriptions

Adds the missing English journal description for quest 3074,
**[Spend Coin] Dangerous Probability (Rep)**. It explains Nagrunerk's Angel's Eye
exchange and the three Kinah/Platinum Coin offers already implemented by the server.
This is restored journal guidance written from the installed dialogue and maintained
handler, not recovered retail summary wording. Existing dialogue and quest behavior
remain unchanged.

`prepare.py --client <original-client> --output <fresh-external-directory>` builds
the native encrypted document with the existing Aion codec. It checks the native
dictionary links, server payment/reward mappings, original page preservation and
every untouched compressed archive member. It also updates the Graphics Menu's
package, restore archive and ownership hashes, plus the installed-mod inventory.
The previous restore archive is backed up externally during guarded delivery.

Preparation does not change the client. Close Aion normally before installing the
prepared affected files, back up their previous versions externally and verify
copied hashes. Record the receipt in the local inventory/current-state note.
Client restart and visible journal text remain owner acceptance checks.

`prepare_desperation.py --client <original-client> --output <fresh-external-directory>`
also supports quest 30500, **Desperation**. It replaces only the first sentence of
`quest_complete` with "Monodia told you that the Rentus Base, a significant Reian
post, was in trouble." It accepts the original "Ancanus told you" sentence and the
interim "You were told" sentence. All eight pages are checked for other Ancanus
references. UTF-16 and the
native HTML/PAK encoding are retained; untouched compressed records and central
metadata (apart from relocated offsets) are verified byte for byte in both the
active and Graphics restore archives. Repeating the overlay preserves current text.

Install this six-file package with `transmog-menu/install_window_queue.py --client
<original-client> --package <prepared-directory>`. Stage it under the external
workspace's `archives/client/<slice>/prepared` directory. The existing installer
requires Aion closed, saves originals outside the client, checks for concurrent
changes and supports `--restore <receipt-directory> --verify-only` to verify reversal
without performing it. No DLL or other archive is replaced.
