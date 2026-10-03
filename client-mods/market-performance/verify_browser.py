"""Measure shop/market frontends in Aion's actual WebKit with isolated data.

Market uses production HTML. Shop fixture follows MarketplaceService's DOM and
uses real catalog items; simulated actions never touch players or databases.
"""
import argparse,copy,ctypes as c,http.server,json,os,statistics,threading,time
from html import escape as esc
from pathlib import Path
from urllib.parse import parse_qs,urlparse
import xml.etree.ElementTree as ET
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
def fixture(stress=False):
    ids=[]
    for line in (ROOT/'game-server/config/ingameshop/marketplace_cash.tsv').read_text(encoding='utf-8').splitlines():
        if line and not line.startswith('#'):
            a=line.split('\t');ids.append((int(a[0]),int(a[1]),int(a[2]),a[3],a[5]))
        if len(ids)==(240 if stress else 24):break
    needed={x[0] for x in ids};names={}
    for _,node in ET.iterparse(ROOT/'target-deploy/game-server/data/static_data/items/item_templates.xml',events=['end']):
        if node.tag=='item_template':
            if int(node.get('id')) in needed:names[int(node.get('id'))]=node.get('name')
            node.clear()
    items=[dict(item_id=i,name=names[i],quantity=q,price=p,group='COSTUME',quality='unique',level=1,stock=50,base_price=p,previous_price=p,variant=str(i)+':0:0',description=d) for i,q,p,cat,d in ids]
    storage_items=items;items=items[:24];now=1791010800000
    orders=[dict(items[n%24],id=n+1,side='B' if n%2 else 'S',state='OPEN',remaining=20,quantity=30,collectQuantity=10 if n%2 else 0,collectGross=100000 if n%2==0 else 0,created_at=now) for n in range(50)]
    for n in range(3):orders.append(dict(items[0],id=1001+n,side='S',state='FILLED' if n!=1 else 'CANCELLED',remaining=0,quantity=1,collectQuantity=0,collectGross=50000 if n==2 else 0,created_at=now))
    history=[dict(items[n%24],direction='Bought' if n%2 else 'Sold',unit_price=10000,traded_at=now) for n in range(50)]
    notifications=[dict(items[n%24],order_id=n+1,direction='Bought',remaining=10,available_at=now) for n in range(30)]
    storages=[dict(id=i,name=name,kinah=2000000,limit=300,items=[dict(item,object=10000+j+i*1000,source=i,quantity=10,volume=3,tradeable=True,marketTransferable=i!=125,reserved=False,enchant=0,tempering=0) for j,item in enumerate(storage_items)]) for i,name in [(0,'Inventory'),(1,'Character Warehouse'),(2,'Account Warehouse'),(125,'Market Warehouse')]]
    storages[-1]['items'].append(dict(storage_items[0],object=999999,quantity=10,reserved=True,enchant=0,tempering=0))
    state=dict(player='Fixture',balance=9000000,reservedKinah=100000,volume=2400,volumeLimit=50000,returnPercent=84.5,netProceeds=84500,activeOrders=50,simulatedTraders=0,serverTime=now,request='fixture-0',storages=storages,catalog=items,subcategories=['COSTUME'],page=1,total=72,selected=None,favorites=[],orders=orders,history=history,collections=[dict(net=84500,gross=100000,collected_at=now)],notifications=notifications,orderPage=1,orderTotal=50,historyPage=1,historyTotal=50)
    state['categoryTree']=[dict(name='Weapons',count=14300,types=[dict(name='Swords',count=3000),dict(name='Daggers',count=2200),dict(name='Aether Keys',count=1200)]),dict(name='Armor',count=15000,types=[dict(name='Cloth Armor',count=3500),dict(name='Plate Armor',count=4000)]),dict(name='Accessories',count=2000,types=[dict(name='Rings',count=900)]),dict(name='Enhancement',count=2000,types=[dict(name='Composite Manastones',count=100)]),dict(name='Materials',count=1500,types=[dict(name='Fluxes',count=400)]),dict(name='Consumables',count=2500,types=[dict(name='Food & Drink',count=800)]),dict(name='Containers',count=1000,types=[dict(name='Boxes & Bundles',count=1000)])]
    state['alwaysMax']=False
    state['catalog'][0]['previous_price']=max(1,int(state['catalog'][0]['base_price']*.9))
    return items,state

def icon(i,shop=True):
    link='nc://aion.ItemInfo/ItemTooltip?item='+str(i['item_id'])+'&count='+str(i['quantity'])+'&enchant_count=0&authorize_count=0'
    return '<a class="'+('native-item-icon' if shop else 'item-icon')+'" href="'+esc(link)+'" title="'+esc(link)+'" onclick="return false" tabindex="-1"><img src="/'+('shop' if shop else 'market')+'/media/icons/'+str(i['item_id'])+'.png?v=native-3" width="64" height="64" alt=""></a>'

def shop_html(items,args,favorites,sequence,defer=False):
    item=args.get('item','');section=args.get('section','all');view=args.get('view','catalog');review=args.get('review','')
    cards=[]
    for i in items:
        target='/shop?session_id=fixture&item='+str(i['item_id']);favorite=i['item_id'] in favorites
        form='<form class="favorite-action'+(' is-favorite' if favorite else '')+'" method="post" action="/shop" onsubmit="return shopAction(this)"><input name="session_id" type="hidden" value="fixture"><input name="item" type="hidden" value="'+str(i['item_id'])+'"><input name="action" type="hidden" value="favorite"><input name="form" type="hidden" value="fixture-'+str(sequence)+'"><button title="Favorite">★</button></form>'
        cards.append('<article class="product quality-unique" id="offer-'+str(i['item_id'])+'"><div class="product-art">'+form+icon(i)+'<span class="product-quantity">×'+str(i['quantity'])+'</span></div><div class="product-content"><div class="product-overline">Appearance · UNIQUE</div><h3>'+esc(i['name'])+'</h3><div class="item-chips"><span>LV 1+</span><span class="appearance-chip">Remodelable</span></div></div><div class="product-bottom"><div class="price">'+format(i['price'],',')+'<small>Kinah</small></div><a class="product-button" href="'+esc(target)+'">View item</a></div><button class="info-trigger">i</button><div class="mini-tooltip"><strong>'+esc(i['name'])+'</strong><p>'+esc(i['description'])+'</p></div></article>')
    content='<div class="collection-heading"><h2>'+esc(section.title())+'</h2><p>Cash Shop items. Prices are in Kinah.</p></div><form class="finder" method="get" action="/shop"><input type="hidden" name="session_id" value="fixture"><div class="search-field"><input name="q" placeholder="Search items"></div><label>Sort<select name="sort"><option value="curated">Curated</option><option value="price-low">Lowest price</option></select></label><button class="filter-button">Search</button></form><div class="results-line">24 ITEMS</div><div class="products">'+''.join(cards)+'</div>'
    if item:
        chosen=next(i for i in items if str(i['item_id'])==item)
        purchase='<form class="purchase-form" method="post" action="/shop" onsubmit="return shopConfirm(this)"><input name="session_id" type="hidden" value="fixture"><input name="item" type="hidden" value="'+item+'"><input name="form" type="hidden" value="fixture-'+str(sequence)+'"><input name="action" type="hidden" value="purchase"><button class="purchase-button">Buy for '+str(chosen['price'])+' Kinah</button></form>'
        content='<div class="breadcrumb"><a href="/shop?session_id=fixture">All items</a></div><div class="detail-page"><div class="detail-art">'+icon(chosen)+'</div><div class="detail-copy"><h2>'+esc(chosen['name'])+'</h2><p class="detail-description">'+esc(chosen['description'])+'</p><button onclick="return shopPreview(this,'+item+')">Item Preview</button>'+(purchase if review else '<a class="purchase-button" href="/shop?session_id=fixture&item='+item+'&review=1">Review purchase</a>')+'</div></div><div class="detail-information">Item details</div>'
    if view=='history':content='<h2>Purchase history</h2><table class="purchase-history">'+''.join('<tr><td>'+icon(i)+'</td><td>'+esc(i['name'])+'</td><td>'+str(i['price'])+' Kinah</td></tr>' for i in items)+'</table>'
    links=''.join('<a class="side-link'+(' active' if section==s else '')+'" href="/shop?session_id=fixture&section='+s+'"><span class="side-symbol">◇</span>'+s.title()+'</a>' for s in ['all','featured','fashion','wings','companions','upgrades'])
    tabs=''.join('<a class="shop-tab'+(' active' if view==v else '')+'" href="/shop?session_id=fixture&view='+v+'">'+v.title()+'</a>' for v in ['catalog','favorites','history'])
    result = '<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/shop/media/marketplace.css"><script>window.onerror=function(m){window.__scriptError=String(m);};</script><script src="/shop/media/marketplace.js"'+(' defer' if defer else '')+'></script></head><body><div class="shell"><div class="skyline"><div class="skyline-inner">BLACK CLOUD MARKETPLACE</div></div><header class="hero"><div class="hero-inner"><h1>Black Cloud <span>Marketplace</span></h1><nav class="shop-tabs">'+tabs+'</nav></div></header><div class="shop-layout"><aside class="sidebar"><div class="sidebar-top"><div class="character-name">Fixture</div><div class="wallet-number">9,000,000</div><div class="wallet-unit">KINAH</div></div><nav class="side-nav">'+links+'</nav></aside><main class="main-content" id="shop-content">'+content+'</main></div><footer class="shop-footer">Pay in Kinah. Purchases arrive in Black Cloud mail.</footer></div></body></html>'

    if not defer:
        tag='<script src="/shop/media/marketplace.js"></script>'
        result=result.replace(tag,'').replace('</body>',tag+'</body>')
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--browser-bin',type=Path,required=True);p.add_argument('--baseline',type=Path);p.add_argument('--report',type=Path,required=True);p.add_argument('--verify',action='store_true');p.add_argument('--sizes');p.add_argument('--storage-stress',action='store_true');a=p.parse_args()
    items,state=fixture(a.storage_stress);sequence=[0];reads=[];posts=[];favorites=set();fault=[False];catalog_queries=[]
    def media(kind,name):
        return a.baseline/kind/name if a.baseline and (a.baseline/kind/name).exists() else ROOT/'game-server/config'/kind/'media'/name
    def snapshot(args):
        r=copy.deepcopy(state);variant=args.get('variant')
        if variant:
            d=copy.deepcopy(next(i for i in items if str(i['item_id'])==variant.split(':')[0]));d.update(maxQuantity=1000,levels=[d['price']-1000,d['price'],d['price']+1000],book=[dict(side='S',price=d['price'],quantity=30)],variants=[dict(variant=d['variant'],enchant=0,tempering=0)],traded=300,volume=10,queue=[],chart=[dict(day=20000+n,price=d['price']+n*100,volume=10) for n in range(30)]);r['selected']=d
        return r
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self,*unused):pass
        def send(self,data,kind='application/json',status=200):
            data=data if isinstance(data,bytes) else data.encode('utf-8');self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
        def do_GET(self):
            u=urlparse(self.path);args={k:v[0] for k,v in parse_qs(u.query).items()};reads.append(u.path)
            if u.path=='/shop':
                sequence[0]+=1
                self.send(shop_html(items,args,favorites,sequence[0],True),'text/html; charset=utf-8')
            elif u.path=='/market':
                html=media('central-market','market.html').read_text(encoding='utf-8').replace('<script src=', '<script>window.__intervals=[];window.setInterval=function(f,ms){window.__intervals.push(f);return window.__intervals.length;};</script><script src=',1)
                self.send(html,'text/html; charset=utf-8')
            elif u.path=='/market/state':
                catalog_queries.append(args)
                sequence[0]+=1;state['request']='fixture-'+str(sequence[0]);state['serverTime']+=1
                self.send(json.dumps(snapshot(args)))
            elif '/media/' in u.path and '/icons/' not in u.path:
                kind='ingameshop' if u.path.startswith('/shop') else 'central-market';name=u.path.split('/media/',1)[1];path=media(kind,name)
                self.send(path.read_bytes(),{'.css':'text/css','.js':'application/javascript','.webp':'image/webp','.svg':'image/svg+xml'}.get(path.suffix,'application/octet-stream'))
            else:self.send('Missing','text/plain',404)
        def do_POST(self):
            form={k:v[0] for k,v in parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode()).items()};assert form['session_id']=='fixture';posts.append(form)
            if fault[0]:fault[0]=False;self.send('Simulated failure','text/plain',500);return
            if self.path.startswith('/shop'):
                if form.get('action')=='favorite':favorites.add(int(form['item']));form.pop('item',None)
                self.send(shop_html(items,form,favorites,sequence[0],True),'text/html; charset=utf-8')
            else:
                assert form['request']==state['request'],'Stale action request'
                if form.get('action')=='collect':
                    order=next(o for o in state['orders'] if str(o['id'])==form['order']);order['collectGross']=order['collectQuantity']=0
                if form.get('action')=='preference':state['alwaysMax']=form['alwaysMax']=='1'
                if form.get('action')=='transferBatch':
                    source=next(s for s in state['storages'] if s['id']==int(form['source']));target=next(s for s in state['storages'] if s['id']==int(form['target']));selected={int(e.split(':')[0]):int(e.split(':')[1]) for e in form['items'].split(',')}
                    moved=[i for i in source['items'] if i['object'] in selected];assert len(moved)==len(selected) and all(i['quantity']==selected[i['object']] for i in moved)
                    if target['id']==125:assert all(i['marketTransferable'] for i in moved);state['volume']+=sum(i['volume']*i['quantity'] for i in moved)
                    source['items']=[i for i in source['items'] if i['object'] not in selected]
                    target['items']+=[dict(i,source=target['id'],marketTransferable=target['id']!=125) for i in moved]
                state['notice']='Fixture action confirmed';self.send(json.dumps(snapshot(form)))
    server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
    directory=os.add_dll_directory(str(a.browser_bin.resolve()));lib=c.CDLL(str(a.browser_bin/'Awesomium.dll'));ptr=c.c_void_p
    def api(name,result,*params):f=getattr(lib,name);f.restype=result;f.argtypes=list(params);return f
    init=api('awe_webcore_initialize_default',None);update=api('awe_webcore_update',None);shutdown=api('awe_webcore_shutdown',None);create=api('awe_webcore_create_webview',ptr,c.c_int,c.c_int,c.c_bool);destroy=api('awe_webview_destroy',None,ptr)
    make=api('awe_string_create_from_wide',ptr,c.c_wchar_p,c.c_size_t);free=api('awe_string_destroy',None,ptr);load=api('awe_webview_load_url',None,ptr,ptr,ptr,ptr,ptr);evaluate=api('awe_webview_execute_javascript_with_result',ptr,ptr,ptr,ptr,c.c_int)
    text=api('awe_jsvalue_to_string',ptr,ptr);utf8=api('awe_string_to_utf8',c.c_size_t,ptr,ptr,c.c_size_t);jsfree=api('awe_jsvalue_destroy',None,ptr);render=api('awe_webview_render',ptr,ptr);dirty=api('awe_webview_is_dirty',c.c_bool,ptr)
    pixels=api('awe_renderbuffer_get_buffer',ptr,ptr);stride=api('awe_renderbuffer_get_rowspan',c.c_int,ptr);move=api('awe_webview_inject_mouse_move',None,ptr,c.c_int,c.c_int)
    down=api('awe_webview_inject_mouse_down',None,ptr,c.c_int);up=api('awe_webview_inject_mouse_up',None,ptr,c.c_int)
    tip_events=[];tip_type=c.CFUNCTYPE(None,ptr,ptr)
    def on_tip(view,value):
        buf=c.create_string_buffer(4096);utf8(value,buf,len(buf));tip_events.append(buf.value.decode())
    tip_listener=tip_type(on_tip);set_tip=api('awe_webview_set_callback_change_tooltip',None,ptr,tip_type)
    init();bridge=c.CDLL(str(a.browser_bin/'AionIconBridge.dll'));bridge.AionIconBridgeInitialize.restype=c.c_int;assert bridge.AionIconBridgeInitialize()==1
    empty=make('',0);views=[];report={};out=ROOT/'output/market-performance';out.mkdir(parents=True,exist_ok=True)
    def js(v,code):
        s=make('String('+code+')',len('String('+code+')'));value=evaluate(v,s,empty,1000);free(s);assert value,code
        string=text(value);buf=c.create_string_buffer(1048576);utf8(string,buf,len(buf));free(string);jsfree(value);return buf.value.decode('utf-8')
    def pump(sec=.03):
        end=time.monotonic()+sec
        while time.monotonic()<end:update();time.sleep(.002)
    def wait(v,code,expected='true'):
        end=time.monotonic()+10
        while time.monotonic()<end:
            update()
            if js(v,code)==expected:return
            time.sleep(.003)
        raise AssertionError(code+': '+js(v,code)+'; xhr='+js(v,'window.__xhr&&JSON.stringify([window.__xhr.readyState,window.__xhr.status])')+'; busy='+js(v,'document.querySelector(".shell")&&document.querySelector(".shell").getAttribute("aria-busy")')+'; posts='+repr(posts[-2:])+'; shop='+js(v,'typeof window.shopAction')+'; error='+js(v,'window.__scriptError')+'; ready='+js(v,'document.readyState')+'; reads='+repr(reads[-8:]))
    def action(v,code):js(v,'(function(){'+code+';return true;}())')
    def click(v,selector):
        action(v,'document.querySelector('+json.dumps(selector)+').scrollIntoView(false)')
        point=json.loads(js(v,'(function(){var r=document.querySelector('+json.dumps(selector)+').getBoundingClientRect();return JSON.stringify([Math.round((r.left+r.right)/2),Math.round((r.top+r.bottom)/2)]);}())'))
        if not (0<=point[0]<w and 0<=point[1]<h):
            buf=render(v);Image.frombytes('RGBA',(w,h),c.string_at(pixels(buf),stride(buf)*h),'raw','BGRA',stride(buf)).save(out/'offscreen-control.png')
            raise AssertionError('Offscreen control: '+selector+' '+repr(point))
        move(v,*point);down(v,0);up(v,0);pump(.04)
    def paint(v):assert render(v)
    def timing(v,actionfn,ready=None):
        pump(.04);paint(v);start=time.perf_counter();actionfn()
        if ready:
            if callable(ready):ready()
            else:wait(v,ready)
        update();paint(v);return (time.perf_counter()-start)*1000
    def measure(v,actionfn,ready=None):return round(statistics.median(timing(v,actionfn,ready) for _ in range(5)),2)
    def refresh(v):click(v,'#refresh');wait(v,'!document.getElementById("refresh").disabled')
    def hover(v,selector):
        action(v,'document.querySelector('+json.dumps(selector)+').scrollIntoView(false)');pump(.1)
        coords=json.loads(js(v,'(function(){var r=document.querySelector('+json.dumps(selector)+').getBoundingClientRect();return JSON.stringify([Math.round(r.left+Math.min(150,r.width/2)),Math.round(r.top+r.height/2)]);}())'))
        move(v,0,0);pump(.25);paint(v);start=time.monotonic();move(v,*coords);frames=0;paint_ms=0
        while time.monotonic()-start<.25:
            update()
            if dirty(v):t=time.perf_counter();paint(v);paint_ms+=(time.perf_counter()-t)*1000;frames+=1
            time.sleep(.002)
        return dict(repaint_frames=frames,render_ms=round(paint_ms,2))
    try:
        sizes=[(1920,1052)] if not a.verify else [(1024,740),(1366,740),(1920,1052),(3440,1412)]
        if a.sizes:sizes=[tuple(map(int,size.split('x'))) for size in a.sizes.split(',')]
        for w,h in sizes:
            for kind,path in [('market','/market'),('shop','/shop')]:
                v=create(w,h,False);views.append(v);set_tip(v,tip_listener);url=f'http://127.0.0.1:{server.server_port}'+path+'?session_id=fixture';s=make(url,len(url));load(v,s,empty,empty,empty);free(s)
                wait(v,'document.querySelectorAll("'+('#catalog-list .product' if kind=='market' else '.products .product')+'").length===24');pump(.3);paint(v)
                if a.verify:
                    buf=render(v);Image.frombytes('RGBA',(w,h),c.string_at(pixels(buf),stride(buf)*h),'raw','BGRA',stride(buf)).save(out/(kind+'-initial-'+str(w)+'x'+str(h)+'.png'))
                if kind=='market':
                    if a.verify:assert js(v,'document.getElementById("orders-list").childNodes.length+document.getElementById("history-list").childNodes.length+document.getElementById("notifications-list").childNodes.length')=='0','Hidden market views must load on demand'
                    if a.verify:
                        assert catalog_queries[-1]['filter']=='changed' and catalog_queries[-1]['sort']=='change','Opening list must request movers, not alphabetic catalogue'
                        assert js(v,'document.getElementById("sub")===null')=='true','Subtype dropdown must be replaced by category children'
                        assert js(v,'document.querySelector(".product .trend").textContent.indexOf("%")>=0')=='true','Price direction and percentage must be visible'
                        click(v,'[data-category="Weapons"]');wait(v,'!document.getElementById("refresh").disabled');assert js(v,'document.querySelectorAll(".category-child").length')=='3','Weapon category must expand'
                        click(v,'[data-sub="Aether Keys"]');wait(v,'!document.getElementById("refresh").disabled');assert catalog_queries[-1]['sub']=='Aether Keys' and catalog_queries[-1]['filter']=='all','Click subtype must browse all matching items'
                        action(v,'document.getElementById("min-level").value="50";document.getElementById("max-level").value="65";document.getElementById("min-price").value="1000";document.getElementById("max-price").value="1000000";document.getElementById("quality").value="MYTHIC";document.getElementById("slot").value="Chest";document.getElementById("item-filters").onsubmit({preventDefault:function(){}})');wait(v,'!document.getElementById("refresh").disabled')
                        assert all(catalog_queries[-1].get(k)==val for k,val in dict(minLevel='50',maxLevel='65',minPrice='1000',maxPrice='1000000',quality='MYTHIC',slot='Chest').items()),'Filter request lost values'
                        before_reads=len(reads);action(v,'document.getElementById("min-price").value="500";document.getElementById("max-price").value="100";document.getElementById("item-filters").onsubmit({preventDefault:function(){}})');assert len(reads)==before_reads,'Inverted range must be rejected before sending'
                        click(v,'#reset-filters');wait(v,'!document.getElementById("refresh").disabled');assert catalog_queries[-1]['quality']=='all' and catalog_queries[-1]['slot']=='All' and not catalog_queries[-1].get('minPrice'),'Reset must clear ranges and grade/slot'
                        click(v,'[data-overview]');wait(v,'!document.getElementById("refresh").disabled')
                        bounds=json.loads(js(v,'JSON.stringify([].map.call(document.querySelectorAll("#item-filters input,#item-filters select,#item-filters button"),function(n){var r=n.getBoundingClientRect(),p=n.parentNode.parentNode.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom};}))'))
                        assert all(0<=b['left']<b['right']<=w and b['bottom']<=284 for b in bounds),'Browse filters overflow their panel'
                        print('PASS: opening movers, category tree, range/grade/slot filters, reset and validation',flush=True)
                        assert js(v,'document.querySelector(".storage-tabs .active").getAttribute("data-storage")')=='125','Market Warehouse must be the opening storage tab'
                        reserved_selector=json.dumps('[data-object="999999"]');assert js(v,'document.querySelector('+reserved_selector+')===null')=='true','Listed stock must not appear in warehouse slots'
                        before_reads=len(reads);switches=[]
                        for storage_id in [0,125,1,2,0,125]:
                            tab_selector=json.dumps('[data-storage="'+str(storage_id)+'"]');duration=float(js(v,'(function(){var t=Date.now();document.querySelector('+tab_selector+').onclick();return Date.now()-t;}())'));switches.append(duration)
                            assert js(v,'document.querySelector(".storage-tabs .active").getAttribute("data-storage")')==str(storage_id),'Storage switch must finish inside its click handler'
                            assert duration<150,'Storage switch blocks UI: '+str(duration)
                        assert len(reads)==before_reads,'Storage tab switch must not wait for HTTP'
                        pump(.5)
                        print('PASS: immediate storage switches',switches,flush=True)
                    click(v,'#catalog-list .product');wait(v,'!!document.getElementById("price-chart")');pump(.15)
                    # Warm every activity view, then measure repeated tab reuse.
                    for view in ['orders','history','notifications','market']:click(v,'[data-view="'+view+'"]')
                    metrics=dict(scroll_ms=measure(v,lambda:action(v,'var n=document.getElementById("catalog-list");n.scrollTop=n.scrollTop>100?40:220')),refresh_ms=measure(v,lambda:refresh(v)),hover=hover(v,'#catalog-list .product'))
                    toggle=[False]
                    def tab():toggle[0]=not toggle[0];click(v,'[data-view="'+('orders' if toggle[0] else 'market')+'"]')
                    metrics['tabs_ms']=measure(v,tab)
                    if a.verify:metrics['storage_switch_ms']=switches
                    if a.verify:
                        click(v,'[data-view="market"]');action(v,'window.__catalog=document.querySelector("#catalog-list .product");window.__chart=document.getElementById("price-chart")');refresh(v)
                        assert js(v,'window.__catalog===document.querySelector("#catalog-list .product")&&window.__chart===document.getElementById("price-chart")')=='true','Unchanged refresh replaced market nodes'
                        action(v,'window.__intervals[0]()');wait(v,'!document.getElementById("refresh").disabled');assert js(v,'window.__chart===document.getElementById("price-chart")')=='true'
                        state['catalog'][0]['stock']+=1;refresh(v);assert js(v,'window.__catalog!==document.querySelector("#catalog-list .product")')=='true','Changed catalog was not updated'
                        click(v,'#buy-item');assert js(v,'document.getElementById("dialog-shade").style.display')=='block';click(v,'#dialog-confirm');wait(v,'document.getElementById("dialog-shade").style.display!=="block"');assert posts[-1]['action']=='buy'
                        click(v,'[data-view="orders"]');assert js(v,'document.querySelectorAll("#orders-list .order-row").length')==str(50+sum(o['id']==1003 and o['collectGross']>0 for o in state['orders'])),'Settled closed orders must be hidden'
                        if next(o for o in state['orders'] if o['id']==1003)['collectGross']:
                            click(v,'[data-collect="1003"]');wait(v,'document.querySelector('+json.dumps('[data-collect="1003"]')+')===null');assert posts[-1]['action']=='collect','Uncollected closed order must stay claimable, then disappear'
                        click(v,'[data-view="history"]');assert js(v,'document.querySelectorAll("#history-list .table-row").length')=='50','Trade history must be preserved';click(v,'[data-view="orders"]')
                else:
                    metrics=dict(scroll_ms=measure(v,lambda:action(v,'var n=document.querySelector(".main-content");n.scrollTop=n.scrollTop>100?40:260')),hover=hover(v,'.products .product'))
                    toggle=[False]
                    def category():toggle[0]=not toggle[0];click(v,'.side-link:nth-child('+('2' if toggle[0] else '1')+')')
                    metrics['navigation_ms']=measure(v,category,lambda:wait(v,'document.readyState==="complete"&&document.querySelector(".side-link.active").href.indexOf("section='+('featured' if toggle[0] else 'all')+'")>0&&document.querySelector(".shell").getAttribute("aria-busy")!=="true"'))
                    if a.verify:
                        action(v,'window.__wallet=document.querySelector(".sidebar-top")');click(v,'.side-link:nth-child(3)');wait(v,'document.querySelector(".shell").getAttribute("aria-busy")==="false"');assert js(v,'window.__wallet===document.querySelector(".sidebar-top")')=='true','Unchanged wallet node replaced'
                        click(v,'.favorite-action button');wait(v,'document.querySelector(".shell").getAttribute("aria-busy")==="false"');assert posts[-1]['action']=='favorite'
                        click(v,'.product-button');wait(v,'!!document.querySelector(".detail-page")')
                        action(v,'window.__preview=null;window.AionObject={ItemPreview:function(id){window.__preview=id;}}');click(v,'.detail-copy button');assert js(v,'window.__preview')==str(items[0]['item_id']),'Native preview dispatch failed'
                        click(v,'a.purchase-button');wait(v,'!!document.querySelector(".purchase-form")');before_posts=len(posts);click(v,'.purchase-button');wait(v,'document.querySelector(".shell").getAttribute("aria-busy")==="false"');assert posts[-1]['action']=='purchase' and len(posts)==before_posts+1
                        click(v,'.breadcrumb a');wait(v,'!!document.querySelector(".products")');fault[0]=True;click(v,'.favorite-action button');wait(v,'!!document.querySelector(".shop-status.is-error")');assert js(v,'document.querySelector(".shell").getAttribute("aria-busy")')=='false'
                if kind=='market' and a.verify:
                    baseline_state=copy.deepcopy(state)
                    click(v,'[data-view="market"]');click(v,'[data-storage="0"]');click(v,'#storage-grid .storage-pane:not([style*="none"]) [data-object]');click(v,'#transfer-item')
                    assert js(v,'document.getElementById("quantity").value')=='10' if state['alwaysMax'] else js(v,'document.getElementById("quantity").value')=='1'
                    assert js(v,'!!document.querySelector(".always-max-frame")')=='true'
                    if not state['alwaysMax']:click(v,'#always-max');wait(v,'!document.getElementById("always-max").disabled');assert posts[-1]['action']=='preference' and state['alwaysMax']
                    assert js(v,'document.getElementById("quantity").value')=='10' and js(v,'document.getElementById("dialog-shade").style.display')=='block','Saving Always Max must fill quantity and keep dialog open'
                    paint(v);buf=render(v);Image.frombytes('RGBA',(w,h),c.string_at(pixels(buf),stride(buf)*h),'raw','BGRA',stride(buf)).save(out/('market-transfer-max-'+str(w)+'x'+str(h)+'.png'))
                    click(v,'#dialog-cancel');click(v,'#transfer-item');assert js(v,'document.getElementById("quantity").value')=='10';click(v,'#dialog-cancel')
                    action(v,'window.__reloadMarker=true');s=make(url,len(url));load(v,s,empty,empty,empty);free(s);wait(v,'!window.__reloadMarker&&!!document.querySelector("#storage-grid [data-object]")&&!document.getElementById("refresh").disabled');pump(.15)
                    click(v,'#storage-grid .storage-pane:not([style*="none"]) [data-object]');click(v,'#sell-item');wait(v,'document.getElementById("dialog-title").textContent==="Register Sale"')
                    assert js(v,'document.getElementById("always-max").checked&&document.getElementById("quantity").value==="10"')=='true','Saved preference must survive reload and apply to Register Sale'
                    paint(v);buf=render(v);Image.frombytes('RGBA',(w,h),c.string_at(pixels(buf),stride(buf)*h),'raw','BGRA',stride(buf)).save(out/('market-sale-max-'+str(w)+'x'+str(h)+'.png'))
                    click(v,'#always-max');wait(v,'!document.getElementById("always-max").disabled');assert not state['alwaysMax'];click(v,'#dialog-cancel');click(v,'#sell-item');wait(v,'document.getElementById("dialog-title").textContent==="Register Sale"');assert js(v,'document.getElementById("quantity").value')=='1'
                    fault[0]=True;click(v,'#always-max');wait(v,'!document.getElementById("refresh").disabled&&!document.getElementById("always-max").disabled');assert not state['alwaysMax'] and js(v,'document.getElementById("always-max").checked')=='false','Failed preference save must revert checkbox without closing the dialog';click(v,'#dialog-cancel')
                    click(v,'[data-storage="0"]');click(v,'#select-items');click(v,'#select-all');assert js(v,'!!document.getElementById("transfer-market-selected")')=='true','All eligible selected inventory items must show direct market transfer'
                    paint(v);buf=render(v);Image.frombytes('RGBA',(w,h),c.string_at(pixels(buf),stride(buf)*h),'raw','BGRA',stride(buf)).save(out/('market-batch-market-'+str(w)+'x'+str(h)+'.png'))
                    rect=json.loads(js(v,'(function(){var b=document.getElementById("transfer-market-selected").getBoundingClientRect(),p=document.getElementById("storage-item").getBoundingClientRect();return JSON.stringify([b.left,b.right,b.top,b.bottom,p.left,p.right,p.top,p.bottom]);}())'));assert rect[0]>=rect[4] and rect[1]<=rect[5] and rect[2]>=rect[6] and rect[3]<=rect[7],'Batch market button must fit its own UI panel'
                    click(v,'#transfer-market-selected');print('CHECK: batch dialog opened',flush=True);assert js(v,'document.getElementById("target").value')=='125','Direct transfer must preselect Market Warehouse';click(v,'#dialog-cancel')
                    print('CHECK: batch eligibility mutations',flush=True);inventory=state['storages'][0];inventory['items'][0]['marketTransferable']=False;refresh(v);assert js(v,'document.getElementById("transfer-market-selected")===null')=='true','A single ineligible item must hide the market batch button'
                    click(v,'#storage-grid .storage-pane:not([style*="none"]) [data-object]');assert js(v,'!!document.getElementById("transfer-market-selected")')=='true','Deselecting the ineligible item must restore the button';click(v,'#storage-grid .storage-pane:not([style*="none"]) [data-object]');assert js(v,'document.getElementById("transfer-market-selected")===null')=='true'
                    inventory['items'][0]['marketTransferable']=True;needed=sum(i['volume']*i['quantity'] for i in inventory['items']);state['volumeLimit']=state['volume']+needed-1;refresh(v);assert js(v,'document.getElementById("transfer-market-selected")===null')=='true','Insufficient combined volume must hide button'
                    state['volumeLimit']+=1;refresh(v);assert js(v,'!!document.getElementById("transfer-market-selected")')=='true','Exact capacity must permit batch transfer'
                    print('CHECK: batch capacity passed',flush=True)
                    for storage_id in [1,2]:click(v,'[data-storage="'+str(storage_id)+'"]');click(v,'#select-all');assert js(v,'!!document.getElementById("transfer-market-selected")')=='true','Character and Account selections must support market transfer'
                    click(v,'[data-storage="125"]');click(v,'#select-all');assert js(v,'document.getElementById("transfer-market-selected")===null')=='true','Market must not offer transfer to itself'
                    print('CHECK: batch submit',flush=True);click(v,'[data-storage="0"]');click(v,'#select-all');click(v,'#transfer-market-selected');click(v,'#dialog-confirm');wait(v,'document.getElementById("dialog-shade").style.display!=="block"');assert posts[-1]['action']=='transferBatch' and posts[-1]['target']=='125' and not inventory['items'],'Whole batch must reach requested warehouse'
                    assert js(v,'document.getElementById("transfer-market-selected")===null')=='true','Successful transfer must clear selection and hide button'
                    state.clear();state.update(baseline_state);action(v,'window.__reloadMarker=true');s=make(url,len(url));load(v,s,empty,empty,empty);free(s);wait(v,'!window.__reloadMarker&&!!document.querySelector("#storage-grid [data-object]")&&!document.getElementById("refresh").disabled');pump(.15)
                    print('PASS: framed Always Max, saved/reloaded preference, failure recovery and batch eligibility/volume/transfer in all source tabs',flush=True)
                report[kind+'-'+str(w)+'x'+str(h)]=metrics
                if a.verify:
                    selector=json.dumps('a[title^="nc://aion.ItemInfo/ItemTooltip?"]');links=json.loads(js(v,'JSON.stringify([].map.call(document.querySelectorAll('+selector+'),function(n){return n.title;}))'));assert links and all('item=' in x and '&count=' in x for x in links)
                    node=json.dumps('#storage-grid a[title^="nc://"]' if kind=='market' else '.products a[title^="nc://"]');action(v,'document.querySelector('+node+').scrollIntoView(false)');pump(.05)
                    point=json.loads(js(v,'(function(){var r=document.querySelector('+node+').getBoundingClientRect();return JSON.stringify([Math.round((r.left+r.right)/2),Math.round((r.top+r.bottom)/2)]);}())'));move(v,0,0);pump(.08);tip_events.clear();move(v,*point);pump(.8);assert any(t.startswith('nc://aion.ItemInfo/ItemTooltip?item=') for t in tip_events),'Native tooltip event missing: '+repr(tip_events)
                    assert js(v,'[].every.call(document.querySelectorAll('+json.dumps('a[title^="nc://"] img[src]')+'),function(n){return n.complete&&n.naturalWidth>0;})')=='true','Native item icon missing'
                    paint(v);buf=render(v);Image.frombytes('RGBA',(w,h),c.string_at(pixels(buf),stride(buf)*h),'raw','BGRA',stride(buf)).save(out/(kind+'-'+str(w)+'x'+str(h)+'.png'))
                    print('PASS:',kind,w,h,'navigation, refresh/actions, native icon and tooltip links',flush=True)
                destroy(v);views.remove(v)
        a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(dict(engine='publisher Aion 4.8 NA Awesomium',scope='isolated frontend fixture; excludes game GPU upload',measurements=report),indent=2),encoding='utf-8');print(json.dumps(report),flush=True)
        assert not any('/media/icons/' in x for x in reads),'Native icons unexpectedly downloaded over HTTP'
    finally:
        for v in views:destroy(v)
        free(empty);shutdown();server.shutdown();directory.close()
if __name__=='__main__':main()
