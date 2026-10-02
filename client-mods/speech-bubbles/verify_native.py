"""Execute the prepared hooks in an isolated allocation, and test the native DLL.

Never loads Game.dll or attaches to Aion. Only our trampolines and small test
continuations run; the IAT points to Python test callbacks.
"""
import argparse, ctypes as C, json, struct
from pathlib import Path
from capstone import Cs, CS_ARCH_X86, CS_MODE_64
from loader_permissions import writable_iats

def check(condition,message):
    if not condition: raise AssertionError(message)

def main():
    p=argparse.ArgumentParser();p.add_argument('package',type=Path);args=p.parse_args()
    package=args.package.resolve();b=(package/'bin64/Game.dll').read_bytes()
    check(writable_iats(b)[0]==b,'all import destinations remain writable for the Windows loader')
    manifest=json.loads((package/'manifest.json').read_text())
    pe=struct.unpack_from('<I',b,60)[0];opt=pe+24;table=opt+struct.unpack_from('<H',b,pe+20)[0]
    sections=[struct.unpack_from('<8sIIIIIIHHI',b,table+i*40) for i in range(struct.unpack_from('<H',b,pe+6)[0])]
    def offset(r):
        for s in sections:
            if s[2]<=r<s[2]+s[3]:return s[4]+r-s[2]
        raise ValueError(hex(r))
    kernel=C.WinDLL('kernel32',use_last_error=True)
    kernel.VirtualAlloc.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong,C.c_ulong];kernel.VirtualAlloc.restype=C.c_void_p
    kernel.VirtualFree.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong]
    size=struct.unpack_from('<I',b,opt+56)[0]+0x1000
    mem=kernel.VirtualAlloc(None,size,0x3000,0x40);check(mem,'VirtualAlloc')
    renderer=C.create_string_buffer(8)
    callbacks=[];records={};result={'click':1,'skin':3,'message':None,'art':C.addressof(renderer)}
    F=C.CFUNCTYPE(C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t)
    message=C.create_unicode_buffer('~ASB1:3:Fixture');body=C.create_unicode_buffer('hello')
    def callback(name):
        def cb(a,b,c,d):
            records[name]=(a,b,c,d)
            if name=='AionSpeechSkin':return result['skin']
            if name=='AionSpeechArtwork':return result['art']
            if name=='AionSpeechMenuCommand':return result['click']
            if name=='AionSpeechMessage':return result['message'] or 0
            return 0
        fn=F(cb);callbacks.append(fn);return C.cast(fn,C.c_void_p).value
    try:
        own=next(s for s in sections if s[0].rstrip(b'\0')==b'.asb')
        C.memmove(mem+own[2],b[own[4]:own[4]+own[3]],own[3])
        # The added import descriptor follows preserved descriptors.
        imp=struct.unpack_from('<I',b,opt+120)[0];cursor=offset(imp)
        while any(b[cursor+20:cursor+40]):cursor+=20
        iat=struct.unpack_from('<I',b,cursor+16)[0]
        for i,h in enumerate(manifest['hooks']):
            C.c_size_t.from_address(mem+iat+i*8).value=callback(h['export'])
            site=int(h['site'],16);hook=int(h['hook'],16);original=bytes.fromhex(h['original'])
            C.memmove(mem+site,b[offset(site):offset(site)+len(original)],len(original))
            ins=list(Cs(CS_ARCH_X86,CS_MODE_64).disasm(b[offset(hook):offset(hook)+h['size']],hook))
            check(sum(i.size for i in ins)==h['size'],'whole-instruction hook '+h['export'])
            for inst in ins:
                check('[rsp -' not in inst.op_str,'no negative stack offsets: '+h['export'])
            continuation={
                'AionSpeechMenuBuild':bytes.fromhex('4883c4205fc3'),
                'AionSpeechMenuCommand':bytes.fromhex('4883c43831c0c3'),
                'AionSpeechMenuRefresh':bytes.fromhex('4881c478010000b809000000c3'),
                'AionSpeechMessage':bytes.fromhex('488b442428c3'),
                'AionSpeechTick':bytes.fromhex('4881c408040000b807000000c3'),
                'AionSpeechSkin':bytes.fromhex('488bc74883c4205fc3'),
                'AionSpeechArtwork':bytes.fromhex('488bc74881c4800000005fc3'),
            }[h['export']]
            C.memmove(mem+site+len(original),continuation,len(continuation))
        by={h['export']:int(h['site'],16) for h in manifest['hooks']}
        check(F(mem+by['AionSpeechMenuCommand'])(11,22,33,44)==1,'click consumed')
        result['click']=0
        check(F(mem+by['AionSpeechMenuCommand'])(11,22,33,44)==0,'native click continues')
        check(F(mem+by['AionSpeechTick'])(11,22,33,44)==7,'tick continues with stack intact')
        check(F(mem+by['AionSpeechMenuRefresh'])(11,22,33,44)==9,'popup refresh continues with stack intact')
        check(records['AionSpeechMenuRefresh'][:2]==(11,22),'refresh receives popup and cached model')
        M=C.CFUNCTYPE(C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t)
        result['message']=C.addressof(message)+16
        check(M(mem+by['AionSpeechMessage'])(11,0,0,99,C.addressof(message),C.addressof(body),0,1)==result['message'],'sender name replacement')
        check(records['AionSpeechMessage']==(C.addressof(message),0,99,C.addressof(body)),'message ABI')
        result['message']=0
        check(M(mem+by['AionSpeechMessage'])(11,0,0,99,C.addressof(message),C.addressof(body),0,1)==1,'ack consumed')
        # Skin hook is reached by JMP inside an aligned native stack frame.
        wrapper=mem+size-0x1000;site=by['AionSpeechSkin']
        code=bytes.fromhex('574883ec20488bc1')+b'\xe9'+struct.pack('<i',mem+site-(wrapper+8+5))
        C.memmove(wrapper,code,len(code));bubble=C.create_string_buffer(40);node=C.create_string_buffer(40)
        C.c_size_t.from_buffer(node,0x20).value=C.addressof(bubble)
        check(F(wrapper)(C.addressof(node),0,0,0)==3,'skin result and preserved stack')
        check(records['AionSpeechSkin'][0]==C.addressof(bubble),'skin gets actual bubble')
        # Popup construction also runs inside an aligned native stack frame.
        site=by['AionSpeechMenuBuild'];wrapper+=0x100
        code=bytes.fromhex('574883ec20488bf9')+b'\xe9'+struct.pack('<i',mem+site-(wrapper+8+5))
        C.memmove(wrapper,code,len(code));parent=C.create_string_buffer(0x1c8)
        F(wrapper)(C.addressof(parent),0,0,0)
        check(records['AionSpeechMenuBuild'][0]==C.addressof(parent),'menu receives real parent item')
        check(C.c_int.from_buffer(parent,0x1a4).value==1,'native separator and popup continuation preserved')
        site=by['AionSpeechArtwork'];wrapper+=0x100
        prefix=bytes.fromhex('574881ec80000000488bf9')
        code=prefix+b'\xe9'+struct.pack('<i',mem+site-(wrapper+len(prefix)+5))
        C.memmove(wrapper,code,len(code))
        native=C.create_string_buffer(8)
        check(F(wrapper)(C.addressof(native),0,0,0)==C.addressof(renderer),'custom renderer replaces native rdi')
        check(records['AionSpeechArtwork'][0]==C.addressof(native),'artwork receives selected renderer')
    finally: kernel.VirtualFree(mem,0,0x8000)
    print('Seven native hooks executed: artwork replacement, stack, register arguments, popup parent/model, native continuations, and metadata removal passed.')

    ini=package/'SpeechBubbles.ini'
    if ini.exists(): ini.unlink()
    dll=C.WinDLL(str(package/'bin64/AionSpeechBubbles.dll'))
    recv=dll.AionSpeechMessage;recv.argtypes=[C.c_wchar_p,C.c_int,C.c_ulong,C.c_wchar_p];recv.restype=C.c_void_p
    skin=dll.AionSpeechSkin;skin.argtypes=[C.c_void_p];skin.restype=C.c_int
    for style,expected in enumerate([0,2,1,3,4]):
        name=C.create_unicode_buffer(f'~ASB1:{style}:Babe')
        ptr=recv(name,0,101,'Hello')
        check(C.wstring_at(ptr)=='Babe','metadata removed from visible name')
        bubble=C.create_string_buffer(40);C.c_int.from_buffer(bubble,0x10).value=0;C.c_ulong.from_buffer(bubble,0x14).value=101
        check(skin(bubble)==expected,'character skin '+str(style))
        C.c_int.from_buffer(bubble,0x10).value=3
        check(skin(bubble)==3,'NPC thought appearance preserved')
    name=C.create_unicode_buffer('~ASB1:3:Babe')
    check(not recv(name,0,101,'~ASB_ACK1~'),'ack stays out of chat')
    for s in ['', '~','~ASB1:', '~ASB1:9:Babe','Babe']:
        name=C.create_unicode_buffer(s);check(recv(name,0,101,'hello')==C.addressof(name),'ordinary or invalid name unchanged')
    name=C.create_unicode_buffer('~ASB1:2:Babe');check(recv(name,4,101,'hello')==C.addressof(name),'whisper unchanged')
    print('Five native styles, stripped metadata, hidden acknowledgements, malformed names, and untouched NPC/whisper behavior passed.')
    # Unrelated native menu commands continue without consuming the event.
    current=C.create_string_buffer(b'font_size_large')
    namefn=C.CFUNCTYPE(C.c_void_p,C.c_void_p)(lambda w:C.addressof(current))
    vt=(C.c_void_p*110)();vt[0xa8//8]=C.cast(namefn,C.c_void_p).value
    widget=C.c_void_p(C.addressof(vt));event=C.c_void_p(C.addressof(widget))
    click=dll.AionSpeechMenuCommand;click.argtypes=[C.c_void_p,C.c_void_p];click.restype=C.c_int
    check(click(None,C.byref(event))==0,'Font Size remains native')
    current=C.create_string_buffer(b'speech_bubble_2')
    check(click(None,C.byref(event))==1,'bubble style choice handled')
    check(not ini.exists(),'no local override/preferences are created')
    print('Native menu selection and existing menu passthrough passed; no local override exists.')
if __name__=='__main__':main()
