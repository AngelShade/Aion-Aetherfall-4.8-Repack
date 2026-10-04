"""Fetch unchanged class references at the existing Playerbots pin; never follow HEAD."""
import hashlib
import urllib.request
from pathlib import Path

PIN='037c01418b5d01506917a3db9b44fd56ac5f965c'
FILES={
 'Mage':['MageTriggers.cpp','Strategy/GenericMageStrategy.cpp','Strategy/FrostMageStrategy.cpp','Strategy/FireMageStrategy.cpp'],
 'Rogue':['RogueTriggers.cpp','Strategy/GenericRogueStrategy.cpp','Strategy/AssassinationRogueStrategy.cpp'],
 'Hunter':['HunterTriggers.cpp','Strategy/GenericHunterStrategy.cpp','Strategy/MarksmanshipHunterStrategy.cpp'],
 'Warlock':['WarlockTriggers.cpp','Strategy/GenericWarlockStrategy.cpp','Strategy/AfflictionWarlockStrategy.cpp'],
 'Shaman':['ShamanTriggers.cpp','Strategy/GenericShamanStrategy.cpp','Strategy/EnhancementShamanStrategy.cpp'],
}
if __name__=='__main__':
 root=Path(__file__).resolve().parents[2]/'third-party/playerbots'
 sums=root/'SHA256SUMS';existing=sums.read_text();added=[]
 for cls,names in FILES.items():
  for name in names:
   path=f'src/Ai/Class/{cls}/{name}';local=root/'upstream'/path
   request=urllib.request.Request(f'https://raw.githubusercontent.com/mod-playerbots/mod-playerbots/{PIN}/{path}',headers={'User-Agent':'Aion-pinned-port-review'})
   data=urllib.request.urlopen(request,timeout=30).read()
   if local.exists():assert local.read_bytes()==data,f'Existing pinned reference differs: {path}'
   else:local.parent.mkdir(parents=True,exist_ok=True);local.write_bytes(data)
   relative=f'upstream/{path}';line=f'{hashlib.sha256(data).hexdigest()}  {relative}'
   if relative not in existing:added.append(line)
 if added:sums.write_text(existing.rstrip()+'\n'+'\n'.join(added)+'\n')
 print(f'OK: {sum(map(len,FILES.values()))} exact pinned class references; {len(added)} new hashes recorded')
