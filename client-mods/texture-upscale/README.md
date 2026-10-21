# Upscaled world textures

2x resolution for the textures a zone actually places: object/brush materials from
`Levels/common/Mesh_Textures_*.pak` resolved through the zone's `brush.lst`/`objects.lst`
and meshes, plus the zone's own terrain detail layers. First pass: Poeta (`lf1`) and
Ishalgen (`df1`).

Keep zone batches paired across races: Poeta/Ishalgen, Verteron/Altgard,
Eltnen/Morheim, Heiron/Beluslan, and the corresponding later zones.

Colour is upscaled 4x with Real-ESRGAN (`realesrgan-x4plus`, ncnn/Vulkan build, external
tool under the development workspace `tools/realesrgan`), with 16-pixel wrap padding so
tiling textures stay seamless, then reduced to 2x. A tenth of the original is mixed back,
then a light unsharp mask, +10% contrast and +15% saturation (owner review: the plain
upscale with original grain read as faded old film). Alpha is
resized from the original; cut-out alpha stays a clean cut-out. Each texture keeps its
original DXT1/DXT3/DXT5 format and gets a full mip chain. Sources under 64 px or over
512 px are left alone (no visible gain / video-memory bound).

Installed choice (owner, 21 October): **4x-PBRify_RPLKSRd_V3** (Kim2091, CC0, OpenModelDB;
trained on 2000s-era game textures, removes DXT block artifacts) with `--finish plain`.
It runs through `pbrify_upscale.py` in a separate tools venv with CUDA PyTorch + spandrel
(`tools/pbrify` in the development workspace). Real-ESRGAN with `--finish boost` remains
available but read as repainted/over-smoothed on moss in owner review.

```powershell
<tools>/pbrify/venv/Scripts/python.exe -I pbrify_upscale.py --model '<tools>/pbrify/models/4x-PBRify_RPLKSRd_V3.pth' --input '<cache>/source' --output '<cache>/pbrify'
python -I build.py --client '<client>' --zones lf1,df1 --upscaled '<cache>/pbrify' --model-name '4x-PBRify_RPLKSRd_V3' --finish plain --work '<cache>' --output '<new folder>'
python -I build.py --client '<client>' --zones lf1,df1 --esrgan '<tools>/realesrgan-ncnn-vulkan.exe' --work '<cache>' --output '<new folder>'
./Install.ps1 -ClientPath '<client>' -PackagePath '<package>'            # Aion closed
./Install.ps1 -ClientPath '<client>' -PackagePath '<package>' -VerifyOnly
./Install.ps1 -ClientPath '<client>' -Restore -PackageId <id>            # Aion closed, newest batch first
```

The builder never touches the client; it verifies entry order, every untouched entry
byte-identical and every changed entry. The installer checks package and client hashes
before changing anything, backs archives up to `Mods/Backups/TextureUpscale/<time>`,
rolls back on failure and records `Mods/TextureUpscale/installed-<packageId>.json`.
Later batches are built from the archives as installed, so they stack; each keeps its own
backup and state, and restore refuses an archive a later batch changed. To leave archives
tracked by GraphicsOverhaul unchanged, pass
`--skip-tracked Mods/GraphicsOverhaul/packages/<id>/manifest.json`
(Sanctum `lc1.pak` and Pandaemonium `dc1.pak` terrain layers are tracked).
When intentionally upscaling shared terrain, such as Eltnen, the installer verifies
the active graphics package baseline, backs up its package copy and manifest, and
updates both along with the live archive. Restore checks and restores that complete
cohort; the launcher's graphics verification remains active. A later rebuild must start from the archives
as installed (the work cache is keyed by source hash).

For later zones, pass every installed earlier package with repeated
`--previous-package '<package>'` arguments. Their changed entries are preserved
instead of being upscaled again. Use `--prepare-only` to extract the pending tiles,
run the GPU upscaler on that new source folder, then build without `--prepare-only`.
For delivery, pass `-BackupRoot '<new external backup folder>'` to keep recovery
archives in the external workspace. Verification follows the complete installed
batch chain; restore still proceeds newest first.
