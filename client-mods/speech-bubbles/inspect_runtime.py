"""Read-only inspection of loaded native extension and network-state code."""
import argparse,ctypes as C,struct
from pathlib import Path
from capstone import Cs,CS_ARCH_X86,CS_MODE_64

def main():
 p=argparse.ArgumentParser();p.add_argument('pid',type=int);p.add_argument('--summary',action='store_true');a=p.parse_args()
 k=C.WinDLL('kernel32',use_last_error=True);ps=C.WinDLL('psapi',use_last_error=True)
 k.OpenProcess.argtypes=[C.c_ulong,C.c_int,C.c_ulong];k.OpenProcess.restype=C.c_void_p
 k.ReadProcessMemory.argtypes=[C.c_void_p,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)]
 k.CloseHandle.argtypes=[C.c_void_p]
 ps.EnumProcessModulesEx.argtypes=[C.c_void_p,C.POINTER(C.c_void_p),C.c_ulong,C.POINTER(C.c_ulong),C.c_ulong]
 ps.GetModuleFileNameExW.argtypes=[C.c_void_p,C.c_void_p,C.c_wchar_p,C.c_ulong]
 h=k.OpenProcess(0x410,False,a.pid)
 if not h:raise C.WinError(C.get_last_error())
 def read(address,size):
  buf=C.create_string_buffer(size);done=C.c_size_t()
  if not k.ReadProcessMemory(h,address,buf,size,C.byref(done)):raise C.WinError(C.get_last_error())
  return buf.raw[:done.value]
 def qword(address):return struct.unpack('<Q',read(address,8))[0]
 def dump(address,size):
  for i in Cs(CS_ARCH_X86,CS_MODE_64).disasm(read(address,size),address):
   print(f'{i.address:x}: {i.bytes.hex():26} {i.mnemonic} {i.op_str}')
 try:
  modules=(C.c_void_p*1024)();needed=C.c_ulong();assert ps.EnumProcessModulesEx(h,modules,C.sizeof(modules),C.byref(needed),3)
  selected={}
  for base in modules[:needed.value//8]:
   path=C.create_unicode_buffer(32768);ps.GetModuleFileNameExW(h,base,path,32768)
   name=Path(path.value).name.lower()
   if name in ('game.dll','aionspeechbubbles.dll'):selected[name]=(base,Path(path.value));print(name,hex(base),path.value)
  base,path=selected['game.dll'];network=qword(base+0x13122f0);print('Network object:',hex(network))
  if network:
   if not a.summary:
    fn=qword(qword(network)+8);print('Native connection state function:');dump(fn,80)
   print('Replay CVar value:',struct.unpack('<i',read(qword(network+0xa8),4))[0])
  print('Native game socket:',hex(qword(base+0x133bb30)),'send enabled:',read(base+0x133bb38,1).hex())
  print('Native chat own ID:',struct.unpack('<I',read(base+0x130da68,4))[0])
  base,path=selected['aionspeechbubbles.dll'];data=path.read_bytes();pe=struct.unpack_from('<I',data,60)[0];opt=pe+24;table=opt+struct.unpack_from('<H',data,pe+20)[0]
  sections=[struct.unpack_from('<8sIIIIIIHHI',data,table+i*40) for i in range(struct.unpack_from('<H',data,pe+6)[0])]
  def offset(r):
   for s in sections:
    if s[2]<=r<s[2]+s[3]:return s[4]+r-s[2]
   raise ValueError(hex(r))
  ex=struct.unpack_from('<I',data,opt+112)[0];fields=struct.unpack_from('<IIHHIIIIIII',data,offset(ex));_,_,_,_,_,ordinal,functions,names,ft,nt,ot=fields
  for j in range(names):
   nr=struct.unpack_from('<I',data,offset(nt)+4*j)[0];no=offset(nr);name=data[no:data.index(0,no)].decode()
   index=struct.unpack_from('<H',data,offset(ot)+2*j)[0];r=struct.unpack_from('<I',data,offset(ft)+4*index)[0]
   if name=='AionSpeechDiagnostics':
    keys=['revision','menuClicks','selectionsSent','acknowledgements','skinCalls','lastSenderId','lastNativeSkin','lastChosenSkin','ownStyle','ownId']
    count=13 if struct.unpack('<I',read(base+r,4))[0]>=5 else 10
    keys+=['artworkDraws','missingArtwork','lastArtworkStyle'] if count==13 else []
    print('Speech diagnostics:',dict(zip(keys,struct.unpack('<'+str(count)+'I',read(base+r,count*4)))))
   elif not a.summary and name in ('AionSpeechTick','AionSpeechMenuCommand','AionSpeechMessage'):
    print(name,hex(r));dump(base+r,340)
 finally:k.CloseHandle(h)
if __name__=='__main__':main()
