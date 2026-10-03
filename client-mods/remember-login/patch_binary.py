"""Append an import bridge; replace three verified instruction sequences only."""
import struct
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'transmog-menu'))
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'speech-bubbles'))
from patch_game_dll import Assembler
from loader_permissions import writable_iats

SITES=[(0x79e7c2,bytes.fromhex('488b5c2460'),'AionRememberLoad'),
       (0x79ec50,bytes.fromhex('4883ec5848895c2468'),'AionRememberClick'),
       (0x4f0c0,bytes.fromhex('4889542410'),'AionRememberAction')]
def align(v,n):return (v+n-1)//n*n
def layout(data):
    pe=struct.unpack_from('<I',data,60)[0];opt=pe+24
    count=struct.unpack_from('<H',data,pe+6)[0];table=opt+struct.unpack_from('<H',data,pe+20)[0]
    return pe,opt,count,table,[struct.unpack_from('<8sIIIIIIHHI',data,table+i*40) for i in range(count)]
def offset(sections,rva):
    for s in sections:
        if s[2]<=rva<s[2]+s[3]:return s[4]+rva-s[2]
    raise ValueError('Unmapped RVA '+hex(rva))
def patch(source):
    data=bytearray(source);pe,opt,count,table,sections=layout(data)
    if any(s[0].rstrip(b'\0')==b'.rlogin' for s in sections):raise ValueError('Remember login already installed')
    header=table+count*40
    if header+40>struct.unpack_from('<I',data,opt+60)[0] or any(data[header:header+40]):raise ValueError('No spare PE header')
    sa,fa=struct.unpack_from('<II',data,opt+32);rva=align(max(s[2]+max(s[1],s[3]) for s in sections),sa);raw=align(len(data),fa)
    imports=bytearray();c=offset(sections,struct.unpack_from('<I',data,opt+120)[0])
    while any(data[c:c+20]):imports+=data[c:c+20];c+=20
    payload=bytearray(imports+bytes(40));descriptor=len(imports)
    def append(b,boundary=1):
        payload.extend(bytes(align(len(payload),boundary)-len(payload)));address=rva+len(payload);payload.extend(b);return address
    module=append(b'AionRememberLogin.dll\0');names=[append(b'\0\0'+s[2].encode()+b'\0',2) for s in SITES]
    lookup=append(struct.pack('<4Q',*names,0),8);iat=append(struct.pack('<4Q',*names,0),8)
    struct.pack_into('<IIIII',payload,descriptor,lookup,0,0,module,iat);hooks=[]
    for index,(site,original,export) in enumerate(SITES):
        pos=offset(sections,site)
        if data[pos:pos+len(original)]!=original:raise ValueError('Unexpected native hook '+hex(site))
        hook=append(b'',16);a=Assembler(hook);reserve=0x110 if index==0 else 0x118
        a.emit(b'\x48\x81\xec'+struct.pack('<I',reserve))
        a.emit(bytes.fromhex('488944242048894c242848895424304c894424384c894c24404c895424484c895c24509c584889442458'))
        for reg in range(6):a.emit(b'\xf3\x0f\x7f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
        if index==0:a.emit(bytes.fromhex('488bcb'))
        a.relative(b'\xff\x15',iat+index*8)
        if index==1:a.emit(bytes.fromhex('898424d0000000'))
        for reg in range(6):a.emit(b'\xf3\x0f\x6f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
        a.emit(bytes.fromhex('488b442458509d488b442420488b4c2428488b5424304c8b4424384c8b4c24404c8b5424484c8b5c2450'))
        if index==1:
            a.emit(bytes.fromhex('83bc24d000000000'));a.branch(b'\x0f\x84','original')
            a.emit(b'\x48\x81\xc4'+struct.pack('<I',reserve)+bytes.fromhex('b801000000c3'));a.label('original')
        a.emit(b'\x48\x81\xc4'+struct.pack('<I',reserve));a.emit(original);a.relative(b'\xe9',site+len(original))
        payload+=a.finish();data[pos:pos+len(original)]=b'\xe9'+struct.pack('<i',hook-site-5)+b'\x90'*(len(original)-5)
        hooks.append(dict(site=site,hook=hook,export=export,original=original.hex(),size=len(a.code),iat=iat+index*8))
    vs=len(payload);rs=align(vs,fa);payload.extend(bytes(rs-vs));data.extend(bytes(raw-len(data)));data.extend(payload)
    struct.pack_into('<8sIIIIIIHHI',data,header,b'.rlogin\0',vs,rva,rs,raw,0,0,0,0,0x60000020)
    struct.pack_into('<H',data,pe+6,count+1);struct.pack_into('<I',data,opt+56,align(rva+vs,sa))
    struct.pack_into('<I',data,opt+4,struct.unpack_from('<I',data,opt+4)[0]+rs);struct.pack_into('<I',data,opt+64,0)
    struct.pack_into('<II',data,opt+120,rva,len(imports)+40)
    return writable_iats(bytes(data))[0],hooks
