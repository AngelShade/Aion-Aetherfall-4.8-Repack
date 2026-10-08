"""Archive then remove only DAO-verified migrated bot preference files; no service lifecycle changes."""
import argparse,json,subprocess,shutil,re,ctypes,hashlib
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from stage_companion_update import ROOT,DEV_ROOT,sha,validate_output
from remove_follow_recovery_offline import stopped

@contextmanager
def unused_files(directory,rows):
 # Deny readers and writers throughout retirement, while allowing our own deletion.
 # A pre-existing read/write handle makes CreateFileW fail: no files are removed.
 from ctypes import wintypes
 api=ctypes.WinDLL('kernel32',use_last_error=True)
 api.CreateFileW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,wintypes.DWORD,ctypes.c_void_p,wintypes.DWORD,wintypes.DWORD,wintypes.HANDLE]
 api.CreateFileW.restype=wintypes.HANDLE
 api.ReadFile.argtypes=[wintypes.HANDLE,ctypes.c_void_p,wintypes.DWORD,ctypes.POINTER(wintypes.DWORD),ctypes.c_void_p]
 api.ReadFile.restype=wintypes.BOOL
 api.CloseHandle.argtypes=[wintypes.HANDLE];api.CloseHandle.restype=wintypes.BOOL
 handles=[]
 try:
  for row in rows:
   path=(directory/row['path']).resolve();assert path.parent==directory
   handle=api.CreateFileW(str(path),0x80000000,4,None,3,0,None)
   if handle==ctypes.c_void_p(-1).value:raise ctypes.WinError(ctypes.get_last_error())
   handles.append(handle);digest=hashlib.sha256();buffer=ctypes.create_string_buffer(65536)
   while True:
    read=wintypes.DWORD()
    if not api.ReadFile(handle,buffer,len(buffer),ctypes.byref(read),None):raise ctypes.WinError(ctypes.get_last_error())
    if not read.value:break
    digest.update(buffer.raw[:read.value])
   assert digest.hexdigest()==row['sha256'],'Legacy settings changed; no removal'
  yield
 finally:
  for handle in handles:api.CloseHandle(handle)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--verify-only',action='store_true');p.add_argument('--authorized-unused-live-files',action='store_true',help='Use only with explicit one-time user authorization; exclusive file-use guards still required');a=p.parse_args()
 server=ROOT/'target-deploy/game-server';directory=(server/'config/playerbots').resolve()
 def lifecycle_guard():
  if not a.authorized_unused_live_files:stopped();return
  # Still inspect successfully; this exception never starts/stops/attaches services.
  subprocess.run(['pwsh','-NoProfile','-Command',"$ErrorActionPreference='Stop'; $p=Get-CimInstance Win32_Process; Write-Output ('Inspected processes='+$p.Count)"],check=True,capture_output=True,text=True)
 lifecycle_guard()
 diagnostics=DEV_ROOT/'diagnostics/playerbots-metadata-retirement';validate_output(diagnostics);diagnostics.mkdir(parents=True,exist_ok=True)
 before={str(f.relative_to(directory)):sha(f) for f in directory.rglob('*') if f.is_file()}
 source=ROOT/'client-mods/playerbots/tooling/PlayerBotMetadataRetirement.java'
 cp=str(server/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/*')
 subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(diagnostics),str(source)],check=True)
 report=diagnostics/'retirement-preflight.txt'
 subprocess.run(['java','-Xverify:all','-cp',str(diagnostics)+';'+cp,'PlayerBotMetadataRetirement',str(server),str(report)],check=True)
 rows=[]
 for line in report.read_text().splitlines():
  name,digest,revision,equal=line.split('|');assert re.fullmatch(r'(?:(care|gear|behavior|formation|spacing|party-rewards)-)?character-[0-9]+\.properties',name)
  path=(directory/name).resolve();assert path.parent==directory and sha(path)==digest
  rows.append(dict(path=name,sha256=digest,revision=int(revision),equalLegacy=equal=='true'))
 assert {r['path'] for r in rows}=={f.name for f in directory.iterdir() if re.fullmatch(r'(?:(care|gear|behavior|formation|spacing|party-rewards)-)?character-[0-9]+\.properties',f.name)}
 if a.verify_only:print('OK: retirement ready; no files removed');return
 lifecycle_guard()
 backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-legacy-settings-cleanup-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
 validate_output(backup);backup.mkdir(parents=True)
 jarsha=sha(server/'libs/playerbot-recruitment-fix.jar')
 for row in rows:
  dest=backup/row['path'];shutil.copy2(directory/row['path'],dest);assert sha(dest)==row['sha256']
 shutil.copy2(report,backup/'database-preflight.txt')
 manifest=dict(scope='PB-SCOPE-012A legacy file retirement',createdAt=datetime.now().isoformat(),deployment=str(server),files=rows,overrideSha256=jarsha,settingsBefore=before,oneTimeUnusedLiveFilesAuthorization=a.authorized_unused_live_files)
 (backup/'prepared.json').write_text(json.dumps(manifest,indent=2))
 lifecycle_guard()
 assert before=={str(f.relative_to(directory)):sha(f) for f in directory.rglob('*') if f.is_file()},'Settings changed during preflight; no deletion'
 try:
  with unused_files(directory,rows):
   for row in rows:
    target=(directory/row['path']).resolve();assert target.parent==directory;target.unlink()
  expected={k:v for k,v in before.items() if k not in {row['path'] for row in rows}}
  assert expected=={str(f.relative_to(directory)):sha(f) for f in directory.rglob('*') if f.is_file()}
  assert sha(server/'libs/playerbot-recruitment-fix.jar')==jarsha
 except Exception:
  for row in rows:
   target=directory/row['path']
   if not target.exists():shutil.copy2(backup/row['path'],target)
  raise
 (backup/'manifest.json').write_text(json.dumps(manifest,indent=2))
 (backup/'installed.json').write_text(json.dumps(dict(removed=len(rows),remainingSettingsMediaFiles=len(expected),mode='explicit one-time unused-file exception; database read-only' if a.authorized_unused_live_files else 'offline files only; database read-only'),indent=2))
 print('OK: retired',len(rows),'migrated legacy bot settings files; other settings/media/JAR unchanged; archive:',backup)
if __name__=='__main__':main()
