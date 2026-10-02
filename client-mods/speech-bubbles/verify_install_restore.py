"""Install/restore the composed speech package in an isolated client fixture."""
import argparse,hashlib,json,os,shutil,subprocess,time
from pathlib import Path
from graphics_compatibility import read

def main():
 p=argparse.ArgumentParser();p.add_argument('package',type=Path);p.add_argument('--backup',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();backup=a.backup.resolve();manifest=read(package/'manifest.json');client=Path(manifest['clientRoot'])
 fixture=package.parent/('speech-cycle-'+str(time.time_ns()));prepared=fixture.parent/(fixture.name+'-package');fixture.mkdir();prepared.mkdir()
 def copy(source,target):target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
 for e in manifest['files']:
  copy(package/e['path'],prepared/e['path'])
  if e['original'] is not None:copy(backup/e['path'],fixture/e['path'])
 for rel in ['bin64/AionGraphicsMenu.dll','DXVK/graphics-menu/package/bin64/AionGraphicsMenu.dll','bin64/XRenderD3D9.dll','Aion Start.bat','dxvk.conf',
  'DXVK/graphics-menu/ApplyGraphicsMenu.ps1','DXVK/cursor-fix/InstallNativeCursorPatch.ps1','DXVK/cursor-fix/RendererCommon.ps1']:
  copy(client/rel,fixture/rel)
 for directory in ['bin32','bin64']:
  for name in ['d3d9.dll','d3d9.dxvk-disabled.dll']:
   if(client/directory/name).exists():copy(client/directory/name,fixture/directory/name)
 def mapped(s):return str(fixture/Path(s).relative_to(client))
 # Both the pre-install and composed records must belong to the fixture.
 for base in [fixture,prepared]:
  state=read(base/'DXVK/graphics-menu/installed.json');state['clientRoot']=str(fixture);state['backupRoot']=mapped(state['backupRoot'])
  (base/'DXVK/graphics-menu/installed.json').write_text(json.dumps(state))
  dxvk=read(base/'DXVK/installed.json');dxvk['clientRoot']=str(fixture);dxvk['backupRoot']=mapped(dxvk['backupRoot'])
  for e in dxvk['nativeCursorPatch']['files']:e['backupPath']=mapped(e['backupPath'])
  (base/'DXVK/installed.json').write_text(json.dumps(dxvk))
 sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
 for e in manifest['files']:
  e['original']=sha(fixture/e['path']) if(fixture/e['path']).exists() else None;e['installed']=sha(prepared/e['path'])
 manifest['clientRoot']=str(fixture);(prepared/'manifest.json').write_text(json.dumps(manifest))
 before={e['path']:e['original'] for e in manifest['files']}
 environment=dict(os.environ);environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
 def run(script,*args):
  result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(script),*args],capture_output=True,text=True,env=environment)
  assert result.returncode==0,(result.stdout,result.stderr)
 scripts=Path(__file__).resolve().parent
 run(scripts/'install.ps1','-PreparedPath',str(prepared))
 run(fixture/'DXVK/graphics-menu/ApplyGraphicsMenu.ps1','-ClientPath',str(fixture),'-VerifyOnly')
 run(fixture/'DXVK/cursor-fix/InstallNativeCursorPatch.ps1','-ClientPath',str(fixture))
 installed_backup=next((fixture/'SpeechBubbles-backups').iterdir())
 run(scripts/'restore.ps1','-BackupPath',str(installed_backup))
 for rel,h in before.items():
  if h is None:assert not(fixture/rel).exists(),rel
  else:assert sha(fixture/rel)==h,rel
 run(fixture/'DXVK/graphics-menu/ApplyGraphicsMenu.ps1','-ClientPath',str(fixture),'-VerifyOnly')
 run(fixture/'DXVK/cursor-fix/InstallNativeCursorPatch.ps1','-ClientPath',str(fixture))
 print('PASS: composed speech installation and byte-exact speech removal; graphics/cursor launch checks pass in both states. Real client untouched.')
if __name__=='__main__':main()
