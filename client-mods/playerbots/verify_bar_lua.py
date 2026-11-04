"""Exercise the bar through the installed client's real Lua 5.1 widget wrappers."""
import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as E
from prepare_bar import HERE,read_pak
sys.path.insert(0,str(HERE.parents[1]/'game-server/target/native-wardrobe-test-deps'))
from lupa.lua51 import LuaRuntime

def main():
    p=argparse.ArgumentParser();p.add_argument('--client',type=Path,required=True);p.add_argument('--pull-only',action='store_true');a=p.parse_args()
    z=read_pak(a.client/'bin32/bin32.pak')
    with read_pak(a.client/'Plugin/RelicCalc/RelicCalc.pak') as addon:
        installed_bar=addon.read('PlayerBotBar.lua').decode('utf-8-sig')
        installed_xml=addon.read('PlayerBotBar.xml')
    requested_textures=set()
    for scale in (0.8,1,1.125,1.5):
        lua=LuaRuntime(unpack_returned_tuples=True);g=lua.globals();widgets={};calls=[]
        def native(api,*args):
            if api in ('RegisterEvent','SetTimer','ResetTimer'):calls.append((api,args));return
            if len(args)<2:return
            state=widgets.setdefault(args[1],dict(text='',visible=1,enabled=True,rect=(0,0,1,1)))
            v=args[2:]
            if api=='SetWidgetText':state['text']=str(v[0])
            elif api=='GetWidgetText':return state['text']
            elif api=='SetWidgetVisible':state['visible']=v[0]
            elif api=='GetWidgetVisible':return state['visible']
            elif api=='SetWidgetEnable':state['enabled']=bool(v[0])
            elif api=='SetWidgetRect':state['rect']=tuple(float(n)*scale for n in v)
            elif api=='GetWidgetRect':return state['rect']
            elif api=='SetWidgetSize':state['rect']=(*state['rect'][:2],float(v[0])*scale,float(v[1])*scale)
            elif api=='SetWidgetFlag':state['flags']=v[0]
            elif api=='SetWidgetFont':state['font']=v[0]
            elif api=='SetWidgetUIImage':
                texture=str(v[1]).replace('\\','/')
                if texture.casefold().startswith('textures/ui/aetherfallsummon/'):
                    requested_textures.add(texture)
                    assert (a.client/texture).is_file(),'Recruitment requested a missing installed texture: '+texture
                state.setdefault('images',{})[v[0]]=v[1]
            elif api=='SetWidgetAlpha':state['alpha']=v[0]
        sources={}
        for n in z.namelist():
            if n.endswith('.lua'):
                text=z.read(n).decode('utf-8-sig',errors='replace');sources[Path(n).stem]=text
                for api in re.findall(r'\b(\w+)\(g_AddonName\b',text):g[api]=lambda *args,api=api:native(api,*args)
        g.GetUIScale=lambda:scale;g.AddNewWidget=lambda *args:None;g.GetAionStr=lambda text:text
        g.DisplayChatMessage=lambda *args:None;g.GetServerName=lambda:'test';g.UnitName=lambda unit:'tester'
        for name in ('Widget','Container','Button','Static','StaticImage','EditBox','MessageWidget','SlashCmd'):
            lua.execute(sources[name]);g.package.loaded[name]=True
        def table(n):
            t=lua.table(tag=n.tag,attr=lua.table_from(n.attrib))
            if n.text and n.text.strip():t[1]=n.text
            for i,child in enumerate(n,1):t[i]=table(child)
            return t
        tree=E.fromstring(installed_xml if a.pull_only else (HERE/'PlayerBotBar.xml').read_bytes());g.g_AddonName='RelicCalc'
        for n in tree:
            w=g.CreateWidget('Dialog',n.get('name'));w.LoadXML(w,table(n))
        lua.execute(installed_bar if a.pull_only else (HERE/'PlayerBotBar.lua').read_text())
        g.PrivateCompanions_Open=lambda:calls.append(('manage',()))
        match=dict(rect=(100,200,640,400),shown=True)
        cursor=dict(down=False)
        def core_call(command,*args):
            parts=(widgets.get('PlayerBotBarMeta',{}).get('text') or '1920|1080|0|1').split('|')
            sw,sh=map(int,parts[:2])
            if command=='scale':return scale,sw,sh
            if command=='cursor':return cursor.get('x',10)*960/sw,cursor.get('y',10)*540/sh,960,540,cursor['down'],False
        g.AetherfallCore=core_call
        # Exercise the actually shipped public wrapper, rather than inventing
        # AFCore.Cursor's contract from the lower-level native return values.
        with read_pak(a.client/'Plugin/RelicCalc/RelicCalc.pak') as addon:
            public_core=addon.read('AetherfallCore.lua').decode('utf-8-sig')
        lua.execute(public_core)
        assert tuple(g.AFCore.Cursor())==(10,10,False,False)
        g.AFCore.Dialog=lambda id:('party_match_dialog',*match['rect'],match['shown']) if id==57 else None
        g.AFCore.FindDialogs=lambda name:lua.table(lua.table_from({'id':57,'name':name}))
        g.AFCore.Widget=lambda id,path:lua.table(name=path,rect=lua.table(match['rect'][0]+400*scale,match['rect'][1]+match['rect'][3]-30*scale,61*scale,24*scale))
        g.lxp=lua.table(lom=lua.table(parse=lambda text:table(E.fromstring(text))))
        def load_dialog(doc):
            w=g.CreateWidget('Dialog',doc.attr.name);w.LoadXML(w,doc)
        g.LoadXMLWidget=load_dialog
        trigger=lambda name,event='OnClick':g.Widget_TriggerScript(name,event,'')
        g.Widget_TriggerScript('PlayerBotBar','OnLoad','')
        tick=lambda n=1:[g.PlayerBotBar_OnEvent('ADDON_TIMER') for _ in range(n)]
        if a.pull_only:
            widgets['PlayerBotBarMeta']['text']='1280|960|0|1';tick(120)
            widgets['PlayerBotBarTx']['text']='attack'
        else:
            assert widgets['PlayerBotBar']['visible']==0
            assert all(not widgets['PlayerBotBar'+n]['enabled'] for n in ('Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread','Close','Normal','Far'))
            widgets['PlayerBotBarMeta']['text']='1280|960|0|1';g.PlayerBotBar_OnEvent('ADDON_TIMER');g.PlayerBotBar_OnEvent('PLAYER_ENTERING_WORLD');g.PlayerBotBar_OnEvent('ADDON_TIMER')
            assert widgets['PlayerBotBar']['visible']==1 and widgets['PlayerBotBarGuard']['visible']==0
            # Actual native dragging changes physical coordinates; every size change
            # must preserve them exactly at each supported UI scale.
            widgets['PlayerBotBar']['rect']=(425,375,400*scale,70*scale)
            trigger('PlayerBotBarMore');assert widgets['PlayerBotBarGuard']['visible']==1
            assert all(widgets['PlayerBotBar'+n]['visible']==1 for n in ('Circle','Box','Line','Spread','Close','Normal','Far'))
            assert all(abs(p-q)<.001 for p,q in zip(widgets['PlayerBotBar']['rect'],(425,375,400*scale,240*scale)))
            trigger('PlayerBotBarHide');assert widgets['PlayerBotBarIcon']['visible']==1 and widgets['PlayerBotBarAttack']['visible']==0
            assert all(widgets['PlayerBotBar'+n]['visible']==0 for n in ('Circle','Box','Line','Spread','Close','Normal','Far'))
            assert widgets['PlayerBotBar']['rect']==(425,375,44*scale,52*scale)
            widgets['PlayerBotBar']['rect']=(525,475,44*scale,52*scale)
            trigger('PlayerBotBarIcon');assert all(abs(p-q)<.001 for p,q in zip(widgets['PlayerBotBar']['rect'],(525,475,400*scale,240*scale)))
            trigger('PlayerBotBarMore');assert widgets['PlayerBotBarGuard']['visible']==0
            assert all(widgets['PlayerBotBar'+n]['visible']==0 for n in ('Circle','Box','Line','Spread','Close','Normal','Far'))
            for n in ('Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread','Close','Normal','Far'):
                widgets['PlayerBotBarTx']['text']='';trigger('PlayerBotBar'+n);assert widgets['PlayerBotBarTx']['text']==(('distance_'+{'Close':'close','Normal':'normal','Far':'far'}[n]) if n in ('Close','Normal','Far') else ('formation_'+n.lower()) if n in ('Circle','Box','Line','Spread') else n.lower())
                trigger('PlayerBotBarAttack');assert widgets['PlayerBotBarTx']['text']==(('distance_'+{'Close':'close','Normal':'normal','Far':'far'}[n]) if n in ('Close','Normal','Far') else ('formation_'+n.lower()) if n in ('Circle','Box','Line','Spread') else n.lower()),'Do not overwrite an unsent command'
            widgets['PlayerBotBarTx']['text']='';widgets['PlayerBotBarMeta']['text']='1280|960|0|0';g.PlayerBotBar_OnEvent('ADDON_TIMER')
            trigger('PlayerBotBarAttack');assert widgets['PlayerBotBarTx']['text']==''
            trigger('PlayerBotBarManage');assert ('manage',()) in calls
            trigger('PlayerBotBarHide');g.DoSlashCmd('/botbar');g.PlayerBotBar_OnEvent('ADDON_TIMER');assert widgets['PlayerBotBarIcon']['visible']==0
            assert not any(c[0] in ('SetTimer','ResetTimer') for c in calls),'Keep Wardrobe shared timer unchanged'
            # Persisted icon mode survives addon load and can be restored.
            g.Widget_TriggerScript('PlayerBotBar','OnLoad','');widgets['PlayerBotBarMeta']['text']='1280|960|2|1';g.PlayerBotBar_OnEvent('ADDON_TIMER')
            assert widgets['PlayerBotBarIcon']['visible']==1
            # Hire details stay collapsed beside Find Group's Help, expand on demand and go with the window.
            tick=lambda n=1:[g.PlayerBotBar_OnEvent('ADDON_TIMER') for _ in range(n)]
            tick(120);assert not g.PlayerBotBarState.hireFailed;terms=widgets['PlayerBotHireTerms'];text=widgets['PlayerBotHireTermsText']
            assert terms['visible']==1 and text['visible']==0,(terms,text)
            assert widgets['PlayerBotHireTermsToggle']['text']=='Hire details +'
            tree=E.fromstring(re.search(r'<Dialog name="PlayerBotHireTerms".*?</Dialog>',(HERE/'PlayerBotBar.lua').read_text(),re.S).group(0))
            assert tree.get('preset') is None,'A skin clips the button'
            assert abs(terms['rect'][0]-(100+72*scale))<.01 and abs(terms['rect'][1]-(600-30*scale))<.01,'Beside Help on the left, whatever Help reports: '+str(terms['rect'])

            cursor.update(x=100+80*scale,y=600-20*scale,down=True);tick();cursor['down']=False;tick()
            assert text['visible']==1 and widgets['PlayerBotHireTermsToggle']['text']=='Hire details -','A press on the button opens the terms'
            trigger('PlayerBotHireTermsToggle');assert text['visible']==1,'OnClick from the same press does not toggle back'
            tick(9);trigger('PlayerBotHireTermsToggle');assert text['visible']==0
            cursor.update(x=10,y=10)
            cursor['down']=True;tick();cursor['down']=False;tick();assert terms['visible']==1,'A click leaves the button shown'
            tick(9);trigger('PlayerBotHireTermsToggle');assert text['visible']==1;match['shown']=False;tick(5)
            assert terms['visible']==0 and text['visible']==0,'Closing Find Group hides and collapses the terms'
            match['shown']=True;match['rect']=(100,800,640,140);tick(5)
            assert terms['visible']==1 and terms['rect'][1]+24*scale<=960,'The button stays on screen near the bottom edge'
            widgets['PlayerBotBarTx']['text']='';widgets['PlayerBotBarMeta']['text']='1280|960|0|1';g.PlayerBotBar_OnEvent('ADDON_TIMER')
            trigger('PlayerBotBarAttack');assert widgets['PlayerBotBarTx']['text']=='attack','Terms never affect orders'
        for width,height in ((1280,960),(1920,1080),(3440,1440)):
            widgets['PlayerBotBarMeta']['text']=f'{width}|{height}|0|1';tick()
            g.DoSlashCmd('/companionpull GUNNER GOOD POOR Gailola');tick(3)
            assert g.PlayerBotBarState.pull is not None
            assert widgets['PlayerBotPullName']['text']=='Gailola'
            assert widgets['PlayerBotPullName']['font']=='credit_team'
            assert widgets['PlayerBotPullClass']['images'][0].endswith('class-GUNNER.dds')
            assert widgets['PlayerBotPullGear']['images'][0].endswith('value-GOOD.dds')
            assert widgets['PlayerBotPullRank']['images'][0].endswith('value-POOR.dds')
            model=widgets['PlayerBotPullModel']['rect']
            assert abs((model[0]+model[2]/2)/width-.505)<.0001
            assert abs((model[1]+model[3]-height*120/1080)/height-.7088888889)<.0001
            assert widgets['PlayerBotPullPanel']['visible']==0 and widgets['PlayerBotPullModel']['visible']==0
            tick(40);assert widgets['PlayerBotPullModel']['visible']==1 and widgets['PlayerBotPullPanel']['visible']==0
            tick(20);assert widgets['PlayerBotPullResult']['visible']==0,'Result group stays hidden through the burst'
            tick(10);assert widgets['PlayerBotPullResult']['visible']==1 and widgets['PlayerBotPullContinue']['visible']==0,'Whole result enters after burst; Continue waits for settling'
            assert widgets['PlayerBotPullPanelFx']['images'][0].endswith('.dds')
            tick(18);assert all(widgets['PlayerBotPull'+part]['visible']==1 for part in ('Result','Panel','Gear','Rank','Continue','ContinueFace')),'One complete result group'
            assert widgets['PlayerBotPullPanelFx']['visible']==0 and g.PlayerBotBarState.pull.panelFrame is None,'Finite entrance stops'
            assert 'PlayerBotPullHeader' not in widgets,'No top banner or labels'
            assert widgets['PlayerBotPullSkip']['visible']==0
            if a.pull_only:tick(120)  # Resolve every held-stage and spark frame through the installed entry point.
            gx,gy,gw,gh=widgets['PlayerBotPullResult']['rect']
            x,y,w,h=widgets['PlayerBotPullContinue']['rect'];x+=gx;y+=gy
            assert x>=0 and y>=0 and x+w<=width and y+h<=height
            cursor.update(x=x+w/2,y=y+h/2,down=False);tick()
            assert widgets['PlayerBotPullContinueFace']['images'][0].endswith('continue-1.dds')
            cursor['down']=True;tick();assert widgets['PlayerBotPullContinueFace']['images'][0].endswith('continue-2.dds')
            cursor['down']=False;tick();assert widgets['PlayerBotPullContinueFace']['images'][0].endswith('continue-1.dds')
            trigger('PlayerBotPullContinue');assert g.PlayerBotBarState.pull is None and widgets['PlayerBotPull']['visible']==0,'OnClick closes; actual native short-click path is covered in native fixture'
            assert 0 not in widgets['PlayerBotPullContinue'].get('images',{}),'Button substates never own the art'
            g.DoSlashCmd('/companionpull TEMPLAR EXCELLENT ORDINARY Othername');tick(10);g.DoSlashCmd('/companionpullclose')
            assert g.PlayerBotBarState.pull is None and widgets['PlayerBotPull']['visible']==0
            g.DoSlashCmd('/companionpull TEMPLAR EXCELLENT ORDINARY Othername');tick();trigger('PlayerBotPullSkip')
            assert g.PlayerBotBarState.pull is None
            g.DoSlashCmd('/companionpull TEMPLAR EXCELLENT ORDINARY Othername')
            widgets['PlayerBotBarMeta']['text']=f'{width}|{height}|0|0';tick()
            assert g.PlayerBotBarState.pull is None and widgets['PlayerBotPull']['visible']==0
        if a.pull_only:
            classes=('TEMPLAR','GLADIATOR','ASSASSIN','RANGER','SORCERER','SPIRIT_MASTER','CLERIC','CHANTER','GUNNER','RIDER','BARD')
            values=('POOR','ORDINARY','GOOD','GREAT','EXCELLENT')
            widgets['PlayerBotBarMeta']['text']='1920|1080|0|1';tick()
            for index,advanced in enumerate(classes):
                g.DoSlashCmd('/companionpull '+advanced+' '+values[index%5]+' '+values[(index+1)%5]+' Assetcheck')
                tick(3);g.DoSlashCmd('/companionpullclose')
        assert widgets['PlayerBotBarTx']['text']=='attack','Pull playback never sends gameplay orders'
    print('OK:',len(requested_textures),'recruitment textures requested by installed Lua resolve in the actual client.')
    print('OK: shipped AFCore Lua cursor wrapper and resolution normalization, no banner, hidden preparation, finite result group entrance only after burst, stable button faces/OnClick, native names/model geometry/close/disconnect, three screens/four UI scales.')
    if not a.pull_only:print('OK: real Lua 5.1 widgets; four UI scales; collapsed Hire details beside Find Group Help; title position, draggable icon, expand/hide/restore, six party orders, four formations and three party distances, disconnect and shared timer preservation.')

if __name__=='__main__':main()
