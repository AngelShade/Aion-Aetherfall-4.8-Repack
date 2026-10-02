"""Exercise real extension against native-shaped network/menu fixtures."""
import argparse,ctypes as C,struct,time
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('dll',type=Path);p.add_argument('--native-send',action='store_true');a=p.parse_args()
    dll=C.WinDLL(str(a.dll.resolve()));k=C.WinDLL('kernel32',use_last_error=True)
    k.VirtualAlloc.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong,C.c_ulong];k.VirtualAlloc.restype=C.c_void_p
    k.VirtualFree.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong]
    mem=k.VirtualAlloc(None,0x1380000,0x3000,0x40);assert mem
    keep=[];packets=[];items=[]
    def callback(signature,fn):
        wrapped=signature(fn);keep.append(wrapped);return C.cast(wrapped,C.c_void_p).value
    def redirect(rva,ptr):C.memmove(mem+rva,b'\x48\xb8'+struct.pack('<Q',ptr)+b'\xff\xe0',12)
    # Regression fixture models the observed game: replay CVar is 0 while the
    # native socket and sending flag are enabled. A replay check must not gate it.
    state_fn=callback(C.CFUNCTYPE(C.c_int,C.c_void_p),lambda obj:0)
    vt=(C.c_void_p*2)();vt[1]=state_fn;network=C.c_void_p(C.addressof(vt))
    C.c_void_p.from_address(mem+0x13122f0).value=C.addressof(network)
    C.c_void_p.from_address(mem+0x133bb30).value=0x1234
    C.c_ubyte.from_address(mem+0x133bb38).value=1
    if a.native_send:
        # Execute the client's actual SendChat packet builder, with the native
        # writer intercepted before encryption/transport. Replay stays at 0,
        # exactly as measured in the user's game. No live process is touched.
        import inspect_client
        code=inspect_client.b[inspect_client.offset(0x33d790):inspect_client.offset(0x33d997)]
        C.memmove(mem+0x33d790,code,len(code))
        C.memmove(mem+0xb544b0,b'\xc3',1) # stack probe; small isolated test frame
        def write(context,raw,size):
            data=C.string_at(raw,size)
            assert struct.unpack_from('<H',data)[0]==size
            assert struct.unpack_from('<H',data,2)[0]==0xfe and data[4]==0x65
            assert data[-2:]==b'\0\0'
            packets.append((data[7],data[8:-2].decode('utf-16le')))
        redirect(0x330590,callback(C.CFUNCTYPE(None,C.c_void_p,C.c_void_p,C.c_int),write))
    else:
        redirect(0x33d790,callback(C.CFUNCTYPE(None,C.c_void_p,C.c_int,C.c_wchar_p),lambda obj,channel,command:packets.append((channel,command))))
    nodes=[]
    def add(parent,kind,name,label,color,alternate,enabled,unused,mode,unused2,tooltip):
        node=C.create_string_buffer(0x1c8);nodes.append(node)
        C.memmove(C.addressof(node)+4,name,len(name)+1)
        C.c_int.from_buffer(node,0x19c).value=enabled
        items.append((parent,kind,name.decode(),label,enabled,mode,tuple(color[i] for i in range(4))))
        return C.addressof(node)
    signature=C.CFUNCTYPE(C.c_void_p,C.c_void_p,C.c_int,C.c_char_p,C.c_wchar_p,C.POINTER(C.c_float),C.c_wchar_p,C.c_int,C.c_int,C.c_int,C.c_int,C.c_wchar_p)
    redirect(0x534ea0,callback(signature,add))
    dll.AionSpeechTestGame.argtypes=[C.c_void_p];dll.AionSpeechTestGame(mem)
    build=dll.AionSpeechMenuBuild;build.argtypes=[C.c_void_p]
    recv=dll.AionSpeechMessage;recv.argtypes=[C.c_wchar_p,C.c_int,C.c_ulong,C.c_wchar_p];recv.restype=C.c_void_p
    skin=dll.AionSpeechSkin;skin.argtypes=[C.c_void_p];skin.restype=C.c_int
    choose=dll.AionSpeechMenuCommand;choose.argtypes=[C.c_void_p,C.c_void_p];choose.restype=C.c_int
    refresh=dll.AionSpeechMenuRefresh;refresh.argtypes=[C.c_void_p,C.c_void_p]
    artwork=dll.AionSpeechArtwork;artwork.argtypes=[C.c_void_p,C.POINTER(C.c_double)];artwork.restype=C.c_void_p
    renderers=[C.create_string_buffer(0x40) for _ in range(3)]
    resources=[];lookups=[];missing=[False]
    rv=(C.c_void_p*17)()
    def get_renderer(resource):return C.addressof(renderers[resources.index(resource)])
    rv[16]=callback(C.CFUNCTYPE(C.c_void_p,C.c_void_p),get_renderer)
    for i in range(3):
        resource=C.c_void_p(C.addressof(rv));keep.append(resource);resources.append(C.addressof(resource))
    def lookup(registry,name):
        assert registry==mem+0x1378d40
        lookups.append(name)
        return None if missing[0] else resources[['asb_ornate','asb_amber','asb_thought'].index(name)]
    redirect(0x58c040,callback(C.CFUNCTYPE(C.c_void_p,C.c_void_p,C.c_wchar_p),lookup))
    parent=C.create_string_buffer(0x1c8)
    try:
        build(parent)
        assert packets==[(0,'.speechbubble sync')],'negotiation must work before knowing character ID'
        assert items[0][1:4]==(2,'speech_bubble','Chat Bubble')
        assert len(items)==6 and all(item[5:]==(2,(1,1,1,1)) for item in items)
        assert [item[4] for item in items[1:]]==[0,1,1,1,1],'active row greyed and disabled'
        submenu=items[1][0];assert submenu==C.addressof(nodes[0]) and all(item[0]==submenu for item in items[1:])
        assert [item[3] for item in items[1:]]==['Classic','Wings','Crystal','Cloud','Paws']
        assert all(item[1]==3 for item in items[1:]),'native checkboxes'
        checked=lambda:[C.c_int.from_buffer(node,0x1a0).value for node in nodes[-5:]]
        assert checked()==[1,0,0,0,0],'Classic checked before initial acknowledgement'
        model=nodes[0];model_items=(C.c_void_p*5)(*[C.addressof(node) for node in nodes[1:]])
        C.c_void_p.from_buffer(model,0x1b0).value=C.addressof(model_items)
        C.c_void_p.from_buffer(model,0x1b8).value=C.addressof(model_items)+C.sizeof(model_items)
        # Two different server-authoritative styles coexist; never override others.
        assert not recv('~ASB1:1:Self',0,101,'~ASB_ACK1~')
        foreign=C.create_unicode_buffer('~ASB1:4:Other');assert C.wstring_at(recv(foreign,0,202,'Hi'))=='Other'
        def appearance(id):
            bubble=C.create_string_buffer(40);C.c_int.from_buffer(bubble,0x10).value=0;C.c_ulong.from_buffer(bubble,0x14).value=id
            return skin(bubble)
        assert appearance(101)==2 and appearance(202)==4
        refresh(None,model);assert checked()==[0,1,0,0,0],'cached Classic model refreshes from saved acknowledgement'
        widget_states={};widget_enables={};widget_names=[];widgets=[];links=[]
        wvt=(C.c_void_p*200)()
        def getname(obj):return C.addressof(widget_names[widgets.index(obj)])
        wvt[0xa8//8]=callback(C.CFUNCTYPE(C.c_void_p,C.c_void_p),getname)
        wvt[0x598//8]=callback(C.CFUNCTYPE(None,C.c_void_p,C.c_int),lambda obj,state:widget_states.__setitem__(obj,state))
        wvt[0xd0//8]=callback(C.CFUNCTYPE(None,C.c_void_p,C.c_int,C.c_int),lambda obj,flag,unused:widget_enables.__setitem__(obj,True))
        wvt[0xd8//8]=callback(C.CFUNCTYPE(None,C.c_void_p,C.c_int,C.c_int),lambda obj,flag,unused:widget_enables.__setitem__(obj,False))
        popup=C.create_string_buffer(0x5a0);head=(C.c_void_p*3)();head_addr=C.addressof(head)
        C.c_void_p.from_buffer(popup,0x2a0).value=head_addr
        C.c_void_p.from_buffer(popup,0x598).value=C.addressof(model)
        for i in range(5):
            widget_names.append(C.create_string_buffer(f'speech_bubble_{i}'.encode()))
            w=C.c_void_p(C.addressof(wvt));keep.append(w);widgets.append(C.addressof(w));link=(C.c_void_p*3)();link[2]=C.addressof(w);links.append(link)
        head[0]=C.addressof(links[0])
        for i,link in enumerate(links):link[0]=C.addressof(links[i+1]) if i<4 else head_addr
        # Execute the actual native popup event dispatcher, including its virtual
        # OnCommand call. Checkbox clicks arrive here before the chat dialog.
        import inspect_client
        code=inspect_client.b[inspect_client.offset(0x4adeb0):inspect_client.offset(0x4adff8)]
        C.memmove(mem+0x4adeb0,code,len(code))
        pvt=(C.c_void_p*200)();pvt[0x490//8]=C.cast(choose,C.c_void_p).value
        C.c_void_p.from_buffer(popup).value=C.addressof(pvt)
        dispatch=C.CFUNCTYPE(C.c_int,C.c_void_p,C.c_int,C.c_void_p)(mem+0x4adeb0)
        event=C.c_void_p(widgets[3])
        assert dispatch(popup,0x76c,C.byref(event))==1 and packets[-1]==(0,'.speechbubble 3')
        assert checked()==[0,0,0,1,0],'open menu model is exclusive immediately'
        assert [widget_states[w] for w in widgets]==[0,0,0,1,0],'visible sibling checkboxes are exclusive'
        assert [widget_enables[w] for w in widgets]==[True,True,True,False,True],'active visible row disabled'
        count=len(packets);assert dispatch(popup,0x76c,C.byref(event))==1 and len(packets)==count,'cannot toggle active style off'
        current=C.create_string_buffer(b'speech_bubble_3')
        getname=callback(C.CFUNCTYPE(C.c_void_p,C.c_void_p),lambda obj:C.addressof(current))
        wvt=(C.c_void_p*22)();wvt[0xa8//8]=getname;widget=C.c_void_p(C.addressof(wvt));event=C.c_void_p(C.addressof(widget))
        assert choose(None,C.byref(event))==1 and packets[-1]==(0,'.speechbubble 3')
        assert appearance(101)==3 and appearance(202)==4,'only the choosing character changes'
        assert not recv('~ASB1:3:Self',0,101,'~ASB_ACK1~')
        items.clear();build(parent);assert checked()==[0,0,0,1,0],'only active style is checked'
        count=len(packets);dll.AionSpeechTick();assert len(packets)==count,'ack stops retries'
        # Leaving world clears identities; next login reloads the actual saved style.
        C.c_ubyte.from_address(mem+0x133bb38).value=0;dll.AionSpeechTick();assert appearance(101)==0
        C.c_ubyte.from_address(mem+0x133bb38).value=1;items.clear();build(parent);assert packets[-1]==(0,'.speechbubble sync')
        assert not recv('~ASB1:2:NewCharacter',0,303,'~ASB_ACK1~');assert appearance(303)==1 and appearance(101)==0
        C.c_ubyte.from_address(mem+0x133bb38).value=0;dll.AionSpeechTick();count=len(packets)
        assert choose(None,C.byref(event))==1 and len(packets)==count,'no commands sent while disconnected'
        # Each shared style must resolve a different native resource and resize
        # the actual frame without moving text or touching stock NPC/pet skins.
        def bubble_style(id,kind=0):
            bubble=C.create_string_buffer(40);C.c_int.from_buffer(bubble,0x10).value=kind;C.c_ulong.from_buffer(bubble,0x14).value=id
            skin(bubble)
        dimensions=[]
        for style in range(1,4):
            name=C.create_unicode_buffer(f'~ASB1:{style}:Player');recv(name,0,909,'test')
            bubble_style(909);rect=(C.c_double*4)(500,300,40,48)
            assert artwork(0x1234,rect)==C.addressof(renderers[style-1])
            assert rect[0]+rect[2]/2==520,'text and frame horizontal centers match'
            assert rect[1]<300 and rect[3]>48
            dimensions.append(tuple(rect))
        assert len(set(dimensions))==3,'styles have distinct dimensions and padding'
        for kind in (2,3,4,5,6,22):
            bubble_style(909,kind);rect=(C.c_double*4)(500,300,40,48)
            assert artwork(0x1234,rect)==0x1234 and tuple(rect)==(500,300,40,48)
        for style in (0,4):
            recv(f'~ASB1:{style}:Player',0,909,'test');bubble_style(909);rect=(C.c_double*4)(500,300,40,48)
            assert artwork(0x1234,rect)==0x1234 and tuple(rect)==(500,300,40,48)
        recv('~ASB1:1:Player',0,909,'test');bubble_style(909);missing[0]=True;rect=(C.c_double*4)(500,300,40,48)
        assert artwork(0x1234,rect)==0x1234 and tuple(rect)==(500,300,40,48),'missing resource safely preserves stock frame'
        print('PASS: three distinct native artwork lookups and dimensions; centered text, stock Classic/Paws/NPC/pet frames, and missing-resource fallback.')
    finally:k.VirtualFree(mem,0,0x8000)
    print('PASS: actual native popup event dispatch, cached-model refresh, exclusive visible checkmarks, disabled active row, shared character styles, relog reset, hidden acknowledgements, and disconnect gating.')
    if a.native_send:print('PASS: actual client SendChat packet builder executed; every command reached its native transport call with valid packet framing and UTF-16 body.')
if __name__=='__main__':main()
