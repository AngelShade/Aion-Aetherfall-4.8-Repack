"""Deterministic, stateful native boundary with the client's real Lua 5.1 widgets."""
from collections import Counter
import base64
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent.parent
FIXTURES = HERE / 'fixtures'
sys.path.insert(0, str(SOURCE.parent / 'expanded-warehouse'))
from codec import read_pak
from lupa.lua51 import LuaRuntime


def frozen(name):
    raw=gzip.decompress((FIXTURES / (name + '.gz')).read_bytes())
    expected=json.loads((FIXTURES/'source-sha256.json').read_text())[name]
    assert hashlib.sha256(raw).hexdigest()==expected, 'Frozen source hash changed: '+name
    return raw


class Recorder:
    def __init__(self, path):
        self.path = path
        self.stream = gzip.GzipFile(filename='', mode='wb', fileobj=path.open('wb'), mtime=0, compresslevel=6)
        self.sha = hashlib.sha256()
        self.counts = Counter()
        self.n = 0
        self.scenario = 'cold-load'

    def write(self, kind, name, args=(), result=()):
        row = [self.scenario, kind, name, list(args), list(result)]
        raw = (json.dumps(row, ensure_ascii=False, separators=(',', ':'), allow_nan=False) + '\n').encode('utf-8')
        self.stream.write(raw)
        self.sha.update(raw)
        self.counts[kind + ':' + name] += 1
        self.n += 1

    def close(self):
        self.stream.close()
        return {'records': self.n, 'sha256': self.sha.hexdigest(), 'calls': dict(sorted(self.counts.items()))}


class Runtime:
    def __init__(self, client, recorder, reference=False, scale=1, framework_only=False):
        self.r = recorder
        self.lua = LuaRuntime(unpack_returned_tuples=True)
        self.g = self.lua.globals()
        self.scale, self.sw, self.sh = scale, 1280 * scale, 960 * scale
        self.focused, self.mouse = True, [50, 400, False, False]
        self.character, self.zone = None, 'Sanctum'
        self.camera_available = True
        self.camera, self.camera_original = {'fov': 90, 'distance': 28}, {'fov': 90, 'distance': 28}
        self.units={'target':(7002,'TraceBot',65,31000,42000,5000,8000), 'targettarget':(7003,'TraceBot',65,21000,42000,3000,8000)}
        self.identities={'TraceBot':('Excellent','Great')}
        self.auras={unit:[(1001,'TraceAura',1,0,8.4,None,8400,7001,2), (1002,'UntimedAura',1,0,None,None,None,7001,0)] for unit in ('player','target','targettarget')}
        self.slots={}
        self.files = {'state.json': '{"Desired":"layout","Applied":"layout"}', 'manifest.json': '{}'}
        self.dialogs, self.widgets, self.pins, self.widget_pins, self.backdrops, self.labels = {}, {}, {}, {}, {}, {}
        self.framework_hashes = {}
        archive = read_pak(client / 'bin32/bin32.pak')
        sources = {}
        raw_hashes={}
        for name in archive.namelist():
            if name.endswith('.lua'):
                raw = archive.read(name)
                source = raw.decode('utf-8-sig', errors='replace')
                sources[Path(name).stem] = source
                raw_hashes[Path(name).stem]=hashlib.sha256(raw).hexdigest()
                for api in re.findall(r'\b(\w+)\(g_AddonName\b', source):
                    self.g[api] = lambda *args, api=api: self.widget(api, *args)
        self.g.AddNewWidget = lambda *args: self.widget('AddNewWidget', *args)
        self.g.GetUIScale = lambda: self.stock('GetUIScale', (), self.scale)
        self.g.GetAionStr = lambda text: text
        self.g.DisplayChatMessage = lambda *args: self.r.write('chat', 'DisplayChatMessage', args)
        self.g.AetherfallCore = self.bridge
        self.g.UnitName = lambda *args: self.stock('UnitName', args, self.character)
        self.g.GetServerName = lambda: self.stock('GetServerName', (), 'TraceServer')
        self.g.GetZoneText = lambda: self.stock('GetZoneText', (), self.zone)
        self.g.GetPlayerMapPosition = lambda *args: self.stock('GetPlayerMapPosition', args, (1520.4, 1488.2, 120))
        self.g.GetTime = lambda: self.stock('GetTime', (), '2026-11-10 21:47\n')
        for name, value in {'UnitLevel':65, 'UnitHP':31000, 'UnitHPMax':42000, 'GetNumPartyMembers':3, 'GetNumLegionMembers':12}.items():
            self.g[name] = lambda *args, name=name, value=value: self.stock(name, args, value)
        self.g.RegisterMenu = lambda *args: self.r.write('stock', 'RegisterMenu', args)
        self.g.WardrobeNative_ResumeTimer = lambda: self.r.write('stock', 'WardrobeNative_ResumeTimer')
        self.g.PlayerBotBar_ApplyVisibility = lambda: self.r.write('stock', 'PlayerBotBar_ApplyVisibility')
        wrap = self.lua.eval('function(fn) return function(...) return fn(...) end end')
        for name in ('GetUIScale','UnitName','GetServerName','GetZoneText','GetPlayerMapPosition','GetTime','UnitLevel','UnitHP','UnitHPMax','GetNumPartyMembers','GetNumLegionMembers','RegisterMenu','WardrobeNative_ResumeTimer','PlayerBotBar_ApplyVisibility'):
            self.g[name] = wrap(self.g[name])
        for name in ('Widget', 'Container', 'Button', 'Static', 'StaticImage', 'EditBox', 'MessageWidget', 'SlashCmd'):
            self.lua.execute(sources[name], name='@framework/' + name + '.lua')
            self.g.package.loaded[name] = True
            self.framework_hashes[name] = raw_hashes[name]
        self.g.package.loaded.sha1 = True
        self.g.sha1 = self.lua.table(sha1=lambda value: hashlib.sha1(value.encode('utf-8')).hexdigest())
        self.g.TRACE_READ = lambda path: self.files.get(str(path).split('/')[-1])
        # No OS reads/writes and no real player files; synthetic files only.
        self.lua.execute('''io={open=function(path, mode)
            local data=TRACE_READ(path); if not data then return nil end
            return {read=function() return data end, close=function() end}
        end}''')
        self.g.g_AddonName = 'RelicCalc'
        self.g.lxp = self.lua.table(lom=self.lua.table(parse=lambda xml: self.table(ET.fromstring(xml))))
        self.lua.execute("function LoadXMLWidget(doc) local w=CreateWidget('Dialog',doc.attr.name); w:LoadXML(doc) end")
        self.lua.execute('''TRACE_COVERAGE={}
        debug.sethook(function(_,line)
            local info=debug.getinfo(2,'S'); local source=info.source
            if source:sub(1,8)=='@source/' then
                local t=TRACE_COVERAGE[source] or {}; TRACE_COVERAGE[source]=t; t[line]=(t[line] or 0)+1
            end
        end,'l')''')
        self.load('AetherfallCore.lua', frozen('AetherfallCore.lua'))
        self.load('AetherfallRegistry.lua', frozen('AetherfallRegistry.lua'))
        self.seed_dialogs()
        self.seed_custom()
        for node in ET.fromstring(frozen('AetherfallHUD.xml')):
            self.g.LoadXMLWidget(self.table(node))
        self.load('AetherfallLayout.lua', frozen('AetherfallLayout.lua'))
        self.load('AetherfallLazy.lua', frozen('AetherfallLazy.lua'))
        # Every public AFCore call is recorded in addition to the raw bridge.
        # Table results stay inside Lua; raw bridge result tuples are recorded.
        self.g.TRACE_AF = lambda name, *args: self.r.write('AFCore', name, args)
        self.lua.execute('''for name,fn in pairs(AFCore) do if type(fn)=='function' then
            AFCore[name]=function(...) TRACE_AF(name,...); return fn(...) end
        end end''')
        if framework_only:
            return
        if reference:
            names = ['AetherfallHUD.lua', 'AetherfallOptions.lua']
            for name in names:
                self.load(name, frozen(name))
        else:
            order = SOURCE / 'load_order.json'
            if order.exists():
                names = json.loads(order.read_text(encoding='utf-8-sig'))
                if isinstance(names, dict):
                    names = names.get('lua', names.get('files', names.get('load_order')))
                for name in names:
                    if Path(name).name in ('AetherfallCore.lua', 'AetherfallRegistry.lua', 'AetherfallLayout.lua', 'AetherfallLazy.lua'):
                        continue
                    self.load(name, (SOURCE / name).read_bytes())
            else:
                for name in ['AetherfallHUD.lua', 'AetherfallOptions.lua']:
                    self.load(name, (SOURCE / name).read_bytes())
        self.g.Widget_TriggerScript('AFHTop', 'OnLoad', '')

    def load(self, name, data):
        self.lua.execute(data.decode('utf-8-sig'), name='@source/' + name)

    def table(self, node):
        table = self.lua.table(tag=node.tag, attr=self.lua.table_from(node.attrib))
        if node.text and node.text.strip():
            table[1] = node.text
        for i, child in enumerate(node, 1):
            table[i] = self.table(child)
        return table

    def stock(self, name, args, result):
        self.r.write('stock', name, args, result if isinstance(result, tuple) else (result,))
        return result

    def widget(self, api, *args):
        result = None
        name = str(args[2]) if api == 'AddNewWidget' and len(args)>2 else str(args[1]) if len(args)>1 else ''
        state = self.widgets.setdefault(name, {'text':'', 'visible':1, 'rect':[0,0,1,1], 'attr':{}})
        values = args[2:]
        if api == 'AddNewWidget':
            # All top-level plugin dialogs receive deterministic session slots.
            if len(args) > 2 and args[1] in ('Dialog', 'dialog'):
                self.plugin_dialog(name)
        elif api == 'SetWidgetText': state['text'] = str(values[0])
        elif api == 'GetWidgetText': result = state['text']
        elif api == 'SetWidgetVisible':
            state['visible'] = values[0]
            for d in self.dialogs.values():
                if d['name'] == name: d['shown'] = values[0] == 1 or values[0] is True
        elif api == 'GetWidgetVisible': result = state['visible']
        elif api == 'SetWidgetRect':
            state['rect'] = [v * self.scale for v in values]
            for d in self.dialogs.values():
                if d['name'] == name: d.update(zip(('x','y','w','h'), state['rect']))
        elif api == 'GetWidgetRect': result = tuple(state['rect'])
        elif api in ('SetWidgetPos', 'SetWidgetPosForced'):
            state['rect'][:2] = [v * self.scale for v in values[:2]]
        elif api == 'SetWidgetSize': state['rect'][2:] = [v * self.scale for v in values[:2]]
        elif api == 'SetWidgetAttr': state['attr'][values[0]] = values[1]
        elif api in ('GetWidgetValue','GetWidgetVScrollValue','GetWidgetTextWidth'): result = 0
        self.r.write('widget', api, args, result if isinstance(result, tuple) else (result,))
        return result

    def plugin_dialog(self, name):
        if any(d['name'] == name for d in self.dialogs.values()): return
        ident = next(i for i in range(0x20e,0x222) if i not in self.dialogs)
        self.dialogs[ident] = dict(name=name,x=0,y=0,w=640,h=460,shown=False,flags=8,instance=100000+ident,cls=4096+ident,nodes={})

    def seed_custom(self):
        for name in ('PrivateCompanions','PrivateAccount','PrivateCashShop','PrivateWarehouse','PrivateJourney','PrivateWardrobe'):
            self.plugin_dialog(name)
            self.g.CreateWidget('Dialog', name)
            self.widgets.setdefault(name, {'text':'', 'visible':0, 'rect':[0,0,640,460], 'attr':{}})['visible'] = 0
            opener = name + '_Open'
            self.g[opener] = self.lua.eval("function(name) return function() _G[name]:Show() end end")(name)
        for name in ('PrivateWarehouseBrowser','PrivateCashShopBrowser','PrivateJourneyBrowser'):
            self.g.CreateWidget('Static', name)

    def seed_dialogs(self):
        # Live nodes derive from the actual shipped registry. They are not
        # invented mappings. Choose the same locale/HUD ranking as featureLayout.
        layouts = self.g.AFCore.Layouts()
        chosen = {}
        for _, record in layouts.items():
            row = record.definition
            for _, ident in row.nativeIds.items():
                score = (4 if row.source.startswith('L10N/') else 0) + (2 if 'game_hud_s1/' in row.source else 0)
                if ident not in chosen or score > chosen[ident][0]: chosen[ident] = (score,row)
        design = self.lua.eval("function(s) local f=assert(loadstring(s)); f(); return AFUI_DESIGN end")(frozen('AetherfallLayout.lua').decode())
        hud_ids = set(self.g.AFCore.DIALOG.values())
        for ident, (_, row) in sorted(chosen.items()):
            if ident >= 0x20e: continue
            rect = [float(v) * self.scale for v in row.attributes.frame.split(',')]
            ref = design[ident]
            if ref is not None: rect = [ref[i]*self.scale for i in range(1,5)]
            flags = row.attributes.flag or ''
            nodes = {}
            for _, node in row.widgets.items():
                bounds = node.attributes.frame or '0,0,1,1'
                nodes[node.path] = {'name':node.attributes.name or '', 'parent':node.parent, 'rect':[float(v)*self.scale for v in bounds.split(',')], 'kind':node.attributes.type, 'shown':'not_visible' not in (node.attributes.flag or '')}
            self.dialogs[ident] = dict(name=row.name,x=rect[0],y=rect[1],w=rect[2],h=rect[3],shown=ident in hud_ids,flags=8 if ('movable' in flags or 'title' in flags) and 'not_movable' not in flags else 0,instance=10000+ident,cls=4096+ident,nodes=nodes)
        for ident, ref in design.items():
            if ident not in self.dialogs:
                self.dialogs[ident] = dict(name=ref[7],x=ref[1]*self.scale,y=ref[2]*self.scale,w=ref[3]*self.scale,h=ref[4]*self.scale,shown=True,flags=0,instance=10000+ident,cls=4096+ident,nodes={})

    def node(self, dialog, path, index=None):
        if not dialog: return None, None
        nodes = dialog['nodes']
        if index is not None:
            children = [(p,n) for p,n in nodes.items() if n['parent'] == (path or '')]
            return children[int(index)-1] if 1 <= index <= len(children) else (None,None)
        if not path: return '', {'name':dialog['name'],'rect':[0,0,dialog['w'],dialog['h']], 'parent':None,'shown':True,'kind':'dialog'}
        if path in nodes: return path,nodes[path]
        if '/' in path:
            parent=''
            for part in path.split('/'):
                candidate=(parent+'/' if parent else '')+part
                matches=[(p,n) for p,n in nodes.items() if n['parent']==parent and (n['name']==part or p==candidate)]
                if not matches:return None,None
                parent=matches[0][0]
            return parent,nodes[parent]
        matches = [(p,n) for p,n in nodes.items() if n['name'] == path]
        return matches[0] if matches else (None,None)

    def rect(self, dialog, path):
        key, node = self.node(dialog,path)
        if node is None: return None
        visible=dialog['shown'] and node['shown']
        parent=node['parent']
        while parent and parent in dialog['nodes']:
            p=dialog['nodes'][parent];visible=visible and p['shown'];parent=p['parent']
        if key and (dialog['instance'],key) in self.widget_pins:
            x,y,w,h = self.widget_pins[(dialog['instance'],key)]
        else:
            x,y,w,h = node['rect']
            parent = node['parent']
            while parent and parent in dialog['nodes']:
                p = dialog['nodes'][parent]; x += p['rect'][0]; y += p['rect'][1]; parent=p['parent']
        return dialog['x']+x,dialog['y']+y,w,h,visible

    def bridge(self, command, *a):
        result = self._bridge(command,*a)
        values = result if isinstance(result,tuple) else (result,)
        self.r.write('bridge', command, a, values)
        if command == 'save' and result:
            self.r.write('saved', a[0], (base64.b64encode(a[1].encode('utf-8')).decode('ascii'),))
        return result

    def _bridge(self, command, *a):
        d = self.dialogs.get(a[0]) if a else None
        if command == 'version': return 1,'AetherfallCore trace-fixture 1.3.15'
        if command == 'scale': return self.scale,self.sw,self.sh
        if command == 'cursor': return (*self.mouse[:2],self.sw,self.sh,*self.mouse[2:]) if self.focused else None
        if command == 'dialog': return (d['name'],d['x'],d['y'],d['w'],d['h'],d['shown'],d['cls'],d['instance'],d['flags']) if d else None
        if command == 'read': return self.files.get(a[0])
        if command == 'save':
            name,text = a
            if re.fullmatch(r'[A-Za-z0-9_.-]{1,76}\.cfg',name) and '..' not in name and len(text.encode('utf-8')) <= 32768:
                self.files[name] = text; return True
            return False
        if command == 'money': return 1284377
        if command == 'exp': return 643,1000,20,120,1000
        if command == 'fps': return 59.6
        if command == 'playerid': return 7001
        if command == 'unit': return self.units.get(a[0])
        if command == 'identity': return self.identities.get(a[0])
        if command == 'auras': return len(self.auras[a[0]]) if self.auras.get(a[0]) is not None else None
        if command == 'aura':
            rows=self.auras.get(a[0]) or []
            return rows[a[1]-1] if 1<=a[1]<=len(rows) else None
        if command == 'slot': return self.slots.get(tuple(a),(0x16,1000+a[1],1000+a[1],1,None,None,bool(d and d['shown'])) if a[1]<=2 else (0,0,0,0,None,None,False))
        if command == 'gauge': return 2000,4000,bool(d and d['shown'])
        if command == 'text':
            _,node=self.node(d,a[1])
            return self.labels.get((a[0],a[1]),'TraceBot' if a[1].endswith('/title') else '') if node else None
        if command == 'settext':
            _,node=self.node(d,a[1])
            if node:self.labels[a[0],a[1]]=a[2]
            return node is not None
        if command == 'widgetshown':
            path,node=self.node(d,a[1])
            if node: node['shown']=a[2]
            return node is not None
        if command == 'widget':
            path,node=self.node(d,a[1],a[2] if len(a)>2 else None)
            if not node: return None
            rect=self.rect(d,path)
            return node['name'],3,0,*node['rect'],*rect
        if command == 'rect': return self.rect(d,a[1]) if d else None
        if command == 'move':
            if not d: return False
            d['x'],d['y']=a[1],a[2]
            if len(a)>4 and a[3] is not None and a[4] is not None: d['w'],d['h']=a[3],a[4]
            return True
        if command == 'setshown':
            if d: d['shown']=a[1]
            return bool(d)
        if command == 'movable':
            if d: d['flags'] = (d['flags'] | 8) if a[1] else (d['flags'] & ~8)
            return bool(d)
        if command == 'pin':
            ident,x,y,mode,clamp,w,h,path,kind=a
            if ident==0: self.pins.clear(); self.widget_pins.clear(); return True
            key=(ident,path)
            if x is None:
                self.pins.pop(key,None)
                if d and path:
                    actual,node=self.node(d,path); self.widget_pins.pop((d['instance'],actual),None)
                return True
            if not d: return False
            self.pins[key]=a
            if path:
                actual,node=self.node(d,path)
                if not node: return False
                self.widget_pins[d['instance'],actual]=(x,y,w or node['rect'][2],h or node['rect'][3])
            else:
                if w and w>0: d['w'],d['h']=w,h
                if mode==5: x,y=-20000,-20000
                elif mode!=4:
                    x=x-d['w'] if mode in (1,3) else x; y=y-d['h'] if mode in (2,3) else y
                if clamp: x,y=max(0,min(self.sw-d['w'],x)),max(0,min(self.sh-d['h'],y))
                if mode!=4: d['x'],d['y']=x,y
            return True
        if command == 'pininfo': return (a[0],a[1] if len(a)>1 else None) in self.pins,0,0,bool(d and d['shown'])
        if command == 'tippin': self.tooltip=a; return True
        if command == 'backdrop': self.backdrops[tuple(a[:1]+a[2:4])]=a[1]; return d is not None
        if command == 'backdropclear': self.backdrops.clear(); return True
        if command == 'backdropprune': return True
        if command == 'camerasetting': return (self.camera[a[0]],self.camera_original[a[0]]) if self.camera_available else None
        if command == 'setcamerasetting':
            if not self.camera_available: return False
            self.camera[a[0]]=self.camera_original[a[0]] if a[1]==-1 else a[1]; return True
        if command in ('gaps','command','slashes'): return True
        if command == 'hittest': return bool(d and d['shown'])
        if command == 'popupowner': return None
        if command == 'shown': return bool(d and d['shown'])
        raise AssertionError('Unhandled bridge command ' + command)

    def mark(self, name):
        self.r.scenario=name
        self.r.write('input','scenario',(name,))
        print('scenario: '+name,flush=True)

    def event(self, event):
        self.r.write('input','event',(event,))
        self.g.AFHUD_Dispatch(event)

    def ticks(self, count):
        for _ in range(count): self.event('ADDON_TIMER')

    def slash(self, text):
        self.r.write('input','slash',(text,)); self.g.DoSlashCmd(text); self.ticks(5)

    def click(self, what):
        self.r.write('input','click',(what,)); self.g.AFUI.Click(what)

    def editor(self, what):
        self.r.write('input','editor',(what,)); self.g.AFUI.Editor(what)

    def pointer(self, x, y, down=False, shift=False):
        self.mouse=[x,y,down,shift]; self.r.write('input','pointer',tuple(self.mouse)); self.ticks(1)

    def coverage(self):
        return {str(name).removeprefix('@source/'): {str(line):int(count) for line,count in lines.items()} for name,lines in self.g.TRACE_COVERAGE.items()}
