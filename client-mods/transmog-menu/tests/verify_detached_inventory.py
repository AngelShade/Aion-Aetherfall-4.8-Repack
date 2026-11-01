"""Execute the detached-inventory opening hook with native widget fixtures."""
import ctypes as c
import struct
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import detached_inventory as p
from patch_game_dll import Assembler

dll = Path(sys.argv[1]).read_bytes()
for site, old, new in p.edits():
    assert dll[site:site+len(new)] == new, hex(site)
owners = struct.unpack_from('<13I', dll, p.OWNER_LIST)
assert owners == tuple(0xffffffff if i in (2,12) else value for i,value in enumerate(p.OWNER_IDS))
assert p.patch_detached_inventory(dll) == dll, 'Reapplying must be idempotent'
assert p.patch_warehouse_item_context(dll) == dll, 'Warehouse context must be installed and idempotent'
k = c.WinDLL('kernel32', use_last_error=True)
k.VirtualAlloc.argtypes=[c.c_void_p,c.c_size_t,c.c_uint32,c.c_uint32]; k.VirtualAlloc.restype=c.c_void_p
k.VirtualProtect.argtypes=[c.c_void_p,c.c_size_t,c.c_uint32,c.POINTER(c.c_uint32)]
k.VirtualFree.argtypes=[c.c_void_p,c.c_size_t,c.c_uint32]
size=0x1450000; base=k.VirtualAlloc(None,size,0x3000,4); assert base
def put(offset, data): c.memmove(base+offset,data,len(data))
try:
    put(p.OPEN_CAVE,p.open_code())
    # Original docking routine increments its call counter and returns.
    a=Assembler(p.DOCK_ROUTINE);a.relative(bytes.fromhex('ff05'),0x2000);a.emit(b'\xc3');put(a.base,a.finish())
    # Mock native Show checks arguments and records visibility, leaving rectangle
    # and all item/search/scroll state untouched.
    put(0x3000,bytes.fromhex('89910001000044898104010000ff8108010000c3'))
    widget=c.create_string_buffer(0x650); vtable=c.create_string_buffer(0x700)
    struct.pack_into('<Q',widget,0,c.addressof(vtable));struct.pack_into('<Q',vtable,0xd0,base+0x3000)
    rectangle=struct.pack('<4d',411,93,560,684);c.memmove(c.addressof(widget)+0x50,rectangle,len(rectangle))
    # Callers retain visibility in r12d. Verify it survives the emitted hook.
    a=Assembler(0x1000);a.emit(bytes.fromhex('41544883ec204189cc'))
    a.relative(b'\xe8',p.OPEN_CAVE);a.emit(bytes.fromhex('4489e04883c420415cc3'));put(a.base,a.finish())
    old=c.c_uint32();assert k.VirtualProtect(base,size,0x40,c.byref(old))
    fn=c.CFUNCTYPE(c.c_int,c.c_int)(base+0x1000)
    for available in [False,True]:
        put(p.NORMAL_INVENTORY,struct.pack('<Q',c.addressof(widget) if available else 0))
        for shown in [0,1,1,0]:
            before=struct.unpack_from('<I',widget,0x108)[0]
            assert fn(shown)==shown, 'Caller visibility register corrupted'
            after=struct.unpack_from('<I',widget,0x108)[0]
            assert after-before==int(available and shown), (available,shown)
            assert widget.raw[0x50:0x70]==rectangle, 'Inventory dragged position changed'
            if available and shown:
                assert struct.unpack_from('<II',widget,0x100)==(1,0), 'Wrong native Show flags'
    assert c.c_uint32.from_address(base+0x2000).value==8
    print('PASS: Warehouse/Profile open the normal inventory; hide does not close it; repeated opens preserve its rectangle; null widgets and r12 state handled; other docking owners preserved.')
    # Execute the installed native resolver, including its original fallback
    # chain. No Game.dll is loaded and no game process/state is accessed.
    put(0x78bfd0, dll[0x78bfd0:0x78c135])
    put(p.CONTEXT_CAVE, dll[p.CONTEXT_CAVE:p.CONTEXT_CAVE+p.CONTEXT_REGION_SIZE])
    put(0x3100, bytes.fromhex('ff81080100008b8100010000c3'))
    struct.pack_into('<Q',vtable,0xf0,base+0x3100)
    context=c.CFUNCTYPE(c.c_int)(base+0x78bfd0)
    for owner in [-1,180,181,208,247,265]:
        put(p.CONTEXT_OWNER,struct.pack('<i',owner))
        for available in [False,True]:
            put(p.WAREHOUSE,struct.pack('<Q',c.addressof(widget) if available else 0))
            for shown in [0,1,0,1]:
                struct.pack_into('<I',widget,0x100,shown)
                before=struct.unpack_from('<I',widget,0x108)[0]
                expected=owner if owner!=-1 else (p.WAREHOUSE_ID if available and shown else -1)
                assert context()==expected,(owner,available,shown)
                assert struct.unpack_from('<I',widget,0x108)[0]-before==int(owner==-1 and available)
                assert c.c_int.from_address(base+p.CONTEXT_OWNER).value==owner,'Native context state rewritten'
                assert widget.raw[0x50:0x70]==rectangle,'Warehouse routing moved a window'
    # Both stock item-click dispatch tables must still choose their native
    # Warehouse branch, whose count and single-item calls remain untouched.
    for call,table,indexes,warehouse_branch in [(0x785e01,0x7866ac,0x7866f0,0x785f58),
                                               (0x78e786,0x78ef2c,0x78ef68,0x78e85d)]:
        assert dll[call]==0xe8 and call+5+struct.unpack_from('<i',dll,call+1)[0]==0x78bfd0
        index=dll[indexes+p.WAREHOUSE_ID-180]
        assert struct.unpack_from('<I',dll,table+4*index)[0]==warehouse_branch
    print('PASS: 48 native item-context cases cover hidden/missing/reopened Warehouse and other owners; both stock item-click dispatchers retain Warehouse routing. In-game acceptance remains pending.')
    if '--warehouse-controls' in sys.argv[2:]:
        sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'expanded-warehouse'))
        import warehouse_controls as w
        assert w.patch_dll(dll)==dll
        put(0x948150,dll[0x948150:0x9483e6])
        put(0xe8f800,dll[0xe8f800:0xe95000])
        controls={name:c.create_string_buffer(0x700) for name in
                  ['dialog','grid','account_title_button','account_container',w.CHARACTER_BUTTON,
                   'personal_container','warehouse_character_query','warehouse_character_search','warehouse_character_clear']}
        dialog=controls['dialog'];methods=c.create_string_buffer(0x700)
        for control in controls.values():
            struct.pack_into('<Q',control,0,c.addressof(methods))
        struct.pack_into('<Q',dialog,0x540,c.addressof(controls['grid']))
        struct.pack_into('<Q',dialog,0x560,c.addressof(controls['account_title_button']))
        event=c.create_string_buffer(8)
        lookup_type=c.CFUNCTYPE(c.c_void_p,c.c_void_p,c.c_void_p)
        @lookup_type
        def lookup(owner,name):
            node=controls.get(c.string_at(name).decode('ascii'))
            return c.addressof(node) if node is not None else 0
        struct.pack_into('<Q',methods,0x340,c.cast(lookup,c.c_void_p).value)
        for slot,offset,code in [
            (0xd0,0x3200,'c7810001000001000000c3'),
            (0xd8,0x3220,'c7810001000000000000c3'),
            (0x598,0x3240,'899104010000c3'),
            (0x5a8,0x3260,'8b8104010000c3'),
            (0x1a8,0x3300,'f30f6f02f30f7f4150f30f6f4210f30f7f4160c3')]:
            put(offset,bytes.fromhex(code))
            struct.pack_into('<Q',methods,slot,base+offset)
        click=c.CFUNCTYPE(c.c_int,c.c_void_p,c.c_void_p)(base+0x948150)
        def toggle(name,checked):
            struct.pack_into('<I',controls[name],0x104,checked)
            struct.pack_into('<Q',event,0,c.addressof(controls[name]))
            assert click(c.addressof(dialog),c.addressof(event))==0
        def rect(name):return struct.unpack_from('<4d',controls[name],0x50)
        for scale in [0.75,1.0,1.25]:
            for name,node in controls.items():
                frame=(112,70,590,680) if name=='dialog' else (0,0,584,25)
                if name=='grid':frame=(15,58,560,240)
                if name=='account_container':frame=(0,334,584,276)
                struct.pack_into('<4d',node,0x50,*(v*scale for v in frame))
            struct.pack_into('<I',dialog,0x534,0)
            put(w.WAREHOUSE,struct.pack('<Q',c.addressof(dialog)))
            for char,account,last in [(1,1,1),(1,1,2),(0,1,2),(1,0,1),(0,0,1),(0,0,2)]:
                toggle(w.CHARACTER_BUTTON,char)
                toggle('account_title_button',account)
                expected_height=680-(0 if char else 282)-(0 if account else 276)
                assert abs(rect('dialog')[3]-expected_height*scale)<1e-8,(char,account,rect('dialog'))
                assert rect('dialog')[:3]==(112*scale,70*scale,590*scale)
                assert abs(rect('account_title_button')[1]-(307 if char else 25)*scale)<1e-8
                assert abs(rect('account_container')[1]-(334 if char else 52)*scale)<1e-8
                assert struct.unpack_from('<I',controls['grid'],0x100)[0]==char
                assert struct.unpack_from('<I',controls['account_container'],0x100)[0]==account
                for name in ['warehouse_character_query','warehouse_character_search','warehouse_character_clear']:
                    assert struct.unpack_from('<I',controls[name],0x100)[0]==char
                put(w.LAST,struct.pack('<I',last))
                expected=last if char==account else (1 if char else 2)
                # Execute each original dispatcher's patched destination mapping
                # with the same stack alignment and saved-register contract.
                for site in w.MAP_SITES:
                    a=Assembler(0x4000);emit_start=bytes.fromhex('534883ec20');a.emit(emit_start)
                    a.relative(b'\xe9',site);done=0x4000+len(a.code)
                    a.emit(bytes.fromhex('89d84883c4205bc3'));put(a.base,a.finish())
                    put(site,dll[site:site+33]);put(site+33,b'\xe9'+struct.pack('<i',done-site-38))
                    transfer=c.CFUNCTYPE(c.c_int)(base+0x4000)
                    assert transfer()==expected,(char,account,last,hex(site))
                if not char and not account:
                    assert struct.unpack_from('<I',controls[w.CHARACTER_BUTTON],0x104)[0]==int(last==1)
                    assert struct.unpack_from('<I',dialog,0x59c)[0]==int(last==2)
            struct.pack_into('<I',dialog,0x534,2)
            toggle(w.CHARACTER_BUTTON,0)
            assert abs(rect('dialog')[3]-680*scale)<1e-8,'Legion height changed'
            assert transfer()==3,'Legion transfer destination changed'
        # Enter the real initialization call and the OnVisible completion
        # jump, with the same owner registers their native callers retain.
        struct.pack_into('<I',dialog,0x534,0)
        a=Assembler(0x4100);a.emit(bytes.fromhex('534883ec204889cb488b8b60050000ba01000000'))
        a.relative(b'\xe9',w.INIT_SITE);done=a.base+len(a.code)
        a.emit(bytes.fromhex('4883c4205bc3'));put(a.base,a.finish())
        put(w.INIT_SITE,dll[w.INIT_SITE:w.INIT_SITE+6])
        put(w.INIT_SITE+6,b'\xe9'+struct.pack('<i',done-w.INIT_SITE-11))
        c.CFUNCTYPE(None,c.c_void_p)(base+0x4100)(c.addressof(dialog))
        assert struct.unpack_from('<I',controls[w.CHARACTER_BUTTON],0x104)[0]==1
        assert abs(rect('dialog')[3]-680*scale)<1e-8
        toggle(w.CHARACTER_BUTTON,0);toggle('account_title_button',0)
        struct.pack_into('<d',dialog,0x68,680*scale)  # stale saved height
        a=Assembler(0x4200);a.emit(bytes.fromhex('554883ec204889cd'))
        a.relative(b'\xe9',w.SHOW_SITE);done=a.base+len(a.code)
        a.emit(bytes.fromhex('4883c4205dc3'));put(a.base,a.finish())
        put(w.SHOW_SITE,dll[w.SHOW_SITE:w.SHOW_SITE+5])
        put(0x947ddd,b'\xe9'+struct.pack('<i',done-0x947ddd-5))
        c.CFUNCTYPE(None,c.c_void_p)(base+0x4200)(c.addressof(dialog))
        assert abs(rect('dialog')[3]-122*scale)<1e-8,'Reopen did not reconcile collapsed geometry'
        print('PASS: actual Warehouse button entry handles both sections at three scales; both Inventory dispatch mappings select expanded storage and reopen last-used storage; Legion routing is retained. Gameplay and Kinah click acceptance remain pending.')

finally:
    k.VirtualFree(base,0,0x8000)
