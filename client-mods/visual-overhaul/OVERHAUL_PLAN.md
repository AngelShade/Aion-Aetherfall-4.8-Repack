# Direction for a substantial Aion visual overhaul

## Target

A more detailed, cohesive fantasy world that retains Aion's identity. Poeta should
have layered natural ground, convincing rock/bark surfaces, richer vegetation and
light that creates depth. Sanctum should have readable marble carving, distinct
stone/metal/glass materials, grounded architecture and controlled reflections.

The existing thirteen-texture pack is a small first asset pass. It does not
establish a successful regional overhaul, renderer improvement or live visual
acceptance. Further work should be judged by matched in-game scenes.

## Order of work

1. Capture a reproducible baseline for a Poeta meadow/village scene and a Sanctum
   plaza/harbor scene. Record resolution, client settings, camera, time/weather,
   frame rate and frame-time behavior.
2. Build a lighting/material prototype in those scenes: validate native ambient
   occlusion, shadow settings, tone mapping and texture filtering; test a small
   set of genuinely normal-mapped stone, cliff, bark and metal materials using
   compatible shader bindings. Adjust real environment/time-of-day settings.
3. Compare native rendering improvements with modest post-processing for clean
   antialiasing, color response and bloom. Judge UI text and character readability
   alongside scenery. Retain only improvements demonstrated in live rendering.
4. Expand the accepted material workflow to the complete visible scene: terrain
   blends, architecture, props, trees, leaves, sky, water and distant LOD assets.
   Match normal/specular maps, scale and material response wherever supported.
5. Rework effect presentation: test particle layering, flame motion, smoke,
   embers, water movement, magic trails and impact timing with existing effects
   formats. Use motion and lighting coherently with the material changes.
6. Expand the accepted approach across Poeta and Sanctum; profile both dense
   scenery and combat. Broader game regions and character/armor work follow the
   same accepted workflow.

## Findings from the installed client

- Aion 4.8 NA uses `bin64/XRenderD3D9.dll` and already selects its High Quality
  graphics mode. A new preset alone does not establish an overhaul.
- Renderer help strings document native `r_mrt_ssao`, `r_mrt_sunshaft`,
  `r_mrt_glow` and `r_mrt_sharpen`. This establishes controls exist, not that they
  are enabled or effective in the current scene.
- `Shaders/shaders.pak` contains readable
  `HWScripts/Declarations/CGPShaders/CGRCAion_BGNormalDir.crycg`: it samples a
  diffuse map and a normal map, and uses tangent/binormal data to alter lighting.
  This establishes a normal-mapped shader path exists. Mesh tangent data,
  bindings, High Quality compatibility and cache recompilation need verification.
- Its readable `CGRCAion_BGBaseDir.crycg` samples only diffuse color in that path.
  Replacing diffuse art alone does not add material depth.
- Some files under the `MRT/DX9` path have an `#AIONENCRYPT` header, including an
  SSAO blur declaration. Archive decoding alone does not make them editable shader
  source. Changes to that path require a separate feasibility investigation.
- The server checkout does not contain the complete native client/renderer source.
  A fundamental renderer replacement would require substantial client engineering.

## External rendering options

[ReShade](https://reshade.me/) supports Direct3D 9 and can supply color correction,
antialiasing and bloom. Its standard build disables depth access during multiplayer;
depth-based AO or lighting must not be assumed available in Aion. Test native AO
first and measure actual post-processing results in this client.

[Beyond Aion's version DLL](https://github.com/beyond-aion/aion-version-dll)
documents DXVK support and quality filtering settings. DXVK is a performance and
compatibility option to evaluate alongside the overhaul, not evidence of newly
authored materials, vegetation or lighting. Wrapper compatibility and frame times
need measurement before adoption.

## Acceptance

The prototype must show a clearly visible improvement in matched, real in-game
images; convincing light/material interaction; clean terrain tiling and UV layout;
readable characters/UI; and acceptable performance. Generated concept art and
asset contact sheets do not establish the in-game result. Hardware and the desired
FPS determine quality budgets before the accepted prototype is expanded.
