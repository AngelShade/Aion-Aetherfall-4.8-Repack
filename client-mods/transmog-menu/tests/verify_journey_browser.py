"""Exercise journey choices in the installed client's WebKit using an isolated HTTP fixture."""
import argparse
import ctypes as c
import http.server
import json
import os
from pathlib import Path
import threading
import time
from PIL import Image
from urllib.parse import parse_qs, urlparse


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--browser-bin', type=Path, required=True)
    args = parser.parse_args()
    media = Path(__file__).resolve().parents[3] / 'game-server/config/journey/media'
    state = dict(eligible=True, prompt=True, name='New Daeva', quests=41, request='fixture-only',
                 welcome=False, ceremonyRewardsMailed=False, decision='', classes=[dict(id='GLADIATOR', name='Gladiator'), dict(id='TEMPLAR', name='Templar')],
                 continuationOpen=False, continuationUnlocked=False, tickets=True)
    def story(*done):
        return dict(chapters=[dict(id=c, title='Chapter '+c, task='Task for '+c, goal=2 if c == 'fight' else 1, value=1 if c == 'fight' else int(c in done), done=c in done)
                              for c in ('earn', 'recruit', 'manage', 'help', 'fight', 'promote')],
                    rewards=[dict(id='midpoint', title='Settler of Aion', titleId=101, ready=set(done) >= {'earn', 'recruit', 'manage'}, claimed=False),
                             dict(id='final', title='Destiny Ascendant', titleId=102, ready=len(done) == 6, claimed=False)])
    posts = []
    state_reads = []
    failures = dict(login=0, stall=0)
    bootstrap_session = '30313233343536373839616263646566'

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass

        def send(self, data, content='application/json', status=200):
            self.send_response(status)
            self.send_header('Content-Type', content)
            self.end_headers()
            try:
                self.wfile.write(data if isinstance(data, bytes) else data.encode())
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                pass  # A stalled state request is intentionally cancelled by the browser.

        def do_GET(self):
            path = urlparse(self.path).path
            if path == '/journey':
                html = (media / 'journey.html').read_text(encoding='utf-8')
                html = html.replace('<head>', '<head><script>window.visibility=[];window.companionOpens=0;window.AionObject={JourneyVisibility:function(v){visibility.push(v);}'+(',JourneyCompanions:function(){companionOpens++;}' if parse_qs(urlparse(self.path).query).get('native') == ['1'] else '')+'};</script>')
                if parse_qs(urlparse(self.path).query).get('bootstrap') == ['1']:
                    # Register after the AionObject stub and before Journey loads.
                    html = html.replace('</head>', '<script>window.sessionRequests=0;window.AionObject.JourneySession=function(){if(++sessionRequests>=3)setTimeout(function(){JourneySessionReady("'+bootstrap_session+'");},20);};</script></head>')
                self.send(html, 'text/html; charset=utf-8')
            elif path.startswith('/journey/media/'):
                file = media / path.rsplit('/', 1)[1]
                self.send(file.read_bytes(), {'.css':'text/css', '.js':'application/javascript', '.jpg':'image/jpeg', '.png':'image/png'}[file.suffix])
            elif path.startswith('/market/companions/media/cards/'):
                self.send((media.parent.parent / 'playerbots/media/cards' / path.rsplit('/', 1)[1]).read_bytes(), 'image/png')
            elif path == '/journey/state':
                state_reads.append(time.monotonic())
                if parse_qs(urlparse(self.path).query).get('session_id') not in (['fixture'], [bootstrap_session]):
                    self.send(json.dumps(dict(error='Log in to choose your journey.')), status=403)
                    return
                if failures['login']:
                    failures['login'] -= 1
                    self.send(json.dumps(dict(error='Log in to choose your journey.')), status=403)
                    return
                if failures['stall']:
                    failures['stall'] -= 1
                    time.sleep(4)
                self.send(json.dumps(state))
            else:
                self.send_error(404)

        def do_POST(self):
            form = parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode())
            assert form['request'] == ['fixture-only'] and form['session_id'] in (['fixture'], [bootstrap_session])
            posts.append(form)
            if form['choice']==['skip']: state.update(eligible=False,prompt=False,decision='SKIP',welcome=True,continuationOpen=True)
            elif form['choice']==['ack']: state.update(welcome=False)
            elif form['choice']==['proceed']: state.update(welcome=False,continuationUnlocked=True,chapters=story('earn'))
            elif form['choice']==['claim']:
                reward=[r for r in state['chapters']['rewards'] if r['id']==form['class'][0]][0]
                reward['claimed']=True
                self.send(json.dumps(dict(state,done=True,notice='Title earned: '+reward['title']+'.')))
                return
            self.send(json.dumps(dict(state, done=True, notice=str(state['quests'])+' quests completed; skipped rewards mailed; complete A Ceremony in '+state['journey']['capital']+' to earn its rewards.')))

    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    directory = os.add_dll_directory(str(args.browser_bin.resolve()))
    lib = c.CDLL(str(args.browser_bin.resolve() / 'Awesomium.dll'))
    ptr = c.c_void_p

    def api(name, result, *parameters):
        f = getattr(lib, name)
        f.restype, f.argtypes = result, list(parameters)
        return f

    initialize = api('awe_webcore_initialize_default', None)
    update = api('awe_webcore_update', None)
    shutdown = api('awe_webcore_shutdown', None)
    create = api('awe_webcore_create_webview', ptr, c.c_int, c.c_int, c.c_bool)
    destroy = api('awe_webview_destroy', None, ptr)
    make = api('awe_string_create_from_wide', ptr, c.c_wchar_p, c.c_size_t)
    free = api('awe_string_destroy', None, ptr)
    load = api('awe_webview_load_url', None, ptr, ptr, ptr, ptr, ptr)
    evaluate = api('awe_webview_execute_javascript_with_result', ptr, ptr, ptr, ptr, c.c_int)
    to_string = api('awe_jsvalue_to_string', ptr, ptr)
    utf8 = api('awe_string_to_utf8', c.c_size_t, ptr, ptr, c.c_size_t)
    jsfree = api('awe_jsvalue_destroy', None, ptr)
    render = api('awe_webview_render', ptr, ptr)
    pixels = api('awe_renderbuffer_get_buffer', ptr, ptr)
    rowspan = api('awe_renderbuffer_get_rowspan', c.c_int, ptr)
    buffer_width = api('awe_renderbuffer_get_width', c.c_int, ptr)
    buffer_height = api('awe_renderbuffer_get_height', c.c_int, ptr)
    mouse_move=api('awe_webview_inject_mouse_move',None,ptr,c.c_int,c.c_int)
    mouse_down=api('awe_webview_inject_mouse_down',None,ptr,c.c_int)
    mouse_up=api('awe_webview_inject_mouse_up',None,ptr,c.c_int)
    initialize()
    empty = make('', 0)
    views = []

    def js(view, code):
        script = make(code, len(code))
        value = evaluate(view, script, empty, 1000)
        free(script)
        if not value:
            return ''
        string = to_string(value)
        buffer = c.create_string_buffer(16384)
        utf8(string, buffer, len(buffer))
        free(string)
        jsfree(value)
        return buffer.value.decode()

    def pump(seconds=.2):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            update()
            time.sleep(.02)

    def click(view, selector):
        point=json.loads(js(view,"(function(){var r=document.querySelector("+json.dumps(selector)+").getBoundingClientRect();return JSON.stringify([Math.round((r.left+r.right)/2),Math.round((r.top+r.bottom)/2)]);}())"))
        mouse_move(view,*point);pump(.05);mouse_down(view,0);mouse_up(view,0);pump(.1)

    try:
        for starter, capital, guide, travel_guide, onward, count, width, height in [(*path,w,h) for path in [('Poeta','Sanctum','Leah','Polyidus','Verteron',41),('Ishalgen','Pandaemonium','Heimdall','Doman','Altgard',47)] for w,h in [(1024,768),(1920,1080),(3440,1440)]]:
            state.update(eligible=True,prompt=True,decision='',welcome=False,continuationOpen=False,continuationUnlocked=False,quests=count,journey=dict(starter=starter,capital=capital,guide=guide,travelGuide=travel_guide,onward=onward,starterArt=starter.lower(),capitalArt=capital.lower(),description='Discover the story of your Ascension.'))
            view = create(width, height, False)
            views.append(view)
            url = f'http://127.0.0.1:{server.server_port}/journey?session_id=fixture'
            s = make(url, len(url)); load(view, s, empty, empty, empty); free(s)
            until = time.monotonic() + 12
            while time.monotonic() < until:
                pump(.1)
                if js(view, 'window.visibility&&visibility.join()') == '1':
                    break
            else:
                raise AssertionError('Journey did not automatically open for eligible character')
            assert js(view, "document.getElementById('play').textContent") == 'Play '+starter
            assert js(view, "document.getElementById('capital-title').textContent") == 'Ascend to '+capital
            assert starter.lower()+'.jpg' in js(view, "document.getElementById('starter-art').style.backgroundImage")
            assert capital.lower()+'.jpg' in js(view, "document.getElementById('capital-art').style.backgroundImage")
            geometry = json.loads(js(view, "(function(){var names=['play','skip'],a=[];for(var i=0;i<names.length;i++){var r=document.getElementById(names[i]).getBoundingClientRect();a.push({left:r.left,top:r.top,right:r.right,bottom:r.bottom});}return JSON.stringify(a);}())"))
            assert all(r['left'] >= 0 and r['top'] >= 0 and r['right'] <= width and r['bottom'] <= height for r in geometry), geometry
            filename = str(Path(__file__).resolve().parents[3] / f'target/journey-{starter.lower()}-{width}x{height}.png')
            Path(filename).parent.mkdir(parents=True, exist_ok=True)
            buffer = render(view)
            assert buffer and buffer_width(buffer) == width and buffer_height(buffer) == height
            Image.frombytes('RGBA', (width,height), c.string_at(pixels(buffer), rowspan(buffer)*height), 'raw', 'BGRA', rowspan(buffer)).save(filename)
            before = len(posts)
            click(view,'#skip')
            assert js(view, "getComputedStyle(document.getElementById('classes'),null).display") != 'none'
            assert js(view, "getComputedStyle(document.getElementById('paths'),null).display") == 'none'
            click(view,'#class-list button')
            assert len(posts) == before, 'Selecting a class prematurely committed the skip'
            confirm = json.loads(js(view, "(function(){var r=document.getElementById('confirm').getBoundingClientRect();return JSON.stringify({top:r.top,bottom:r.bottom});}())"))
            assert confirm['top'] >= 0 and confirm['bottom'] <= height, confirm
            click(view,'#confirm')
            click(view,'#confirm')
            pump(.5)
            assert len(posts) == before + 1 and posts[-1]['class'] == ['GLADIATOR'] and posts[-1]['choice'] == ['skip'], posts
            assert js(view, "document.getElementById('complete-title').textContent") == 'Welcome to '+capital
            # Map entry reloads the hidden webview. The durable receipt opens it
            # again and remains pending until a click explicitly acknowledges it.
            s=make(url,len(url));load(view,s,empty,empty,empty);free(s);pump(.7)
            assert js(view,'visibility.join()')=='1', 'Welcome lost on map-change reload'
            assert js(view,"document.getElementById('complete-title').textContent")=='Welcome to '+capital
            assert guide in js(view,"document.getElementById('receipt').textContent")
            assert onward in js(view,"document.getElementById('receipt').textContent")
            assert 'Complete the ceremony to earn its rewards.' in js(view,"document.getElementById('receipt').textContent")
            pump(.7)
            assert state['welcome'] and js(view,'visibility.join()')=='1'
            click(view,'#close');pump(.3)
            assert posts[-1]['choice']==['ack'] and not state['welcome']
            assert js(view,'visibility.join()')=='1,0'
            state.update(eligible=True,prompt=True,decision='',welcome=False,continuationOpen=False)
            # Reopening can select the original story without taking the skip route.
            s = make(url, len(url)); load(view, s, empty, empty, empty); free(s); pump(.7)
            click(view,'#play')
            pump(.5)
            assert posts[-1]['choice'] == ['play'] and js(view, 'visibility.join()') == '1,0'
            print(f'PASS actual Aion WebKit {starter} {width}x{height}: mouse clicks hit visible buttons, class confirmation, map-reload welcome persists until acknowledgement, original-story close')
            destroy(view); views.remove(view)
        for initial in ('', '&session_id=stale'):
            state.update(eligible=True, prompt=True, decision='', welcome=False)
            view = create(1024, 768, False); views.append(view)
            bootstrap_url = f'http://127.0.0.1:{server.server_port}/journey?bootstrap=1'+initial
            started = time.monotonic()
            s = make(bootstrap_url, len(bootstrap_url)); load(view, s, empty, empty, empty); free(s)
            until = started + 6
            while time.monotonic() < until:
                pump(.1)
                if js(view, 'window.visibility&&visibility.join()') == '1':
                    break
            else:
                raise AssertionError('Journey did not recover a missing/stale native session')
            assert time.monotonic()-started < 3
            assert js(view, 'sessionRequests') == '3'
            before = len(posts);click(view, '#play');pump(.3)
            assert len(posts)==before+1 and posts[-1]['session_id']==[bootstrap_session]
            print('PASS actual Aion WebKit native session: '+('stale' if initial else 'missing')+' key recovered without the publisher queue, authenticated choice succeeded')
            destroy(view);views.remove(view)
        state.update(eligible=True, prompt=True, decision='', welcome=False)
        def shown(view, element):
            return js(view, "getComputedStyle(document.getElementById('"+element+"'),null).display") != 'none'

        def reload(view):
            s = make(url, len(url)); load(view, s, empty, empty, empty); free(s); pump(.7)

        for starter, capital, count, width, height in [(*path,w,h) for path in [('Poeta','Sanctum',41),('Ishalgen','Pandaemonium',47)] for w,h in [(1024,768),(1920,1080)]]:
            journey = dict(starter=starter,capital=capital,guide='Guide',travelGuide='Travel',onward='Onward',starterArt=starter.lower(),capitalArt=capital.lower(),description='Fixture.')
            state.update(eligible=False,prompt=False,decision='SKIP',welcome=True,continuationOpen=True,continuationUnlocked=False,quests=count,journey=journey)
            view = create(width, height, False); views.append(view)
            url = f'http://127.0.0.1:{server.server_port}/journey?session_id=fixture'
            reload(view)
            assert js(view, 'visibility.join()') == '1' and shown(view, 'complete') and shown(view, 'proceed') and shown(view, 'close'), 'Finale lost Proceed'
            reload(view)
            assert shown(view, 'proceed') and state['welcome'], 'Map reload must keep the unread finale'
            before = len(posts); click(view, '#proceed'); click(view, '#proceed'); pump(.4)
            assert len(posts) == before + 1 and posts[-1]['choice'] == ['proceed'], posts[before:]
            assert shown(view, 'continuation') and not shown(view, 'complete') and not shown(view, 'intro') and not shown(view, 'choice-hint')
            pump(.4)  # Let the banner decode before the screenshot.
            box = json.loads(js(view, "(function(){var r=document.getElementById('next-close').getBoundingClientRect();return JSON.stringify({left:r.left,top:r.top,right:r.right,bottom:r.bottom,scroll:document.documentElement.scrollWidth});}())"))
            assert box['left'] >= 0 and box['top'] >= 0 and box['right'] <= width and box['bottom'] <= height and box['scroll'] <= width, box
            assert js(view, "document.getElementsByClassName('next-emblem')[3].style.backgroundPosition").startswith('0px -48px')
            filename = str(Path(__file__).resolve().parents[3] / f'target/journey-continuation-{starter.lower()}-{width}x{height}.png')
            buffer = render(view)
            Image.frombytes('RGBA', (width,height), c.string_at(pixels(buffer), rowspan(buffer)*height), 'raw', 'BGRA', rowspan(buffer)).save(filename)
            click(view, '#next-close'); pump(.2)
            assert js(view, 'visibility.join()') == '1,0', js(view, 'visibility.join()')
            destroy(view); views.remove(view)
            # A hidden map-entry load never reopens it; the normal menu shows the page, not the finale.
            view = create(width, height, False); views.append(view)
            reload(view)
            assert js(view, 'visibility.length') == '0' and shown(view, 'continuation') and not shown(view, 'complete'), 'Finale replayed after Proceed'
            assert len(posts) == before + 1, 'Reopening wrote a second unlock'
            assert js(view, "document.getElementById('chapter-count').textContent") == '1 of 6 chapters complete'
            assert js(view, "document.getElementsByClassName('next-row').length") == '6' and 'Complete' in js(view, "document.getElementsByClassName('next-state')[0].textContent")
            assert js(view, "document.getElementsByClassName('next-state')[4].textContent") == '1 / 2'
            assert js(view, "document.querySelectorAll('#rewards button').length") == '0', 'Unfinished chapters offered a reward'
            assert shown(view, 'next-hint') and not shown(view, 'open-companions'), 'Old bridge must keep the written directions'
            state.update(chapters=story('earn', 'recruit', 'manage'))
            url = f'http://127.0.0.1:{server.server_port}/journey?native=1&session_id=fixture'
            reload(view)
            assert shown(view, 'open-companions') and not shown(view, 'next-hint'), 'New bridge must show the button'
            click(view, '#open-companions'); pump(.1)
            assert js(view, 'companionOpens') == '1' and len(posts) == before + 1, 'Opening Companions must not write progress'
            before = len(posts); click(view, '#rewards button'); pump(.4)
            assert len(posts) == before + 1 and posts[-1]['choice'] == ['claim'] and posts[-1]['class'] == ['midpoint'], posts[before:]
            assert 'Settler of Aion' in js(view, "document.getElementById('notice').textContent") and js(view, "document.querySelectorAll('#rewards button').length") == '0'
            assert 'Claimed' in js(view, "document.getElementById('rewards').textContent")
            state.update(chapters=story('earn', 'recruit', 'manage', 'help', 'fight', 'promote'))
            state['chapters']['rewards'][0]['claimed'] = True
            reload(view)
            assert js(view, "document.getElementById('chapter-count').textContent") == '6 of 6 chapters complete' and 'chapter is written' in js(view, "document.getElementById('next-lead').textContent")
            box = json.loads(js(view, "(function(){var r=document.getElementById('next-close').getBoundingClientRect();return JSON.stringify({bottom:r.bottom,scroll:document.documentElement.scrollWidth});}())"))
            assert box['bottom'] <= height and box['scroll'] <= width, box
            pump(.4); filename = str(Path(__file__).resolve().parents[3] / f'target/journey-chapters-{starter.lower()}-{width}x{height}.png')
            buffer = render(view)
            Image.frombytes('RGBA', (width,height), c.string_at(pixels(buffer), rowspan(buffer)*height), 'raw', 'BGRA', rowspan(buffer)).save(filename)
            destroy(view); views.remove(view)
            print(f'PASS actual Aion WebKit {starter} {width}x{height}: Proceed unlocks once, chapters show state, rewards claim once, Companions button opens, finale never replays')
        view = create(1024, 768, False); views.append(view)
        state.update(welcome=False, continuationUnlocked=False)
        reload(view)
        assert js(view, 'visibility.length') == '0' and shown(view, 'proceed'), 'Enter the world must not lose Proceed'
        for decision in ('PLAY', ''):
            state.update(decision=decision)
            reload(view)
            assert js(view, 'visibility.length') == '0' and shown(view, 'proceed') and 'Ascension is complete' in js(view, "document.getElementById('receipt').textContent"), decision
            state.update(continuationOpen=False)
            reload(view)
            assert not shown(view, 'proceed') and 'before Ascension' in js(view, "document.getElementById('receipt').textContent"), decision
            state.update(continuationOpen=True)
        state.update(eligible=True, prompt=True, decision='', welcome=False, continuationOpen=False, continuationUnlocked=False, tickets=True)
        destroy(view); views.remove(view)
        print('PASS actual Aion WebKit: closed finale keeps Proceed, older and played characters reach it, unfinished ones do not')
        for failure in ('login', 'stall'):
            failures[failure] = 2 if failure == 'login' else 1
            before = len(state_reads)
            view = create(1024, 768, False); views.append(view)
            s = make(url, len(url)); load(view, s, empty, empty, empty); free(s)
            until = time.monotonic() + 9
            while time.monotonic() < until:
                pump(.1)
                if js(view, 'window.visibility&&visibility.join()') == '1':
                    break
            else:
                raise AssertionError('Journey did not recover from '+failure)
            elapsed = time.monotonic() - state_reads[before]
            assert elapsed < (2 if failure == 'login' else 3.8), elapsed
            pump(1.5)
            assert len(state_reads)-before == (3 if failure == 'login' else 2), 'Overlapping retry requests'
            assert js(view, 'visibility.join()') == '1', 'Late response opened the prompt twice'
            print(f'PASS actual Aion WebKit {failure}: prompt opened in {elapsed:.2f}s, late callbacks ignored')
            destroy(view); views.remove(view)
        failures['stall'] = 20
        before = len(state_reads)
        before_posts = len(posts)
        view = create(1024, 768, False); views.append(view)
        s = make(url, len(url)); load(view, s, empty, empty, empty); free(s)
        until = time.monotonic() + 16
        while time.monotonic() < until:
            pump(.1)
            if 'Close it and reopen' in js(view, "document.getElementById('notice').textContent"):
                break
        else:
            raise AssertionError('Stalled login did not reach a bounded error')
        elapsed = time.monotonic() - state_reads[before]
        assert elapsed < 13.5 and len(state_reads)-before <= 5, (elapsed, len(state_reads)-before)
        reads = len(state_reads); pump(1)
        assert len(state_reads) == reads and len(posts) == before_posts
        assert js(view, 'visibility.join()') == '', 'Failure prompted an unauthenticated character'
        print(f'PASS actual Aion WebKit persistent stall: stopped in {elapsed:.2f}s, no overlapping requests or actions')
        destroy(view); views.remove(view)
        failures['stall'] = 0
        state.update(eligible=False, prompt=False, decision='SKIP',welcome=False)
        view = create(1024, 768, False); views.append(view)
        s = make(url, len(url)); load(view, s, empty, empty, empty); free(s); pump(1)
        assert js(view, 'visibility.length') == '0', 'Already processed character unexpectedly opened the menu'
        assert 'already been applied' in js(view, "document.getElementById('receipt').textContent")
        state.update(ceremonyRewardsMailed=True)
        s = make(url, len(url)); load(view, s, empty, empty, empty); free(s); pump(.7)
        assert 'already included in your earlier mail bundle' in js(view, "document.getElementById('receipt').textContent")
        print('PASS: completed/ineligible characters do not auto-open')
    finally:
        for view in views:
            destroy(view)
        free(empty); shutdown(); directory.close(); server.shutdown()


if __name__ == '__main__':
    main()
