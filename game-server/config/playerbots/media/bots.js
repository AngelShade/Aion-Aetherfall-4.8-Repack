(function () {

  'use strict';

  var state = null, busy = false, selectedDetails = {}, session = '', requestSerial = 0, spacingDrafts = {};

  var currentView = 'party', selectedBot = null, botTab = 'overview', rosterPage = 0, pageSize = 6, inspecting = false, careDrafts = {}, removalConfirmation = null, subTabs = {}, fieldDrafts = {}, teamDraft = {}, teamConfirmation = null;
  var cardFaces = {}, cardMore = {}, cardTurns = {}, cardScroll = {};
  var stateRevision = 0, receivedAt = 0, renderedViews = {}, partyRevision = -1;
  var mountedDetail = null, detailCache = {}, detailHistory = [], rosterView = 'characters';

  var itemSelections = {}, helpAbandon = '';

  var parts = location.search.substring(1).split('&');

  for (var p = 0; p < parts.length; p++) {

    var pair = parts[p].split('=');

    if (pair[0] === 'help_requests') currentView = 'quests';
    if (pair[0] === 'session_id') { try { session = decodeURIComponent((pair[1] || '').replace(/\+/g, ' ')); } catch (ignore) {} }

  }

  function byId(id) { return document.getElementById(id); }

  function node(tag, text, className) { var el = document.createElement(tag); if (text != null) el.textContent = text; if (className) el.className = className; return el; }

  function label(value) { if (value === 'RIDER') return 'Aethertech'; return String(value || '').toLowerCase().replace(/_/g, ' ').replace(/\b[a-z]/g, function (c) { return c.toUpperCase(); }); }

  function ticketRecruit(growth) { return !!growth && growth.origin === 'GACHA'; }

  function originLabel(temporary, world, growth) { return world ? 'World Bot' : ticketRecruit(growth) ? 'Ticket Recruit' : temporary ? 'Temporary Bot' : 'Account Alt'; }

  function rarityClass(growth) { return growth ? ' rarity-' + growth.rarity.toLowerCase() : ''; }

  function rankLine(growth) { return label(growth.rank) + (growth.needed ? ' (' + growth.learning + ' / ' + growth.needed + ' Learning XP)' : ' (fully trained)'); }

  function mistakeLine(bot) { return bot.growth.mistakeChance + '% mistake chance \u00b7 ' + (bot.mistakes || 0) + ' this session'; }

  function originBadge(parent, temporary, world, growth) { parent.appendChild(node('span', originLabel(temporary, world, growth), 'companion-origin' + (world ? ' world' : temporary ? ' temporary' : ' alt'))); }

  function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); }

  function notice(text, error) { byId('notice').textContent = text; byId('notice').className = error ? 'error' : ''; document.querySelector('footer').className = error ? 'error' : ''; }
  // Class medallion: class initials in a ring coloured by combat role (no role: a plain ring for human members).
  // Client UI art served in memory by the native icon bridge (native-icon-bridge/prepare_ui_art.py); nothing is extracted.
  var ART = '/market/companions/media/art/';
  // The class's own emblem from the client in a ring coloured by combat role; initials if the image is missing.
  function crest(playerClass, role) {
    var words = label(playerClass).split(' '), letters = words.length > 1 ? words[0].charAt(0) + words[1].charAt(0) : words[0].substring(0, 2);
    var ring = node('span', null, 'crest ' + (role ? 'role-' + String(role).toLowerCase() : 'human')), emblem = node('img');
    emblem.alt = ''; emblem.src = ART + 'class-' + String(playerClass || '').toLowerCase() + '.png'; ring.title = label(playerClass);
    emblem.onerror = function () { this.parentNode.appendChild(node('span', letters || '?', 'crest-letters')); this.style.display = 'none'; };
    ring.appendChild(emblem); return ring;
  }
  // One shared illustrated setting across the window, selected companion and collection.
  function headerArt(header, character) { var art = node('img', null, 'bot-art'); art.alt = ''; art.src = '/market/companions/media/cards/castle-background.png?v=2'; art.onerror = function () { this.style.display = 'none'; }; header.appendChild(art); header.appendChild(node('div', null, 'bot-shade')); if (character) header.appendChild(cardPortrait(character.playerClass,'header-class-art',character.gender)); }

  var sectionNames = {
    behavior: [['combat','Combat'],['automation','Quest automation'],['spacing','Spacing'],['focus','Focus']],
    equipment: [['profile','Character'],['inventory','Inventory'],['transfer','Transfer items'],['build','Stigma build'],['saved','Saved gear'],['policy','Equipment rules'],['warehouse','Private warehouse'],['legion','Legion storage']],
    mail: [['inbox','Inbox'],['send','Send']], quests: [['journal','Journal'],['requests','Requests']],
    care: [['maintenance','Maintenance'],['budget','Spending budget'],['benefits','Benefits'],['recycle','Recycle']]
  };

  function captureDrafts() {
    var fields = document.querySelectorAll('[data-draft-key]');
    for (var i = 0; i < fields.length; i++) {
      var el = fields[i], owner = el.getAttribute('data-draft-owner'), key = el.getAttribute('data-draft-key');
      if (!fieldDrafts[owner]) fieldDrafts[owner] = {};
      if (String(el.value) === el.getAttribute('data-draft-base')) delete fieldDrafts[owner][key];
      else fieldDrafts[owner][key] = String(el.value);
    }
  }

  function draftField(el, bot, key) {
    el.setAttribute('data-draft-owner', bot.id); el.setAttribute('data-draft-key', key);
    el.setAttribute('data-draft-base', String(el.value));
    if (!el.id) el.id = 'draft-' + key;
    var draft = fieldDrafts[bot.id], value = draft && draft[key];
    if (value != null) {
      if (value === String(el.value)) delete draft[key];
      else if (el.tagName !== 'SELECT') el.value = value;
      else for (var i = 0; i < el.options.length; i++) if (el.options[i].value === value) { el.value = value; break; }
    }
    return el;
  }

  function captureFocus() {
    var el = document.activeElement, start = null, end = null;
    try { start = el && el.selectionStart; end = el && el.selectionEnd; } catch (ignore) {}
    return el ? { id: el.id, key: el.getAttribute('data-focus-key'), start: start, end: end } : null;
  }

  function restoreFocus(saved) {
    if (!saved) return;
    var el = saved.id && byId(saved.id), fields;
    if (!el && saved.key) {
      fields = document.querySelectorAll('[data-focus-key]');
      for (var i = 0; i < fields.length; i++) if (fields[i].getAttribute('data-focus-key') === saved.key && visible(fields[i]) && !fields[i].disabled) { el = fields[i]; break; }
    }
    if (el && !el.disabled && visible(el)) {
      el.focus(); if (el.setSelectionRange && saved.start != null) try { el.setSelectionRange(saved.start, saved.end); } catch (ignore) {}
    }
  }

  function visible(el) {
    for (var p = el; p && p.nodeType === 1; p = p.parentNode) if (p.style.display === 'none') return false;
    return !!el.getClientRects().length;
  }

  function subPanel(parent, key, title) {
    var el = node('section', null, 'control-panel'); el.id = 'bot-section-' + key;
    el.setAttribute('data-section', key); el.setAttribute('role', 'tabpanel');
    el.setAttribute('aria-labelledby', 'bot-subtab-' + key); el.appendChild(node('h3', title, 'section-toolbar'));
    parent.appendChild(el); return el;
  }

  function renderSubTabs(body, bot) {
    var names = sectionNames[botTab]; if (!names || !body.querySelector('[data-section]')) return;
    var selected = subTabs[botTab] || names[0][0], tabs = node('nav', null, 'tabs subtabs');
    tabs.id = 'bot-subtabs'; tabs.setAttribute('role', 'tablist'); tabs.setAttribute('aria-label', label(botTab) + ' sections');
    if (!byId('bot-section-' + selected)) selected = names[0][0];
    body.insertBefore(tabs, body.firstChild);
    for (var i = 0; i < names.length; i++) (function (key, title) {
      var panel = byId('bot-section-' + key); if (!panel) return;
      var tab = localButton(tabs, title, function () { captureDrafts(); subTabs[botTab] = key; chooseSubTab(body, key); tab.focus(); if (key === 'benefits') request(null); });
      tab.id = 'bot-subtab-' + key; tab.setAttribute('role','tab'); tab.setAttribute('aria-controls',panel.id);
    }(names[i][0], names[i][1]));
    chooseSubTab(body, selected);
  }

  function chooseSubTab(body, key) {
    var panels = body.querySelectorAll('[data-section]'), tabs = body.querySelectorAll('#bot-subtabs button');
    for (var i = 0; i < panels.length; i++) panels[i].style.display = panels[i].getAttribute('data-section') === key ? 'block' : 'none';
    for (var t = 0; t < tabs.length; t++) {
      var active = tabs[t].getAttribute('aria-controls') === 'bot-section-' + key;
      tabs[t].className = active ? 'selected' : ''; tabs[t].setAttribute('aria-selected',active ? 'true' : 'false'); tabs[t].tabIndex = active ? 0 : -1;
    }
  }

  function compactHelp(body) {
    var hints = body.querySelectorAll('p.hint'), groups = {};
    for (var i = 0; i < hints.length; i++) if (hints[i].textContent.length > 190 && hints[i].className.indexOf('availability-note') === -1) {
      var hint = hints[i], parent = hint.parentNode;
      while (parent !== body && parent.className.indexOf('control-panel') === -1) parent = parent.parentNode;
      var key = 'help-' + botTab + '-' + (parent.id || 'main'), group = groups[key];
      if (!group) {
        group = { section: node('section',null,'section-help'), toggle: node('button','Details and rules','disclosure-toggle'), text: node('div') };
        group.toggle.type = 'button'; group.toggle.setAttribute('data-local-control','true');
        group.toggle.setAttribute('aria-expanded',selectedDetails[key] ? 'true' : 'false'); group.text.style.display = selectedDetails[key] ? 'block' : 'none';
        group.section.appendChild(group.toggle); group.section.appendChild(group.text); parent.appendChild(group.section); groups[key] = group;
        (function (button,text,id) { button.onclick = function () { selectedDetails[id] = !selectedDetails[id]; text.style.display = selectedDetails[id] ? 'block' : 'none'; button.setAttribute('aria-expanded',selectedDetails[id] ? 'true' : 'false'); }; }(group.toggle,group.text,key));
      }
      group.text.appendChild(hint);
    }
  }

  function nativeItemLink(item) {
    if (!item.itemId || !/^[0-9]+$/.test(String(item.itemId))) return '';
    // The installed ItemInfo hover bridge consumes href/title, as Wardrobe does.
    return item.tooltip ? 'nc://aion.ItemInfo/ItemTooltip?' + item.tooltip : '';
  }

  function itemIcon(item, className) {
    var link = nativeItemLink(item), icon = node(link ? 'a' : 'span',null,className || 'item-icon');
    if (link) { icon.href = link; icon.title = link; icon.onclick = function (event) { if (event) event.preventDefault(); return false; }; }
    if (item.itemId && /^[0-9]+$/.test(String(item.itemId))) {
      var picture = node('img'); picture.alt = ''; picture.src = '/shop/media/icons/' + encodeURIComponent(item.itemId) + '.png';
      picture.onerror = function () { this.style.display = 'none'; }; icon.appendChild(picture);
    } else icon.appendChild(node('span','?', 'unknown-item'));
    return icon;
  }

  function itemHeading(parent,item) {
    var heading = node('div',null,'item-title'); heading.appendChild(itemIcon(item));
    heading.appendChild(node('span',item.name + ' \u00d7' + item.count)); parent.appendChild(heading);
    if (item.equipped) parent.appendChild(node('span','Equipped','status-pill'));
    if (item.quality) parent.appendChild(node('p',label(item.quality) + ' \u00b7 Item level ' + item.itemLevel,'item-meta'));
    if (item.enchant || item.tempering) parent.appendChild(node('p','Enchant +' + (item.enchant || 0) + ' \u00b7 Tempering +' + (item.tempering || 0),'item-meta'));
    if (item.fusionItemId) parent.appendChild(node('p','Fused with ' + (item.fusionName || 'an unavailable item'),'item-meta'));
    if (item.godstoneId) parent.appendChild(node('p','Godstone: ' + (item.godstoneName || 'name unavailable'),'item-meta'));
    var bonusSets = [['bonusModifiers','Random bonuses'],['fusionModifiers','Fused bonuses']];
    for (var bs = 0; bs < bonusSets.length; bs++) {
      var mods = item[bonusSets[bs][0]] || [], lines = [];
      for (var m = 0; m < mods.length; m++) { var stat = mods[m], prefix = stat.operation === 'subtract' ? '-' : stat.operation === 'set' ? 'Set to ' : stat.operation === 'absolute' ? 'Absolute ' : '+';
        var statNames = {MAXHP:'Maximum HP',MAXMP:'Maximum MP',BOOST_MAGICAL_SKILL:'Magic boost',BOOST_CASTING_TIME:'Casting speed',BOOST_HATE:'Enmity boost'};
        lines.push((statNames[stat.name] || label(stat.name)) + ' ' + prefix + stat.value + (stat.operation === 'rate' ? '%' : '') + (stat.conditional ? ' (conditional)' : ''));
      }
      if (lines.length) parent.appendChild(node('p',bonusSets[bs][1] + ': ' + lines.join(', '),'item-meta'));
      else if (item[bs === 0 ? 'bonusStatsId' : 'fusionBonusStatsId']) parent.appendChild(node('p',bonusSets[bs][1] + ': details unavailable','item-meta'));
    }
    var stoneSets = [['manastones','Manastones'],['fusionStones','Fused manastones']];
    for (var s = 0; s < stoneSets.length; s++) { var stones = item[stoneSets[s][0]] || []; if (stones.length) { var names = []; for (var n = 0; n < stones.length; n++) names.push(stones[n].name + ' (slot ' + (Number(stones[n].slot) + 1) + ')'); parent.appendChild(node('p',stoneSets[s][1] + ': ' + names.join(', '),'item-meta')); } }

  }

  function itemGrid(parent,bot,items,key,title,renderer) {
    var box = node('section',null,'cube-panel'), grid = node('div',null,'cube-grid'), details = node('div',null,'cube-details');
    var choiceKey = bot.id + '|' + key, selected = itemSelections[choiceKey], exists = false;
    for (var i = 0; i < items.length; i++) if (String(items[i].id) === String(selected)) exists = true;
    if (!exists) selected = items.length ? items[0].id : null; itemSelections[choiceKey] = selected;
    box.appendChild(node('h3',title)); grid.setAttribute('aria-label',title); grid.setAttribute('data-grid',key);
    for (var i = 0; i < items.length; i++) (function(item) {
      var tile = node('button',null,'cube-slot' + (String(item.id) === String(selected) ? ' selected' : ''));
      if (nativeItemLink(item)) tile.title = nativeItemLink(item);
      tile.type = 'button'; tile.setAttribute('data-local-control','true'); tile.setAttribute('data-item-id',item.id);
      tile.setAttribute('data-focus-key','cube|' + bot.id + '|' + key + '|' + item.id);
      tile.setAttribute('aria-label',item.name + ', ' + item.count); tile.setAttribute('aria-pressed',String(item.id) === String(selected) ? 'true' : 'false');
      var icon = itemIcon(item,'cube-icon');
      // Keep the native hover anchor and selection control separate, never nest interactive controls.
      var cell = node('div',null,'cube-cell'); cell.appendChild(icon); tile.appendChild(node('span',item.count > 1 ? item.count : '', 'stack-count')); cell.appendChild(tile);
      tile.onclick = function () { captureDrafts(); itemSelections[choiceKey] = item.id; renderSelection(); };
      grid.appendChild(cell);
      if (String(item.id) === String(selected)) renderer(details,bot,item);
    }(items[i]));
    if (!items.length) grid.appendChild(node('p','Empty','empty-cube'));
    box.appendChild(grid); box.appendChild(details); parent.appendChild(box); return box;
  }

  // Character-panel layout of the native player info dialog: weapons and wings on top, armour left, accessories right.
  var DOLL = [['MAIN_HAND','rhand',0,0],['POWER_SHARD_RIGHT','battery',62,5],['WINGS','wing',134,0],['POWER_SHARD_LEFT','battery',215,5],['SUB_HAND','lhand',268,0],
    ['HELMET','helmet',0,62],['EARRINGS_LEFT','learring',0,110],['TORSO','armor',0,158],['PANTS','pants',0,206],['RING_LEFT','lring',0,254],['BOOTS','shoes',0,302],
    ['NECKLACE','amulet',268,62],['EARRINGS_RIGHT','rearring',268,110],['SHOULDER','shoulder',268,158],['GLOVES','glove',268,206],['RING_RIGHT','rring',268,254],['WAIST','belt',268,302],
    ['MAIN_OFF_HAND','switch',62,358],['PLUME','feather',138,362],['SUB_OFF_HAND','switch',206,358]];
  function renderProfile(parent,bot,items) {
    var layout = node('div',null,'character-profile'), doll = node('div',null,'paperdoll'), portrait = node('div',null,'portrait'), details = node('div',null,'doll-details');
    var choiceKey = bot.id + '|equipped', selected = itemSelections[choiceKey], chosen = null;
    portrait.appendChild(cardPortrait(bot.playerClass,'profile-class-art',bot.gender));
    portrait.appendChild(crest(bot.playerClass, bot.role)); portrait.appendChild(node('strong',bot.name));
    portrait.appendChild(node('span','Level ' + bot.level + ' ' + label(bot.playerClass),'portrait-class'));
    portrait.appendChild(node('span',label(bot.role) + ' · ' + items.length + ' equipped','portrait-role')); doll.appendChild(portrait);
    for (var n = 0; n < DOLL.length; n++) (function (slotName, art, x, y) {
      var present = null, small = art === 'battery' || art === 'feather';
      for (var i = 0; i < items.length; i++) if ((items[i].equippedSlots || []).indexOf(slotName) !== -1) present = items[i];
      var slot = node('div',null,'doll-slot' + (small ? ' small' : '') + (present ? ' filled' : '')); slot.style.left = x + 'px'; slot.style.top = y + 'px';
      slot.title = label(slotName);
      if (!present) { var empty = node('img',null,'doll-empty'); empty.alt = ''; empty.src = ART + 'slot-' + art + '.png'; slot.appendChild(empty); doll.appendChild(slot); return; }
      if (String(present.id) === String(selected)) { chosen = present; slot.className += ' selected'; }
      slot.appendChild(itemIcon(present,'doll-icon'));
      var pick = node('button',null,'doll-pick'); pick.type = 'button'; pick.setAttribute('data-local-control','true'); pick.setAttribute('data-item-id',present.id);
      pick.setAttribute('data-focus-key','doll|' + bot.id + '|' + slotName); pick.setAttribute('aria-label',label(slotName) + ': ' + present.name);
      if (nativeItemLink(present)) pick.title = nativeItemLink(present);
      pick.onclick = function () { captureDrafts(); itemSelections[choiceKey] = present.id; renderSelection(); };
      slot.appendChild(pick); doll.appendChild(slot);
    }(DOLL[n][0], DOLL[n][1], DOLL[n][2], DOLL[n][3]));
    if (!chosen && items.length) { chosen = items[0]; itemSelections[choiceKey] = chosen.id; var first = doll.querySelector('[data-item-id="' + chosen.id + '"]'); if (first) first.parentNode.className += ' selected'; }
    if (chosen) renderItem(details,bot,chosen);
    else details.appendChild(node('p','Nothing equipped yet. Equip items from the Inventory section.','empty-state'));
    layout.appendChild(doll); layout.appendChild(details); parent.appendChild(layout);
    var other = items.filter(function (item) {
      return !(item.equippedSlots || []).some(function (slot) {
        return DOLL.some(function (position) { return position[0] === slot; });
      });
    });
    if (other.length) itemGrid(parent,bot,other,'other-equipment','Other equipped items',renderItem);
  }

  var ORDERS = [['attack','Attack','Attack your selected target',{action:'attack',name:'all'}],['follow','Follow','Follow you',{action:'order',name:'all',order:'FOLLOW'}],
    ['stay','Stay','Hold position',{action:'order',name:'all',order:'STAY'}],['guard','Guard','Guard this position',{action:'order',name:'all',order:'GUARD'}],
    ['passive','Passive','Stop attacking and follow without casting',{action:'order',name:'all',order:'PASSIVE'}],['summon','Summon','Summon every companion to you (out of combat)',{action:'summon',name:'all'}],
    ['pull','Pull','The tank pulls your selected target back to the party',{action:'pull'}],['mark-kill','Kill','Mark your selected target to kill first',{action:'mark',mark:'kill'}],
    ['mark-cc','Crowd control','Mark your selected target for sleep, paralysis or fear; nobody hits it',{action:'mark',mark:'cc'}],['mark-clear','Clear marks','Remove kill and crowd-control marks',{action:'mark',mark:'clear'}]];
  var GLYPHS = {attack:'⚔',follow:'➤',stay:'✋',guard:'⛨',passive:'☮',summon:'✧',pull:'⛓','mark-kill':'◎','mark-cc':'☾','mark-clear':'✖'};
  function renderOrders() {
    var parent = byId('party-orders'); if (!parent || !state) return; clear(parent);
    for (var i = 0; i < ORDERS.length; i++) {
      var order = button(parent,'',ORDERS[i][3],!state.active.length,'order'), icon = node('span', GLYPHS[ORDERS[i][0]], 'order-glyph ' + ORDERS[i][0]);
      order.title = ORDERS[i][1] + ': ' + ORDERS[i][2]; order.setAttribute('aria-label',ORDERS[i][1]); order.appendChild(icon);
    }
  }

  function metrics() {
    var parent = byId('party-metrics'); if (!parent || !state) return;
    clear(parent); var ready = 0, requests = 0;
    for (var i = 0; i < state.active.length; i++) { if (!state.active[i].dead && !state.active[i].closing) ready++; requests += (state.active[i].questions || []).length; }
    var values = [['Active companions',state.active.length + ' / ' + state.limit],['Alive companions',ready],['Quest requests',requests],['Recruitment slots',state.recruitRemaining == null ? 'Refresh to check' : state.recruitRemaining]];
    for (var n = 0; n < values.length; n++) { var card = node('div',null,'stat-card'); card.appendChild(node('span',values[n][0])); card.appendChild(node('strong',values[n][1])); parent.appendChild(card); }
  }

  function button(parent, text, values, disabled, className) {

    var el = node('button', text, className); el.type = 'button'; el.disabled = disabled || busy;

    el.setAttribute('data-action', values.action || '');
    el.setAttribute('data-focus-key', [values.action,values.name,values.setting,values.order,values.item,values.object,values.quest,values.answer,values.target,values.operation,values.team,values.member,values.group,values.second,values.preset].join('|'));

    if (values.operation) el.setAttribute('data-operation',values.operation);
    if (values.item != null) el.setAttribute('data-item',values.item);
    if (values.object != null) el.setAttribute('data-object',values.object);
    if (values.name) el.setAttribute('data-name', values.name);

    if (values.setting) el.setAttribute('data-setting', values.setting);

    if (values.order) el.setAttribute('data-order', values.order);

    el.onclick = function () { act(values); }; parent.appendChild(el); return el;

  }

  function select(parent, options, current) {

    var el = node('select');

    for (var i = 0; i < options.length; i++) { var opt = node('option', label(options[i])); opt.value = options[i]; el.appendChild(opt); }

    if (current) el.value = current; parent.appendChild(el); skinSelects(parent); return el;

  }

  function encode(values) { var encoded = []; for (var key in values) if (Object.prototype.hasOwnProperty.call(values, key)) encoded.push(encodeURIComponent(key) + '=' + encodeURIComponent(values[key])); return encoded.join('&'); }

  function request(values, followup) {

    if (busy) return;

    captureDrafts(); var focus = captureFocus(), original = values, recycleFlow = pendingRecycle, holdNotice = keepNotice; keepNotice = false;
    if (values) { values = {}; for (var key in original) if (Object.prototype.hasOwnProperty.call(original,key)) values[key] = original[key]; }
    busy = true; var serial = ++requestSerial; setBusy();

    var xhr = new XMLHttpRequest(), mutation = values !== null, finished = false, watchdog;

    var rosterArgs = {page:rosterPage, search:byId('roster-search').value, filter:currentView === 'roster' && rosterView === 'presets' ? 'all' : collectionFilter(), sort:byId('roster-sort').value};
    if (currentView === 'party' && botTab === 'care' && subTabs.care === 'benefits' && inspecting && selectedBot != null) rosterArgs.benefitBot = selectedBot;
    xhr.open(mutation ? 'POST' : 'GET', '/market/companions/' + (mutation ? 'action' : 'state?session_id=' + encodeURIComponent(session) + '&' + encode(rosterArgs)), true);
    xhr.timeout = 30000;
    if (mutation) { for (var arg in rosterArgs) if (Object.prototype.hasOwnProperty.call(rosterArgs,arg)) values[arg] = rosterArgs[arg]; values.session_id = session; values.request = state.request; xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded'); }

    function finish() { if (finished || serial !== requestSerial) return false; finished = true; window.clearTimeout(watchdog); busy = false; setBusy(); return true; }

    xhr.onload = function () {

      if (!finish()) return;

      var result;

      try { result = JSON.parse(xhr.responseText); } catch (ignore) { clearPendingRecruit(); if (state) state.request = ''; notice('Companions is unavailable. Refresh before trying again.', true); return; }

      if (!result || xhr.status !== 200 || result.error) {
        clearPendingRecruit(); if (state) state.request = ''; notice(result && result.error || 'Refresh Companions to reconnect.', true);
        // A refused action spends its nonce: fetch one fresh state now, under the same message.
        if (mutation && xhr.status === 400 && state) { keepNotice = true; request(null); }
        return;
      }

      if (!result || !Array.isArray(result.active) || !Array.isArray(result.roster) || !Array.isArray(result.quests) || !Array.isArray(result.classes) || !Array.isArray(result.roles) || typeof result.request !== 'string' || !result.request) { clearPendingRecruit(); if (state) state.request = ''; notice('Companion data is incomplete. Refresh before trying again.',true); return; }

      if (mutation && values.action === 'spacing') { for (var d = 0; d < result.active.length; d++) if (result.active[d].name === values.name && Number(result.active[d].ownerSpacing) === Number(values.ownerSpacing) && Number(result.active[d].attackSpacing) === Number(values.attackSpacing)) delete spacingDrafts[result.active[d].id]; }

      if (mutation && values.action === 'carebudget') { for (var d = 0; d < result.active.length; d++) if (result.active[d].name === values.name && String(result.active[d].reserve) === String(values.reserve) && String(result.active[d].dailyBudget) === String(values.dailyBudget)) delete careDrafts[result.active[d].id]; }

      receivedAt = new Date().getTime();
      if (followup && (!recycleFlow || pendingRecycle === recycleFlow)) { state = result; invalidateViews(); request(followup); return; }
      // Refresh is explicit. Keep an unchanged visible panel instead of rebuilding it.
      var fingerprint = viewPrint(result);
      if (!mutation && state && fingerprint === lastFingerprint) { state = result; invalidateViews(); quietUpdate(); advanceRecycling(); return; }
      lastFingerprint = fingerprint;
      state = result; invalidateViews(); render(); restoreFocus(focus); if (!holdNotice) notice(result.notice || 'Connected as ' + result.owner + '. Refresh when you want updated status.', false);
      if (mutation && values.action === 'helptrack' && window.AionObject && AionObject.HelpRequestTrack) AionObject.HelpRequestTrack();
      advanceRecycling();

    };

    function interrupted() { if (finish()) { clearPendingRecruit(); if (state) state.request = ''; notice('Connection interrupted. Refresh to check the result before retrying.', true); } }

    xhr.onerror = xhr.ontimeout = interrupted;

    // Older native WebKit may accept xhr.timeout without enforcing it.

    watchdog = window.setTimeout(function () { interrupted(); xhr.abort(); }, 30000);

    try { xhr.send(mutation ? encode(values) : null); } catch (ignore) { interrupted(); }

  }

  function setBusy() {
    skinSelects(document);

    var controls = document.querySelectorAll(busy ? 'button,select,input' : '[data-disabled]');

    for (var i = 0; i < controls.length; i++) {

      if (controls[i].getAttribute('data-local-control') === 'true' || controls[i].getAttribute('role') === 'tab') continue;
      if (busy) { if (!controls[i].hasAttribute('data-disabled')) controls[i].setAttribute('data-disabled', controls[i].disabled ? 'true' : 'false'); controls[i].disabled = true; }

      else if (controls[i].hasAttribute('data-disabled')) { controls[i].disabled = controls[i].getAttribute('data-disabled') === 'true'; controls[i].removeAttribute('data-disabled'); }

    }

    byId('active').className = busy ? 'busy' : ''; byId('notice').setAttribute('aria-busy',busy ? 'true' : 'false');

  }

  function act(values) {
    if (!state) { clearPendingRecruit(); notice('Refresh Companions before submitting another action.', true); return; }
    // Renew a spent or old nonce only when an action needs it; never poll or replay a submitted mutation.
    if (!state.request || new Date().getTime() - receivedAt > 90000) request(null,values); else request(values);
  }

  function vitals(parent, text, value, cls) {

    var el = node('div', null, 'vital ' + cls), bar = node('div', null, 'bar'), fill = node('div', null, 'fill');

    el.appendChild(node('span', text + ' ' + value + '%')); fill.style.width = Math.max(0, Math.min(100, Number(value) || 0)) + '%'; bar.appendChild(fill); el.appendChild(bar); parent.appendChild(el);

  }

  function detail(parent, title, key) {

    var section = node('section', null, 'disclosure'), toggle = node('button', title, 'disclosure-toggle'), body = node('div');

    toggle.type = 'button'; body.style.display = selectedDetails[key] ? 'block' : 'none';

    toggle.setAttribute('aria-expanded', selectedDetails[key] ? 'true' : 'false');
    toggle.setAttribute('data-focus-key','disclosure|' + key);

    toggle.onclick = function () { selectedDetails[key] = !selectedDetails[key]; body.style.display = selectedDetails[key] ? 'block' : 'none'; toggle.setAttribute('aria-expanded', selectedDetails[key] ? 'true' : 'false'); };

    section.appendChild(toggle); section.appendChild(body); parent.appendChild(section); return body;

  }

  function localButton(parent, text, handler, className) {

    var el = node('button', text, className); el.type = 'button'; el.setAttribute('data-local-control','true'); el.setAttribute('data-focus-key','local|' + encodeURIComponent(text)); el.onclick = handler; parent.appendChild(el); return el;

  }

  function rosterIdentity(character) { var parts = []; if (character.level != null) parts.push('Level ' + character.level); if (character.playerClass) parts.push(label(character.playerClass)); return parts.join(' ') || (character.generated ? 'Temporary Bot' : 'Account character'); }

  function activeBot(id) { for (var i = 0; state && i < state.active.length; i++) if (String(state.active[i].id) === String(id)) return state.active[i]; return null; }

  function rosterBot(id) { for (var i = 0; state && i < state.roster.length; i++) if (String(state.roster[i].id) === String(id)) return state.roster[i]; return null; }

  function showView(view) {

    if (['party','roster','create','quests'].indexOf(view) === -1) return;
    captureDrafts(); currentView = view;

    var names = ['party', 'roster', 'create', 'quests'];

    for (var i = 0; i < names.length; i++) {

      var selected = view === names[i], tab = byId('tab-' + names[i]);

      byId('view-' + names[i]).style.display = selected ? 'block' : 'none'; tab.className = selected ? 'selected' : ''; tab.setAttribute('aria-selected', selected ? 'true' : 'false'); tab.tabIndex = selected ? 0 : -1;

    }

    if (state) renderCurrentView(true);

  }

  function openBot(id, tab) { selectedBot = id; if (tab) botTab = tab; inspecting = true; showView('party'); }

  function invalidateViews() {
    stateRevision++; renderedViews = {}; partyRevision = -1;
    mountedDetail = null; detailCache = {}; detailHistory = [];
  }

  function renderCurrentView(reuse) {
    if (currentView === 'party') { if (partyRevision !== stateRevision) renderOrders(); renderSelection(reuse); }
    else if (currentView === 'roster') {
      if (!reuse || renderedViews[rosterView] !== stateRevision) {
        if (rosterView === 'presets') renderPresets(); else renderRoster();
        renderedViews[rosterView] = stateRevision;
      }
    } else if (currentView === 'quests' && (!reuse || renderedViews.quests !== stateRevision)) {
      renderHelpRequests(); renderOverview();
      var quest = byId('owner-quest'), previous = quest.value; clear(quest);
      for (var q = 0; q < state.quests.length; q++) { var opt = node('option',(state.quests[q].name || 'Quest ' + state.quests[q].id) + ' \u00b7 ' + label(state.quests[q].status)); opt.value = state.quests[q].id; quest.appendChild(opt); }
      if (previous) quest.value = previous;
      if (quest.selectedIndex < 0 && quest.options.length) quest.selectedIndex = 0;
      updateShare(); renderedViews.quests = stateRevision;
    } else if (currentView === 'create' && (!reuse || renderedViews.create !== stateRevision)) {
      updateClasses(); updateCreation(); renderedViews.create = stateRevision;
    }
    setBusy();
  }

  // Fingerprint of only the data the visible section shows, with object keys sorted so the server's map
  // ordering cannot count as a change. Other sections draw the new state when they are opened.
  var lastFingerprint = null;
  function canon(value) {
    if (value === null || typeof value !== 'object') return JSON.stringify(value);
    var out = [], i;
    if (value instanceof Array) { for (i = 0; i < value.length; i++) out.push(canon(value[i])); return '[' + out.join(',') + ']'; }
    var keys = Object.keys(value).sort(); for (i = 0; i < keys.length; i++) out.push(JSON.stringify(keys[i]) + ':' + canon(value[keys[i]]));
    return '{' + out.join(',') + '}';
  }
  var VIEW_KEYS = { party: ['active', 'team', 'teamKind', 'recruitRemaining', 'limit', 'enabled', 'generatedEnabled', 'ownerBag', 'ownerKinah', 'movable', 'companionMovable', 'collectionRenown', 'benefits', 'recycleQuote', 'recyclingEnabled', 'recycleReceivers', 'roles', 'owner'],
    roster: ['roster', 'rosterTotal', 'rosterPage', 'presets', 'savedBots', 'presetLimit', 'recruitRemaining', 'enabled', 'generatedEnabled', 'recyclingEnabled', 'recycleReceivers', 'ownerBag', 'ownerKinah', 'roles'],
    create: ['classes', 'startingClasses', 'creationJourney', 'generatedCount', 'freeGeneratedCount', 'generatedLimit', 'enabled', 'generatedEnabled', 'generatedRandomLooks', 'recruitRemaining', 'tickets'],
    quests: ['active', 'quests', 'helpRequest', 'helpPosts'] };
  function viewPrint(result) {
    var keys = VIEW_KEYS[currentView] || [], picked = { view: currentView }, i;
    for (i = 0; i < keys.length; i++) picked[keys[i]] = result[keys[i]];
    // Only card-visible configuration/status, never moving positions, bags or changing HP.
    if (currentView === 'roster') {
      picked.party = [];
      for (i = 0; i < (result.active || []).length; i++) {
        var bot = result.active[i];
        picked.party.push([bot.id, bot.closing, bot.dead, bot.role, bot.order, bot.gear, bot.stigmaBuild, bot.gearProfile]);
      }
    }
    return canon(picked);
  }
  // Unchanged section: keep the page as it is and refresh only the small counters.
  function quietUpdate() {
    metrics(); byId('count').textContent = state.active.length + ' / ' + state.limit; byId('roster-count').textContent = state.rosterTotal == null ? state.roster.length : state.rosterTotal;
  }
  // Scrolling panes are emptied while redrawn; put each back where the player left it.
  function keepScroll(redraw) {
    var panes = document.querySelectorAll('.party-list, .detail-panel, .main-view.scroll'), tops = [];
    for (var i = 0; i < panes.length; i++) tops.push(panes[i].scrollTop);
    redraw();
    for (var j = 0; j < panes.length; j++) if (panes[j].scrollTop !== tops[j]) panes[j].scrollTop = tops[j];
  }
  function render() { keepScroll(redraw); summonEntry(); }
  function redraw() {

    captureDrafts(); metrics();
    byId('count').textContent = state.active.length + ' / ' + state.limit;
    byId('team-label').textContent = state.teamKind || 'Party';

    byId('roster-count').textContent = state.rosterTotal == null ? state.roster.length : state.rosterTotal;

    byId('owner-label').textContent = state.owner;

    var pending = 0;

    for (var i = 0; i < state.active.length; i++) pending += (state.active[i].questions || []).length;

    byId('question-count').textContent = pending ? String(pending) : '';
    byId('question-count').title = pending + ' quest requests';
    byId('question-count').setAttribute('aria-label',pending + ' quest requests');



    if (recycleSource && activeBot(recycleSource.id)) recycleSource = null;
    if (!activeBot(selectedBot) && !rosterBot(selectedBot) && !(recycleSource && String(recycleSource.id) === String(selectedBot))) selectedBot = state.active.length ? state.active[0].id : null;
    if (state.recycleQuote) recycleQuotes[String(state.recycleQuote.sourceId)] = state.recycleQuote;

    if (pendingTransfer) for (var pt = 0; pt < state.active.length; pt++) if (state.active[pt].name === pendingTransfer) { pendingTransfer = null; window.clearTimeout(pendingTransferTimer); subTabs.equipment = 'transfer'; selectedBot = state.active[pt].id; botTab = 'equipment'; inspecting = true; showView('party'); }
    renderCurrentView(false);
    byId('save-party').querySelector('button').disabled = !state.active.length || busy;

  }

  function botSummary(parent,bot) {
      var row = localButton(parent, null, function () { openBot(bot.id); }, 'bot-summary' + (String(selectedBot) === String(bot.id) ? ' selected' : ''));

      row.setAttribute('data-bot-id', bot.id); row.setAttribute('aria-pressed', String(selectedBot) === String(bot.id) ? 'true' : 'false'); row.appendChild(cardPortrait(bot.playerClass,'summary-class-art',bot.gender)); row.appendChild(crest(bot.playerClass, bot.role));

      row.appendChild(node('strong', bot.name)); row.appendChild(node('span', 'Lv. ' + bot.level + ' ' + label(bot.playerClass) + ' \u00b7 ' + label(bot.role), 'meta'));

      originBadge(row, bot.generated || bot.temporary, bot.world, bot.growth);

      row.appendChild(node('span', bot.closing ? 'Saving and dismissing\u2026' : bot.dead ? 'Waiting for resurrection' : label(bot.order) + ' \u00b7 ' + label(bot.status), 'summary-status'));

      var bars = node('span', null, 'mini-bars');

      for (var v = 0; v < 2; v++) { var track = node('span', null, 'mini-track'), fill = node('span', null, 'mini-fill' + (v ? ' mana' : '')); fill.style.width = Math.max(0, Math.min(100, Number(v ? bot.mana : bot.health) || 0)) + '%'; track.appendChild(fill); bars.appendChild(track); } row.appendChild(bars);

      if ((bot.questions || []).length) row.appendChild(node('span', bot.questions.length + ' catch-up request' + (bot.questions.length === 1 ? '' : 's'), 'needs-attention'));

  }

  function teamMembers() {
    var result = [], groups = state && state.team && state.team.groups || [];
    for (var g = 0; g < groups.length; g++) for (var m = 0; m < groups[g].members.length; m++) result.push(groups[g].members[m]);
    return result;
  }

  function renderTeamGroups(parent) {
    var team = state.team, shown = {}, groups = team && team.groups || [];
    for (var g = 0; g < groups.length; g++) {
      var group = groups[g], panel = node('section',null,'team-group'), heading = node('div',null,'team-group-heading');
      panel.setAttribute('data-group-id',group.id);
      heading.appendChild(node('strong',team.kind === 'Alliance' ? 'Group ' + group.number : 'Party'));
      heading.appendChild(node('span',group.members.length + ' / ' + group.capacity,'status-pill')); panel.appendChild(heading);
      for (var m = 0; m < group.members.length; m++) {
        var member = group.members[m], bot = activeBot(member.id); shown[member.id] = true;
        if (bot) botSummary(panel,bot);
        else {
          var row = node('div',null,'human-member'); row.appendChild(cardPortrait(member.playerClass,'summary-class-art',member.gender)); row.appendChild(crest(member.playerClass)); row.appendChild(node('strong',member.name + (member.self ? ' (you)' : '')));
          row.appendChild(node('span','Lv. ' + member.level + ' ' + label(member.playerClass),'meta'));
          var rank = member.leader ? 'Leader' : member.viceCaptain ? 'Vice-captain' : '';
          row.appendChild(node('span',(rank ? rank + ' \u00b7 ' : '') + (member.dead ? 'Dead' : member.online ? 'Online' : 'Offline'),'summary-status'));
          if (member.online && typeof member.health === 'number' && typeof member.mana === 'number') row.appendChild(node('span','Health ' + member.health + '% \u00b7 Mana ' + member.mana + '%','meta'));
          panel.appendChild(row);
        }
      }
      if (!group.members.length) panel.appendChild(node('p','No members in this group.','empty-state'));
      parent.appendChild(panel);
    }
    for (var i = 0; i < state.active.length; i++) if (!shown[state.active[i].id]) botSummary(parent,state.active[i]);
  }

  function renderTeamControls() {
    var parent = byId('team-controls'), team = state.team; if (!parent) return; clear(parent);
    if (!team) { parent.appendChild(node('p','Refresh to load native team controls.','hint')); return; }
    var members = teamMembers(), groups = team.groups || [], manager = !!team.canManage;
    if (String(teamDraft.team) !== String(team.id)) { teamDraft = {team:team.id}; teamConfirmation = null; }
    parent.appendChild(node('h3',team.kind + ' management','section-toolbar'));
    parent.appendChild(node('p',team.memberCount + ' / ' + team.capacity + ' native members. ' + (manager ? 'Your native team permissions apply.' : 'Team changes need native leadership rights.'),'hint'));
    function values(operation,extra) { var value = {action:'team',operation:operation,team:team.id}; if (extra) for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra,k)) value[k] = extra[k]; return value; }
    function action(text,operation,extra,disabled) { return button(parent,text,values(operation,extra),disabled); }
    action('Convert party to alliance','convert',null,!team.canConvert);
    parent = detail(parent,'Team management','team-management');
    action('Start ready check','ready',null,!manager || team.ready && team.ready.active);
    action('Cancel ready check','cancelready',null,!manager || !(team.ready && team.ready.active));
    if (team.ready && team.ready.states && team.ready.states.length) {
      parent.appendChild(node('p',team.ready.active ? 'Ready check: waiting for ' + team.ready.waiting + ' members.' : 'Ready check results','status-pill'));
      var readyStates = team.ready.states || [];
      for (var r = 0; r < readyStates.length; r++) for (var m = 0; m < members.length; m++) if (String(members[m].id) === String(readyStates[r].id)) parent.appendChild(node('span',members[m].name + ': ' + label(readyStates[r].status),'ready-state'));
    }
    var invitation = node('div',null,'team-invite control-grid'), inviteLabel = node('label','Invite a player'), invite = node('input');
    invite.id = 'team-invite-name'; invite.type = 'text'; invite.maxLength = 24; invite.value = teamDraft.invite || ''; inviteLabel.htmlFor = invite.id; inviteLabel.appendChild(invite); invitation.appendChild(inviteLabel);
    invite.oninput = invite.onchange = function () { teamDraft.invite = invite.value; };
    var inviteButton = button(invitation,'Send native invitation',values('invite'),!manager && team.kind !== 'Solo');
    inviteButton.onclick = function () { if (!invite.value.replace(/^\s+|\s+$/g,'')) { notice('Enter a player name to invite.',true); return; } act(values('invite',{name:invite.value})); }; parent.appendChild(invitation);
    if (members.length) {
      var management = node('div',null,'team-members control-panel'), memberLabel = node('label','Team member'), memberChoice = node('select'); memberChoice.id = 'team-member'; memberLabel.htmlFor = memberChoice.id; memberLabel.appendChild(memberChoice); management.appendChild(memberLabel);
      for (var m = 0; m < members.length; m++) { var option = node('option',members[m].name + (members[m].self ? ' (you)' : '')); option.value = members[m].id; memberChoice.appendChild(option); }
      if (teamDraft.member) memberChoice.value = teamDraft.member; if (memberChoice.selectedIndex < 0) memberChoice.selectedIndex = 0;
      teamDraft.member = memberChoice.value; memberChoice.onchange = function () { teamDraft.member = memberChoice.value; teamConfirmation = null; renderTeamControls(); byId('team-member').focus(); };
      var member = null; for (var m = 0; m < members.length; m++) if (String(members[m].id) === memberChoice.value) member = members[m];
      function manage(text,operation,extra,disabled) { var value = extra || {}; value.member = memberChoice.value; return button(management,text,values(operation,value),disabled); }
      if (team.kind === 'Alliance') {
        var groupLabel = node('label','Destination group'), groupChoice = node('select'); groupChoice.id = 'team-group'; groupLabel.htmlFor = groupChoice.id; groupLabel.appendChild(groupChoice); management.appendChild(groupLabel);
        for (var g = 0; g < groups.length; g++) { var option = node('option','Group ' + groups[g].number + ' (' + groups[g].members.length + '/' + groups[g].capacity + ')'); option.value = groups[g].id; groupChoice.appendChild(option); }
        if (teamDraft.group) groupChoice.value = teamDraft.group; if (groupChoice.selectedIndex < 0) groupChoice.selectedIndex = 0; groupChoice.onchange = function () { teamDraft.group = groupChoice.value; updateMove(); };
        var move = manage('Move to group','move',null,!manager);
        function updateMove() { var destination = null, already = false; for (var g = 0; g < groups.length; g++) if (String(groups[g].id) === groupChoice.value) { destination = groups[g]; for (var m = 0; m < groups[g].members.length; m++) if (String(groups[g].members[m].id) === memberChoice.value) already = true; } move.disabled = busy || !manager || !destination || already || destination.members.length >= destination.capacity; } updateMove();
        move.onclick = function () { act(values('move',{member:memberChoice.value,group:groupChoice.value})); };
        var secondLabel = node('label','Swap with'), secondChoice = node('select'); secondChoice.id = 'team-second'; secondLabel.htmlFor = secondChoice.id; secondLabel.appendChild(secondChoice); management.appendChild(secondLabel);
        for (var m = 0; m < members.length; m++) if (String(members[m].id) !== memberChoice.value) { var option = node('option',members[m].name); option.value = members[m].id; secondChoice.appendChild(option); }
        if (teamDraft.second) secondChoice.value = teamDraft.second; if (secondChoice.selectedIndex < 0 && secondChoice.options.length) secondChoice.selectedIndex = 0; secondChoice.onchange = function () { teamDraft.second = secondChoice.value; };
        var swap = manage('Swap group positions','swap',null,!manager || !secondChoice.options.length); swap.onclick = function () { act(values('swap',{member:memberChoice.value,second:secondChoice.value})); };
      }
      manage('Make leader','leader',null,!team.canLead || !member || member.leader);
      if (team.kind === 'Alliance') manage(member && member.viceCaptain ? 'Remove vice-captain' : 'Make vice-captain','vice',{enabled:!(member && member.viceCaptain)},!team.canLead || !member || member.leader);
      localButton(management,'Remove member…',function () { teamConfirmation = {operation:'kick',team:team.id,member:memberChoice.value,name:member && member.name}; renderTeamControls(); },'danger').disabled = !team.canLead || !member || member.self || member.leader;
      parent.appendChild(management);
    }
    if (team.kind !== 'Solo') localButton(parent,'Leave ' + team.kind.toLowerCase() + '…',function () { teamConfirmation = {operation:'leave',team:team.id}; renderTeamControls(); },'danger');
    if (teamConfirmation && teamConfirmation.operation === 'kick') { var validMember = false; for (var cm = 0; cm < members.length; cm++) if (String(members[cm].id) === String(teamConfirmation.member) && !members[cm].self && !members[cm].leader) validMember = true; if (!validMember || !team.canLead) teamConfirmation = null; }
    if (teamConfirmation && String(teamConfirmation.team) === String(team.id)) {
      var confirm = node('div',null,'team-confirmation control-panel'); confirm.setAttribute('role','alert');
      confirm.appendChild(node('strong',teamConfirmation.operation === 'kick' ? 'Remove ' + teamConfirmation.name + ' from this team?' : 'Leave this native team?'));
      button(confirm,'Confirm',values(teamConfirmation.operation,{member:teamConfirmation.member || '',confirm:'yes'}),teamConfirmation.operation === 'kick' && !team.canLead,'danger');
      localButton(confirm,'Cancel',function () { teamConfirmation = null; renderTeamControls(); },'secondary'); parent.appendChild(confirm);
    }
    setBusy();
  }

  function renderSelection(reuse) { drawSelection(reuse); }
  function drawSelection(reuse) {

    captureDrafts(); var savedFocus = captureFocus(), detailScroll = byId('bot-detail').scrollTop;
    var parent = byId('active'), scroll = parent.scrollTop;

    if (partyRevision !== stateRevision) {
      clear(parent);
      if (!state.active.length) parent.appendChild(node('p','No active companions yet. Recruit from your roster to build a party.','hint'));
      renderTeamGroups(parent); renderTeamControls(); partyRevision = stateRevision;
    } else {
      var summaries = parent.querySelectorAll('.bot-summary');
      for (var s = 0; s < summaries.length; s++) {
        var chosen = String(summaries[s].getAttribute('data-bot-id')) === String(selectedBot);
        summaries[s].className = 'bot-summary' + (chosen ? ' selected' : ''); summaries[s].setAttribute('aria-pressed',chosen ? 'true' : 'false');
      }
    }

    parent.scrollTop = scroll;

    document.querySelector('.party-layout').className = 'party-layout' + (inspecting ? ' inspecting' : '');

    var detailPanel = byId('bot-detail'), key = String(selectedBot) + '|' + botTab;
    if (reuse && mountedDetail === key) return;
    // Take the destination out before evicting: cycling all seven tabs must not
    // discard the very oldest panel just as it is about to become visible.
    var cached = reuse ? detailCache[key] : null;
    if (cached) {
      delete detailCache[key];
      for (var h = detailHistory.length - 1; h >= 0; h--) if (detailHistory[h] === key) detailHistory.splice(h,1);
    }
    if (mountedDetail && mountedDetail !== key) {
      var fragment = document.createDocumentFragment();
      while (detailPanel.firstChild) fragment.appendChild(detailPanel.firstChild);
      detailCache[mountedDetail] = {nodes:fragment,scroll:detailScroll}; detailHistory.push(mountedDetail);
      while (detailHistory.length > 6) delete detailCache[detailHistory.shift()];
    } else clear(detailPanel);
    if (cached) {
      detailPanel.appendChild(cached.nodes); mountedDetail = key;
      var body = byId('bot-content'); if (body && sectionNames[botTab]) chooseSubTab(body,subTabs[botTab] || sectionNames[botTab][0][0]);
      detailPanel.scrollTop = cached.scroll; restoreFocus(savedFocus); setBusy(); return;
    }
    mountedDetail = key;

    var back = localButton(detailPanel, '\u2039 Back to active party', function () { inspecting = false; renderSelection(); }, 'secondary'); back.id = 'bot-back';

    var bot = activeBot(selectedBot), character = rosterBot(selectedBot) || (recycleSource && String(recycleSource.id) === String(selectedBot) ? recycleSource : null);

    if (bot) renderBot(detailPanel, bot);

    else if (character) {

      var card = node('article', null, 'bot'), head = node('div', null, 'bot-header'), headTop = node('div', null, 'bot-top'), headId = node('div'); headerArt(head,character); head.appendChild(crest(character.playerClass, character.generated ? 'support' : null)); headId.appendChild(node('p', 'ROSTER COMPANION', 'eyebrow')); headId.appendChild(node('h3', character.name)); headId.appendChild(node('p', rosterIdentity(character), 'badge')); headTop.appendChild(headId); head.appendChild(headTop); card.appendChild(head);

      var body = node('div', null, 'bot-body'); body.appendChild(node('h3', character.generated ? originLabel(true, false, character.growth) : 'Account character')); body.appendChild(node('p', !character.ready ? 'Creation is pending.' : character.reserved ? 'This character is active or saving and cannot currently be recruited.' : 'This character is offline and in your roster.', 'hint'));

      if (botTab === 'care' && subTabs.care === 'recycle' && ticketRecruit(character.growth)) renderRecycling(subPanel(body,'recycle','Recycle a ticket recruit'), character);
      else { body.appendChild(node('p', character.recruitmentEnabled === false ? 'Temporary Bot recruitment is disabled by this server. Your saved companion and progress are kept.' : 'Recruit this companion to view their live equipment, quests, orders and care settings.', 'hint')); button(body, 'Recruit companion', { action: 'recruit', name: character.name }, !state.enabled || character.recruitmentEnabled === false || character.reserved || !character.ready || !(state.recruitRemaining > 0), 'primary'); }
      localButton(body, 'Back to roster', function () { showView('roster'); }, 'secondary'); card.appendChild(body); detailPanel.appendChild(card);

    } else { var empty = node('div', null, 'empty'); empty.appendChild(node('h3', 'Your next adventure starts with a party')); empty.appendChild(node('p', 'Recruit an existing character or create a Temporary Bot. Select them here to manage orders, equipment and quests.')); localButton(empty, 'Browse your roster', function () { showView('roster'); }, 'primary'); detailPanel.appendChild(empty); }

    byId('bot-detail').scrollTop = detailScroll; restoreFocus(savedFocus);

  }

  byId('save-party').onsubmit = function (event) { event.preventDefault(); act({ action: 'saveparty', presetName: byId('preset-name').value }); };

  function isSavedBot(id) { var saved = state.savedBots || []; for (var i = 0; i < saved.length; i++) if (String(saved[i]) === String(id)) return true; return false; }

  function removalControls(parent, character, live) {
    if (!character.generated) return;
    if (String(removalConfirmation) === String(character.id)) {
      parent.appendChild(node('p', 'Remove ' + character.name + ' from your roster and saved parties? They will be dismissed; archived progress is kept.', 'hint'));
      button(parent, 'Confirm removal', { action: 'removebot', id: character.id, confirm: 'yes' }, live ? live.closing : character.reserved, 'secondary');
      var cancel = localButton(parent, 'Cancel', function () { removalConfirmation = null; render(); }, 'secondary'); cancel.setAttribute('data-action', 'cancel-removebot');
    } else {
      var remove = localButton(parent, 'Remove from roster', function () { removalConfirmation = character.id; render(); }, 'secondary');
      remove.setAttribute('data-action', 'confirm-removebot'); remove.disabled = busy || (live ? live.closing : character.reserved);
    }
  }
  // Saved parties: one banner per preset, members in party-sized groups (alliances show Group 1-4), edited in place.
  var presetEdit = null;
  function rosterEntry(id) { for (var i = 0; state && i < state.roster.length; i++) if (String(state.roster[i].id) === String(id)) return state.roster[i]; return null; }
  function presetMember(parent, member, editing) {
    var known = rosterEntry(member.id), chip = node('div', null, 'preset-member' + (editing ? ' editing' : ''));
    chip.appendChild(crest(known && known.playerClass, member.role));
    chip.appendChild(node('strong', member.name));
    chip.appendChild(node('span', (known && known.level != null ? 'Lv. ' + known.level + ' ' : '') + (known && known.playerClass ? label(known.playerClass) : member.temporary ? 'Temporary Bot' : 'Account character'), 'meta'));
    originBadge(chip, member.temporary);
    if (!editing) { chip.appendChild(node('span', label(member.role) + ' · ' + label(member.order), 'summary-status')); parent.appendChild(chip); return; }
    var role = select(chip, state.roles, member.role), order = select(chip, ['FOLLOW', 'STAY', 'GUARD', 'PASSIVE'], member.order);
    role.className = 'preset-role'; order.className = 'preset-order'; role.setAttribute('aria-label', member.name + ' role'); order.setAttribute('aria-label', member.name + ' order');
    role.onchange = function () { member.role = role.value; }; order.onchange = function () { member.order = order.value; };
    var drop = localButton(chip, '✖', function () { presetEdit.members.splice(presetEdit.members.indexOf(member), 1); renderPresets(); }, 'preset-drop');
    drop.title = 'Remove ' + member.name + ' from this saved party';
    parent.appendChild(chip);
  }
  function renderPresets() {
    var parent = byId('saved-parties'), presets = state.presets || []; clear(parent);
    var head = node('div', null, 'preset-summary'); head.appendChild(node('span', presets.length + ' / ' + (state.presetLimit || 20) + ' saved parties', 'status-pill'));
    head.appendChild(node('span', 'Save your current party from the Party tab. Edit a saved party to rename it, change roles and orders, or add and remove members.', 'hint'));
    parent.appendChild(head);
    if (!presets.length) { parent.appendChild(node('p', 'No saved parties yet. You can mix player-owned alts and Temporary Bots.', 'empty')); return; }
    if (presetEdit) { var still = false; for (var k = 0; k < presets.length; k++) if (presets[k].id === presetEdit.id) still = true; if (!still) presetEdit = null; }
    for (var i = 0; i < presets.length; i++) (function (preset) {
      var editing = presetEdit && presetEdit.id === preset.id, members = editing ? presetEdit.members : preset.members;
      var card = node('article', null, 'preset-card' + (editing ? ' editing' : '')); card.setAttribute('data-preset-id', preset.id);
      var top = node('div', null, 'preset-head'), temporary = 0;
      for (var t = 0; t < members.length; t++) if (members[t].temporary) temporary++;
      if (editing) { var title = node('input'); title.id = 'preset-edit-name'; title.maxLength = 40; title.value = presetEdit.name; title.setAttribute('aria-label', 'Saved party name'); title.oninput = function () { presetEdit.name = title.value; }; top.appendChild(title); }
      else top.appendChild(node('h4', preset.name));
      var alts = members.length - temporary;
      top.appendChild(node('span', (members.length > 5 ? 'Alliance' : 'Party') + ' · ' + members.length + ' companions · ' + temporary + (temporary === 1 ? ' Temporary Bot' : ' Temporary Bots') + ' · ' + alts + (alts === 1 ? ' Account Alt' : ' Account Alts'), 'preset-meta'));
      var actions = node('div', null, 'preset-actions');
      if (editing) {
        var save = button(actions, 'Save changes', {}, !members.length, 'primary small');
        save.onclick = function () { var spec = []; for (var m = 0; m < presetEdit.members.length; m++) spec.push(presetEdit.members[m].id + ':' + presetEdit.members[m].role + ':' + presetEdit.members[m].order); act({ action: 'editparty', preset: preset.id, presetName: presetEdit.name, members: spec.join(',') }); presetEdit = null; };
        localButton(actions, 'Cancel', function () { presetEdit = null; renderPresets(); }, 'quiet small');
      } else {
        button(actions, 'Summon', { action: 'loadparty', preset: preset.id }, !state.enabled, 'primary small');
        localButton(actions, 'Edit', function () { var copy = []; for (var m = 0; m < preset.members.length; m++) copy.push({ id: preset.members[m].id, name: preset.members[m].name, temporary: preset.members[m].temporary, role: preset.members[m].role, order: preset.members[m].order }); presetEdit = { id: preset.id, name: preset.name, members: copy }; renderPresets(); }, 'small');
        button(actions, 'Remove', { action: 'deleteparty', preset: preset.id }, false, 'danger small');
      }
      top.appendChild(actions); card.appendChild(top);
      for (var g = 0; g * 6 < Math.max(members.length, 1); g++) {
        var group = node('div', null, 'preset-group');
        if (members.length > 6) group.appendChild(node('span', 'Group ' + (g + 1), 'preset-group-title'));
        for (var n = g * 6; n < Math.min(members.length, g * 6 + 6); n++) presetMember(group, members[n], editing);
        card.appendChild(group);
      }
      if (editing && members.length < 23) {
        var add = node('div', null, 'preset-add'), choice = node('select'), taken = {};
        var choiceLabel = node('label', 'Choose a companion to add'); choice.id = 'preset-add-choice'; choiceLabel.htmlFor = choice.id; add.appendChild(choiceLabel);
        // Render inside the page: popup menus can extend beyond the native browser surface.
        var searchLabel = node('label', 'Search the collection'); searchLabel.htmlFor = 'preset-roster-search'; add.appendChild(searchLabel);
        var search = node('input'); search.id = 'preset-roster-search'; search.type = 'search'; search.value = byId('roster-search').value;
        search.oninput = function () { byId('roster-search').value = search.value; rosterPage = 0; queueRosterSearch(); }; add.appendChild(search);
        var previousPage = localButton(add, 'Previous companions', function () { if (rosterPage > 0) { rosterPage--; request(null); } }, 'small'); previousPage.disabled = rosterPage === 0;
        var nextPage = localButton(add, 'Next companions', function () { rosterPage++; request(null); }, 'small'); nextPage.disabled = (rosterPage + 1) * pageSize >= state.rosterTotal;
        choice.size = 6;
        for (var a = 0; a < members.length; a++) taken[members[a].id] = true;
        for (var r = 0; r < state.roster.length; r++) { var c = state.roster[r]; if (taken[c.id] || (c.generated && !c.ready)) continue; var opt = node('option', '[' + originLabel(c.generated, false, c.growth) + '] ' + c.name + (c.level != null ? ' · Lv. ' + c.level : '') + (c.playerClass ? ' ' + label(c.playerClass) : '')); opt.value = c.id; choice.appendChild(opt); }
        if (choice.options.length) choice.selectedIndex = 0;
        else choice.disabled = true;
        choice.setAttribute('aria-label', 'Companion to add'); add.appendChild(choice);
        localButton(add, '+ Add to party', function () { var c = rosterEntry(choice.value); if (!c) return; presetEdit.members.push({ id: c.id, name: c.name, temporary: !!c.generated, role: state.roles[0], order: 'FOLLOW' }); renderPresets(); }, 'small').disabled = !choice.options.length;
        card.appendChild(add);
      }
      parent.appendChild(card);
    }(presets[i]));
  }

  // Class illustrations are deliberately separate from a character's saved native face/equipment.
  var CARD_ART = '/market/companions/media/cards/';
  var cardRenames = {};
  var cardPortraits = { TEMPLAR:0, GLADIATOR:1, ASSASSIN:2, RANGER:3, SORCERER:4, SPIRIT_MASTER:5, CLERIC:6, CHANTER:7, GUNNER:8, RIDER:9, BARD:10,
    WARRIOR:1, SCOUT:3, MAGE:4, PRIEST:6, ENGINEER:8, ARTIST:10 };

  // Who each cell of the main atlas shows. The counterpart atlas has the other gender in the same cell.
  var cardPortraitMale = [true,true,false,false,true,false,false,true,true,false,false];

  function cardPortraitAtlas(index, gender) {
    var other = index < cardPortraitMale.length && (gender === 'MALE' || gender === 'FEMALE') && (gender === 'MALE') !== cardPortraitMale[index];
    return other ? 'class-portraits-counterpart.jpg?v=1' : 'class-portraits.jpg?v=2';
  }

  function cardPortrait(playerClass, className, gender) {
    var key = String(playerClass || '').toUpperCase(), index = Object.prototype.hasOwnProperty.call(cardPortraits,key) ? cardPortraits[key] : 11;
    var portrait = node('span',null,className), image = node('img');
    portrait.setAttribute('aria-hidden','true'); image.alt = ''; image.src = CARD_ART + cardPortraitAtlas(index, gender);
    image.style.left = -(index % 4) * 100 + '%'; image.style.top = -Math.floor(index / 4) * 100 + '%';
    portrait.appendChild(image); return portrait;
  }

  function cardStatus(character, live) {
    return live ? live.closing ? 'Saving progress' : live.dead ? 'Awaiting resurrection' : 'In your party' :
      !character.ready ? 'Creation pending' : character.reserved ? 'Active or saving' : 'Available';
  }

  function cardBuild(live) {
    if (!live) return 'Not loaded';
    if (!live.stigmaBuild || live.stigmaBuild === 'AUTO') return 'Automatic';
    var builds = live.stigmaBuilds || [];
    for (var i = 0; i < builds.length; i++) if (builds[i].key === live.stigmaBuild) return builds[i].name;
    return label(live.stigmaBuild);
  }

  // Gear rarity caps what auto-equip may wear: PlayerBotTemporary.ceiling.
  var RARITY_GEAR = { POOR:'Common', ORDINARY:'Rare', GOOD:'Legendary', GREAT:'Unique', EXCELLENT:'Mythic' };
  function cardTier(parent, title, value) {
    var tier = summonTier(value), row = node('span',null,'collection-tier t' + tier), pips = node('span',null,'collection-pips');
    row.appendChild(node('span',title,'collection-metric-label')); row.appendChild(node('strong',label(value)));
    for (var i = 0; i < 5; i++) pips.appendChild(node('i',null,i <= tier ? 'on' : ''));
    row.appendChild(pips); parent.appendChild(row);
  }
  function cardTierNote(parent, growth) {
    var rarity = String(growth.rarity).toUpperCase(), gear = node('p',null,'collection-tier-note t' + summonTier(rarity)), rank = node('p',null,'collection-tier-note t' + summonTier(growth.rank));
    gear.appendChild(node('strong','Gear rarity ' + label(growth.rarity))); gear.appendChild(node('span',': permanent, equips up to ' + (RARITY_GEAR[rarity] || 'its own') + ' gear.'));
    rank.appendChild(node('strong','Combat rank ' + label(growth.rank))); rank.appendChild(node('span',': ' + (growth.mistakeChance != null ? growth.mistakeChance + '% mistakes' : 'how well it fights') + (growth.needed ? ', ' + growth.learning + ' / ' + growth.needed + ' Learning XP.' : ', fully trained.')));
    parent.appendChild(gear); parent.appendChild(rank);
  }
  function cardMetric(parent, title, value) {
    var field = node('span',null,'collection-metric'); field.appendChild(node('span',title,'collection-metric-label'));
    field.appendChild(node('strong',value)); parent.appendChild(field);
  }

  function cancelCardTurn(id) {
    var turn = cardTurns[id]; if (!turn) return;
    window.clearTimeout(turn.swap); window.clearTimeout(turn.end); delete cardTurns[id];
  }

  // Authored painted icon atlas shared by card actions and the window chrome.
  var skinIcons = {summon:0,equipment:1,focus:2,progress:3,coins:4,more:5,profile:6,save:7,transfer:8,mail:9,retire:10,collection:11,create:12,quests:13,refresh:14,chevron:15};
  function paintedIcon(kind, className) {
    var index = Object.prototype.hasOwnProperty.call(skinIcons,kind) ? skinIcons[kind] : skinIcons.profile;
    var icon = node('span',null,'authored-icon ' + (className || '')), image = node('img');
    icon.setAttribute('aria-hidden','true'); image.alt = ''; image.src = CARD_ART + 'skin-icons.png?v=2';
    image.style.left = -(index % 4) * 100 + '%'; image.style.top = -Math.floor(index / 4) * 100 + '%';
    icon.appendChild(image); return icon;
  }
  function cardActionIcon(control, kind) {
    control.insertBefore(paintedIcon(kind,'collection-action-icon'),control.firstChild);
  }
  function skinChromeIcons() {
    var names = [['party','profile'],['roster','collection'],['create','create'],['quests','quests']];
    for (var i = 0; i < names.length; i++) {
      var glyph = byId('tab-' + names[i][0]).querySelector('.glyph'); clear(glyph); glyph.appendChild(paintedIcon(names[i][1]));
    }
    var refresh = byId('refresh'); refresh.textContent = 'Refresh'; refresh.insertBefore(paintedIcon('refresh','refresh-icon'),refresh.firstChild);
  }
  // Keep the real select/label/change handler; only the generated chevron is overlaid.
  function skinSelects(parent) {
    var fields = parent.querySelectorAll('select');
    for (var i = 0; i < fields.length; i++) {
      var field = fields[i];
      if (field.hasAttribute('size') || field.parentNode.className === 'skin-select') continue;
      var wrap = node('span',null,'skin-select'); field.parentNode.insertBefore(wrap,field); wrap.appendChild(field);
    }
  }

  function cardActionKind(control) {
    var text = control.textContent;
    if (/^(Recruit|Summon|Manage)/.test(text)) return 'summon';
    if (/Equipment/.test(text)) return 'equipment';
    if (/Behavior/.test(text)) return 'focus';
    if (/Quests/.test(text)) return 'progress';
    if (/Care/.test(text)) return 'coins';
    if (/actions$/.test(text)) return 'more';
    if (/^Save/.test(text)) return 'save';
    if (/^Move/.test(text)) return 'transfer';
    if (/^Mail/.test(text)) return 'mail';
    if (/remov|Remove/.test(text)) return 'retire';
    return 'profile';
  }

  function collectionFilter() {
    var filter = byId('roster-filter').value, fields = ['class','rank','rarity'];
    for (var i = 0; i < fields.length; i++) { var field = byId('roster-' + fields[i]); if (field && field.value !== 'all') filter += '|' + fields[i] + ':' + field.value; }
    return filter;
  }
  function renderCollectionCard(parent, character) {
    var id = String(character.id), live = activeBot(character.id), flipped = !!cardFaces[id];
    var card = node('article',null,'collection-card'), shell = node('div',null,'collection-shell'), turn = node('div',null,'collection-turn');
    card.setAttribute('data-bot-id',id); card.setAttribute('aria-label',character.name + ', ' + rosterIdentity(character));
    var front = localButton(turn,null,function () { flip(true); },'collection-front');
    front.id = 'card-front-' + id; front.setAttribute('aria-controls','card-back-' + id);
    front.setAttribute('aria-label','Turn ' + character.name + "'s card for details and actions");
    front.setAttribute('data-focus-key','card|' + id + '|front'); front.title = 'Illustrated ' + label(character.playerClass || 'Daeva') + ' class artwork';
    front.appendChild(cardPortrait(character.playerClass,'collection-portrait',character.gender));
    front.appendChild(node('span',null,'collection-shade'));
    var origin = node('span',character.generated && isSavedBot(character.id) ? 'Saved ' + originLabel(true,false,character.growth) : originLabel(character.generated,false,character.growth),'collection-origin' + rarityClass(character.growth)); front.appendChild(origin);
    var identity = node('span',null,'collection-identity'); identity.appendChild(crest(character.playerClass,live ? live.role : null));
    identity.appendChild(node('strong',character.name,'collection-name'));
    identity.appendChild(node('span',rosterIdentity(character),'collection-class'));
    var metrics = node('span',null,'collection-metrics');
    cardMetric(metrics,'Auto-equip',ticketRecruit(character.growth) ? 'Always on' : live ? live.gear ? 'On' : 'Off' : 'Not loaded');
    cardMetric(metrics,'Build',cardBuild(live));
    if (ticketRecruit(character.growth)) { cardTier(metrics,'Gear rarity',character.growth.rarity); cardTier(metrics,'Combat rank',character.growth.rank); }
    identity.appendChild(metrics);
    var stateLine = node('span',cardStatus(character,live),'collection-status' + (!live && character.ready && !character.reserved ? ' available' : ''));
    identity.appendChild(stateLine); identity.appendChild(node('span','Click to flip \u203a','collection-flip-hint')); front.appendChild(identity);

    var back = node('div',null,'collection-back'); back.id = 'card-back-' + id;
    var content = node('div',null,'collection-back-content'); content.setAttribute('data-card-scroll',id);
    var heading = node('div',null,'collection-back-heading'); heading.appendChild(cardPortrait(character.playerClass,'collection-mini',character.gender));
    heading.appendChild(node('h3',character.name)); heading.appendChild(node('p',rosterIdentity(character),'collection-class'));
    heading.appendChild(node('span',originLabel(character.generated,false,character.growth),'collection-back-origin')); content.appendChild(heading);
    content.appendChild(node('p',cardStatus(character,live),'collection-back-status'));
    if (ticketRecruit(character.growth)) cardTierNote(content,character.growth);
    if (live) {
      content.appendChild(node('p',label(live.role) + ' \u00b7 ' + label(live.order),'collection-config'));
      content.appendChild(node('p','Build: ' + cardBuild(live),'collection-config'));
    } else content.appendChild(node('p','Recruit to inspect live equipment, builds and quests.','collection-config'));
    var actions = node('div',null,'collection-actions'); content.appendChild(actions);
    var unavailable = !state.enabled || character.recruitmentEnabled === false || character.reserved || !character.ready || !(state.recruitRemaining > 0);
    if (live) localButton(actions,'Manage companion',function () { openBot(character.id,'overview'); },'primary');
    else {
      var summon = button(actions,character.generated && isSavedBot(character.id) ? 'Summon saved bot' : 'Recruit companion',{action:'recruit',name:character.name},unavailable,'primary');
      if (unavailable) summon.title = !state.enabled ? 'Companion recruitment is disabled.' : character.recruitmentEnabled === false ? 'Temporary Bot recruitment is disabled by this server.' : !character.ready ? 'Creation is still pending.' : character.reserved ? 'This character is active or saving.' : 'Your active team has no recruitment slots.';
      localButton(actions,'View companion record',function () { openBot(character.id,'overview'); });
      if (character.generated && !live && character.ready) {
        var rename = node('div', null, 'collection-rename'), field = document.createElement('input'), renamed = cardRenames[id] || '';
        field.type = 'text'; field.maxLength = 16; field.placeholder = 'New name'; field.value = renamed; field.setAttribute('aria-label', 'New name for ' + character.name);
        field.oninput = function () { cardRenames[id] = field.value; };
        rename.appendChild(field);
        localButton(rename, 'Rename', function () { var wanted = field.value; delete cardRenames[id]; act({ action:'renamebot', name:character.name, newName:wanted }); });
        actions.appendChild(rename);
      }
    }
    if (live) {
      localButton(actions,'Equipment & builds',function () { subTabs.equipment = 'profile'; openBot(character.id,'equipment'); });
      localButton(actions,'Behavior & role',function () { openBot(character.id,'behavior'); });
      localButton(actions,'Quests & progress',function () { openBot(character.id,'quests'); });
      localButton(actions,'Care & spending',function () { openBot(character.id,'care'); });
    }
    var extra = node('div',null,'collection-more'); extra.id = 'card-more-' + id;
    var more = localButton(actions,cardMore[id] ? 'Fewer actions' : 'More actions',function () {
      cardMore[id] = !cardMore[id]; extra.style.display = cardMore[id] ? 'block' : 'none';
      more.textContent = cardMore[id] ? 'Fewer actions' : 'More actions'; cardActionIcon(more,'more'); more.setAttribute('aria-expanded',cardMore[id] ? 'true' : 'false');
    },'quiet'); more.setAttribute('aria-controls',extra.id); more.setAttribute('aria-expanded',cardMore[id] ? 'true' : 'false');
    if (character.generated && character.ready) button(extra,isSavedBot(character.id) ? 'Save progress' : 'Save bot',{action:'savebot',name:character.name},live && live.closing,'secondary');
    if (live) localButton(extra,'Move items',function () { subTabs.equipment = 'transfer'; openBot(character.id,'equipment'); });
    else if (!character.generated) {
      var move = button(extra,'Move items',{action:'recruit',name:character.name},unavailable);
      move.title = 'Recruit ' + character.name + ', then open item transfer'; move.onclick = function () { if (busy) return; pendingTransfer = character.name; window.clearTimeout(pendingTransferTimer); pendingTransferTimer = window.setTimeout(function () { clearPendingRecruit(); notice('The companion did not arrive. Refresh before trying again.',true); },30000); act({action:'recruit',name:character.name}); };
      localButton(extra,'Mail items',function () { mailTarget = character.name; transferPick.mail = null; renderRoster(); });
    }
    if (ticketRecruit(character.growth)) {
      localButton(extra,'Recycle',function () { openRecycling(live || character); });
    }
    removalControls(extra,character,live); extra.style.display = cardMore[id] ? 'block' : 'none'; actions.appendChild(extra);
    back.appendChild(content);
    back.appendChild(node('span','\u2039 Click to flip','collection-flip-back')); back.tabIndex = -1;
    turn.appendChild(back);
    var frame = node('img',null,'collection-frame'); frame.src = CARD_ART + 'ornate-frame.png?v=2'; frame.alt = ''; frame.setAttribute('aria-hidden','true'); turn.appendChild(frame);
    shell.appendChild(turn); card.appendChild(shell); parent.appendChild(card);
    // Identical local labels on different cards still restore focus to the correct native identity.
    var controls = back.querySelectorAll('button');
    for (var c = 0; c < controls.length; c++) {
      controls[c].setAttribute('data-focus-key','card|' + id + '|' + (controls[c].getAttribute('data-focus-key') || c));
      if (controls[c].parentNode.className !== 'collection-rename') cardActionIcon(controls[c],cardActionKind(controls[c]));
    }
    function face(value, focus) {
      front.style.display = value ? 'none' : 'block'; back.style.display = value ? 'block' : 'none';
      front.setAttribute('aria-expanded',value ? 'true' : 'false'); front.setAttribute('aria-hidden',value ? 'true' : 'false'); back.setAttribute('aria-hidden',value ? 'false' : 'true');
      if (focus) {
        var pane = byId('view-roster'), top = pane.scrollTop, inside = content.scrollTop, x = window.pageXOffset, y = window.pageYOffset;
        (value ? back : front).focus();
        pane.scrollTop = top; content.scrollTop = inside;
        if (window.pageXOffset !== x || window.pageYOffset !== y) window.scrollTo(x,y);
      }
    }
    function flip(value) {
      if (cardTurns[id]) return;
      cardFaces[id] = value;
      var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      var supported = typeof turn.style.webkitAnimationName !== 'undefined' || typeof turn.style.WebkitAnimationName !== 'undefined' || typeof turn.style.animationName !== 'undefined';
      if (!supported || reduced) { face(value,true); return; }
      turn.className = 'collection-turn collection-flipping';
      cardTurns[id] = {swap:window.setTimeout(function () { face(value,true); },160),end:window.setTimeout(function () {
        turn.className = 'collection-turn'; delete cardTurns[id];
      },340)};
    }
    // Anywhere on the back turns the card over, except the controls placed on it.
    turn.onclick = function (event) {
      if (!cardFaces[id]) return;
      for (var el = event.target; el && el !== turn; el = el.parentNode) if (/^(BUTTON|INPUT|SELECT|TEXTAREA|LABEL|OPTION|A)$/.test(el.tagName)) return;
      flip(false);
    };
    face(flipped,false); content.scrollTop = cardScroll[id] || 0;
  }

  function renderRoster() {

    var priorFocus = captureFocus(), oldScroll = parentCardScroll();
    for (var turnId in cardTurns) if (Object.prototype.hasOwnProperty.call(cardTurns,turnId)) cancelCardTurn(turnId);

    var parent = byId('roster'), query = byId('roster-search').value.toLowerCase().replace(/^\s+|\s+$/g, ''), filter = byId('roster-filter').value, sort = byId('roster-sort').value, rows = []; clear(parent);


    rows = state.roster;
    var total = state.rosterTotal == null ? rows.length : state.rosterTotal;
    rosterPage = state.rosterPage || 0;
    var pages = Math.max(1, Math.ceil(total / pageSize)), start = 0, end = rows.length;
    byId('roster-results').textContent = total ? 'Showing ' + (rosterPage * pageSize + 1) + '\u2013' + (rosterPage * pageSize + end) + ' of ' + total + ' companions' : 'No companions match your search.';
    byId('roster-page').textContent = 'Page ' + (rosterPage + 1) + ' of ' + pages; byId('roster-prev').disabled = rosterPage === 0; byId('roster-next').disabled = rosterPage >= pages - 1;
    var mailPanel = byId('mail-alt-panel'); clear(mailPanel); if (mailTarget) renderMailAlt(mailPanel);
    if (!rows.length) parent.appendChild(node('div', state.roster.length ? 'Try another name, class or filter.' : 'Your roster is empty. Create a companion to get started.', 'empty'));

    var classSelect = byId('roster-class');
    if (classSelect && classSelect.options.length === 1) {
      var classes = (state.startingClasses || []).concat(state.classes || []).sort(), seenClasses = {};
      for (var k = 0; k < classes.length; k++) if (!seenClasses[classes[k]]) { var option = node('option',label(classes[k])); option.value = classes[k]; classSelect.appendChild(option); seenClasses[classes[k]] = true; }
    }
    for (var r = start; r < end; r++) renderCollectionCard(parent,rows[r]);
    restoreFocus(priorFocus); byId('view-roster').scrollTop = oldScroll; setBusy();
  }

  function parentCardScroll() {
    var content = byId('roster').querySelectorAll('[data-card-scroll]');
    for (var i = 0; i < content.length; i++) cardScroll[content[i].getAttribute('data-card-scroll')] = content[i].scrollTop;
    return byId('view-roster').scrollTop;
  }

  function renderSpacing(parent, bot) {
    if (!bot.rangedSpacing) return;
    var group = node('div', null, 'spacing-controls'), draft = spacingDrafts[bot.id];
    group.appendChild(node('h3', 'Ranged spacing'));
    function distance(id, text, value, min, max) {
      var row = node('label', text), input = node('input'); input.id = id; input.type = 'number'; input.min = min; input.max = max; input.step = '0.5'; input.value = value; input.disabled = bot.closing || busy; row.htmlFor = id; row.appendChild(input); group.appendChild(row); return input;
    }
    var follow = distance('spacing-owner', 'Follow spread around you (2–12 m)', draft ? draft.follow : bot.ownerSpacing, 2, 12);
    var attack = distance('spacing-attack', 'Distance from enemy while attacking (4–18 m)', draft ? draft.attack : bot.attackSpacing, 4, 18);
    follow.oninput = attack.oninput = function () { spacingDrafts[bot.id] = { follow: follow.value, attack: attack.value }; };
    follow.onchange = follow.oninput; attack.onchange = attack.oninput;
    var save = button(group, 'Save ranged spacing', { action: 'spacing' }, bot.closing);
    save.onclick = function () { var f = Number(follow.value), a = Number(attack.value); if (!isFinite(f) || f < 2 || f > 12 || !isFinite(a) || a < 4 || a > 18) { notice('Choose owner spacing 2–12 m and attack spacing 4–18 m.', true); return; } act({ action: 'spacing', name: bot.name, ownerSpacing: f, attackSpacing: a }); };
    group.appendChild(node('p', 'Follow spread sets the formation radius; inner Line slots stay closer. Attack distance is capped by usable skill range. A close enemy allows one short retreat, then the companion stands and fights. Combat hazards still take priority.', 'hint')); parent.appendChild(group);
    draftField(follow,bot,'spacing-owner'); draftField(attack,bot,'spacing-attack');
  }

  // Overview: one framed tile for each area, each leading to the tab that manages it.
  function overviewTile(grid, title, target, section) {
    var tile = node('section', null, 'overview-tile'), head = node('div', null, 'overview-head'); head.appendChild(node('h4', title));
    if (target) localButton(head, 'Open \u203a', function () { captureDrafts(); if (section) subTabs[target] = section; botTab = target; renderSelection(true); }, 'overview-open');
    tile.appendChild(head); grid.appendChild(tile); return tile;
  }
  function overviewRow(tile, title, value) {
    var row = node('p', null, 'overview-row'); row.appendChild(node('span', title)); row.appendChild(node('strong', String(value))); tile.appendChild(row);
  }
  function overviewBoard(body, bot) {
    var grid = node('div', null, 'overview-grid'), items = bot.inventory || [], worn = 0, carried = 0, ready = 0;
    for (var i = 0; i < items.length; i++) { if (items[i].equipped) worn++; else carried++; }
    for (var q = 0; q < (bot.quests || []).length; q++) if (bot.quests[q].ready) ready++;
    var now = overviewTile(grid, 'Right now', 'activity');
    overviewRow(now, 'Status', bot.closing ? 'Saving and dismissing' : bot.dead ? 'Waiting for resurrection' : label(bot.status));
    overviewRow(now, 'Doing', bot.action ? label(bot.action) : 'Nothing yet'); overviewRow(now, 'Order', label(bot.order));
    var progress = overviewTile(grid, ticketRecruit(bot.growth) ? 'Rarity and rank' : 'Character');
    if (ticketRecruit(bot.growth)) {
      cardTier(progress, 'Gear rarity', bot.growth.rarity); cardTier(progress, 'Combat rank', bot.growth.rank);
      overviewRow(progress, 'Learning XP', bot.growth.needed ? bot.growth.learning + ' / ' + bot.growth.needed : 'Fully trained');
      if (bot.growth.mistakeChance != null) overviewRow(progress, 'Mistake chance', bot.growth.mistakeChance + '%');
    } else { overviewRow(progress, 'Type', bot.characterType || 'Companion'); overviewRow(progress, 'Build', bot.build || 'Automatic'); }
    var gear = overviewTile(grid, 'Equipment', 'equipment', 'profile');
    overviewRow(gear, 'Worn', worn + ' items'); overviewRow(gear, 'Carried', carried + (bot.inventoryLimit ? ' / ' + bot.inventoryLimit : '') + ' items');
    overviewRow(gear, 'Auto-equip', ticketRecruit(bot.growth) ? 'Always on' : bot.gear ? 'On' : 'Off');
    var quests = overviewTile(grid, 'Quests', 'quests', 'journal');
    overviewRow(quests, 'Active', (bot.quests || []).length); overviewRow(quests, 'Ready to turn in', ready); overviewRow(quests, 'Requests', (bot.questions || []).length);
    var combat = overviewTile(grid, 'Combat', 'behavior', 'combat');
    overviewRow(combat, 'Role', label(bot.role)); overviewRow(combat, 'Build', bot.stigmaBuild ? label(bot.stigmaBuild) : bot.build || 'Automatic');
    overviewRow(combat, 'Mistakes this session', bot.mistakes || 0);
    var care = overviewTile(grid, 'Care', 'care', 'maintenance');
    overviewRow(care, 'Spent today', (bot.spent || 0) + (bot.dailyBudget ? ' / ' + bot.dailyBudget : '') + ' Kinah'); overviewRow(care, 'Mail', bot.mailCount || 0);
    body.appendChild(grid);
  }
  function renderBot(parent, bot) {

    var card = node('article', null, 'bot'), header = node('div', null, 'bot-header'), top = node('div', null, 'bot-top'), identity = node('div'); card.setAttribute('data-bot-id', bot.id); headerArt(header,bot); header.appendChild(crest(bot.playerClass, bot.role));

    identity.appendChild(node('p', 'COMPANION / ' + label(bot.role).toUpperCase(), 'eyebrow')); identity.appendChild(node('h3', bot.name)); identity.appendChild(node('p', 'Level ' + bot.level + ' ' + label(bot.playerClass) + ' · ' + originLabel(bot.generated || bot.temporary, bot.world, bot.growth), 'badge')); if (bot.growth) { identity.appendChild(node('p', 'Gear rarity: ' + label(bot.growth.rarity) + ' (permanent) · Combat rank: ' + rankLine(bot.growth), 'badge' + rarityClass(bot.growth))); identity.appendChild(node('p', mistakeLine(bot), 'badge')); } top.appendChild(identity); top.appendChild(node('div', bot.closing ? 'Saving\u2026' : bot.dead ? 'Awaiting resurrection' : label(bot.order), 'bot-state')); header.appendChild(top);

    var health = node('div', null, 'vitals'); vitals(health, 'Health', bot.health, 'health'); vitals(health, 'Mana', bot.mana, 'mana'); if (bot.xp != null) vitals(health, 'XP', bot.xp, 'xp'); header.appendChild(health); card.appendChild(header);

    if (bot.generated || bot.temporary) button(header, 'Save ' + originLabel(true, false, bot.growth), { action: 'savebot', name: bot.name }, bot.closing, 'secondary');
    var tabs = node('nav', null, 'tabs bot-tabs'), names = [['overview','Overview'],['behavior','Behavior'],['equipment','Equipment'],['mail','Mail'],['quests','Quests'],['care','Care'],['activity','Activity']]; tabs.setAttribute('role', 'tablist'); tabs.setAttribute('aria-label', bot.name + ' details');

    for (var t = 0; t < names.length; t++) (function (name, title) { var tab = localButton(tabs, title, function () { captureDrafts(); botTab = name; renderSelection(true); var selected = byId('bot-tab-' + name); if (selected) selected.focus(); }, botTab === name ? 'selected' : ''); tab.id = 'bot-tab-' + name; tab.setAttribute('role','tab'); tab.setAttribute('aria-controls','bot-content'); tab.setAttribute('aria-selected',botTab === name ? 'true' : 'false'); tab.tabIndex = botTab === name ? 0 : -1; }(names[t][0], names[t][1])); card.appendChild(tabs);

    var body = node('div', null, 'bot-body'); body.id = 'bot-content'; body.setAttribute('role','tabpanel'); body.setAttribute('aria-labelledby','bot-tab-' + botTab); card.appendChild(body);

    if (botTab === 'overview') {

      overviewBoard(body,bot);

      if (bot.ownedProgression) {
        var together = node('div', null, 'role-row'); together.appendChild(node('h3', 'Level together'));
        together.appendChild(node('p', 'While alive, nearby, and in your team, this alt catches up to your level and XP progress after combat. Higher levels are kept. Equipment remains earned. A starting class still needs your advanced-class choice.', 'hint'));
        button(together, bot.levelTogether ? 'Disable level matching' : 'Enable level matching', { action: 'leveltogether', name: bot.name, enabled: !bot.levelTogether }, bot.closing); body.appendChild(together);
      }
      if (bot.ascensionChoices && bot.ascensionChoices.length) {
        var ascend = node('div', null, 'role-row'); ascend.appendChild(node('h3', 'Choose advanced class'));
        ascend.appendChild(node('p', 'This is a permanent class choice for your account character. Equipment and earned progress are preserved. Available from level 9.', 'hint'));
        var advanced = select(ascend, bot.ascensionChoices, bot.ascensionChoices[0]); advanced.id = 'owned-advanced-class'; advanced.setAttribute('aria-label', 'Advanced class');
        var ascendButton = localButton(ascend, 'Confirm advanced class', function () { act({ action: 'ascend', name: bot.name, playerClass: advanced.value }); }, 'primary');
        ascendButton.disabled = bot.closing || busy || !bot.ascensionReady; body.appendChild(ascend);
      }

      if (bot.mission) { var mission = node('div', null, 'mission'); mission.appendChild(node('p', 'Quest ' + bot.mission + ' \u00b7 ' + label(bot.missionStatus), 'route')); button(mission, 'Cancel quest mission', { action: 'mission', name: bot.name, quest: 0 }, bot.closing); body.appendChild(mission); }



      var roleRow = node('div', null, 'role-row'), roleLabel = node('label', 'Combat role'); roleLabel.htmlFor = 'bot-role'; roleRow.appendChild(roleLabel); var role = select(roleRow, state.roles, bot.role); role.id = 'bot-role'; role.disabled = bot.closing; role.onchange = function () { act({ action: 'role', name: bot.name, role: role.value }); }; body.appendChild(roleRow);

      renderQuestions(body, bot);

    } else if (botTab === 'behavior') {
      var combat = subPanel(body,'combat','Combat behavior');
      var settings = node('div', null, 'settings control-grid'), flags = [['area','Area attacks'],['supplies','Recovery and build supplies'],['gear','Auto equipment'],['loot','Auto loot']];
      if (ticketRecruit(bot.growth)) flags.splice(2,1);

      for (var f = 0; f < flags.length; f++) button(settings, flags[f][1] + ': ' + (bot[flags[f][0]] ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: flags[f][0], enabled: !bot[flags[f][0]] }, bot.closing || (flags[f][0] === 'gear' && !bot.temporary), 'setting-card' + (bot[flags[f][0]] ? ' on' : '')); combat.appendChild(settings);
      if (bot.temporary || bot.world) combat.appendChild(node('p','Supply refills spend this companion\'s own Kinah at native replacement prices, within its Care reserve and daily spending limit. If funds run short it keeps the supplies it owns.','hint'));
      var automation = subPanel(body,'automation','Quest automation'); automation.className += ' settings control-grid'; var flags = [['questing','Auto quests'],['questCombat','Nearby quest combat'],['partySync','Party completion sync']];
      if (bot.world) { flags = []; automation.appendChild(node('p', 'World characters manage their own quests and progress automatically.', 'hint')); }
      for (var f = 0; f < flags.length; f++) button(automation,flags[f][1] + ': ' + (bot[flags[f][0]] ? 'On' : 'Off'),{ action:'setting',name:bot.name,setting:flags[f][0],enabled:!bot[flags[f][0]] },bot.closing,'setting-card' + (bot[flags[f][0]] ? ' on' : ''));
      var spacing = subPanel(body,'spacing','Follow and attack spacing'); renderSpacing(spacing,bot);
      if (!bot.rangedSpacing) spacing.appendChild(node('p','Ranged spacing controls apply to ranged companions. This companion uses its native role spacing.','empty-state'));
      if (bot.focus && bot.focus.available) renderFocus(subPanel(body,'focus','Optional activity focus'), bot);

    } else if (botTab === 'equipment') {

      if (bot.temporary && (bot.stigmaBuilds || []).length) renderStigmaBuilds(subPanel(body,'build','Stigma build'), bot);
      if (bot.loadout) renderLoadout(subPanel(body,'saved','Saved gear'), bot);
      if (!ticketRecruit(bot.growth)) renderGearPolicy(subPanel(body,'policy','Equipment rules'), bot);
      var equipped = [], carried = [];
      for (var i = 0; i < bot.inventory.length; i++) (bot.inventory[i].equipped ? equipped : carried).push(bot.inventory[i]);
      var profile = subPanel(body,'profile','Character equipment'); renderProfile(profile,bot,equipped);
      var inventory = subPanel(body,'inventory','Inventory');
      inventory.appendChild(node('p','Items belong to this companion. Hover for native item details; select a slot for available actions.','hint'));
      itemGrid(inventory,bot,carried,'inventory','Inventory (' + carried.length + (bot.inventoryLimit != null ? ' / ' + bot.inventoryLimit : '') + ')',renderItem);
      renderTransfer(subPanel(body,'transfer','Transfer items'),bot);

      var warehousePanel = subPanel(body,'warehouse','Private warehouse'), legionPanel = subPanel(body,'legion','Legion storage');
      if (bot.world) { warehousePanel.appendChild(node('p','Independent bots retain ownership of their private warehouse.','empty-state')); legionPanel.appendChild(node('p','Inviting an independent bot does not grant access to its stored possessions.','empty-state')); }
      if (!bot.world) {
        var warehouse = bot.warehouse || [];
        warehousePanel.appendChild(node('h3', 'Private warehouse (' + warehouse.length + ' / ' + (bot.warehouseLimit || 0) + ')'));
        warehousePanel.appendChild(node('p', bot.warehouseReason || 'Deposit or withdraw a whole stack while you and your companion are beside the same warehouse keeper. Items stay owned by this companion.', 'hint availability-note'));
        if (!warehouse.length) warehousePanel.appendChild(node('p', 'The companion\u2019s private warehouse is empty.', 'empty'));
        itemGrid(warehousePanel,bot,warehouse,'warehouse','Stored items',renderWarehouseItem);
        var deposits = bot.inventory || [], bankable = [], legionable = [];
        for (var d = 0; d < deposits.length; d++) { if (deposits[d].bankable) bankable.push(deposits[d]); if (deposits[d].legionStorable) legionable.push(deposits[d]); }
        itemGrid(warehousePanel,bot,bankable,'bankdeposit','Inventory available to deposit',function(parent,bot,item) { var row = node('div',null,'item'); itemHeading(row,item); button(row,'Deposit whole stack',{action:'bankdeposit',name:bot.name,item:item.id},bot.closing || !bot.warehouseAvailable); parent.appendChild(row); });
        var shared = bot.legionStorage || [];
        legionPanel.appendChild(node('h3', 'Legion storage: ' + (bot.legionStorageName || 'No access') + ' (' + shared.length + ' / ' + (bot.legionStorageLimit || 0) + ')'));
        legionPanel.appendChild(node('p', bot.legionStorageReason || 'Deposit a whole stack beside the legion keeper. Deposited items belong to the legion and can be withdrawn by members with native withdrawal rights.', 'hint availability-note'));
        if (!shared.length) legionPanel.appendChild(node('p', 'No legion items to display.', 'empty'));
        itemGrid(legionPanel,bot,shared,'legion','Legion items',function(parent,bot,item) { var row = node('div',null,'item'); itemHeading(row,item); row.appendChild(node('p','Legion-owned item. Use the native legion warehouse for withdrawals.','hint')); parent.appendChild(row); });
        itemGrid(legionPanel,bot,legionable,'legiondeposit','Inventory available to deposit',function(parent,bot,item) { var row = node('div',null,'item'); itemHeading(row,item); button(row,'Deposit whole stack to legion',{action:'legiondeposit',name:bot.name,item:item.id},bot.closing || !bot.legionStorageAvailable); parent.appendChild(row); });

      }

    } else if (botTab === 'mail') {
      if (bot.world) { body.appendChild(node('p', 'World bots retain their private mailbox. Inviting one does not grant access to its mail or money.', 'hint')); }
      else {
        var inbox = subPanel(body,'inbox','Inbox'), send = subPanel(body,'send','Send to ' + state.owner);
        var mails = bot.mail || [], locked = bot.closing || !bot.mailAvailable;
        inbox.appendChild(node('h3', 'Mailbox (' + (bot.mailCount || 0) + ')'));
        inbox.appendChild(node('p', bot.mailReason || 'Use a regular mailbox with your companion. Collected items and Kinah go into its own cube. Requested outgoing mail goes to you and charges native postage.', 'hint availability-note'));
        button(inbox, 'Refresh mailbox', { action: 'maillist', name: bot.name }, locked);
        if (!bot.mailLoaded) inbox.appendChild(node('p', 'Refresh beside a mailbox to load this companion’s letters.', 'empty'));
        else if (!mails.length) inbox.appendChild(node('p', 'The companion’s mailbox is empty.', 'empty'));
        for (var m = 0; m < mails.length; m++) {
          var letter = mails[m], mailRow = node('div', null, 'item');
          mailRow.appendChild(node('div', letter.title + ' · From ' + letter.sender + (letter.unread ? ' · Unread' : '')));
          mailRow.appendChild(node('p', letter.message, 'detail'));
          if (letter.hasAttachments) mailRow.appendChild(node('p', (letter.item || '') + (letter.kinah ? ' ' + letter.kinah + ' Kinah' : ''), 'detail'));
          button(mailRow, 'Read', { action: 'mailread', name: bot.name, object: letter.id }, locked);
          button(mailRow, 'Collect attachments', { action: 'mailtake', name: bot.name, object: letter.id }, locked || !letter.hasAttachments);
          button(mailRow, 'Delete empty letter', { action: 'maildelete', name: bot.name, object: letter.id }, locked || letter.hasAttachments);
          inbox.appendChild(mailRow);
        }
        send.appendChild(node('h3', 'Request mail to ' + state.owner));
        send.appendChild(node('p', bot.mailSendEnabled ? 'Choose one whole tradeable, unpacked stack, or a Kinah amount. Native postage is paid by this companion. Equipped items and private world-bot possessions cannot be requested.' : 'Companion sending is disabled by server configuration; mailbox collection remains available.', 'hint'));
        var outgoing = (bot.inventory || []).filter(function (item) { return item.mailable; });
        for (var o = 0; o < outgoing.length; o++) {
          var outgoingRow = node('div', null, 'item'); outgoingRow.appendChild(node('div', outgoing[o].name + ' ×' + outgoing[o].count));
          button(outgoingRow, 'Mail whole stack to me', { action: 'mailsenditem', name: bot.name, object: outgoing[o].id }, locked || !bot.mailSendEnabled);send.appendChild(outgoingRow);
        }
        var moneyLabel = node('label', 'Kinah to request'), money = node('input');money.type = 'text';money.inputMode = 'numeric';money.setAttribute('aria-label','Kinah to request');money.value = '';draftField(money,bot,'mail-kinah');moneyLabel.appendChild(money);send.appendChild(moneyLabel);
        var moneyButton = button(send,'Mail Kinah to me',{},locked || !bot.mailSendEnabled);
        moneyButton.setAttribute('data-action','mailsendkinah');moneyButton.onclick = function () {
          if (!/^[1-9][0-9]{0,18}$/.test(money.value) || money.value.length === 19 && money.value > '9223372036854775807') { notice('Enter a positive whole Kinah amount within the native currency limit.', true);return; }
          act({ action:'mailsendkinah',name:bot.name,kinah:money.value });
        };
      }

    } else if (botTab === 'quests') {
      var journal = subPanel(body,'journal','Quest journal'), requests = subPanel(body,'requests','Catch-up requests'); renderQuestions(requests,bot);

      if (!bot.world) button(journal, 'Nearby quest combat: ' + (bot.questCombat ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'questCombat', enabled: !bot.questCombat }, bot.closing, bot.questCombat ? 'on' : '');

      if (bot.world) journal.appendChild(node('p', 'This world character manages its own quests. Party orders do not disable its autonomous quest progress.', 'hint'));
      else journal.appendChild(node('p', 'When On, the party tackles isolated quest targets within 25 m of you. Tanks lead; healers and support keep their roles. Untouched nearby packs are left for you to pull.', 'hint'));

      journal.appendChild(node('h3', 'Quest journal (' + bot.quests.length + ')'));

      if (bot.mission) { var missionRow = node('div', null, 'mission'); missionRow.appendChild(node('p', 'Quest ' + bot.mission + ' \u00b7 ' + label(bot.missionStatus))); button(missionRow, 'Cancel quest mission', { action: 'mission', name: bot.name, quest: 0 }, bot.closing); journal.appendChild(missionRow); }

      if (!bot.quests.length) journal.appendChild(node('p', 'Share an eligible quest or enable nearby quests.', 'empty'));

      var journalStart = pageControls(journal, bot.id, journalPages, bot.quests.length, 4);
      for (var q = journalStart; q < Math.min(bot.quests.length, journalStart + 4); q++) { var row = node('div', null, 'quest'); journal.appendChild(row); renderQuest(row, bot.quests[q]); if (!bot.world && bot.quests[q].missionEligible) button(row, 'Assign quest mission', { action: 'mission', name: bot.name, quest: bot.quests[q].id }, bot.closing || bot.dead); }

    } else if (botTab === 'care') {
      renderCare(body, bot);
      if (bot.generated || bot.world) renderBenefits(subPanel(body,'benefits','Benefits earned by this companion'), bot);
      if (bot.giveOnly) renderRecycling(subPanel(body,'recycle','Recycle a ticket recruit'), bot);
    }

    else { body.appendChild(node('h3', 'Recent activity')); if (!bot.announcements || !bot.announcements.length) body.appendChild(node('p', 'Companion announcements will appear here.', 'empty')); else for (var msg = bot.announcements.length - 1; msg >= 0; msg--) body.appendChild(node('p', bot.announcements[msg], 'activity-row')); }

    parent.appendChild(card); renderSubTabs(body,bot); compactHelp(body); setBusy();

  }

  function renderQuestions(parent, bot) {

    var questions = bot.questions || [];

    for (var ask = 0; ask < questions.length; ask++) {

      var question = questions[ask], prompt = node('div', null, 'catch-up'); prompt.appendChild(node('strong', question.name + ' \u00b7 Catch-up request')); prompt.appendChild(node('p', question.reason, 'hint'));

      if (question.prerequisites.length) { var missing = []; for (var m = 0; m < question.prerequisites.length; m++) missing.push(question.prerequisites[m].name); prompt.appendChild(node('p', 'Still needed: ' + missing.join(', '), 'hint')); }

      button(prompt, 'Yes, I will help', { action: 'answer', name: bot.name, quest: question.quest, answer: 'yes' }, bot.closing); button(prompt, 'No, cancel this quest', { action: 'answer', name: bot.name, quest: question.quest, answer: 'no' }, bot.closing); parent.appendChild(prompt);

    }

  }

  function coordinates(destination) { return destination.name + ' \u00b7 ' + (destination.mapName || 'Map ' + destination.map) + ' \u00b7 X ' + Math.round(destination.x) + ', Y ' + Math.round(destination.y) + ', Z ' + Math.round(destination.z) + (destination.distance == null ? '' : ' \u00b7 ' + destination.distance + ' m from you'); }
  function questStatus(quest) { return quest.ready ? 'Ready to turn in' : quest.status === 'START' ? 'In progress' : label(quest.status); }
  function canShowOnMap() { return !!(window.AionObject && AionObject.CodexAction); }
  function showOnMap(map, x, y, z) { AionObject.CodexAction('help-location', map, Math.round(x * 100), Math.round(y * 100), Math.round(z * 100)); }

  function renderQuest(parent, quest) {

    parent.appendChild(node('strong', (quest.name || 'Quest ' + quest.id) + ' \u00b7 ' + questStatus(quest)));

    parent.appendChild(node('p', quest.guidance || 'Lead this companion to its quest objective.', 'hint'));

    var objectives = quest.objectives || [];

    for (var o = 0; o < objectives.length; o++) parent.appendChild(node('p', objectives[o].name + ': ' + objectives[o].current + ' / ' + objectives[o].required, 'route'));

    var destinations = quest.destinations || [];

    for (var d = 0; d < destinations.length; d++) parent.appendChild(node('p', coordinates(destinations[d]), 'route'));

    if (!destinations.length) parent.appendChild(node('p', 'No known NPC location for this step. Follow your quest journal.', 'hint'));

  }

  // One line per quest, a page at a time. Journals open once per companion, not once per quest.
  var QUEST_PAGE = 7, questBoard = { page:0, ready:false }, helpBoard = { page:0, open:'', canHelp:null };
  function helpWhere(c) { return c.mapName || 'Unknown map'; }
  function helpFacts(parent, c) {
    if (c.botLevel) parent.appendChild(node('p', 'Level ' + c.botLevel + ' ' + label(c.botClass) + ' \u00b7 Gear rarity ' + label(c.rarity), 'hint'));
    var counts = c.objectives || [];
    for (var i = 0; i < counts.length; i++) parent.appendChild(node('p', counts[i].name + ': ' + counts[i].current + (counts[i].required < 0 ? '' : ' / ' + counts[i].required), 'route'));
    parent.appendChild(node('p', helpWhere(c) + ' \u00b7 X ' + Math.round(c.x) + ', Y ' + Math.round(c.y) + ', Z ' + Math.round(c.z), 'hint'));
  }
  function helpControls(parent, c) {
    var controls = node('div', null, 'actions'), locate = localButton(controls, 'Show location', function () { showOnMap(c.map, c.x, c.y, c.z); });
    locate.disabled = !canShowOnMap(); parent.appendChild(controls); return controls;
  }
  function renderHelpRequests() {
    var parent = byId('help-requests'), mine = state.helpRequest, active = !!(mine && mine.key && mine.status !== 'CLAIMED' && mine.status !== 'ABANDONED'), all = state.helpPosts || [], posts = [], helpable = 0; clear(parent);
    function redraw() { keepScroll(renderHelpRequests); }
    for (var i = 0; i < all.length; i++) if (!active || all[i].bot !== mine.bot) { posts.push(all[i]); if (all[i].eligible) helpable++; }
    var board = node('div', null, 'quest-board help-board'), head = node('div', null, 'quest-board-head'), reward = (mine && mine.reward) || (posts[0] && posts[0].reward) || '';
    head.appendChild(node('h3', 'Help requests')); board.appendChild(head); parent.appendChild(board);
    board.appendChild(node('p', 'Accept a world companion\'s request, then join through its group invitation. ' + reward, 'hint'));
    if (mine && mine.key) {
      var panel = node('div', null, 'help-mine'), title = node('div', null, 'help-mine-head');
      title.appendChild(node('strong', mine.botName + ' \u00b7 ' + mine.need)); title.appendChild(node('span', label(mine.status))); panel.appendChild(title);
      helpFacts(panel, mine);
      var controls = helpControls(panel, mine);
      if (active) {
        button(controls, 'Track', { action:'helptrack' }, !mine.myCharacter || !(window.AionObject && AionObject.HelpRequestTrack));
        button(controls, 'Collect reward', { action:'helpclaim' }, mine.status !== 'COMPLETE');
        localButton(controls, 'Abandon request', function () { helpAbandon = mine.key; redraw(); }, 'quiet');
        if (helpAbandon === mine.key) button(controls, 'Confirm abandon', { action:'helpabandon' }, false);
        if (!mine.myCharacter) panel.appendChild(node('p', 'Track this request on the character that accepted it.', 'hint'));
        if (mine.status === 'REVISED' || mine.status === 'CANCELLED' || mine.status === 'RESOLVED') panel.appendChild(node('p', 'This request ended or changed. Credit you already earned stays, but no partial reward is paid. Abandon it before choosing another.', 'hint'));
      }
      board.appendChild(panel);
    }
    if (!posts.length) { board.appendChild(node('p', 'No world companions need help right now. An accepted request stays saved when its listing ends.', 'hint')); return; }
    var canHelp = helpBoard.canHelp === null ? helpable > 0 : helpBoard.canHelp && helpable > 0;
    var filters = node('div', null, 'quest-filters');
    function filter(text, value) { var choice = localButton(filters, text, function () { helpBoard.canHelp = value; helpBoard.page = 0; redraw(); }, canHelp === value ? 'on' : ''); if (value && !helpable) choice.disabled = true; }
    filter('All ' + posts.length, false); filter('Can help ' + helpable, true); head.appendChild(filters);
    var shown = posts.filter(function (c) { return !canHelp || c.eligible; });
    var pages = Math.max(1, Math.ceil(shown.length / QUEST_PAGE)); helpBoard.page = Math.min(helpBoard.page, pages - 1);
    var first = helpBoard.page * QUEST_PAGE, table = node('div', null, 'quest-table'), titles = node('div', null, 'quest-line help-line quest-titles');
    titles.appendChild(node('span', 'Companion')); titles.appendChild(node('span', 'Request')); titles.appendChild(node('span', 'Where')); titles.appendChild(node('span', 'Level')); table.appendChild(titles);
    shown.slice(first, first + QUEST_PAGE).forEach(function (c) {
      var key = String(c.bot), open = helpBoard.open === key, line = node('div', null, 'quest-line help-line t' + summonTier(c.rarity) + (c.eligible ? '' : ' locked'));
      var name = node('strong', c.botName), need = node('span', c.need), levels = node('span', 'Lv ' + c.minLevel + '-' + c.maxLevel + (c.eligible ? '' : ', you are ' + c.level));
      name.title = 'Gear rarity ' + label(c.rarity); need.title = c.need;
      line.appendChild(name); line.appendChild(need); line.appendChild(node('span', helpWhere(c))); line.appendChild(levels);
      localButton(line, open ? 'Hide' : 'Details', function () { helpBoard.open = open ? '' : key; redraw(); }, 'small quest-show');
      table.appendChild(line);
      if (!open) return;
      var more = node('div', null, 'help-detail'); helpFacts(more, c);
      var actions = helpControls(more, c);
      button(actions, 'Accept request', { action:'helpaccept', bot:c.bot }, !c.eligible || active, 'primary');
      if (active) more.appendChild(node('p', 'Finish or abandon your current request first.', 'hint'));
      table.appendChild(more);
    });
    board.appendChild(table);
    if (pages > 1) {
      var pager = node('div', null, 'quest-pager');
      var back = localButton(pager, '\u2039 Previous', function () { helpBoard.page--; redraw(); }); back.disabled = helpBoard.page === 0;
      pager.appendChild(node('span', (first + 1) + '\u2013' + Math.min(first + QUEST_PAGE, shown.length) + ' of ' + shown.length));
      var next = localButton(pager, 'Next \u203a', function () { helpBoard.page++; redraw(); }); next.disabled = helpBoard.page >= pages - 1;
      board.appendChild(pager);
    }
  }

  function renderOverview() {
    var parent = byId('quest-overview'), groups = {}, order = []; clear(parent);
    if (!state.active.length) { parent.appendChild(node('div', 'Recruit companions to track their quests together.', 'empty')); return; }
    var body = node('div', null, 'quest-board'); parent.appendChild(body);
    var head = node('div', null, 'quest-board-head'); head.appendChild(node('h3', 'Party quest tracker')); body.appendChild(head);
    var journals = node('div', null, 'quest-journals'); journals.appendChild(node('span', 'Open journal'));
    for (var j = 0; j < state.active.length; j++) (function (bot) { localButton(journals, bot.name, function () { openBot(bot.id, 'quests'); }, 'quest-member'); }(state.active[j]));
    body.appendChild(journals);
    for (var a = 0; a < state.active.length; a++) if ((state.active[a].questions || []).length) { var requestRow = node('div', null, 'catch-up'), asking = state.active[a]; requestRow.appendChild(node('strong', asking.name + ' \u00b7 ' + asking.questions.length + ' catch-up request(s)')); (function (id) { localButton(requestRow, 'Review requests', function () { openBot(id, 'quests'); }); }(asking.id)); body.appendChild(requestRow); }
    for (var b = 0; b < state.active.length; b++) {
      var bot = state.active[b];
      for (var q = 0; q < bot.quests.length; q++) {
        var quest = bot.quests[q], key = String(quest.id);
        if (!groups[key]) { groups[key] = { quest: quest, total: 0, ready: 0, states: {}, place: null, placeReady: false, bot: bot.id }; order.push(key); }
        var group = groups[key], status = questStatus(quest);
        group.total++; if (quest.ready) group.ready++;
        group.states[status] = (group.states[status] || 0) + 1;
        // A companion ready to turn in decides where the party goes next.
        var destinations = quest.destinations || []; if (destinations.length && (!group.place || (quest.ready && !group.placeReady))) { group.place = destinations[0]; group.placeReady = quest.ready; group.bot = bot.id; }
      }
    }
    if (!order.length) { body.appendChild(node('p', 'Companion quests will appear here as they are accepted.', 'hint')); return; }
    var readyCount = order.filter(function (k) { return groups[k].ready > 0; }).length;
    if (!readyCount) questBoard.ready = false;
    var filters = node('div', null, 'quest-filters');
    function filter(text, ready) { var choice = localButton(filters, text, function () { questBoard.ready = ready; questBoard.page = 0; renderOverview(); }, questBoard.ready === ready ? 'on' : ''); if (ready && !readyCount) choice.disabled = true; }
    filter('All ' + order.length, false); filter('Ready to turn in ' + readyCount, true); head.appendChild(filters);
    var shown = order.filter(function (k) { return !questBoard.ready || groups[k].ready > 0; });
    shown.sort(function (x, y) { return (groups[y].ready > 0) - (groups[x].ready > 0) || String(groups[x].quest.name).localeCompare(String(groups[y].quest.name)); });
    var pages = Math.max(1, Math.ceil(shown.length / QUEST_PAGE)); questBoard.page = Math.min(questBoard.page, pages - 1);
    var first = questBoard.page * QUEST_PAGE, table = node('div', null, 'quest-table'), titles = node('div', null, 'quest-line quest-titles');
    titles.appendChild(node('span', 'Quest')); titles.appendChild(node('span', 'Progress')); titles.appendChild(node('span', 'Where to go')); table.appendChild(titles);
    shown.slice(first, first + QUEST_PAGE).forEach(function (k) {
      var entry = groups[k], line = node('div', null, 'quest-line' + (entry.ready ? ' ready' : '')), parts = [];
      for (var name in entry.states) if (Object.prototype.hasOwnProperty.call(entry.states, name)) parts.push(name + ' ' + entry.states[name] + '/' + entry.total);
      line.appendChild(node('strong', entry.quest.name || 'Quest ' + entry.quest.id)); line.appendChild(node('span', parts.join(' \u00b7 ')));
      if (entry.place) {
        var where = node('span', entry.place.name + ' \u00b7 ' + (entry.place.mapName || 'Map ' + entry.place.map)); where.title = coordinates(entry.place); line.appendChild(where);
        var show = localButton(line, 'Show', function () { showOnMap(entry.place.map, entry.place.x, entry.place.y, entry.place.z); }, 'small quest-show'); show.disabled = !canShowOnMap();
      } else localButton(line, 'See quest journal', function () { openBot(entry.bot, 'quests'); }, 'quest-link');
      table.appendChild(line);
    });
    body.appendChild(table);
    if (pages > 1) {
      var pager = node('div', null, 'quest-pager');
      var back = localButton(pager, '\u2039 Previous', function () { questBoard.page--; renderOverview(); }); back.disabled = questBoard.page === 0;
      pager.appendChild(node('span', (first + 1) + '\u2013' + Math.min(first + QUEST_PAGE, shown.length) + ' of ' + shown.length));
      var next = localButton(pager, 'Next \u203a', function () { questBoard.page++; renderOverview(); }); next.disabled = questBoard.page >= pages - 1;
      body.appendChild(pager);
    }
  }

  // Stigma builds (config/playerbots/stigma-builds.properties): the chosen build's Stigmas are equipped first, out of combat.
  // Item transfer: your bag on the left, the companion's on the right; one whole stack or a Kinah amount per move.
  var keepNotice = false, transferPick = {}, pendingTransfer = null, pendingTransferTimer = null, pendingRecycle = null, recycleTimer = null, recyclePoll = null, recycleSource = null, mailTarget = null;
  function bagGrid(parent, title, items, key) {
    var box = node('section', null, 'bag'), grid = node('div', null, 'cube-grid');
    box.appendChild(node('h3', title));
    for (var i = 0; i < items.length; i++) (function (item) {
      var cell = node('div', null, 'cube-cell'), tile = node('button', null, 'cube-slot' + (String(transferPick[key]) === String(item.id) ? ' selected' : ''));
      cell.appendChild(itemIcon(item, 'cube-icon')); tile.type = 'button'; tile.setAttribute('data-local-control', 'true');
      tile.title = nativeItemLink(item) || item.name; tile.setAttribute('aria-label', item.name + ', ' + item.count);
      tile.appendChild(node('span', item.count > 1 ? item.count : '', 'stack-count'));
      tile.onclick = function () { transferPick[key] = String(transferPick[key]) === String(item.id) ? null : item.id; keepScroll(function () { renderSelection(); renderRoster(); }); };
      cell.appendChild(tile); grid.appendChild(cell);
    }(items[i]));
    if (!items.length) grid.appendChild(node('p', 'Nothing tradeable here.', 'empty-cube'));
    box.appendChild(grid);
    var picked = null; for (var n = 0; n < items.length; n++) if (String(items[n].id) === String(transferPick[key])) picked = items[n];
    box.appendChild(node('p', picked ? picked.name + ' ×' + picked.count : 'Select a stack to move it.', 'bag-pick'));
    parent.appendChild(box); return picked;
  }
  function kinahField(parent, id, text) {
    var row = node('label', text), input = node('input'); input.id = id; input.type = 'text'; input.setAttribute('inputmode', 'numeric'); input.placeholder = '0';
    row.appendChild(input); parent.appendChild(row); return input;
  }
  function wholeKinah(input) { return /^[1-9][0-9]{0,18}$/.test(input.value) ? input.value : null; }
  function renderTransfer(parent, bot) {
    if (bot.world) { parent.appendChild(node('p', 'Invited world bots keep their own items.', 'empty-state')); return; }
    var ids = (state.movable || {})[bot.name] || [], theirs = [], ownKey = 'own', botKey = 'bot' + bot.id;
    for (var i = 0; i < (bot.inventory || []).length; i++) if (ids.indexOf(bot.inventory[i].id) !== -1) theirs.push(bot.inventory[i]);
    parent.appendChild(node('p', bot.giveOnly ? 'Give a whole tradeable stack or Kinah to ' + bot.name + ' outside combat. Ticket recruits keep what you give them.' : 'Move a whole tradeable stack or Kinah between you and ' + bot.name + ', anywhere outside combat. Equipped, soul-bound and quest items stay where they are.', 'hint'));
    var layout = node('div', null, 'transfer'), mine = bagGrid(layout, 'Your bag', state.ownerBag || [], ownKey);
    var middle = node('div', null, 'transfer-arrows');
    var give = button(middle, 'Give →', { action: 'transfer', name: bot.name, direction: 'give', item: mine ? mine.id : 0 }, !mine || bot.closing, 'primary');
    layout.appendChild(middle);
    var picked = bagGrid(layout, bot.name + '’s bag', theirs, botKey);
    var take = button(middle, '← Take', { action: 'transfer', name: bot.name, direction: 'take', item: picked ? picked.id : 0 }, !picked || bot.closing || bot.giveOnly, 'primary');
    give.onclick = function () { transferPick[ownKey] = null; act({ action: 'transfer', name: bot.name, direction: 'give', item: mine.id }); };
    take.onclick = function () { transferPick[botKey] = null; act({ action: 'transfer', name: bot.name, direction: 'take', item: picked.id }); };
    parent.appendChild(layout);
    if (bot.giveOnly) parent.appendChild(node('p', 'Ticket companions keep their items and Kinah. Giving is permanent.', 'hint'));
    var money = node('div', null, 'transfer-kinah'), amount = kinahField(money, 'transfer-kinah-' + bot.id, 'Kinah (you have ' + (state.ownerKinah || 0) + ')');
    var giveK = button(money, 'Give Kinah →', {}, bot.closing), takeK = button(money, '← Take Kinah', {}, bot.closing || bot.giveOnly);
    giveK.onclick = function () { var k = wholeKinah(amount); if (!k) { notice('Enter a whole Kinah amount.', true); return; } act({ action: 'transfer', name: bot.name, direction: 'give', kinah: k }); };
    takeK.onclick = function () { var k = wholeKinah(amount); if (!k) { notice('Enter a whole Kinah amount.', true); return; } act({ action: 'transfer', name: bot.name, direction: 'take', kinah: k }); };
    draftField(amount, bot, 'transfer-kinah'); parent.appendChild(money);
    if (bot.giveOnly) renderCompanionTransfer(parent, bot);
  }
  function renderCompanionTransfer(parent, bot) {
    var allowed = (state.companionMovable || {})[bot.name];
    if (!allowed) return;
    var panel = node('section', null, 'control-panel'), items = [], targets = [];
    panel.appendChild(node('h3', 'Give to another ticket recruit'));
    panel.appendChild(node('p', 'Move one eligible whole stack between your active ticket recruits. Equipped, bound, saved-loadout and protected quest or crafting items stay with their current companion.', 'hint'));
    for (var i = 0; i < (bot.inventory || []).length; i++) if (allowed.indexOf(bot.inventory[i].id) !== -1) items.push(bot.inventory[i]);
    for (var n = 0; n < state.active.length; n++) {
      var candidate = state.active[n];
      if (candidate.id !== bot.id && !candidate.closing && Object.prototype.hasOwnProperty.call(state.companionMovable || {}, candidate.name)) targets.push(candidate.name);
    }
    var picked = bagGrid(panel, bot.name + '\u2019s transferable items', items, 'collection' + bot.id);
    if (!targets.length) panel.appendChild(node('p', 'Recruit another ticket companion from this account to receive items.', 'hint'));
    else {
      var targetLabel = node('label', 'Receiving ticket recruit'); panel.appendChild(targetLabel);
      var target = select(panel, targets, targets[0]);
      target.id = 'companion-transfer-target-' + bot.id; targetLabel.htmlFor = target.id;
      draftField(target, bot, 'companion-transfer-target');
      var send = localButton(panel, 'Give selected stack', function () {
        if (!picked) return;
        transferPick['collection' + bot.id] = null;
        act({action:'companiontransfer',name:bot.name,target:target.value,item:picked.id});
      }, 'primary');
      send.disabled = busy || bot.closing || !picked;
    }
    parent.appendChild(panel);
  }
  function renderMailAlt(parent) {
    var panel = node('section', null, 'mail-alt'), head = node('div', null, 'preset-head');
    head.appendChild(node('h4', 'Mail items to ' + mailTarget));
    head.appendChild(node('span', 'Express mail: arrives in their mailbox at once; normal postage applies', 'preset-meta'));
    var actions = node('div', null, 'preset-actions'); localButton(actions, 'Close', function () { mailTarget = null; renderRoster(); }, 'quiet small'); head.appendChild(actions);
    panel.appendChild(head);
    var picked = bagGrid(panel, 'Your bag', state.ownerBag || [], 'mail');
    var send = button(panel, 'Mail this stack', {}, !picked, 'primary');
    send.onclick = function () { transferPick.mail = null; act({ action: 'sendalt', alt: mailTarget, item: picked.id }); };
    var money = node('div', null, 'transfer-kinah'), amount = kinahField(money, 'mail-alt-kinah', 'Kinah (you have ' + (state.ownerKinah || 0) + ')');
    var sendK = button(money, 'Mail Kinah', {}, false); sendK.onclick = function () { var k = wholeKinah(amount); if (!k) { notice('Enter a whole Kinah amount.', true); return; } act({ action: 'sendalt', alt: mailTarget, kinah: k }); };
    panel.appendChild(money); parent.appendChild(panel);
  }

  var stoneOpen = {}, loadoutOpen = {}, loadoutAuto = false;
  function renderStigmaBuilds(parent, bot) {
    var builds = bot.stigmaBuilds || []; if (!bot.temporary || !builds.length) return;
    var box = node('section', null, 'stigma-builds');
    box.appendChild(node('p', 'Your companion swaps to the chosen build the next time it is out of combat.', 'hint'));
    // Automatic is a default, not a build to compare: one line above the choices.
    var automatic = node('div', null, 'build-auto' + ((bot.stigmaBuild || 'AUTO') === 'AUTO' ? ' active' : ''));
    automatic.appendChild(node('strong', 'Automatic')); automatic.appendChild(node('span', 'Chooses Stigmas for the companion’s combat role.'));
    if ((bot.stigmaBuild || 'AUTO') === 'AUTO') automatic.appendChild(node('em', 'In use', 'status-pill'));
    else button(automatic, 'Use automatic', { action: 'stigmabuild', name: bot.name, build: 'AUTO' }, bot.closing, 'small');
    box.appendChild(automatic);
    var choices = builds;
    for (var i = 0; i < choices.length; i++) (function (build) {
      var active = (bot.stigmaBuild || 'AUTO') === build.key, card = node('div', null, 'build-card' + (active ? ' active' : ''));
      card.appendChild(node('strong', build.name)); card.appendChild(node('p', build.text, 'hint'));
      var buttons = node('div', null, 'build-actions'), stones = node('div', null, 'build-stones'); stones.style.display = stoneOpen[build.key] ? 'block' : 'none';
      for (var n = 0; n < build.stigmas.length; n++) { var stone = node('span', build.stigmas[n], 'build-stone' + (n < 6 ? ' core' : '')); stones.appendChild(stone); }
      if (build.stigmas.length) {
        card.appendChild(stones);
        var toggle = localButton(buttons, (stoneOpen[build.key] ? 'Hide' : 'Show') + ' stones', function () { stoneOpen[build.key] = !stoneOpen[build.key]; stones.style.display = stoneOpen[build.key] ? 'block' : 'none'; toggle.textContent = (stoneOpen[build.key] ? 'Hide' : 'Show') + ' stones'; }, 'build-toggle small');
      }
      if (build.savedStages) card.appendChild(node('p', 'Saved equipment: ' + build.savedStages + (build.savedStages === 1 ? ' level range' : ' level ranges'), 'hint'));
      if (active) buttons.appendChild(node('span', 'In use', 'status-pill'));
      else button(buttons, 'Use this build', { action: 'stigmabuild', name: bot.name, build: build.key }, bot.closing, 'primary small');
      card.appendChild(buttons); box.appendChild(card);
    }(choices[i]));
    parent.appendChild(box);
  }

  var LOADOUT_STATUS = { SAVED: 'Saved', HELD: 'Not usable now', MISSING: 'Item gone', GAP: 'Gap', AUTO: 'Automatic' };
  function loadoutNote(row) {
    var goal = row.target ? row.source + (row.moreSources ? ' (+' + row.moreSources + ' more)' : '') + '.' + (row.usableAt ? ' Usable at level ' + row.usableAt + '.' : '') + (row.market ? ' Also tradable on the Central Market.' : '') : '';
    if (row.status === 'HELD') return row.saved + ' is held but cannot be worn at this level or with this weapon choice. Wearing the best fallback.';
    if (row.status === 'MISSING') return row.saved + ' is no longer owned. Wearing the best fallback. ' + (row.target ? 'Goal: ' + goal : 'No recorded source to plan from.');
    if (row.status === 'GAP') return goal;
    if (row.status === 'SAVED' && row.saved !== row.worn) return 'Putting on ' + row.saved + ' when out of combat.';
    return '';
  }
  function renderLoadout(parent, bot) {
    var plan = bot.loadout; if (!plan) return;
    var box = node('section', null, 'loadout-plan'); box.appendChild(node('h3', plan.build + ' equipment, levels ' + plan.from + '–' + plan.to));
    box.appendChild(node('p', (ticketRecruit(bot.growth) ? 'Equipment is automatic, up to ' + label(bot.growth.rarity) + ' gear rarity. ' : '') + 'Saved items are worn instead of automatic picks. Click a row for its goal and source.', 'hint'));
    if (!bot.gear) box.appendChild(node('p', 'Auto equipment is Off, so saved items are not put on until you turn it On.', 'hint'));
    if (!plan.ready) box.appendChild(node('p', 'Item sources are still loading. Gaps appear shortly.', 'hint'));
    var stages = node('div', null, 'loadout-stages'), current = 0, i;
    for (i = 0; i < plan.stages.length; i++) {
      var stage = plan.stages[i]; if (stage.current) current = stage.saved;
      stages.appendChild(node('span', stage.from + (stage.to > stage.from ? '–' + stage.to : '') + (stage.saved ? ' · ' + stage.saved : ''), 'loadout-stage' + (stage.current ? ' current' : '') + (stage.saved ? ' saved' : '')));
    }
    box.appendChild(stages);
    var actions = node('div', null, 'loadout-actions'); box.appendChild(actions);
    button(actions, 'Save worn gear for this level range', { action: 'loadoutsave', name: bot.name }, bot.closing, 'primary small');
    if (current) button(actions, 'Clear saved gear for this level range', { action: 'loadoutclear', name: bot.name }, bot.closing, 'quiet small');
    var automatic = [];
    for (i = 0; i < plan.rows.length; i++) (function (index) {
      var row = plan.rows[index], line = node('div', null, 'loadout-row ' + row.status.toLowerCase()), text = loadoutNote(row);
      if (row.status === 'AUTO') { automatic.push(line); line.style.display = loadoutAuto ? 'block' : 'none'; }
      line.appendChild(node('span', LOADOUT_STATUS[row.status], 'status-pill'));
      line.appendChild(node('span', row.slot, 'loadout-slot')); line.appendChild(node('span', (row.worn || 'Empty') + (row.target ? ' \u2192 ' + row.target : ''), 'loadout-worn'));
      if (text) {
        var note = node('p', text, 'hint'); if (row.sourceDetail) note.title = row.sourceDetail;
        note.style.display = loadoutOpen[index] ? 'block' : 'none'; line.className += ' expandable'; line.setAttribute('role', 'button'); line.setAttribute('tabindex', '0');
        line.onclick = function () { loadoutOpen[index] = !loadoutOpen[index]; note.style.display = loadoutOpen[index] ? 'block' : 'none'; };
        line.appendChild(note);
      }
      box.appendChild(line);
    }(i));
    if (automatic.length) {
      var more = localButton(box, (loadoutAuto ? 'Hide ' : 'Show ') + automatic.length + ' automatic slots', function () {
        loadoutAuto = !loadoutAuto; for (var a = 0; a < automatic.length; a++) automatic[a].style.display = loadoutAuto ? 'block' : 'none';
        more.textContent = (loadoutAuto ? 'Hide ' : 'Show ') + automatic.length + ' automatic slots';
      }, 'loadout-more');
    }
    parent.appendChild(box);
  }

  function renderGearPolicy(parent, bot) {

    var body = node('div', null, 'gear-policy'); parent.appendChild(body);

    if (!bot.temporary) { body.appendChild(node('p', bot.world ? 'Independent bot: equipment comes from its own earned loot, quests and native shops. Private ownership and progression stay with this bot.' : 'Player-owned alt: automatic equipment, build and Stigma management are disabled. Your existing setup is preserved; you can equip items manually below.', 'hint')); return; }

    body.appendChild(node('p', (bot.build || 'Temporary Bot') + '. Automatic native skills and Stigmas follow your role and level. Catch-up gear is checked every level, up to its permanent gear rarity. Only replaced system-generated gear is retired, and never an item saved in a build; earned equipment stays in your inventory.', 'hint'));

    button(body, 'Auto equipment: ' + (bot.gear ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'gear', enabled: !bot.gear }, bot.closing, bot.gear ? 'on' : '');

    var fields = {};

    function choice(key, title, options, value) { var row = node('label', title); fields[key] = select(row, options, value); fields[key].disabled = bot.closing; draftField(fields[key],bot,'gear-' + key); body.appendChild(row); }

    choice('mode', 'How equipment is gained', ['EARNED', 'STARTER', 'GENERATED'], bot.gearMode || 'EARNED');

    body.appendChild(node('p', 'Earned: loot, quest rewards and optional real shops. Starter: generates gear for empty slots once, then progresses through the game. Generated: adds useful level-appropriate upgrades automatically. Existing equipment is kept. Auto equipment must be On.', 'hint'));

    choice('profile', 'Equipment priority (independent of combat role)', ['AUTO', 'TANK', 'HEALER', 'DAMAGE', 'SUPPORT'], bot.gearProfile || 'AUTO');

    choice('quality', 'Maximum generated quality', ['COMMON', 'RARE', 'LEGEND', 'UNIQUE', 'EPIC', 'MYTHIC'], bot.gearQuality || 'LEGEND');

    choice('weapon', 'Preferred weapon', ['AUTO', 'SWORD', '2H_SWORD', 'DAGGER', 'MACE', 'ORB', 'SPELLBOOK', 'POLEARM', 'STAFF', 'BOW', 'HARP', 'GUN', 'CANNON', 'KEYBLADE'], bot.gearWeapon || 'AUTO');

    choice('rolls', 'Party dice rolls', ['PASS', 'UPGRADES', 'ALL'], bot.gearRolls || 'UPGRADES');

    choice('vendors', 'Buy equipment upgrades at nearby shops', ['false', 'true'], String(!!bot.gearVendors));

    function input(key, title, value) { var row = node('label', title); fields[key] = node('input'); fields[key].type = 'text'; fields[key].value = value; fields[key].disabled = bot.closing; draftField(fields[key],bot,'gear-' + key); row.appendChild(fields[key]); body.appendChild(row); }

    input('level', 'Generated item level limit (0 follows your level, up to the bot\u2019s level)', bot.gearLevel || 0);

    input('threshold', 'Upgrade score ratio (1.1 requires a 10% improvement)', bot.gearThreshold == null ? 1.1 : bot.gearThreshold);

    body.appendChild(node('p', 'Current generated level ceiling: ' + (bot.gearTargetLevel || bot.level) + '. Uses normal class, mastery, faction and equipment rules. Shop purchases share the Care reserve and daily budget; Kinah bids are always passed. Generated items are protected from automatic extraction.', 'hint'));

    var save = button(body, 'Save equipment choices', {action:'gearpolicy',name:bot.name}, bot.closing); save.onclick = function () { var request = { action: 'gearpolicy', name: bot.name }; for (var key in fields) if (fields.hasOwnProperty(key)) request[key] = fields[key].value; act(request); };

  }

  function renderFocus(parent, bot) {
    var focus = bot.focus;
    parent.appendChild(node('p', focus.scope, 'hint'));
    var focusLabel = node('label', 'Activity priority'), choice = node('select'); choice.id = 'focus-' + bot.id; focusLabel.htmlFor = choice.id;
    for (var i = 0; i < focus.choices.length; i++) { var option = node('option', focus.choices[i].name); option.value = focus.choices[i].id; choice.appendChild(option); }
    choice.value = focus.selected; parent.appendChild(focusLabel); parent.appendChild(choice); draftField(choice, bot, 'focus-choice');
    var professionLabel = node('label', 'Profession'), profession = node('select'); profession.id = 'focus-profession-' + bot.id; professionLabel.htmlFor = profession.id;
    var automatic = node('option', 'Best currently learned profession'); automatic.value = '0'; profession.appendChild(automatic);
    for (var n = 0; n < focus.professions.length; n++) { var p = focus.professions[n], opt = node('option', label(p.name) + ' \u00b7 ' + p.level); opt.value = p.id; profession.appendChild(opt); }
    profession.value = String(focus.profession); parent.appendChild(professionLabel); parent.appendChild(profession); draftField(profession, bot, 'focus-profession');
    var save = localButton(parent, 'Save activity focus', function () { act({action:'focus',name:bot.name,focus:choice.value,profession:profession.value}); }, 'primary'); save.disabled = busy || bot.closing;
    parent.appendChild(node('p', focus.knownRecipes + ' known native recipes \u00b7 ' + focus.reason, 'hint'));
    parent.appendChild(node('p', 'Instance PvE prioritizes equipment readiness; it does not enter a queue or leave your party. Gathering and crafting use real nearby sources, trainers, materials, tools and this companion\u2019s own budget.', 'hint'));
    renderMarket(parent, focus.market);
  }
  function renderMarket(parent, market) {
    if (!market || !market.available) return;
    parent.appendChild(node('h4', 'Central Market'));
    parent.appendChild(node('p', market.scope, 'hint'));
    if (market.held > 0) parent.appendChild(node('p', market.held.toLocaleString() + ' Kinah waiting in this companion\u2019s market wallet.', 'hint'));
    var rows = market.orders.concat(market.trades);
    if (!rows.length) parent.appendChild(node('p', 'No market activity yet.', 'hint'));
    for (var i = 0; i < rows.length; i++) parent.appendChild(node('p', rows[i].text, 'market-line'));
  }
  var passConfirmation = {}, recycleQuotes = {}, recycleTargets = {};
  var benefitPages = {}, missionPages = {}, journalPages = {};
  function pageControls(parent, key, pages, total, size) {
    var page = Math.min(pages[key] || 0, Math.max(0, Math.ceil(total / size) - 1)); pages[key] = page;
    var bar = node("div", null, "pagination"); parent.appendChild(bar);
    localButton(bar, "Previous", function () { pages[key] = page - 1; renderSelection(); byId('bot-detail').scrollTop = 0; }).disabled = page === 0;
    bar.appendChild(node("span", " Page " + (page + 1) + " / " + Math.max(1, Math.ceil(total / size)) + " "));
    localButton(bar, "Next", function () { pages[key] = page + 1; renderSelection(); byId('bot-detail').scrollTop = 0; }).disabled = (page + 1) * size >= total;
    return page * size;
  }
  function renderBenefits(parent, bot) {
    var frame = parent, chronicle = bot.chronicle || {}, collection = state.collectionRenown || {};
    var tabs = node('nav', null, 'tabs benefit-tabs'), summary = node('div', null, 'benefit-summary');
    var missionsPane = node('div', null, 'benefit-missions'), rewardsPane = node('div', null, 'benefit-rewards'), upgradesPane = node('div', null, 'benefit-upgrades');
    var key = 'benefit-view-' + bot.id, panes = {summary:summary, missions:missionsPane, rewards:rewardsPane, upgrades:upgradesPane};
    frame.appendChild(tabs); frame.appendChild(summary); frame.appendChild(missionsPane); frame.appendChild(rewardsPane); frame.appendChild(upgradesPane);
    function choose(view) {
      selectedDetails[key] = view;
      for (var id in panes) if (panes.hasOwnProperty(id)) panes[id].style.display = id === view ? 'block' : 'none';
      var buttons = tabs.querySelectorAll('button');
      for (var i = 0; i < buttons.length; i++) buttons[i].setAttribute('aria-selected', buttons[i].getAttribute('data-benefit-view') === view ? 'true' : 'false');
    }
    var labels = [['summary','Summary'],['missions','Activity missions'],['rewards','Pass rewards']];
    if (!bot.world && bot.generated) labels.push(['upgrades','Pass upgrades']);
    for (var i = 0; i < labels.length; i++) (function (id, title) {
      localButton(tabs, title, function () { choose(id); byId('bot-detail').scrollTop = 0; }).setAttribute('data-benefit-view', id);
    }(labels[i][0], labels[i][1]));
    var grid = node('div', null, 'overview-grid benefit-grid'); summary.appendChild(grid);
    var chapter = overviewTile(grid, 'Chronicle of Atreia');
    overviewRow(chapter, 'Chapters', (chronicle.chapters || 0) + ' / ' + (chronicle.chapterGoal || 5));
    overviewRow(chapter, 'Own completed quests', chronicle.completedQuests || 0);
    chapter.appendChild(node('p', chronicle.pending ? 'Recent quest progress awaits native save.' : 'Earned from this character’s own quest history.', 'hint'));
    if (bot.giveOnly && collection.enabled) {
      var renown = overviewTile(grid, 'Collection Renown');
      overviewRow(renown, 'Combat / Life / Legend', collection.combat + ' / ' + collection.life + ' / ' + collection.legend);
      overviewRow(renown, 'Credited recruits', collection.creditedBots);
      button(renown, collection.stipendClaimed ? 'Stipend claimed today' : 'Claim ' + collection.stipend + ' Kinah', {action:'collectionstipend',name:bot.name}, bot.closing || collection.stipendClaimed || !(collection.stipend > 0), 'primary');
      renown.appendChild(node('p', 'One daily collection stipend. Recruits also claim it automatically while idle.', 'hint'));
    }
    var data = state.benefits;
    if (!data || String(data.id) !== String(bot.id) || data.error) {
      var loading = overviewTile(grid, 'Personal Season Pass'); loading.appendChild(node('p', data && data.error ? data.error : 'Refresh to load this companion’s Pass.', 'hint'));
      localButton(loading, 'Refresh Pass', function () { request(null); });
      choose('summary'); return;
    }
    var pass = data.pass, season = pass.season || {}, personal = overviewTile(grid, season.name || 'Personal Season Pass');
    overviewRow(personal, 'Level / XP', pass.level + ' / ' + pass.xp);
    overviewRow(personal, 'Own Kinah', pass.kinah);
    overviewRow(personal, 'Pass tier', pass.tier >= 2 ? 'Grace of the Tower' : pass.tier >= 1 ? 'Charter of Atreia' : 'Free');
    if (pass.queuedActivity) personal.appendChild(node('p', pass.queuedActivity + ' activities awaiting native save.', 'hint'));
    localButton(personal, 'Refresh Pass', function () { request(null); }, 'secondary').disabled = busy || bot.closing;
    if (pass.canManage && pass.active && pass.tier < 2) {
      var upgrades = upgradesPane; upgrades.appendChild(node('h3', 'Optional Pass upgrades'));
      upgrades.appendChild(node('p', 'Paid from ' + bot.name + '’s own Kinah. Purchases require your confirmation.', 'hint'));
      for (var tier = 1; tier <= 2; tier++) (function (tier) {
        if (pass.tier >= tier) return;
        var cost = (tier === 1 ? season.premiumKinah : season.advancedKinah) - (pass.tier === 1 ? season.premiumKinah : 0);
        var title = tier === 1 ? 'Charter of Atreia / Premium' : 'Grace of the Tower / Advanced Premium';
        if (passConfirmation[bot.id] === tier) {
          upgrades.appendChild(node('p', 'Pay ' + cost + ' Kinah from ' + bot.name + ' for ' + title + '?', 'detail'));
          button(upgrades, 'Confirm purchase · ' + cost + ' Kinah', {action:'passaction',passAction:'purchase',name:bot.name,tier:tier}, bot.closing || pass.kinah < cost, 'primary');
          localButton(upgrades, 'Cancel purchase', function () { delete passConfirmation[bot.id]; renderSelection(); }, 'secondary');
        } else localButton(upgrades, 'Review ' + title + ' · ' + cost + ' Kinah', function () { passConfirmation[bot.id] = tier; renderSelection(); }, 'secondary').disabled = busy || bot.closing || pass.kinah < cost;
      }(tier));
    }
    summary.appendChild(node('p', bot.world ? 'This independent world character owns its progress, Pass and items. These pages show its status.' : 'Rewards and boxes are managed automatically while idle, outside party combat. Optional manual claims remain available.', 'benefit-note'));
    summary.appendChild(node('p', 'Veteran’s Coffer is unavailable for generated companions.', 'benefit-note'));
    var missions = pass.missions || [], missionStart = pageControls(missionsPane, bot.id, missionPages, missions.length, 5);
    for (var m = missionStart; m < Math.min(missions.length, missionStart + 5); m++) {
      var mission = missions[m], missionRow = node('div', null, 'benefit-mission');
      missionRow.appendChild(node('strong', mission.name));
      missionRow.appendChild(node('p', mission.progress + ' / ' + mission.target + (mission.complete ? ' · Complete' : ' · ' + mission.xp + ' XP'), 'route')); missionsPane.appendChild(missionRow);
    }
    var rewards = pass.rewards || [], available = 0;
    for (var r = 0; r < rewards.length; r++) if (rewards[r].available) available++;
    if (pass.canManage) button(rewardsPane, 'Claim ' + available + ' available rewards', {action:'passaction',passAction:'claimAll',name:bot.name}, !available || bot.closing, 'primary');
    rewardsPane.appendChild(node('p', 'Available rewards, reward mail and boxes are handled automatically while idle and outside party combat.', 'benefit-note'));
    var rewardStart = pageControls(rewardsPane, bot.id, benefitPages, rewards.length, 5);
    for (var n = rewardStart; n < Math.min(rewards.length, rewardStart + 5); n++) {
      var reward = rewards[n], row = node('div', null, 'item benefit-reward');
      row.appendChild(node('strong', 'Lv. ' + reward.level + ' · ' + (reward.track === 0 ? 'Free' : reward.track === 1 ? 'Premium' : 'Advanced') + ' · ' + reward.name + ' x' + reward.quantity));
      row.appendChild(node('span', reward.claimed ? 'Claimed' : reward.available ? 'Available' : 'Locked', 'reward-status'));
      if (reward.available && pass.canManage) button(row, 'Claim reward', {action:'passaction',passAction:'claim',name:bot.name,level:reward.level,track:reward.track}, bot.closing);
      rewardsPane.appendChild(row);
    }
    choose(selectedDetails[key] || 'summary');
  }
  var recyclePages = {};
  function stopRecycling(message) {
    pendingRecycle = null; window.clearTimeout(recycleTimer); window.clearTimeout(recyclePoll);
    if (message) { if (state) { invalidateViews(); renderCurrentView(false); } notice(message,true); }
  }
  function clearPendingRecruit() {
    var pending = pendingRecycle || pendingTransfer;
    pendingTransfer = null; window.clearTimeout(pendingTransferTimer); stopRecycling();
    if (state && pending) { invalidateViews(); renderCurrentView(false); }
  }
  function openRecycling(bot) {
    if (busy || pendingRecycle) return;
    recycleSource = bot; subTabs.care = 'recycle'; openBot(bot.id,'care');
  }
  function recyclingTargets(bot) {
    var targets = [], receivers = state.recycleReceivers || [];
    for (var i = 0; i < receivers.length; i++) if (String(receivers[i].id) !== String(bot.id) && receivers[i].needed > 0 && receivers[i].rank !== 'EXCELLENT') targets.push(receivers[i]);
    return targets;
  }
  function startRecycling(bot, target) {
    if (busy || pendingRecycle) return;
    delete recycleQuotes[String(bot.id)]; recycleSource = bot;
    pendingRecycle = {source:bot,target:target,waiting:null}; advanceRecycling();
  }
  function advanceRecycling() {
    var flow = pendingRecycle; if (!flow || busy) return;
    if (currentView !== 'party' || String(selectedBot) !== String(flow.source.id) || botTab !== 'care' || subTabs.care !== 'recycle') { stopRecycling(); return; }
    var source = activeBot(flow.source.id), target = activeBot(flow.target.id);
    if (source && source.closing || target && target.closing) { stopRecycling('A companion is saving or leaving the party. Refresh before trying again.'); return; }
    if (flow.waiting && !activeBot(flow.waiting.id)) {
      window.clearTimeout(recyclePoll); recyclePoll = window.setTimeout(function () { if (pendingRecycle === flow) request(null); },1000); return;
    }
    window.clearTimeout(recycleTimer); window.clearTimeout(recyclePoll); flow.waiting = null;
    if (source && target) {
      stopRecycling(); recycleTargets[source.id] = target.name; subTabs.care = 'recycle'; openBot(source.id,'care');
      act({action:'recyclequote',name:source.name,target:target.name}); return;
    }
    flow.waiting = !source ? flow.source : flow.target;
    recycleTimer = window.setTimeout(function () { if (pendingRecycle === flow) stopRecycling('The companion did not arrive. Refresh before trying again.'); },30000);
    invalidateViews(); renderCurrentView(false);
    notice('Bringing ' + flow.source.name + ' and ' + flow.target.name + ' into your party. Waiting for ' + flow.waiting.name + '.',false);
    act({action:'recruit',name:flow.waiting.name});
  }
  function renderRecycling(parent, bot) {
    parent.appendChild(node('p', 'Recycling permanently archives this recruit. Their items and stored Kinah cannot be withdrawn. All three displayed rewards are granted together once.', 'hint'));
    if (!state.recyclingEnabled) { parent.appendChild(node('p', 'Recycling is disabled.', 'hint')); return; }
    if (pendingRecycle && String(pendingRecycle.source.id) === String(bot.id)) {
      parent.appendChild(node('p', 'Bringing ' + pendingRecycle.source.name + ' and ' + pendingRecycle.target.name + ' into your party. Both must be here before you can review the rewards.', 'detail'));
      if (pendingRecycle.waiting) parent.appendChild(node('p', 'Waiting for ' + pendingRecycle.waiting.name + '\u2026', 'hint'));
      localButton(parent,'Cancel',function () { stopRecycling(); invalidateViews(); renderSelection(); },'secondary'); return;
    }
    var targets = recyclingTargets(bot), target = null;
    if (!targets.length) { parent.appendChild(node('p', 'Recycling needs another ticket recruit that can still gain Learning XP.', 'hint')); return; }
    for (var t = 0; t < targets.length; t++) if (targets[t].name === recycleTargets[bot.id]) target = targets[t];
    if (!target) target = targets[0]; recycleTargets[bot.id] = target.name;
    parent.appendChild(node('h3', 'Who receives the Learning XP?'));
    parent.appendChild(node('p', 'Choose another ticket recruit. Both companions will be brought into your party before you review the rewards.', 'hint'));
    var start = targets.length > 5 ? pageControls(parent,bot.id,recyclePages,targets.length,5) : 0;
    for (var i = start; i < Math.min(targets.length,start + 5); i++) (function (candidate) {
      var chosen = candidate.name === target.name;
      var pick = localButton(parent,candidate.name + ' \u00b7 ' + label(candidate.playerClass) + ' \u00b7 ' + label(candidate.rank) + ' \u00b7 ' + (activeBot(candidate.id) ? 'In your party' : 'Saved'),function () {
        recycleTargets[bot.id] = candidate.name; delete recycleQuotes[String(bot.id)]; renderSelection();
      },'recycle-choice' + (chosen ? ' on' : ''));
      pick.setAttribute('aria-pressed',chosen ? 'true' : 'false');
    }(targets[i]));
    parent.appendChild(node('p', target.name + ' can gain ' + target.needed + ' more Learning XP.', 'detail'));
    var prepare = localButton(parent, activeBot(bot.id) && activeBot(target.id) ? 'Preview exact recycling rewards' : 'Bring both into the party and preview rewards',function () { startRecycling(bot,target); },'secondary');
    prepare.disabled = busy || bot.closing || (!activeBot(bot.id) && (!bot.ready || bot.reserved));
    var quote = recycleQuotes[String(bot.id)];
    if (quote && quote.target === target.name && activeBot(bot.id) && activeBot(target.id)) {
      parent.appendChild(node('p', 'Account: +0.33 Ticket credit \u00b7 ' + quote.target + ': +' + quote.learningXp + ' Learning XP \u00b7 Your inventory: +' + quote.kinah + ' Kinah', 'detail'));
      parent.appendChild(node('p', quote.appraisedItems + ' eligible owned items appraised at ' + quote.itemAppraisal + ' Kinah. ' + quote.warning, 'hint'));
      var expired = quote.expiresAt <= new Date().getTime();
      button(parent, expired ? 'Quote expired \u00b7 preview again' : 'Confirm permanent recycling of ' + bot.name, {action:'recycle',name:bot.name,target:quote.target,quote:quote.key,confirm:'yes'}, bot.closing || expired, 'danger');
    }
  }
  function renderCare(parent, bot) {

    var body = subPanel(parent,'maintenance','Equipment care'); body.className += ' care-fields'; body.appendChild(node('h3', 'Equipment care'));

    if (!bot.temporary) { body.appendChild(node('p', bot.world ? 'Independent bots retain their own care settings and funds.' : 'Automatic enchanting and extraction apply only to Temporary Bots. Your alt retains its existing items.', 'hint')); return; }

    var draft = careDrafts[bot.id];

    button(body, 'Auto enchant: ' + (bot.enchant ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'enchant', enabled: !bot.enchant }, bot.closing, bot.enchant ? 'on' : '');

    button(body, 'Extract unused loot: ' + (bot.salvage ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'salvage', enabled: !bot.salvage }, bot.closing, bot.salvage ? 'on' : '');

    body.appendChild(node('p', 'Uses this companion\u2019s own stones, tools and Kinah. Visits nearby real shops when needed. Enchants ordinary equipped armour toward +5 using native success/failure rules. Extracts expendable new loot; protects existing possessions, upgrades, quest items and valuable gear.', 'hint'));

    body = subPanel(parent,'budget','Spending limits');
    var reserveLabel = node('label', 'Minimum Kinah reserve (at least 10,000)'), reserve = node('input'); reserve.id = 'care-reserve'; reserve.type = 'text'; reserve.value = draft ? draft.reserve : (bot.reserve == null ? '10000' : bot.reserve); reserve.disabled = bot.closing; reserveLabel.appendChild(reserve); body.appendChild(reserveLabel);

    var budgetLabel = node('label', 'Daily purchase budget (0 disables purchases)'), budget = node('input'); budget.id = 'care-budget'; budget.type = 'text'; budget.value = draft ? draft.dailyBudget : (bot.dailyBudget == null ? '50000' : bot.dailyBudget); budget.disabled = bot.closing; budgetLabel.appendChild(budget); body.appendChild(budgetLabel);

    reserve.oninput = budget.oninput = function () { careDrafts[bot.id] = { reserve: reserve.value, dailyBudget: budget.value }; };

    reserve.onchange = reserve.oninput; budget.onchange = budget.oninput;

    var save = button(body, 'Save spending limits', { action: 'carebudget' }, bot.closing); save.onclick = function () { act({ action: 'carebudget', name: bot.name, reserve: reserve.value, dailyBudget: budget.value }); };

    draftField(reserve,bot,'care-reserve'); draftField(budget,bot,'care-budget');
    body.appendChild(node('p', 'Bought today: ' + (bot.spent || 0) + ' Kinah. Also keeps at least 25% of its current balance.', 'hint'));

  }

  function renderItem(parent, bot, item) {

    var row = node('div', null, 'item'); row.setAttribute('data-item-id',item.id); itemHeading(row,item);

    if (item.equipped && item.appearanceSet) {
      row.appendChild(node('p', 'Appearance: ' + item.skinName + ' (combat stats kept)', 'hint'));
      button(row, 'Restore original look', { action: 'resetappearance', name: bot.name, target: item.id }, bot.closing);
    }
    if (item.appearanceTargets && item.appearanceTargets.length) {
      var looks = node('select'); looks.setAttribute('aria-label', 'Combat equipment to transmog with ' + item.name);
      for (var t = 0; t < item.appearanceTargets.length; t++) { var option = node('option', item.appearanceTargets[t].name); option.value = item.appearanceTargets[t].id; looks.appendChild(option); }
      draftField(looks,bot,'look-' + item.id); row.appendChild(looks);
      var transmog = button(row, 'Use as transmog', {}, bot.closing);
      transmog.onclick = function () { act({ action: 'appearance', name: bot.name, item: item.id, target: looks.value }); };
      row.appendChild(node('p', 'Keeps combat stats and this appearance item. The look follows this slot through gear upgrades.', 'hint'));
    }

    if (!item.equipped && item.slots.length) {

      var slot = draftField(select(row, item.slots),bot,'slot-' + item.id), equip = button(row, 'Equip', { action: 'equip', name: bot.name, item: item.id, slot: slot.value }, bot.closing);

      equip.onclick = function () { act({ action: 'equip', name: bot.name, item: item.id, slot: slot.value }); };

    }

    if (!bot.world && item.legionStorable) button(row, 'Deposit to legion', { action: 'legiondeposit', name: bot.name, item: item.id }, bot.closing || !bot.legionStorageAvailable);
    if (!bot.world && item.bankable) button(row, 'Deposit', { action: 'bankdeposit', name: bot.name, item: item.id }, bot.closing || !bot.warehouseAvailable);

    parent.appendChild(row);

  }

  function renderWarehouseItem(parent, bot, item) {
    var row = node('div', null, 'item'); row.setAttribute('data-item-id',item.id); itemHeading(row,item);
    button(row, 'Withdraw', { action: 'bankwithdraw', name: bot.name, item: item.id }, bot.closing || !bot.warehouseAvailable);
    parent.appendChild(row);
  }

  function updateClasses() {

    if (!state) return;
    var model = byId('model'), temporaryDisabled = state.generatedEnabled === false;
    model.querySelector('option[value="generate"]').disabled = temporaryDisabled;
    if (temporaryDisabled && model.value === 'generate') model.value = 'create';
    var temporaryNotice = byId('temporary-recruitment-notice');
    if (temporaryNotice) temporaryNotice.style.display = temporaryDisabled ? 'block' : 'none';

    var accountCharacter = byId('model').value === 'create';
    var journey = state.creationJourney, journeySelect = byId('new-journey'), previousJourney = journeySelect.value;
    clear(journeySelect);
    var play = node('option', 'Play through ' + journey.starter + ' · level 1'); play.value = 'level1'; journeySelect.appendChild(play);
    if (journey.enabled) {
      var skip = node('option', 'Skip ' + journey.starter + ' · level ' + journey.skipLevel); skip.value = 'skip'; journeySelect.appendChild(skip);
    }
    if (previousJourney === 'skip' && journey.enabled) journeySelect.value = 'skip';
    var skipJourney = accountCharacter && journeySelect.value === 'skip';

    var classes = accountCharacter ? (skipJourney ? journey.classes : state.startingClasses) : state.classes, el = byId('new-class'), previous = el.value; clear(el);

    for (var i = 0; i < classes.length; i++) { var opt = node('option', label(classes[i])); opt.value = classes[i]; el.appendChild(opt); }

    if (classes.indexOf(previous) !== -1) el.value = previous;

    byId('starting-level').style.display = accountCharacter ? 'none' : 'block';
    byId('creation-journey').style.display = accountCharacter ? 'block' : 'none';

    byId('model-hint').textContent = accountCharacter ? (skipJourney ?
      'Choose Your Journey: starts at level ' + journey.skipLevel + ' in ' + journey.capital + ' with your selected advanced class. Skipped quest rewards arrive in its mailbox. Complete the capital ceremony normally. Uses a normal character slot.' :
      'Begins at level 1 in ' + journey.starter + '. Uses a normal character slot; quests, class advancement and rewards follow normal progression.') :
      'Temporary Bot: matches your level, receives class and role gear, native skills and Stigmas. ' + (state.generatedRandomLooks ? 'Randomized looks keep your height. ' : '') + 'Automatically follows map and dungeon transitions. Dismissal keeps its private roster entry for later.';

  }

  var ticketRequest = '';
  function freshTicketRequest() { return 'r' + new Date().getTime().toString(36) + Math.floor(Math.random() * 1e9).toString(36); }

  // Companion Recruitment. The saved result decides everything; the animation has no say in it.
  var RANKS = { POOR:0, ORDINARY:1, GOOD:2, GREAT:3, EXCELLENT:4 };
  var summon = { phase:'ready', timers:[], waiting:null, result:null, odds:false, start:'matched' };
  function summonTier(value) { var key = String(value || '').toUpperCase(); return Object.prototype.hasOwnProperty.call(RANKS,key) ? RANKS[key] : 1; }
  function summonLater(handler, delay) { summon.timers.push(window.setTimeout(handler, delay)); }
  function summonClear() { while (summon.timers.length) window.clearTimeout(summon.timers.pop()); }
  function summonPhase(phase) { summon.phase = phase; byId('summon').className = 'recruit-gate s-' + phase; }
  function summonRoll(parent, title, value, tier) {
    var row = node('div', null, 'summon-roll t' + tier); row.appendChild(node('span', title)); row.appendChild(node('strong', label(value))); parent.appendChild(row);
  }
  function summonPanel() {
    var t = state && state.tickets, actions = byId('summon-actions'), cards = byId('summon-card'), rolls = byId('summon-rolls'), hint = byId('summon-hint'), result = summon.result;
    clear(actions); clear(cards); clear(rolls); clear(byId('summon-result-card')); hint.textContent = '';
    byId('summon-balance').textContent = t ? t.tickets : '';
    byId('summon-credit').textContent = t && t.credit ? 'Recycle credit: ' + (t.credit / 100).toFixed(2) : '';
    byId('summon-start-matched').className = summon.start === 'matched' ? 'on' : ''; byId('summon-start-level1').className = summon.start === 'level1' ? 'on' : '';
    var go = byId('summon-go'), pending = !!(t && t.unfinished), broke = !t || (!pending && t.tickets < t.cost);
    go.textContent = pending ? 'Reveal your paid recruit' : 'Recruit \u00b7 ' + (t ? t.cost : 1) + ' Ticket'; go.disabled = broke || summon.phase !== 'ready' && summon.phase !== 'result';
    if (summon.phase === 'result' && result) {
      byId('summon-panel-title').textContent = 'Companion recruited';
      var medal = node('span', label(result.advancedClass).slice(0, 2), 'summon-medal'), who = node('span', null, 'summon-who');
      who.appendChild(node('strong', result.name)); who.appendChild(node('span', label(result.advancedClass)));
      cards.appendChild(medal); cards.appendChild(who);
      var shown = byId('summon-result-card'); shown.appendChild(cardPortrait(result.advancedClass, 'summon-mini', result.gender));
      var plate = node('span', null, 'summon-plate'); plate.appendChild(node('strong', result.name)); plate.appendChild(node('span', label(result.advancedClass))); shown.appendChild(plate);
      summonRoll(rolls, 'Auto-equip', result.gear, summonTier(result.gear)); summonRoll(rolls, 'Combat rank', result.rank, summonTier(result.rank));
      localButton(actions, 'View companion', function () { closeSummon(); byId('tab-roster').onclick(); });
      go.textContent = 'Recruit again \u00b7 ' + t.cost + ' Ticket';
      if (broke) hint.textContent = 'You have no Tickets left.';
    } else {
      byId('summon-panel-title').textContent = 'Recruit a companion';
      hint.textContent = pending ? 'Your Ticket is already spent. Revealing it costs nothing more.' : broke ? 'You need a Recruitment Ticket to recruit.' : 'Each of the ' + (t ? t.classCount : 11) + ' classes is equally likely. A name is chosen for you, and you can rename them later from their card.';
    }
    var odds = byId('summon-odds'); clear(odds); odds.style.display = summon.odds ? 'block' : 'none';
    if (summon.odds && t) {
      odds.appendChild(node('p', 'Auto-equip and Combat rank are drawn separately, each with these chances:', 'hint'));
      for (var tier in t.odds) if (Object.prototype.hasOwnProperty.call(t.odds, tier)) odds.appendChild(node('span', label(tier) + ' ' + t.odds[tier].toFixed(2) + '%'));
    }
  }
  function summonShowResult() { summonClear(); summonPhase('result'); summonPanel(); }
  // The frames and the companion's own 3D model play natively; a browser window cannot animate at this size.
  function nativePull(r) {
    if (!window.AionObject || !AionObject.CompanionPull) return;
    AionObject.CompanionPull([r.advancedClass, r.gear, r.rank, r.name].join(' '));
    if (AionObject.CompanionModel) AionObject.CompanionModel(r.model, r.look, r.items || '');
  }
  function summonWatch(started) {
    // The roll is stored by the server. Show it only once its answer is here, however long the gate has to charge.
    if (!summon.waiting) return;
    if (busy) { summonLater(function () { summonWatch(started); }, 120); return; }
    var t = state.tickets, key = summon.waiting; summon.waiting = null;
    if (t && t.last && t.last.request === key) {
      summon.result = t.last; ticketRequest = '';
      nativePull(t.last); summonShowResult(); return;
    }
    summonClear(); summonPhase('ready'); summonPanel();
    byId('summon-hint').textContent = byId('notice').textContent;
  }
  function summonRecruit() {
    var t = state && state.tickets; if (!t || busy || summon.waiting) return;
    if (t.unfinished) ticketRequest = t.unfinished.request;
    if (!ticketRequest || (t.last && t.last.request === ticketRequest)) ticketRequest = freshTicketRequest();
    summon.result = null; summon.waiting = ticketRequest;
    summonPhase('charge'); summonPanel();
    act({ action:'recruitticket', name:'', recruitRequest:ticketRequest, start:summon.start });
    if (!busy) { summon.waiting = null; summonClear(); summonPhase('ready'); summonPanel(); return; }
    summonLater(function () { summonWatch(new Date().getTime()); }, 120);
  }
  function openSummon() {
    summonClear(); summon.result = null; summon.waiting = null; summon.odds = false;
    byId('summon').style.display = 'block'; summonPhase('ready'); summonPanel();
  }
  function closeSummon() { if (summon.waiting) return; summonClear(); byId('summon').style.display = 'none'; }
  function summonEntry() { var t = state && state.tickets; byId('open-summon').style.display = t && t.enabled ? 'block' : 'none'; }
  byId('open-summon').onclick = function () { openSummon(); };
  byId('summon-go').onclick = function () { summonRecruit(); };
  byId('summon-start-matched').onclick = function () { summon.start = 'matched'; summonPanel(); };
  byId('summon-start-level1').onclick = function () { summon.start = 'level1'; summonPanel(); };
  byId('summon-close').onclick = function () { closeSummon(); };
  byId('summon-collection').onclick = function () { closeSummon(); byId('tab-roster').onclick(); };
  byId('summon-odds-toggle').onclick = function () { summon.odds = !summon.odds; summonPanel(); };
  document.addEventListener('keydown', function (event) { if (event.keyCode === 27 && byId('summon').style.display === 'block') closeSummon(); }, false);

  function updateCreation() {
    if (!state) return;
    var temporary = byId('model').value === 'generate', freeCount = state.freeGeneratedCount == null ? state.generatedCount : state.freeGeneratedCount;
    byId('create').querySelector('button').disabled = !state.enabled || (temporary ?
      !state.generatedEnabled || freeCount >= state.generatedLimit : !(state.recruitRemaining > 0));
    byId('creation-capacity').textContent = temporary ? 'Free-generation companions: ' + freeCount + ' / ' + state.generatedLimit +
      ' created. Full teams keep new bots in your roster for later recruitment.' : '';
  }

  var mainTabs = document.querySelectorAll('#main-tabs button[data-view]');

  for (var nav = 0; nav < mainTabs.length; nav++) mainTabs[nav].onclick = function () { showView(this.getAttribute('data-view')); };

  byId('browse-roster').onclick = byId('create-roster').onclick = function () { showView('roster'); };

  byId('roster-create').onclick = function () { showView('create'); };

  var rosterSearchTimer;
  function queueRosterSearch() { window.clearTimeout(rosterSearchTimer); rosterSearchTimer = window.setTimeout(function () { if (busy) queueRosterSearch(); else request(null); }, 250); }
  byId('roster-search').oninput = byId('roster-filter').onchange = byId('roster-sort').onchange = function () { rosterPage = 0; queueRosterSearch(); };
  byId('roster-class').onchange = byId('roster-rank').onchange = byId('roster-rarity').onchange = function () { rosterPage = 0; queueRosterSearch(); };

  byId('roster-prev').onclick = function () { if (rosterPage > 0) rosterPage--; request(null); };

  byId('roster-next').onclick = function () { rosterPage++; request(null); };

  byId('refresh').onclick = function () { request(null); };

  byId('model').onchange = function () { updateClasses(); updateCreation(); };

  byId('start-level').onchange = updateClasses;
  byId('new-journey').onchange = updateClasses;

  byId('create').onsubmit = function (event) { event.preventDefault(); act({ action: byId('model').value, name: byId('new-name').value, playerClass: byId('new-class').value, start: byId('model').value === 'create' ? byId('new-journey').value : byId('start-level').value }); };

  byId('share').onclick = function () { act({action:'share',name:'all',quest:byId('owner-quest').value}); };
  function updateShare() { if (!state) return; byId('share').disabled = busy || !state.quests.length || !state.active.length; byId('share').textContent = 'Share with party'; }

  function rosterSection(view) {
    rosterView = view;
    var names = ['characters','presets'];
    for (var i = 0; i < names.length; i++) {
      var panel = byId('roster-' + names[i]), tab = byId('roster-tab-' + names[i]), active = view === names[i];
      if (panel) panel.style.display = active ? 'block' : 'none';
      if (tab) { tab.className = active ? 'selected' : ''; tab.setAttribute('aria-selected',active ? 'true' : 'false'); tab.tabIndex = active ? 0 : -1; }
    }
    if (state && currentView === 'roster') renderCurrentView(true);
  }

  function disclosure(toggleId,panelId) {
    var toggle = byId(toggleId), panel = byId(panelId); if (!toggle || !panel) return;
    toggle.setAttribute('data-local-control','true'); toggle.setAttribute('aria-controls',panelId); toggle.setAttribute('aria-expanded','false'); panel.style.display = 'none';
    toggle.onclick = function () { var open = panel.style.display === 'none'; panel.style.display = open ? 'block' : 'none'; toggle.setAttribute('aria-expanded',open ? 'true' : 'false'); };
  }
  disclosure('keyboard-help','keyboard-panel'); disclosure('creation-help-toggle','creation-help');
  if (byId('keyboard-close')) { byId('keyboard-close').setAttribute('data-local-control','true'); byId('keyboard-close').onclick = function () { byId('keyboard-panel').style.display = 'none'; byId('keyboard-help').setAttribute('aria-expanded','false'); byId('keyboard-help').focus(); }; }
  var rosterTabs = document.querySelectorAll('[data-roster-view]');
  for (var rt = 0; rt < rosterTabs.length; rt++) { rosterTabs[rt].setAttribute('data-local-control','true'); rosterTabs[rt].onclick = function () { rosterSection(this.getAttribute('data-roster-view')); }; }
  rosterSection('characters');
  for (var mt = 0; mt < mainTabs.length; mt++) mainTabs[mt].tabIndex = mt ? -1 : 0;

  document.addEventListener('keydown',function (event) {
    var target = event.target || document.activeElement, code = event.which || event.keyCode;
    if (!target || target.tagName === 'INPUT' || target.tagName === 'SELECT' || target.tagName === 'TEXTAREA' || target.isContentEditable) return;
    if (event.ctrlKey || event.metaKey) return;
    if (event.altKey && code >= 49 && code <= 52) { event.preventDefault(); var name = ['party','roster','create','quests'][code-49]; showView(name); byId('tab-' + name).focus(); return; }
    if (event.altKey) return;
    if (code === 191 && event.shiftKey && byId('keyboard-help')) { event.preventDefault(); byId('keyboard-help').click(); return; }
    if (target.getAttribute('role') === 'tab' && (code === 37 || code === 39 || code === 36 || code === 35)) {
      var list = target.parentNode.querySelectorAll('[role="tab"]'), index = 0;
      for (var i = 0; i < list.length; i++) if (list[i] === target) index = i;
      index = code === 36 ? 0 : code === 35 ? list.length-1 : (index + (code === 39 ? 1 : -1) + list.length) % list.length;
      event.preventDefault(); if (!list[index].disabled) { var id = list[index].id; list[index].click(); if (byId(id)) byId(id).focus(); } return;
    }
    if (code === 191 && !event.shiftKey) { event.preventDefault(); showView('roster'); rosterSection('characters'); byId('roster-search').focus(); return; }
    if (code === 27) {
      if (byId('keyboard-panel') && byId('keyboard-panel').style.display !== 'none') { event.preventDefault(); byId('keyboard-panel').style.display = 'none'; byId('keyboard-help').setAttribute('aria-expanded','false'); byId('keyboard-help').focus(); }
      else if (currentView === 'party' && inspecting) { event.preventDefault(); inspecting = false; renderSelection(); var rows = byId('active').querySelectorAll('[data-bot-id]'); for (var n = 0; n < rows.length; n++) if (String(rows[n].getAttribute('data-bot-id')) === String(selectedBot)) rows[n].focus(); }
    }
  },false);

  skinChromeIcons();
  if (currentView !== 'party') showView(currentView);
  if (session) request(null); else notice('Open Companions from Additional Functions while logged in to your character.', true);

}());
