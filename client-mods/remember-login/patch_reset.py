"""Bounded observer for the reused login dialog's native Reset lifecycle."""
import struct
from patch_binary import layout,offset,Assembler,align
SITE=0x79e7f0
ORIGINAL=bytes.fromhex('40534883ec20')
HOOK=0x144eb80
LIMIT=0x144ed00
SAFE_HOOK=0x190f4c0

def code(iat,hook=HOOK):
    a=Assembler(hook);reserve=0x118
    a.emit(b'\x48\x81\xec'+struct.pack('<I',reserve))
    a.emit(bytes.fromhex('488944242048894c242848895424304c894424384c894c24404c895424484c895c24509c584889442458'))
    for reg in range(6):a.emit(b'\xf3\x0f\x7f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
    a.emit(bytes.fromhex('488b54242833c9'))  # Visibility(nullptr, actual Reset dialog)
    a.relative(b'\xff\x15',iat)
    for reg in range(6):a.emit(b'\xf3\x0f\x6f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
    a.emit(bytes.fromhex('488b442458509d488b442420488b4c2428488b5424304c8b4424384c8b4c24404c8b5424484c8b5c2450'))
    a.emit(b'\x48\x81\xc4'+struct.pack('<I',reserve));a.emit(ORIGINAL);a.relative(b'\xe9',SITE+len(ORIGINAL))
    return a.finish()

def patch(source,receipt):
    data=bytearray(source);_,_,_,_,ss=layout(data)
    old=next(h for h in receipt['hooks'] if h['export']=='AionRememberVisibility')
    for h in receipt['hooks']:
        original=bytes.fromhex(h['original']);expected=b'\xe9'+struct.pack('<i',h['hook']-h['site']-5)+b'\x90'*(len(original)-5)
        pos=offset(ss,h['site'])
        if data[pos:pos+len(expected)]!=expected:raise ValueError('Existing login observers changed')
    # Check that the exact existing IAT still names the intended export.
    hint=struct.unpack_from('<Q',data,offset(ss,old['iat']))[0]
    at=offset(ss,hint)+2
    if data[at:at+len(b'AionRememberVisibility\0')]!=b'AionRememberVisibility\0':raise ValueError('Wrong observer import')
    at=offset(ss,SITE)
    if data[at:at+len(ORIGINAL)]!=ORIGINAL:raise ValueError('Native login Reset changed')
    payload=code(old['iat'],SAFE_HOOK)
    reserve_storage(data,payload)
    data[at:at+len(ORIGINAL)]=b'\xe9'+struct.pack('<i',SAFE_HOOK-SITE-5)+b'\x90'
    return bytes(data),dict(site=SITE,hook=SAFE_HOOK,size=len(payload),iat=old['iat'],export='AionRememberVisibility',mode='native-reset-section',original=ORIGINAL.hex())

def reserve_storage(data,payload,hook=SAFE_HOOK):
    # Only extend the dedicated patch section's unused file-alignment tail.
    # Zero bytes in the stock image are not proof of unused runtime storage.
    _,_,_,table,ss=layout(data)
    index=next(i for i,s in enumerate(ss) if s[0].rstrip(b'\0')==b'.rreturn')
    section=ss[index];end=hook+len(payload)
    if (section[2],section[1]) not in ((0x190f000,0x4b1),(0x190e000,0x491)) or section[9]&0x60000020!=0x60000020:
        raise ValueError('Dedicated return section changed')
    if hook!=align(section[2]+section[1],16) or end>section[2]+section[3]:raise ValueError('No owned section tail available')
    if any(data[offset(ss,section[2]+section[1]):offset(ss,end)]):raise ValueError('Return section tail changed')
    struct.pack_into('<I',data,table+index*40+8,end-section[2])
    data[offset(ss,hook):offset(ss,end)]=payload

def relocate(source,receipt):
    """Repair only the exact installed legacy Reset hook, retaining all mods."""
    data=bytearray(source);ss=layout(data)[-1]
    old=next(h for h in receipt['hooks'] if h['export']=='AionRememberVisibility')
    payload=code(old['iat']);at=offset(ss,SITE);cave=offset(ss,HOOK)
    expected=b'\xe9'+struct.pack('<i',HOOK-SITE-5)+b'\x90'
    if data[at:at+len(ORIGINAL)]!=expected or data[cave:cave+len(payload)]!=payload:
        raise ValueError('Installed legacy Reset hook changed')
    # Reuse all import/native ownership checks in patch after removing just
    # our legacy bytes in memory. No installed files are changed here.
    data[at:at+len(ORIGINAL)]=ORIGINAL
    data[cave:cave+len(payload)]=bytes(len(payload))
    return patch(bytes(data),receipt)

def relocate_baseline(source,legacy_iat):
    """Relocate the verified legacy copy using this restore image's own IAT."""
    data=bytearray(source);_,opt,_,_,ss=layout(data)
    at=offset(ss,SITE);cave=offset(ss,HOOK);legacy=code(legacy_iat)
    if data[at:at+len(ORIGINAL)]!=b'\xe9'+struct.pack('<i',HOOK-SITE-5)+b'\x90' or data[cave:cave+len(legacy)]!=legacy:
        raise ValueError('Restore image legacy Reset hook changed')
    section=next(s for s in ss if s[0].rstrip(b'\0')==b'.rreturn')
    descriptor=offset(ss,struct.unpack_from('<I',data,opt+120)[0]);iats=[]
    while any(data[descriptor:descriptor+20]):
        lookup,_,_,name,iat=struct.unpack_from('<IIIII',data,descriptor);descriptor+=20
        pos=offset(ss,name);end=data.index(0,pos)
        if data[pos:end]!=b'AionRememberLogin.dll':continue
        for index in range(32):
            value=struct.unpack_from('<Q',data,offset(ss,lookup)+index*8)[0]
            if not value:break
            if value>>63:continue
            pos=offset(ss,value)+2;end=data.index(0,pos)
            if data[pos:end]==b'AionRememberVisibility':iats.append(iat+index*8)
    if len(iats)!=1:raise ValueError('Expected one native Visibility import')
    iat=iats[0];hook=align(section[2]+section[1],16)
    payload=code(iat,hook);reserve_storage(data,payload,hook)
    data[cave:cave+len(legacy)]=bytes(len(legacy))
    data[at:at+len(ORIGINAL)]=b'\xe9'+struct.pack('<i',hook-SITE-5)+b'\x90'
    return bytes(data),dict(site=SITE,hook=hook,size=len(payload),iat=iat,export='AionRememberVisibility',mode='native-reset-section',original=ORIGINAL.hex())
