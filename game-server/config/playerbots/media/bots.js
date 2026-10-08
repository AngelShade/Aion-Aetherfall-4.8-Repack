(function () {

  'use strict';

  var state = null, busy = false, selectedDetails = {}, session = '', requestSerial = 0, spacingDrafts = {};

  var currentView = 'party', selectedBot = null, botTab = 'overview', rosterPage = 0, pageSize = 12, inspecting = false, careDrafts = {}, removalConfirmation = null;

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

    el.setAttribute('data-action', values.action || '');

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

      if (mutation && values.action === 'carebudget') { for (var d = 0; d < result.active.length; d++) if (result.active[d].name === values.name) delete careDrafts[result.active[d].id]; }

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

    button(parent, name === 'all' ? 'Revive / summon selected or party' : 'Revive / summon companion', { action: 'summon', name: name }, disabled);

    button(parent, 'Dismiss', { action: 'dismiss', name: name }, disabled, 'danger');

  }

  function detail(parent, title, key) {

    var section = node('section', null, 'disclosure'), toggle = node('button', title, 'disclosure-toggle'), body = node('div');

    toggle.type = 'button'; body.style.display = selectedDetails[key] ? 'block' : 'none';

    toggle.setAttribute('aria-expanded', selectedDetails[key] ? 'true' : 'false');

    toggle.onclick = function () { selectedDetails[key] = !selectedDetails[key]; body.style.display = selectedDetails[key] ? 'block' : 'none'; toggle.setAttribute('aria-expanded', selectedDetails[key] ? 'true' : 'false'); };

    section.appendChild(toggle); section.appendChild(body); parent.appendChild(section); return body;

  }

  function localButton(parent, text, handler, className) {

    var el = node('button', text, className); el.type = 'button'; el.onclick = handler; parent.appendChild(el); return el;

  }

  function rosterIdentity(character) { var parts = []; if (character.level != null) parts.push('Level ' + character.level); if (character.playerClass) parts.push(label(character.playerClass)); return parts.join(' ') || (character.generated ? 'Temporary Bot' : 'Account character'); }

  function activeBot(id) { for (var i = 0; state && i < state.active.length; i++) if (String(state.active[i].id) === String(id)) return state.active[i]; return null; }

  function rosterBot(id) { for (var i = 0; state && i < state.roster.length; i++) if (String(state.roster[i].id) === String(id)) return state.roster[i]; return null; }

  function showView(view) {

    currentView = view;

    var names = ['party', 'roster', 'create', 'quests'];

    for (var i = 0; i < names.length; i++) {

      var selected = view === names[i], tab = byId('tab-' + names[i]);

      byId('view-' + names[i]).style.display = selected ? 'block' : 'none'; tab.className = selected ? 'selected' : ''; tab.setAttribute('aria-selected', selected ? 'true' : 'false');

    }

    if (state) { if (view === 'roster') renderRoster(); if (view === 'party') renderSelection(); if (view === 'quests') renderOverview(); }

  }

  function openBot(id, tab) { selectedBot = id; if (tab) botTab = tab; inspecting = true; showView('party'); }

  function render() {

    byId('count').textContent = state.active.length + ' / ' + state.limit;

    byId('roster-count').textContent = state.roster.length;

    byId('owner-label').textContent = state.owner;

    var pending = 0;

    for (var i = 0; i < state.active.length; i++) pending += (state.active[i].questions || []).length;

    byId('question-count').textContent = pending ? pending + ' requests' : '';

    var partyOrders = byId('party-orders'); clear(partyOrders); orders(partyOrders, 'all', !state.active.length);

    if (!activeBot(selectedBot) && !rosterBot(selectedBot)) selectedBot = state.active.length ? state.active[0].id : null;

    renderSelection(); renderRoster(); renderOverview(); renderPresets(); updateClasses();
    byId('save-party').querySelector('button').disabled = !state.active.length || busy;

    var quest = byId('owner-quest'), previous = quest.value; clear(quest);

    for (var q = 0; q < state.quests.length; q++) { var opt = node('option', (state.quests[q].name || 'Quest ' + state.quests[q].id) + ' \u00b7 ' + label(state.quests[q].status)); opt.value = state.quests[q].id; quest.appendChild(opt); }

    if (previous) quest.value = previous;

    if (quest.selectedIndex < 0 && quest.options.length) quest.selectedIndex = 0;

    byId('share').disabled = !state.quests.length || !state.active.length;

    byId('create').querySelector('button').disabled = !state.enabled || (byId('model').value === 'generate' && !state.generatedEnabled) || state.active.length >= state.limit;

  }

  function renderSelection() {

    var parent = byId('active'), scroll = parent.scrollTop; clear(parent);

    if (!state.active.length) parent.appendChild(node('p', 'No active companions yet. Recruit from your roster to build a party.', 'hint'));

    for (var i = 0; i < state.active.length; i++) (function (bot) {

      var row = localButton(parent, null, function () { openBot(bot.id); }, 'bot-summary' + (String(selectedBot) === String(bot.id) ? ' selected' : ''));

      row.setAttribute('data-bot-id', bot.id); row.setAttribute('aria-pressed', String(selectedBot) === String(bot.id) ? 'true' : 'false');

      row.appendChild(node('strong', bot.name)); row.appendChild(node('span', 'Lv. ' + bot.level + ' ' + label(bot.playerClass) + ' \u00b7 ' + label(bot.role), 'meta'));

      row.appendChild(node('span', bot.closing ? 'Saving and dismissing\u2026' : bot.dead ? 'Waiting for resurrection' : label(bot.order) + ' \u00b7 ' + label(bot.status), 'summary-status'));

      var bars = node('span', null, 'mini-bars');

      for (var v = 0; v < 2; v++) { var track = node('span', null, 'mini-track'), fill = node('span', null, 'mini-fill' + (v ? ' mana' : '')); fill.style.width = Math.max(0, Math.min(100, Number(v ? bot.mana : bot.health) || 0)) + '%'; track.appendChild(fill); bars.appendChild(track); } row.appendChild(bars);

      if ((bot.questions || []).length) row.appendChild(node('span', bot.questions.length + ' catch-up request' + (bot.questions.length === 1 ? '' : 's'), 'needs-attention'));

    }(state.active[i]));

    parent.scrollTop = scroll;

    document.querySelector('.party-layout').className = 'party-layout' + (inspecting ? ' inspecting' : '');

    var detailPanel = byId('bot-detail'); clear(detailPanel);

    var back = localButton(detailPanel, '\u2039 Back to active party', function () { inspecting = false; renderSelection(); }, 'secondary'); back.id = 'bot-back';

    var bot = activeBot(selectedBot), character = rosterBot(selectedBot);

    if (bot) renderBot(detailPanel, bot);

    else if (character) {

      var card = node('article', null, 'bot'), head = node('div', null, 'bot-header'); head.appendChild(node('p', 'ROSTER COMPANION', 'eyebrow')); head.appendChild(node('h3', character.name)); head.appendChild(node('p', rosterIdentity(character), 'badge')); card.appendChild(head);

      var body = node('div', null, 'bot-body'); body.appendChild(node('h3', character.generated ? 'Temporary Bot' : 'Account character')); body.appendChild(node('p', !character.ready ? 'Creation is pending.' : character.reserved ? 'This character is active or saving and cannot currently be recruited.' : 'This character is offline and in your roster.', 'hint'));

      body.appendChild(node('p', 'Recruit this companion to view their live equipment, quests, orders and care settings.', 'hint')); button(body, 'Recruit companion', { action: 'recruit', name: character.name }, !state.enabled || character.reserved || !character.ready || state.active.length >= state.limit, 'primary'); localButton(body, 'Back to roster', function () { showView('roster'); }, 'secondary'); card.appendChild(body); detailPanel.appendChild(card);

    } else { var empty = node('div', null, 'empty'); empty.appendChild(node('h3', 'Your next adventure starts with a party')); empty.appendChild(node('p', 'Recruit an existing character or create a Temporary Bot. Select them here to manage orders, equipment and quests.')); localButton(empty, 'Browse your roster', function () { showView('roster'); }, 'primary'); detailPanel.appendChild(empty); }

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
      if (!live) button(action, character.generated && isSavedBot(character.id) ? 'Summon saved bot' : 'Recruit', { action: 'recruit', name: character.name }, !state.enabled || character.reserved || !character.ready || state.active.length >= state.limit, 'secondary');

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
    save.onclick = function () { var f = Number(follow.value), a = Number(attack.value); if (!isFinite(f) || f < 2 || f > 12 || !isFinite(a) || a < 4 || a > 18) { notice('Choose owner spacing 2–12 m and attack spacing 4–18 m.', true); return; } delete spacingDrafts[bot.id]; act({ action: 'spacing', name: bot.name, ownerSpacing: f, attackSpacing: a }); };
    group.appendChild(node('p', 'Follow spread sets the formation radius; inner Line slots stay closer. Attack distance is capped by usable skill range. A close enemy allows one short retreat, then the companion stands and fights. Combat hazards still take priority.', 'hint')); parent.appendChild(group);
  }

  function renderBot(parent, bot) {

    var card = node('article', null, 'bot'), header = node('div', null, 'bot-header'), top = node('div', null, 'bot-top'), identity = node('div'); card.setAttribute('data-bot-id', bot.id);

    identity.appendChild(node('p', 'COMPANION / ' + label(bot.role).toUpperCase(), 'eyebrow')); identity.appendChild(node('h3', bot.name)); identity.appendChild(node('p', 'Level ' + bot.level + ' ' + label(bot.playerClass), 'badge')); top.appendChild(identity); top.appendChild(node('div', bot.closing ? 'Saving\u2026' : bot.dead ? 'Awaiting resurrection' : label(bot.order), 'bot-state')); header.appendChild(top);

    var health = node('div', null, 'vitals'); vitals(health, 'Health', bot.health, 'health'); vitals(health, 'Mana', bot.mana, 'mana'); header.appendChild(health); card.appendChild(header);

    if (bot.generated || bot.temporary) button(header, 'Save Temporary Bot', { action: 'savebot', name: bot.name }, bot.closing, 'secondary');
    var tabs = node('nav', null, 'tabs bot-tabs'), names = [['overview','Overview'],['equipment','Equipment'],['quests','Quests'],['care','Care'],['activity','Activity']]; tabs.setAttribute('role', 'tablist'); tabs.setAttribute('aria-label', bot.name + ' details');

    for (var t = 0; t < names.length; t++) (function (name, title) { var tab = localButton(tabs, title, function () { botTab = name; renderSelection(); }, botTab === name ? 'selected' : ''); tab.id = 'bot-tab-' + name; tab.setAttribute('role','tab'); tab.setAttribute('aria-controls','bot-content'); tab.setAttribute('aria-selected',botTab === name ? 'true' : 'false'); }(names[t][0], names[t][1])); card.appendChild(tabs);

    var body = node('div', null, 'bot-body'); body.id = 'bot-content'; body.setAttribute('role','tabpanel'); body.setAttribute('aria-labelledby','bot-tab-' + botTab); card.appendChild(body);

    if (botTab === 'overview') {

      body.appendChild(node('h3', 'Orders & behaviour')); body.appendChild(node('p', bot.closing ? 'Saving and dismissing\u2026' : bot.dead ? 'Waiting for resurrection' : label(bot.status) + ' \u00b7 ' + bot.action, 'detail'));

      if (bot.mission) { var mission = node('div', null, 'mission'); mission.appendChild(node('p', 'Quest ' + bot.mission + ' \u00b7 ' + label(bot.missionStatus), 'route')); button(mission, 'Cancel quest mission', { action: 'mission', name: bot.name, quest: 0 }, bot.closing); body.appendChild(mission); }

      var controls = node('div', null, 'actions'); orders(controls, bot.name, bot.closing); body.appendChild(controls);

      var roleRow = node('div', null, 'role-row'), roleLabel = node('label', 'Combat role'); roleLabel.htmlFor = 'bot-role'; roleRow.appendChild(roleLabel); var role = select(roleRow, state.roles, bot.role); role.id = 'bot-role'; role.disabled = bot.closing; role.onchange = function () { act({ action: 'role', name: bot.name, role: role.value }); }; body.appendChild(roleRow);

      var settings = node('div', null, 'settings'), flags = [['area','Area attacks'],['supplies','Recovery and build supplies'],['gear','Auto equipment'],['loot','Auto loot'],['questing','Auto quests'],['questCombat','Nearby quest combat'],['partySync','Party completion sync']];

      for (var f = 0; f < flags.length; f++) button(settings, flags[f][1] + ': ' + (bot[flags[f][0]] ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: flags[f][0], enabled: !bot[flags[f][0]] }, bot.closing || (flags[f][0] === 'gear' && !bot.temporary), bot[flags[f][0]] ? 'on' : ''); body.appendChild(settings); renderSpacing(body, bot); renderQuestions(body, bot);

    } else if (botTab === 'equipment') {

      renderGearPolicy(body, bot);

      body.appendChild(node('h3', 'Equipment & inventory (' + bot.inventory.length + ')')); body.appendChild(node('p', bot.appearanceEnabled ? 'Equip combat armor for stats. Keep outfits in the cube and choose Use as transmog to apply their look. Equip is still available for normal equipment.' : 'Items belong to this companion. Choose an available slot to equip an item.', 'hint'));

      if (!bot.inventory.length) body.appendChild(node('p', 'The companion\u2019s inventory is empty.', 'empty'));

      for (var i = 0; i < bot.inventory.length; i++) renderItem(body, bot, bot.inventory[i]);

    } else if (botTab === 'quests') {

      button(body, 'Nearby quest combat: ' + (bot.questCombat ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'questCombat', enabled: !bot.questCombat }, bot.closing, bot.questCombat ? 'on' : '');

      body.appendChild(node('p', 'When On, the party tackles isolated quest targets within 25 m of you. Tanks lead; healers and support keep their roles. Untouched nearby packs are left for you to pull.', 'hint'));

      body.appendChild(node('h3', 'Quest journal (' + bot.quests.length + ')')); renderQuestions(body, bot);

      if (bot.mission) { var missionRow = node('div', null, 'mission'); missionRow.appendChild(node('p', 'Quest ' + bot.mission + ' \u00b7 ' + label(bot.missionStatus))); button(missionRow, 'Cancel quest mission', { action: 'mission', name: bot.name, quest: 0 }, bot.closing); body.appendChild(missionRow); }

      if (!bot.quests.length) body.appendChild(node('p', 'Share an eligible quest or enable nearby quests.', 'empty'));

      for (var q = 0; q < bot.quests.length; q++) { var row = node('div', null, 'quest'); body.appendChild(row); renderQuest(row, bot.quests[q]); if (bot.quests[q].missionEligible) button(row, 'Assign quest mission', { action: 'mission', name: bot.name, quest: bot.quests[q].id }, bot.closing || bot.dead); }

    } else if (botTab === 'care') renderCare(body, bot);

    else { body.appendChild(node('h3', 'Recent activity')); if (!bot.announcements || !bot.announcements.length) body.appendChild(node('p', 'Companion announcements will appear here.', 'empty')); else for (var msg = bot.announcements.length - 1; msg >= 0; msg--) body.appendChild(node('p', bot.announcements[msg], 'activity-row')); }

    parent.appendChild(card);

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

    if (!bot.temporary) { body.appendChild(node('p', 'Player-owned alt: automatic equipment, build and Stigma management are disabled. Your existing setup is preserved; you can equip items manually below.', 'hint')); return; }

    body.appendChild(node('p', (bot.build || 'Temporary Bot') + '. Automatic native skills and Stigmas follow your role and level. Gear tiers advance every 10 levels from 20. Only replaced system-generated gear is retired; earned equipment stays in your inventory.', 'hint'));

    button(body, 'Auto equipment: ' + (bot.gear ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'gear', enabled: !bot.gear }, bot.closing, bot.gear ? 'on' : '');

    var fields = {};

    function choice(key, title, options, value) { var row = node('label', title); fields[key] = select(row, options, value); fields[key].disabled = bot.closing; body.appendChild(row); }

    choice('mode', 'How equipment is gained', ['EARNED', 'STARTER', 'GENERATED'], bot.gearMode || 'EARNED');

    body.appendChild(node('p', 'Earned: loot, quest rewards and optional real shops. Starter: generates gear for empty slots once, then progresses through the game. Generated: adds useful level-appropriate upgrades automatically. Existing equipment is kept. Auto equipment must be On.', 'hint'));

    choice('profile', 'Equipment priority (independent of combat role)', ['AUTO', 'TANK', 'HEALER', 'DAMAGE', 'SUPPORT'], bot.gearProfile || 'AUTO');

    choice('quality', 'Maximum generated quality', ['COMMON', 'RARE', 'LEGEND', 'UNIQUE', 'EPIC', 'MYTHIC'], bot.gearQuality || 'LEGEND');

    choice('weapon', 'Preferred weapon', ['AUTO', 'SWORD', '2H_SWORD', 'DAGGER', 'MACE', 'ORB', 'SPELLBOOK', 'POLEARM', 'STAFF', 'BOW', 'HARP', 'GUN', 'CANNON', 'KEYBLADE'], bot.gearWeapon || 'AUTO');

    choice('rolls', 'Party dice rolls', ['PASS', 'UPGRADES', 'ALL'], bot.gearRolls || 'UPGRADES');

    choice('vendors', 'Buy equipment upgrades at nearby shops', ['false', 'true'], String(!!bot.gearVendors));

    function input(key, title, value) { var row = node('label', title); fields[key] = node('input'); fields[key].type = 'text'; fields[key].value = value; fields[key].disabled = bot.closing; row.appendChild(fields[key]); body.appendChild(row); }

    input('level', 'Generated item level limit (0 follows your level, up to the bot\u2019s level)', bot.gearLevel || 0);

    input('threshold', 'Upgrade score ratio (1.1 requires a 10% improvement)', bot.gearThreshold == null ? 1.1 : bot.gearThreshold);

    body.appendChild(node('p', 'Current generated level ceiling: ' + (bot.gearTargetLevel || bot.level) + '. Uses normal class, mastery, faction and equipment rules. Shop purchases share the Care reserve and daily budget; Kinah bids are always passed. Generated items are protected from automatic extraction.', 'hint'));

    var save = button(body, 'Save equipment choices', {}, bot.closing); save.onclick = function () { var request = { action: 'gearpolicy', name: bot.name }; for (var key in fields) if (fields.hasOwnProperty(key)) request[key] = fields[key].value; act(request); };

  }

  function renderCare(parent, bot) {

    var body = node('div', null, 'care-fields'); parent.appendChild(body); body.appendChild(node('h3', 'Equipment care'));

    if (!bot.temporary) { body.appendChild(node('p', 'Automatic enchanting and extraction apply only to Temporary Bots. Your alt retains its existing items.', 'hint')); return; }

    var draft = careDrafts[bot.id];

    button(body, 'Auto enchant: ' + (bot.enchant ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'enchant', enabled: !bot.enchant }, bot.closing, bot.enchant ? 'on' : '');

    button(body, 'Extract unused loot: ' + (bot.salvage ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'salvage', enabled: !bot.salvage }, bot.closing, bot.salvage ? 'on' : '');

    body.appendChild(node('p', 'Uses this companion\u2019s own stones, tools and Kinah. Visits nearby real shops when needed. Enchants ordinary equipped armour toward +5 using native success/failure rules. Extracts expendable new loot; protects existing possessions, upgrades, quest items and valuable gear.', 'hint'));

    var reserveLabel = node('label', 'Minimum Kinah reserve (at least 10,000)'), reserve = node('input'); reserve.id = 'care-reserve'; reserve.type = 'text'; reserve.value = draft ? draft.reserve : (bot.reserve == null ? '10000' : bot.reserve); reserve.disabled = bot.closing; reserveLabel.appendChild(reserve); body.appendChild(reserveLabel);

    var budgetLabel = node('label', 'Daily purchase budget (0 disables purchases)'), budget = node('input'); budget.id = 'care-budget'; budget.type = 'text'; budget.value = draft ? draft.dailyBudget : (bot.dailyBudget == null ? '50000' : bot.dailyBudget); budget.disabled = bot.closing; budgetLabel.appendChild(budget); body.appendChild(budgetLabel);

    reserve.oninput = budget.oninput = function () { careDrafts[bot.id] = { reserve: reserve.value, dailyBudget: budget.value }; };

    reserve.onchange = reserve.oninput; budget.onchange = budget.oninput;

    var save = button(body, 'Save spending limits', { action: 'carebudget' }, bot.closing); save.onclick = function () { act({ action: 'carebudget', name: bot.name, reserve: reserve.value, dailyBudget: budget.value }); };

    body.appendChild(node('p', 'Bought today: ' + (bot.spent || 0) + ' Kinah. Also keeps at least 25% of its current balance.', 'hint'));

  }

  function renderItem(parent, bot, item) {

    var row = node('div', null, 'item'); row.appendChild(node('div', item.name + ' \u00d7' + item.count + (item.equipped ? ' \u00b7 Equipped' : '')));

    if (item.equipped && item.appearanceSet) {
      row.appendChild(node('p', 'Appearance: ' + item.skinName + ' (combat stats kept)', 'hint'));
      button(row, 'Restore original look', { action: 'resetappearance', name: bot.name, target: item.id }, bot.closing);
    }
    if (item.appearanceTargets && item.appearanceTargets.length) {
      var looks = node('select'); looks.setAttribute('aria-label', 'Combat equipment to transmog with ' + item.name);
      for (var t = 0; t < item.appearanceTargets.length; t++) { var option = node('option', item.appearanceTargets[t].name); option.value = item.appearanceTargets[t].id; looks.appendChild(option); }
      row.appendChild(looks);
      var transmog = button(row, 'Use as transmog', {}, bot.closing);
      transmog.onclick = function () { act({ action: 'appearance', name: bot.name, item: item.id, target: looks.value }); };
      row.appendChild(node('p', 'Keeps combat stats and this appearance item. The look follows this slot through gear upgrades.', 'hint'));
    }

    if (!item.equipped && item.slots.length) {

      var slot = select(row, item.slots), equip = button(row, 'Equip', { action: 'equip', name: bot.name, item: item.id, slot: slot.value }, bot.closing);

      equip.onclick = function () { act({ action: 'equip', name: bot.name, item: item.id, slot: slot.value }); };

    }

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

  var mainTabs = document.querySelectorAll('#main-tabs button');

  for (var nav = 0; nav < mainTabs.length; nav++) mainTabs[nav].onclick = function () { showView(this.getAttribute('data-view')); };

  byId('browse-roster').onclick = byId('create-roster').onclick = function () { showView('roster'); };

  byId('roster-create').onclick = function () { showView('create'); };

  byId('roster-search').oninput = byId('roster-filter').onchange = byId('roster-sort').onchange = function () { rosterPage = 0; if (state) renderRoster(); };

  byId('roster-prev').onclick = function () { if (rosterPage > 0) rosterPage--; if (state) renderRoster(); };

  byId('roster-next').onclick = function () { rosterPage++; if (state) renderRoster(); };

  byId('refresh').onclick = function () { request(null); };

  byId('model').onchange = function () { updateClasses(); if (state) byId('create').querySelector('button').disabled = !state.enabled || (this.value === 'generate' && !state.generatedEnabled) || state.active.length >= state.limit; };

  byId('start-level').onchange = updateClasses;

  byId('create').onsubmit = function (event) { event.preventDefault(); act({ action: byId('model').value, name: byId('new-name').value, playerClass: byId('new-class').value, start: byId('model').value === 'create' ? 'level1' : byId('start-level').value }); };

  byId('share').onclick = function () { act({ action: 'share', name: 'all', quest: byId('owner-quest').value }); };

  if (session) request(null); else notice('Open Companions from Additional Functions while logged in to your character.', true);

  window.setInterval(function () { var focused = document.activeElement, tag = focused && focused.tagName; if (session && !busy && !document.hidden && tag !== 'INPUT' && tag !== 'SELECT') request(null); }, 10000);

}());
