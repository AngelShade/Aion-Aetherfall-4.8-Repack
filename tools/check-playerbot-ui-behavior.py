"""Exercise production companion controls in Aion's actual offscreen WebKit.

Uses an isolated HTTP fixture, never a game account/database or running game.

"""

import argparse

import copy

import ctypes as c

import http.server

import json
import hashlib

import os

from pathlib import Path

import threading

import time

from urllib.parse import parse_qs, urlparse

from PIL import Image



ROOT = Path(__file__).resolve().parents[1]

MEDIA = ROOT / 'game-server/config/playerbots/media'



def fixture():

    bot = dict(rangedSpacing=True,ownerSpacing=4,attackSpacing=10,temporary=True,generated=True,build='Temporary Bot · CLERIC · tier 60',id=1, name='Astra', level=65, playerClass='CLERIC', role='HEALER', order='FOLLOW', health=87, mana=64,

               dead=False, closing=False, status='ready', action='heal party member', mission=0, missionStatus='',

               area=False, supplies=True, gear=True, loot=False, questing=True, questCombat=True, enchant=False, salvage=False, partySync=True, reserve=10000, dailyBudget=50000, spent=0,

               gearMode='EARNED',gearProfile='AUTO',gearQuality='LEGEND',gearWeapon='AUTO',gearLevel=0,gearThreshold=1.1,gearVendors=False,gearRolls='UPGRADES',gearTargetLevel=65,

               questions=[dict(quest=900003,name='A companion behind',reason='Will you help me catch up?',prerequisites=[dict(id=900005,name='The first step')]),dict(quest=900004,name='A declined path',reason='You already finished this quest. Will you help?',prerequisites=[])], announcements=['I accepted Symbol of the Chosen. Returning to you.'],

               inventory=[dict(id=10,itemId=100000096,tooltip='item=100000096&count=1&enchant_count=9&authorize_count=3',enchant=9,tempering=3, name='Companion training mace', count=1, equipped=False, equippedSlots=[],slots=['MAIN_HAND']),dict(id=11,itemId=162000001,tooltip='item=162000001&count=12&enchant_count=0&authorize_count=0',name='Recovery potion',count=12,equipped=False,slots=[]),dict(id=30,itemId=115000075,tooltip='item=115000075&count=1&enchant_count=4&authorize_count=0',name='Native worn gloves',count=1,equipped=True,equippedSlots=['GLOVES'],slots=['GLOVES'],enchant=4,bonusStatsId=1,bonusModifiers=[dict(name='MAXHP',value=120,operation='add',conditional=False),dict(name='MAXMP',value=10,operation='rate',conditional=True)],fusionItemId=100000096,fusionName='Native fusion sword',fusionModifiers=[dict(name='PHYSICAL_ATTACK',value=8,operation='add',conditional=False)],godstoneId=168000001,godstoneName='Native godstone',manastones=[dict(slot=0,itemId=167000001,name='Native stone')]),dict(id=31,itemId=140000001,tooltip='item=140000001&count=1&enchant_count=0&authorize_count=0',name='Native worn stigma',count=1,equipped=True,equippedSlots=['STIGMA1'],slots=['STIGMA1'])],

               quests=[dict(id=900001,name='Protect the road',status='START',progress=2,missionEligible=True,ready=False,guidance='These are my current quest objective NPCs.',objectives=[dict(name='Road invader',current=2,required=5)],destinations=[]),dict(id=900002,name='Symbol of the Chosen',status='REWARD',progress=0,missionEligible=False,ready=True,guidance='Lead me near a listed NPC; I will turn it in.',destinations=[dict(npc=203011,name='Lunax',map=110010000,x=1292,y=1684,z=574,distance=25)])])

    return dict(owner='FixturePlayer', ownerLevel=65,levelDifference=10,enabled=True, generatedEnabled=True, limit=5, active=[bot],

                roster=[dict(id=1, name='Astra', level=65, playerClass='CLERIC', reserved=True, generated=True, ready=True),

                        dict(id=2, name='Kael', level=64, playerClass='TEMPLAR', reserved=False, generated=False, ready=True)],

                classes=['PRIEST', 'CLERIC', 'TEMPLAR'], startingClasses=['PRIEST', 'WARRIOR'], roles=['TANK', 'HEALER', 'MELEE', 'RANGED'],

                quests=[dict(id=900001,name='Protect the road',status='START')], notice='', request='fixture-only-0')



def main():

    global MEDIA

    parser = argparse.ArgumentParser(description=__doc__)

    parser.add_argument('--browser-bin', type=Path, required=True)

    parser.add_argument('--media', type=Path, default=MEDIA)
    parser.add_argument('--output',type=Path,default=Path(r'D:/Proiecte/Project Restructure/Aion Development Workspace/diagnostics/playerbots-ui-20261010'))

    args = parser.parse_args()

    MEDIA=args.media.resolve()
    out=args.output.resolve()
    if out==ROOT or ROOT in out.parents or out==args.browser_bin.resolve().parent or args.browser_bin.resolve().parent in out.parents: raise ValueError('Generated browser evidence must remain outside source and installations')
    out.mkdir(parents=True,exist_ok=True); (out/'report.json').unlink(missing_ok=True)
    for old in out.glob('companions-*.png'):old.unlink()
    def digest(path):
        with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
    bound=[Path(__file__).resolve(),*[MEDIA/name for name in ['bots.html','bots.css','bots.js']],*[args.browser_bin.resolve()/name for name in ['Awesomium.dll','AwesomiumProcess.exe','icudt42.dll']]]
    inputs={str(path):digest(path) for path in bound}

    state, posts, errors, sequence = fixture(), [], [], [0]; failed_response=[None]; requested=[]

    class Handler(http.server.BaseHTTPRequestHandler):

        def log_message(self, *unused): pass

        def send(self, data, content='application/json', status=200):

            if isinstance(data, dict):

                state['recruitRemaining'] = max(0,state['limit']-len(state['active']))
                state.setdefault('generatedCount',1);state.setdefault('generatedLimit',23)
                sequence[0] += 1; state['request'] = 'fixture-only-' + str(sequence[0]); data = json.dumps(data)

            data = data if isinstance(data, bytes) else data.encode()

            self.send_response(status); self.send_header('Content-Type', content); self.send_header('Content-Length', str(len(data)))

            self.end_headers(); self.wfile.write(data)

        def do_GET(self):
            requested.append(self.path)

            path = urlparse(self.path).path

            if path == '/market/companions': self.send((MEDIA/'bots.html').read_bytes(), 'text/html; charset=utf-8')

            elif path in ['/market/companions/media/bots.js', '/market/companions/media/bots.css']:

                self.send((MEDIA/path.rsplit('/', 1)[1]).read_bytes(), 'application/javascript' if path.endswith('.js') else 'text/css')

            elif path == '/market/companions/state':
                mode=failed_response[0]
                if mode=='malformed':self.send({'request':'bad-shape'})
                elif mode=='null':self.send('null')
                elif mode=='denied':self.send({'error':'Fixture session expired'},status=403)
                else:self.send(state)

            else: self.send_error(404)

        def do_POST(self):

            try:

                form = {k:v[0] for k,v in parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode()).items()}

                assert form['session_id'] == 'fixture' and form['request'] == state['request'], 'stale or missing form ticket'

                posts.append(form); bot = state['active'][0]; op = form['action']

                if op == 'savebot':
                    assert form['name']=='Astra'; state.setdefault('savedBots',[])
                    if 1 not in state['savedBots']: state['savedBots'].append(1)
                elif op == 'saveparty':
                    assert form['presetName']=='Dungeon team'
                    state['presets']=[dict(id='1'*32,name=form['presetName'],members=[dict(id=b['id'],name=b['name'],temporary=b.get('generated',True),role=b['role'],order=b['order']) for b in state['active']])]
                elif op == 'loadparty':
                    assert form['preset']=='1'*32 and any(not m['temporary'] for m in state['presets'][0]['members'])
                elif op == 'deleteparty':
                    assert form['preset']=='1'*32;state['presets']=[]
                elif op == 'removebot':
                    assert form['id']=='1' and form['confirm']=='yes'
                    assert next(r for r in state['roster'] if r['id']==1)['generated']
                    state['active']=[b for b in state['active'] if b['id']!=1]
                    state['roster']=[r for r in state['roster'] if r['id']!=1]
                    state['savedBots']=[i for i in state.get('savedBots',[]) if i!=1]
                    for preset in state['presets']:preset['members']=[m for m in preset['members'] if m['id']!=1]
                elif op == 'order': bot['order'] = form['order']

                elif op == 'setting': bot[form['setting']] = form['enabled'] == 'true'

                elif op == 'role': bot['role'] = form['role'];bot['rangedSpacing']=form['role'] in ('HEALER','RANGED','SUPPORT')

                elif op == 'spacing':
                    assert form['name']=='Astra'
                    f,a=float(form['ownerSpacing']),float(form['attackSpacing']);assert 2<=f<=12 and 4<=a<=18
                    bot.update(ownerSpacing=f,attackSpacing=a)

                elif op == 'mission': bot['mission'] = int(form['quest']); bot['missionStatus'] = 'traveling to quest objective'

                elif op == 'equip':

                    assert form['item'] == '10' and form['slot'] == 'MAIN_HAND'; bot['inventory'][0]['equipped'] = True;bot['inventory'][0]['equippedSlots']=['MAIN_HAND']

                elif op == 'recruit':

                    assert form['name'] == 'Kael'; state['roster'][1]['reserved'] = True

                    other = copy.deepcopy(bot); other.update(id=2, name='Kael', playerClass='TEMPLAR', role='TANK',temporary=False,generated=False,gear=False); state['active'].append(other)

                elif op in ['create', 'generate']:

                    assert form['name'] in ['Newpriest', 'Newtemplar', 'Newnovice']

                    assert form['start'] in ['matched','level1']

                elif op in ['maillist','mailread','mailtake','maildelete','mailsendkinah','mailsenditem','bankdeposit','bankwithdraw','legiondeposit']:
                    assert form['name']=='Astra' and not bot.get('world')
                    if op=='maillist':bot['mailLoaded']=True
                    elif op in ['mailread','mailtake','maildelete']:
                        letter=next(x for x in bot['mail'] if str(x['id'])==form['object'])
                        if op=='mailread':letter['unread']=False
                        elif op=='mailtake':assert letter['hasAttachments'];letter['hasAttachments']=False
                        else:assert not letter['hasAttachments'];bot['mail'].remove(letter)
                    elif op=='mailsendkinah':assert form['kinah']=='1000'
                    elif op=='mailsenditem':
                        item=next(x for x in bot['inventory'] if str(x['id'])==form['object']);assert item['mailable'];bot['inventory'].remove(item)
                    elif op=='bankwithdraw':
                        item=next(x for x in bot['warehouse'] if str(x['id'])==form['item']);bot['warehouse'].remove(item);bot['inventory'].append(item)
                    else:
                        item=next(x for x in bot['inventory'] if str(x['id'])==form['item']);assert item['bankable'] if op=='bankdeposit' else item['legionStorable'];bot['inventory'].remove(item);bot['warehouse' if op=='bankdeposit' else 'legionStorage'].append(item)
                elif op == 'team':
                    team=state['team'];operation=form['operation'];assert form['team']==str(team['id'])
                    members=[member for group in team['groups'] for member in group['members']]
                    member=next((m for m in members if str(m['id'])==form.get('member')),None)
                    if operation=='convert':
                        assert team['kind']=='Party' and team['canConvert']
                        group=team['groups'][0];group.update(id=1000,number=1)
                        state['teamKind']='Alliance';team.update(id=9001,kind='Alliance',capacity=24,canConvert=False,groups=[group]+[dict(id=1000+i,number=i+1,capacity=6,members=[]) for i in range(1,4)])
                    elif operation=='invite':assert form['name']=='InvitedHuman'
                    elif operation=='ready':team['ready']=dict(active=True,waiting=1,states=[dict(id=m['id'],status='WAITING' if m['self'] else 'READY') for m in members])
                    elif operation=='cancelready':team['ready']['active']=False;team['ready']['waiting']=0
                    elif operation=='move':
                        assert member and form['group']=='1001'
                        for group in team['groups']:
                            if member in group['members']:group['members'].remove(member)
                        team['groups'][1]['members'].append(member)
                    elif operation=='swap':
                        second=next(m for m in members if str(m['id'])==form['second']);assert member and second!=member
                        left=next(g for g in team['groups'] if member in g['members']);right=next(g for g in team['groups'] if second in g['members'])
                        left['members'].remove(member);right['members'].remove(second);left['members'].append(second);right['members'].append(member)
                    elif operation=='vice':assert member;member['viceCaptain']=form['enabled']=='true'
                    elif operation=='kick':
                        assert member and not member['self'] and form['confirm']=='yes'
                        for group in team['groups']:
                            if member in group['members']:group['members'].remove(member)
                        team['memberCount']-=1
                    elif operation=='leader':
                        assert member
                        for m in members:m['leader']=m==member
                        team.update(leaderId=member['id'],canLead=False,canManage=False)
                    elif operation=='leave':
                        assert form['confirm']=='yes';state['teamKind']='Solo';state['team']=dict(id=0,kind='Solo',leaderId=5000,canLead=True,canManage=False,canConvert=False,capacity=6,memberCount=1,groups=[],ready=dict(active=False,waiting=0,states=[]))
                    else:raise AssertionError('unknown native team operation '+operation)
                elif op == 'share': assert form['quest'] == '900001'

                elif op == 'attack': pass

                elif op == 'summon': assert form['name'] in ['all','Astra']

                elif op == 'answer':

                    assert (form['quest'],form['answer']) in [('900003','yes'),('900004','no')]

                    bot['questions']=[q for q in bot['questions'] if str(q['quest'])!=form['quest']]

                elif op == 'gearpolicy':

                    assert form['mode']=='GENERATED' and form['profile']=='TANK' and form['quality']=='UNIQUE' and form['weapon']=='SWORD' and form['level']=='60' and form['threshold']=='1.2' and form['vendors']=='true' and form['rolls']=='PASS'

                    bot.update(gearMode=form['mode'],gearProfile=form['profile'],gearQuality=form['quality'],gearWeapon=form['weapon'],gearLevel=int(form['level']),gearThreshold=float(form['threshold']),gearVendors=form['vendors']=='true',gearRolls=form['rolls'],gearTargetLevel=60)

                elif op == 'carebudget':

                    assert form['reserve']=='25000' and form['dailyBudget']=='75000';bot['reserve']=25000;bot['dailyBudget']=75000

                else: raise AssertionError('unexpected action ' + op)

                state['notice'] = 'Companion request completed.'; self.send(state)

            except Exception as error:

                errors.append(str(error)); self.send({'error':str(error)}, status=400)

    server = http.server.ThreadingHTTPServer(('127.0.0.1',0), Handler)

    threading.Thread(target=server.serve_forever, daemon=True).start()

    directory = os.add_dll_directory(str(args.browser_bin.resolve()))

    lib = c.CDLL(str(args.browser_bin/'Awesomium.dll')); ptr = c.c_void_p

    def api(name, result, *parameters):

        f = getattr(lib, name); f.restype = result; f.argtypes = list(parameters); return f

    initialize=api('awe_webcore_initialize_default',None); update=api('awe_webcore_update',None); shutdown=api('awe_webcore_shutdown',None)

    create=api('awe_webcore_create_webview',ptr,c.c_int,c.c_int,c.c_bool); destroy=api('awe_webview_destroy',None,ptr)

    make=api('awe_string_create_from_wide',ptr,c.c_wchar_p,c.c_size_t); free=api('awe_string_destroy',None,ptr)

    load=api('awe_webview_load_url',None,ptr,ptr,ptr,ptr,ptr); evaluate=api('awe_webview_execute_javascript_with_result',ptr,ptr,ptr,ptr,c.c_int)

    text=api('awe_jsvalue_to_string',ptr,ptr); utf8=api('awe_string_to_utf8',c.c_size_t,ptr,ptr,c.c_size_t); jsfree=api('awe_jsvalue_destroy',None,ptr)

    render=api('awe_webview_render',ptr,ptr); pixels=api('awe_renderbuffer_get_buffer',ptr,ptr); rowspan=api('awe_renderbuffer_get_rowspan',c.c_int,ptr)

    move=api('awe_webview_inject_mouse_move',None,ptr,c.c_int,c.c_int); down=api('awe_webview_inject_mouse_down',None,ptr,c.c_int); up=api('awe_webview_inject_mouse_up',None,ptr,c.c_int)

    tip_events=[]
    tip_callback=c.CFUNCTYPE(None,ptr,ptr)
    def on_tooltip(webview,value):
        buf=c.create_string_buffer(4096);utf8(value,buf,len(buf));tip_events.append(buf.value.decode())
    tip_listener=tip_callback(on_tooltip)
    set_tooltip=api('awe_webview_set_callback_change_tooltip',None,ptr,tip_callback)
    initialize(); empty=make('',0); views=[]

    def js(view,code):

        code='String('+code+')'; s=make(code,len(code)); value=evaluate(view,s,empty,1000); free(s)

        if not value:return ''

        string=text(value); buf=c.create_string_buffer(65536); utf8(string,buf,len(buf)); free(string); jsfree(value); return buf.value.decode()

    def pump(seconds=.2):

        until=time.monotonic()+seconds

        while time.monotonic()<until:update();time.sleep(.015)

    def wait(view,code,expected):

        until=time.monotonic()+12

        while time.monotonic()<until:

            pump(.1)

            if js(view,code)==expected:return

        raise AssertionError('Expected '+expected+', got '+js(view,code)+'; '+js(view,"document.getElementById('notice').textContent")+'; ready='+js(view,'document.readyState')+'; requests='+repr(requested[-10:]))

    def hover(view,selector,query):
        set_tooltip(view,tip_listener)
        js(view,'(function(){document.querySelector('+json.dumps(selector)+').scrollIntoView(false);return true;}())');pump(.1)
        point=json.loads(js(view,'(function(){var r=document.querySelector('+json.dumps(selector)+').getBoundingClientRect();return JSON.stringify([Math.round((r.left+r.right)/2),Math.round((r.top+r.bottom)/2)]);}())'))
        move(view,0,0);pump(.1);tip_events.clear();move(view,*point);pump(.5)
        assert 'nc://aion.ItemInfo/ItemTooltip?'+query in tip_events,('Native hover missing',selector,tip_events)

    def click(view,selector):

        wait(view,"document.getElementById('notice').getAttribute('aria-busy')",'false')
        js(view,'(function(){document.querySelector('+json.dumps(selector)+').scrollIntoView(false);return true;}())');pump(.15)

        geometry=json.loads(js(view,'(function(){var b=document.querySelector('+json.dumps(selector)+'),r=b.getBoundingClientRect(),x=Math.round((r.left+r.right)/2),y=Math.round((r.top+r.bottom)/2),h=document.elementFromPoint(x,y);return JSON.stringify({point:[x,y],hit:h===b||b.contains(h)});}())'))
        point=geometry['point']
        assert geometry['hit'],('Mouse point obstructed',selector,point,js(view,'window.innerWidth+","+window.innerHeight'))

        move(view,*point);down(view,0);up(view,0);pump(.3)
        wait(view,"document.getElementById('notice').getAttribute('aria-busy')",'false')

    def click_named(view,parent,prefix):

        wait(view,"document.getElementById('notice').getAttribute('aria-busy')",'false')
        index=int(js(view,'(function(){var buttons=document.querySelectorAll('+json.dumps(parent+' button')+');for(var i=0;i<buttons.length;i++)if(buttons[i].textContent.indexOf('+json.dumps(prefix)+')===0)return i;return -1;}())'))

        assert index>=0,'Missing button: '+prefix

        selector=js(view,'(function(){var button=document.querySelectorAll('+json.dumps(parent+' button')+')['+str(index)+'],key=button.getAttribute("data-focus-key");if(key)return '+json.dumps(parent+' button[data-focus-key=')+'+JSON.stringify(key)+"]";button.setAttribute("data-fixture-target","true");return "[data-fixture-target=true]";}())')

        click(view,selector)

        js(view,'(function(){var old=document.querySelector("[data-fixture-target=true]");if(old)old.removeAttribute("data-fixture-target");return true;}())')

    def shot(view,name,w,h,top=True):

        if top:js(view,'(function(){window.scrollTo(0,0);return true;}())')
        pump(.2);buffer=render(view);assert buffer

        Image.frombytes('RGBA',(w,h),c.string_at(pixels(buffer),rowspan(buffer)*h),'raw','BGRA',rowspan(buffer)).save(out/name)

    try:

        spacingView=create(390,844,False);views.append(spacingView)
        spacingUrl='http://127.0.0.1:'+str(server.server_port)+'/market/companions?session_id=fixture'
        string=make(spacingUrl,len(spacingUrl));load(spacingView,string,empty,empty,empty);free(string)
        wait(spacingView,"document.querySelectorAll('#active .bot-summary').length",'1')
        click(spacingView,'#active .bot-summary:first-child');click(spacingView,'#bot-tab-behavior');click(spacingView,'#bot-subtab-spacing')
        assert js(spacingView,"document.getElementById('spacing-attack').value")=='10'
        js(spacingView,"(function(){document.querySelector('.spacing-controls').scrollIntoView(true);return true;}())")
        assert js(spacingView,'document.documentElement.scrollWidth <= window.innerWidth')=='true'
        shot(spacingView,'companions-spacing-390.png',390,844,False)
        destroy(spacingView);views.remove(spacingView)

        for width,height in [(1280,900),(1024,768),(900,700),(390,844)]:

            view=create(width,height,False);views.append(view); url='http://127.0.0.1:'+str(server.server_port)+'/market/companions?session_id=fixture'

            string=make(url,len(url));load(view,string,empty,empty,empty);free(string)

            wait(view,"document.querySelectorAll('#active .bot-summary').length",str(len(state['active'])))

            shot(view,'companions-'+str(width)+'.png',width,height)

            assert js(view,'document.documentElement.scrollWidth <= window.innerWidth')=='true', 'horizontal overflow'

            if width!=1280:

                click(view,'#active .bot-summary:first-child')

                shot(view,'companions-detail-'+str(width)+'.png',width,height)

                assert js(view,'document.documentElement.scrollWidth <= window.innerWidth')=='true'

                click(view,'#bot-tab-equipment');click(view,'#bot-subtab-profile')
                js(view,"(function(){document.querySelector('.character-profile').scrollIntoView(true);return true;}())")
                assert js(view,'document.documentElement.scrollWidth <= window.innerWidth')=='true'
                shot(view,'companions-character-'+str(width)+'.png',width,height,False)
                click(view,'#bot-subtab-inventory')
                js(view,"(function(){document.querySelector('#bot-section-inventory .cube-panel').scrollIntoView(true);return true;}())")
                assert js(view,'document.documentElement.scrollWidth <= window.innerWidth')=='true'
                shot(view,'companions-inventory-'+str(width)+'.png',width,height,False)
                if width==390:click(view,'#bot-back')

                click(view,'#tab-roster')

                assert js(view,"document.querySelectorAll('#roster .roster-row').length")=='12'

                shot(view,'companions-roster-'+str(width)+'.png',width,height)

                assert js(view,'document.documentElement.scrollWidth <= window.innerWidth')=='true'

                continue

            # Exercise actual legacy WebKit DOM events and focus, without server mutations.
            before=len(posts)
            def key(selector,code,alt=False):
                return js(view,"(function(){var n=document.querySelector("+json.dumps(selector)+");n.focus();var e=document.createEvent('Event');e.initEvent('keydown',true,true);e.keyCode="+str(code)+";e.altKey="+str(alt).lower()+";n.dispatchEvent(e);return true;}())")
            key('#tab-party',39)
            assert js(view,"document.getElementById('tab-roster').getAttribute('aria-selected')")=='true'
            assert js(view,"document.activeElement.id")=='tab-roster'
            key('#tab-roster',35)
            assert js(view,"document.activeElement.id")=='tab-quests'
            key('#tab-quests',36)
            assert js(view,"document.activeElement.id")=='tab-party'
            key('#tab-party',51,True)
            assert js(view,"document.activeElement.id")=='tab-create'
            key('#new-name',191)
            assert js(view,"document.getElementById('tab-create').getAttribute('aria-selected')")=='true', 'Typing shortcut changed view'
            key('#tab-create',191)
            assert js(view,"document.activeElement.id")=='roster-search'
            click(view,'#keyboard-help');key('#keyboard-help',27)
            assert js(view,"document.getElementById('keyboard-panel').style.display")=='none'
            click(view,'#tab-party');click(view,'#bot-tab-equipment');click(view,'#bot-subtab-build')
            js(view,"(function(){var f=document.getElementById('draft-gear-profile');f.value='SUPPORT';f.focus();document.getElementById('refresh').onclick();return true;}())")
            pump(.3)
            assert js(view,"document.getElementById('draft-gear-profile').value")=='SUPPORT'
            assert js(view,"document.activeElement.id")=='draft-gear-profile','Refresh stole focus'
            click(view,'#bot-tab-activity');click(view,'#bot-tab-equipment');click(view,'#bot-subtab-build')
            assert js(view,"document.getElementById('draft-gear-profile').value")=='SUPPORT', 'Gear draft lost through tabs'
            js(view,"(function(){document.getElementById('draft-gear-profile').value='AUTO';return true;}())")
            click(view,'#bot-tab-mail');click(view,'#bot-subtab-send')
            js(view,"(function(){document.getElementById('draft-mail-kinah').value='12345';return true;}())")
            click(view,'#bot-tab-overview');click(view,'#bot-tab-mail');click(view,'#bot-subtab-send');click(view,'#refresh')
            assert js(view,"document.getElementById('draft-mail-kinah').value")=='12345','Mail amount lost through navigation/refresh'
            click(view,'#tab-roster');click(view,'#roster-tab-presets')
            assert js(view,"document.getElementById('roster-characters').style.display")=='none'
            key('#roster-tab-presets',37)
            assert js(view,"document.getElementById('roster-characters').style.display")=='block'
            assert len(posts)==before, 'Local navigation/drafts produced mutation'
            click(view,'#tab-party')
            click(view,'#bot-tab-behavior');click(view,'#bot-subtab-spacing')
            assert js(view,"document.getElementById('spacing-owner').value")=='4'
            assert js(view,"document.getElementById('spacing-attack').value")=='10'
            js(view,"(function(){var f=document.getElementById('spacing-owner'),a=document.getElementById('spacing-attack');f.value='3.5';a.value='9';f.oninput();return true;}())")
            click(view,'#refresh');assert js(view,"document.getElementById('spacing-owner').value")=='3.5'
            click(view,'#bot-tab-care');click(view,'#bot-tab-behavior');click(view,'#bot-subtab-spacing');assert js(view,"document.getElementById('spacing-attack').value")=='9'
            before=len(posts);js(view,"(function(){document.getElementById('spacing-attack').value='99';return true;}())")
            click_named(view,'.spacing-controls','Save ranged spacing');assert len(posts)==before
            js(view,"(function(){document.getElementById('spacing-attack').value='9';return true;}())")
            click_named(view,'.spacing-controls','Save ranged spacing');assert posts[-1]['action']=='spacing' and posts[-1]['ownerSpacing']=='3.5' and posts[-1]['attackSpacing']=='9'
            js(view,"(function(){document.querySelector('.spacing-controls').scrollIntoView(true);return true;}())")
            shot(view,'companions-spacing-1280.png',width,height,False)

            click(view,'#bot-tab-overview')

            assert js(view,"document.querySelectorAll('.bot .catch-up').length")=='2'

            click_named(view,'.bot .catch-up','Yes, I will help');wait(view,"document.querySelectorAll('.bot .catch-up').length",'1');assert posts[-1]['answer']=='yes'

            click_named(view,'.bot .catch-up','No, cancel this quest');wait(view,"document.querySelectorAll('.bot .catch-up').length",'0');assert posts[-1]['answer']=='no'

            click(view,'#tab-quests')

            assert js(view,"document.getElementById('quest-overview').textContent.indexOf('Astra: Ready to turn in')>=0 && document.getElementById('quest-overview').textContent.indexOf('Lunax')>=0 && document.getElementById('quest-overview').textContent.indexOf('1292')>=0")=='true'

            click(view,'#tab-party');click(view,'#bot-tab-care')

            click_named(view,'.bot','Auto enchant:');assert posts[-1]['setting']=='enchant' and posts[-1]['enabled']=='true'

            click_named(view,'.bot','Extract unused loot:');assert posts[-1]['setting']=='salvage' and posts[-1]['enabled']=='true'

            click(view,'#bot-subtab-budget')
            js(view,"(function(){var inputs=document.querySelectorAll('.bot input');inputs[0].value='25000';inputs[1].value='75000';return true;}())")

            click_named(view,'.bot','Save spending limits');assert posts[-1]['action']=='carebudget'

            click(view,'#bot-tab-overview')

            click(view,'#bot-tab-behavior');click(view,'#bot-subtab-automation')
            click_named(view,'#bot-section-automation','Party completion sync:');assert posts[-1]['setting']=='partySync' and posts[-1]['enabled']=='false'

            click_named(view,'#bot-section-automation','Nearby quest combat:');assert posts[-1]['setting']=='questCombat' and posts[-1]['enabled']=='false'


            click(view,'#bot-subtab-combat')
            click(view,'.bot .settings button:first-child');wait(view,"document.querySelector('.bot .settings button').textContent",'Area attacks: On')

            click(view,'#bot-tab-overview')
            js(view,"(function(){var n=document.querySelector('.role-row select');n.value='TANK';n.onchange();return true;}())");wait(view,"document.querySelector('.role-row select').value",'TANK')

            click(view,'#bot-tab-quests')

            assert js(view,"document.querySelectorAll('.bot .quest button').length")=='1', 'unsupported mission was offered'

            click(view,'.bot .quest button');wait(view,"document.querySelectorAll('.mission button').length",'1')

            click(view,'.mission button');wait(view,"document.querySelectorAll('.mission button').length",'0')

            click(view,'#bot-tab-equipment');click(view,'#bot-subtab-build')

            js(view,"(function(){var p=document.querySelector('.gear-policy'),sels=p.querySelectorAll('select'),inputs=p.querySelectorAll('input');var values=['GENERATED','TANK','UNIQUE','SWORD','PASS','true'];for(var i=0;i<values.length;i++)sels[i].value=values[i];inputs[0].value='60';inputs[1].value='1.2';return true;}())")

            click_named(view,'.gear-policy','Save equipment choices');assert posts[-1]['action']=='gearpolicy'

            wait(view,"document.querySelector('.gear-policy select').value",'GENERATED')

            shot(view,'companions-equipment-choices-1280.png',width,height)

            click(view,'#bot-subtab-profile')
            assert js(view,"document.querySelectorAll('#bot-section-profile .profile-slot').length")=='20'
            assert 'Native worn gloves' in js(view,"document.getElementById('bot-section-profile').textContent")
            profile_text=js(view,"document.getElementById('bot-section-profile').textContent")
            assert all(text in profile_text for text in ['Native stone','Maximum HP +120','Maximum MP +10% (conditional)','Native fusion sword','Physical Attack +8','Native godstone'])
            assert js(view,"document.querySelectorAll('#bot-section-profile [data-grid=equipped] [data-item-id=\"31\"]').length")=='1','Stigma outside profile positions omitted from equipped grid'
            click(view,'#bot-section-profile .cube-slot[data-item-id="31"]')
            assert 'Native worn stigma' in js(view,"document.querySelector('#bot-section-profile .cube-details').textContent")
            click(view,'#bot-section-profile .cube-slot[data-item-id="30"]')
            js(view,"(function(){document.querySelector('.character-profile').scrollIntoView(true);return true;}())")
            shot(view,'companions-character-1280.png',width,height,False)
            hover(view,'#bot-section-profile .profile-slot .item-icon','item=115000075&count=1&enchant_count=4&authorize_count=0')
            click(view,'#bot-subtab-inventory')
            assert js(view,"document.querySelectorAll('#bot-section-inventory [data-grid=inventory] .cube-cell').length")=='2'
            assert js(view,"document.querySelectorAll('#bot-section-inventory [data-item-id=\"30\"]').length")=='0','Equipped item duplicated in cube'
            hover(view,'#bot-section-inventory .cube-slot[data-item-id=\"10\"]','item=100000096&count=1&enchant_count=9&authorize_count=3')
            before=len(posts)
            click(view,'#bot-section-inventory .cube-slot[data-item-id="11"]');click(view,'#refresh')
            assert js(view,"document.querySelector('#bot-section-inventory .cube-slot.selected').getAttribute('data-item-id')")=='11','Selected cube item lost on refresh'
            assert len(posts)==before,'Cube selection/refresh made a mutation'
            click(view,'#bot-section-inventory .cube-slot[data-item-id="10"]')
            js(view,"(function(){document.querySelector('#bot-section-inventory .cube-panel').scrollIntoView(true);return true;}())")
            shot(view,'companions-inventory-1280.png',width,height,False)
            click(view,'.bot .item button')

            wait(view,"document.querySelector('.bot .item').textContent.indexOf('Equipped')>=0",'true')

            click(view,'#tab-quests');click(view,'#share');click(view,'#tab-roster');click_named(view,'#roster .roster-row:nth-child(2)','Recruit');wait(view,"document.querySelectorAll('#active .bot-summary').length",'2')

            click(view,'#tab-create')

            for model,name,pc in [('create','Newpriest','PRIEST'),('generate','Newtemplar','TEMPLAR')]:

                js(view,"(function(){var m=document.getElementById('model');m.value="+json.dumps(model)+";m.onchange();document.getElementById('new-name').value="+json.dumps(name)+";document.getElementById('new-class').value="+json.dumps(pc)+";return true;}())")

                before=len(posts);click(view,'#create button');wait(view,"document.getElementById('notice').textContent",'Companion request completed.');assert len(posts)==before+1

                assert posts[-1]['action']==model and posts[-1]['playerClass']==pc

                assert posts[-1]['start']==('level1' if model=='create' else 'matched')

            assert js(view,"document.querySelectorAll('#start-level option').length")=='1', 'Temporary Bots must offer level matching'

            assert 'Temporary Bot' in js(view,"document.getElementById('model').textContent")

            for index in range(3):

                other=copy.deepcopy(state['active'][0]);other.update(id=index+3,name='Companion'+str(index+3));state['active'].append(other)

            click(view,'#refresh');wait(view,"document.querySelectorAll('#active .bot-summary').length",'5')

            assert all(name in js(view,"document.getElementById('quest-overview').textContent") for name in ['Astra','Kael','Companion3','Companion4','Companion5'])

            click(view,'#tab-quests');shot(view,'companions-party-tracker-'+str(width)+'.png',width,height)

            # Large saved rosters must stay bounded; navigation/refresh are local UI state.

            for index in range(101):

                state['roster'].append(dict(id=100+index,name='Saved%03d'%index,level=65,playerClass='CLERIC',reserved=False,generated=index%2==0,ready=True))

            # Dedicated roster snapshots intentionally do not expose live level/class.

            del state['roster'][-1]['level'];del state['roster'][-1]['playerClass']

            click(view,'#tab-roster');click(view,'#refresh')

            wait(view,"document.querySelectorAll('#roster .roster-row').length",'12')

            shot(view,'companions-large-roster-'+str(width)+'.png',width,height)

            first=js(view,"document.querySelector('#roster .roster-row').textContent")

            click(view,'#roster-next')

            assert js(view,"document.querySelector('#roster .roster-row').textContent")!=first

            js(view,"(function(){var s=document.getElementById('roster-search');s.value='Saved100';s.oninput();return true;}())")

            wait(view,"document.querySelectorAll('#roster .roster-row').length",'1')

            assert 'Saved100' in js(view,"document.getElementById('roster').textContent")

            assert js(view,"document.getElementById('roster').textContent.indexOf('undefined')")=='-1'

            click(view,'#refresh')

            assert js(view,"document.getElementById('roster-search').value")=='Saved100'

            assert js(view,"document.querySelectorAll('#roster .roster-row').length")=='1'

            js(view,"(function(){var s=document.getElementById('roster-search'),f=document.getElementById('roster-filter');s.value='';f.value='generated';f.onchange();return true;}())")

            assert '52 companions' in js(view,"document.getElementById('roster-results').textContent")

            assert js(view,"document.querySelectorAll('#roster .roster-row').length")=='12'

            js(view,"(function(){var f=document.getElementById('roster-filter');f.value='active';f.onchange();return true;}())")

            assert js(view,"document.querySelectorAll('#roster .roster-row').length")=='2'

            js(view,"(function(){var f=document.getElementById('roster-filter'),s=document.getElementById('roster-search');f.value='all';s.value='Saved100';f.onchange();return true;}())")

            click(view,'#roster [data-action="view"]')

            assert js(view,"document.querySelector('#bot-detail .bot').textContent.indexOf('Saved100')>=0")=='true'

            assert js(view,"document.querySelector('#bot-detail .bot').textContent.indexOf('undefined')")=='-1'

            assert js(view,"document.querySelector('#bot-detail [data-action=recruit]').disabled")=='true'

            click(view,'#tab-roster')

            js(view,"(function(){var s=document.getElementById('roster-search');s.value='NoSuchCompanion';s.oninput();return true;}())")

            assert js(view,"document.querySelectorAll('#roster .roster-row').length")=='0'

            click(view,'#tab-party');click(view,'#active .bot-summary[data-bot-id="2"]');click(view,'#bot-tab-activity');click(view,'#refresh')

            assert js(view,"document.querySelector('#bot-detail .bot').textContent.indexOf('Kael')>=0")=='true'

            assert js(view,"document.getElementById('bot-tab-activity').getAttribute('aria-selected')")=='true'

            assert js(view,"document.querySelectorAll('#bot-detail .bot').length")=='1'

            shot(view,'companions-detail-activity-'+str(width)+'.png',width,height)

            click(view,'#bot-tab-equipment');assert 'Player-owned alt' in js(view,"document.querySelector('.gear-policy').textContent");assert js(view,"document.querySelectorAll('.gear-policy select').length")=='0'
            click(view,'#bot-tab-care');assert 'only to Temporary Bots' in js(view,"document.querySelector('.care-fields').textContent")
            click(view,'#active .bot-summary[data-bot-id="1"]');click(view,'#bot-tab-care');click(view,'#bot-subtab-budget')

            js(view,"(function(){var r=document.getElementById('care-reserve');r.value='32100';r.oninput();return true;}())")

            click(view,'#bot-tab-activity');click(view,'#bot-tab-care');click(view,'#refresh')

            assert js(view,"document.getElementById('care-reserve').value")=='32100', 'Care draft lost through tabs/refresh'

            assert js(view,"document.body.textContent.indexOf('\u00c2\u00b7')")=='-1', 'UTF-8 separator was corrupted'

            assert js(view,"document.body.textContent.indexOf('\u00e2\u20ac')")=='-1', 'UTF-8 punctuation was corrupted'

            click(view,'#bot-tab-overview');click_named(view,'.bot-header','Save Temporary Bot');assert posts[-1]['action']=='savebot'
            js(view,"(function(){document.getElementById('preset-name').value='Dungeon team';return true;}())")
            click(view,'#save-party button');assert posts[-1]['action']=='saveparty'
            click(view,'#tab-roster');wait(view,"document.querySelectorAll('#saved-parties .preset-card').length",'1')
            assert 'Kael / Tank / Player-owned alt' in js(view,"document.getElementById('saved-parties').textContent")
            shot(view,'companions-saved-parties-'+str(width)+'.png',width,height)
            click(view,'#roster-tab-presets');click_named(view,'#saved-parties','Summon preset');assert posts[-1]['action']=='loadparty'
            js(view,"(function(){var s=document.getElementById('roster-search'),f=document.getElementById('roster-filter');s.value='';f.value='saved';f.onchange();return true;}())")
            assert js(view,"document.querySelectorAll('#roster .roster-row').length")=='1'
            assert 'Saved Temporary Bot' in js(view,"document.getElementById('roster').textContent")
            click(view,'#roster-tab-presets');click_named(view,'#saved-parties','Remove preset');wait(view,"document.querySelectorAll('#saved-parties .preset-card').length",'0')
            assert 1 in state['savedBots'], 'Removing preset deleted saved bot'
            click(view,'#tab-party');click(view,'#save-party button');assert posts[-1]['action']=='saveparty'
            click(view,'#tab-roster');click(view,'#roster-tab-characters')
            js(view,"(function(){var s=document.getElementById('roster-search'),f=document.getElementById('roster-filter');s.value='';f.value='all';f.onchange();return true;}())")
            assert js(view,'document.querySelectorAll("#roster [data-bot-id=\\\"2\\\"] [data-action=confirm-removebot]").length')=='0','Owned alt exposed removal'
            before=len(posts);kept_active=[copy.deepcopy(b) for b in state['active'] if b['id']!=1];kept_members=[copy.deepcopy(m) for m in state['presets'][0]['members'] if m['id']!=1]
            click(view,'#roster [data-bot-id="1"] [data-action="confirm-removebot"]')
            assert len(posts)==before,'First click removed bot before confirmation'
            click(view,'#roster [data-bot-id="1"] [data-action="cancel-removebot"]');assert len(posts)==before
            click(view,'#tab-party');click(view,'#active .bot-summary[data-bot-id="1"]');click(view,'#bot-tab-mail');click(view,'#bot-subtab-send')
            assert js(view,"document.getElementById('draft-mail-kinah').value")=='12345','Cancel removal erased unrelated draft'
            click(view,'#tab-roster');click(view,'#roster-tab-characters')
            click(view,'#roster [data-bot-id="1"] [data-action="confirm-removebot"]');click(view,'#refresh')
            assert 'Confirm removal' in js(view,'document.querySelector("#roster [data-bot-id=\\\"1\\\"]").textContent'),'Refresh lost confirmation'
            shot(view,'companions-remove-confirmation-1280.png',width,height)
            click(view,'#roster [data-bot-id="1"] [data-action="removebot"]')
            wait(view,'document.querySelectorAll("#roster [data-bot-id=\\\"1\\\"]").length','0')
            assert len(posts)==before+1 and posts[-1]['action']=='removebot'
            assert 1 not in state['savedBots'] and state['active']==kept_active
            assert state['presets'][0]['members']==kept_members and any(m['id']==2 and not m['temporary'] for m in kept_members),'Mixed preset lost owned alt or another companion'
            assert not errors, errors

        assert len(posts)==24, posts
        view=views[0]
        for extra in views[1:]:destroy(extra)
        views[:]=[view]
        # Actual native-shaped DTOs; no players, accounts, World, SQL or actor creation.
        state.clear();state.update(fixture())
        def member(oid,name,self=False,controlled=False,leader=False):
            return dict(id=oid,name=name,level=65,playerClass='CLERIC',online=True,dead=False,controlled=controlled,self=self,leader=leader,viceCaptain=False,health=100,mana=100)
        state['team']=dict(id=9000,kind='Party',leaderId=5000,canLead=True,canManage=True,canConvert=True,capacity=6,memberCount=2,groups=[dict(id=0,number=1,capacity=6,members=[member(5000,'FixturePlayer',True,leader=True),member(1,'Astra',controlled=True)])],ready=dict(active=False,waiting=0,states=[]))
        click(view,'#refresh');click(view,'#tab-party');click(view,'#active .bot-summary[data-bot-id="1"]')
        assert js(view,"document.querySelectorAll('#active .team-group').length")=='1'
        assert js(view,"document.querySelectorAll('#active .human-member button').length")=='0'
        click_named(view,'#team-controls','Convert party to alliance')
        assert posts[-1]['operation']=='convert' and posts[-1]['team']=='9000'
        assert js(view,"document.querySelectorAll('#active .team-group').length")=='4'
        shot(view,'companions-alliance-groups-1280.png',1280,900)
        mobile=create(390,844,False);views.append(mobile)
        string=make(url,len(url));load(mobile,string,empty,empty,empty);free(string)
        wait(mobile,"document.querySelectorAll('#active .team-group').length",'4')
        assert js(mobile,'document.documentElement.scrollWidth <= window.innerWidth')=='true','Alliance mobile overflow'
        shot(mobile,'companions-alliance-groups-390.png',390,844)
        click_named(mobile,'#team-controls','Team management')
        shot(mobile,'companions-alliance-management-390.png',390,844)
        destroy(mobile);views.remove(mobile);click(view,'#refresh')
        click_named(view,'#team-controls','Team management')
        js(view,"(function(){var n=document.getElementById('team-invite-name');n.focus();n.value='InvitedHuman';var e=document.createEvent('Event');e.initEvent('input',true,true);n.dispatchEvent(e);return true;}())")
        click(view,'#refresh')
        assert js(view,"document.getElementById('team-invite-name').value")=='InvitedHuman','Typed native invitation draft lost on refresh'
        shot(view,'companions-alliance-management-1280.png',1280,900)
        click_named(view,'#team-controls','Send native invitation');assert posts[-1]['operation']=='invite'
        click_named(view,'#team-controls','Start ready check');assert posts[-1]['operation']=='ready'
        assert 'Astra: Ready' in js(view,"document.getElementById('team-controls').textContent")
        click_named(view,'#team-controls','Cancel ready check');assert posts[-1]['operation']=='cancelready'
        js(view,"(function(){var m=document.getElementById('team-member');m.value='1';m.onchange();var g=document.getElementById('team-group');g.value='1001';g.onchange();return true;}())")
        click_named(view,'#team-controls','Move to group');assert posts[-1]['operation']=='move' and posts[-1]['member']=='1'
        js(view,"(function(){document.getElementById('team-second').value='5000';return true;}())")
        click_named(view,'#team-controls','Swap group positions');assert posts[-1]['operation']=='swap' and posts[-1]['second']=='5000'
        click_named(view,'#team-controls','Make vice-captain');assert posts[-1]['enabled']=='true'
        state['team']['groups'][2]['members']=[member(6000,'ForeignMember')];state['team']['memberCount']=3
        click(view,'#refresh')
        js(view,"(function(){var m=document.getElementById('team-member');m.value='6000';m.onchange();return true;}())")
        state['team']['canLead']=False;click(view,'#refresh')
        assert js(view,"(function(){var b=document.querySelectorAll('#team-controls button');for(var i=0;i<b.length;i++)if(b[i].textContent.indexOf('Remove member')===0)return b[i].disabled;return false;}())")=='true','Vice-captain may kick'
        assert js(view,"document.querySelector('#team-controls [data-operation=ready]').disabled")=='false','Vice-captain lost ready-check authority'
        state['team']['canLead']=True;click(view,'#refresh')
        before=len(posts);click_named(view,'#team-controls','Remove member')
        assert len(posts)==before and js(view,"document.querySelectorAll('.team-confirmation').length")=='1'
        click_named(view,'.team-confirmation','Cancel');assert len(posts)==before
        click_named(view,'#team-controls','Remove member');click_named(view,'.team-confirmation','Confirm')
        assert posts[-1]['operation']=='kick' and posts[-1]['confirm']=='yes'
        js(view,"(function(){var m=document.getElementById('team-member');m.value='1';m.onchange();return true;}())")
        click_named(view,'#team-controls','Make leader');assert posts[-1]['operation']=='leader'
        assert js(view,"(function(){var a=document.querySelectorAll('#team-controls [data-action=team]');for(var i=0;i<a.length;i++)if(a[i].textContent==='Convert party to alliance'&&!a[i].disabled)return false;return true;}())")=='true'
        before=len(posts);click_named(view,'#team-controls','Leave alliance')
        assert len(posts)==before;click_named(view,'.team-confirmation','Confirm');assert posts[-1].get('operation')=='leave' and posts[-1].get('confirm')=='yes',(posts[-3:],errors,js(view,"document.getElementById('notice').textContent"),js(view,"document.querySelector('.team-confirmation')&&document.querySelector('.team-confirmation').textContent"))
        assert len(posts)==34,posts
        bot=state['active'][0];bot.update(warehouseAvailable=True,warehouseLimit=24,warehouse=[dict(id=20,name='Stored native shield',count=1,itemId=115000075,tooltip='item=115000075&count=1&enchant_count=6&authorize_count=0',slots=['SUB_HAND'],equipped=False,bankable=True,legionStorable=True)],legionStorageAvailable=True,legionStorageLimit=24,legionStorageName='Fixture legion',legionStorage=[],mailAvailable=True,mailLoaded=True,mailCount=2,mailSendEnabled=True,mail=[dict(id=35,title='Native supply letter',sender='FixtureSender',message='<b>Literal mail text</b>',unread=True,hasAttachments=True,item='Potion',kinah=100),dict(id=36,title='Empty native letter',sender='FixtureSender',message='No attachments',unread=False,hasAttachments=False)])
        bot['inventory'][0]['mailable']=True;bot['inventory'][1].update(bankable=True,legionStorable=True)
        click(view,'#refresh');click(view,'#active .bot-summary[data-bot-id="1"]');click(view,'#bot-tab-equipment');click(view,'#bot-subtab-warehouse')
        hover(view,'#bot-section-warehouse [data-grid=warehouse] .cube-slot[data-item-id=\"20\"]','item=115000075&count=1&enchant_count=6&authorize_count=0')
        assert 'Stored native shield' in js(view,"document.getElementById('bot-section-warehouse').textContent")
        click_named(view,'#bot-section-warehouse','Deposit whole stack');assert posts[-1]['action']=='bankdeposit' and posts[-1]['item']=='11'
        js(view,"(function(){document.querySelector('#bot-section-warehouse .cube-panel').scrollIntoView(true);return true;}())")
        shot(view,'companions-warehouse-1280.png',1280,900,False)
        click(view,'#bot-section-warehouse [data-grid=warehouse] [data-item-id="11"]');click(view,'#bot-section-warehouse [data-action=bankwithdraw][data-item="11"]');assert posts[-1]['action']=='bankwithdraw' and posts[-1]['item']=='11'
        click(view,'#bot-subtab-legion');click_named(view,'#bot-section-legion','Deposit whole stack to legion');assert posts[-1]['action']=='legiondeposit' and posts[-1]['item']=='11'
        hover(view,'#bot-section-legion [data-grid=legion] .cube-slot[data-item-id="11"]','item=162000001&count=12&enchant_count=0&authorize_count=0')
        js(view,"(function(){document.querySelector('#bot-section-legion .cube-panel').scrollIntoView(true);return true;}())")
        shot(view,'companions-legion-storage-1280.png',1280,900,False)
        click(view,'#bot-tab-mail');click(view,'#bot-subtab-inbox')
        assert js(view,"document.querySelectorAll('#bot-section-inbox b').length")=='0','Mail text interpreted as HTML'
        click_named(view,'#bot-section-inbox','Refresh mailbox');assert posts[-1]['action']=='maillist'
        click_named(view,'#bot-section-inbox .item','Read');assert posts[-1]['action']=='mailread' and posts[-1]['object']=='35'
        click_named(view,'#bot-section-inbox .item','Collect attachments');assert posts[-1]['action']=='mailtake'
        shot(view,'companions-mail-inbox-1280.png',1280,900)
        click(view,'#bot-section-inbox [data-action=maildelete][data-object="36"]');assert posts[-1]['action']=='maildelete' and posts[-1]['object']=='36'
        click(view,'#bot-subtab-send');before=len(posts)
        js(view,"(function(){document.getElementById('draft-mail-kinah').value='9223372036854775808';return true;}())")
        click_named(view,'#bot-section-send','Mail Kinah to me');assert len(posts)==before,'Overflow amount submitted'
        js(view,"(function(){document.getElementById('draft-mail-kinah').value='1000';return true;}())")
        click_named(view,'#bot-section-send','Mail Kinah to me');assert posts[-1]['action']=='mailsendkinah'
        click_named(view,'#bot-section-send','Mail whole stack to me');assert posts[-1]['action']=='mailsenditem' and posts[-1]['object']=='10'
        assert len(posts)==43,posts
        # Failed state replies invalidate tickets without erasing input drafts or replaying a mutation.
        failed_response[0]='denied';click(view,'#refresh');before=len(posts)
        click_named(view,'#bot-section-send','Mail Kinah to me');assert len(posts)==before,'Used failed-session ticket'
        assert js(view,"document.getElementById('draft-mail-kinah').value")=='1000'
        failed_response[0]=None;click(view,'#refresh');failed_response[0]='malformed';click(view,'#refresh')
        click_named(view,'#bot-section-send','Mail Kinah to me');assert len(posts)==before,'Used malformed-state ticket'
        failed_response[0]=None;click(view,'#refresh')
        failed_response[0]='null';click(view,'#refresh')
        click_named(view,'#bot-section-send','Mail Kinah to me');assert len(posts)==before,'Used null-state ticket'
        assert js(view,"document.getElementById('draft-mail-kinah').value")=='1000','Null reply erased draft'
        failed_response[0]=None;click(view,'#refresh')
        bot.update(world=True,temporary=False,generated=False)
        click(view,'#refresh');click(view,'#bot-tab-mail')
        assert js(view,"document.querySelectorAll('#bot-content [data-action=maillist],#bot-content [data-action=mailsendkinah]').length")=='0'
        click(view,'#bot-tab-equipment')
        assert js(view,"document.querySelectorAll('#bot-content [data-action=bankdeposit],#bot-content [data-action=bankwithdraw],#bot-content [data-action=legiondeposit]').length")=='0'
        assert js(view,"document.querySelectorAll('#bot-detail [data-action=savebot]').length")=='0'
        assert not errors,errors

        assert js(view,"document.querySelectorAll('[data-action=order],[data-action=attack],[data-action=summon]').length")=='0','Duplicate in-game command buttons'

        assert inputs=={str(path):digest(path) for path in bound},'Browser inputs changed during verification'
        (out/'report.json').write_text(json.dumps(dict(version=1,passed=True,authenticatedActions=len(posts),inputs=inputs,screenshots={path.name:digest(path) for path in sorted(out.glob('*.png'))},boundary='isolated native WebKit with synthetic HTTP responses; no game account/World/SQL/lifecycle'),indent=2),encoding='utf-8')
        print('OK: native Aion WebKit desktop/mobile, ranged spacing values/drafts/validation/save, roster removal confirmation/cancel/refresh, owned-alt preservation and 43 authenticated fixture actions plus keyboard/focus/draft and native alliance controls; existing equipment, quests, 103-entry roster and saved-party controls retained; screenshots:',out)

    finally:

        for view in views:destroy(view)

        free(empty);shutdown();server.shutdown();directory.close()



if __name__=='__main__':main()
