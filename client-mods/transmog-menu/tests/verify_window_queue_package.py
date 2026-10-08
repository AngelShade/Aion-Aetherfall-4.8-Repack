"""Verify launcher/restore preservation on a disposable file-only client tree."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from window_queue_patch import SITE, ORIGINAL

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True)
    p.add_argument('--fixture',type=Path)
    a=p.parse_args();package=a.package.resolve();m=json.loads((package/'manifest.json').read_text());client=Path(m['clientRoot'])
    fixture=a.fixture.resolve() if a.fixture else package.parent/(package.name+'-restore-fixture')
    if fixture.parent!=package.parent or not fixture.name.endswith('-restore-fixture'):raise ValueError('Fixture must be external staging sibling')
    if fixture.exists():raise ValueError('Use fresh fixture path')
    fixture.mkdir()
    for e in m['files']:
        dest=fixture/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(package/e['path'],dest)
        assert sha(dest)==e['staged']
    for name in ['Aion Start.bat','bin64/AionGraphicsMenu.dll','bin64/XRenderD3D9.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak']:
        dest=fixture/name
        if not dest.exists():dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(client/name,dest)
    state_path=fixture/'DXVK/graphics-menu/installed.json';state=json.loads(state_path.read_text())
    state['clientRoot']=str(fixture);state['backupRoot']=str(fixture/'DXVK-backups'/m['graphicsCompatibility']['backupName'])
    state_path.write_text(json.dumps(state))
    for e in state['files']:
        name='DXVK/graphics-menu/package/'+e['path'];dest=fixture/name
        if not dest.exists():dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(client/name,dest)
    dx_path=fixture/'DXVK/installed.json';dx=json.loads(dx_path.read_text());dx['clientRoot']=str(fixture)
    record=next(e for e in dx['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
    record['backupPath']=str(Path(state['backupRoot'])/'cursor-base/bin64/Game.dll');dx_path.write_text(json.dumps(dx))
    wrapper=fixture/'run-fixture.ps1'
    wrapper.write_text('''param([string]$ScriptPath,[string]$ClientPath,[switch]$VerifyOnly)
$ErrorActionPreference='Stop'
$fixtureRoot=(Resolve-Path -LiteralPath $ClientPath).Path
if(-not $fixtureRoot.EndsWith('-restore-fixture')){throw 'Expected isolated fixture'}
function Get-Process {
 param([string[]]$Name,[string]$ErrorAction)
 Microsoft.PowerShell.Management\\Get-Process -Name $Name -ErrorAction SilentlyContinue |
 Where-Object { $_.Path -and $_.Path.StartsWith($fixtureRoot+'\\',[StringComparison]::OrdinalIgnoreCase) }
}
if($VerifyOnly){ & $ScriptPath -ClientPath $fixtureRoot -VerifyOnly }
else { & $ScriptPath -ClientPath $fixtureRoot }
''')
    env=dict(os.environ);env['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    scripts=Path(__file__).resolve().parents[2]/'dxvk'
    def run(name,verify=False,success=True):
        cmd=['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(wrapper),'-ScriptPath',str(scripts/name),'-ClientPath',str(fixture)]
        if verify:cmd.append('-VerifyOnly')
        result=subprocess.run(cmd,capture_output=True,text=True,env=env)
        if (result.returncode==0)!=success:raise AssertionError((result.stdout,result.stderr))
        return result
    game=fixture/'bin64/Game.dll';before=game.read_bytes()
    run('ApplyGraphicsMenu.ps1',True);assert game.read_bytes()==before
    damaged=bytearray(before);damaged[SITE]^=1;game.write_bytes(damaged)
    rejected=run('ApplyGraphicsMenu.ps1',True,False)
    assert 'Later client changes detected' in rejected.stderr
    run('RestoreGraphicsMenu.ps1',success=False);assert game.read_bytes()==damaged
    game.write_bytes(before);run('RestoreGraphicsMenu.ps1')
    restored=(Path(state['backupRoot'])/'bin64/Game.dll').read_bytes()
    assert game.read_bytes()==restored
    assert restored[SITE:SITE+7]!=ORIGINAL
    cursor=Path(record['backupPath']).read_bytes();assert cursor[SITE:SITE+7]==before[SITE:SITE+7]
    if m['feature'] in ('remember-login-return-v2','remember-login-reconnect-v3'):
        sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'remember-login'))
        import patch_reset
        from patch_binary import layout,offset
        _,_,_,_,sections=layout(before)
        hook=m['hook']
        hooks=[hook,hook,hook]
        if m['feature']=='remember-login-reconnect-v3':
            restore_hooks=m['graphicsCompatibility']['resetRestoreHooks']
            hooks=[hook,restore_hooks['graphics'],restore_hooks['cursor']]
        for data,hook in zip([before,restored,cursor],hooks):
            sections=layout(data)[-1];expected=patch_reset.code(hook['iat'],hook['hook'])
            at=offset(sections,hook['site']);entry=data[at:at+len(patch_reset.ORIGINAL)]
            import struct
            assert entry==b'\xe9'+struct.pack('<i',hook['hook']-hook['site']-5)+b'\x90'
            at=offset(sections,hook['hook']);assert data[at:at+len(expected)]==expected
    for e in m['preservedFiles']:assert sha(client/e['path'])==e['sha256'],e['path']
    print('OK: launcher accepts staged hashes, rejects later edits, and graphics/cursor restore baselines preserve native repairs; real client untouched')

if __name__=='__main__':main()
