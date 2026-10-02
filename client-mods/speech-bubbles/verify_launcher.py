"""Exercise actual graphics launch/restore scripts against an isolated fixture."""
import argparse,hashlib,json,os,shutil,subprocess,sys
from pathlib import Path
from graphics_compatibility import read,read_pak
from build_package import binary_xml

def main():
 p=argparse.ArgumentParser();p.add_argument('package',type=Path);p.add_argument('--client',type=Path,required=True);a=p.parse_args()
 package=a.package.resolve();client=a.client.resolve();manifest=read(package/'manifest.json')
 fixture=package.parent/(package.name+'-fixture');fixture.mkdir(exist_ok=True)
 def copy(source,relative):
  target=fixture/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
 for e in manifest['files']:copy(package/e['path'],e['path'])
 for rel in ['bin64/AionGraphicsMenu.dll','DXVK/graphics-menu/package/bin64/AionGraphicsMenu.dll','bin64/XRenderD3D9.dll','Aion Start.bat','dxvk.conf',
  'DXVK/graphics-menu/ApplyGraphicsMenu.ps1','DXVK/graphics-menu/RestoreGraphicsMenu.ps1',
  'DXVK/cursor-fix/InstallNativeCursorPatch.ps1','DXVK/cursor-fix/RendererCommon.ps1']:
  copy(client/rel,rel)
 for directory in ['bin32','bin64']:
  for name in ['d3d9.dll','d3d9.dxvk-disabled.dll']:
   if(client/directory/name).exists():copy(client/directory/name,directory+'/'+name)
 state=read(fixture/'DXVK/graphics-menu/installed.json');state['clientRoot']=str(fixture)
 state['backupRoot']=str(fixture/'DXVK-backups'/manifest['graphicsCompatibility']['backupName'])
 (fixture/'DXVK/graphics-menu/installed.json').write_text(json.dumps(state))
 dxvk=read(fixture/'DXVK/installed.json');dxvk['clientRoot']=str(fixture)
 game_record=next(e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
 game_record['backupPath']=str(Path(state['backupRoot'])/'cursor-base/bin64/Game.dll')
 (fixture/'DXVK/installed.json').write_text(json.dumps(dxvk))
 environment=dict(os.environ);environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
 def run(script,success=True,*options):
  result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(fixture/script),'-ClientPath',str(fixture),*options],capture_output=True,text=True,env=environment)
  assert(result.returncode==0)==success,(result.stdout,result.stderr)
  return result
 game=fixture/'bin64/Game.dll';original=game.read_bytes()
 for _ in range(2):run('DXVK/graphics-menu/ApplyGraphicsMenu.ps1',True,'-VerifyOnly')
 run('DXVK/graphics-menu/ApplyGraphicsMenu.ps1')
 run('DXVK/cursor-fix/InstallNativeCursorPatch.ps1')
 assert game.read_bytes()==original,'Launch verification overwrote speech bubbles'
 damaged=bytearray(original);damaged[0x6878d0]^=1;game.write_bytes(damaged)
 result=run('DXVK/graphics-menu/ApplyGraphicsMenu.ps1',False,'-VerifyOnly');assert 'Later client changes detected' in result.stderr
 run('DXVK/graphics-menu/RestoreGraphicsMenu.ps1',False);assert game.read_bytes()==damaged
 game.write_bytes(original);run('DXVK/graphics-menu/RestoreGraphicsMenu.ps1')
 expected=(Path(state['backupRoot'])/'bin64/Game.dll').read_bytes();assert game.read_bytes()==expected
 assert(fixture/'bin64/AionSpeechBubbles.dll').exists()
 assert expected[0x551c96:0x551c98]==b'\x90\x90'
 for rel,name in [('Data/ui/game/game.pak','chat_option_dialog.xml'),('L10N/enu/Data/data.pak','ui/game/chat_option_dialog.xml')]:
  archive=read_pak(fixture/rel)
  assert archive.read(name)==read_pak(package/rel).read(name),'Graphics removal changed Chat Options'
  assert binary_xml(archive.read(name)).find(".//Widget[@name='speech_scope']") is None,'obsolete speech panel remains'
  global_name='global_option_dialog.xml' if rel.startswith('Data/') else 'ui/game/global_option_dialog.xml'
  root=binary_xml(archive.read(global_name));assert root.find(".//Widget[@name='cb_use_vulkan']") is None
 run('DXVK/cursor-fix/InstallNativeCursorPatch.ps1')
 print('PASS: repeated launch checks and cursor checks; unknown changes rejected; Graphics removal retains native speech submenu and original Chat Options.')
 hooks=read(package/'graphics-baseline-hooks.json')
 for domain in ['graphics','cursor']:
  test=fixture/('native-'+domain);(test/'bin64').mkdir(parents=True,exist_ok=True)
  baseline=Path(state['backupRoot'])/('bin64/Game.dll' if domain=='graphics' else 'cursor-base/bin64/Game.dll')
  shutil.copy2(baseline,test/'bin64/Game.dll');shutil.copy2(package/'bin64/AionSpeechBubbles.dll',test/'bin64/AionSpeechBubbles.dll')
  (test/'manifest.json').write_text(json.dumps({'hooks':hooks[domain]}))
  result=subprocess.run([sys.executable,'-X','faulthandler',str(Path(__file__).with_name('verify_native.py')),str(test)],capture_output=True,text=True)
  assert result.returncode==0,(result.stdout,result.stderr)
  print('PASS:',domain,'restore baseline executes all speech hooks and switcher tests')
 print('Client was never written or launched by this test.')
if __name__=='__main__':main()
