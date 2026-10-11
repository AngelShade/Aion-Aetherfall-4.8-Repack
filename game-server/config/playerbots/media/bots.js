(function () {

  'use strict';

  var state = null, busy = false, selectedDetails = {}, session = '', requestSerial = 0, spacingDrafts = {};

  var currentView = 'party', selectedBot = null, botTab = 'overview', rosterPage = 0, pageSize = 12, inspecting = false, careDrafts = {}, removalConfirmation = null, subTabs = {}, fieldDrafts = {}, teamDraft = {}, teamConfirmation = null;

  var itemSelections = {};

  var parts = location.search.substring(1).split('&');

  for (var p = 0; p < parts.length; p++) {

    var pair = parts[p].split('=');

    if (pair[0] === 'session_id') { try { session = decodeURIComponent((pair[1] || '').replace(/\+/g, ' ')); } catch (ignore) {} }

  }

  function byId(id) { return document.getElementById(id); }

  function node(tag, text, className) { var el = document.createElement(tag); if (text != null) el.textContent = text; if (className) el.className = className; return el; }

  function label(value) { if (value === 'RIDER') return 'Aethertech'; return String(value || '').toLowerCase().replace(/_/g, ' ').replace(/\b[a-z]/g, function (c) { return c.toUpperCase(); }); }

  function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); }

  function notice(text, error) { byId('notice').textContent = text; byId('notice').className = error ? 'error' : ''; }

  var sectionNames = {
    behavior: [['combat','Combat'],['automation','Quest automation'],['spacing','Spacing']],
    equipment: [['profile','Character'],['inventory','Inventory'],['build','Build choices'],['warehouse','Private warehouse'],['legion','Legion storage']],
    mail: [['inbox','Inbox'],['send','Send']], quests: [['journal','Journal'],['requests','Requests']],
    care: [['maintenance','Maintenance'],['budget','Spending budget']]
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
      var tab = localButton(tabs, title, function () { captureDrafts(); subTabs[botTab] = key; chooseSubTab(body, key); tab.focus(); });
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

  function renderProfile(parent,bot,items) {
    var layout = node('div',null,'character-profile'), slots = node('div',null,'profile-slots'), summary = node('div',null,'profile-identity');
    summary.appendChild(node('h3',bot.name)); summary.appendChild(node('p','Level ' + bot.level + ' ' + label(bot.playerClass)));
    summary.appendChild(node('p',label(bot.role) + ' \u00b7 Health ' + bot.health + '% \u00b7 Mana ' + bot.mana + '%','hint'));
    var names = ['HELMET','NECKLACE','SHOULDER','EARRINGS_LEFT','TORSO','EARRINGS_RIGHT','GLOVES','RING_LEFT','PANTS','RING_RIGHT','BOOTS','WAIST','MAIN_HAND','SUB_HAND','WINGS','PLUME','MAIN_OFF_HAND','SUB_OFF_HAND','POWER_SHARD_RIGHT','POWER_SHARD_LEFT'];
    for (var n = 0; n < names.length; n++) {
      var slot = node('div',null,'profile-slot'), present = null;
      for (var i = 0; i < items.length; i++) if ((items[i].equippedSlots || []).indexOf(names[n]) !== -1) present = items[i];
      slot.appendChild(node('span',label(names[n]),'slot-label'));
      if (present) { var icon = itemIcon(present); slot.appendChild(icon); slot.appendChild(node('span',present.name,'slot-name')); }
      else slot.appendChild(node('span','Empty','slot-empty'));
      slots.appendChild(slot);
    }
    layout.appendChild(summary); layout.appendChild(slots); parent.appendChild(layout);
    itemGrid(parent,bot,items,'equipped','Equipped items (' + items.length + ')',renderItem);
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

    if (current) el.value = current; parent.appendChild(el); return el;

  }

  function encode(values) { var encoded = []; for (var key in values) if (Object.prototype.hasOwnProperty.call(values, key)) encoded.push(encodeURIComponent(key) + '=' + encodeURIComponent(values[key])); return encoded.join('&'); }

  function request(values) {

    if (busy) return;

    captureDrafts(); var focus = captureFocus(), original = values;
    if (values) { values = {}; for (var key in original) if (Object.prototype.hasOwnProperty.call(original,key)) values[key] = original[key]; }
    busy = true; var serial = ++requestSerial; setBusy();

    var xhr = new XMLHttpRequest(), mutation = values !== null, finished = false, watchdog;

    xhr.open(mutation ? 'POST' : 'GET', '/market/companions/' + (mutation ? 'action' : 'state?session_id=' + encodeURIComponent(session)), true);

    xhr.timeout = 30000;

    if (mutation) { values.session_id = session; values.request = state.request; xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded'); }

    function finish() { if (finished || serial !== requestSerial) return false; finished = true; window.clearTimeout(watchdog); busy = false; setBusy(); return true; }

    xhr.onload = function () {

      if (!finish()) return;

      var result;

      try { result = JSON.parse(xhr.responseText); } catch (ignore) { if (state) state.request = ''; notice('Companions is unavailable. Refresh before trying again.', true); return; }

      if (!result || xhr.status !== 200 || result.error) { if (state) state.request = ''; notice(result && result.error || 'Refresh Companions to reconnect.', true); return; }

      if (!result || !Array.isArray(result.active) || !Array.isArray(result.roster) || !Array.isArray(result.quests) || !Array.isArray(result.classes) || !Array.isArray(result.roles) || typeof result.request !== 'string' || !result.request) { if (state) state.request = ''; notice('Companion data is incomplete. Refresh before trying again.',true); return; }

      if (mutation && values.action === 'spacing') { for (var d = 0; d < result.active.length; d++) if (result.active[d].name === values.name && Number(result.active[d].ownerSpacing) === Number(values.ownerSpacing) && Number(result.active[d].attackSpacing) === Number(values.attackSpacing)) delete spacingDrafts[result.active[d].id]; }

      if (mutation && values.action === 'carebudget') { for (var d = 0; d < result.active.length; d++) if (result.active[d].name === values.name && String(result.active[d].reserve) === String(values.reserve) && String(result.active[d].dailyBudget) === String(values.dailyBudget)) delete careDrafts[result.active[d].id]; }

      state = result; render(); restoreFocus(focus); notice(result.notice || 'Connected as ' + result.owner + '. Select a companion to manage its settings and equipment.', false);

    };

    function interrupted() { if (finish()) { if (state) state.request = ''; notice('Connection interrupted. Refresh to check the result before retrying.', true); } }

    xhr.onerror = xhr.ontimeout = interrupted;

    // Older native WebKit may accept xhr.timeout without enforcing it.

    watchdog = window.setTimeout(function () { interrupted(); xhr.abort(); }, 30000);

    try { xhr.send(mutation ? encode(values) : null); } catch (ignore) { interrupted(); }

  }

  function setBusy() {

    var controls = document.querySelectorAll('button,select,input');

    for (var i = 0; i < controls.length; i++) {

      if (controls[i].getAttribute('data-local-control') === 'true' || controls[i].getAttribute('role') === 'tab') continue;
      if (busy) { if (!controls[i].hasAttribute('data-disabled')) controls[i].setAttribute('data-disabled', controls[i].disabled ? 'true' : 'false'); controls[i].disabled = true; }

      else if (controls[i].hasAttribute('data-disabled')) { controls[i].disabled = controls[i].getAttribute('data-disabled') === 'true'; controls[i].removeAttribute('data-disabled'); }

    }

    byId('active').className = busy ? 'busy' : ''; byId('notice').setAttribute('aria-busy',busy ? 'true' : 'false');

  }

  function act(values) { if (!state || !state.request) { notice('Refresh Companions before submitting another action.', true); return; } request(values); }

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

    if (state) { if (view === 'roster') renderRoster(); if (view === 'party') renderSelection(); if (view === 'quests') renderOverview(); }

  }

  function openBot(id, tab) { selectedBot = id; if (tab) botTab = tab; inspecting = true; showView('party'); }

  function render() {

    captureDrafts(); metrics();
    byId('count').textContent = state.active.length + ' / ' + state.limit;
    byId('team-label').textContent = state.teamKind || 'Party';

    byId('roster-count').textContent = state.roster.length;

    byId('owner-label').textContent = state.owner;

    var pending = 0;

    for (var i = 0; i < state.active.length; i++) pending += (state.active[i].questions || []).length;

    byId('question-count').textContent = pending ? pending + ' requests' : '';



    if (!activeBot(selectedBot) && !rosterBot(selectedBot)) selectedBot = state.active.length ? state.active[0].id : null;

    renderSelection(); renderRoster(); renderOverview(); renderPresets(); updateClasses();
    byId('save-party').querySelector('button').disabled = !state.active.length || busy;

    var quest = byId('owner-quest'), previous = quest.value; clear(quest);

    for (var q = 0; q < state.quests.length; q++) { var opt = node('option', (state.quests[q].name || 'Quest ' + state.quests[q].id) + ' \u00b7 ' + label(state.quests[q].status)); opt.value = state.quests[q].id; quest.appendChild(opt); }

    if (previous) quest.value = previous;

    if (quest.selectedIndex < 0 && quest.options.length) quest.selectedIndex = 0;

    updateShare();

    updateCreation();

  }

  function botSummary(parent,bot) {
      var row = localButton(parent, null, function () { openBot(bot.id); }, 'bot-summary' + (String(selectedBot) === String(bot.id) ? ' selected' : ''));

      row.setAttribute('data-bot-id', bot.id); row.setAttribute('aria-pressed', String(selectedBot) === String(bot.id) ? 'true' : 'false');

      row.appendChild(node('strong', bot.name)); row.appendChild(node('span', 'Lv. ' + bot.level + ' ' + label(bot.playerClass) + ' \u00b7 ' + label(bot.role), 'meta'));

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
          var row = node('div',null,'human-member'); row.appendChild(node('strong',member.name + (member.self ? ' (you)' : '')));
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

  function renderSelection() {

    captureDrafts(); var savedFocus = captureFocus(), detailScroll = byId('bot-detail').scrollTop;
    var parent = byId('active'), scroll = parent.scrollTop; clear(parent);

    if (!state.active.length) parent.appendChild(node('p', 'No active companions yet. Recruit from your roster to build a party.', 'hint'));

    renderTeamGroups(parent); renderTeamControls();

    parent.scrollTop = scroll;

    document.querySelector('.party-layout').className = 'party-layout' + (inspecting ? ' inspecting' : '');

    var detailPanel = byId('bot-detail'); clear(detailPanel);

    var back = localButton(detailPanel, '\u2039 Back to active party', function () { inspecting = false; renderSelection(); }, 'secondary'); back.id = 'bot-back';

    var bot = activeBot(selectedBot), character = rosterBot(selectedBot);

    if (bot) renderBot(detailPanel, bot);

    else if (character) {

      var card = node('article', null, 'bot'), head = node('div', null, 'bot-header'); head.appendChild(node('p', 'ROSTER COMPANION', 'eyebrow')); head.appendChild(node('h3', character.name)); head.appendChild(node('p', rosterIdentity(character), 'badge')); card.appendChild(head);

      var body = node('div', null, 'bot-body'); body.appendChild(node('h3', character.generated ? 'Temporary Bot' : 'Account character')); body.appendChild(node('p', !character.ready ? 'Creation is pending.' : character.reserved ? 'This character is active or saving and cannot currently be recruited.' : 'This character is offline and in your roster.', 'hint'));

      body.appendChild(node('p', 'Recruit this companion to view their live equipment, quests, orders and care settings.', 'hint')); button(body, 'Recruit companion', { action: 'recruit', name: character.name }, !state.enabled || character.reserved || !character.ready || !(state.recruitRemaining > 0), 'primary'); localButton(body, 'Back to roster', function () { showView('roster'); }, 'secondary'); card.appendChild(body); detailPanel.appendChild(card);

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
  function renderPresets() {
    var parent = byId('saved-parties'), presets = state.presets || []; clear(parent);
    if (!presets.length) { parent.appendChild(node('p', 'Save your current party from the Party tab. You can mix player-owned alts and Temporary Bots.', 'empty')); return; }
    for (var i = 0; i < presets.length; i++) (function (preset) {
      var card = node('article', null, 'preset-card'); card.setAttribute('data-preset-id', preset.id); card.appendChild(node('h4', preset.name));
      var members = node('div', null, 'preset-members');
      for (var j = 0; j < preset.members.length; j++) { var m = preset.members[j]; members.appendChild(node('p', m.name + ' / ' + label(m.role) + ' / ' + (m.temporary ? 'Temporary Bot' : 'Player-owned alt'), 'hint')); }
      card.appendChild(members); var actions = node('div', null, 'roster-action');
      button(actions, 'Summon preset', { action: 'loadparty', preset: preset.id }, !state.enabled, 'primary');
      button(actions, 'Remove preset', { action: 'deleteparty', preset: preset.id }, false, 'secondary');
      card.appendChild(actions); parent.appendChild(card);
    }(presets[i]));
  }

  function renderRoster() {

    var parent = byId('roster'), query = byId('roster-search').value.toLowerCase().replace(/^\s+|\s+$/g, ''), filter = byId('roster-filter').value, sort = byId('roster-sort').value, rows = []; clear(parent);

    for (var i = 0; i < state.roster.length; i++) {

      var character = state.roster[i], live = activeBot(character.id);

      if (query && (character.name + ' ' + label(character.playerClass)).toLowerCase().indexOf(query) === -1) continue;

      if (filter === 'available' && (character.reserved || !character.ready || live)) continue;

      if (filter === 'active' && !live) continue;

      if (filter === 'generated' && !character.generated) continue;
      if (filter === 'saved' && (!character.generated || !isSavedBot(character.id))) continue;

      if (filter === 'characters' && character.generated) continue;

      rows.push(character);

    }

    rows.sort(function (a, b) { var first = sort === 'class' ? label(a.playerClass) : a.name, second = sort === 'class' ? label(b.playerClass) : b.name; if (sort === 'level' && (a.level || 0) !== (b.level || 0)) return (b.level || 0) - (a.level || 0); return first.toLowerCase() < second.toLowerCase() ? -1 : first.toLowerCase() > second.toLowerCase() ? 1 : String(a.name).localeCompare(String(b.name)); });

    var pages = Math.max(1, Math.ceil(rows.length / pageSize)); rosterPage = Math.min(rosterPage, pages - 1); var start = rosterPage * pageSize, end = Math.min(start + pageSize, rows.length);

    byId('roster-results').textContent = rows.length ? 'Showing ' + (start + 1) + '\u2013' + end + ' of ' + rows.length + ' companions' : 'No companions match your search.';

    byId('roster-page').textContent = 'Page ' + (rosterPage + 1) + ' of ' + pages; byId('roster-prev').disabled = rosterPage === 0; byId('roster-next').disabled = rosterPage >= pages - 1;

    if (!rows.length) parent.appendChild(node('div', state.roster.length ? 'Try another name, class or filter.' : 'Your roster is empty. Create a companion to get started.', 'empty'));

    for (var r = start; r < end; r++) (function (character) {

      var live = activeBot(character.id), row = node('div', null, 'roster-row'), identity = node('div', null, 'roster-identity'), status = node('div', null, 'roster-state'), action = node('div', null, 'roster-action'); row.setAttribute('data-bot-id', character.id);

      identity.appendChild(node('strong', character.name)); identity.appendChild(node('p', rosterIdentity(character), 'hint')); if (character.level != null || character.playerClass) identity.appendChild(node('p', character.generated ? 'Temporary Bot' : 'Account character', 'hint'));

      if (character.generated && isSavedBot(character.id)) identity.appendChild(node('span', 'Saved Temporary Bot', 'badge'));
      status.textContent = live ? 'In your party' : !character.ready ? 'Creation pending' : character.reserved ? 'Active or saving' : 'Offline \u00b7 available';

      var view = localButton(action, live ? 'Manage' : 'View', function () { openBot(character.id, 'overview'); }); view.setAttribute('data-action', 'view');

      if (character.generated && character.ready) button(action, isSavedBot(character.id) ? 'Save progress' : 'Save bot', { action: 'savebot', name: character.name }, live && live.closing, 'secondary');
      if (!live) button(action, character.generated && isSavedBot(character.id) ? 'Summon saved bot' : 'Recruit', { action: 'recruit', name: character.name }, !state.enabled || character.reserved || !character.ready || !(state.recruitRemaining > 0), 'secondary');

      removalControls(action, character, live);

      row.appendChild(identity); row.appendChild(status); row.appendChild(action); parent.appendChild(row);

    }(rows[r]));

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

  function renderBot(parent, bot) {

    var card = node('article', null, 'bot'), header = node('div', null, 'bot-header'), top = node('div', null, 'bot-top'), identity = node('div'); card.setAttribute('data-bot-id', bot.id);

    identity.appendChild(node('p', 'COMPANION / ' + label(bot.role).toUpperCase(), 'eyebrow')); identity.appendChild(node('h3', bot.name)); identity.appendChild(node('p', 'Level ' + bot.level + ' ' + label(bot.playerClass), 'badge')); top.appendChild(identity); top.appendChild(node('div', bot.closing ? 'Saving\u2026' : bot.dead ? 'Awaiting resurrection' : label(bot.order), 'bot-state')); header.appendChild(top);

    var health = node('div', null, 'vitals'); vitals(health, 'Health', bot.health, 'health'); vitals(health, 'Mana', bot.mana, 'mana'); header.appendChild(health); card.appendChild(header);

    if (bot.generated || bot.temporary) button(header, 'Save Temporary Bot', { action: 'savebot', name: bot.name }, bot.closing, 'secondary');
    var tabs = node('nav', null, 'tabs bot-tabs'), names = [['overview','Overview'],['behavior','Behavior'],['equipment','Equipment'],['mail','Mail'],['quests','Quests'],['care','Care'],['activity','Activity']]; tabs.setAttribute('role', 'tablist'); tabs.setAttribute('aria-label', bot.name + ' details');

    for (var t = 0; t < names.length; t++) (function (name, title) { var tab = localButton(tabs, title, function () { captureDrafts(); botTab = name; renderSelection(); var selected = byId('bot-tab-' + name); if (selected) selected.focus(); }, botTab === name ? 'selected' : ''); tab.id = 'bot-tab-' + name; tab.setAttribute('role','tab'); tab.setAttribute('aria-controls','bot-content'); tab.setAttribute('aria-selected',botTab === name ? 'true' : 'false'); tab.tabIndex = botTab === name ? 0 : -1; }(names[t][0], names[t][1])); card.appendChild(tabs);

    var body = node('div', null, 'bot-body'); body.id = 'bot-content'; body.setAttribute('role','tabpanel'); body.setAttribute('aria-labelledby','bot-tab-' + botTab); card.appendChild(body);

    if (botTab === 'overview') {

      body.appendChild(node('h3', 'Current status')); body.appendChild(node('p', bot.closing ? 'Saving and dismissing\u2026' : bot.dead ? 'Waiting for resurrection' : label(bot.status) + ' \u00b7 ' + bot.action, 'detail'));

      if (bot.mission) { var mission = node('div', null, 'mission'); mission.appendChild(node('p', 'Quest ' + bot.mission + ' \u00b7 ' + label(bot.missionStatus), 'route')); button(mission, 'Cancel quest mission', { action: 'mission', name: bot.name, quest: 0 }, bot.closing); body.appendChild(mission); }



      var roleRow = node('div', null, 'role-row'), roleLabel = node('label', 'Combat role'); roleLabel.htmlFor = 'bot-role'; roleRow.appendChild(roleLabel); var role = select(roleRow, state.roles, bot.role); role.id = 'bot-role'; role.disabled = bot.closing; role.onchange = function () { act({ action: 'role', name: bot.name, role: role.value }); }; body.appendChild(roleRow);

      renderQuestions(body, bot);

    } else if (botTab === 'behavior') {
      var combat = subPanel(body,'combat','Combat behavior');
      var settings = node('div', null, 'settings control-grid'), flags = [['area','Area attacks'],['supplies','Recovery and build supplies'],['gear','Auto equipment'],['loot','Auto loot']];

      for (var f = 0; f < flags.length; f++) button(settings, flags[f][1] + ': ' + (bot[flags[f][0]] ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: flags[f][0], enabled: !bot[flags[f][0]] }, bot.closing || (flags[f][0] === 'gear' && !bot.temporary), 'setting-card' + (bot[flags[f][0]] ? ' on' : '')); combat.appendChild(settings);
      var automation = subPanel(body,'automation','Quest automation'); automation.className += ' settings control-grid'; var flags = [['questing','Auto quests'],['questCombat','Nearby quest combat'],['partySync','Party completion sync']];
      for (var f = 0; f < flags.length; f++) button(automation,flags[f][1] + ': ' + (bot[flags[f][0]] ? 'On' : 'Off'),{ action:'setting',name:bot.name,setting:flags[f][0],enabled:!bot[flags[f][0]] },bot.closing,'setting-card' + (bot[flags[f][0]] ? ' on' : ''));
      var spacing = subPanel(body,'spacing','Follow and attack spacing'); renderSpacing(spacing,bot);
      if (!bot.rangedSpacing) spacing.appendChild(node('p','Ranged spacing controls apply to ranged companions. This companion uses its native role spacing.','empty-state'));

    } else if (botTab === 'equipment') {

      var build = subPanel(body,'build','Equipment build'); renderGearPolicy(build, bot);
      var equipped = [], carried = [];
      for (var i = 0; i < bot.inventory.length; i++) (bot.inventory[i].equipped ? equipped : carried).push(bot.inventory[i]);
      var profile = subPanel(body,'profile','Character equipment'); renderProfile(profile,bot,equipped);
      var inventory = subPanel(body,'inventory','Inventory');
      inventory.appendChild(node('p','Items belong to this companion. Hover for native item details; select a slot for available actions.','hint'));
      itemGrid(inventory,bot,carried,'inventory','Inventory (' + carried.length + (bot.inventoryLimit != null ? ' / ' + bot.inventoryLimit : '') + ')',renderItem);

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

      button(journal, 'Nearby quest combat: ' + (bot.questCombat ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'questCombat', enabled: !bot.questCombat }, bot.closing, bot.questCombat ? 'on' : '');

      journal.appendChild(node('p', 'When On, the party tackles isolated quest targets within 25 m of you. Tanks lead; healers and support keep their roles. Untouched nearby packs are left for you to pull.', 'hint'));

      journal.appendChild(node('h3', 'Quest journal (' + bot.quests.length + ')'));

      if (bot.mission) { var missionRow = node('div', null, 'mission'); missionRow.appendChild(node('p', 'Quest ' + bot.mission + ' \u00b7 ' + label(bot.missionStatus))); button(missionRow, 'Cancel quest mission', { action: 'mission', name: bot.name, quest: 0 }, bot.closing); journal.appendChild(missionRow); }

      if (!bot.quests.length) journal.appendChild(node('p', 'Share an eligible quest or enable nearby quests.', 'empty'));

      for (var q = 0; q < bot.quests.length; q++) { var row = node('div', null, 'quest'); journal.appendChild(row); renderQuest(row, bot.quests[q]); if (bot.quests[q].missionEligible) button(row, 'Assign quest mission', { action: 'mission', name: bot.name, quest: bot.quests[q].id }, bot.closing || bot.dead); }

    } else if (botTab === 'care') renderCare(body, bot);

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

  function coordinates(destination) { return destination.name + ' \u00b7 ' + (destination.mapName || 'Map ' + destination.map) + ' \u00b7 X ' + Math.round(destination.x) + ', Y ' + Math.round(destination.y) + ', Z ' + Math.round(destination.z) + ' \u00b7 ' + destination.distance + ' m from you'; }

  function renderQuest(parent, quest) {

    parent.appendChild(node('strong', (quest.name || 'Quest ' + quest.id) + ' \u00b7 ' + (quest.ready ? 'Ready to turn in' : label(quest.status))));

    parent.appendChild(node('p', quest.guidance || 'Lead this companion to its quest objective.', 'hint'));

    var objectives = quest.objectives || [];

    for (var o = 0; o < objectives.length; o++) parent.appendChild(node('p', objectives[o].name + ': ' + objectives[o].current + ' / ' + objectives[o].required, 'route'));

    var destinations = quest.destinations || [];

    for (var d = 0; d < destinations.length; d++) parent.appendChild(node('p', coordinates(destinations[d]), 'route'));

    if (!destinations.length) parent.appendChild(node('p', 'No verified NPC location in this map/instance. Follow the native quest journal for the next step.', 'hint'));

  }

  function renderOverview() {

    var parent = byId('quest-overview'), groups = {}, order = []; clear(parent);

    if (!state.active.length) { parent.appendChild(node('div', 'Recruit companions to track their quests together.', 'empty')); return; }

    var body = node('div'); parent.appendChild(body); body.appendChild(node('h3', 'Party quest tracker'));

    for (var a = 0; a < state.active.length; a++) if ((state.active[a].questions || []).length) { var requestRow = node('div', null, 'catch-up'), member = state.active[a]; requestRow.appendChild(node('strong', member.name + ' \u00b7 ' + member.questions.length + ' catch-up request(s)')); (function (id) { localButton(requestRow, 'Review requests', function () { openBot(id, 'quests'); }); }(member.id)); body.appendChild(requestRow); }

    for (var b = 0; b < state.active.length; b++) {

      var bot = state.active[b];

      for (var q = 0; q < bot.quests.length; q++) {

        var quest = bot.quests[q], key = String(quest.id);

        if (!groups[key]) { groups[key] = { quest: quest, members: [], locations: [] }; order.push(key); }

        groups[key].members.push(bot.name + ': ' + (quest.ready ? 'Ready to turn in' : label(quest.status)));

        if (!groups[key].bots) groups[key].bots = []; groups[key].bots.push({ id: bot.id, name: bot.name });

        var destinations = quest.destinations || []; if (quest.ready && destinations.length) groups[key].locations.push(bot.name + ' \u2192 ' + coordinates(destinations[0]));

      }

    }

    if (!order.length) body.appendChild(node('p', 'Companion quests will appear here as they are accepted.', 'hint'));

    for (var i = 0; i < order.length; i++) {

      var group = groups[order[i]], row = node('div', null, 'quest');

      row.appendChild(node('strong', group.quest.name || 'Quest ' + group.quest.id));

      row.appendChild(node('p', group.members.join(' \u00b7 '), 'hint'));

      for (var loc = 0; loc < group.locations.length; loc++) row.appendChild(node('p', group.locations[loc], 'route'));

      for (var member = 0; member < group.bots.length; member++) (function (bot) { localButton(row, bot.name + ' \u00b7 Open journal', function () { openBot(bot.id, 'quests'); }, 'quest-member'); }(group.bots[member]));

      body.appendChild(row);

    }

  }

  function renderGearPolicy(parent, bot) {

    var body = node('div', null, 'gear-policy'); parent.appendChild(body); body.appendChild(node('h3', 'Equipment acquisition & choices'));

    if (!bot.temporary) { body.appendChild(node('p', bot.world ? 'Independent bot: equipment comes from its own earned loot, quests and native shops. Private ownership and progression stay with this bot.' : 'Player-owned alt: automatic equipment, build and Stigma management are disabled. Your existing setup is preserved; you can equip items manually below.', 'hint')); return; }

    body.appendChild(node('p', (bot.build || 'Temporary Bot') + '. Automatic native skills and Stigmas follow your role and level. Gear tiers advance every 10 levels from 20. Only replaced system-generated gear is retired; earned equipment stays in your inventory.', 'hint'));

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

    var levelOne = byId('model').value === 'create';

    var classes = levelOne ? state.startingClasses : state.classes, el = byId('new-class'), previous = el.value; clear(el);

    for (var i = 0; i < classes.length; i++) { var opt = node('option', label(classes[i])); opt.value = classes[i]; el.appendChild(opt); }

    if (classes.indexOf(previous) !== -1) el.value = previous;

    byId('starting-level').style.display = byId('model').value === 'create' ? 'none' : 'block';

    byId('model-hint').textContent = byId('model').value === 'create' ? 'Creates a player-owned alt in a normal character slot. Automatic bot builds will not overwrite it.' : 'Temporary Bot: matches your level, receives class and role gear, native skills and Stigmas. Automatically follows map and dungeon transitions. Dismissal removes it from the world; its private roster entry remains available.';

  }

  function updateCreation() {
    if (!state) return;
    var temporary = byId('model').value === 'generate';
    byId('create').querySelector('button').disabled = !state.enabled || (temporary ?
      !state.generatedEnabled || state.generatedCount >= state.generatedLimit : !(state.recruitRemaining > 0));
    byId('creation-capacity').textContent = temporary ? 'Temporary Bots: ' + state.generatedCount + ' / ' + state.generatedLimit +
      ' created. Full teams keep new bots in your roster for later recruitment.' : '';
  }

  var mainTabs = document.querySelectorAll('#main-tabs button');

  for (var nav = 0; nav < mainTabs.length; nav++) mainTabs[nav].onclick = function () { showView(this.getAttribute('data-view')); };

  byId('browse-roster').onclick = byId('create-roster').onclick = function () { showView('roster'); };

  byId('roster-create').onclick = function () { showView('create'); };

  byId('roster-search').oninput = byId('roster-filter').onchange = byId('roster-sort').onchange = function () { rosterPage = 0; if (state) renderRoster(); };

  byId('roster-prev').onclick = function () { if (rosterPage > 0) rosterPage--; if (state) renderRoster(); };

  byId('roster-next').onclick = function () { rosterPage++; if (state) renderRoster(); };

  byId('refresh').onclick = function () { request(null); };

  byId('model').onchange = function () { updateClasses(); updateCreation(); };

  byId('start-level').onchange = updateClasses;

  byId('create').onsubmit = function (event) { event.preventDefault(); act({ action: byId('model').value, name: byId('new-name').value, playerClass: byId('new-class').value, start: byId('model').value === 'create' ? 'level1' : byId('start-level').value }); };

  byId('share').onclick = function () { act({action:'share',name:'all',quest:byId('owner-quest').value}); };
  function updateShare() { if (!state) return; byId('share').disabled = busy || !state.quests.length || !state.active.length; byId('share').textContent = 'Share with party'; }

  function rosterSection(view) {
    var names = ['characters','presets'];
    for (var i = 0; i < names.length; i++) {
      var panel = byId('roster-' + names[i]), tab = byId('roster-tab-' + names[i]), active = view === names[i];
      if (panel) panel.style.display = active ? 'block' : 'none';
      if (tab) { tab.className = active ? 'selected' : ''; tab.setAttribute('aria-selected',active ? 'true' : 'false'); tab.tabIndex = active ? 0 : -1; }
    }
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

  if (session) request(null); else notice('Open Companions from Additional Functions while logged in to your character.', true);

  window.setInterval(function () { var focused = document.activeElement, tag = focused && focused.tagName; if (session && !busy && !document.hidden && tag !== 'INPUT' && tag !== 'SELECT') request(null); }, 10000);

}());
