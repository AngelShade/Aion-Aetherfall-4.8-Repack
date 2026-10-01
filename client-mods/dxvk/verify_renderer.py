"""Compile and run hidden native x64/x86 renderer checks on this host."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parent
for arch, folder, vcvars in (("x64","bin64","vcvars64.bat"),("x86","bin32","vcvars32.bat")):
    build = ROOT / "build" / arch
    logs = build / "logs"
    logs.mkdir(parents=True, exist_ok=True)
    toolchain = Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")) / "Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build" / vcvars
    script = build / "compile.cmd"
    exe = build / ("dxvk-smoke-" + arch + ".exe")
    script.write_text(f'@echo off\ncall "{toolchain}" >nul\nif errorlevel 1 exit /b 1\ncl /nologo /EHsc /O2 /MT /Fo:"{build / "smoke.obj"}" "{ROOT / "smoke.cpp"}" /link /OUT:"{exe}" user32.lib\n', encoding="utf-8")
    subprocess.run(f'cmd.exe /d /s /c ""{script}""', check=True, cwd=build)
    subprocess.run([str(exe), str(ROOT / "payload" / folder / "d3d9.dll"),
                    str(ROOT / "payload/dxvk.conf"), str(logs)], check=True, cwd=build)
    log = next(logs.glob("*_d3d9.log")).read_text()
    assert "DXVK: v3.1.1" in log and "NVIDIA GeForce RTX 4060" in log
    assert "d3d9.samplerAnisotropy = 16" in log
    assert "d3d9.forceSamplerTypeSpecConstants = True" in log
    assert "err:" not in log, log
    print(f"Verified {arch} renderer and profile on the RTX 4060")
