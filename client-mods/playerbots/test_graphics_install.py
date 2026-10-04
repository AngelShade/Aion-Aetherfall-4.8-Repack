"""Install companions in a disposable client and exercise startup/restore guards."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

import prepare


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--companion-backup',type=Path,required=True)
    parser.add_argument('--recovery-backup',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();client=args.client.resolve();output=args.output.resolve()
    assert not output.exists() and not output.is_relative_to(client)
    fixture=output/'client-fixture';fixture.mkdir(parents=True)
    companion=args.companion_backup.resolve();recovery=args.recovery_backup.resolve()
    receipt=json.loads((companion/'manifest.json').read_text())

    def copy(source,rel):
        target=fixture/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)

    for rel in set(prepare.PRESERVED+['Addon.key','bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf']):
        if (client/rel).is_file():copy(client/rel,rel)
    for entry in receipt['files']:copy(companion/entry['path'],entry['path'])
    # The signer also verifies the stock signatures kept before addon signing.
    # Copy only tiny public keys/signatures, never account or model data.
    for saved in (client/'TransmogMenu-backups').iterdir():
        if (saved/'Pub.key').is_file():
            for rel in ['Pub.key','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig']:
                if (saved/rel).is_file():copy(saved/rel,saved.relative_to(client)/rel)
    for rel in ['DXVK/installed.json','DXVK/graphics-menu/installed.json','DXVK/graphics-menu/package/manifest.json']:
        copy(recovery/rel,rel)
    state_path=fixture/'DXVK/graphics-menu/installed.json';state=json.loads(state_path.read_text())
    old_backup=Path(state['backupRoot'])
    for entry in state['files']:
        rel=entry['path'];package='DXVK/graphics-menu/package/'+rel
        copy(recovery/package if (recovery/package).exists() else client/package,package)
        if entry['original'] is not None:copy(old_backup/rel,old_backup.relative_to(client)/rel)
    state['clientRoot']=str(fixture);state['backupRoot']=str(fixture/old_backup.relative_to(client));state_path.write_text(json.dumps(state))
    dxvk_path=fixture/'DXVK/installed.json';dxvk=json.loads(dxvk_path.read_text())
    cursor=next(e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
    old_cursor=Path(cursor['backupPath']);copy(old_cursor,old_cursor.relative_to(client))
    cursor['backupPath']=str(fixture/old_cursor.relative_to(client));dxvk['clientRoot']=str(fixture);dxvk_path.write_text(json.dumps(dxvk))
    for rel in ['DXVK/RendererCommon.ps1','DXVK/cursor-fix/InstallNativeCursorPatch.ps1','DXVK/graphics-menu/ApplyGraphicsMenu.ps1']:
        copy(client/rel,rel)
    package=output/'package'
    subprocess.run([sys.executable,str(prepare.HERE/'prepare.py'),'--client',str(fixture),'--output',str(package)],check=True)
    manifest=json.loads((package/'manifest.json').read_text());assert len(manifest['files'])==16
    environment=dict(os.environ);environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    wrapper=output/'install-fixture.ps1'
    wrapper.write_text('''param([string]$Installer,[string]$ClientPath,[string]$PreparedPath)
$ErrorActionPreference='Stop'
$fixtureRoot=(Resolve-Path -LiteralPath $ClientPath).Path
if(-not $fixtureRoot.EndsWith('-fixture')){throw 'Expected isolated fixture'}
function Get-Process {
 param([string[]]$Name,[string]$ErrorAction)
 Microsoft.PowerShell.Management\\Get-Process -Name $Name -ErrorAction SilentlyContinue |
  Where-Object { $_.Path -and $_.Path.StartsWith($fixtureRoot+'\\',[StringComparison]::OrdinalIgnoreCase) }
}
& $Installer -ClientPath $fixtureRoot -PreparedPath $PreparedPath
''')
    install=['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(wrapper),'-Installer',str(prepare.HERE/'Install.ps1'),'-ClientPath',str(fixture),'-PreparedPath',str(package)]
    def run(success):
        result=subprocess.run(install,capture_output=True,text=True,env=environment)
        assert (result.returncode==0)==success,(result.stdout,result.stderr)
        return result
    # Excluding recovery used to create the reported live startup failure.
    original=(package/'manifest.json').read_bytes();broken=dict(manifest);broken.pop('graphicsCompatibility')
    (package/'manifest.json').write_text(json.dumps(broken))
    failure=run(False);assert 'active graphics/cursor recovery' in failure.stdout
    assert not list((fixture/'TransmogMenu-backups').glob('playerbots-*'))
    (package/'manifest.json').write_bytes(original)
    # Force a failure after payload copying to check that both live files and
    # recovery tracking roll back together, including removal of new baselines.
    run(False)
    for entry in manifest['files']:
        target=fixture/entry['path']
        assert (prepare.sha(target.read_bytes()) if target.is_file() else None)==entry['original'],'incomplete rollback: '+entry['path']
    copy(client/'DXVK/cursor-fix/RendererCommon.ps1','DXVK/cursor-fix/RendererCommon.ps1')
    result=run(True);print(result.stdout)
    for entry in manifest['files']:assert prepare.sha((fixture/entry['path']).read_bytes())==entry['staged']
    # Reinstalling a stale input must refuse, even after a successful install.
    installed=(fixture/'bin64/Game.dll').read_bytes();run(False);assert (fixture/'bin64/Game.dll').read_bytes()==installed
    manifest['compactBrowserTitles']=True;manifest['companionRoutes']=True
    (package/'manifest.json').write_text(json.dumps(manifest))
    subprocess.run([sys.executable,str(prepare.HERE.parent/'transmog-menu/tests/verify_incremental_graphics.py'),
        '--package',str(package),'--client',str(fixture)],check=True)
    print('PASS: recovery omission refused before writes; post-copy failure rolls back all 16 files; full install passes startup; stale reinstall refused; graphics/cursor removal retains all service routes.')


if __name__=='__main__':main()
