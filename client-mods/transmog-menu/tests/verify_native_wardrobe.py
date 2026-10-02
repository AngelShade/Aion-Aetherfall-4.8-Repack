"""Exercise Wardrobe through the client's actual Lua 5.1 Widget wrappers."""
import sys,json,re,xml.etree.ElementTree as ET
from pathlib import Path
BASE=Path(__file__).resolve().parents[1]
ROOT=BASE.parents[1]
sys.path.insert(0,str(ROOT/'game-server/target/native-wardrobe-test-deps'))
from lupa.lua51 import LuaRuntime
runtime=LuaRuntime(unpack_returned_tuples=True)
g=runtime.globals()
stock=ROOT/'game-server/target/native-wardrobe-inspect'
g.package.path=str(stock).replace('\\','/')+'/?.lua;'+g.package.path
widgets={}
ui_scale=1.125
def native(api,*args):
    if api=='GetTime':return 100.0
    if api=='GetPlayerEquip':return '0:100000096,2:125000001,15:187000001'
    if api in ('RegisterEvent','SetTimer','ResetTimer','RegisterHandleCancelKeyFrame'):return
    if len(args)<2:return
    name=args[1];data=widgets.setdefault(name,dict(text='',visible=1,enabled=True,value=0,items=[]))
    values=args[2:]
    if api=='SetWidgetText':data['text']=values[0]
    elif api=='GetWidgetText':return data['text'].encode('utf-8')[:1023].decode('utf-8',errors='ignore')
    elif api=='SetWidgetRect':data['rect']=tuple(float(v)*ui_scale for v in values)
    elif api=='GetWidgetRect':return data.get('rect',(0,0,1,1))
    elif api=='SetWidgetVisible':data['visible']=values[0]
    elif api=='GetWidgetVisible':return data['visible']
    elif api=='SetWidgetEnable':data['enabled']=bool(values[0])
    elif api=='AddWidgetItem':data['items'].append(values[0])
    elif api=='ClearWidgetItem':data['items']=[]
    elif api=='SetWidgetValue':data['value']=values[0]
    elif api=='GetWidgetValue':return data['value']
    elif api=='SetWidgetUIImage':
        assert values[0] in (0,1,2,256,1024)
        data.setdefault('imageStates',{})[values[0]]=values[1]
        if values[0]==0:data['image']=values[1]
        data['imageLoads']=data.get('imageLoads',0)+1
    elif api=='SetWidgetFlag' and 'not_visible' in values[0]:data['visible']=0
    elif api=='SetWidgetPreset':data['preset']=values[0]
    elif api=='SetWidgetAttr':data.setdefault('attributes',{})[values[0]]=values[1]
    elif api=='SetWidgetAlpha':data['alpha']=values[0]
for source in stock.glob('*.lua'):
    for api in re.findall(r'\b(\w+)\(g_AddonName\b',source.read_text(encoding='utf-8',errors='replace')):
        g[api]=lambda *args,api=api:native(api,*args)
g.GetTime=lambda:100.0
g.GetUIScale=lambda:ui_scale
g.GetPlayerEquip=lambda:native('GetPlayerEquip')
g.GetAionStr=lambda text:text
g.AddNewWidget=lambda *args:None
for module in ('Widget','Container','Button','StaticImage','Static','SimpleHTML','EditBox','ComboBox','Scrollable'):
    runtime.execute(f'require "{module}"')
def table(element):
    value=runtime.table(tag=element.tag,attr=runtime.table_from(element.attrib))
    if element.text and element.text.strip():value[1]=element.text
    for i,child in enumerate(element,1):value[i]=table(child)
    return value
tree=ET.parse(BASE/'Wardrobe.xml');names=[n.get('name') for n in tree.iter() if n.get('name')]
assert len(names)==len(set(names)),'Duplicate native widget names'
g.g_AddonName='RelicCalc'
for dialog in tree.getroot():
    node=g.CreateWidget('Dialog',dialog.get('name'))
    node.LoadXML(node,table(dialog))
for name in ('WardrobeJSON.lua','WardrobeTheme.lua','WardrobeNative.lua'):
    runtime.execute((BASE/name).read_text(encoding='utf-8'))
g.WardrobeIcons=runtime.table_from({100000096:'Data/Items/test_weapon',125000001:'Data/Items/test_helmet',125000002:'Data/Items/test_hat',187000001:'Data/Items/test_wing'})
def click(name):
    g.g_AddonName='OtherAddon'
    g.Widget_TriggerScript(name,'OnClick','')
    assert g.g_AddonName=='RelicCalc'
for node in tree.iter('Widget'):
    if node.get('type')=='button':
        assert node.get('style')=='pushbutton'
        assert node.findtext('Scripts/OnClick')=='WardrobeNative_Click(this.name);'
        assert g[node.get('name')].triggerScript.OnClick

g.Widget_TriggerScript('PrivateWardrobe','OnLoad','')
g.WardrobeNative_Open()
state=dict(character='Babe',unlocked=2,total=3,tickets=2,page=1,pages=1,target=0,results=3,notice='',request='fixture',
    equipment=[dict(object=10,item=100000096,skin=100000096,name='Sword',equipped=True,slot=1,type='Sword',group='SWORD',slots=1,tooltip='item=100000096&count=1'),
        dict(object=11,item=125000001,skin=125000001,name='Helmet',equipped=True,slot=4,type='Headwear',group='HEAD',slots=4),
        dict(object=12,item=187000001,skin=187000001,name='Wings',equipped=True,slot=32768,type='Wings',group='WING',slots=32768)],
    skins=[dict(item=125000002,name='Appearance Headwear',category='Headwear',type='Headwear',unlocked=True,group='HEAD',slots=4,sources=[]),
        dict(item=100000097,name='Locked Sword',category='Weapons',type='Sword',unlocked=False,group='SWORD',slots=1,sources=[dict(object=20,name='Sword Skin',equipped=False)]),
        dict(item=187000002,name='Wing Appearance',category='Wings',type='Wings',unlocked=True,group='WING',slots=32768,sources=[])],
    outfits=[dict(name='Outfit',skins_json='{"4":125000002,"32768":187000002}',updated_at=1)],outfitAppearances=[])
def respond(s,action=None):
    sequence=g.WardrobeNative.pending.sequence
    body=json.dumps(s,ensure_ascii=True)
    parts=[body[i:i+800] for i in range(0,len(body),800)]
    for start in range(0,len(parts),8):
        for channel,part in enumerate(parts[start:start+8]):
            index=start+channel
            name='WardrobeNativeRx'+(str(channel) if channel else '')
            widgets[name]['text']=f'{sequence}|{index*800}|{int(index==len(parts)-1)}\n'+part
        g.Widget_TriggerScript('PrivateWardrobe','OnEvent','ADDON_TIMER')
if len(sys.argv)>1:
    live=json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
    respond(live)
    assert len(g.WardrobeNative.state.equipment)==len(live['equipment'])
    assert len(g.WardrobeNative.state.skins)==len(live['skins'])
    assert len(widgets['WardrobeNativePreview']['text'])>10
    print('PASS: complete live server response through bounded native channels')
    g.WardrobeNative.started=False;g.WardrobeNative.cache=runtime.table();g.WardrobeNative.skins=runtime.table();g.WardrobeNative_Fetch()
respond(state)
assert g.WardrobeNative.state.character=='Babe'
assert widgets['WardrobeApply']['visible']
assert '187000001' in widgets['WardrobeNativePreview']['text']
for width,height,scale in [(1024,768,1),(1920,1080,1.125),(2560,1440,1.5),(3440,1440,1.5),(3840,2160,2.25)]:
    ui_scale=scale;g.WardrobeNative_Layout(width,height);g.WardrobeNative_Render()
    preview=widgets['WardrobePreviewBounds']['rect'];scroll=widgets['WardrobeScroll']['rect']
    assert preview[0]+preview[2]<scroll[0],(width,height,preview,scroll)
    for name,data in widgets.items():
        if 'rect' not in data or not data['visible'] or name.startswith(('WardrobeRow','WardrobeIcon','WardrobeName','WardrobeState','WardrobeConfirm','WardrobeOutfitName','WardrobeUnlockSource')):continue
        x,y,w,h=data['rect'];assert min(x,y,w,h)>=0,(name,data['rect']);assert x+w<=width+1 and y+h<=height+1,(name,data['rect'])
    slots=[data['rect'] for name,data in widgets.items() if re.fullmatch('WardrobeSlot[0-9]+',name)]
    assert len(slots)==9
    for i,(x,y,w,h) in enumerate(slots):
        for xx,yy,ww,hh in slots[i+1:]:
            assert x+w<=xx or xx+ww<=x or y+h<=yy or yy+hh<=y,'Overlapping equipment slots'
click('WardrobeCategory2');assert g.WardrobeNative.query.category=='Weapons';respond(state)
click('WardrobeInventory');assert g.WardrobeNative.view=='inventory'
click('WardrobeCollection');assert g.WardrobeNative.view=='collection'
click('WardrobeCategory1');assert g.WardrobeNative.query.category=='All'
if g.WardrobeNative.pending:respond(state)
image_loads=sum(data.get('imageLoads',0) for name,data in widgets.items() if re.fullmatch(r'Wardrobe(?:SlotIcon\d+|Icon\d+)',name))
click('WardrobeRow1');assert sum(data.get('imageLoads',0) for name,data in widgets.items() if re.fullmatch(r'Wardrobe(?:SlotIcon\d+|Icon\d+)',name))==image_loads
assert g.WardrobeTheme.images['WardrobeRow1:0']=='card_selected'
assert g.WardrobeTheme.images['WardrobeCollection:0']=='button_selected'
assert widgets['WardrobeSearch']['image']=='Textures/UI/WardrobeNative/field'
assert widgets['PrivateWardrobe']['alpha']==1 and widgets['WardrobeConfirm']['alpha']==1
assert widgets['WardrobeConfirmError']['attributes']['textColor']=='1,0.64,0.58,1'
click('WardrobeTryOn')
assert g.WardrobeNative.drafts[11].skin==125000002
assert widgets['WardrobeApply']['enabled']
click('WardrobeRow3');g.WardrobeNative.query.target=0;click('WardrobeTryOn')
assert g.WardrobeNative.drafts[12].skin==187000002
assert '125000002' in widgets['WardrobeNativePreview']['text'] and '187000002' in widgets['WardrobeNativePreview']['text']
click('WardrobeApply')
assert widgets['WardrobeNativeControl']['text']=='input|0'
click('WardrobeConfirmCancel')
assert widgets['WardrobeNativeControl']['text']=='input|1'
click('WardrobeApply');click('WardrobeConfirmAccept')
assert '|P|' in widgets['WardrobeNativeTx']['text'] and 'applyChanges' in widgets['WardrobeNativeTx']['text']
g.WardrobeNative_CloseConfirm()
widgets['WardrobeNativeMeta']['text']='3840|2160|0|1|1|WardrobeRow1'
g.Widget_TriggerScript('PrivateWardrobe','OnEvent','ADDON_TIMER')
assert widgets['WardrobeNativeHover']['text'].startswith('item=125000002')
widgets['WardrobeNativeMeta']['text']='3840|2160|0|1|1|';g.Widget_TriggerScript('PrivateWardrobe','OnEvent','ADDON_TIMER');assert widgets['WardrobeNativeHover']['text']=='clear'
respond(dict(error='Fixture rejection'))
assert widgets['WardrobeStatus']['text']=='Fixture rejection'
g.WardrobeNative_CloseConfirm();g.WardrobeNative.query.target=0
click('WardrobeRow2');click('WardrobeTryOn')
assert not widgets['WardrobeApply']['enabled']
click('WardrobeRemoveLocked');assert widgets['WardrobeApply']['enabled']
click('WardrobeUnlock')
assert widgets['WardrobeNativeControl']['text']=='input|0'
click('WardrobeConfirmAccept')
assert 'action=unlock' in widgets['WardrobeNativeTx']['text'] and 'source=20' in widgets['WardrobeNativeTx']['text']
respond(state,'unlock')
click('WardrobeOutfits');click('WardrobeRow1');click('WardrobeTryOn')
assert g.WardrobeNative.drafts[11].skin==125000002 and g.WardrobeNative.drafts[12].skin==187000002
for bad in ('{','[1,]','"\\uD800"','{} trailing'):
    try:g.WardrobeJSON.decode(bad)
    except Exception:pass
    else:raise AssertionError('Malformed JSON accepted: '+bad)
assert g.WardrobeJSON.decode('{"value":"\\uD83D\\uDE00"}').value==chr(0x1f600)
g.PrivateWardrobe.Hide(g.PrivateWardrobe);g.WardrobeNative_OnEvent('ADDON_TIMER')
assert not g.WardrobeNative.opened and not g.WardrobeNative.pending
assert 'SlotIcon1:100000096' in widgets['WardrobeNativeIcons']['text']
print('PASS: registered native OnClick scripts, bounded icon assignments, cursor hover metadata; actual client Widget.lua with Lua 5.1, unique native widgets, five resolutions, headwear/wings/multiple drafts, locked-skin gating, apply/unlock requests, modal errors, saved outfits, native tooltip requests, JSON bounds, close lifecycle')
