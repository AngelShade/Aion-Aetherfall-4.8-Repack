# Poeta and Sanctum visual overhaul: first pass

This is a client asset and atmosphere pack for the installed Aion 4.8 NA client.
The renderer is supplied as compiled CryEngine DLLs; renderer source code is not
present in the server project. This pack improves its visual inputs and authored
environment settings. It is the first regional pass, with further scenery,
character, vegetation, effect and renderer work still outside this release.

## Included

- Poeta: new meadow grass, warm earth, mossy paving and one cliff surface.
- Sanctum: all five terrain materials referenced by its terrain surface list,
  plus selected marble floor and carved wall textures.
- Shared environmental fire and water ripple sprites. These two sprites and the
  two architecture textures can also affect other locations referencing them.
- Poeta/Sanctum mission atmosphere: slightly lower ambient amplification and
  stronger sunlight, later fog falloff, adjusted sky scattering/color, stronger
  water reflection/refraction and lower water glare/fog density.

Thirteen authored replacement textures use legacy DXT1/DXT5 compression and full
mipmap chains. Terrain is 1024 square or 1024x2048; marble is 1024 square or
1024x2048; particles are 512 square. Original alpha and aspect ratios are retained.
Generated PNG source dimensions vary; the build normalizes them to these sizes.
The asset art was produced with the built-in image_gen tool from original client
references. Exact prompts are in `prompts.json`; mappings are in `assets.json`.
Pillow performs DDS conversion, resizing, mipmaps and inspection previews.

## Build and installation

```powershell
python ./build_package.py --client-path '<Aion 4.8 NA root>' --output './package-v2'
./Install.ps1 -ClientPath '<Aion 4.8 NA root>' -PackagePath './package-v2'
```

Use a new output directory for each build. The builder does not change the client.
Installation verifies all six original archive hashes, copies the package into
the client's `VisualOverhaul/packages`, backs up the launcher, and inserts its
apply command before the existing client start command. Other startup commands,
including free flight, remain in place. No graphics/flight/UI settings are reset.

When Aion is running, installation queues the package; it does not stop the game
or replace loaded archives. Exit normally and use `Aion Start.bat` for the next
launch. The apply helper backs up and verifies all six archives before replacing
them, rolls back on application failure, and verifies installed hashes. Every
later launch verifies the installed pack and does not create another backup.

The installed helper can inspect hashes without changing anything:

```powershell
./VisualOverhaul/ApplyPackage.ps1 -ClientPath '.' -PackagePath './VisualOverhaul/packages/<packageId>' -VerifyOnly
```

## Restore

Close Aion and run this from the client root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File './VisualOverhaul/Restore.ps1'
```

Restoration verifies backups and current archive hashes before changing any
archive. It stops if an archive contains later changes, and removes only this
pack's startup block, retaining later unrelated launcher edits. Cancelling a
queued pack removes the startup block without modifying the archives.

## Validation and limits

The builder verifies every changed DDS mip, legacy format, dimensions, alpha and
archive CRC; all 6,095 other entries are byte-identical. Mission XML is verified
after encoding. The synthetic install check covers queued installation while
running, the running-client guard, backups, application, repeated application,
later edit protection and exact restoration without losing launcher changes.

Actual in-game appearance, tile seams, FPS, day/night behavior and shader cache
behavior still need live verification after relaunch. Time-of-day environment
presets may override mission atmosphere values. No new geometry, PBR renderer,
ray tracing, character textures or revised skill animations are included.

Inspect Poeta's main meadow, Akarios paving, brown trails and cliffs, and Sanctum's
garden ground, marble floors/walls and harbor water, at a matched camera position
before/after. Test terrain at close/medium distance and watch for repeated edge
lines, marble ornament alignment, particle clipping and excessive brightness.
`comparison.png` compares the original textures to the final compressed DDS art;
it is an asset comparison, not an in-game screenshot.
