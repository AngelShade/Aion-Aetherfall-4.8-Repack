"""Verify archives, exact patch bounds and execute all three native trampolines."""
import ctypes as C,json,struct,sys
from pathlib import Path
from capstone import Cs,CS_ARCH_X86,CS_MODE_64
from prepare import sha,read_pak,binary_xml,login,strings
from patch_binary import patch,layout,offset,SITES
from loader_permissions import writable_iats
def verify(out):
    m=json.loads((out/'manifest.json').read_text());root=Path(m['clientRoot']);assert m['feature']=='remember-login'
    for e in m['files']:
        assert sha((out/e['path']).read_bytes())==e['installed'],e['path']
        assert (sha((root/e['path']).read_bytes()) if e['original'] else None)==e['original'],e['path']
    for e in m['preservedFiles']:assert sha((root/e['path']).read_bytes())==e['sha256'],e['path']
    checks=0
    for rel,entry in [('Data/ui/ui.pak','UI_Login.xml'),('L10N/enu/Data/data.pak','ui/ui_login.xml')]:
        before=read_pak(root/rel);after=read_pak(out/rel);assert before.namelist()==after.namelist() and after.testzip() is None
        for name in before.namelist():
            expected=login(before.read(name)) if name==entry else strings(before.read(name)) if name=='strings/client_strings_ui.xml' else before.read(name)
            assert after.read(name)==expected,(rel,name);checks+=1
        tree=binary_xml(after.read(entry));assert tree.find(".//Widget[@name='remember_login']").get('preset')=='v5_check'
        assert tree.find(".//Widget[@name='password']").get('flag')=='password'
    before=(root/'bin64/Game.dll').read_bytes();after=(out/'bin64/Game.dll').read_bytes();assert patch(before)[0]==after and writable_iats(after)[0]==after
    pe,opt,count,table,ss=layout(before);_,_,_,_,newss=layout(after)
    normalized=bytearray(after[:len(before)])
    allowed=[(pe+6,2),(opt+4,4),(opt+56,4),(opt+64,4),(opt+120,8),(table+count*40,40)]
    allowed +=[(table+i*40+36,4) for i in range(count)]
    allowed +=[(offset(ss,site),len(original)) for site,original,_ in SITES]
    for start,length in allowed:normalized[start:start+length]=before[start:start+length]
    assert bytes(normalized)==before,'Unexpected modification outside PE headers and login hooks'
    # Recovery baselines retain the same new login hooks, so graphics/cursor
    # rollback does not remove remembered login or Season Pass.
    for state in ['DXVK/graphics-menu/installed.json','DXVK/graphics-menu/package/manifest.json']:
        data=json.loads((out/state).read_text());
        for e in data['files']:
            source=out/e['path'] if (out/e['path']).exists() else root/e['path'];assert sha(source.read_bytes())==e['installed']
    own=next(s for s in newss if s[0].rstrip(b'\0')==b'.rlogin')
    kernel=C.WinDLL('kernel32');kernel.VirtualAlloc.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong,C.c_ulong];kernel.VirtualAlloc.restype=C.c_void_p;kernel.VirtualFree.argtypes=[C.c_void_p,C.c_size_t,C.c_ulong]
    size=struct.unpack_from('<I',after,opt+56)[0]+4096;mem=kernel.VirtualAlloc(None,size,0x3000,0x40);assert mem
    F=C.CFUNCTYPE(C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t,C.c_size_t);records={};handled=1
    callbacks=[F(lambda a,b,c,d:records.update(load=a) or 0),F(lambda a,b,c,d:records.update(click=(a,b,c,d)) or handled),F(lambda a,b,c,d:records.update(action=(a,b,c,d)) or 0)]
    try:
        C.memmove(mem+own[2],after[own[4]:own[4]+own[3]],own[3])
        for i,h in enumerate(m['hooks']):
            C.c_size_t.from_address(mem+h['iat']).value=C.cast(callbacks[i],C.c_void_p).value
            site=h['site'];original=bytes.fromhex(h['original']);pos=offset(newss,site);C.memmove(mem+site,after[pos:pos+len(original)],len(original))
            hp=offset(newss,h['hook']);ins=list(Cs(CS_ARCH_X86,CS_MODE_64).disasm(after[hp:hp+h['size']],h['hook']));assert sum(x.size for x in ins)==h['size']
            continuation=[bytes.fromhex('488bc14883c4405bc3'),bytes.fromhex('488b5c24684883c458b82a000000c3'),bytes.fromhex('488bc2c3')][i]
            C.memmove(mem+site+len(original),continuation,len(continuation))
        wrapper=mem+size-4096;prefix=bytes.fromhex('534883ec40488bd948895c2460');code=prefix+b'\xe9'+struct.pack('<i',mem+m['hooks'][0]['site']-(wrapper+len(prefix)+5));C.memmove(wrapper,code,len(code))
        assert F(wrapper)(123,456,789,101)==123 and records['load']==123
        click=F(mem+m['hooks'][1]['site']);assert click(11,22,33,44)==1 and records['click']==(11,22,33,44)
        handled=0;assert click(11,22,33,44)==42
        assert F(mem+m['hooks'][2]['site'])(11,22,33,44)==22 and records['action']==(11,22,33,44)
    finally:kernel.VirtualFree(mem,0,0x8000)
    print('OK:',checks,'archive entries checked; masked password preserved; exact DLL bounds; all three machine-code hooks, stack/register preservation and original-handler delegation; graphics/cursor recovery guards')
if __name__=='__main__':
    try:verify(Path(sys.argv[1]))
    except Exception as e:print('FAIL:',repr(e));sys.exit(1)
