"""Exercise the bar through the installed client's real Lua 5.1 widget wrappers."""
import argparse
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as E
from prepare_bar import HERE,read_pak
sys.path.insert(0,str(HERE.parents[1]/'game-server/target/native-wardrobe-test-deps'))
from lupa.lua51 import LuaRuntime

def main():
    p=argparse.ArgumentParser();p.add_argument('--client',type=Path,required=True);p.add_argument('--pull-only',action='store_true')
    p.add_argument('--delivery',type=Path,help='Overlay staged new assets on read-only client; exercise maintained Lua')
    p.add_argument('--trace-output',type=Path,help='Write actual 20 Hz widget states for offline composited previews')
    p.add_argument('--json-output',type=Path);a=p.parse_args()
    source_mode=bool(a.delivery) or not a.pull_only
    report=dict(scales=4,screens=3,rollPairs=0,cancellations=0,restarts=0,maxPlaybackImageWritesPerTick=0,traces={})
    z=read_pak(a.client/'bin32/bin32.pak')
    with read_pak(a.client/'Plugin/RelicCalc/RelicCalc.pak') as addon:
        installed_bar=addon.read('PlayerBotBar.lua').decode('utf-8-sig')
        tiered=source_mode or 'PULL_TIERS' in installed_bar  # The installed addon is tier-aware once this build is in.
        installed_xml=addon.read('PlayerBotBar.xml')
    requested_textures=set();image_writes=[]
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
                    staged=a.delivery/texture if a.delivery else None
                    assert (staged and staged.is_file()) or (a.client/texture).is_file(),'Recruitment requested a missing texture: '+texture
                    image_writes.append((args[1],texture))
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
        tree=E.fromstring((HERE/'PlayerBotBar.xml').read_bytes() if source_mode else installed_xml);g.g_AddonName='RelicCalc'
        for n in tree:
            w=g.CreateWidget('Dialog',n.get('name'));w.LoadXML(w,table(n))
        lua.execute((HERE/'PlayerBotBar.lua').read_text() if source_mode else installed_bar)
        g.PrivateCompanions_Open=lambda:calls.append(('manage',()))
        match=dict(rect=(100,200,640,400),shown=True)
        cursor=dict(down=False)
        def core_call(command,*args):
            if command=='hireterms':calls.append(('hireterms',()));return
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
            # Hire Details now belongs to the existing native Find Group owner.
            # Test its real Lua handoff, not the deleted duplicate Lua dialog.
            tick(120)  # Admit the lazily built recruitment dialog through the real timer.
            before=sum(c[0]=='hireterms' for c in calls);tick(10)
            assert not g.PlayerBotBarState.hireFailed and sum(c[0]=='hireterms' for c in calls)==before+2
            assert 'PlayerBotHireTerms' not in widgets
            widgets['PlayerBotBarTx']['text']='attack'
        values=('POOR','ORDINARY','GOOD','GREAT','EXCELLENT')
        quality=dict(POOR=1,ORDINARY=1,GOOD=2,GREAT=3,EXCELLENT=4)
        timings={1:(38,33),2:(44,33),3:(56,36),4:(70,38)}
        def start(gear,rank):
            g.DoSlashCmd('/companionpull GUNNER '+gear+' '+rank+' Gailola')
            assert g.PlayerBotBarState.pull is not None
        def image(part):return str(widgets['PlayerBotPull'+part].get('images',{}).get(0,''))
        def stopped():
            assert g.PlayerBotBarState.pull is None and widgets['PlayerBotPull']['visible']==0
            before=len(image_writes);tick(3);assert len(image_writes)==before,'A closed pull must not update textures'
            if tiered:
                assert all(widgets['PlayerBotPull'+part]['visible']==0 for part in ('Sparks','Flourish','PanelFx','GearStamp','RankStamp'))
        def snapshot():
            parts=('Stage','Model','Sparks','Flourish','Result','Panel','Title','Medal','Name','Class','GearRow','GearIcon','GearLabel','Gear','GearStamp','RankRow','RankIcon','RankLabel','Rank','RankStamp','Divider','Note','PanelFx','ContinueFace','SkipFace')
            return {part:{key:(value.copy() if isinstance(value,dict) else value) for key,value in widgets.get('PlayerBotPull'+part,{}).items() if key in ('visible','rect','alpha','text','images')}
                    for part in parts if 'PlayerBotPull'+part in widgets} | {'rootAlpha':widgets['PlayerBotPull'].get('alpha',1)}
        def play(gear,rank,trace_name=None):
            tier=max(quality[gear],quality[rank]) if tiered else 1
            charge,burst=timings[tier];start(gear,rank)
            if tiered:assert g.PlayerBotBarState.pull.tier==tier
            assert image('Gear').endswith('value-'+gear+'.dds') and image('Rank').endswith('value-'+rank+'.dds')
            assert widgets['PlayerBotPullName']['text']=='Gailola' and widgets['PlayerBotPullName']['font']=='credit_team'
            assert image('Class').endswith('class-GUNNER.dds')
            assert all(widgets['PlayerBotPull'+part]['visible']==0 for part in ('Result','Model','Panel'))
            sequence=[];trace=[];stamp_ticks={'Gear':[],'Rank':[]};flourish=[];settled=None;model_tick=None;panel_tick=None
            # Include every shared held frame and complete stamp/ember loops.
            for n in range(2+charge+burst+18+110):
                before=len(image_writes);tick();P=g.PlayerBotBarState.pull
                written=image_writes[before:]
                report['maxPlaybackImageWritesPerTick']=max(report['maxPlaybackImageWritesPerTick'],len(written))
                assert len(written)<=5 and len({part for part,path in written})==len(written),'Only the active frame of each layer; no clip preload'
                assert P is not None,'Pull failed or timer swallowed an error'
                sequence.append(Path(image('Stage')).name)
                if widgets['PlayerBotPullModel']['visible'] and model_tick is None:model_tick=n
                if widgets['PlayerBotPullResult']['visible'] and panel_tick is None:panel_tick=n
                if P.complete and settled is None:settled=n
                if widgets.get('PlayerBotPullFlourish',{}).get('visible'):flourish.append(n)
                if n<2+charge+burst-1:assert widgets['PlayerBotPullResult']['visible']==0
                if widgets['PlayerBotPullResult']['visible']:
                    assert all(widgets['PlayerBotPull'+part]['visible']==1 for part in ('Panel','Title','Medal','Name','Class','Gear','Rank','GearLabel','RankLabel','ContinueFace')),'Panel contents enter together'
                for part in stamp_ticks:
                    if widgets.get('PlayerBotPull'+part+'Stamp',{}).get('visible'):
                        stamp_ticks[part].append(n)
                        assert P.complete and widgets['PlayerBotPullContinue']['visible']==1
                        value=gear if part=='Gear' else rank
                        assert f'stamp-tier{quality[value]}-' in image(part+'Stamp')
                if trace_name:trace.append(snapshot())
            expected=['charge-000.dds']*2
            for n in range(1,charge):
                expected.append(f'tier{tier}-charge-{n-18:03d}.dds' if tier>1 and n>=18 else f'charge-{n:03d}.dds')
            expected += [f'tier{tier}-burst-{n:03d}.dds' if tier>1 else f'burst-{n:03d}.dds' for n in range(burst)]
            assert sequence[:len(expected)]==expected,(tier,sequence[:len(expected)],expected)
            assert sequence[len(expected)]=='hold-000.dds'
            assert model_tick==2+charge+2 and panel_tick==2+charge+burst-1
            assert settled==panel_tick+17 and widgets['PlayerBotPullContinue']['visible']==1
            assert widgets['PlayerBotPullPanelFx']['visible']==0 and g.PlayerBotBarState.pull.panelFrame is None
            assert 'PlayerBotPullHeader' not in widgets
            if tiered:
                assert len(stamp_ticks['Gear'])==12 and len(stamp_ticks['Rank'])==12
                assert stamp_ticks['Gear'][0]==settled and stamp_ticks['Rank'][0]==settled+14
                assert g.PlayerBotBarState.pull.stampFrame is None
                strength=dict(POOR=.64,ORDINARY=.76,GOOD=.9,GREAT=1.05,EXCELLENT=1.2)
                actual=widgets['PlayerBotPullGearStamp']['rect'][2]/widgets['PlayerBotPullRankStamp']['rect'][2]
                assert abs(actual-strength[gear]/strength[rank])<.000001,'Each value has its own stamp size'
                assert len(flourish)==(16 if gear==rank=='EXCELLENT' else 0),'Exclusive finite double-Excellent flourish'
                if tier==4:assert (settled+1)*.05<=6.5
                assert all(widgets['PlayerBotPull'+part]['visible']==0 for part in ('Flourish','GearStamp','RankStamp'))
            if trace_name:
                report['traces'][trace_name]=dict(tier=tier,gear=gear,rank=rank,panelSeconds=(panel_tick+1)*.05,settledSeconds=(settled+1)*.05,frames=trace)
            return settled
        for width,height in ((1280,960),(1920,1080),(3440,1440)):
            widgets['PlayerBotBarMeta']['text']=f'{width}|{height}|0|1';tick()
            for gear,rank in (('POOR','ORDINARY'),('GOOD','POOR'),('POOR','GREAT'),('GOOD','EXCELLENT'),('EXCELLENT','EXCELLENT')):
                name=('tier'+str(max(quality[gear],quality[rank]))+('-double' if gear==rank=='EXCELLENT' else '')) if scale==1 and width==1920 else None
                play(gear,rank,name)
                # Cached native modal bounds and visible native widgets agree;
                # keep the existing physical-coordinate contract at every scale.
                P=g.PlayerBotBarState.pull
                assert tuple(P.skipRect[i] for i in range(1,5))==widgets['PlayerBotPullSkip']['rect']
                model=widgets['PlayerBotPullModel']['rect']
                assert abs((model[0]+model[2]/2)/width-.505)<.0001
                assert abs((model[1]+model[3]-height*120/1080)/height-.7088888889)<.0001
                gx,gy,gw,gh=widgets['PlayerBotPullResult']['rect']
                x,y,w,h=widgets['PlayerBotPullContinue']['rect'];x+=gx;y+=gy
                assert x>=0 and y>=0 and x+w<=width and y+h<=height
                cursor.update(x=x+w/2,y=y+h/2,down=False);tick();assert image('ContinueFace').endswith('continue-1.dds')
                cursor['down']=True;tick();assert image('ContinueFace').endswith('continue-2.dds')
                cursor['down']=False;tick();assert image('ContinueFace').endswith('continue-1.dds')
                trigger('PlayerBotPullContinue');stopped()
                assert 0 not in widgets['PlayerBotPullContinue'].get('images',{}),'Inert image owns button decoration'
                start(gear,rank);tick(3)
                x,y,w,h=widgets['PlayerBotPullSkip']['rect']
                cursor.update(x=x+w/2,y=y+h/2,down=False);tick();assert image('SkipFace').endswith('skip-1.dds')
                cursor['down']=True;tick();assert image('SkipFace').endswith('skip-2.dds')
                cursor['down']=False;trigger('PlayerBotPullSkip');stopped()
        if tiered and scale==1:
            widgets['PlayerBotBarMeta']['text']='1920|1080|0|1';tick()
            # Every ordered pair, including all mixed results, through slash/timer.
            for gear in values:
                for rank in values:
                    play(gear,rank);g.DoSlashCmd('/companionpullclose');stopped();report['rollPairs']+=1
            # Each interruption phase of each tier: warmup, shared charge, tier
            # tail, burst before/after model, wash/flourish, panel-in, stamps,
            # ready/hold. Native keyboard/mouse dispatch itself needs the game.
            for gear,rank in (('POOR','ORDINARY'),('GOOD','POOR'),('POOR','GREAT'),('GOOD','EXCELLENT'),('EXCELLENT','EXCELLENT')):
                tier=max(quality[gear],quality[rank]);charge,burst=timings[tier]
                phases=(0,2,12,25,charge+3,charge+6,charge+17,charge+burst+5,charge+burst+19,charge+burst+34,charge+burst+55)
                for phase in phases:
                    for action in ('Skip','Continue','close','disconnect','world','hidden','restart'):
                        start(gear,rank);tick(phase)
                        if action in ('Skip','Continue'):trigger('PlayerBotPull'+action)
                        elif action=='close':g.DoSlashCmd('/companionpullclose')
                        elif action=='disconnect':
                            widgets['PlayerBotBarMeta']['text']='1920|1080|0|0';tick()
                        elif action=='world':g.PlayerBotBar_OnEvent('PLAYER_ENTERING_WORLD')
                        elif action=='hidden':
                            # A native modal hide (e.g. Escape) is observed on the next tick.
                            tick(2);widgets['PlayerBotPull']['visible']=0;tick()
                        else:
                            start('POOR','ORDINARY');P=g.PlayerBotBarState.pull
                            assert P.tier==1 and P.frame==0 and P.reveal is None and P.panelFrame is None and P.stampFrame is None
                            assert all(widgets['PlayerBotPull'+part]['visible']==0 for part in ('Result','Model','Sparks','Flourish','GearStamp','RankStamp','Continue'))
                            assert image('Stage').endswith('charge-000.dds')
                            tick(3);assert image('Stage').endswith('charge-001.dds')
                            g.DoSlashCmd('/companionpullclose');report['restarts']+=1
                        stopped();report['cancellations']+=1
                        widgets['PlayerBotBarMeta']['text']='1920|1080|0|1';tick()
        if a.pull_only or source_mode:
            classes=('TEMPLAR','GLADIATOR','ASSASSIN','RANGER','SORCERER','SPIRIT_MASTER','CLERIC','CHANTER','GUNNER','RIDER','BARD')
            widgets['PlayerBotBarMeta']['text']='1920|1080|0|1';tick()
            for index,advanced in enumerate(classes):
                g.DoSlashCmd('/companionpull '+advanced+' '+values[index%5]+' '+values[(index+1)%5]+' Assetcheck')
                tick(3);g.DoSlashCmd('/companionpullclose')
        assert widgets['PlayerBotBarTx']['text']=='attack','Pull playback never sends gameplay orders'
    report['requestedTextures']=sorted(requested_textures)
    report['sourceMode']=source_mode
    if a.delivery:
        added={path.relative_to(a.delivery).as_posix().lower() for path in (a.delivery/'Textures/UI/AetherfallSummon').glob('*.dds')}
        requested={path.lower() for path in requested_textures}
        assert added<=requested,'Unexercised staged textures: '+str(added-requested)
        report['newTexturesExercised']=len(added)
    traces=report.pop('traces')
    if a.trace_output:
        a.trace_output.parent.mkdir(parents=True,exist_ok=True)
        a.trace_output.write_text(json.dumps(dict(fps=20,width=1920,height=1080,model='Native 3D companion omitted; in-game acceptance required',pulls=traces)))
    if a.json_output:
        a.json_output.parent.mkdir(parents=True,exist_ok=True)
        a.json_output.write_text(json.dumps(report,indent=2)+'\n')
    print('OK:',len(requested_textures),'recruitment textures resolve using real Lua 5.1 and installed widget/Core wrappers.')
    print('OK: exact clip order, suspense, model timing, simultaneous panel, finite effects, button geometry/faces; three screens/four scales.')
    if tiered:print('OK:',report['rollPairs'],'ordered roll pairs,',report['cancellations'],'phase interruptions,',report['restarts'],'clean restarts.')
    if not a.pull_only:print('OK: bar expand/hide/restore, commands/formations/distances, native Hire Details handoff, disconnect and shared timer.')

if __name__=='__main__':main()
