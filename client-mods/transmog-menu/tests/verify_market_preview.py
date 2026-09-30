import ctypes,struct,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import patch_game_dll as p
k=ctypes.WinDLL('kernel32',use_last_error=True)
k.VirtualAlloc.argtypes=[ctypes.c_void_p,ctypes.c_size_t,ctypes.c_uint32,ctypes.c_uint32];k.VirtualAlloc.restype=ctypes.c_void_p
k.VirtualProtect.argtypes=[ctypes.c_void_p,ctypes.c_size_t,ctypes.c_uint32,ctypes.POINTER(ctypes.c_uint32)]
k.VirtualFree.argtypes=[ctypes.c_void_p,ctypes.c_size_t,ctypes.c_uint32]
base=k.VirtualAlloc(None,0x1450000,0x3000,4);assert base
present=None;calls=[]
@ctypes.CFUNCTYPE(ctypes.c_void_p,ctypes.c_void_p,ctypes.c_char_p,ctypes.c_uint32)
def lookup(dialog,name,kind):
 calls.append((name,kind));return 0x1234 if name==present else 0
def put(off,code):ctypes.memmove(base+off,code,len(code))
try:
 put(p.PREVIEW_DOCK_HOOK_RVA,p.build_preview_dock_code())
 epilogue=b'\x48\x83\xc4\x20\x5f\xc3'
 put(p.PREVIEW_DOCK_POSITION_RVA,b'\xb8\x01\0\0\0'+epilogue);put(p.PREVIEW_DOCK_SKIP_RVA,b'\xb8\x02\0\0\0'+epilogue)
 # Reproduce a jump from the native caller with its free ABI shadow space.
 wrapper=0x1000;code=b'\x57\x48\x83\xec\x20\x48\x89\xcf\x89\xd0';code+=b'\xe9'+struct.pack('<i',p.PREVIEW_DOCK_HOOK_RVA-wrapper-len(code)-5);put(wrapper,code)
 table=ctypes.create_string_buffer(0x340);dialog=ctypes.create_string_buffer(0x70)
 struct.pack_into('<Q',table,0x338,ctypes.cast(lookup,ctypes.c_void_p).value);struct.pack_into('<Q',dialog,0,ctypes.addressof(table))
 old=ctypes.c_uint32();assert k.VirtualProtect(base,0x1450000,0x20,ctypes.byref(old))
 fn=ctypes.CFUNCTYPE(ctypes.c_int,ctypes.c_void_p,ctypes.c_uint32)(base+wrapper)
 for did in (0,0x20d,0x222,0x1000):calls.clear();assert fn(dialog,did)==2 and not calls
 calls.clear();assert fn(dialog,0x16b)==1 and not calls
 for did in (0x20e,0x21a,0x221):
  for present in (None,b'PrivateCashShopBrowser',b'PrivateWarehouseBrowser',b'OtherBrowser'):
   calls.clear();assert fn(dialog,did)==(1 if present in (b'PrivateCashShopBrowser',b'PrivateWarehouseBrowser') else 2)
   assert calls[0]==(b'PrivateCashShopBrowser',0x2027)
   if present!=b'PrivateCashShopBrowser':assert calls[1]==(b'PrivateWarehouseBrowser',0x2027)
 print('PASS: native preview docks for Cash Shop and Warehouse; original dialogs and addon boundaries preserve native behavior.')
finally:k.VirtualFree(base,0,0x8000)
