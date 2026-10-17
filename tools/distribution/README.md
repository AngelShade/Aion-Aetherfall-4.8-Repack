# Aetherfall repack distribution

This packages the **portable repack**, not the original playable client or the
development server. It does not upload, start seeding, run tests, or start any game
component. Keep the repack stopped and unchanged while it is packed.

`pack.py` reads the repack's existing `FILES.json`, adds the inventory itself,
and creates a store-mode 7z archive split into **1900 MiB** volumes. Store mode
avoids spending hours recompressing native game archives. It needs space for a
second copy of the packaged data on the output drive. Existing output is never
overwritten. The copy in the repack remains the input.

The archive volumes are hashed in one pass for SHA-256 and cross-file BitTorrent
v1 pieces. The output contains the volumes, torrent/magnet, checksum list, a small
`Aetherfall-Downloader.zip`, mirror publisher and a seeding command. No repeated
full-client preservation scan or extraction test is part of packing.

Example in this workspace:

```powershell
python tools/distribution/pack.py `
  --package 'D:/path/to/Aetherfall-Repack' `
  --output '<DISTRIBUTION_ROOT>' `
  --tools 'D:/path/to/staging/distribution-tools'
```

The tools directory contains the official extracted distributions:

```text
aria2/aria2-1.37.0-win-64bit-build1/aria2c.exe
aria2/aria2-1.37.0-win-64bit-build1/{COPYING,LICENSE.OpenSSL,README.mingw,AUTHORS}
7zip/x64/7za.exe
7zip/{License.txt,readme.txt}
```

7-Zip's installed `7z.exe` creates the archives; `--sevenzip` changes that path.
The downloader includes standalone `7za.exe` and aria2, licenses, source links and
recorded tool hashes. It requires Windows PowerShell 5.1, not Python. The owner
uses Python only to pack or publish mirror metadata.

Once uploaded, edit `Aetherfall-Downloader/mirrors.json`, then:

```powershell
python publish.py 'C:/path/to/distribution' --mirrors-url 'https://host/release/mirrors.json'
```

`WebSeeds` is an array of direct HTTPS folder bases **above** the torrent's root
folder. aria2 combines these with peers. `Files` maps each exact volume name to
an array of unrelated direct HTTPS URLs; that uses HTTP mode. `SharePages` is for
manual download links and is not passed to aria2. Keep the release manifest and
hashes fixed when changing URLs. New content requires a new release/torrent.

Private Google Drive home and MEGA `/fm/` URLs are not public download URLs.
Browser shares, authentication, MEGA decryption and transfer quotas are not
bypassed. No functioning public mirrors are claimed until real share/direct
links exist. With no mirrors, the downloader uses torrent peers and needs an
online seed. The packer does not silently publish an initial seed.

`publish.py` changes only outer torrent webseed metadata, retaining the info hash,
updates the local torrent checksum, emits a standalone HTTP Metalink when every
part has a URL, and rebuilds the small ZIP. A stable live mirror URL lets existing
downloaders pick up later URL changes; it cannot replace their pinned content
hashes.

The downloader checks free space for archives plus extracted data, resumes with
aria2, verifies all volume hashes, extracts through 7-Zip, checks the extracted
inventory hash, and retains parts for recovery/seeding. Existing installs are
not overwritten; only an interrupted extraction bearing this release's own
marker can be resumed. It never launches downloaded programs or game components.

References: [aria2 manual](https://aria2.github.io/manual/en/html/aria2c.html),
[BEP19 webseeds](https://www.bittorrent.org/beps/bep_0019.html),
[GitHub asset limits](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases),
[7-Zip redistribution](https://www.7-zip.org/faq.html).
