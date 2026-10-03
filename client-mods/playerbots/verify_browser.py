"""Exercise production companion controls in Aion's actual offscreen WebKit.
Uses an isolated HTTP fixture, never a game account/database or running game.
"""
import argparse
import copy
import ctypes as c
import http.server
import json
import os
from pathlib import Path
import threading
import time
from urllib.parse import parse_qs, urlparse
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MEDIA = ROOT / 'game-server/config/playerbots/media'

def fixture():
    bot = dict(id=1, name='Astra', level=65, playerClass='CLERIC', role='HEALER', order='FOLLOW', health=87, mana=64,
               dead=False, closing=False, status='ready', action='heal party member', mission=0, missionStatus='',
               area=False, supplies=True, gear=False, loot=False, questing=False,
               inventory=[dict(id=10, name='Companion training mace', count=1, equipped=False, slots=['MAIN_HAND'])],
               quests=[dict(id=900001, status='START', progress=2, missionEligible=True), dict(id=900002, status='START', progress=0, missionEligible=False)])
    return dict(owner='FixturePlayer', enabled=True, generatedEnabled=True, limit=5, active=[bot],
                roster=[dict(id=1, name='Astra', level=65, playerClass='CLERIC', reserved=True, generated=False, ready=True),
                        dict(id=2, name='Kael', level=64, playerClass='TEMPLAR', reserved=False, generated=False, ready=True)],
                classes=['PRIEST', 'CLERIC', 'TEMPLAR'], startingClasses=['PRIEST', 'WARRIOR'], roles=['TANK', 'HEALER', 'MELEE', 'RANGED'],
                quests=[dict(id=900001, status='START')], notice='', request='fixture-only-0')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--browser-bin', type=Path, required=True)
    args = parser.parse_args()
    state, posts, errors, sequence = fixture(), [], [], [0]
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *unused): pass
        def send(self, data, content='application/json', status=200):
            if isinstance(data, dict):
                sequence[0] += 1; state['request'] = 'fixture-only-' + str(sequence[0]); data = json.dumps(data)
            data = data if isinstance(data, bytes) else data.encode()
            self.send_response(status); self.send_header('Content-Type', content); self.send_header('Content-Length', str(len(data)))
            self.end_headers(); self.wfile.write(data)
        def do_GET(self):
            path = urlparse(self.path).path
            if path == '/market/companions': self.send((MEDIA/'bots.html').read_bytes(), 'text/html; charset=utf-8')
            elif path in ['/market/companions/media/bots.js', '/market/companions/media/bots.css']:
                self.send((MEDIA/path.rsplit('/', 1)[1]).read_bytes(), 'application/javascript' if path.endswith('.js') else 'text/css')
            elif path == '/market/companions/state': self.send(state)
            else: self.send_error(404)
        def do_POST(self):
            try:
                form = {k:v[0] for k,v in parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode()).items()}
                assert form['session_id'] == 'fixture' and form['request'] == state['request'], 'stale or missing form ticket'
                posts.append(form); bot = state['active'][0]; op = form['action']
                if op == 'order': bot['order'] = form['order']
                elif op == 'setting': bot[form['setting']] = form['enabled'] == 'true'
                elif op == 'role': bot['role'] = form['role']
                elif op == 'mission': bot['mission'] = int(form['quest']); bot['missionStatus'] = 'traveling to quest objective'
                elif op == 'equip':
                    assert form['item'] == '10' and form['slot'] == 'MAIN_HAND'; bot['inventory'][0]['equipped'] = True
                elif op == 'recruit':
                    assert form['name'] == 'Kael'; state['roster'][1]['reserved'] = True
                    other = copy.deepcopy(bot); other.update(id=2, name='Kael', playerClass='TEMPLAR', role='TANK'); state['active'].append(other)
                elif op in ['create', 'generate']:
                    assert form['name'] in ['Newpriest', 'Newtemplar']
                elif op == 'share': assert form['quest'] == '900001'
                elif op == 'attack': pass
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
    initialize(); empty=make('',0); views=[]; out=ROOT/'output/playwright/playerbots'; out.mkdir(parents=True,exist_ok=True)
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
        raise AssertionError('Expected '+expected+', got '+js(view,code)+'; '+js(view,"document.getElementById('notice').textContent"))
    def click(view,selector):
        js(view,'(function(){document.querySelector('+json.dumps(selector)+').scrollIntoView(false);return true;}())');pump(.05)
        point=json.loads(js(view,'(function(){var r=document.querySelector('+json.dumps(selector)+').getBoundingClientRect();return JSON.stringify([Math.round((r.left+r.right)/2),Math.round((r.top+r.bottom)/2)]);}())'))
        move(view,*point);down(view,0);up(view,0);pump(.2)
    def shot(view,name,w,h):
        js(view,'(function(){window.scrollTo(0,0);return true;}())');pump(.2);buffer=render(view);assert buffer
        Image.frombytes('RGBA',(w,h),c.string_at(pixels(buffer),rowspan(buffer)*h),'raw','BGRA',rowspan(buffer)).save(out/name)
    try:
        for width,height in [(1280,900),(390,844)]:
            view=create(width,height,False);views.append(view); url='http://127.0.0.1:'+str(server.server_port)+'/market/companions?session_id=fixture'
            string=make(url,len(url));load(view,string,empty,empty,empty);free(string)
            wait(view,"document.querySelectorAll('#active .bot').length",str(len(state['active'])))
            shot(view,'companions-'+str(width)+'.png',width,height)
            assert js(view,'document.documentElement.scrollWidth <= window.innerWidth')=='true', 'horizontal overflow: '+js(view,"JSON.stringify([window.innerWidth,document.documentElement.scrollWidth,document.querySelector('.party').getBoundingClientRect().width,document.querySelector('aside').getBoundingClientRect().width])")
            if width==390:continue
            click(view,'#party-orders button:nth-child(2)');wait(view,"document.querySelector('.badge').textContent.indexOf('Stay')>=0",'true')
            click(view,'.bot .settings button:first-child');wait(view,"document.querySelector('.bot .settings button').textContent",'Area attacks: On')
            js(view,"(function(){var n=document.querySelector('.role-row select');n.value='TANK';n.onchange();return true;}())");wait(view,"document.querySelector('.role-row select').value",'TANK')
            click(view,'.bot .disclosure:nth-of-type(2) .disclosure-toggle')
            assert js(view,"document.querySelectorAll('.bot .quest button').length")=='1', 'unsupported mission was offered'
            click(view,'.bot .quest button');wait(view,"document.querySelectorAll('.mission button').length",'1')
            click(view,'.mission button');wait(view,"document.querySelectorAll('.mission button').length",'0')
            click(view,'.bot .disclosure:first-of-type .disclosure-toggle');click(view,'.bot .item button')
            wait(view,"document.querySelector('.bot .item').textContent.indexOf('Equipped')>=0",'true')
            click(view,'#share');click(view,'#roster .roster-row:nth-child(2) button');wait(view,"document.querySelectorAll('#active .bot').length",'2')
            for model,name,pc in [('create','Newpriest','PRIEST'),('generate','Newtemplar','TEMPLAR')]:
                js(view,"(function(){var m=document.getElementById('model');m.value="+json.dumps(model)+";m.onchange();document.getElementById('new-name').value="+json.dumps(name)+";document.getElementById('new-class').value="+json.dumps(pc)+";return true;}())")
                before=len(posts);click(view,'#create button');wait(view,"document.getElementById('notice').textContent",'Companion request completed.');assert len(posts)==before+1
                assert posts[-1]['action']==model and posts[-1]['playerClass']==pc
            assert not errors, errors
        assert len(posts)==10, posts
        print('OK: native Aion WebKit desktop/mobile layout and 10 authenticated fixture actions; screenshots:',out)
    finally:
        for view in views:destroy(view)
        free(empty);shutdown();server.shutdown();directory.close()

if __name__=='__main__':main()
