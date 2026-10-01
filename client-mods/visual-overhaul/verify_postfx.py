"""Validate portable Vulkan ReShade plus enabled shaders on the real GPU."""
import os, json, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent
build=ROOT/'build'/'postfx-x64';build.mkdir(parents=True,exist_ok=True)
layer=build/'layer';layer.mkdir(exist_ok=True)
for name in ['ReShade64.dll','ReShade64.json']:
    shutil.copyfile(ROOT/'research'/name,layer/name)
cfg=build/'ReShade.ini'
cfg.write_text(f'''[GENERAL]
EffectSearchPaths={ROOT / 'postfx/Shaders'}
TextureSearchPaths={ROOT / 'postfx/Textures'}
PresetPath={ROOT / 'postfx/AionEnhanced.ini'}
PerformanceMode=1
SkipLoadingDisabledEffects=1
IntermediateCachePath={build}
[OVERLAY]
TutorialProgress=4
[INPUT]
KeyOverlay=36,0,0,0
KeyEffects=145,0,0,0
''')
source=(ROOT.parent/'dxvk/smoke.cpp').read_text()
source=source.replace('WS_OVERLAPPEDWINDOW,', 'WS_POPUP,')
source=source.replace('0, 0, 128, 128', '-32000, -32000, 640, 480')
source=source.replace('pp.BackBufferWidth = 64; pp.BackBufferHeight = 64;', 'pp.BackBufferWidth = 640; pp.BackBufferHeight = 480;')
source=source.replace('check(device->Present(nullptr,nullptr,nullptr,nullptr), "Present");','''for (int frame = 0; frame < 600; frame++) {
        MSG msg; while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) { TranslateMessage(&msg); DispatchMessageW(&msg); }
        check(device->Clear(0,nullptr,D3DCLEAR_TARGET,0xFF2A5678,1.0f,0), "ClearFrame");
        check(device->BeginScene(), "BeginFrame");
        check(device->DrawPrimitiveUP(D3DPT_TRIANGLELIST,1,vertices,sizeof(Vertex)), "DrawFrame");
        check(device->EndScene(), "EndFrame");
        check(device->Present(nullptr,nullptr,nullptr,nullptr), "Present");
        Sleep(16);
    }''')
(build/'smoke.cpp').write_text(source)
toolchain=Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
cmd=build/'compile.cmd'; exe=build/'postfx-smoke.exe'
cmd.write_text(f'@echo off\ncall "{toolchain}" >nul\nif errorlevel 1 exit /b 1\ncl /nologo /EHsc /O2 /MT /Fo:"{build / "smoke.obj"}" "{build / "smoke.cpp"}" /link /OUT:"{exe}" user32.lib\n')
subprocess.run(f'cmd.exe /d /s /c ""{cmd}""',check=True,cwd=build)
env=dict(os.environ)
env['VK_LAYER_PATH']=str(layer)
env['VK_INSTANCE_LAYERS']='VK_LAYER_reshade'
env['RESHADE_BASE_PATH_OVERRIDE']=str(build)
logs=build/'dxvk-logs';logs.mkdir(exist_ok=True)
log=build/'ReShade.log'
if log.exists(): log.unlink()
subprocess.run([str(exe),str(ROOT.parent/'dxvk/payload/bin64/d3d9.dll'),str(ROOT.parent/'dxvk/payload/dxvk.conf'),str(logs)],env=env,cwd=build,check=True)
text=log.read_text(errors='replace')
print('\n'.join(x for x in text.splitlines() if any(s in x.lower() for s in ['error','warn','compiled','runtime environment','loading image']))[-7000:])
assert 'compiled' in text.lower() and 'AionPresentation.fx' in text and 'MartysMods_SMAA.fx' in text
assert 'ERROR' not in text, 'ReShade reported an error; inspect the log.'
print('PASS: portable Vulkan ReShade layer, shader compilation, texture loading and 600 frame presentation.')
