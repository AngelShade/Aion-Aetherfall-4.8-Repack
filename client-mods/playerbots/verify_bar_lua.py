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
    p=argparse.ArgumentParser();p.add_argument('--client',type=Path,required=True);a=p.parse_args()
    z=read_pak(a.client/'bin32/bin32.pak')
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
            elif api=='SetWidgetFlag':state['flags']=v[0]
        sources={}
        for n in z.namelist():
            if n.endswith('.lua'):
                text=z.read(n).decode('utf-8-sig',errors='replace');sources[Path(n).stem]=text
                for api in re.findall(r'\b(\w+)\(g_AddonName\b',text):g[api]=lambda *args,api=api:native(api,*args)
        g.GetUIScale=lambda:scale;g.AddNewWidget=lambda *args:None;g.GetAionStr=lambda text:text
        g.DisplayChatMessage=lambda *args:None
        for name in ('Widget','Container','Button','Static','EditBox','MessageWidget','SlashCmd'):
            lua.execute(sources[name]);g.package.loaded[name]=True
        def table(n):
            t=lua.table(tag=n.tag,attr=lua.table_from(n.attrib))
            if n.text and n.text.strip():t[1]=n.text
            for i,child in enumerate(n,1):t[i]=table(child)
            return t
        tree=E.parse(HERE/'PlayerBotBar.xml');g.g_AddonName='RelicCalc'
        for n in tree.getroot():
            w=g.CreateWidget('Dialog',n.get('name'));w.LoadXML(w,table(n))
        lua.execute((HERE/'PlayerBotBar.lua').read_text())
        g.PrivateCompanions_Open=lambda:calls.append(('manage',()))
        trigger=lambda name,event='OnClick':g.Widget_TriggerScript(name,event,'')
        g.Widget_TriggerScript('PlayerBotBar','OnLoad','')
        assert widgets['PlayerBotBar']['visible']==0
        assert all(not widgets['PlayerBotBar'+n]['enabled'] for n in ('Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread'))
        widgets['PlayerBotBarMeta']['text']='1280|960|0|1';g.PlayerBotBar_OnEvent('ADDON_TIMER');g.PlayerBotBar_OnEvent('PLAYER_ENTERING_WORLD')
        assert widgets['PlayerBotBar']['visible']==1 and widgets['PlayerBotBarGuard']['visible']==0
        # Actual native dragging changes physical coordinates; every size change
        # must preserve them exactly at each supported UI scale.
        widgets['PlayerBotBar']['rect']=(425,375,400*scale,70*scale)
        trigger('PlayerBotBarMore');assert widgets['PlayerBotBarGuard']['visible']==1
        assert all(widgets['PlayerBotBar'+n]['visible']==1 for n in ('Circle','Box','Line','Spread'))
        assert widgets['PlayerBotBar']['rect']==(425,375,400*scale,168*scale)
        trigger('PlayerBotBarHide');assert widgets['PlayerBotBarIcon']['visible']==1 and widgets['PlayerBotBarAttack']['visible']==0
        assert all(widgets['PlayerBotBar'+n]['visible']==0 for n in ('Circle','Box','Line','Spread'))
        assert widgets['PlayerBotBar']['rect']==(425,375,44*scale,52*scale)
        widgets['PlayerBotBar']['rect']=(525,475,44*scale,52*scale)
        trigger('PlayerBotBarIcon');assert widgets['PlayerBotBar']['rect']==(525,475,400*scale,168*scale)
        trigger('PlayerBotBarMore');assert widgets['PlayerBotBarGuard']['visible']==0
        assert all(widgets['PlayerBotBar'+n]['visible']==0 for n in ('Circle','Box','Line','Spread'))
        for n in ('Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread'):
            widgets['PlayerBotBarTx']['text']='';trigger('PlayerBotBar'+n);assert widgets['PlayerBotBarTx']['text']==(('formation_'+n.lower()) if n in ('Circle','Box','Line','Spread') else n.lower())
            trigger('PlayerBotBarAttack');assert widgets['PlayerBotBarTx']['text']==(('formation_'+n.lower()) if n in ('Circle','Box','Line','Spread') else n.lower()),'Do not overwrite an unsent command'
        widgets['PlayerBotBarTx']['text']='';widgets['PlayerBotBarMeta']['text']='1280|960|0|0';g.PlayerBotBar_OnEvent('ADDON_TIMER')
        trigger('PlayerBotBarAttack');assert widgets['PlayerBotBarTx']['text']==''
        trigger('PlayerBotBarManage');assert ('manage',()) in calls
        trigger('PlayerBotBarHide');g.DoSlashCmd('/botbar');assert widgets['PlayerBotBarIcon']['visible']==0
        assert not any(c[0] in ('SetTimer','ResetTimer') for c in calls),'Keep Wardrobe shared timer unchanged'
        # Persisted icon mode survives addon load and can be restored.
        g.Widget_TriggerScript('PlayerBotBar','OnLoad','');widgets['PlayerBotBarMeta']['text']='1280|960|2|1';g.PlayerBotBar_OnEvent('ADDON_TIMER')
        assert widgets['PlayerBotBarIcon']['visible']==1
    print('OK: real Lua 5.1 widgets; four UI scales; title position, draggable icon, expand/hide/restore, six party orders and four formations, disconnect and shared timer preservation.')

if __name__=='__main__':main()
