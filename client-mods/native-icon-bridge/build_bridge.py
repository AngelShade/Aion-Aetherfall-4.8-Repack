"""Compile a self-contained x64 DLL, then create an index for this original client."""
import hashlib
import os
import subprocess
from pathlib import Path
from prepare_index import prepare
from prepare_ui_art import prepare as prepare_ui_art
from patch_client import AWESOMIUM_SHA256
from prepare_shells import prepare as prepare_shells, inputs as shell_inputs

def build(client, output, *, refresh_indexes=True):
    awesomium = client / 'bin64/Awesomium.dll'
    if hashlib.sha256(awesomium.read_bytes()).hexdigest() != AWESOMIUM_SHA256:
        raise ValueError('Native icon bridge requires the verified 4.8 NA Awesomium.dll')
    game = (client / 'bin64/Game.dll').read_bytes()
    for rva, expected in [(0x12cc80, bytes.fromhex('488bc44881ecd8010000c744243003000000')),
                          (0x3663c0, bytes.fromhex('4c8bdc4881eca800000048634220')),
                          (0x3321c0, bytes.fromhex('40534881ec50040000'))]:
        if game[rva:rva + len(expected)] != expected:
            raise ValueError('Native window connection owner changed; review the newer Game.dll before building')
    source = Path(__file__).resolve().parent
    inputs = [*source.glob('*.h'), source / 'icon_bridge.cpp', Path(__file__).resolve(),
              source.parent / 'playerbots/playerbot_bar.h', source.parent / 'shared/web_endpoint.h', source / 'prepare_shells.py',
              *[p for _, p in shell_inputs()]]
    source_hashes = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
    target = output / 'bin64'
    target.mkdir(parents=True, exist_ok=True)
    # Keep compiler intermediates outside the installer payload.
    work = output.parent / (output.name + '-native-build')
    work.mkdir(parents=True, exist_ok=True)
    prepare_shells(work / 'web_shells.h')
    program_files = Path(os.environ.get('ProgramFiles(x86)', r'C:\Program Files (x86)'))
    vcvars = program_files / 'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    if not vcvars.exists():
        raise ValueError('Install Visual Studio 2022 C++ Build Tools, or edit build_bridge.py for your MSVC installation')
    command = f'call "{vcvars}" >nul && cl /nologo /std:c++17 /EHsc /O2 /MT /LD /I"{work}" /Fo:"{work / "icon_bridge.obj"}" "{source / "icon_bridge.cpp"}" /link /OUT:"{target / "AionIconBridge.dll"}" /IMPLIB:"{work / "icon_bridge.lib"}" windowscodecs.lib ole32.lib bcrypt.lib user32.lib'
    script = work / 'compile.cmd'
    script.write_text('@echo off\n' + command + '\n', encoding='utf-8')
    # A raw command line preserves cmd.exe's nested quotes; list2cmdline escapes
    # them as C-runtime arguments, which breaks a quoted CALL path.
    subprocess.run(f'cmd.exe /d /s /c ""{script}""', check=True)
    if any(hashlib.sha256(Path(p).read_bytes()).hexdigest() != digest for p, digest in source_hashes.items()):
        raise ValueError('Native bridge source changed during build; do not install this output')
    if refresh_indexes:
        result = prepare(client, target / 'AionIconBridge.index')
        result['uiArt'] = prepare_ui_art(client, target / 'AionUiArt.index')
    else:
        # Code-only update: the item/UI archives have not changed. Keep the
        # installed indexes and guard their exact inputs for installation.
        paths = ['bin64/Game.dll', 'Data/Items/Items.pak', 'Textures/ui/ui.pak', 'Textures/ui/skill_bg/skill_bg.pak',
                 'bin64/AionIconBridge.index', 'bin64/AionUiArt.index', 'bin64/Awesomium.dll']
        result = {'inputGuards': {p: hashlib.file_digest((client / p).open('rb'), 'sha256').hexdigest() for p in paths}}
    result['awesomiumSha256'] = AWESOMIUM_SHA256
    result['sourceHashes'] = source_hashes
    print('Original client icon bridge: built; source guards:', len(source_hashes),
          '; retained archive/index guards:', len(result.get('inputGuards', {})))
    return result
