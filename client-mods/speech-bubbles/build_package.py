"""Incremental, hash-checked native Speech Bubbles patch for Aion 4.8 NA x64."""
import argparse, copy, hashlib, io, json, os, struct, subprocess, sys, zipfile
from pathlib import Path
from xml.etree import ElementTree as ET
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'expanded-warehouse'))
sys.path.insert(0,str(HERE.parent/'transmog-menu'))
from codec import read_pak,encode_pak,binary_xml,encode_binary_xml
from patch_game_dll import Assembler
from loader_permissions import writable_iats
EXPECTED='abc96d9b8410a565861cb156265f88010770b50ef9ff3b82d25fb61f8c5102d2'
SITES=[
    (0x6736b0,bytes.fromhex('c787a401000001000000'),'AionSpeechMenuBuild'),
    (0x535e70,bytes.fromhex('488bc44883ec38'),'AionSpeechMenuCommand'),
    (0x5365b0,bytes.fromhex('488bc44881ec78010000'),'AionSpeechMenuRefresh'),
    (0x73480,bytes.fromhex('4489442418b8b82b0000'),'AionSpeechMessage'),
    (0x4a2a83,bytes.fromhex('488b482048637910'),'AionSpeechSkin'),
    (0x4a2b18,bytes.fromhex('488b07488d542460'),'AionSpeechArtwork'),
    (0x4a0780,bytes.fromhex('48894c24084881ec08040000'),'AionSpeechTick'),
]
def sha(b): return hashlib.sha256(b).hexdigest()
def align(v,n): return (v+n-1)//n*n

def patch(data,expected=EXPECTED):
    if sha(data)!=expected: raise ValueError('Game.dll changed; recheck existing client patches before rebuilding.')
    data=bytearray(data);pe=struct.unpack_from('<I',data,60)[0];opt=pe+24
    if data[pe:pe+6]!=b'PE\0\0\x64\x86': raise ValueError('Expected x64 PE')
    count=struct.unpack_from('<H',data,pe+6)[0];table=opt+struct.unpack_from('<H',data,pe+20)[0]
    sections=[struct.unpack_from('<8sIIIIIIHHI',data,table+i*40) for i in range(count)]
    def offset(rva):
        for s in sections:
            if s[2]<=rva<s[2]+s[3]: return s[4]+rva-s[2]
        raise ValueError('Unmapped RVA')
    header=table+count*40
    if header+40>struct.unpack_from('<I',data,opt+60)[0] or any(data[header:header+40]): raise ValueError('No spare PE header')
    section_align,file_align=struct.unpack_from('<II',data,opt+32)
    rva=align(max(s[2]+max(s[1],s[3]) for s in sections),section_align);raw=align(len(data),file_align)
    imports=bytearray();cursor=offset(struct.unpack_from('<I',data,opt+120)[0])
    while any(data[cursor:cursor+20]): imports+=data[cursor:cursor+20];cursor+=20
    payload=bytearray(imports+bytes(40));descriptor=len(imports)
    def append(b,boundary=1):
        payload.extend(bytes(align(len(payload),boundary)-len(payload)))
        address=rva+len(payload);payload.extend(b);return address
    module=append(b'AionSpeechBubbles.dll\0')
    names=[append(b'\0\0'+s[2].encode()+b'\0',2) for s in SITES]
    lookup=append(struct.pack('<'+'Q'*(len(names)+1),*names,0),8)
    iat=append(struct.pack('<'+'Q'*(len(names)+1),*names,0),8)
    struct.pack_into('<IIIII',payload,descriptor,lookup,0,0,module,iat)
    hooks=[]
    for index,(site,original,export) in enumerate(SITES):
        pos=offset(site)
        if data[pos:pos+len(original)]!=original: raise ValueError('Unexpected hook prologue')
        hook=append(b'',16);a=Assembler(hook)
        # 0x118 bytes: shadow space, all volatile integer and SSE registers, flags.
        # Entry hooks start rsp=8 mod16. The skin hook is inside an aligned frame.
        reserve=0x110 if export in ('AionSpeechSkin','AionSpeechMenuBuild','AionSpeechArtwork') else 0x118
        a.emit(b'\x48\x81\xec'+struct.pack('<I',reserve))
        a.emit(bytes.fromhex('488944242048894c242848895424304c894424384c894c24404c895424484c895c2450'))
        a.emit(bytes.fromhex('9c584889442458'))
        for reg in range(6): a.emit(b'\xf3\x0f\x7f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
        if export=='AionSpeechMessage':
            a.emit(b'\x48\x8b\x8c\x24'+struct.pack('<I',reserve+0x28)) # sender name, fifth argument
            a.emit(bytes.fromhex('458bc1')) # r8d=sender object id from r9d
            a.emit(b'\x4c\x8b\x8c\x24'+struct.pack('<I',reserve+0x30)) # sixth argument: message
        elif export=='AionSpeechSkin': a.emit(bytes.fromhex('488b442420488b4820')) # recover native map entry after flags save
        elif export=='AionSpeechMenuBuild': a.emit(bytes.fromhex('488bcf')) # native parent popup item
        elif export=='AionSpeechArtwork':
            a.emit(bytes.fromhex('488bcf')+b'\x48\x8d\x94\x24'+struct.pack('<I',reserve+0x60))
        a.relative(b'\xff\x15',iat+index*8)
        if export=='AionSpeechMessage':
            a.emit(b'\x48\x89\x84\x24'+struct.pack('<I',reserve+0x28))
            a.emit(bytes.fromhex('48898424d0000000')) # null result consumes extension acknowledgements
        elif export=='AionSpeechSkin': a.emit(bytes.fromhex('4863f8')) # chosen skin index -> rdi
        elif export=='AionSpeechArtwork': a.emit(bytes.fromhex('488bf8')) # custom renderer -> rdi
        elif export=='AionSpeechMenuCommand': a.emit(bytes.fromhex('898424d0000000')) # outside SSE saves
        for reg in range(6): a.emit(b'\xf3\x0f\x6f'+bytes([0x84+reg*8,0x24])+struct.pack('<I',0x60+reg*16))
        a.emit(bytes.fromhex('488b442458509d488b442420488b4c2428488b5424304c8b4424384c8b4c24404c8b5424484c8b5c2450'))
        if export=='AionSpeechMenuCommand':
            a.emit(bytes.fromhex('83bc24d000000000'));a.branch(b'\x0f\x84','original')
            a.emit(b'\x48\x81\xc4'+struct.pack('<I',reserve)+bytes.fromhex('b801000000c3'));a.label('original')
        elif export=='AionSpeechMessage':
            a.emit(bytes.fromhex('4883bc24d000000000'));a.branch(b'\x0f\x85','original')
            a.emit(b'\x48\x81\xc4'+struct.pack('<I',reserve)+bytes.fromhex('b801000000c3'));a.label('original')
        a.emit(b'\x48\x81\xc4'+struct.pack('<I',reserve))
        if export=='AionSpeechSkin': a.emit(bytes.fromhex('488b4820')) # preserve native rcx and selected rdi
        else: a.emit(original)
        a.relative(b'\xe9',site+len(original));payload+=a.finish()
        data[pos:pos+len(original)]=b'\xe9'+struct.pack('<i',hook-site-5)+b'\x90'*(len(original)-5)
        hooks.append({'site':hex(site),'hook':hex(hook),'export':export,'original':original.hex(),'size':len(a.code)})
    virtual_size=len(payload);raw_size=align(virtual_size,file_align);payload.extend(bytes(raw_size-len(payload)))
    data.extend(bytes(raw-len(data)));data.extend(payload)
    struct.pack_into('<8sIIIIIIHHI',data,header,b'.asb\0\0\0\0',virtual_size,rva,raw_size,raw,0,0,0,0,0x60000020)
    struct.pack_into('<H',data,pe+6,count+1);struct.pack_into('<I',data,opt+56,align(rva+virtual_size,section_align))
    struct.pack_into('<I',data,opt+4,struct.unpack_from('<I',data,opt+4)[0]+raw_size)
    struct.pack_into('<I',data,opt+64,0);struct.pack_into('<II',data,opt+120,rva,len(imports)+40)
    # The loader now processes descriptors in .asb. Preserved destinations in
    # earlier addon sections must remain writable too (not just the newest IAT).
    return writable_iats(bytes(data))[0],hooks

def ui(source,name,target):
    # The selector is native popup code. Chat Options needs no extra widgets.
    z=read_pak(source);root=binary_xml(z.read(name))
    if root.get('name')!='chat_option_dialog' or root.find(".//Widget[@name='speech_scope']") is not None:
        raise ValueError('Expected the original Chat Options layout')
    data=source.read_bytes();target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    return data,len(z.namelist())

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--client',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();client=args.client.resolve();out=args.output.resolve()
    if out==client or client in out.parents: raise ValueError('Stage outside the installed client')
    out.mkdir(parents=True,exist_ok=True);work=out.parent/(out.name+'-compile');work.mkdir(exist_ok=True)
    vcvars=Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    dll=out/'bin64/AionSpeechBubbles.dll';dll.parent.mkdir(parents=True,exist_ok=True)
    script=work/'compile.cmd'
    script.write_text(f'@echo off\ncall "{vcvars}" >nul\ncl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"{work / "speech.obj"}" "{HERE / "speech_bubbles.cpp"}" /link /OUT:"{dll}" /IMPLIB:"{work / "speech.lib"}"\n')
    subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True)
    changed,hooks=patch((client/'bin64/Game.dll').read_bytes());(out/'bin64/Game.dll').write_bytes(changed)
    files=[{'path':'bin64/Game.dll','original':sha((client/'bin64/Game.dll').read_bytes()),'installed':sha(changed)},
           {'path':'bin64/AionSpeechBubbles.dll','original':None,'installed':sha(dll.read_bytes())}]
    preserved={}
    for rel,name in [('Data/ui/game/game.pak','chat_option_dialog.xml'),('L10N/enu/Data/data.pak','ui/game/chat_option_dialog.xml')]:
        data,unchanged=ui(client/rel,name,out/rel);preserved[rel]=unchanged
        files.append({'path':rel,'original':sha((client/rel).read_bytes()),'installed':sha(data)})
    from artwork import ASSETS,patch_resources,make_skins,rewrite,skins_xml
    artout=out/'artwork';artout.mkdir(parents=True,exist_ok=True)
    resources=patch_resources(client/ASSETS[0],client/ASSETS[1],out/'L10N/enu/Data/data.pak',artout)
    for rel,data in resources.items():
        target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        record=next((e for e in files if e['path']==rel),None)
        if record is not None:record['installed']=sha(data)
        else:files.append({'path':rel,'original':sha((client/rel).read_bytes()),'installed':sha(data)})
    definitions,_=make_skins(artout)
    def art_ui(source,name,target):
        data,count=ui(source,name,target)
        if name=='ui/game/chat_option_dialog.xml':
            data=rewrite(target,{'ui/ui_preload.xml':skins_xml(read_pak(target).read('ui/ui_preload.xml'),definitions)})
            target.write_bytes(data)
        return data,count
    from graphics_compatibility import prepare
    compatibility=prepare(client,out,files,patch,art_ui)
    manifest={'clientRoot':str(client),'architecture':'x64','files':files,'hooks':hooks,'unchangedEntries':preserved,'styles':['Classic','Wings','Crystal','Cloud','Paws'],'artworkArchives':list(ASSETS)}
    if compatibility: manifest['graphicsCompatibility']=compatibility
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2));print(json.dumps(manifest,indent=2))
if __name__=='__main__': main()
