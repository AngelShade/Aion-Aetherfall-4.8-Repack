"""Exercise graphics launch/restore guards against a staged incremental repair."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from patch_game_dll import BROWSER_HOOK_RVA, MARKET_AUTH_HOOK_RVA, build_browser_hook_code, build_market_auth_code, MARKET_RECT_HOOK_RVA, build_market_rect_code


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package',type=Path,required=True)
    parser.add_argument('--client',type=Path,required=True)
    args=parser.parse_args()
    package=args.package.resolve();client=args.client.resolve()
    manifest=json.loads((package/'manifest.json').read_text())
    fixture=package.parent/(package.name+'-fixture');fixture.mkdir(exist_ok=True)
    for entry in manifest['files']:
        dest=fixture/entry['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(package/entry['path'],dest)
    for relative in ['bin64/Game.dll','bin64/AionGraphicsMenu.dll','bin64/XRenderD3D9.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak','Aion Start.bat']:
        if (fixture/relative).exists():continue
        dest=fixture/relative;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(client/relative,dest)
    state_path=fixture/'DXVK/graphics-menu/installed.json'
    state=json.loads(state_path.read_text());state['clientRoot']=str(fixture)
    for entry in state['files']:
        relative='DXVK/graphics-menu/package/'+entry['path']
        dest=fixture/relative
        if not dest.exists():
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(client/relative,dest)
    state['backupRoot']=str(fixture/'DXVK-backups'/manifest['graphicsCompatibility']['backupName'])
    state_path.write_text(json.dumps(state))
    dxvk_path=fixture/'DXVK/installed.json';dxvk=json.loads(dxvk_path.read_text());dxvk['clientRoot']=str(fixture)
    game_record=next(e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
    game_record['backupPath']=str(Path(state['backupRoot'])/'cursor-base/bin64/Game.dll')
    dxvk_path.write_text(json.dumps(dxvk))
    scripts=Path(__file__).resolve().parents[2]/'dxvk'
    environment=dict(os.environ)
    environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    # The fixture is a synthetic client tree. Scope the legacy global process
    # guard to it so a real Aion session cannot block isolated restore checks.
    # No production script or real process is modified.
    wrapper=fixture/'run-fixture.ps1'
    wrapper.write_text('''param([string]$ScriptPath,[string]$ClientPath,[switch]$VerifyOnly)
$ErrorActionPreference='Stop'
$fixtureRoot=(Resolve-Path -LiteralPath $ClientPath).Path
if(-not $fixtureRoot.EndsWith('-fixture')){throw 'Expected isolated fixture client.'}
function Get-Process {
 param([string[]]$Name,[string]$ErrorAction)
 Microsoft.PowerShell.Management\\Get-Process -Name $Name -ErrorAction SilentlyContinue |
  Where-Object { $_.Path -and $_.Path.StartsWith($fixtureRoot+'\\',[StringComparison]::OrdinalIgnoreCase) }
}
if($VerifyOnly){ & $ScriptPath -ClientPath $fixtureRoot -VerifyOnly }
else { & $ScriptPath -ClientPath $fixtureRoot }
''',encoding='utf-8')

    def run(script,*extra,success=True):
        result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(wrapper),'-ScriptPath',str(scripts/script),'-ClientPath',str(fixture),*extra],capture_output=True,text=True,env=environment)
        assert (result.returncode==0)==success,(result.stdout,result.stderr)
        return result

    game=fixture/'bin64/Game.dll';original=game.read_bytes()
    for _ in range(2):
        run('ApplyGraphicsMenu.ps1','-VerifyOnly')
    assert game.read_bytes()==original
    damaged=bytearray(original);damaged[BROWSER_HOOK_RVA]^=1;game.write_bytes(damaged)
    result=run('ApplyGraphicsMenu.ps1','-VerifyOnly',success=False)
    assert 'Later client changes detected' in result.stderr and game.read_bytes()==damaged
    run('RestoreGraphicsMenu.ps1',success=False)
    game.write_bytes(original)
    run('RestoreGraphicsMenu.ps1')
    expected=(Path(state['backupRoot'])/'bin64/Game.dll').read_bytes()
    assert game.read_bytes()==expected
    routes=['http://127.0.0.1:8091/shop','http://127.0.0.1:8091/market','http://127.0.0.1:8091/market/wardrobe','http://127.0.0.1:8091/journey']
    if manifest.get('compactBrowserTitles'):
        routes.append('http://127.0.0.1:8091/market/pass')
    for offset,code in [(BROWSER_HOOK_RVA,build_browser_hook_code(routes)),(MARKET_AUTH_HOOK_RVA,build_market_auth_code(routes[1:],compact=manifest.get('compactBrowserTitles',False))),(MARKET_RECT_HOOK_RVA,build_market_rect_code())]:
        assert expected[offset:offset+len(code)]==code,'Graphics removal lost the Poeta route'
        cursor=(Path(state['backupRoot'])/'cursor-base/bin64/Game.dll').read_bytes()
        assert cursor[offset:offset+len(code)]==code,'DXVK removal would lose the Poeta route'
    assert expected[0x551c96:0x551c98]==b'\x90\x90' and cursor[0x551c96:0x551c98]==b'\x78\x18'
    record=json.loads(dxvk_path.read_text(encoding='utf-8-sig'))
    current=next(e for e in record['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
    assert current['installed']==hashlib.sha256(expected).hexdigest()
    for entry in state['files']:
        if entry['original'] is not None:
            assert hashlib.sha256((fixture/entry['path']).read_bytes()).hexdigest()==entry['original']
    if manifest.get('nativeWindowMovement'):
        from graphics_compat import read_pak,binary_xml
        from movable_windows import WINDOWS
        for relative,prefix in [('Data/ui/game/game.pak',''),('L10N/enu/Data/data.pak','ui/game/')]:
            with read_pak(fixture/relative) as archive:
                for name in WINDOWS:
                    root=binary_xml(archive.read(prefix+name))
                    assert not root.get('align_type') and 'movable' in root.get('flag').split(';')
        print('PASS: native window movement survives graphics removal')
    print('PASS: repeated graphics launch verification, unexpected DLL rejection, byte-exact graphics restore, cursor tracking and Poeta routes survive both restore baselines')


if __name__=='__main__':
    main()
