"""Prepare the native Graphics checkbox without replacing any existing mods."""
import copy,hashlib,io,json,os,struct,subprocess,sys,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'expanded-warehouse'))
from codec import read_pak,encode_pak,binary_xml,encode_binary_xml
EXPECTED='037772efbcda5b8be7ada07f38131371719c5431329f27da9c050f7107c9db2a'
SITES=[(0x6f8650,bytes.fromhex('488bc44881ec88000000'),'AionGraphicsClick'),
       (0x6fca60,bytes.fromhex('488bc44883ec68'),'AionGraphicsLoad')]
def sha(b):return hashlib.sha256(b).hexdigest()
def align(v,n):return (v+n-1)//n*n
def patch(data,expected=EXPECTED):
    if sha(data)!=expected:raise ValueError('Unsupported Game.dll; preserve existing patches and inspect the new build first')
    data=bytearray(data);pe=struct.unpack_from('<I',data,60)[0];opt=pe+24
    if data[pe:pe+6]!=b'PE\0\0\x64\x86':raise ValueError('Expected x64 PE')
    count=struct.unpack_from('<H',data,pe+6)[0];table=opt+struct.unpack_from('<H',data,pe+20)[0]
    sections=[struct.unpack_from('<8sIIIIIIHHI',data,table+i*40) for i in range(count)]
    def offset(rva):
        for s in sections:
            if s[2]<=rva<s[2]+s[3]:return s[4]+rva-s[2]
        raise ValueError('Unmapped RVA')
    header=table+count*40
    if header+40>struct.unpack_from('<I',data,opt+60)[0] or any(data[header:header+40]):raise ValueError('No spare PE header')
    section_align,file_align=struct.unpack_from('<II',data,opt+32)
    rva=align(max(s[2]+max(s[1],s[3]) for s in sections),section_align);raw=align(len(data),file_align)
    imports=bytearray();cursor=offset(struct.unpack_from('<I',data,opt+120)[0])
    while any(data[cursor:cursor+20]):imports+=data[cursor:cursor+20];cursor+=20
    payload=bytearray(imports+bytes(40));descriptor=len(imports)
    def append(b,boundary=1):
        payload.extend(bytes(align(len(payload),boundary)-len(payload)));address=rva+len(payload);payload.extend(b);return address
    module=append(b'AionGraphicsMenu.dll\0')
    names=[append(b'\0\0'+s[2].encode()+b'\0',2) for s in SITES]
    lookup=append(struct.pack('<QQQ',*names,0),8);iat=append(struct.pack('<QQQ',*names,0),8)
    struct.pack_into('<IIIII',payload,descriptor,lookup,0,0,module,iat)
    for index,(site,original,export) in enumerate(SITES):
        pos=offset(site)
        if data[pos:pos+len(original)]!=original:raise ValueError('Unexpected hook prologue')
        hook=append(b'',16)
        # Save all four argument registers; stack aligned with 32-byte shadow area.
        code=bytearray.fromhex('4883ec4848894c242048895424284c894424304c894c2438')
        at=hook+len(code);code+=b'\xff\x15'+struct.pack('<i',iat+index*8-at-6)
        code+=bytes.fromhex('488b4c2420488b5424284c8b4424304c8b4c24384883c448')
        if index==0:
            code+=bytes.fromhex('85c07401c3') # handled checkbox returns 1; others replay original
        code+=original
        code+=b'\xe9'+struct.pack('<i',site+len(original)-(hook+len(code)+5))
        payload+=code
        data[pos:pos+len(original)]=b'\xe9'+struct.pack('<i',hook-site-5)+b'\x90'*(len(original)-5)
    virtual_size=len(payload);raw_size=align(virtual_size,file_align);payload.extend(bytes(raw_size-len(payload)))
    data.extend(bytes(raw-len(data)));data.extend(payload)
    struct.pack_into('<8sIIIIIIHHI',data,header,b'.agfx\0\0\0',virtual_size,rva,raw_size,raw,0,0,0,0,0x60000020)
    struct.pack_into('<H',data,pe+6,count+1);struct.pack_into('<I',data,opt+56,align(rva+virtual_size,section_align))
    struct.pack_into('<I',data,opt+4,struct.unpack_from('<I',data,opt+4)[0]+raw_size)
    struct.pack_into('<I',data,opt+64,0);struct.pack_into('<II',data,opt+120,rva,len(imports)+40)
    return bytes(data)
def ui(source,name='global_option_dialog.xml'):
    z=read_pak(source);old=z.read(name);root=binary_xml(old)
    page=root.find(".//Widget[@name='page_screen']")
    if page is None or root.find(".//Widget[@name='cb_use_vulkan']") is not None:raise ValueError('Unsupported graphics layout')
    ET.SubElement(page,'Widget',{'font':'v3_option','frame':'45,145,390,19','name':'cb_use_vulkan','preset':'v5_check','style':'checkbox','text':'Use Vulkan (requires restart)','text_offset':'5,0','type':'button','valign':'middle'})
    ET.SubElement(page,'Widget',{'font':'v3_option','frame':'65,176,350,60','name':'st_vulkan_status','type':'static','flag':'multiline;wordwrap','valign':'top','text':'Restart Aion to change renderer.'})
    replacement=encode_binary_xml(root)
    assert ET.tostring(binary_xml(replacement))==ET.tostring(root)
    buff=io.BytesIO()
    with zipfile.ZipFile(buff,'w',zipfile.ZIP_DEFLATED) as out:
        for entry in z.infolist():out.writestr(copy.copy(entry),replacement if entry.filename==name else z.read(entry))
    result=encode_pak(buff.getvalue())
    # Verify every unrelated UI entry is preserved exactly.
    check=HERE/'build'/'check-ui.pak';check.parent.mkdir(parents=True,exist_ok=True);check.write_bytes(result)
    after=read_pak(check)
    assert z.namelist()==after.namelist()
    for n in z.namelist():assert n==name or z.read(n)==after.read(n)
    return result,len(z.namelist())-1
def main():
    client=Path(sys.argv[1]);out=HERE/'build'/'graphics-menu';out.mkdir(parents=True,exist_ok=True)
    vcvars=Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    script=HERE/'build'/'compile-menu.cmd'
    script.write_text(f'@echo off\ncall "{vcvars}" >nul\ncl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"{out / "menu.obj"}" "{HERE / "graphics_menu.cpp"}" /link /OUT:"{out / "AionGraphicsMenu.dll"}" /IMPLIB:"{out / "menu.lib"}"\n')
    subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True)
    changed={'bin64/Game.dll':patch((client/'bin64/Game.dll').read_bytes())}
    changed['Data/ui/game/game.pak'],preserved=ui(client/'Data/ui/game/game.pak')
    files=[]
    for name,b in changed.items():
        target=out/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
        files.append({'path':name,'original':sha((client/name).read_bytes()),'installed':sha(b)})
    files.append({'path':'bin64/AionGraphicsMenu.dll','original':None,'installed':sha((out/'AionGraphicsMenu.dll').read_bytes())})
    localized=client/'L10N/enu/Data/data.pak'
    if localized.exists():
        b,preserved_localized=ui(localized,'ui/game/global_option_dialog.xml')
        target=out/'L10N/enu/Data/data.pak';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
        files.append({'path':'L10N/enu/Data/data.pak','original':sha(localized.read_bytes()),'installed':sha(b)})
    (out/'bin64/AionGraphicsMenu.dll').write_bytes((out/'AionGraphicsMenu.dll').read_bytes())
    manifest={'architecture':'x64','files':files,'unchangedUiEntries':preserved,'settings':'DXVK/renderer.ini','uiPath':'Options > Graphics > Screen','hooks':[hex(s[0]) for s in SITES]}
    if localized.exists():manifest['unchangedLocalizedEntries']=preserved_localized
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2));print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
