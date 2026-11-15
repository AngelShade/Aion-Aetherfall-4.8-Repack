(function () {
  'use strict';
  var tr = window.AetherfallWeb.text, fmt = window.AetherfallWeb.format;

  function marketNotice(text) {
    var m = /^Transferred (\d+) item stacks?\.$/.exec(text);
    if (m) return fmt(text.slice(-7) === 'stacks.' ? 'Transferred {count} item stacks.' : 'Transferred {count} item stack.', {count:m[1]});
    m = /^Transferred (\d+) (.+)\.$/.exec(text);
    if (m) return fmt('Transferred {count} {item}.', {count:m[1], item:m[2]});
    m = /^(Deposited|Withdrew) (\d+) Kinah\.$/.exec(text);
    if (m) return fmt(m[1] === 'Deposited' ? 'Deposited {count} Kinah.' : 'Withdrew {count} Kinah.', {count:m[2]});
    m = /^Collected (\d+) (items into Market Warehouse\. Transfer them to Inventory when ready\.|Kinah after market tax into Market Warehouse\.)$/.exec(text);
    if (m) return fmt(m[2].charAt(0) === 'i' ? 'Collected {count} items into Market Warehouse. Transfer them to Inventory when ready.' : 'Collected {count} Kinah after market tax into Market Warehouse.', {count:m[1]});
    m = /^(Bought|Sold) (\d+) \/ (\d+) items\. (Collect (?:items|Kinah) beside this order in My Orders\. )?(Sale queued for 15 minutes\.|Order complete\.|(\d+) items remain on order\.)$/.exec(text);
    if (m) {
      var fill = fmt(m[1] === 'Bought' ? 'Bought {filled} / {total} items.' : 'Sold {filled} / {total} items.', {filled:m[2], total:m[3]});
      return fill + ' ' + (m[4] ? tr(m[4].slice(0, -1)) + ' ' : '') + (m[6] ? fmt('{count} items remain on order.', {count:m[6]}) : tr(m[5]));
    }
    m = /^Order quantity must be between 1 and (\d+)\.$/.exec(text);
    if (m) return fmt('Order quantity must be between 1 and {count}.', {count:m[1]});
    m = /^(.+) (cannot be transferred to this warehouse\.|cannot enter Market Warehouse\.) No items were transferred\.$/.exec(text);
    if (m) return fmt(m[2] === 'cannot be transferred to this warehouse.' ? '{item} cannot be transferred to this warehouse. No items were transferred.' : '{item} cannot enter Market Warehouse. No items were transferred.', {item:m[1]});
    return tr(text);
  }
  var state = null, storage = 125, view = 'market', selectedObject = 0, selectedPrice = 0, busy = false, loading = false, modalAction = null, searchTimer, warehouseQuery = '';
  var activeRead = null, readVersion = 0, refreshPending = false, storagePanes = {}, activePane = null, rendered = {}, storageIcons = null, batchIcons = null;
  var selectMode = false, selectedObjects = {}, expandedCategory = '';
  // Icons already requested this session come straight from the browser's memory cache, so
  // rebuilt grids (after a transfer or refresh) show them at once instead of re-queuing them.
  var loadedIcons = {};
  // Troubleshooting readout (click the status bar): page stalls, icon loading and request times as
  // seen inside the game, where the client's own browser pump can behave differently from tests.
  var diag = { on: false, last: Date.now(), gap: 0, req: 0, ok: 0, err: 0, tab: '', tabStart: 0, tabLast: 0, tabIcons: 0, xhr: [] };
  function diagIcon(failed) { if (failed) { diag.err++; this.style.visibility = 'hidden'; } else diag.ok++; if (diag.tabStart) { diag.tabIcons++; diag.tabLast = Date.now() - diag.tabStart; } }
  function diagShow() { var hint = document.querySelector('.keyboard-hint'); if (hint) hint.innerHTML = diag.on ? html('Diag · longest stall ' + diag.gap + ' ms · ' + (diag.tab ? diag.tab + ': ' + diag.tabIcons + ' icons, last at ' + diag.tabLast + ' ms · ' : '') + 'icons ' + diag.ok + '/' + diag.req + ' loaded, ' + diag.err + ' failed · ' + diag.xhr.join(' · ')) : label('Enter to search · Esc to close item details'); }
  setInterval(function () { var now = Date.now(); diag.gap = Math.max(diag.gap, now - diag.last - 50); diag.last = now; if (diag.on) diagShow(); }, 50);
  var query = { q: '', category: 'All Items', sub: 'All', filter: 'changed', page: 1, variant: '', sort: 'change', minLevel: '', maxLevel: '', minPrice: '', maxPrice: '', quality: 'all', slot: 'All', orderPage: 1, historyPage: 1, orderFilter: 'all', historyFilter: 'all' };
  var session = (location.search.match(/[?&](?:session_id|token)=([^&]*)/) || [null, ''])[1];
  function byId(id) { return document.getElementById(id); }
  function setMarkup(id, markup, key) {
    key = key == null ? markup : key;
    if (rendered[id] === key) return false;
    byId(id).innerHTML = markup; rendered[id] = key; return true;
  }
  function renderActivity() {
    if (view === 'orders') renderOrders();
    else if (view === 'history') renderHistory();
    else if (view === 'notifications') renderNotifications();
  }
  function html(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;'); }
  function label(text) { return html(tr(text)); }
  function amount(n) { return fmt('{count} Kinah', {count:money(n)}); }
  function money(n) { return String(n == null ? 0 : Math.floor(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ','); }
  function netProceeds(gross) { var rate=state.returnPercent===84.5?845:650; return Math.floor(gross/1000)*rate+Math.floor((gross%1000)*rate/1000); }
  function volume(n) { return money(Math.floor(n / 10)) + (n % 10 ? '.' + n % 10 : ''); }
  function date(n) { return new Date(n).toLocaleString(); }
  // Native browser tooltip type 28 parses named fields separated by '&'.
  function tooltipLink(i) { return 'nc://aion.ItemInfo/ItemTooltip?' + (i.tooltip || 'item=' + (+i.item_id) + '&count=' + (i.quantity || 1) + '&enchant_count=' + (i.enchant || 0) + '&authorize_count=' + (i.tempering || 0)); }
  // Icons are cropped during asset preparation, avoiding synchronous canvas/GPU readback.
  function image(i, lazy) { var link = html(tooltipLink(i)), url = '/market/media/icons/' + i.item_id + '.png?v=native-3'; return '<a class="item-icon" href="' + link + '" title="' + link + '" onclick="return false" tabindex="-1" aria-hidden="true"><img ' + (lazy && !loadedIcons[url] ? 'data-src' : 'src') + '="' + url + '" alt="" onerror="this.style.visibility=\'hidden\'"></a>'; }
  // The game's browser serves about one icon per game frame and queues everything else (clicks,
  // warehouse updates) behind pending icon requests. Keep two icons in flight and ask for the next
  // one only when one arrives, so input is never stuck behind a whole tab of icons.
  function feedIcons(nodes, wanted) {
    var job = { off: false }, cursor = 0, inflight = 0;
    function next() { if (job.off || !wanted()) return; while (inflight < 2 && cursor < nodes.length) start(nodes[cursor++]); }
    function start(node) {
      var src = node.getAttribute('data-src'), settled = false; if (!src) return;
      function finish(state) { if (settled) return; settled = true; inflight--; if (state) diagIcon.call(node, state === 'error'); setTimeout(next, 0); }
      inflight++; diag.req++; loadedIcons[src] = true;
      node.onload = function () { finish('load'); }; node.onerror = function () { finish('error'); };
      setTimeout(function () { finish(''); }, 4000);
      node.src = src; node.removeAttribute('data-src');
    }
    // Let the tab/frame paint before the first icon request.
    setTimeout(next, 16);
    return job;
  }
  function loadStorageIcons(cached) {
    if (storageIcons) storageIcons.off = true;
    var grid = byId('storage-grid'), top = grid.scrollTop - 56, bottom = grid.scrollTop + grid.clientHeight + 56, images = cached.images, pending = [], i, node, slot;
    for (i = 0; i < images.length; i++) {
      node = images[i]; if (!node.getAttribute('data-src')) continue; slot = node.parentNode.parentNode;
      if (slot.offsetTop >= top && slot.offsetTop <= bottom) pending.push(node);
    }
    storageIcons = feedIcons(pending, function () { return activePane === cached.pane; });
  }
  function name(i, display) { return (i.enchant ? '+' + i.enchant + ' ' : '') + i.name + (i.tempering ? ' · ' + (display ? fmt('Tempering +{level}', {level:i.tempering}) : 'Tempering +' + i.tempering) : ''); }
  function stackLabel(n) { return fmt(n === 1 ? '{count} item stack' : '{count} item stacks', {count:n}); }
  function fields(extra) { var a = {}, k; for (k in query) if (query.hasOwnProperty(k)) a[k] = query[k]; a.session_id = decodeURIComponent(session); for (k in extra) if (extra.hasOwnProperty(k)) a[k] = extra[k]; return a; }
  function encode(a) { var out = [], k; for (k in a) if (a.hasOwnProperty(k)) out.push(encodeURIComponent(k) + '=' + encodeURIComponent(a[k])); return out.join('&'); }
  function status(text, error) { byId('status').textContent = marketNotice(text); byId('connection-dot').className = 'connection-dot' + (error ? ' error' : ''); }
  function request(method, url, data, done) {
    var x = new XMLHttpRequest(), delivered = false;
    x.open(method, url, true); if (method === 'POST') x.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    x.onreadystatechange = function () { if (x.readyState !== 4 || delivered) return; delivered = true; var r; try { r = JSON.parse(x.responseText); } catch (e) { r = { error: 'Central Market could not confirm this request. Refresh Warehouse.' }; } done(x.status === 200, r); };
    x.onerror = function () { if (delivered) return; delivered = true; done(false, { error: 'Connection lost. Refresh Warehouse before another transfer.' }); };
    var timer = setTimeout(function () { if (delivered) return; delivered = true; x.abort(); done(false, {error: 'Request timed out. Refresh Warehouse to check the result.'}); }, 30000);
    var started = Date.now(), originalDone = done; done = function (ok, r) { clearTimeout(timer); diag.xhr.unshift((url.indexOf('action') >= 0 ? 'action ' : url.indexOf('section=detail') >= 0 ? 'detail ' : url.indexOf('section=') >= 0 ? 'poll ' : 'state ') + (Date.now() - started) + ' ms ' + Math.round((x.responseText || '').length / 1024) + ' KB' + (ok ? '' : ' failed')); diag.xhr.length = Math.min(diag.xhr.length, 3); originalDone(ok, r); };
    x.send(method === 'POST' ? encode(data) : null);
    return { cancel: function () { delivered = true; clearTimeout(timer); x.abort(); } };
  }
  function cancelRead() {
    readVersion++; if (activeRead) activeRead.cancel(); activeRead = null;
    loading = false; byId('refresh').disabled = false;
  }
  function fetchState(detail, done, catalogOnly) {
    if (busy) { refreshPending = true; return; }
    if (!state) { detail = false; catalogOnly = false; }
    cancelRead(); var version = readVersion, requestedVariant = query.variant;
    loading = true; byId('refresh').disabled = true;
    status(detail ? 'Loading item prices…' : 'Updating Warehouse…');
    activeRead = request('GET', '/market/state?' + encode(fields(detail ? {section:'detail'} : catalogOnly ? {section:catalogOnly === 'activity' ? 'activity' : 'catalog'} : {})), null, function (ok, r) {
      if (version !== readVersion) return;
      activeRead = null; loading = false; byId('refresh').disabled = false;
      if (!ok) { status(r.error, true); return; }
      if (detail) {
        state.selected = r.selected; state.request = r.request; state.serverTime = r.serverTime;
        renderDetail(); markCatalogSelection();
      } else if (catalogOnly === 'activity') {
        for (var key in r) if(r.hasOwnProperty(key)) state[key] = r[key]; keepScroll(function () { render(true); });
      } else if (catalogOnly) {
        state.catalog = r.catalog; state.subcategories = r.subcategories; state.categoryTree = r.categoryTree; state.page = r.page; state.total = r.total;
        state.request = r.request; state.serverTime = r.serverTime; query.page = r.page; renderCatalog();
      } else {
        var print = marketPrint(r), unchanged = print === lastPrint;
        state = r; query.page = r.page; lastPrint = print;
        if (!unchanged) keepScroll(render);
      }
      status(fmt('Warehouse updated · {date}', {date:date(r.serverTime)}));
      if (done && requestedVariant === query.variant) done();
    });
  }
  function refresh() { fetchState(false); }
  // Fingerprint of what the page shows, with sorted keys so the server's map order cannot count as a change.
  var lastPrint = null;
  function canon(value) {
    if (value === null || typeof value !== 'object') return JSON.stringify(value);
    var out = [], i;
    if (value instanceof Array) { for (i = 0; i < value.length; i++) out.push(canon(value[i])); return '[' + out.join(',') + ']'; }
    var keys = Object.keys(value).sort(); for (i = 0; i < keys.length; i++) if (keys[i] !== 'serverTime' && keys[i] !== 'request') out.push(JSON.stringify(keys[i]) + ':' + canon(value[keys[i]]));
    return '{' + out.join(',') + '}';
  }
  function marketPrint(result) { return query.variant + '|' + storage + '|' + canon(result); }
  // Scrolling panes are emptied while redrawn; put each back where the player left it.
  function keepScroll(redraw) {
    var nodes = document.getElementsByTagName('*'), panes = [], i;
    for (i = 0; i < nodes.length; i++) if (nodes[i].scrollTop > 0) panes.push([nodes[i], nodes[i].scrollTop]);
    redraw();
    for (i = 0; i < panes.length; i++) if (panes[i][0].scrollTop !== panes[i][1]) panes[i][0].scrollTop = panes[i][1];
  }
  function refreshCatalog() {
    if(state) renderCategories();
    fetchState(false, null, true);
  }
  function markCatalogSelection() {
    var products = byId('catalog-list').querySelectorAll('[data-item]'), i, selected = query.variant.split(':')[0];
    for (i = 0; i < products.length; i++) products[i].className = 'product' + (products[i].getAttribute('data-item') === selected ? ' selected' : '');
  }
  function act(a, keepDialog) {
    if (busy || !state) return;
    cancelRead(); busy = true; byId('dialog-confirm').disabled = true;
    var body = fields(a); body.request = state.request;
    status(fmt('Processing {action}…', {action:tr(a.action === 'transfer' ? 'item transfer' : a.action)}));
    request('POST', '/market/action', body, function (ok, r) {
      busy = false; byId('dialog-confirm').disabled = false;
      if (byId('always-max')) byId('always-max').disabled = false;
      if (!ok) { if(keepDialog){byId('dialog-confirm').disabled=true;if(byId('always-max')){byId('always-max').checked=!!state.alwaysMax;byId('always-max').disabled=true;}} byId('dialog-error').textContent = marketNotice(r.error); status(r.error, true); fetchState(false,function(){if(keepDialog){byId('dialog-confirm').disabled=false;if(byId('always-max')){byId('always-max').checked=!!state.alwaysMax;byId('always-max').disabled=false;}}status(r.error,true);}); return; }
      state = r; if (a.action === 'transferBatch') selectedObjects = {}; if(!keepDialog)closeDialog();else if(byId('always-max'))byId('always-max').checked=!!state.alwaysMax; render(); status(r.notice || 'Warehouse updated.');
      if (refreshPending) { refreshPending = false; refresh(); }
    });
  }
  function currentStorage() { var i; if (!state) return null; for (i = 0; i < state.storages.length; i++) if (state.storages[i].id === storage) return state.storages[i]; return state.storages[0]; }
  function selectedItem() { var s = currentStorage(), i; if (!s) return null; for (i = 0; i < s.items.length; i++) if (s.items[i].object === selectedObject) return s.items[i]; return null; }
  function render(activityOnly) {
    byId('codex-link').style.display = state.codex ? '' : 'none';
    byId('character').innerHTML = html(fmt('{character} · Account Market Warehouse', {character:state.player}) + (state.simulatedTraders ? ' · ' + fmt('{count} simulated traders', {count:money(state.simulatedTraders)}) : ''));
    byId('balance').innerHTML = html(amount(state.balance)); byId('reserved').innerHTML = html(fmt('Reserved {amount}', {amount:amount(state.reservedKinah)}));
    byId('volume').innerHTML = volume(state.volume) + ' / ' + volume(state.volumeLimit) + ' VT';
    byId('volume-fill').style.width = Math.min(100, state.volume * 100 / state.volumeLimit) + '%';
    byId('return-rate').innerHTML = html(fmt('{rate}% return', {rate:state.returnPercent})); byId('proceeds').innerHTML = html(amount(state.netProceeds));
    var active = 0, i; for (i = 0; i < state.orders.length; i++) if (state.orders[i].state === 'OPEN' || state.orders[i].state === 'QUEUED') active++;
    byId('order-count').innerHTML = state.activeOrders || ''; byId('order-count').style.display = state.activeOrders ? 'inline-block' : 'none';
    byId('notification-count').innerHTML = state.notifications.length || ''; byId('notification-count').style.display = state.notifications.length ? 'inline-block' : 'none';
    if(!activityOnly) { renderStorage(); renderCatalog(); renderDetail(); } renderActivity();
  }
  byId('storage-grid').onscroll = function () { if (storagePanes[storage]) { storagePanes[storage].scroll = this.scrollTop; loadStorageIcons(storagePanes[storage]); } };
  function renderStorage() {
    var s = currentStorage(), text = '', i, item, visible = 0, grid = byId('storage-grid');
    byId('capacity').innerHTML = s.id === 125 ? html(fmt('{count} stacks', {count:s.items.length})) : s.items.length + ' / ' + s.limit;
    var wallet = kinahStorage(); byId('source-kinah').innerHTML = label(wallet.name) + ' <b>' + money(wallet.kinah) + '</b>';
    byId('deposit-kinah').disabled = wallet.id === 125; byId('withdraw-kinah').disabled = wallet.id === 125;
    var cached = storagePanes[s.id], key, markup = cached && cached.source === s && cached.query === warehouseQuery ? cached.markup : JSON.stringify(s) + warehouseQuery;
    if (!cached) { cached = storagePanes[s.id] = {pane: document.createElement('div'), markup: '', scroll: 0}; cached.pane.className = 'storage-pane'; grid.appendChild(cached.pane); }
    activePane = cached.pane;
    cached.source = s; cached.query = warehouseQuery;
    for (key in storagePanes) if (storagePanes.hasOwnProperty(key)) storagePanes[key].pane.style.display = storagePanes[key] === cached ? '' : 'none';
    if (markup === cached.markup) { grid.scrollTop = cached.scroll; renderStorageSelection(); loadStorageIcons(cached); return; } cached.markup = markup;
    for (i = 0; i < s.items.length; i++) {
      item = s.items[i]; if (item.reserved || name(item).toLowerCase().indexOf(warehouseQuery.toLowerCase()) < 0) continue; visible++;
      text += '<div tabindex="0" role="button" aria-label="' + html(fmt('{item}, quantity {count}', {item:name(item, true), count:item.quantity})) + '" draggable="true" class="slot' + (item.object === selectedObject ? ' selected' : '') + '" data-object="' + item.object + '">' + image(item, true) + '<span class="selection-check"></span>' + (item.enchant ? '<span class="enchant">+' + item.enchant + '</span>' : '') + '<span class="quantity">' + (item.quantity > 1 ? money(item.quantity) : '') + '</span></div>';
    }
    // Expanded warehouses have up to 540 slots; capacity shows the limit, so only pad a few
    // rows of empty slots instead of building hundreds of placeholder nodes per tab.
    if (!warehouseQuery) for (i = visible; i < Math.max(30, Math.min(s.limit || visible + 6, visible + 36)); i++) text += '<div class="slot empty"></div>';
    if (!visible && warehouseQuery) text = '<p class="empty-list">'+label('No items found.')+'</p>';
    activePane.innerHTML = text; cached.images = activePane.querySelectorAll('img[data-src]'); cached.nextIcon = 0; grid.scrollTop = cached.scroll;
    loadStorageIcons(cached);
    renderStorageSelection();
    var slots = activePane.querySelectorAll('[data-object]');
    for (i = 0; i < slots.length; i++) (function (el) {
      el.onclick = function (e) { var id = +el.getAttribute('data-object'); if (selectMode || e && e.ctrlKey) { selectMode = true; toggleSelection(id); } else { selectedObject = id; renderStorageSelection(); } };
      el.onkeydown = function (e) { if (e.keyCode === 13 || e.keyCode === 32) { e.preventDefault(); el.onclick(e); } };
      el.ondblclick = function () { if (selectMode || busy) return; selectedObject = +el.getAttribute('data-object'); var item = selectedItem(); if (!item.reserved) transfer(item, storage === 125 ? 0 : 125); };
      el.ondragstart = function (e) { var item = findObject(+el.getAttribute('data-object')); if (busy || item.reserved) { e.preventDefault(); return; } if (selectMode && !selectedObjects[item.object]) { selectedObjects = {}; selectedObjects[item.object] = true; renderStorageSelection(); } e.dataTransfer.setData('text/plain', item.object); };
    }(slots[i]));
  }
  function renderStorageSelection() {
    var slots = activePane ? activePane.querySelectorAll('[data-object]') : [], j, batch = selectedItems(), batchActions=selectMode&&batch.length>0;
    var valid = {}; for (j = 0; j < batch.length; j++) valid[batch[j].object] = true; selectedObjects = valid;
    byId('storage-grid').className = 'storage-grid' + (selectMode ? ' selection-mode' : '') + (batchActions ? ' batch-actions' : '');
    byId('storage-item').className = 'storage-item' + (batchActions ? ' batch-actions' : '');
    byId('select-items').className = selectMode ? 'active' : ''; byId('select-items').innerHTML = label(selectMode ? 'Done' : 'Select Items'); byId('select-items').setAttribute('aria-pressed', String(selectMode));
    byId('select-all').disabled = !selectMode; byId('clear-selection').disabled = !batch.length;
    byId('selection-count').innerHTML = selectMode ? html(fmt('{count} selected', {count:batch.length})) : '';
    for (j = 0; j < slots.length; j++) {
      var selected = +slots[j].getAttribute('data-object') === selectedObject;
      var checked = !!selectedObjects[+slots[j].getAttribute('data-object')];
      var slotClass = slots[j].className.replace(/ selected| batch-selected/g, '') + (!selectMode && selected ? ' selected' : '') + (checked ? ' batch-selected' : ''), pressed = String(selectMode ? checked : selected);
      if (slots[j].className !== slotClass) slots[j].className = slotClass;
      if (slots[j].getAttribute('aria-pressed') !== pressed) slots[j].setAttribute('aria-pressed', pressed);
    }
    var s = currentStorage(), item = selectedItem(), text = '<span>'+label('Select an item to transfer or register a sale.')+'</span>';
    if (item) text = '<strong class="quality-' + html(item.quality) + '">' + html(name(item, true)) + '</strong>' + (item.reserved ? '<small>'+label('Reserved for a sale listing. Cancel it in My Orders.')+'</small>' : '<button id="transfer-item">'+label('Transfer')+'</button>' + (s.id === 125 ? '<button id="sell-item" class="primary">'+label('Register Sale')+'</button>' : canEnterMarket([item]) ? '<button id="market-deposit">'+label('Market Warehouse')+'</button>' : ''));
    if (selectMode) text = batch.length ? '<strong>' + html(fmt('{stacks} selected', {stacks:stackLabel(batch.length)})) + '</strong><button id="transfer-selected">'+label('Transfer Selected')+'</button><small>'+label('Full stacks')+'</small>' + (canEnterMarket(batch) ? '<button id="transfer-market-selected" class="primary">'+label('Transfer Market Warehouse')+'</button>' : '<small class="market-selection-hint">' + label(s.id===125 ? 'Choose a destination with Transfer Selected.' : 'Every selected item must be eligible and fit in Market Warehouse.') + '</small>') : '<span>' + label('Select items, then choose Transfer Selected.') + '<br>' + label('Ctrl + click also selects items.') + '</span>';
    if (!setMarkup('storage-item', text, text + '|' + storage + '|' + JSON.stringify(item))) return;
    if (byId('transfer-item')) byId('transfer-item').onclick = function () { transfer(item); };
    if (byId('sell-item')) byId('sell-item').onclick = function () { chooseVariant(item.variant, function () { sell(item); }); };
    if (byId('market-deposit')) byId('market-deposit').onclick = function () { transfer(item, 125); };
    if (byId('transfer-selected')) byId('transfer-selected').onclick = function () { transferSelected(); };
    if (byId('transfer-market-selected')) byId('transfer-market-selected').onclick = function () { transferSelected(125); };
  }
  function canEnterMarket(items) {
    if(storage===125||!state||!items.length)return false;
    var room=state.volumeLimit-state.volume,j,item,unit;
    for(j=0;j<items.length;j++){item=items[j];unit=+item.volume;if(!item.marketTransferable||item.reserved||unit<=0||item.quantity>Math.floor(room/unit))return false;room-=unit*item.quantity;}
    return true;
  }
  function selectedItems() { var s = currentStorage(), out = [], i; if (!s) return out; for (i = 0; i < s.items.length; i++) if (selectedObjects[s.items[i].object] && !s.items[i].reserved) out.push(s.items[i]); return out; }
  function toggleSelection(id) { var item = findObject(id); if (!item || item.reserved) return; if (selectedObjects[id]) delete selectedObjects[id]; else { if (selectedItems().length >= 300) { status('Select up to 300 item stacks.', true); return; } selectedObjects[id] = true; } renderStorageSelection(); }
  function findObject(id) { var s = currentStorage(), i; for (i = 0; i < s.items.length; i++) if (s.items[i].object === id) return s.items[i]; return null; }
  function browseAll() {
    if(query.filter === 'changed') { query.filter = 'all'; if(query.sort === 'change') query.sort = 'name'; }
    byId('filter').value = query.filter; byId('sort').value = query.sort;
  }
  function renderCategories() {
    var tree = state.categoryTree || [], cats = '<button data-overview="true" class="' + (query.filter === 'changed' ? 'active' : '') + '">'+label('↕ Price Changes')+'</button><button data-category="All Items" data-sub="All" class="' + (query.category === 'All Items' && query.filter !== 'changed' ? 'active' : '') + '">'+label('All Items')+'</button>', i, j, c, t;
    for(i=0;i<tree.length;i++) {
      c=tree[i]; cats += '<button data-category="' + html(c.name) + '" data-sub="All" aria-expanded="' + (expandedCategory === c.name) + '" class="category-parent' + (query.category === c.name ? ' active' : '') + '"><span>' + (expandedCategory === c.name ? '▾ ' : '▸ ') + label(c.name) + '</span><small>' + money(c.count) + '</small></button>';
      if(expandedCategory === c.name) for(j=0;j<c.types.length;j++) {
        t=c.types[j]; cats += '<button data-category="' + html(c.name) + '" data-sub="' + html(t.name) + '" class="category-child' + (query.sub === t.name ? ' active' : '') + '"><span>' + label(t.name) + '</span><small>' + money(t.count) + '</small></button>';
      }
    }
    if(!setMarkup('categories',cats)) return;
    var buttons=byId('categories').getElementsByTagName('button');
    for(i=0;i<buttons.length;i++) buttons[i].onclick=function(){
      if(this.getAttribute('data-overview')) { expandedCategory='';query.category='All Items';query.sub='All';query.filter='changed';query.sort='change';byId('filter').value=query.filter;byId('sort').value=query.sort; }
      else { query.category=this.getAttribute('data-category');query.sub=this.getAttribute('data-sub');expandedCategory=query.category === 'All Items' ? '' : query.sub === 'All' && expandedCategory === query.category ? '' : query.category;browseAll(); }
      query.page=1;refreshCatalog();
    };
  }
  function renderCatalog() {
    var list = '', i, item, price, change, percent;
    renderCategories(); byId('results').innerHTML = html(fmt('{count} items', {count:money(state.total)}));
    byId('filter').value=query.filter;byId('sort').value=query.sort;
    byId('catalog-context').innerHTML=query.filter === 'changed' ? '<strong>' + label('Price Changes') + '</strong> · ' + html(fmt('Last price update within 24 hours · Ranked by {sort}', {sort:query.sort === 'change' ? tr('largest change %') : byId('sort').options[byId('sort').selectedIndex].text})) : label(query.category) + (query.sub !== 'All' ? ' › ' + label(query.sub) : '') + ' · ' + label('Base prices for unenchanted items');
    for (i = 0; i < state.catalog.length; i++) {
      item = state.catalog[i]; price = item.base_price || 0; change = price - (item.previous_price || price); percent = item.previous_price > 0 ? Math.abs(change * 100 / item.previous_price).toFixed(2) : '0.00';
      list += '<div tabindex="0" role="button" class="product' + (query.variant.split(':')[0] === String(item.item_id) ? ' selected' : '') + '" data-item="' + item.item_id + '">' + image(item) + '<span class="item-name quality-' + html(item.quality) + '">' + html(item.name) + '</span><small>' + label(item.type || gameTerm(item.group)) + ' · ' + html(fmt('Lv. {level}', {level:item.level})) + '</small><span class="stock" title="'+label('Listed quantity / cumulative quantity traded across all variants')+'">' + money(item.stock) + '<small>' + html(fmt('{count} traded', {count:money(item.traded || 0)})) + '</small></span><span class="price">' + money(price) + (change ? '<small class="trend ' + (change > 0 ? 'up' : 'down') + '" title="' + html(fmt('Previous base price: {amount}. Compared with the last market price adjustment.', {amount:amount(item.previous_price)})) + '">' + (change > 0 ? '↑' : '↓') + ' ' + money(Math.abs(change)) + ' (' + percent + '%)</small>' : '') + '</span>' + (isFavorite(item.item_id) ? '<span class="favorite-star">★</span>' : '') + '</div>';
    }
    var catalogChanged = setMarkup('catalog-list', list || '<p class="empty-list">' + label(query.filter === 'changed' ? 'No matching base prices changed in the last 24 hours. Choose All Items to browse the market.' : 'No items match these filters. Adjust the ranges or select Reset.') + '</p>');
    byId('page').innerHTML = state.page + ' / ' + Math.max(1, Math.ceil(state.total / 24)); byId('previous').disabled = state.page <= 1; byId('next').disabled = state.page * 24 >= state.total;
    if (!catalogChanged) { markCatalogSelection(); return; }
    var products = byId('catalog-list').querySelectorAll('[data-item]');
    for (i = 0; i < products.length; i++) products[i].onclick = function () { chooseVariant(this.getAttribute('data-item') + ':0:0'); };
    for (i = 0; i < products.length; i++) products[i].onkeydown = function (e) { if (e.keyCode === 13) this.onclick(); };
  }
  function chooseVariant(key, done) {
    if (busy || !state) return; query.variant = key; selectedPrice = 0;
    state.selected = null; renderDetail(); markCatalogSelection(); fetchState(true, done);
  }
  function isFavorite(id) { var i; for (i = 0; i < state.favorites.length; i++) if (state.favorites[i] === id) return true; return false; }
  function renderDetail() {
    var d = state.selected, text, i, level, sellCount, buyCount, book, variants = '';
    byId('detail').style.display = d && view === 'market' ? '' : 'none';
    document.querySelector('main').className = d && view === 'market' ? 'has-detail' : '';
    if (view !== 'market') return;
    if (!d) { setMarkup('detail', '<div class="empty-detail"><span class="market-emblem">◇</span><h2>'+label('Select an item')+'</h2><p>'+label('Check prices, buy orders and sale listings.')+'</p></div>'); return; }
    if (!selectedPrice || d.levels.indexOf(selectedPrice) < 0) {
      selectedPrice = d.base_price;
      var lowest = 0;
      for(i=0;i<d.book.length;i++) if(d.book[i].side === 'S' && bookQuantity(d.book[i]) > 0 && d.levels.indexOf(d.book[i].price)>=0 && (!lowest || d.book[i].price < lowest)) lowest = d.book[i].price;
      if(lowest) selectedPrice = lowest;
    }
    var detailKey = JSON.stringify(d) + '|' + selectedPrice + '|' + isFavorite(d.item_id) + '|' + query.variant + '|' + document.documentElement.clientWidth + 'x' + document.documentElement.clientHeight;
    if (rendered.detail === detailKey) return;
    for (i = 0; i < d.variants.length; i++) { var v = d.variants[i]; variants += '<option value="' + html(v.variant) + '">' + '+' + v.enchant + (v.tempering ? ' · ' + html(fmt('Tempering +{level}', {level:v.tempering})) : '') + (v.variant.split(':').length > 3 ? ' · ' + html(fmt('Modified item {index}', {index:i + 1})) : '') + '</option>'; }
    text = '<div class="detail-head">' + image(d) + '<h2 class="quality-' + html(d.quality) + '">' + html(d.name) + '</h2><select id="variant-select" aria-label="'+label('Item enchantment and attributes')+'">' + variants + '</select><button class="favorite" id="favorite" title="' + label(isFavorite(d.item_id) ? 'Remove from Favorites' : 'Add to Favorites') + '">' + (isFavorite(d.item_id) ? '★' : '☆') + '</button><button id="close-detail" class="close-detail" title="'+label('Close item details')+'">×</button></div><div class="detail-main"><div class="price-book"><div class="book-heading"><span>'+label('Available to buy')+'</span><b>'+label('Waiting buyers')+'</b><strong>'+label('Price · Kinah')+'</strong></div><div class="book-levels">';
    var displayLevels = d.levels.slice(0);
    for(i = 0; i < d.book.length; i++) if(displayLevels.indexOf(d.book[i].price) < 0) displayLevels.push(d.book[i].price);
    displayLevels.sort(function(a,b){return a-b;});
    for (i = displayLevels.length - 1; i >= 0; i--) {
      level = displayLevels[i]; sellCount = 0; buyCount = 0;
      for (var j = 0; j < d.book.length; j++) { book = d.book[j]; if (book.price === level) { if (book.side === 'S') sellCount += bookQuantity(book); else buyCount += bookQuantity(book); } }
      text += '<div' + (d.levels.indexOf(level) >= 0 ? ' tabindex="0" role="button" data-price="' + level + '"' : ' title="' + label('Existing orders outside the current price band') + '"') + ' class="book-row' + (level === selectedPrice ? ' chosen' : '') + '"><span class="sell-count">' + (sellCount ? money(sellCount) : '—') + '</span><span class="buy-count">' + (buyCount ? money(buyCount) : '—') + '</span><span class="price-value">' + money(level) + '</span></div>';
    }
    text += '</div></div><div class="buy-controls"><div class="summary">'+label('Base price')+' <strong>' + html(amount(d.base_price)) + '</strong> · ' + html(fmt('{count} traded', {count:money(d.traded)})) + '</div><div class="summary">'+label('Warehouse volume')+' <strong>' + d.volume / 10 + ' VT</strong> '+label('each')+'</div><div id="purchase-availability" class="availability"></div><button class="primary" id="buy-item">'+label('Buy / Place Order')+'</button><button id="preview">'+label('Item Preview')+'</button><button id="item-stats">'+label('Item Details')+'</button><div class="chart"><span class="chart-label">' + label('30-day price history') + ' · ' + (d.queue.length ? html(fmt('Registration queue: {count} items', {count:money(d.queue[0].quantity)})) : label('Completed trades')) + '</span><canvas id="price-chart" width="320" height="65"></canvas></div></div></div>';
    if (!setMarkup('detail', text, detailKey)) return;
    byId('variant-select').value = query.variant;
    byId('variant-select').onchange = function () { chooseVariant(this.value); };
    byId('favorite').onclick = function () { act({ action: 'favorite', item: d.item_id }); };
    byId('close-detail').onclick = function () { cancelRead(); query.variant = ''; state.selected = null; renderDetail(); markCatalogSelection(); };
    byId('buy-item').onclick = buy;
    byId('preview').onclick = function () { if (!window.AionObject || !window.AionObject.ItemPreview) { status('Item Preview is available in Aion.', true); return; } try { window.AionObject.ItemPreview(+d.item_id); } catch (e) { status('Item Preview could not open.', true); } };
    byId('item-stats').onclick = function () { details(d); };
    var levels = byId('detail').querySelectorAll('[data-price]');
    for (i = 0; i < levels.length; i++) { levels[i].onclick = function () { selectedPrice = +this.getAttribute('data-price'); var rows = byId('detail').querySelectorAll('[data-price]'), k; for (k = 0; k < rows.length; k++) rows[k].className = 'book-row' + (rows[k] === this ? ' chosen' : ''); updateAvailability(); }; levels[i].onkeydown = function (e) { if (e.keyCode === 13) this.onclick(); }; }
    var chosen = byId('detail').querySelector('.chosen'); if (chosen) chosen.parentNode.scrollTop = Math.max(0, chosen.offsetTop - 62);
    updateAvailability(); drawChart(d.chart);
  }
  function bookQuantity(entry) { return entry.available_quantity == null ? entry.quantity : entry.available_quantity; }
  function stockAt(price) {
    var d=state.selected, count=0, i;
    if(d) for(i=0;i<d.book.length;i++) if(d.book[i].side === 'S' && d.book[i].price <= price) count+=bookQuantity(d.book[i]);
    return count;
  }
  function updateAvailability() {
    var available=stockAt(selectedPrice), el=byId('purchase-availability');
    if(el) el.textContent=available ? fmt('{count} available at this price or lower · Buy now', {count:money(available)}) : tr('No sale stock at this price · Place a preorder');
    if(byId('buy-item')) byId('buy-item').textContent=tr(available ? 'Buy Now' : 'Place Buy Order');
  }
  function drawChart(points) {
    var canvas = byId('price-chart');
    canvas.width = canvas.clientWidth || 320; canvas.height = canvas.clientHeight || 65;
    var c = canvas.getContext('2d'), w = canvas.width, h = canvas.height, i, min, max;
    c.clearRect(0, 0, w, h); c.font = '10px Tahoma';
    if (!points.length) { c.fillStyle = '#93abbc'; c.textAlign = 'center'; c.fillText(tr('No completed trades yet'), w / 2, h / 2); return; }
    min = max = points[0].price; for (i = 1; i < points.length; i++) { min = Math.min(min, points[i].price); max = Math.max(max, points[i].price); }
    for (i = 0; i < 3; i++) { c.strokeStyle = '#1f3447'; c.beginPath(); c.moveTo(0, 8 + i * (h - 25) / 2); c.lineTo(w, 8 + i * (h - 25) / 2); c.stroke(); }
    c.strokeStyle = '#8fe3ec'; c.lineWidth = 1.5; c.beginPath();
    for (i = 0; i < points.length; i++) { var x = 5 + i * (w - 10) / Math.max(1, points.length - 1), y = max === min ? (h - 17) / 2 : 8 + (max - points[i].price) * (h - 25) / (max - min); if (!i) c.moveTo(x, y); else c.lineTo(x, y); }
    c.stroke(); c.fillStyle = '#93abbc'; c.textAlign = 'left'; c.fillText(money(min) + ' – ' + money(max), 5, h - 2);
    canvas.onmousemove = function (e) { var r = canvas.getBoundingClientRect(), index = Math.min(points.length - 1, Math.max(0, Math.round((e.clientX - r.left) * (points.length - 1) / r.width))), p = points[index]; canvas.title = fmt('{date} · {amount} · {count} traded', {date:new Date(p.day * 86400000).toLocaleDateString(), amount:amount(p.price), count:money(p.volume)}); };
  }
  function itemHeader(i) { return '<div class="confirm-item">' + image(i) + html(name(i, true)) + '<small>' + label(gameTerm(i.group || '')) + '</small></div>'; }
  function positionDialog() {
    if (byId('dialog-shade').style.display !== 'block') return;
    var dialog = byId('dialog'), width = document.documentElement.clientWidth, height = document.documentElement.clientHeight;
    dialog.style.width = Math.min(460, width - 32) + 'px';
    byId('dialog-body').style.maxHeight = Math.max(100, height - 230) + 'px';
    dialog.style.left = Math.max(16, Math.round((width - dialog.offsetWidth) / 2)) + 'px';
    dialog.style.top = Math.max(16, Math.round((height - dialog.offsetHeight) / 2)) + 'px';
  }
  function openDialog(title, body, action, button) { byId('dialog-cancel').textContent = tr(action ? 'Cancel' : 'Close'); byId('dialog-title').textContent = tr(title); byId('dialog-body').innerHTML = body; byId('dialog-error').innerHTML = ''; byId('dialog-confirm').textContent = tr(button || 'Confirm'); byId('dialog-confirm').disabled = false; byId('dialog-confirm').style.display = action ? '' : 'none'; modalAction = action; byId('dialog-shade').style.display = 'block'; positionDialog(); var input = byId('dialog-body').querySelector('input'); if (input) { input.focus(); input.select(); } }
  function closeDialog() { if (busy) return; if (batchIcons) batchIcons.off = true; byId('dialog-shade').style.display = 'none'; modalAction = null; }
  function loadBatchIcons() {
    if(batchIcons)batchIcons.off=true;var list=byId('dialog-body').querySelector('.batch-items');if(!list)return;
    var bounds=list.getBoundingClientRect(),images=list.querySelectorAll('img[data-src]'),pending=[],j,rect;
    for(j=0;j<images.length;j++){rect=images[j].parentNode.parentNode.getBoundingClientRect();if(rect.bottom>=bounds.top-43&&rect.top<=bounds.bottom+43)pending.push(images[j]);}
    batchIcons=feedIcons(pending,function(){return byId('dialog-shade').style.display==='block'&&!!list.parentNode;});
  }
  function quantity(max, preference) {
    var extra = preference ? '<label class="always-max-frame" title="'+label('Default to maximum quantity when opening a transfer or sale. Saved for this account.')+'"><input id="always-max" type="checkbox"' + (state.alwaysMax ? ' checked' : '') + '><span class="check-mark" aria-hidden="true"></span><span>'+label('Always Max')+'</span></label>' : '';
    return window.AionQuantity.markup(max, preference && state.alwaysMax ? max : 1, extra, money);
  }
  function quantityValue(max) { var value = window.AionQuantity.value(byId('quantity'), max); if (!value) byId('dialog-error').textContent = fmt('Quantity must be between 1 and {count}.', {count:money(max)}); return value; }
  function bindMax(max) {
    byId('dialog-body').querySelector('label[for="quantity"]').textContent = fmt('Quantity · maximum {count}', {count:money(max)});
    byId('max-quantity').textContent = tr('Max');
    window.AionQuantity.bindMax(byId('quantity'), byId('max-quantity'), max);
    if(byId('always-max'))byId('always-max').onchange=function(){if(busy){this.checked=!!state.alwaysMax;return;}if(this.checked)byId('max-quantity').onclick();this.disabled=true;act({action:'preference',alwaysMax:this.checked?'1':'0'},true);};
  }
  function transfer(i, target) {
    if (i.reserved) return;
    var options = '', j, s;
    for (j = 0; j < state.storages.length; j++) { s = state.storages[j]; if (s.id !== i.source && (s.id !== 125 || i.marketTransferable && +i.volume<=state.volumeLimit-state.volume)) options += '<option value="' + s.id + '">' + label(s.name) + '</option>'; }
    openDialog('Transfer Item', itemHeader(i) + quantity(i.quantity,true) + '<label for="target">'+label('Destination')+'</label><select id="target">' + options + '</select>', function () { var q = quantityValue(i.quantity); if (q) act({ action: 'transfer', source: i.source, target: byId('target').value, object: i.object, quantity: q }); }, 'Transfer');
    if (target != null) byId('target').value = String(target); bindMax(i.quantity);
  }
  function transferSelected(target) {
    var items = selectedItems(), options = '', rows = '', entries = [], allTradeable, j, s, source = storage;
    if (busy || !items.length) return;
    allTradeable=canEnterMarket(items);
    for (j = 0; j < items.length; j++) { entries.push(items[j].object + ':' + items[j].quantity); rows += '<div class="batch-row">' + image(items[j],true) + '<strong class="quality-' + html(items[j].quality) + '">' + html(name(items[j], true)) + '</strong><small>× ' + money(items[j].quantity) + '</small></div>'; }
    for (j = 0; j < state.storages.length; j++) { s = state.storages[j]; if (s.id !== source && (s.id !== 125 || allTradeable)) options += '<option value="' + s.id + '">' + label(s.name) + '</option>'; }
    openDialog('Transfer Selected Items', '<p class="batch-summary">' + html(fmt('{stacks} from {storage} · Full stacks', {stacks:stackLabel(items.length), storage:tr(currentStorage().name)})) + '</p><div class="batch-items">' + rows + '</div><label for="target">'+label('Destination')+'</label><select id="target">' + options + '</select><p class="batch-summary" style="margin-top:12px">'+label('All selected items transfer together. Storage restrictions and capacity apply.')+'</p>', function () { act({action:'transferBatch', source:source, target:byId('target').value, items:entries.join(',')}); }, 'Transfer All');
    byId('dialog-body').querySelector('.batch-items').onscroll=loadBatchIcons;loadBatchIcons();
    if (target != null) { byId('target').value = String(target); if (byId('target').value !== String(target)) { byId('dialog-error').textContent = tr('A selected item cannot enter this warehouse.'); byId('dialog-confirm').disabled = true; byId('target').onchange = function () { byId('dialog-error').innerHTML = ''; byId('dialog-confirm').disabled = !this.value; }; } }
  }
  function demandAt(price) { var d=state.selected,count=0,i; if(d) for(i=0;i<d.book.length;i++) if(d.book[i].side==='B' && d.book[i].price>=price) count+=bookQuantity(d.book[i]); return count; }
  function priceOptions(d,sale) { var out = '', i, count; for (i = 0; i < d.levels.length; i++) { count=sale ? demandAt(d.levels[i]) : stockAt(d.levels[i]); out += '<option value="' + d.levels[i] + '">' + html(amount(d.levels[i])) + ' · ' + html(count ? fmt(sale ? '{count} waiting buyers' : '{count} available to buy', {count:money(count)}) : tr(sale ? 'List for sale' : 'Preorder')) + '</option>'; } return out; }
  function buy() {
    var d = state.selected; if (!d || d.variant !== query.variant) return; var max = d.maxQuantity;
    openDialog('Buy / Place Order', itemHeader(d) + '<label for="unit-price">'+label('Unit price')+'</label><select id="unit-price">' + priceOptions(d) + '</select>' + quantity(max) + '<div class="cost">'+label('Reserved Kinah')+'<strong id="total-cost"></strong></div><p style="font-size:14px;color:#b9cbd5;margin-top:9px">'+label('Stock fills immediately at this price or lower. Collect purchased items beside the order in My Orders. Any remainder waits as a preorder.')+'</p>', function () { var q = quantityValue(max), price = +byId('unit-price').value; if (!q) return; if (q * price > state.balance) { byId('dialog-error').textContent = tr('Not enough Kinah in Market Warehouse.'); return; } act({ action: 'buy', variant: query.variant, quantity: q, price: price }); }, 'Buy / Place Order');
    byId('unit-price').value = selectedPrice; bindMax(max); bindCost(false);
  }
  function sell(i) {
    var d = state.selected, max = Math.min(i.quantity, d.maxQuantity);
    var highest=0,j; for(j=0;j<d.book.length;j++) if(d.book[j].side==='B' && bookQuantity(d.book[j])>0 && d.levels.indexOf(d.book[j].price)>=0) highest=Math.max(highest,d.book[j].price); if(highest) selectedPrice=highest;
    openDialog('Register Sale', itemHeader(i) + '<label for="unit-price">'+label('Unit price')+'</label><select id="unit-price">' + priceOptions(d,true) + '</select>' + quantity(max,true) + '<div class="cost">'+label('Proceeds after tax')+'<strong id="total-cost"></strong></div><p style="font-size:14px;color:#b9cbd5;margin-top:9px">'+label('Waiting buyers fill immediately at this price or higher. Unsold items stay listed. Collect Kinah beside the order in My Orders. High-value listings enter a 15-minute registration queue.')+'</p>', function () { var q = quantityValue(max); if (q) act({ action: 'sell', object: i.object, variant: i.variant, quantity: q, price: byId('unit-price').value }); }, 'Register Sale');
    byId('unit-price').value = selectedPrice; bindMax(max); bindCost(true);
  }
  function bindCost(sale) { function calculate() { var price = +byId('unit-price').value, qty = +byId('quantity').value, total = price * qty, rate = state.returnPercent === 84.5 ? 845 : 650; if(sale) total = Math.floor(total / 1000) * rate + Math.floor((total % 1000) * rate / 1000); byId('total-cost').textContent = amount(total); var available=sale ? demandAt(price) : stockAt(price), immediate=Math.min(available, Math.max(0,qty)); byId('dialog-confirm').textContent = tr(sale ? price>=20000000000 ? 'Queue Sale' : immediate>=qty ? 'Sell Now' : immediate ? 'Sell + List Rest' : 'List for Sale' : immediate>=qty ? 'Buy Now' : immediate ? 'Buy + Preorder Rest' : 'Place Buy Order'); } byId('quantity').oninput = calculate; byId('unit-price').onchange = calculate; calculate(); }
  function details(i) {
    var text = itemHeader(i) + '<p style="margin-top:12px">' + html(fmt('Lv. {level}', {level:i.level})) + ' · ' + label(gameTerm(i.race)) + ' · ' + i.volume / 10 + ' VT</p>', j, k;
    if (i.damage) text += '<p>' + html(fmt('Weapon damage {damage}', {damage:i.damage})) + '</p>'; if (i.sockets) text += '<p>' + html(fmt('Manastone sockets {count}', {count:i.sockets})) + '</p>';
    for (j = 0; j < i.stats.length; j++) text += '<p>' + label(i.stats[j].name) + ' ' + html(i.stats[j].value) + '</p>';
    for (k in (i.attributes || {})) if (i.attributes.hasOwnProperty(k)) text += '<p>' + label(k) + ': ' + html(i.attributes[k]) + '</p>';
    openDialog('Item Details', text, null); byId('dialog-cancel').textContent = tr('Close');
  }
  function renderOrders() {
    var filter = byId('order-filter').value, text = '', i, o;
    for (i = 0; i < state.orders.length; i++) {
      o = state.orders[i]; if (o.state !== 'OPEN' && o.state !== 'QUEUED' && !(o.collectQuantity > 0 || o.collectGross > 0)) continue;
      if (filter === 'B' || filter === 'S') { if (o.side !== filter) continue; } else if (filter === 'open' && o.state !== 'OPEN' && o.state !== 'QUEUED') continue;
      text += '<div class="table-row order-row">' + image(o) + '<strong>' + html(orderName(o)) + '</strong><span class="state ' + o.state + '">' + label(o.side === 'B' ? 'Buy order' : 'Sale listing') + ' · ' + label(stateName(o.state)) + '</span><small>' + html(fmt('{remaining} remaining / {total} · {amount} each', {remaining:money(o.remaining), total:money(o.quantity), amount:amount(o.price)})) + (o.state === 'QUEUED' ? ' · ' + html(fmt('Registers {date}', {date:date(o.available_at)})) : ' · ' + date(o.created_at)) + '</small>' + '<div class="order-actions">' + (o.collectQuantity > 0 || o.collectGross > 0 ? '<button class="primary" data-collect="' + o.id + '">' + html(o.side === 'B' ? fmt('Collect Items ({count})', {count:money(o.collectQuantity)}) : tr('Collect Kinah')) + '</button>' : '<span>' + label(o.state === 'FILLED' ? 'Collected' : o.state === 'CANCELLED' ? 'Nothing to collect' : 'Awaiting trades') + '</span>') + (o.state === 'OPEN' || o.state === 'QUEUED' ? '<button data-cancel="' + o.id + '">'+label('Cancel')+'</button>' : '') + '</div>' + (o.collectGross > 0 ? '<small class="claim-info">' + html(fmt('{amount} ready after tax', {amount:amount(netProceeds(o.collectGross))})) + '</small>' : '') + '</div>';
    }
    var changed = setMarkup('orders-list', text || '<p class="empty-list">'+label('No orders to display.')+'</p>');
    pagination('order-pagination','orderPage',state.orderPage,state.orderTotal);
    if (!changed) return;
    var claims=byId('orders-list').querySelectorAll('[data-collect]');
    for(i=0;i<claims.length;i++) claims[i].onclick=function(){act({action:'collect',order:this.getAttribute('data-collect')});};
    var buttons = byId('orders-list').querySelectorAll('[data-cancel]');
    for (i = 0; i < buttons.length; i++) buttons[i].onclick = function () { var id = +this.getAttribute('data-cancel'); openDialog('Cancel Order', '<p>'+label('Remaining items or reserved Kinah return to Market Warehouse.')+'</p>', function () { act({ action: 'cancel', order: id }); }, 'Cancel Order'); };
  }
  function renderHistory() {
    var filter = byId('history-filter').value, text = '', i, t;
    if (filter === 'collections') { for (i = 0; i < state.collections.length; i++) { t = state.collections[i]; text += '<div class="table-row"><strong>' + html(fmt('Collected {amount}', {amount:amount(t.net)})) + '</strong><small>' + html(fmt('Sale proceeds {gross} · Market tax {tax}', {gross:money(t.gross), tax:money(t.gross - t.net)})) + '</small><small>' + date(t.collected_at) + '</small></div>'; } }
    else { for (i = 0; i < state.history.length; i++) { t = state.history[i]; if (filter !== 'all' && t.direction !== filter) continue; text += '<div class="table-row">' + image(t) + '<strong>' + html(orderName(t)) + '</strong><span class="state">' + label(t.direction) + ' · ' + money(t.quantity) + '</span><small>' + html(fmt('{unit} each · {total} total · {date}', {unit:amount(t.unit_price), total:amount(t.unit_price * t.quantity), date:date(t.traded_at)})) + '</small></div>'; } }
    setMarkup('history-list', text || '<p class="empty-list">'+label('No completed trades to display.')+'</p>');
    pagination('history-pagination','historyPage',state.historyPage,state.historyTotal);
  }
  function pagination(id, key, page, total) {
    var pages=Math.max(1,Math.ceil(total/50));
    if (!setMarkup(id,'<button class="page-back"'+(page<=1?' disabled':'')+'>‹</button><span>'+page+' / '+pages+'</span><button class="page-forward"'+(page>=pages?' disabled':'')+'>›</button>')) return;
    byId(id).querySelector('.page-back').onclick=function(){query[key]=page-1;refresh();};
    byId(id).querySelector('.page-forward').onclick=function(){query[key]=page+1;refresh();};
  }
  function renderNotifications() {
    var text='',i,o;
    for(i=0;i<state.notifications.length;i++) {
      o=state.notifications[i];
      text+='<div class="table-row">'+image(o)+'<strong>'+html(orderName(o))+'</strong><span class="state">'+(o.direction ? label(o.direction)+' · '+html(fmt('{count} items',{count:money(o.remaining)})) : html(fmt('Registration queue · {count} items',{count:money(o.remaining)})))+'</span><small>'+html(fmt('{amount} each · {date}', {amount:amount(o.price), date:o.direction ? date(o.available_at) : fmt('Registers {date}', {date:date(o.available_at)})}))+'</small><button '+(o.direction ? 'data-order-notification="'+o.order_id+'"' : 'data-notification="'+html(o.variant)+'"')+'>'+label(o.direction ? 'My Orders' : 'View Item')+'</button></div>';
    }
    if (!setMarkup('notifications-list',text||'<p class="empty-list">'+label('Purchase and sale notifications appear here when an order fills. High-value listings appear while awaiting registration.')+'</p>')) return;
    var buttons=byId('notifications-list').querySelectorAll('[data-notification]');
    for(i=0;i<buttons.length;i++) buttons[i].onclick=function(){document.querySelector('[data-view="market"]').onclick();chooseVariant(this.getAttribute('data-notification'));};
    buttons=byId('notifications-list').querySelectorAll('[data-order-notification]');
    for(i=0;i<buttons.length;i++) buttons[i].onclick=function(){query.orderFilter='all';query.orderPage=1;byId('order-filter').value='all';document.querySelector('[data-view="orders"]').onclick();fetchState(false,null,'activity');};
  }
  function orderName(o) { return (o.enchant ? '+' + o.enchant + ' ' : '') + (o.name || fmt('Item {id}', {id:o.item_id})) + (o.tempering ? ' · ' + fmt('Tempering +{level}', {level:o.tempering}) : ''); }
  function stateName(s) { return { OPEN: 'Active', FILLED: 'Completed', CANCELLED: 'Cancelled', QUEUED: 'Registration queue' }[s] || s; }
  function gameTerm(s) { var terms = { PC_ALL: 'All factions', GUN: 'Pistol', CANNON: 'Aethercannon', KEYBLADE: 'Aether Key', TAMPERING: 'Tempering Solution', ENCHANTMENT: 'Enchantment Stone', RB: 'Cloth', LT: 'Leather', CH: 'Chain', PL: 'Plate', CL: 'Costume', HEAD: 'Headwear', TORSO: 'Chest Armor', PANTS: 'Leg Armor', SHOES: 'Foot Armor', GLOVE: 'Gloves', SHOULDER: 'Shoulders', MULTISLOT: 'Costume' }; if(terms[s]) return terms[s]; var parts = String(s).split('_'), i; for (i = 0; i < parts.length; i++) parts[i] = terms[parts[i]] || parts[i].charAt(0) + parts[i].substring(1).toLowerCase(); return parts.join(' '); }
  var tabs = document.querySelectorAll('[data-storage]'), i;
  for (i = 0; i < tabs.length; i++) (function (button) {
    // Tabs, selection and search only change the view, so they stay usable while an action is processing.
    button.onclick = function () { if (storage === +button.getAttribute('data-storage')) return; diag.tab = button.textContent; diag.tabStart = Date.now(); diag.tabLast = 0; diag.tabIcons = 0; diag.gap = 0; var j; if (storagePanes[storage]) storagePanes[storage].scroll = byId('storage-grid').scrollTop; storage = +button.getAttribute('data-storage'); selectedObject = 0; selectedObjects = {}; for (j = 0; j < tabs.length; j++) tabs[j].className = tabs[j] === button ? 'active' : ''; if (state) renderStorage(); };
    button.ondragover = function (e) { e.preventDefault(); };
    button.ondrop = function (e) { e.preventDefault(); var target = +button.getAttribute('data-storage'), item = findObject(+e.dataTransfer.getData('text/plain')); if (target === storage || !item || busy) return; if (selectMode && selectedObjects[item.object]) transferSelected(target); else transfer(item, target); };
  }(tabs[i]));
  var views = document.querySelectorAll('[data-view]');
  for (i = 0; i < views.length; i++) views[i].onclick = function () { view = this.getAttribute('data-view'); var j; for (j = 0; j < views.length; j++) views[j].className = views[j] === this ? 'active' : ''; byId('market-view').style.display = view === 'market' ? '' : 'none'; byId('orders-view').style.display = view === 'orders' ? 'block' : 'none'; byId('history-view').style.display = view === 'history' ? 'block' : 'none'; byId('notifications-view').style.display = view === 'notifications' ? 'block' : 'none'; if(state) { renderDetail(); renderActivity(); } };
  byId('codex-link').onclick = function () { if (!busy) location.href = '/market/codex?session_id=' + session; };
  byId('warehouse-search').oninput = function () { warehouseQuery = this.value; if (state) renderStorage(); };
  byId('select-items').onclick = function () { if (!state) return; selectMode = !selectMode; selectedObjects = {}; renderStorageSelection(); };
  byId('select-all').onclick = function () { if (!state) return; var s = currentStorage(), i, count = selectedItems().length; for (i = 0; i < s.items.length && count < 300; i++) if (!s.items[i].reserved && !selectedObjects[s.items[i].object] && name(s.items[i]).toLowerCase().indexOf(warehouseQuery.toLowerCase()) >= 0) { selectedObjects[s.items[i].object] = true; count++; } renderStorageSelection(); if (count === 300) status('Selected 300 item stacks. Transfer these before selecting more.'); };
  byId('clear-selection').onclick = function () { selectedObjects = {}; renderStorageSelection(); };
  byId('clear-warehouse').onclick = function () { warehouseQuery = ''; byId('warehouse-search').value = ''; renderStorage(); byId('warehouse-search').focus(); };
  byId('search').oninput = function () { clearTimeout(searchTimer); searchTimer = setTimeout(function () { query.q = byId('search').value; browseAll(); query.page = 1; refreshCatalog(); }, 300); };
  byId('search').onkeydown = function (e) { if (e.keyCode === 13) { clearTimeout(searchTimer); query.q = this.value; browseAll(); query.page = 1; refreshCatalog(); } };
  byId('clear-search').onclick = function () { byId('search').value = ''; query.q = ''; query.page = 1; refreshCatalog(); byId('search').focus(); };
  byId('save-search').onclick=function(){var term=byId('search').value.replace(/^\s+|\s+$/g,'');if(!term){status('Enter an item name to save.',true);return;}act({action:'saveSearch',term:term});};
  byId('saved-searches').onclick=function(){var text='',i;for(i=0;i<state.savedSearches.length;i++)text+='<div class="saved-row"><button data-search="'+i+'">'+html(state.savedSearches[i].term)+'</button><button data-remove="'+i+'" title="'+label('Remove search')+'">×</button></div>';openDialog('Saved Searches',text||'<p>'+label('No saved searches. Enter an item name, then select ★.')+'</p>',null);var buttons=byId('dialog-body').querySelectorAll('[data-search]');for(i=0;i<buttons.length;i++)buttons[i].onclick=function(){query.q=state.savedSearches[+this.getAttribute('data-search')].term;query.category='All Items';query.sub='All';query.page=1;browseAll();byId('search').value=query.q;closeDialog();refreshCatalog();};buttons=byId('dialog-body').querySelectorAll('[data-remove]');for(i=0;i<buttons.length;i++)buttons[i].onclick=function(){act({action:'saveSearch',mode:'remove',term:state.savedSearches[+this.getAttribute('data-remove')].term});};};
  byId('filter').onchange = function () { query.filter = this.value; if(query.filter === 'changed') query.sort='change';query.page = 1; refreshCatalog(); };
  byId('item-filters').onsubmit = function (e) {
    e.preventDefault(); var ids=['min-level','max-level','min-price','max-price'], keys=['minLevel','maxLevel','minPrice','maxPrice'], j, value;
    for(j=0;j<ids.length;j++){ value=byId(ids[j]).value.replace(/^\s+|\s+$/g,''); if(value && !/^\d+$/.test(value)){status('Enter whole numbers for level and Kinah filters.',true);return;} }
    if((byId('max-level').value && +byId('min-level').value > +byId('max-level').value) || (byId('max-price').value && +byId('min-price').value > +byId('max-price').value)){status('Minimum must be less than or equal to maximum.',true);return;}
    for(j=0;j<ids.length;j++) query[keys[j]]=byId(ids[j]).value;
    query.quality=byId('quality').value;query.slot=byId('slot').value;query.page=1;browseAll();refreshCatalog();
  };
  byId('reset-filters').onclick=function(){var ids=['min-level','max-level','min-price','max-price'],keys=['minLevel','maxLevel','minPrice','maxPrice'],j;for(j=0;j<ids.length;j++){byId(ids[j]).value='';query[keys[j]]='';}query.quality='all';query.slot='All';byId('quality').value='all';byId('slot').value='All';query.filter='all';query.sort='name';query.q='';byId('search').value='';query.page=1;refreshCatalog();};
  byId('previous').onclick = function () { query.page = Math.max(1, query.page - 1); refreshCatalog(); };
  byId('next').onclick = function () { query.page++; refreshCatalog(); };
  byId('order-filter').onchange = function () { query.orderFilter = this.value; query.orderPage = 1; fetchState(false,null,'activity'); }; byId('history-filter').onchange = function () { query.historyFilter = this.value; query.historyPage = 1; fetchState(false,null,'activity'); }; byId('sort').onchange = function () { query.sort = this.value; query.page = 1; refreshCatalog(); };
  byId('refresh').onclick = refresh; byId('dialog-close').onclick = closeDialog; byId('dialog-cancel').onclick = function () { closeDialog(); this.textContent = tr('Cancel'); };
  byId('dialog-confirm').onclick = function () { if (modalAction && !busy) modalAction(); };
  // Kinah moves between a character storage and the Market Warehouse wallet; on the Market tab that is the Inventory.
  function kinahStorage() { var s = currentStorage(), i; if (s.id !== 125) return s; for (i = 0; i < state.storages.length; i++) if (state.storages[i].id === 0) return state.storages[i]; return s; }
  function transferKinah(direction) { var s = kinahStorage(), value = byId('kinah-amount').value; if (!/^\d+$/.test(value) || +value < 1) { status('Enter a Kinah amount.', true); return; } openDialog(direction === 'deposit' ? 'Deposit Kinah' : 'Withdraw Kinah', '<div class="cost">'+label('Kinah')+'<strong>' + money(+value) + '</strong></div><p style="margin-top:12px">' + label(s.name) + ' ' + label(direction === 'deposit' ? '→ Market Warehouse' : '← Market Warehouse') + '</p>', function () { act({ action: 'kinah', direction: direction, source: s.id, quantity: value }); }, direction === 'deposit' ? 'Deposit' : 'Withdraw'); }
  document.getElementsByTagName('footer')[0].onclick = function () { diag.on = !diag.on; diag.gap = 0; diagShow(); };
  byId('deposit-kinah').onclick = function () { transferKinah('deposit'); }; byId('withdraw-kinah').onclick = function () { transferKinah('withdraw'); };
  document.onkeydown = function (e) { if (e.keyCode === 27) { if (byId('dialog-shade').style.display === 'block') closeDialog(); else if (query.variant) { cancelRead(); query.variant = ''; state.selected = null; renderDetail(); markCatalogSelection(); } } };
  window.onresize = function () { if (state) { renderDetail(); if (storagePanes[storage]) loadStorageIcons(storagePanes[storage]); } positionDialog(); };
  for (i = 0; i < tabs.length; i++) tabs[i].className = +tabs[i].getAttribute('data-storage') === storage ? 'active' : '';
  refresh();
}());
