"""Execute real client coordinate/hit-test code; ensure every visible control fits."""
import argparse
import ctypes as C
import json
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as E
from prepare_bar import HERE
sys.path.insert(0,str(HERE.parent/'speech-bubbles'))
import inspect_client as native

def main():
    p=argparse.ArgumentParser();p.add_argument('--live-capture',type=Path);a=p.parse_args()
    k=C.WinDLL('kernel32',use_last_error=True)
    k.VirtualAlloc.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong,C.c_ulong];k.VirtualAlloc.restype=C.c_void_p
    k.VirtualFree.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong]
    mem=k.VirtualAlloc(None,0x2000,0x3000,0x40);assert mem
    def put(at,data):C.memmove(mem+at,data,len(data))
    put(0,native.b[native.offset(0x5b4580):native.offset(0x5b465d)])
    put(0x200,native.b[native.offset(0x4b0410):native.offset(0x4b07e7)])
    put(0x800,bytes.fromhex('488b4130c3')) # GetFlags
    put(0x820,bytes.fromhex('488bc1c3')) # GetRoot
    put(0x840,bytes.fromhex('488b8148050000c3')) # attached child
    put(0x860,native.b[native.offset(0x4b0250):native.offset(0x4b027c)]) # real GetTitleRect
    vtable=C.create_string_buffer(0x680);bar=C.create_string_buffer(0x600)
    for slot,at in [(0xb8,0x800),(0x360,0x820),(0x538,0x840),(0x5d0,0x860)]:struct.pack_into('<Q',vtable,slot,mem+at)
    struct.pack_into('<Q',bar,0,C.addressof(vtable));struct.pack_into('<Q',bar,0x30,0x412f)
    translate=C.CFUNCTYPE(None,C.c_void_p,C.POINTER(C.c_double),C.POINTER(C.c_double))(mem)
    hit=C.CFUNCTYPE(C.c_int,C.c_void_p,C.c_double,C.c_double)(mem+0x200)
    widgets={n.get('name'):tuple(map(float,n.get('frame').split(','))) for n in E.parse(HERE/'PlayerBotBar.xml').iter('Widget')}
    modes=[(400,70,22,['Attack','Follow','Stay','Summon','More','Hide']),
           (400,168,22,['Attack','Follow','Stay','Summon','More','Hide','Guard','Passive','Manage','Circle','Box','Line','Spread','Hint']),
           (44,52,10,['Icon'])]
    try:
        # Reproduce the user's current clipping using captured native margins,
        # without calling or writing to the running process.
        if a.live_capture:
            old=json.loads(a.live_capture.read_text());r=old['rect'];m=old['margins'];attack=old['children']['PlayerBotBarAttack'];local=attack['localRect']
            struct.pack_into('<4d',bar,0x50,*r);struct.pack_into('<4d',bar,0x70,*m)
            x,y=C.c_double(local[0]),C.c_double(local[1]);translate(bar,C.byref(x),C.byref(y))
            assert abs(x.value-attack['absoluteRect'][0])<.001 and abs(y.value-attack['absoluteRect'][1])<.001
            assert local[1]+local[3]>m[3],'Captured old bar must reproduce the clipping'
        checked=0
        for scale in (.8,1,1.125,1.5):
            for w,h,title,shown in modes:
                r=(211.125,229.75,w*scale,h*scale)
                margins=(3*scale,(title+3)*scale,(w-6)*scale,(h-title-6)*scale)
                struct.pack_into('<4d',bar,0x50,*r);struct.pack_into('<4d',bar,0x70,*margins)
                struct.pack_into('<4d',bar,0x3e8,3*scale,3*scale,(w-6)*scale,title*scale)
                assert hit(bar,r[0]+r[2]/2,r[1]+5*scale)==5,'Title/icon grip must start native dragging'
                # Native borders can paint outside their logical frames. Reserve
                # four pixels around every button and still require separation.
                buttons=[widgets['PlayerBotBar'+n] for n in shown if n!='Hint']
                for i,(ax,ay,aw,ah) in enumerate(buttons):
                    for bx,by,bw,bh in buttons[i+1:]:
                        assert ax+aw+8<=bx or bx+bw+8<=ax or ay+ah+8<=by or by+bh+8<=ay,'Button borders overlap'
                for name in shown:
                    x0,y0,cw,ch=widgets['PlayerBotBar'+name]
                    x,y=C.c_double(x0*scale),C.c_double(y0*scale);translate(bar,C.byref(x),C.byref(y))
                    assert x.value>=r[0]+margins[0] and y.value>=r[1]+margins[1],name
                    assert x.value+cw*scale<=r[0]+margins[0]+margins[2]+.001,name+' right edge clipped'
                    assert y.value+ch*scale<=r[1]+margins[1]+margins[3]+.001,name+' bottom edge clipped'
                    assert hit(bar,x.value+cw*scale/2,y.value+ch*scale/2)==0,'Commands must not initiate title dragging'
                    checked+=1
        print('OK:',checked,'visible-control bounds through real Game.dll coordinate/hit-test code; collapsed, expanded and draggable icon at four scales; old live clipping reproduced.')
    finally:k.VirtualFree(mem,0,0x8000)

if __name__=='__main__':main()
