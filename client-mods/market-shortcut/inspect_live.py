"""Read HUD diagnostic counters and addon command registration; never writes process memory."""
import ctypes as C
import json
import struct
import sys
from pathlib import Path

pid=int(sys.argv[1]);mods=json.loads(Path(sys.argv[2]).read_text(encoding='utf-8-sig'))
mods={m['ModuleName']:m['Base'] for m in mods}
k=C.WinDLL('kernel32',use_last_error=True)
k.OpenProcess.argtypes=[C.c_ulong,C.c_int,C.c_ulong];k.OpenProcess.restype=C.c_void_p
k.ReadProcessMemory.argtypes=[C.c_void_p,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)]
k.CloseHandle.argtypes=[C.c_void_p]
h=k.OpenProcess(0x410,False,pid)
if not h:raise C.WinError(C.get_last_error())
def read(p,n):
    buf=C.create_string_buffer(n);got=C.c_size_t()
    if not k.ReadProcessMemory(h,p,buf,n,C.byref(got)) or got.value!=n:raise C.WinError(C.get_last_error())
    return buf.raw
def q(p):return struct.unpack('<Q',read(p,8))[0]
def string(p):
    size=q(p+16)
    return read(p+24,min(size,256)).decode('utf8',errors='replace')
def table_values(p):
    info=read(p,64);size=1<<info[11];nodes=struct.unpack_from('<Q',info,32)[0]
    if size>65536:raise ValueError('Unexpected Lua table size')
    result={}
    for i in range(size):
        n=read(nodes+i*40,40);kind=struct.unpack_from('<i',n,24)[0]
        if kind==4:
            key=string(struct.unpack_from('<Q',n,16)[0]);value,tag=struct.unpack_from('<Qi',n)
            result[key]=(value,tag)
    return result
try:
    dll=mods['AionMarketShortcut.dll'];pe=struct.unpack('<I',read(dll+60,4))[0]
    export=struct.unpack('<I',read(dll+pe+24+112,4))[0]
    count,functions,names,ordinals=struct.unpack_from('<IIII',read(dll+export,40),24)
    for i in range(count):
        nrva=struct.unpack('<I',read(dll+names+4*i,4))[0];name=read(dll+nrva,80).split(b'\0')[0]
        if name==b'AionMarketDiagnostics':
            ordinal=struct.unpack('<H',read(dll+ordinals+2*i,2))[0];rva=struct.unpack('<I',read(dll+functions+4*ordinal,4))[0]
            print('HUD version, layouts, repositionings, clicks:',struct.unpack('<4I',read(dll+rva,16)))
    game=mods['Game.dll'];print('Hook bytes:',read(game+0x8b5ea0,9).hex())
    head=q(game+0x137e218);print('Native addon command objects:',q(game+0x137e220))
    seen=set()
    def walk(node):
        if not node or node==head or node in seen or len(seen)>=64:return
        seen.add(node);walk(q(node));obj=q(node+0x40)
        state=q(obj+0xb0);print('Registered command object:',hex(obj),'Lua state:',hex(state))
        globals_pointer,globals_type=struct.unpack('<Qi',read(state+0x78,12));print('Lua globals type:',globals_type)
        if globals_type==5:
            values=table_values(globals_pointer)
            for name in ('g_AddonName','PrivateWarehouse','PrivateWarehouseBrowser','PrivateWarehouse_Open','PrivateWarehouse_OnLoad','PrivateMenus_Register','SlashCmdList','SLASH_PRIVATEWAREHOUSE1'):
                val,tag=values.get(name,(0,0));print(name, 'type',tag, 'value',string(val) if tag==4 else hex(val))
                if name=='SlashCmdList' and tag==5:
                    cmds=table_values(val);print('Private slash registrations:',{n:tag for n,(ptr,tag) in cmds.items() if 'PRIVATE' in n})
                if name in ('PrivateWarehouse','PrivateWarehouseBrowser') and tag==5:
                    print('Widget members:',{n:(string(ptr) if typ==4 else hex(ptr),typ) for n,(ptr,typ) in table_values(val).items()})
        walk(q(node+16))
    walk(q(head+8))
    from capstone import Cs,CS_ARCH_X86,CS_MODE_64
    cs=Cs(CS_ARCH_X86,CS_MODE_64)
    for widget_id in range(0x20e,0x214):
        widget=q(game+0x13875c0+widget_id*8)
        if not widget:continue
        nameptr=q(widget+0x10) if q(widget+0x28)>=16 else widget+0x10
        print('Addon widget',hex(widget_id),read(nameptr,64).split(b'\0')[0].decode(errors='replace'),hex(widget),'flags',hex(q(widget+0x30)),'rect',struct.unpack('<4d',read(widget+0x50,32)))
        vt=q(widget)
        for slot in (0xa8,0xb8):
            method=q(vt+slot)
            print('Widget method',hex(slot),[(i.mnemonic,i.op_str) for i in list(cs.disasm(read(method,32),method))[:5]])
finally:k.CloseHandle(h)
