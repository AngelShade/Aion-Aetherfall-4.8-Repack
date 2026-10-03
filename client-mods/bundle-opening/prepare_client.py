"""Remove bundle reuse fields from client data; preserve unrelated items, raw textures and installed DLLs."""
import argparse,copy,hashlib,io,json,struct,sys,zipfile,zlib,binascii
from pathlib import Path
from xml.etree import ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'expanded-warehouse'))
from codec import read_pak,binary_xml,encode_binary_xml,KEYS

LOCAL=bytes.fromhex('afb4fcfb');CENTRAL=bytes.fromhex('afb4fefd');END=bytes.fromhex('afb4faf9')
FIELDS=('use_delay','use_delay_type_id')
PROTECTED=('bin64/Game.dll','bin64/crysystem.dll','bin64/AionIconBridge.dll','bin64/AionRememberLogin.dll','Pub.key','Addon.key','Plugin/RelicCalc/RelicCalc.pak','Plugin/RelicCalc/RelicCalc.pak.sig','Data/ui/game/game.pak','L10N/enu/Data/data.pak','Data/func_pet/func_pet.pak','Data/func_pet/func_pet.pak.sig','bin32/bin32.pak','bin32/bin32.pak.sig')
def sha(data):return hashlib.sha256(data).hexdigest()
def parts(raw):
 local={};central={};pos=0;version=None
 while raw[pos:pos+4]==LOCAL:
  f=struct.unpack_from('<4sHHHHHIIIHH',raw,pos);flags,method,crc,size,plain,namesz,extrasz=f[2],f[3],f[6],f[7],f[8],f[9],f[10]
  assert not flags&8,'Data descriptors unsupported'
  name=raw[pos+30:pos+30+namesz].decode('utf-8' if flags&2048 else 'cp437');start=pos+30+namesz+extrasz;end=start+size
  assert name not in local
  local[name]=(raw[pos:end],start,pos)
  if version is None and size:
   for v,key in enumerate(KEYS,1):
    trial=bytearray(raw[start:end]);base=(size&31)*32 if v==1 else size&1023
    for i in range(min(32,size)):trial[i]^=key[base+i]
    try:decoded=zlib.decompress(trial,-15) if method==8 else trial
    except zlib.error:continue
    if len(decoded)==plain and binascii.crc32(decoded)==crc:version=v;break
   assert version is not None,'Unknown PAK version'
  pos=end
 while raw[pos:pos+4]==CENTRAL:
  f=struct.unpack_from('<4sHHHHHHIIIHHHHHII',raw,pos);size=46+f[10]+f[11]+f[12]
  name=raw[pos+46:pos+46+f[10]].decode('utf-8' if f[3]&2048 else 'cp437');central[name]=raw[pos:pos+size];pos+=size
 assert raw[pos:pos+4]==END and len(local)==len(central) and set(local)==set(central)
 assert pos+22+struct.unpack_from('<H',raw,pos+20)[0]==len(raw)
 return local,central,raw[pos:],version
def encrypted_zip(data,version):
 data=bytearray(data);pos=0;key=KEYS[version-1]
 while True:
  if data[pos:pos+4]==b'PK\x03\x04':
   f=struct.unpack_from('<4sHHHHHIIIHH',data,pos);size=f[7];start=pos+30+f[9]+f[10];base=(size&31)*32 if version==1 else size&1023
   assert not f[2]&8
   for i in range(min(32,size)):data[start+i]^=key[base+i]
   data[pos:pos+4]=LOCAL;pos=start+size
  elif data[pos:pos+4]==b'PK\x01\x02':
   f=struct.unpack_from('<4sHHHHHHIIIHHHHHII',data,pos);data[pos:pos+4]=CENTRAL;pos+=46+f[10]+f[11]+f[12]
  else:
   assert data[pos:pos+4]==b'PK\x05\x06';data[pos:pos+4]=END;break
 return bytes(data)
def rebuild(raw,replacements,infos):
 original,central,end,version=parts(raw);temp=io.BytesIO()
 with zipfile.ZipFile(temp,'w') as archive:
  for name,data in replacements.items():archive.writestr(copy.copy(infos[name]),data)
 replacements_raw,replacements_central,_,_=parts(encrypted_zip(temp.getvalue(),version))
 result=bytearray();offsets={};data_offsets={}
 for name,(record,start,offset) in original.items():
  replacement=replacements_raw.get(name,(record,start,offset));offsets[name]=len(result);data_offsets[start]=len(result)+replacement[1]-replacement[2];result+=replacement[0]
 central_start=len(result)
 for name,record in central.items():
  changed=bytearray(replacements_central.get(name,record));struct.pack_into('<I',changed,42,offsets[name]);result+=changed
 central_size=len(result)-central_start;end=bytearray(end);struct.pack_into('<II',end,12,central_size,central_start);result+=end
 return bytes(result),data_offsets
def index_for(index,old,new,offsets):
 result=bytearray(index);magic,items,textures,length,digest=struct.unpack_from('<8sIIQ32s',index)
 assert magic==b'AICON002' and length==len(old) and digest==hashlib.sha256(old).digest(),'Installed native icon index does not match current item archive'
 assert len(index)==56+items*8+textures*56
 struct.pack_into('<Q32s',result,16,len(new),hashlib.sha256(new).digest())
 for i in range(textures):
  pos=56+items*8+i*56;previous=struct.unpack_from('<I',index,pos)[0];assert previous in offsets
  struct.pack_into('<I',result,pos,offsets[previous])
  # Every indexed texture payload, key, sprite size and CRC remains untouched.
  size=struct.unpack_from('<I',index,pos+4)[0];assert old[previous:previous+size]==new[offsets[previous]:offsets[previous]+size]
 return bytes(result),dict(items=items,textures=textures)
def bundles(server_items):
 result={}
 for _,node in ET.iterparse(server_items,events=['end']):
  if node.tag!='item_template':continue
  if node.find('actions/decompose') is not None:result[node.get('id')]=node.get('name')
  node.clear()
 return result
def verify(client,output):
 m=json.loads((output/'manifest.json').read_text());assert str(client.resolve())==m['clientRoot']
 for f in m['files']:
  assert sha((client/f['path']).read_bytes())==f['original'],'Live file changed: '+f['path']
  assert sha((output/f['path']).read_bytes())==f['staged'],'Staged file changed: '+f['path']
 for f in m['preserved']:
  assert sha((client/f['path']).read_bytes())==f['sha256'],'Preserved file changed: '+f['path']
 old=(client/'Data/Items/Items.pak').read_bytes();new=(output/'Data/Items/Items.pak').read_bytes();ol,oc,oe,ov=parts(old);nl,nc,ne,nv=parts(new)
 assert list(ol)==list(nl) and list(oc)==list(nc) and ov==nv
 allowed=set(m['changedXml']);checks=0
 for name in ol:
  if name not in allowed:assert ol[name][0]==nl[name][0],name;checks+=1
  # Central metadata can change only for replaced XML and rebased entry offsets.
  if name not in allowed:assert oc[name][:42]+oc[name][46:]==nc[name][:42]+nc[name][46:]
 with read_pak(client/'Data/Items/Items.pak') as a,read_pak(output/'Data/Items/Items.pak') as b:
  assert a.namelist()==b.namelist() and b.testzip() is None
  for name in allowed:
   before=binary_xml(a.read(name));after=binary_xml(b.read(name));assert len(before)==len(after)
   for x,y in zip(before,after):
    ident=x.findtext('id');assert ident==y.findtext('id')
    if y.findtext('disassembly_item')=='1':
     assert ident in m['bundleIds'] and all(y.find(field) is None for field in FIELDS),('Bundle type still has a reuse field',ident)
    if ident in m['bundleIds']:
     for field in FIELDS:
      child=x.find(field)
      if child is not None:x.remove(child)
     assert y.find('use_delay') is None and y.find('use_delay_type_id') is None
    assert ET.tostring(x)==ET.tostring(y),(name,ident);checks+=1
   if name=='client_items_misc.xml':
    bag=next(x for x in after if x.findtext('id')=='188052490')
    assert bag.find('use_delay') is None and bag.findtext('casting_delay')=='1000'
 data,indexstats=index_for((client/'bin64/AionIconBridge.index').read_bytes(),old,new,{v[1]:nl[k][1] for k,v in ol.items()})
 assert data==(output/'bin64/AionIconBridge.index').read_bytes()
 print('OK:',checks,'archive/item preservation checks;',len(m['changedItems']),'bundle reuse entries removed;',indexstats['textures'],'native icons preserved; opening delays and all installed DLLs unchanged',flush=True)
 return m
def prepare(client,server_items,output):
 assert not output.exists(),'Use a fresh staging directory'
 assert client.resolve() not in output.resolve().parents,'Stage outside the client'
 ids=bundles(server_items);changed={};replacements={};found=set()
 with read_pak(client/'Data/Items/Items.pak') as archive:
  infos={e.filename:e for e in archive.infolist()}
  for name in archive.namelist():
   if not name.startswith('client_items_') or not name.endswith('.xml'):continue
   tree=binary_xml(archive.read(name));count=0
   for item in tree:
    ident=item.findtext('id')
    assert (ident in ids)==(item.findtext('disassembly_item')=='1'),('Server/client bundle type mismatch',name,ident)
    if ident not in ids:continue
    found.add(ident);fields={}
    for field in FIELDS:
     child=item.find(field)
     if child is not None:fields[field]=child.text;item.remove(child)
    if fields:changed[ident]=dict(name=ids[ident],original=fields);count+=1
   if count:replacements[name]=encode_binary_xml(tree)
   print(name+': '+str(count)+' bundle entries changed',flush=True)
 assert '188052490' in changed and changed['188052490']['original']['use_delay']=='5000'
 assert found==set(ids),'Some server bundle items were not found in the client tables'
 assert replacements
 original=(client/'Data/Items/Items.pak').read_bytes();patched,offsets=rebuild(original,replacements,infos)
 index,stats=index_for((client/'bin64/AionIconBridge.index').read_bytes(),original,patched,offsets)
 files=[]
 for name,data in {'Data/Items/Items.pak':patched,'bin64/AionIconBridge.index':index}.items():
  destination=output/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(data)
  files.append(dict(path=name,original=sha((client/name).read_bytes()),staged=sha(data)))
 m=dict(feature='bundle-opening-no-reuse-v1',clientRoot=str(client.resolve()),files=files,preserved=[dict(path=n,sha256=sha((client/n).read_bytes())) for n in PROTECTED if (client/n).is_file()],changedXml=sorted(replacements),bundleIds=sorted(found),changedItems=changed,nativeIcons=stats)
 (output/'manifest.json').write_text(json.dumps(m,indent=2),encoding='utf-8');verify(client,output)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--items',type=Path);p.add_argument('--verify-only',action='store_true');a=p.parse_args()
 try:
  if a.verify_only:verify(a.client,a.output)
  else:assert a.items is not None;prepare(a.client,a.items,a.output)
 except Exception as error:print('FAIL:',error,file=sys.stderr);raise
if __name__=='__main__':main()
