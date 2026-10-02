"""Check untouched archive entries and run installation/rejection in a disposable client."""
import sys,json,hashlib,shutil,subprocess,os,time,copy
from pathlib import Path
from xml.etree import ElementTree as ET
HERE=Path(__file__).resolve().parent;OUT=Path(sys.argv[1] if len(sys.argv)>1 else 'output/market-shortcut/package').resolve()
sys.path.insert(0,str(HERE.parent/'speech-bubbles'))
from artwork import read_pak,binary_xml
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    m=json.loads((OUT/'manifest.json').read_text());root=Path(m['clientRoot'])
    assert len({e['path'] for e in m['files']})==len(m['files'])
    for e in m['files']:
        assert sha(OUT/e['path'])==e['installed'],e['path']
        if e['original'] is not None:assert sha(root/e['path'])==e['original'],e['path']
    allowed={
        'Data/ui/ui.pak':{'UI_Preload.xml'},'Textures/ui/ui.pak':{'mkt_scales.dds'},
        'L10N/enu/Data/data.pak':{'ui/ui_preload.xml','ui/game_hud_s1/start_dialog.xml','ui/game_hud_s2/start_dialog.xml'},
        'Data/ui/game_hud_s1/game_hud_s1.pak':{'start_dialog.xml'},'Data/ui/game_hud_s2/game_hud_s2.pak':{'start_dialog.xml'},
        'Plugin/RelicCalc/RelicCalc.pak':{'PrivateMenus.lua'}}
    if m.get('revision')==2:
        allowed['L10N/enu/Data/data.pak'].add('strings/client_strings_ui.xml')
        allowed['Plugin/RelicCalc/RelicCalc.pak'].add('CashShop.xml')
    for rel,names in allowed.items():
        before=read_pak(root/rel);after=read_pak(OUT/rel)
        assert set(after.namelist())==set(before.namelist())|({'mkt_scales.dds'} if rel.startswith('Textures/') else set())
        for n in before.namelist():
            if n not in names:assert before.read(n)==after.read(n),(rel,n)
        for n in names:
            if n.endswith('.xml') and n!='CashShop.xml':
                old=binary_xml(before.read(n));new=binary_xml(after.read(n))
                if n.endswith('start_dialog.xml'):
                    button=new.find("./Widget[@name='central_market_button']");assert button is not None
                    assert button.get('preset')=='mkt_scales_button'
                    if m.get('revision')==2:
                        assert button.get('tooltip')=='STR_PRIVATE_CENTRAL_MARKET_HUD'
                        button.attrib=copy.deepcopy(old.find("./Widget[@name='central_market_button']").attrib)
                        for name in ['item_shop','item_shop_gf','item_ingame_web_shop']:
                            w=new.find(".//Widget[@name='"+name+"']");assert w.get('preset')=='mkt_shop_button' and w.get('frame').endswith(',35,36') and w.get('tooltip')=='STR_WINDOW_INGAMESHOP'
                            w.attrib=copy.deepcopy(old.find(".//Widget[@name='"+name+"']").attrib)
                    else:new.remove(button)
                elif n.startswith('strings/'):
                    entries=[e for e in new if e.findtext('name')=='STR_PRIVATE_CENTRAL_MARKET_HUD']
                    assert len(entries)==1 and entries[0].findtext('body')=='Central Market';new.remove(entries[0])
                else:
                    # Validate new skins/preset and exact preservation of all existing definitions.
                    for tree in ([old,new] if m.get('revision')==2 else [new]):
                        for parent in tree.iter():
                            for child in list(parent):
                                if child.get('name','').startswith(('mkt_scales','mkt_shop')):parent.remove(child)
                assert ET.tostring(old)==ET.tostring(new),(rel,n)
    library=binary_xml(read_pak(OUT/'Data/ui/ui.pak').read('UI_Preload.xml'))
    preset=library.find(".//Preset[@name='mkt_scales_button']");assert preset is not None and len(preset)==3
    # The native library already supports Preset and Skin siblings in this category.
    native=binary_xml(read_pak(root/'Data/ui/ui.pak').read('UI_Preload.xml'))
    print('Library categories:',[(e.attrib.get('name'),sorted({c.tag for c in e})) for e in native])
    lua=read_pak(OUT/'Plugin/RelicCalc/RelicCalc.pak').read('PrivateMenus.lua')
    assert b'{label = "Central Market"' not in lua and b'SLASH_PRIVATEWAREHOUSE1 = "/privatewarehouse"' in lua
    if m.get('revision')==2:
        assert b'RegisterMenu(PRIVATE_CASH_SHOP_LABEL' not in lua
        assert b'SLASH_PRIVATECASHSHOP1 = "/privatecashshop"' in lua and b'PrivateCashShopBrowser:LoadUrlWithWebAuth(PRIVATE_CASH_SHOP_URL)' in lua
        xml=ET.fromstring(read_pak(OUT/'Plugin/RelicCalc/RelicCalc.pak').read('CashShop.xml').decode('utf-16'))
        assert xml.find('Dialog').get('text')=='Black Cloud Marketplace' and xml.find('.//Widget').get('name')=='PrivateCashShopBrowser'
    for prefix in ('','SpeechBubbles-backups/20261001-203148-743/'):
        s=json.loads((OUT/(prefix+'DXVK/graphics-menu/installed.json')).read_text());p=json.loads((OUT/(prefix+'DXVK/graphics-menu/package/manifest.json')).read_text())
        assert s['files']==p['files']
        for e in s['files']:
            rel=prefix+e['path']
            if (OUT/rel).exists():assert sha(OUT/rel)==e['installed']
            baseline=Path(s['backupRoot']).relative_to(root).as_posix()+'/'+e['path']
            if (OUT/baseline).exists():assert sha(OUT/baseline)==e['original']
    receipt=json.loads((OUT/'SpeechBubbles-backups/20261001-203148-743/manifest.json').read_text())
    for e in receipt['files']:
        for rel,digest in [(e['path'],e['installed']),('SpeechBubbles-backups/20261001-203148-743/'+e['path'],e['original'])]:
            if (OUT/rel).exists():assert sha(OUT/rel)==digest
    fixture=OUT.parent/('install-fixture-'+str(time.time_ns()));fixture.mkdir();prepared=fixture/'prepared';prepared.mkdir()
    shutil.copy2(root/'Pub.key',fixture/'Pub.key')
    for e in m['files']:
        target=prepared/e['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(OUT/e['path'],target)
        if e['original'] is not None:
            target=fixture/e['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/e['path'],target)
    m['clientRoot']=str(fixture);(prepared/'manifest.json').write_text(json.dumps(m))
    args=['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(HERE/'install.ps1'),'-PreparedPath',str(prepared)]
    environment=dict(os.environ);environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    result=subprocess.run(args,capture_output=True,text=True,env=environment);assert result.returncode==0,(result.stdout,result.stderr)
    backup=max((fixture/'MarketShortcut-backups').iterdir(),key=lambda p:p.name)
    for e in m['files']:
        assert sha(fixture/e['path'])==e['installed']
        if e['original'] is not None:assert sha(backup/e['path'])==e['original']
    before={e['path']:sha(fixture/e['path']) for e in m['files']};result=subprocess.run(args,capture_output=True,text=True,env=environment)
    assert result.returncode!=0 and 'Client changed' in result.stderr
    assert all(sha(fixture/rel)==h for rel,h in before.items())
    assert sha(fixture/'Pub.key')==sha(root/'Pub.key')
    print('PASS: stock archive entries and native layouts preserved; complete launcher/speech recovery hashes; actual installer, verified backups and later-change rejection; stock model key unchanged.')
if __name__=='__main__':main()
