"""Fetch the exact existing Playerbots pin for quest destination selection; never follow HEAD."""
import hashlib
import urllib.request
from pathlib import Path
PIN='037c01418b5d01506917a3db9b44fd56ac5f965c'
FILES=['src/Ai/Base/Actions/ChooseTravelTargetAction.cpp','src/Ai/Base/Actions/ChooseTravelTargetAction.h']
if __name__=='__main__':
 root=Path(__file__).resolve().parents[2]/'third-party/playerbots'
 sums=root/'SHA256SUMS';existing=sums.read_text();added=[]
 for path in FILES:
  request=urllib.request.Request(f'https://raw.githubusercontent.com/mod-playerbots/mod-playerbots/{PIN}/{path}',headers={'User-Agent':'Aion-pinned-port-review'})
  data=urllib.request.urlopen(request,timeout=30).read();local=root/'upstream'/path
  if local.exists():assert local.read_bytes()==data,f'Existing reference differs: {path}'
  else:local.parent.mkdir(parents=True,exist_ok=True);local.write_bytes(data)
  relative='upstream/'+path
  if relative not in existing:added.append(hashlib.sha256(data).hexdigest()+'  '+relative)
 if added:sums.write_text(existing.rstrip()+'\n'+'\n'.join(added)+'\n')
 print(f'OK: {len(FILES)} exact pinned quest travel references; {len(added)} hashes added')
