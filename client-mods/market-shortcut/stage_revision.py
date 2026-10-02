"""Stage outlined HUD icons, localized tooltip and embedded Black Cloud Shop."""
import sys,os,json,copy,struct,subprocess,re
from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image,ImageEnhance
from hashlib import sha256
HERE=Path(__file__).resolve().parent
ROOT=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
sys.path.insert(0,str(HERE.parent/'speech-bubbles'))
from artwork import read_pak,rewrite,binary_xml,encode_binary_xml
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(b):return sha256(b).hexdigest()
OUT=Path('output/market-shortcut/revision-2').resolve()
MARKET_RECEIPT='MarketShortcut-backups/20261002-010612-233/manifest.json'
SPEECH_RECEIPT='SpeechBubbles-backups/20261001-203148-743/manifest.json'
TOOLTIP='STR_PRIVATE_CENTRAL_MARKET_HUD'
def icons():
    atlas=Image.new('RGBA',(256,64));defs=[]
    for row,(stem,asset,w,h) in enumerate([('mkt_scales','scales-outlined.png',34,36),('mkt_shop','shop-outlined.png',35,36)]):
        img=Image.open(HERE/'assets'/asset).convert('RGBA');img=img.crop(img.getbbox());img.thumbnail((w-2,h-2),Image.Resampling.LANCZOS)
        for i,(state,factor) in enumerate([('up',1),('over',1.28),('down',.72)]):
            icon=ImageEnhance.Brightness(img).enhance(factor);x=row*120+i*40
            atlas.alpha_composite(icon,(x+(w-img.width)//2,(h-img.height)//2))
            defs.append(ET.Element('Skin',name=stem+'_'+state,src_image=f'{x},0,{w},{h}',texture='Textures/UI/mkt_scales'))
        preset=ET.Element('Preset',name=stem+'_button',type='button')
        for state in ('up','over','down'):ET.SubElement(preset,'SkinRef',main_state='0',name=stem+'_'+state,sub_state=state)
        defs.append(preset)
    atlas.save(OUT/'icon-states.png')
    header=[124,0x100f,64,256,1024,0,0]+[0]*11+[32,0x41,0,32,0xff,0xff00,0xff0000,0xff000000,0x1000,0,0,0,0]
    return defs,b'DDS '+struct.pack('<31I',*header)+atlas.tobytes()
def library(data,defs):
    t=binary_xml(data);existing=t.find(".//Preset[@name='mkt_scales_button']")
    if existing is None:raise ValueError('Original Market skin missing')
    for parent in t.iter():
        for child in list(parent):
            if child.get('name','').startswith(('mkt_scales','mkt_shop')):parent.remove(child)
    category=t.find(".//Category[@name='version5']")
    if category is None:raise ValueError('Native version5 artwork category missing')
    category.extend(copy.deepcopy(defs));return encode_binary_xml(t)
def hud(data):
    t=binary_xml(data);button=t.find(".//Widget[@name='central_market_button']")
    if button is None or button.get('tooltip')!='Central Market':raise ValueError('Unexpected Market HUD version')
    button.set('tooltip',TOOLTIP)
    for name in ['item_shop','item_shop_gf','item_ingame_web_shop']:
        w=t.find(".//Widget[@name='"+name+"']")
        if w is None:raise ValueError('Native Shop button missing')
        w.set('preset','mkt_shop_button');x,y,_,_=w.get('frame').split(',');w.set('frame',f'{x},{y},35,36');w.set('tooltip','STR_WINDOW_INGAMESHOP')
    return encode_binary_xml(t)
def strings(data):
    t=binary_xml(data)
    if any(e.findtext('name')==TOOLTIP or e.findtext('id')=='990100001' for e in t):raise ValueError('Market tooltip already exists')
    e=ET.SubElement(t,'string');ET.SubElement(e,'id').text='990100001';ET.SubElement(e,'name').text=TOOLTIP;ET.SubElement(e,'body').text='Central Market'
    return encode_binary_xml(t)
def main():
    OUT.mkdir(parents=True,exist_ok=True);defs,texture=icons();staged={};market=read(ROOT/MARKET_RECEIPT)
    for e in market['files']:
        if sha((ROOT/e['path']).read_bytes())!=e['installed']:raise ValueError('Later Market change: '+e['path'])
    def stage(rel,data):
        if rel in staged and staged[rel]!=data:raise ValueError('Conflicting composed payload: '+rel)
        staged[rel]=data;target=OUT/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    def transform(path,rel):
        if rel=='Data/ui/ui.pak':return rewrite(path,{'UI_Preload.xml':library(read_pak(path).read('UI_Preload.xml'),defs)})
        if rel=='Textures/ui/ui.pak':return rewrite(path,{'mkt_scales.dds':texture})
        if rel=='L10N/enu/Data/data.pak':
            z=read_pak(path);changes={'ui/ui_preload.xml':library(z.read('ui/ui_preload.xml'),defs),'strings/client_strings_ui.xml':strings(z.read('strings/client_strings_ui.xml'))}
            for style in (1,2):
                name=f'ui/game_hud_s{style}/start_dialog.xml';changes[name]=hud(z.read(name))
            return rewrite(path,changes)
        raise ValueError(rel)
    core=['Data/ui/ui.pak','Textures/ui/ui.pak','L10N/enu/Data/data.pak']
    for rel in core:stage(rel,transform(ROOT/rel,rel))
    for style in (1,2):
        rel=f'Data/ui/game_hud_s{style}/game_hud_s{style}.pak';stage(rel,rewrite(ROOT/rel,{'start_dialog.xml':hud(read_pak(ROOT/rel).read('start_dialog.xml'))}))
    rel='Plugin/RelicCalc/RelicCalc.pak';z=read_pak(ROOT/rel);lua=z.read('PrivateMenus.lua')
    line=re.search(rb'^[ \t]*RegisterMenu\(PRIVATE_CASH_SHOP_LABEL[^\n]*\n',lua,re.M)
    if not line:raise ValueError('Existing Cash Shop menu registration missing')
    lua=lua[:line.start()]+lua[line.end():];lua=lua.replace(b'PRIVATE_CASH_SHOP_LABEL = "Cash Shop"',b'PRIVATE_CASH_SHOP_LABEL = "Black Cloud Marketplace"')
    xml=z.read('CashShop.xml').decode('utf-16');assert xml.count('text="Cash Shop"')==1
    xml=xml.replace('text="Cash Shop"','text="Black Cloud Marketplace"')
    stage(rel,rewrite(ROOT/rel,{'PrivateMenus.lua':lua,'CashShop.xml':xml.encode('utf-16')}))
    subprocess.run(['java',str(HERE.parent/'transmog-menu/SignClientPackages.java'),str(ROOT),str(OUT)],check=True)
    for rel in ['Addon.key','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig','Plugin/RelicCalc/RelicCalc.pak.sig']:stage(rel,(OUT/rel).read_bytes())
    (OUT/'Pub.key').unlink()
    work=OUT.parent/'revision-2-compile';work.mkdir(exist_ok=True);dll=OUT/'bin64/AionMarketShortcut.dll';dll.parent.mkdir(exist_ok=True)
    vcvars=r'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat'
    script=work/'compile.cmd';script.write_text(f'@echo off\ncall "{vcvars}" >nul\ncl /nologo /std:c++17 /EHsc /O2 /MT /LD /Fo:"{work / "market.obj"}" "{HERE / "market_shortcut.cpp"}" /link /OUT:"{dll}" /IMPLIB:"{work / "market.lib"}"\n')
    subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True);stage('bin64/AionMarketShortcut.dll',dll.read_bytes())
    # Update only localized resource hashes; the existing native hooks and Game.dll stay byte-identical.
    def metadata(prefix):
        prefix=prefix+'/' if prefix else '';srel=prefix+'DXVK/graphics-menu/installed.json';mrel=prefix+'DXVK/graphics-menu/package/manifest.json'
        state=read(ROOT/srel);manifest=read(ROOT/mrel)
        if state['files']!=manifest['files']:raise ValueError('Graphics state differs')
        entry=next(e for e in state['files'] if e['path']=='L10N/enu/Data/data.pak');rel=entry['path'];baseline=Path(state['backupRoot'])/rel
        if not baseline.resolve().is_relative_to(ROOT/'DXVK-backups') or sha(baseline.read_bytes())!=entry['original']:raise ValueError('Graphics locale backup changed')
        if sha((ROOT/prefix/rel).read_bytes())!=entry['installed']:raise ValueError('Graphics locale changed')
        baseline_rel=baseline.relative_to(ROOT).as_posix()
        if baseline_rel not in staged:stage(baseline_rel,transform(baseline,rel))
        entry.update(original=sha(staged[baseline_rel]),installed=sha(staged[prefix+rel]));stage(prefix+'DXVK/graphics-menu/package/'+rel,staged[prefix+rel])
        manifest['files']=copy.deepcopy(state['files'])
        stage(srel,json.dumps(state,indent=2).encode());stage(mrel,json.dumps(manifest,indent=2).encode())
    metadata('');speech=read(ROOT/SPEECH_RECEIPT);base=Path(SPEECH_RECEIPT).parent.as_posix()
    for e in speech['files']:
        if sha((ROOT/e['path']).read_bytes())!=e['installed']:raise ValueError('Speech tracking changed')
        if e['path'] in core:
            saved=ROOT/base/e['path']
            if sha(saved.read_bytes())!=e['original']:raise ValueError('Speech restore baseline changed')
            stage(base+'/'+e['path'],transform(saved,e['path']))
    metadata(base)
    for e in speech['files']:
        if e['path'] in staged:e['installed']=sha(staged[e['path']])
        if base+'/'+e['path'] in staged:e['original']=sha(staged[base+'/'+e['path']])
    stage(SPEECH_RECEIPT,json.dumps(speech,indent=2).encode())
    for e in market['files']:
        if e['path'] in staged:e['installed']=sha(staged[e['path']])
    market['hudRevision']=2;stage(MARKET_RECEIPT,json.dumps(market,indent=2).encode())
    m=dict(clientRoot=str(ROOT),feature='central-market-hud',revision=2,files=[dict(path=rel,original=sha((ROOT/rel).read_bytes()) if (ROOT/rel).exists() else None,installed=sha(data)) for rel,data in staged.items()],sourceKey=sha((ROOT/'Pub.key').read_bytes()),hooks=market['hooks'])
    (OUT/'manifest.json').write_text(json.dumps(m,indent=2));print('Prepared HUD revision 2:',len(staged),'files; client untouched.')
if __name__=='__main__':main()
