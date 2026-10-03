(function () {
  'use strict';
  var state = null, busy = false, selectedDetails = {}, session = '', requestSerial = 0;
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
  function button(parent, text, values, disabled, className) {
    var el = node('button', text, className); el.type = 'button'; el.disabled = disabled || busy;
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
      if (xhr.status !== 200 || result.error) { if (state) state.request = ''; notice(result.error || 'Refresh Companions to reconnect.', true); return; }
      state = result; render(); notice(result.notice || 'Connected as ' + result.owner + '. Select a companion or issue a party order.', false);
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
      if (busy) { controls[i].setAttribute('data-disabled', controls[i].disabled ? 'true' : 'false'); controls[i].disabled = true; }
      else if (controls[i].hasAttribute('data-disabled')) { controls[i].disabled = controls[i].getAttribute('data-disabled') === 'true'; controls[i].removeAttribute('data-disabled'); }
    }
    byId('active').className = busy ? 'busy' : '';
  }
  function act(values) { if (!state || !state.request) { notice('Refresh Companions before submitting another action.', true); return; } request(values); }
  function vitals(parent, text, value, cls) {
    var el = node('div', null, 'vital ' + cls), bar = node('div', null, 'bar'), fill = node('div', null, 'fill');
    el.appendChild(node('span', text + ' ' + value + '%')); fill.style.width = Math.max(0, Math.min(100, Number(value) || 0)) + '%'; bar.appendChild(fill); el.appendChild(bar); parent.appendChild(el);
  }
  function orders(parent, name, disabled) {
    var names = ['FOLLOW', 'STAY', 'GUARD', 'PASSIVE'];
    for (var i = 0; i < names.length; i++) button(parent, label(names[i]), { action: 'order', name: name, order: names[i] }, disabled);
    button(parent, 'Attack selected target', { action: 'attack', name: name }, disabled);
    button(parent, 'Dismiss', { action: 'dismiss', name: name }, disabled, 'danger');
  }
  function detail(parent, title, key) {
    var section = node('section', null, 'disclosure'), toggle = node('button', title, 'disclosure-toggle'), body = node('div');
    toggle.type = 'button'; body.style.display = selectedDetails[key] ? 'block' : 'none';
    toggle.setAttribute('aria-expanded', selectedDetails[key] ? 'true' : 'false');
    toggle.onclick = function () { selectedDetails[key] = !selectedDetails[key]; body.style.display = selectedDetails[key] ? 'block' : 'none'; toggle.setAttribute('aria-expanded', selectedDetails[key] ? 'true' : 'false'); };
    section.appendChild(toggle); section.appendChild(body); parent.appendChild(section); return body;
  }
  function render() {
    var active = byId('active'), roster = byId('roster'), partyOrders = byId('party-orders'); clear(active); clear(roster); clear(partyOrders);
    byId('count').textContent = state.active.length + ' / ' + state.limit;
    orders(partyOrders, 'all', !state.active.length);
    if (!state.active.length) active.appendChild(node('div', 'Your party has no companions. Recruit an eligible offline character from your roster or create a new companion.', 'empty'));
    for (var a = 0; a < state.active.length; a++) renderBot(active, state.active[a]);
    if (!state.roster.length) roster.appendChild(node('p', 'No other characters yet.', 'hint'));
    for (var r = 0; r < state.roster.length; r++) {
      var character = state.roster[r], row = node('div', null, 'roster-row'); row.appendChild(node('strong', character.name));
      row.appendChild(node('p', (character.generated ? 'Dedicated companion' : 'Level ' + character.level + ' ' + label(character.playerClass)) + (!character.ready ? ' · Creation pending' : character.reserved ? ' · Active or saving' : ' · Roster character'), 'hint'));
      button(row, 'Recruit', { action: 'recruit', name: character.name }, !state.enabled || character.reserved || !character.ready || state.active.length >= state.limit); roster.appendChild(row);
    }
    updateClasses();
    var quest = byId('owner-quest'), previous = quest.value; clear(quest);
    for (var q = 0; q < state.quests.length; q++) { var opt = node('option', 'Quest ' + state.quests[q].id + ' · ' + label(state.quests[q].status)); opt.value = state.quests[q].id; quest.appendChild(opt); }
    if (previous) quest.value = previous;
    byId('share').disabled = !state.quests.length || !state.active.length;
    byId('create').querySelector('button').disabled = !state.enabled || !state.generatedEnabled || state.active.length >= state.limit;
  }
  function renderBot(parent, bot) {
    var card = node('article', null, 'bot'), top = node('div', null, 'bot-top'); top.appendChild(node('h3', bot.name));
    top.appendChild(node('span', 'Level ' + bot.level + ' ' + label(bot.playerClass) + ' · ' + label(bot.order), 'badge')); card.appendChild(top);
    var health = node('div', null, 'vitals'); vitals(health, 'Health', bot.health, 'health'); vitals(health, 'Mana', bot.mana, 'mana'); card.appendChild(health);
    card.appendChild(node('p', bot.closing ? 'Saving and dismissing…' : bot.dead ? 'Waiting for resurrection' : label(bot.status) + ' · ' + bot.action, 'detail'));
    if (bot.mission) {
      var mission = node('div', null, 'mission'); mission.appendChild(node('p', 'Quest ' + bot.mission + ' · ' + label(bot.missionStatus), 'route'));
      button(mission, 'Cancel quest mission', { action: 'mission', name: bot.name, quest: 0 }, bot.closing); card.appendChild(mission);
    }
    var controls = node('div', null, 'actions'); orders(controls, bot.name, bot.closing); card.appendChild(controls);
    var roleRow = node('div', null, 'role-row'); roleRow.appendChild(node('label', 'Combat role')); var role = select(roleRow, state.roles, bot.role); role.disabled = bot.closing;
    role.onchange = function () { act({ action: 'role', name: bot.name, role: role.value }); }; card.appendChild(roleRow);
    var settings = node('div', null, 'settings'), flags = [['area', 'Area attacks'], ['supplies', 'Recovery supplies'], ['gear', 'Auto equipment'], ['loot', 'Auto loot'], ['questing', 'Nearby quests']];
    for (var f = 0; f < flags.length; f++) button(settings, flags[f][1] + ': ' + (bot[flags[f][0]] ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: flags[f][0], enabled: !bot[flags[f][0]] }, bot.closing, bot[flags[f][0]] ? 'on' : '');
    card.appendChild(settings);
    var inventory = detail(card, 'Equipment & inventory (' + bot.inventory.length + ')', bot.id + ':inventory');
    if (!bot.inventory.length) inventory.appendChild(node('p', 'The companion’s inventory is empty.', 'hint'));
    for (var i = 0; i < bot.inventory.length; i++) renderItem(inventory, bot, bot.inventory[i]);
    var quests = detail(card, 'Quests (' + bot.quests.length + ')', bot.id + ':quests');
    if (!bot.quests.length) quests.appendChild(node('p', 'Share an eligible quest or enable nearby quests.', 'hint'));
    for (var q = 0; q < bot.quests.length; q++) {
      var row = node('div', 'Quest ' + bot.quests[q].id + ' · ' + label(bot.quests[q].status), 'quest'); quests.appendChild(row);
      if (bot.quests[q].missionEligible) button(row, 'Assign quest mission', { action: 'mission', name: bot.name, quest: bot.quests[q].id }, bot.closing || bot.dead);
      else row.appendChild(node('p', 'This quest requires your guidance.', 'hint'));
    }
    parent.appendChild(card);
  }
  function renderItem(parent, bot, item) {
    var row = node('div', null, 'item'); row.appendChild(node('div', item.name + ' ×' + item.count + (item.equipped ? ' · Equipped' : '')));
    if (!item.equipped && item.slots.length) {
      var slot = select(row, item.slots), equip = button(row, 'Equip', { action: 'equip', name: bot.name, item: item.id, slot: slot.value }, bot.closing);
      equip.onclick = function () { act({ action: 'equip', name: bot.name, item: item.id, slot: slot.value }); };
    }
    parent.appendChild(row);
  }
  function updateClasses() {
    if (!state) return;
    var classes = byId('model').value === 'create' ? state.startingClasses : state.classes, el = byId('new-class'), previous = el.value; clear(el);
    for (var i = 0; i < classes.length; i++) { var opt = node('option', label(classes[i])); opt.value = classes[i]; el.appendChild(opt); }
    if (classes.indexOf(previous) !== -1) el.value = previous;
    byId('model-hint').textContent = byId('model').value === 'create' ? 'Uses a normal character slot and starts at level 1. Recruitment requires being within the allowed level difference.' : 'Uses a dedicated companion slot and starts at your level. Progress is saved as a real character.';
  }
  byId('refresh').onclick = function () { request(null); };
  byId('model').onchange = updateClasses;
  byId('create').onsubmit = function (event) { event.preventDefault(); act({ action: byId('model').value, name: byId('new-name').value, playerClass: byId('new-class').value }); };
  byId('share').onclick = function () { act({ action: 'share', name: 'all', quest: byId('owner-quest').value }); };
  if (session) request(null); else notice('Open Companions from Additional Functions while logged in to your character.', true);
}());
