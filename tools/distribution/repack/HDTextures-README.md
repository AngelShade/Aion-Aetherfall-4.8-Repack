# Optional HD world textures

Update the repack to 0.94 first. Standard world textures work without another
download. Other graphics, interface and gameplay updates remain in the core patch.

To use HD textures:

1. Download the separate `Aetherfall-HD-Textures-2026.10.22.1.zip`.
2. Close Aion normally and allow **10 GiB free on the game drive** after downloading.
3. Open **Aetherfall Hub** and choose **Mods & tools**.
4. Choose **Install ZIP** and select the downloaded ZIP intact.

Do not extract the HD ZIP into the game. The manager verifies the download,
saves one standard-texture recovery copy, and updates the shared graphics files.
Keep the ZIP if you want to reinstall HD later. Turn off **Use HD world textures**
in the Hub to restore standard textures without another download. Turn it on to
use the previously selected ZIP again, or choose a downloaded ZIP when prompted.

The HD pack contains 91 world texture archives from seven upgrade batches.
It changes appearance only. Memory use, loading times and visual quality still
need testing in the game on each player's hardware.

The download button opens the configured public hosting page. Download the ZIP
there, then select it through Install HD from ZIP.

For future core releases, the builder protects these optional archives. A changed
archive requires updating the component contract and compatible HD pack together.
The local installed selection and standard cache are never release payloads.
Changing only the hosting URL is enough when the archive is byte-for-byte identical.
A new or recompressed ZIP needs a new archive checksum in the core metadata.
Changed textures also need matching file hashes, shared graphics entries and asset
version. Existing HD installations need a transition from their previous profile;
the current manager does not automatically accept a different asset version.
Restore standard textures with the previous manager before that transition.
