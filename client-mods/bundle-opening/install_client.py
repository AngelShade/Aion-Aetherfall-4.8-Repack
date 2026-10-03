"""Install the tested bundle item data and matching icon index, with guarded rollback."""
import argparse,hashlib,json,os,shutil,subprocess,tempfile
from datetime import datetime
from pathlib import Path
from prepare_client import verify,sha
EXPECTED={'Data/Items/Items.pak','bin64/AionIconBridge.index'}
def running():
 result=subprocess.run(['tasklist','/FI','IMAGENAME eq aion.bin','/FO','CSV','/NH'],capture_output=True,text=True,check=True)
 return '"aion.bin"' in result.stdout.lower()
def replace(path,data):
 fd,name=tempfile.mkstemp(prefix='bundle-opening-',suffix='.tmp',dir=path.parent)
 try:
  with os.fdopen(fd,'wb') as stream:stream.write(data)
  os.replace(name,path)
 finally:
  if Path(name).exists():Path(name).unlink()
def installed(client,prepared):
 m=json.loads((prepared/'manifest.json').read_text());assert str(client.resolve())==m['clientRoot']
 assert {f['path'] for f in m['files']}==EXPECTED
 for f in m['files']:assert sha((client/f['path']).read_bytes())==f['staged'],f['path']
 for f in m['preserved']:assert sha((client/f['path']).read_bytes())==f['sha256'],f['path']
 index=(client/'bin64/AionIconBridge.index').read_bytes();archive=(client/'Data/Items/Items.pak').read_bytes()
 import struct
 assert struct.unpack_from('<Q',index,16)[0]==len(archive) and index[24:56]==hashlib.sha256(archive).digest()
 print('OK: bundle item data and native icon index installed; all',len(m['preserved']),'protected client files unchanged')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--prepared',type=Path,required=True);p.add_argument('--verify-installed',action='store_true');a=p.parse_args()
 if a.verify_installed:installed(a.client,a.prepared);return
 if running():raise RuntimeError('Close every Aion client normally before replacing Items.pak; the client holds the archive open')
 m=verify(a.client,a.prepared);assert {f['path'] for f in m['files']}==EXPECTED
 backup=a.client/'BundleOpening-backups'/datetime.now().strftime('%Y%m%d-%H%M%S-%f');backup.mkdir(parents=True,exist_ok=False)
 for f in m['files']:
  dest=backup/f['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(a.client/f['path'],dest);assert sha(dest.read_bytes())==f['original']
 (backup/'manifest.json').write_text(json.dumps(m,indent=2),encoding='utf-8');applied=[]
 try:
  if running():raise RuntimeError('Aion started during preparation; installation stopped')
  for f in m['files']:
   dest=a.client/f['path'];assert sha(dest.read_bytes())==f['original'];replace(dest,(a.prepared/f['path']).read_bytes());applied.append(f)
  installed(a.client,a.prepared)
 except Exception:
  for f in reversed(applied):
   dest=a.client/f['path']
   if sha(dest.read_bytes())==f['staged']:replace(dest,(backup/f['path']).read_bytes())
  raise
 (a.prepared/'installed-receipt.json').write_text(json.dumps(dict(backup=str(backup),files=m['files']),indent=2))
 print('Backup:',backup)
if __name__=='__main__':
 try:main()
 except Exception as error:print('FAIL:',error);raise
