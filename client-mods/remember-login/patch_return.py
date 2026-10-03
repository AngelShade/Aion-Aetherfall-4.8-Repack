"""Add login refresh and visibility observers while retaining native handler bodies."""
import struct
from patch_binary import layout,offset,align,Assembler
from loader_permissions import writable_iats

SITES=[(0x79e820,bytes.fromhex('4881ece8000000'),'AionRememberRefresh'),
       (0x4ae000,bytes.fromhex('4883ec28488b4130'),'AionRememberVisibility')]
def patch(source):
    data=bytearray(source);pe,opt,count,table,ss=layout(data);base=struct.unpack_from('<Q',data,opt+24)[0]
    assert any(s[0].rstrip(b'\0')==b'.rlogin' for s in ss),'Remember Login not installed'
    assert not any(s[0].rstrip(b'\0')==b'.rreturn' for s in ss),'Return patch already installed'
    header=table+count*40
    assert header+40<=struct.unpack_from('<I',data,opt+60)[0] and not any(data[header:header+40]),'No PE header room'
    sa,fa=struct.unpack_from('<II',data,opt+32);rva=align(max(s[2]+max(s[1],s[3]) for s in ss),sa);raw=align(len(data),fa)
    imports=bytearray();p=offset(ss,struct.unpack_from('<I',data,opt+120)[0])
    while any(data[p:p+20]):imports+=data[p:p+20];p+=20
    payload=bytearray(imports+bytes(40));descriptor=len(imports)
    def append(b,boundary=1):
        payload.extend(bytes(align(len(payload),boundary)-len(payload)));at=rva+len(payload);payload.extend(b);return at
    module=append(b'AionRememberLogin.dll\0');names=[append(b'\0\0'+name.encode()+b'\0',2) for _,_,name in SITES]
    lookup=append(struct.pack('<3Q',*names,0),8);iat=append(struct.pack('<3Q',*names,0),8)
    struct.pack_into('<IIIII',payload,descriptor,lookup,0,0,module,iat)
    hooks=[]
    for i,(site,original,name) in enumerate(SITES):
        pos=offset(ss,site);assert data[pos:pos+len(original)]==original,'Native site changed'
        hook=append(b'',16);a=Assembler(hook);reserve=0x118
        a.emit(b'\x48\x81\xec'+struct.pack('<I',reserve))
        a.emit(bytes.fromhex('488944242048894c242848895424304c894424384c894c24404c895424484c895c24509c584889442458'))
        for reg in range(6):a.emit(b'\xf3\x0f\x7f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
        a.relative(b'\xff\x15',iat+i*8)
        for reg in range(6):a.emit(b'\xf3\x0f\x6f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
        a.emit(bytes.fromhex('488b442458509d488b442420488b4c2428488b5424304c8b4424384c8b4c24404c8b5424484c8b5c2450'))
        a.emit(b'\x48\x81\xc4'+struct.pack('<I',reserve));a.emit(original);a.relative(b'\xe9',site+len(original));payload+=a.finish()
        data[pos:pos+len(original)]=b'\xe9'+struct.pack('<i',hook-site-5)+b'\x90'*(len(original)-5)
        hooks.append(dict(site=site,original=original.hex(),size=len(a.code),hook=hook,iat=iat+i*8,export=name))
    vs=len(payload);rs=align(vs,fa);payload.extend(bytes(rs-vs));data.extend(bytes(raw-len(data)));data.extend(payload)
    struct.pack_into('<8sIIIIIIHHI',data,header,b'.rreturn',vs,rva,rs,raw,0,0,0,0,0x60000020)
    struct.pack_into('<H',data,pe+6,count+1);struct.pack_into('<I',data,opt+56,align(rva+vs,sa))
    struct.pack_into('<I',data,opt+4,struct.unpack_from('<I',data,opt+4)[0]+rs);struct.pack_into('<I',data,opt+64,0)
    struct.pack_into('<II',data,opt+120,rva,len(imports)+40)
    return writable_iats(bytes(data))[0],hooks
