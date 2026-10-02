"""Read-only inspection of supported native menu and bubble objects."""
import argparse,ctypes as C,struct
from pathlib import Path

class MBI(C.Structure):
 _fields_=[('BaseAddress',C.c_void_p),('AllocationBase',C.c_void_p),('AllocationProtect',C.c_ulong),('PartitionId',C.c_ushort),('RegionSize',C.c_size_t),('State',C.c_ulong),('Protect',C.c_ulong),('Type',C.c_ulong)]

def main():
 p=argparse.ArgumentParser();p.add_argument('pid',type=int);p.add_argument('base',type=lambda v:int(v,16));p.add_argument('--manager-only',action='store_true');a=p.parse_args()
 k=C.WinDLL('kernel32',use_last_error=True)
 k.OpenProcess.argtypes=[C.c_ulong,C.c_int,C.c_ulong];k.OpenProcess.restype=C.c_void_p
 k.ReadProcessMemory.argtypes=[C.c_void_p,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)]
 k.VirtualQueryEx.argtypes=[C.c_void_p,C.c_void_p,C.POINTER(MBI),C.c_size_t];k.VirtualQueryEx.restype=C.c_size_t
 k.CloseHandle.argtypes=[C.c_void_p]
 h=k.OpenProcess(0x410,False,a.pid)
 if not h:raise C.WinError(C.get_last_error())
 def read(addr,size):
  buf=C.create_string_buffer(size);done=C.c_size_t()
  k.ReadProcessMemory(h,addr,buf,size,C.byref(done));return buf.raw[:done.value]
 def q(addr):return struct.unpack('<Q',read(addr,8))[0]
 def s(addr):return read(addr,64).split(b'\0')[0].decode('ascii',errors='replace')
 def widget(addr):
  return s(q(addr+0x10) if q(addr+0x28)>=16 else addr+0x10)
 patterns={'bubble':struct.pack('<Q',a.base+0xc42bb8),'checkbox':struct.pack('<Q',a.base+0xc413b8)}
 found={n:set() for n in patterns};addr=0;mbi=MBI()
 try:
  manager=q(a.base+0x1378e60);print('manager',hex(manager))
  if manager:
   print('templates',[hex(q(manager+i)) for i in range(0x48,0x78,8)])
   frame=q(manager+0x48)
   print('frame vtable',hex(q(frame)-a.base),'methods',[hex(q(q(frame)+i)-a.base) for i in range(0,0x40,8)])
   head=q(manager+8);node=q(head)
   for i in range(100):
    if node==head:break
    obj=q(node+0x10);print('live bubble',hex(obj),'vtable',hex(q(obj)-a.base),'type/id/expiry',struct.unpack('<III',read(obj+0x10,12)))
    node=q(node)
   print('list size',q(manager+0x10),'render tree size',q(manager+0x30))
   tree=q(manager+0x28)
   def walk(n,depth=0):
    if n==tree or depth>20:return
    walk(q(n),depth+1);obj=q(n+0x20);print('render bubble',hex(obj),struct.unpack('<II',read(obj+0x10,8)));walk(q(n+0x10),depth+1)
   walk(q(tree+8))
  if a.manager_only:return
  while k.VirtualQueryEx(h,addr,C.byref(mbi),C.sizeof(mbi)):
   region=mbi.BaseAddress or 0;end=region+mbi.RegionSize
   if mbi.State==0x1000 and mbi.Type==0x20000 and not mbi.Protect&0x101 and mbi.Protect&0xee:
    for start in range(region,end,2**20):
     data=read(start,min(2**20+7,end-start))
     for name,pattern in patterns.items():
      pos=data.find(pattern)
      while pos>=0:
       if (start+pos)%8==0:found[name].add(start+pos)
       pos=data.find(pattern,pos+1)
   if end<=addr:break
   addr=end
  for name,addresses in found.items():
   print(name,'count',len(addresses))
   for obj in sorted(addresses):
    try:
     if name=='bubble':print(hex(obj),'type/id/expiry=',struct.unpack('<III',read(obj+0x10,12)))
     elif widget(obj).startswith('speech_bubble'):
      parent=q(obj+0x290);print(hex(obj),widget(obj),'parent',hex(parent),'state',read(obj+0x38,8).hex(),'parent type',hex(q(parent)-a.base),'model',hex(q(parent+0x598)))
      model=q(parent+0x598);start=q(model+0x1b0);end=q(model+0x1b8)
      for entry in range(start,end,8):
       item=q(entry)
       if item:print(' model',s(item+4),'kind',struct.unpack('<i',read(item,4))[0],'enabled/check',read(item+0x19c,8).hex())
    except Exception as e:print(type(e).__name__,str(e))
 finally:k.CloseHandle(h)
if __name__=='__main__':main()
