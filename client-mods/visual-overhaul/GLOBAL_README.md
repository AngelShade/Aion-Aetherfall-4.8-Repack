# Broad Aion 4.8 visual upgrade

This extends the existing regional artwork with renderer effects for the whole
game, additional shared materials, and authored environment changes. It targets
the current modified Aion 4.8 NA client and its normal 64-bit launcher. The player
confirmed the native cursor correction works before this package was staged.
DXVK 3.1.1, its native compatibility DLLs, the cursor patch and existing artwork
are retained.

## Included

- ReShade 6.8.0 through an explicit Vulkan layer, loaded only by the Aion launcher.
  No registry registration or global ReShade installation is performed.
- iMMERSE SMAA anti-aliasing, followed by a custom four-pass light bloom/color
  shader. Bloom uses quarter-resolution separable filtering. White monochrome UI
  receives much less bloom and color adjustment; this is not complete UI masking.
- Twelve newly authored terrain materials applied to 73 byte-identical texture
  copies across 38 zones: grass, leaf litter, sand, cliffs, basalt, carved ruins,
  moss/gravel, dry earth, sandstone, blue forest ground, lava and snow.
- One shared smoke particle texture with finer internal detail and BC3 alpha.
- 1,068 allow-listed lighting/water attributes in 89 level archives: ambient
  amplification reduced by 6%, sunlight amplification increased by 4.5%, Mie
  scattering reduced by 4%, reflection increased by 8%, refraction by 4%, and
  water sun glare reduced by 10%. Existing zero values remain zero. Positive
  reflection/refraction values are capped at one. Each zone retains its colors,
  fog, weather, time, entity placements, gameplay and material bindings.

190 level archives were inspected. Other level archives had no eligible positive
values for these adjustments, so they are unchanged. All locations still use the
global renderer effects. The previous 13-texture Poeta/Sanctum pack is retained.

This is a broad upgrade, not a completed remaster of every client asset. Character
and armor textures, most architecture and vegetation, additional terrain art,
new normal/specular material bindings and skill-specific effect animations remain.
No additional depth-based AO/GI is enabled. Native Aion AO remains enabled.

## Artwork and reproducibility

The built-in image_gen tool remastered local references. Art and briefs are in
`assets-global` and `prompts-global.json`. Generated source images are 1254 square;
the package normalizes terrain to compatible 1024/2048 powers of two, preserving
aspect ratio, original DXT1/DXT5 format, opaque terrain alpha and full mip chains.
Smoke is 512 square with alpha and a full DXT5 mip chain. Python handles DDS
conversion, resizing, mipmaps and inspection previews.

`postfx-sources.json` pins the official installer/DLL hashes and shader commits.
Ignored `research` and third-party shader folders are prepared from those sources;
they are required to reproduce installation. The source-level presentation shader
and initial preset are tracked. Third-party shader headers/license notices remain
in the copied files.

```powershell
python ./build_global_package.py --client-path '<client root>' --output './package-global-v1'
./InstallGlobal.ps1 -ClientPath '<client root>' -PackagePath './package-global-v1'
```

The builder requires a new output directory. `InstallGlobal.ps1` stages the
verified package and Vulkan files in `GraphicsOverhaul`, backs up the launcher,
and replaces the older regional verification startup block with the broad one.
It saves that old block for restoration. The old pack's state and original
backups remain intact. Its archive contents are the baseline for this new pack.

While Aion is running, only the prepared package and launcher are updated.
Installed archives are replaced after a normal exit, on the next launcher run.
All 128 archive baselines and package hashes are preflighted before mutation.
Verified backups precede archive replacement; failures roll back changed files.
Repeat launches verify the installed package and do not create extra backups.
Repeat installation preserves player edits to ReShade settings.

The launcher scopes the Vulkan environment to its batch process with `setlocal`.
It prepends the explicit layer path and layer name while retaining inherited
values. `RESHADE_BASE_PATH_OVERRIDE` selects the package's own configuration.
The normal launcher uses the 64-bit layer; 32-bit binaries are also staged for a
future separately validated 32-bit launch. Aion's `d3d9.dll` stays DXVK.

## Controls and verification

- **Scroll Lock:** toggle all additional post-processing for comparison.
- **Home:** open/close ReShade's settings overlay.
- **Print Screen:** save PNG screenshots before and after post-processing in the
  client's `Screenshot` folder. This compares renderer effects; archive artwork
  and native environment changes remain in both images.

```powershell
./GraphicsOverhaul/VerifyGlobal.ps1 -ClientPath '.'
```

The verifier checks all package/client archive hashes, the running ReShade module
path, current-session log age, shader compilation and errors. File/process checks
may need elevated access. They do not establish visual acceptance or FPS.

## Restore

Exit Aion normally and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File './GraphicsOverhaul/RestoreGlobal.ps1' -ClientPath '.'
```

Restoration checks every current and backup archive before replacing any. Later
archive edits stop restoration. It restores the exact pre-upgrade artwork,
removes this package's launcher block and restores the prior regional block,
retaining cursor/DXVK setup and unrelated later launcher lines. The private
Vulkan layer files remain on disk but are inactive without the launcher block.
Player-edited effects settings are retained. Use this restore before attempting
to restore the older regional pack.

## Validation and remaining checks

- All staged archive CRCs, entry order and contents checked. 9,038 other entries
  were verified byte-identical. Every changed DDS mip was independently decoded.
- Environment trees were reconstructed back to their originals after reverting
  only allow-listed attributes, then compared exactly; serialized XML was decoded
  and compared again.
- Fixture checks passed for a running-client queue/guard, prior pack handoff,
  backups, repeated install/application, player preset/launcher edit retention,
  later archive edit protection and exact restoration.
- An actual RTX 4060 DXVK/Vulkan test created a ReShade runtime, compiled both
  enabled effects and presented 600 frames without ReShade errors. The test uses
  a hidden 640x480 window with matching backbuffer size. A test assertion was
  corrected to match the lowercase `compiled` message in ReShade's log.
- Real-client package and baseline hashes were checked after staging. Activation
  and current-game shader logs await the player's normal restart.

In-game texture seams, smoke edges, brightness, UI legibility, cursor behavior
with the overlay, day/night/weather and dense-combat frame times still need
confirmation. Time-of-day systems may override mission atmosphere values.

Sources: [ReShade](https://reshade.me/),
[DXVK integration guide](https://guides.martysmods.com/additionalguides/apiwrappers/dxvk/),
[iMMERSE](https://github.com/martymcmodding/iMMERSE),
[ReShade base shaders](https://github.com/crosire/reshade-shaders).
