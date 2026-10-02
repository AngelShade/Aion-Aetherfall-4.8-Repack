"""Read-only native API registration lookup for this supported client."""
import sys,struct
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'speech-bubbles'))
import inspect_client as m
from capstone import Cs,CS_ARCH_X86,CS_MODE_64
for name in sys.argv[1:]:
    wide=name.startswith('W:');name=name[2:] if wide else name
    needle=name.encode('utf-16le')+b'\0\0' if wide else name.encode()+b'\0';pos=m.b.find(needle)
    if pos<0:print(name,'not present');continue
    rva=m.rva(pos);print(name,'string',hex(rva))
    refs=[];pattern=struct.pack('<Q',m.base+rva);start=0
    while (p:=m.b.find(pattern,start))>=0:
        print('pointer',hex(m.rva(p)),[hex(v-m.base) for v in struct.unpack_from('<5Q',m.b,p-16)])
        start=p+1
    code=m.sections[0];raw=m.b[code[4]:code[4]+code[3]]
    import re
    for match in re.finditer(rb'[\x48\x4c]\x8d[\x05\x0d\x15\x1d\x35\x3d]',raw):
        p=match.start();address=code[2]+p;disp=struct.unpack_from('<i',raw,p+3)[0]
        if address+7+disp==rva:refs.append(address)
    for ref in refs:m.dump(ref-18,58)
