"""Stage a passive browser diagnostic without rebuilding existing client hooks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'browser-replacement'))
from audit import PE

AWESOMIUM='618b62df03032a94cdef5800f6de92a1f0b297234efc54ccb6a82fe5f086ceff'
SITES=(0x604ae0,0x131ef0)
def sha(data):return hashlib.sha256(data).hexdigest()
def align(n,a):return (n+a-1)//a*a

def patch(source):
    pe=PE(source);data=bytearray(source);opt=pe.optional;header=opt+pe.u16(opt-4)
    count=pe.u16(opt-18);new_header=header+count*40
    if new_header+40>pe.u32(opt+60) or any(data[new_header:new_header+40]):raise ValueError('No spare section header')
    prior=[]
    for site in SITES:
        off=pe.offset(site)
        if data[off]!=0xe9 or data[off+5:off+10]!=b'\x90'*5:raise ValueError('Expected installed native icon bridge gates')
        destination=site+5+struct.unpack_from('<i',data,off+1)[0]
        if data[pe.offset(destination):pe.offset(destination)+4]!=bytes.fromhex('4883ec48'):raise ValueError('Unknown existing gate')
        prior.append(destination)
    if 'AionBrowserProbe.dll' in pe.imports():raise ValueError('Already instrumented')
    section_align,file_align=struct.unpack_from('<II',data,opt+32)
    rva=align(max(s[2]+max(s[1],s[3]) for s in pe.sections),section_align);raw=align(len(data),file_align)
    old_imports=pe.u32(opt+120);pos=pe.offset(old_imports);imports=bytearray()
    while any(data[pos:pos+20]):imports+=data[pos:pos+20];pos+=20
    payload=bytearray(imports+bytes(40))
    def add(value,alignment=1):
        payload.extend(bytes(align(len(payload),alignment)-len(payload)))
        location=rva+len(payload);payload.extend(value);return location
    module=add(b'AionBrowserProbe.dll\0')
    init=add(b'\0\0AionBrowserProbeInitialize\0',2);tick=add(b'\0\0AionBrowserProbeTick\0',2)
    lookup=add(struct.pack('<QQQ',init,tick,0),8);iat=add(struct.pack('<QQQ',init,tick,0),8)
    struct.pack_into('<IIIII',payload,len(imports),lookup,0,0,module,iat)
    entries=[]
    for i,old_gate in enumerate(prior):
        gate=add(b'',16);code=bytearray.fromhex('4883ec4848894c242048895424284c894424304c894c2438')
        call=gate+len(code);code+=b'\xff\x15'+struct.pack('<i',iat+i*8-(call+6))
        code+=bytes.fromhex('488b4c2420488b5424284c8b4424304c8b4c24384883c448')
        code+=b'\xe9'+struct.pack('<i',old_gate-(gate+len(code)+5))
        payload.extend(code);entries.append(gate)
    size=len(payload);raw_size=align(size,file_align);payload+=bytes(raw_size-size)
    data+=bytes(raw-len(data));data+=payload
    struct.pack_into('<8sIIIIIIHHI',data,new_header,b'.bprobe\0',size,rva,raw_size,raw,0,0,0,0,0xe0000060)
    struct.pack_into('<H',data,opt-18,count+1)
    struct.pack_into('<I',data,opt+56,align(rva+size,section_align))
    struct.pack_into('<I',data,opt+4,pe.u32(opt+4)+raw_size)
    struct.pack_into('<I',data,opt+8,pe.u32(opt+8)+raw_size)
    struct.pack_into('<I',data,opt+64,0)
    struct.pack_into('<II',data,opt+120,rva,len(imports)+40)
    for site,gate in zip(SITES,entries):data[pe.offset(site):pe.offset(site)+5]=b'\xe9'+struct.pack('<i',gate-(site+5))
    # Every byte outside headers and the two five-byte gate jumps is preserved.
    allowed=set(range(opt-18,opt-16))|set(range(opt+4,opt+12))|set(range(opt+56,opt+60))|set(range(opt+64,opt+68))|set(range(opt+120,opt+128))|set(range(new_header,new_header+40))
    for site in SITES:allowed.update(range(pe.offset(site),pe.offset(site)+5))
    assert all(a==b or i in allowed for i,(a,b) in enumerate(zip(source,data)))
    old=pe.imports();new=PE(data).imports()
    for name,value in old.items():assert new[name]==value,name
    return bytes(data),dict(priorGates=prior,newGates=entries,iat=iat,changedOriginalOffsets=sorted(i for i,(a,b) in enumerate(zip(source,data)) if a!=b))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--client',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    client=args.client.resolve();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True);target=out/'bin64';target.mkdir(exist_ok=True)
    if sha((client/'bin64/Awesomium.dll').read_bytes())!=AWESOMIUM:raise ValueError('Unexpected browser build')
    source=(client/'bin64/Game.dll').read_bytes();patched,details=patch(source);(target/'Game.dll').write_bytes(patched)
    work=out/'build';work.mkdir(exist_ok=True)
    vc=Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    script=work/'compile.cmd';cpp=Path(__file__).with_name('frame_probe.cpp')
    script.write_text(f'@echo off\ncall "{vc}" >nul && cl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"{work / "frame_probe.obj"}" "{cpp}" /link /OUT:"{target / "AionBrowserProbe.dll"}" /IMPLIB:"{work / "frame_probe.lib"}"\n',encoding='utf-8')
    subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True)
    from graphics_compat import prepare
    extras,compat=prepare(client,out,source,patched)
    manifest=dict(kind='passive-browser-diagnostic-not-a-flash-fix',client=str(client),originalGameSha256=sha(source),awesomiumSha256=AWESOMIUM,files={f.name:sha(f.read_bytes()) for f in target.iterdir() if f.suffix.lower()=='.dll'},graphicsTrackingFiles=extras,graphicsCompatibility=compat,**details)
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('OK: staged two diagnostic DLLs; existing client files untouched; imports and all unrelated hook bytes preserved')
if __name__=='__main__':main()
