"""Check bounded XML changes and execute the client's title-bar drag test."""
import ctypes as c
import json
import struct
import sys
from pathlib import Path
import xml.etree.ElementTree as E
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from graphics_compat import read_pak,binary_xml
from movable_windows import WINDOWS,patch_layout

client=Path(sys.argv[1]);package=Path(sys.argv[2])
manifest=json.loads((package/'manifest.json').read_text())
for relative,prefix in [('Data/ui/game/game.pak',''),('L10N/enu/Data/data.pak','ui/game/')]:
    with read_pak(client/relative) as old,read_pak(package/relative) as new:
        assert old.namelist()==new.namelist() and new.testzip() is None
        for name in old.namelist():
            before=old.read(name);after=new.read(name)
            if name not in {prefix+n for n in WINDOWS}:assert before==after,name;continue
            a=binary_xml(before);b=binary_xml(after)
            assert not b.get('align_type') and 'movable' in b.get('flag').split(';')
            assert 'not_movable' not in b.get('flag').split(';')
            expected=patch_layout(E.fromstring(E.tostring(a)))
            assert E.tostring(expected)==E.tostring(b),name
            assert E.tostring(patch_layout(b))==E.tostring(b),'Layout must be idempotent'
    if manifest.get('graphicsCompatibility'):
        relative=next(n['path'] for n in json.loads((package/'DXVK/graphics-menu/installed.json').read_text())['files'] if n['path'].lower()==relative.lower())
        baseline=package/'DXVK-backups'/manifest['graphicsCompatibility']['backupName']/relative
        with read_pak(baseline) as restore:
            for name in WINDOWS:
                root=binary_xml(restore.read(prefix+name))
                assert not root.get('align_type') and 'movable' in root.get('flag').split(';')
print('PASS: only three dialog roots change in base/English archives; grids, items, search and all other UI entries preserved; movement survives graphics restore')

# Run the original native drag-hit test with small native widgets. This checks
# the real client movement flag, title rectangle and attached-window guard.
dll=(client/'bin64/Game.dll').read_bytes()
assert dll[0x4b0410:0x4b041c]==bytes.fromhex('f20f11542418f20f114c2410')
assert dll[0x4ad306:0x4ad310]==bytes.fromhex('c787b404000005000000'),'Unmanaged alignment must default to 5'
k=c.WinDLL('kernel32',use_last_error=True)
k.VirtualAlloc.argtypes=[c.c_void_p,c.c_size_t,c.c_uint32,c.c_uint32];k.VirtualAlloc.restype=c.c_void_p
k.VirtualProtect.argtypes=[c.c_void_p,c.c_size_t,c.c_uint32,c.POINTER(c.c_uint32)]
k.VirtualFree.argtypes=[c.c_void_p,c.c_size_t,c.c_uint32]
base=k.VirtualAlloc(None,0x2000,0x3000,4);assert base
def put(p,data):c.memmove(base+p,data,len(data))
try:
    put(0,dll[0x4b0410:0x4b07e7])
    put(0x1000,bytes.fromhex('488bc1c3')) # GetRoot
    put(0x1020,bytes.fromhex('488b8148050000c3')) # attached child, null normally
    put(0x1040,bytes.fromhex('488b4130c3')) # native flags
    code=bytearray()
    for offset in (0,8,16,24):
        code+=bytes.fromhex('488b81')+struct.pack('<I',0x500+offset)
        code+=bytes.fromhex('488942')+bytes([offset])
    code+=b'\xc3';put(0x1060,bytes(code)) # title rect
    widget=c.create_string_buffer(0x580);vtable=c.create_string_buffer(0x680)
    struct.pack_into('<Q',widget,0,c.addressof(vtable))
    for slot,code in [(0x360,0x1000),(0x538,0x1020),(0xb8,0x1040),(0x5d0,0x1060)]:struct.pack_into('<Q',vtable,slot,base+code)
    struct.pack_into('<4d',widget,0x50,90,120,560,684)
    struct.pack_into('<4d',widget,0x500,0,0,560,25)
    old=c.c_uint32();assert k.VirtualProtect(base,0x2000,0x40,c.byref(old))
    hit=c.CFUNCTYPE(c.c_int,c.c_void_p,c.c_double,c.c_double)(base)
    for width,height in [(406,431),(560,684),(590,680)]:
        struct.pack_into('<4d',widget,0x50,90,120,width,height)
        struct.pack_into('<4d',widget,0x500,0,0,width,25)
        struct.pack_into('<Q',widget,0x30,15)
        assert hit(widget,100,130)==5,'Title must start native move'
        assert hit(widget,100,160)==0,'Content must not start window move'
        struct.pack_into('<Q',widget,0x30,7)
        assert hit(widget,100,130)==0,'Movement flag must control dragging'
        struct.pack_into('<Q',widget,0x30,15)
        struct.pack_into('<Q',widget,0x548,c.addressof(widget))
        assert hit(widget,100,130)==0,'Preserve attached-window guard'
        struct.pack_into('<Q',widget,0x548,0)
    print('PASS: original native title hit test enables movement for Profile/Inventory/Warehouse dimensions; content and attached windows retain guards')
finally:k.VirtualFree(base,0,0x8000)
