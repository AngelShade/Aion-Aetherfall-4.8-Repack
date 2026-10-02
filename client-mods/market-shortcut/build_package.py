"""Incremental Market HUD shortcut; stage all composed launcher and restore files."""
import sys,os,json,copy,struct,subprocess,re
from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image,ImageEnhance
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'speech-bubbles'))
from artwork import rewrite,skins_xml,read_pak,binary_xml,encode_binary_xml
from patch_binary import patch
from hashlib import sha256
sha=lambda b:sha256(b).hexdigest()
ROOT=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
OUT=Path('output/market-shortcut/package').resolve()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def icon_definitions():
    img=Image.open(HERE/'assets/scales.png').convert('RGBA');img=img.crop(img.getbbox());img.thumbnail((32,34),Image.Resampling.LANCZOS)
    atlas=Image.new('RGBA',(128,64));defs=[]
    for i,(name,factor) in enumerate([('up',1),('over',1.28),('down',.72)]):
        icon=ImageEnhance.Brightness(img).enhance(factor);atlas.alpha_composite(icon,(i*40+(34-img.width)//2,(36-img.height)//2))
        defs.append(ET.Element('Skin',name='mkt_scales_'+name,src_image=f'{i*40},0,34,36',texture='Textures/UI/mkt_scales'))
    preset=ET.Element('Preset',name='mkt_scales_button',type='button')
    for state in ('up','over','down'):ET.SubElement(preset,'SkinRef',main_state='0',name='mkt_scales_'+state,sub_state=state)
    defs.append(preset);atlas.save(OUT/'icon-states.png')
    h=[124,0x100f,64,128,512,0,0]+[0]*11+[32,0x41,0,32,0xff,0xff00,0xff0000,0xff000000,0x1000,0,0,0,0]
    return defs,b'DDS '+struct.pack('<31I',*h)+atlas.tobytes()
def main():
    OUT.mkdir(parents=True,exist_ok=True);definitions,dds=icon_definitions();staged={};hooks={}
    def stage(rel,data):
        if rel in staged and staged[rel]!=data:raise ValueError('Conflicting composition: '+rel)
        staged[rel]=data;target=OUT/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    def transformed(path,rel):
        if rel=='bin64/Game.dll':
            data,record=patch(path.read_bytes());hooks[path.relative_to(ROOT).as_posix()]=record;return data
        if rel=='Data/ui/ui.pak':return rewrite(path,{'UI_Preload.xml':skins_xml(read_pak(path).read('UI_Preload.xml'),definitions)})
        if rel=='Textures/ui/ui.pak':return rewrite(path,{'mkt_scales.dds':dds})
        if rel=='L10N/enu/Data/data.pak':
            z=read_pak(path);replacements={'ui/ui_preload.xml':skins_xml(z.read('ui/ui_preload.xml'),definitions)}
            for style in (1,2):
                name=f'ui/game_hud_s{style}/start_dialog.xml';replacements[name]=hud(z.read(name),style)
            return rewrite(path,replacements)
        return path.read_bytes()
    def hud(data,style):
        t=binary_xml(data)
        if t.get('name')!='start_dialog' or t.find(".//Widget[@name='central_market_button']") is not None:raise ValueError('Unexpected HUD')
        ET.SubElement(t,'Widget',name='central_market_button',type='button',frame='64,53,34,36' if style==1 else '77,35,34,36',flag='visible',preset='mkt_scales_button',tooltip='Central Market')
        return encode_binary_xml(t)
    core=['bin64/Game.dll','Data/ui/ui.pak','Textures/ui/ui.pak','L10N/enu/Data/data.pak']
    for rel in core:stage(rel,transformed(ROOT/rel,rel))
    for style in (1,2):
        rel=f'Data/ui/game_hud_s{style}/game_hud_s{style}.pak';p=ROOT/rel
        stage(rel,rewrite(p,{'start_dialog.xml':hud(read_pak(p).read('start_dialog.xml'),style)}))
    # Keep every existing addon entry and function; move only its menu registration.
    rel='Plugin/RelicCalc/RelicCalc.pak';z=read_pak(ROOT/rel);lua=z.read('PrivateMenus.lua')
    line=re.search(rb'^[ \t]*\{label = "Central Market"[^\n]*\n',lua,re.M)
    if not line:raise ValueError('Expected current Market registration')
    lua=lua[:line.start()]+lua[line.end():];stage(rel,rewrite(ROOT/rel,{'PrivateMenus.lua':lua}))
    subprocess.run(['java',str(HERE.parent/'transmog-menu/SignClientPackages.java'),str(ROOT),str(OUT)],check=True)
    for rel in ['Addon.key','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig','Plugin/RelicCalc/RelicCalc.pak.sig']:stage(rel,(OUT/rel).read_bytes())
    (OUT/'Pub.key').unlink() # The stock model validation key stays byte-identical.
    work=OUT.parent/'compile';work.mkdir(exist_ok=True);dll=OUT/'bin64/AionMarketShortcut.dll'
    vcvars=Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    script=work/'compile.cmd';script.write_text(f'@echo off\ncall "{vcvars}" >nul\ncl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"{work / "market.obj"}" "{HERE / "market_shortcut.cpp"}" /link /OUT:"{dll}" /IMPLIB:"{work / "market.lib"}"\n')
    subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True);stage('bin64/AionMarketShortcut.dll',dll.read_bytes())
    # Apply the feature to graphics/cursor removal baselines as well.
    def metadata(source_prefix):
        prefix=(source_prefix+'/') if source_prefix else ''
        srel=prefix+'DXVK/graphics-menu/installed.json';mrel=prefix+'DXVK/graphics-menu/package/manifest.json';drel=prefix+'DXVK/installed.json'
        state=read(ROOT/srel);manifest=read(ROOT/mrel);dxvk=read(ROOT/drel)
        if state['files']!=manifest['files']:raise ValueError('Graphics tracking differs')
        baseline=Path(state['backupRoot']).resolve()
        if not baseline.is_relative_to(ROOT/'DXVK-backups'):raise ValueError('Unsafe graphics baseline')
        for e in state['files']:
            rel=e['path'];p=ROOT/prefix/rel
            if rel not in core:continue
            if sha(p.read_bytes())!=e['installed']:raise ValueError('Graphics installed hash differs: '+str(p))
            original=baseline/rel
            if e['original'] is not None and sha(original.read_bytes())!=e['original']:raise ValueError('Graphics baseline changed')
            if rel in core:
                b_rel=original.relative_to(ROOT).as_posix()
                if b_rel not in staged:stage(b_rel,transformed(original,rel))
                e['original']=sha(staged[b_rel]);e['installed']=sha(staged[p.relative_to(ROOT).as_posix()])
                package=prefix+'DXVK/graphics-menu/package/'+rel;stage(package,staged[p.relative_to(ROOT).as_posix()])
        cursor=next(e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll');p=Path(cursor['backupPath']).resolve()
        if not p.is_relative_to(ROOT/'DXVK-backups') or sha(p.read_bytes())!=cursor['original'].lower():raise ValueError('Cursor baseline changed')
        rel=p.relative_to(ROOT).as_posix()
        if rel not in staged:stage(rel,transformed(p,'bin64/Game.dll'))
        cursor.update(original=sha(staged[rel]),installed=sha(staged[prefix+'bin64/Game.dll']))
        manifest['files']=copy.deepcopy(state['files'])
        for rel,obj in [(srel,state),(mrel,manifest),(drel,dxvk)]:stage(rel,json.dumps(obj,indent=2).encode())
    metadata('')
    receipt_rel='SpeechBubbles-backups/20261001-203148-743/manifest.json';receipt=read(ROOT/receipt_rel);base=str(Path(receipt_rel).parent).replace('\\','/')
    for e in receipt['files']:
        if sha((ROOT/e['path']).read_bytes())!=e['installed']:raise ValueError('Speech receipt changed: '+e['path'])
        if e['original'] is not None and sha((ROOT/base/e['path']).read_bytes())!=e['original']:raise ValueError('Speech backup changed')
        if e['path'] in core:stage(base+'/'+e['path'],transformed(ROOT/base/e['path'],e['path']))
    metadata(base)
    for e in receipt['files']:
        if e['path'] in staged:e['installed']=sha(staged[e['path']])
        if base+'/'+e['path'] in staged:e['original']=sha(staged[base+'/'+e['path']])
    receipt['marketShortcutCompatible']=True;stage(receipt_rel,json.dumps(receipt,indent=2).encode())
    files=[dict(path=rel,original=sha((ROOT/rel).read_bytes()) if (ROOT/rel).exists() else None,installed=sha(data)) for rel,data in staged.items()]
    result=dict(clientRoot=str(ROOT),feature='central-market-hud',files=files,hooks=hooks,sourceKey=sha((ROOT/'Pub.key').read_bytes()))
    (OUT/'manifest.json').write_text(json.dumps(result,indent=2));print('Prepared',len(files),'incremental files; client untouched.')
if __name__=='__main__':main()
