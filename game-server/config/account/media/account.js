/* Aetherfall Account window. ES5 for Aion's embedded browser. Read-only view of /market/account/state. */
(function () {
  var tr = window.AetherfallWeb.text, fmt = window.AetherfallWeb.format;
  var BOONS = {
    shugo_trade_charter: { name: 'Shugo Trade Charter', icon: 100000679, earn: 'SEASON PASS PREMIUM',
      effects: ['+30% XP from hunting, groups, quests, gathering and crafting', '+30% gathering and crafting skill XP',
        'Central Market sales return 84.5% instead of 65%', 'Wardrobe appearance unlocks need no ticket'] },
    grace_of_the_tower: { name: 'Grace of the Tower', icon: 182206059, earn: 'SEASON PASS ADVANCED PREMIUM',
      effects: ['+20% item drops', 'Energy of Repose refills in 24 hours offline (normally 48)', '+2,000 VT Central Market warehouse capacity'] },
    aetherfall_renown: { name: 'Aetherfall Renown', icon: 186000242, earn: 'ACCOUNT FAME',
      effects: ['Daily Kinah stipend by mail on your first login', '1,000 fame: Central Market sales return 5% more', '2,500 fame: +10% item drops', 'Each fame climbs its own five bonus steps'] },
    chronicle_of_atreia: { name: 'Chronicle of Atreia', icon: 182208043, earn: 'STORY AND QUESTS',
      effects: ['Each chapter: +3% max HP and MP', 'Each chapter: +2% physical attack and +20 magic boost', 'Each chapter: +3% physical defence and +10 magic resist'] },
    daeva_veterans_coffer: { name: "Daeva Veteran's Coffer", icon: 182215746, earn: 'LEVEL 65 DAEVA',
      effects: ['A coffer by mail for every month of account age', 'Expansion tickets, enchantment stones, crafting charms', 'Mounts, pets and more as the months go on'] }
  };
  var CLASSES = { WARRIOR: 'Warrior', GLADIATOR: 'Gladiator', TEMPLAR: 'Templar', SCOUT: 'Scout', ASSASSIN: 'Assassin', RANGER: 'Ranger', MAGE: 'Mage',
    SORCERER: 'Sorcerer', SPIRIT_MASTER: 'Spiritmaster', PRIEST: 'Priest', CLERIC: 'Cleric', CHANTER: 'Chanter', ENGINEER: 'Engineer', GUNNER: 'Gunner',
    ARTIST: 'Artist', BARD: 'Bard', RIDER: 'Aethertech' };
  var session = (/[?&]session_id=([^&]+)/.exec(location.search) || [])[1] || '';
  var state = null, busy = false;
  var lastPrint = null;

  function id(x) { return document.getElementById(x); }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function num(n) { return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ','); }
  function pct(a, b) { return b > 0 ? Math.max(0, Math.min(100, a * 100 / b)) : 0; }
  function date(ms) { var d = new Date(ms), m = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']; return fmt('{day} {month} {year}', {day:d.getDate(), month:tr(m[d.getMonth()]), year:d.getFullYear()}); }
  function left(until, now) {
    if (!until) return tr('No time limit');
    var s = Math.max(0, Math.floor((until - now) / 1000)), d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600);
    return d > 0 ? fmt('{days}d {hours}h left', {days:d, hours:h}) : fmt('{hours}h {minutes}m left', {hours:h, minutes:Math.floor(s % 3600 / 60)});
  }
  function status(text, cls) { id('status').textContent = tr(text); id('dot').className = 'dot ' + (cls || ''); }

  function fameBenefit(text) {
    var m = /^Abyss Points \+(\d+)%$/.exec(text);
    if (m) return fmt('Abyss Points +{rate}%', {rate:m[1]});
    m = /^Crafting XP \+(\d+)%(?:, gathering XP \+(\d+)%)?$/.exec(text);
    if (m) return fmt(m[2] ? 'Crafting XP +{craft}%, gathering XP +{gather}%' : 'Crafting XP +{craft}%', {craft:m[1], gather:m[2]});
    m = /^Repose XP bonus (\d+)%$/.exec(text);
    if (m) return fmt('Repose XP bonus {rate}%', {rate:m[1]});
    m = /^Daily Kinah from each fame: ([\d,]+) per point, up to ([\d,]+) a day in total$/.exec(text);
    if (m) return fmt('Daily Kinah from each fame: {rate} per point, up to {cap} a day in total', {rate:m[1], cap:m[2]});
    return tr(text);
  }

  function renderBanner(s) {
    var next = s.renown < 1000 ? 1000 : 2500;
    id('character').innerHTML = esc(s.character); id('account-name').innerHTML = esc(String(s.account).toUpperCase());
    id('renown').innerHTML = num(s.renown);
    id('renown-fill').style.width = pct(Math.min(s.renown, next), next) + '%';
    id('renown-label').textContent = s.renown >= 2500 ? tr('Every fame tier reached') : fmt('{current} / {next} fame to the next tier', {current:num(s.renown), next:num(next)});
    id('stipend').innerHTML = s.stipend > 0 ? esc(num(s.stipend)) + ' <small style="display:inline;font-size:11px;color:#93abbc">' + esc(tr('Kinah')) + '</small>' : '&mdash;';
    id('chapters').innerHTML = s.chapters + ' / ' + s.chapterGoal;
    id('market').innerHTML = s.marketReturn + '%';
    id('season').textContent = s.seasonEnds ? date(s.seasonEnds) : tr('No season');
  }

  function progressLabel(b, s) {
    if (b.id === 'aetherfall_renown') {
      return s.renown >= 2500
        ? fmt('{current} Account Fame · every tier reached', {current:num(s.renown)})
        : fmt('{current} / {next} Account Fame to the next tier', {current:num(s.renown), next:num(b.goal)});
    }
    if (b.id === 'chronicle_of_atreia') return fmt('{current} of {goal} chapters written · {quests} quests completed', {current:b.progress, goal:b.goal, quests:num(s.fame.quests)});
    if (b.id === 'daeva_veterans_coffer' && b.progress < b.goal) return fmt('Highest level {current} of {goal}', {current:b.progress, goal:b.goal});
    if ((b.id === 'shugo_trade_charter' || b.id === 'grace_of_the_tower') && s.season && b.progress === b.goal) {
      return fmt('Unlocked for {season}', {season:tr(s.season)});
    }
    return tr(b.progressLabel);
  }

  function renderBoons(s) {
    var html = '', active = 0, chapters = null, i, b, info, j;
    for (i = 0; i < s.boons.length; i++) {
      b = s.boons[i]; info = BOONS[b.id]; if (!info) continue;
      if (b.active) active++;
      if (b.chapters) chapters = b.chapters;
      html += '<div class="boon ' + (b.active ? 'active' : 'locked') + '"><div class="boon-frame"><div class="boon-head">' +
        '<span class="boon-icon"><a href="nc://aion.ItemInfo/ItemTooltip?item=' + info.icon + '&count=1&enchant_count=0&authorize_count=0" onclick="return false" tabindex="-1">' +
        '<img src="/market/media/icons/' + info.icon + '.png?v=native-3" alt=""></a></span>' +
        '<span class="boon-time">' + (b.active ? esc(left(b.until, s.now)) : '') + '</span>' +
        '<h3>' + esc(tr(info.name)) + '</h3><span class="badge">' + esc(tr(b.active ? 'ACTIVE' : 'LOCKED')) + '</span></div><ul class="effects">';
      for (j = 0; j < info.effects.length; j++) html += '<li>' + esc(tr(info.effects[j])) + '</li>';
      html += '</ul><div class="earn"><small>' + esc(fmt('EARNED BY · {source}', {source:tr(info.earn)})) + '</small><div class="meter"><i style="width:' + pct(b.progress, b.goal) + '%"></i></div>' +
        '<span>' + esc(progressLabel(b, s)) + '</span></div></div></div>';
    }
    if (chapters) {
      html += '<div class="chapters"><h4>' + esc(tr('Chronicle chapters')) + '</h4>';
      for (i = 0; i < chapters.length; i++)
        html += '<div class="chapter' + (chapters[i].done ? ' done' : '') + '"><b>' + (chapters[i].done ? '&#10003;' : '&#9671;') + '</b>' + esc(tr(chapters[i].name)) + '</div>';
      html += '<div class="clear"></div></div>';
    }
    id('boons').innerHTML = html + '<div class="clear"></div>';
    id('boon-count').innerHTML = active + ' / ' + s.boons.length;
  }

  function renderFame(s) {
    var f = s.fame, html = '<div class="fame-parts">', i, j, p, t, how;
    if (!f) return;
    how = ['Character levels from 10. Level 55+ counts double, level 65 five times.',
      'Gathering and crafting skills from skill 100: one fame per five skill points.',
      fmt('{quests} quests (one fame per ten) and {titles} titles (five fame each).', {quests:num(f.quests), titles:f.titles})];
    for (i = 0; i < f.parts.length; i++) {
      p = f.parts[i];
      html += '<div class="fame-part"><div class="fame-card"><small>' + esc(tr(p.name).toUpperCase()) + '</small><strong>' + num(p.points) + '</strong>' +
        '<p class="how">' + esc(tr(how[i])) + '</p><div class="fame-now"><span>' + esc(fmt('Step {current} / {total}', {current:p.step, total:p.steps.length})) + '</span>' + esc(fameBenefit(p.bonus)) + '</div>' +
        '<div class="meter"><i style="width:' + pct(p.next ? p.points : 1, p.next || 1) + '%"></i></div>' +
        '<p class="next">' + esc(p.next ? fmt('{current} / {next} to the next step', {current:num(p.points), next:num(p.next)}) : tr('Every step reached')) + '</p><ol class="steps">';
      for (j = 0; j < p.steps.length; j++)
        html += '<li' + (p.steps[j].reached ? ' class="reached"' : '') + '><b>' + num(p.steps[j].at) + '</b>' + esc(fameBenefit(p.steps[j].bonus)) + '</li>';
      html += '</ol><div class="kinah"><small>' + esc(tr('DAILY KINAH')) + '</small>' + num(p.stipend) + ' <em>/ ' + num(p.stipendCap) + '</em></div></div></div>';
    }
    html += '<div class="clear"></div></div><div class="tiers"><h4>' + esc(fmt('Account-wide fame · {total} fame', {total:num(f.total)})) + '</h4>';
    for (i = 0; i < f.tiers.length; i++) {
      t = f.tiers[i];
      html += '<div class="tier' + (f.total >= t.at ? ' reached' : '') + '"><b>' + (f.total >= t.at ? '&#10003;' : (i + 1)) + '</b><em>' + esc(t.at > 1 ? fmt('{points} fame', {points:num(t.at)}) : tr('Any fame')) +
        '</em><strong>' + esc(tr(t.name)) + '</strong><span>' + esc(fameBenefit(t.benefit)) + '</span></div>';
    }
    id('fame').innerHTML = html + '</div>';
    id('fame-count').innerHTML = num(f.total);
  }

  function renderCharacters(s) {
    var html = '', i, c;
    for (i = 0; i < s.characters.length; i++) {
      c = s.characters[i];
      html += '<tr' + (c.online ? ' class="online"' : '') + '><td class="name">' + esc(c.name) + (c.online ? ' &#10022;' : '') + '</td><td>' +
        esc(tr(CLASSES[c.playerClass] || c.playerClass)) + '</td><td>' + esc(tr(c.race === 'ELYOS' ? 'Elyos' : 'Asmodian')) + '</td><td class="num">' + c.level +
        '</td><td class="num">' + num(c.renown) + '</td></tr>';
    }
    id('roster').innerHTML = html;
    id('character-count').innerHTML = s.characters.length;
  }

  function load() {
    if (busy) return; busy = true; status('Loading your account...');
    var x = new XMLHttpRequest();
    x.open('GET', '/market/account/state?session_id=' + encodeURIComponent(session) + '&t=' + new Date().getTime(), true);
    x.onreadystatechange = function () {
      if (x.readyState !== 4) return;
      busy = false;
      var data = null;
      try { data = JSON.parse(x.responseText); } catch (e) { }
      if (x.status !== 200 || !data || data.error) { status(data && data.error ? data.error : 'Account is unavailable. Refresh to try again.', 'bad'); return; }
      var print = JSON.stringify(data), unchanged = print === lastPrint;
      state = data; lastPrint = print;
      if (!unchanged) keepScroll(function () { renderBanner(data); renderBoons(data); renderFame(data); renderCharacters(data); });
      status('Up to date · boons apply to every character on this account', 'ok');
    };
    x.send(null);
  }

  // Scrolling panes are emptied while redrawn; put each back where the player left it.
  function keepScroll(redraw) {
    var nodes = document.getElementsByTagName('*'), panes = [], i;
    for (i = 0; i < nodes.length; i++) if (nodes[i].scrollTop > 0) panes.push([nodes[i], nodes[i].scrollTop]);
    redraw();
    for (i = 0; i < panes.length; i++) if (panes[i][0].scrollTop !== panes[i][1]) panes[i][0].scrollTop = panes[i][1];
  }

  var tabs = document.querySelectorAll('nav.sections button'), t;
  for (t = 0; t < tabs.length; t++) tabs[t].onclick = function () {
    for (var k = 0; k < tabs.length; k++) { tabs[k].className = tabs[k] === this ? 'active' : ''; id(tabs[k].getAttribute('data-view') + '-view').className = 'view' + (tabs[k] === this ? '' : ' hidden'); }
    id('main').scrollTop = 0;
  };
  id('refresh').onclick = load;
  load();
}());
