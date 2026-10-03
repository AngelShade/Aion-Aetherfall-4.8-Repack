"""Verify narrow binary bounds, recovery copies and execute the actual native observers."""
import ctypes as C,json,struct,sys
from pathlib import Path
from prepare import sha,read
from patch_return import patch,SITES
from patch_binary import layout,offset
from loader_permissions import writable_iats

def main(out):
 m=read(out/'manifest.json');root=Path(m['clientRoot']);assert m['revision']=='return-1'
 for e in m['files']:
  assert sha((out/e['path']).read_bytes())==e['installed'],e['path']
  assert sha((root/e['path']).read_bytes())==e['original'],e['path']
 for e in m['preservedFiles']:assert sha((root/e['path']).read_bytes())==e['sha256'],e['path']
 before=(root/'bin64/Game.dll').read_bytes();after=(out/'bin64/Game.dll').read_bytes();assert patch(before)[0]==after and writable_iats(after)[0]==after
 pe,opt,count,table,ss=layout(before);_,_,_,_,ns=layout(after);normalized=bytearray(after[:len(before)])
 allowed=[(pe+6,2),(opt+4,4),(opt+56,4),(opt+64,4),(opt+120,8),(table+count*40,40)]
 allowed +=[(table+i*40+36,4) for i in range(count)]+[(offset(ss,a),len(b)) for a,b,_ in SITES]
 for p,n in allowed:normalized[p:p+n]=before[p:p+n]
 assert normalized==before,'Unexpected bytes outside header and two observer sites'
 for state in ['DXVK/graphics-menu/installed.json','DXVK/graphics-menu/package/manifest.json']:
  for e in read(out/state)['files']:
   p=out/e['path'] if (out/e['path']).exists() else root/e['path'];assert sha(p.read_bytes())==e['installed']
 g=read(out/'DXVK/graphics-menu/installed.json')
 for e in g['files']:
  p=Path(g['backupRoot'])/e['path'];rel=p.relative_to(root);p=out/rel if (out/rel).exists() else p;assert (sha(p.read_bytes()) if e['original'] is not None else None)==e['original']
 for e in read(out/'DXVK/installed.json')['nativeCursorPatch']['files']:
  p=Path(e['backupPath']);rel=p.relative_to(root);p=out/rel if (out/rel).exists() else p;assert (sha(p.read_bytes()) if e['original'] is not None else None)==e['original']
 # Execute at a different mapping address: branches and imports must remain relative.
 k=C.WinDLL('kernel32');k.VirtualAlloc.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong,C.c_ulong];k.VirtualAlloc.restype=C.c_void_p;k.VirtualFree.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong]
 size=struct.unpack_from('<I',after,opt+56)[0]+4096;mem=k.VirtualAlloc(None,size,0x3000,0x40);assert mem
 F=C.CFUNCTYPE(C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t);events=[]
 callbacks=[F(lambda a,b,c,d:events.append((0,a,b,c,d)) or 999),F(lambda a,b,c,d:events.append((1,a,b,c,d)) or 999)]
 own=next(s for s in ns if s[0].rstrip(b'\0')==b'.rreturn')
 try:
  C.memmove(mem+own[2],after[own[4]:own[4]+own[3]],own[3])
  for i,h in enumerate(m['hooks']):
   C.c_size_t.from_address(mem+h['iat']).value=C.cast(callbacks[i],C.c_void_p).value
   original=bytes.fromhex(h['original']);pos=offset(ns,h['site']);C.memmove(mem+h['site'],after[pos:pos+len(original)],len(original))
   tail=bytes.fromhex('488bc14881c4e8000000c3') if i==0 else bytes.fromhex('4883c428c3')
   C.memmove(mem+h['site']+len(original),tail,len(tail))
  assert F(mem+m['hooks'][0]['site'])(77,88,99,101)==77 and events[-1]==(0,77,88,99,101)
  obj=C.create_string_buffer(0x100);address=C.addressof(obj);C.c_uint64.from_address(address+0x30).value=1
  assert F(mem+m['hooks'][1]['site'])(address,23,99,101)==1 and events[-1]==(1,address,23,99,101)
 finally:k.VirtualFree(mem,0,0x8000)
 print('PASS: staged hashes, all preserved files, exact binary bounds, recovery baselines and two relocated native trampolines with original-handler delegation')
if __name__=='__main__':main(Path(sys.argv[1]))
