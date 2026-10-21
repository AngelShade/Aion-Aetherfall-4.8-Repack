/* Aetherfall Account window. ES5 for Aion's embedded browser. Read-only view of /market/account/state. */
(function () {
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

  function id(x) { return document.getElementById(x); }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function num(n) { return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ','); }
  function pct(a, b) { return b > 0 ? Math.max(0, Math.min(100, a * 100 / b)) : 0; }
  function date(ms) { var d = new Date(ms), m = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']; return d.getDate() + ' ' + m[d.getMonth()] + ' ' + d.getFullYear(); }
  function left(until, now) {
    if (!until) return 'No time limit';
    var s = Math.max(0, Math.floor((until - now) / 1000)), d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600);
    return d > 0 ? d + 'd ' + h + 'h left' : h + 'h ' + Math.floor(s % 3600 / 60) + 'm left';
  }
  function status(text, cls) { id('status').innerHTML = esc(text); id('dot').className = 'dot ' + (cls || ''); }

  function renderBanner(s) {
    var next = s.renown < 1000 ? 1000 : 2500;
    id('character').innerHTML = esc(s.character); id('account-name').innerHTML = esc(String(s.account).toUpperCase());
    id('renown').innerHTML = num(s.renown);
    id('renown-fill').style.width = pct(Math.min(s.renown, next), next) + '%';
    id('renown-label').innerHTML = s.renown >= 2500 ? 'Every fame tier reached' : esc(num(s.renown) + ' / ' + num(next) + ' fame to the next tier');
    id('stipend').innerHTML = s.stipend > 0 ? esc(num(s.stipend)) + ' <small style="display:inline;font-size:11px;color:#93abbc">Kinah</small>' : '&mdash;';
    id('chapters').innerHTML = s.chapters + ' / ' + s.chapterGoal;
    id('market').innerHTML = s.marketReturn + '%';
    id('season').innerHTML = s.seasonEnds ? esc(date(s.seasonEnds)) : 'No season';
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
        '<h3>' + esc(info.name) + '</h3><span class="badge">' + (b.active ? 'ACTIVE' : 'LOCKED') + '</span></div><ul class="effects">';
      for (j = 0; j < info.effects.length; j++) html += '<li>' + esc(info.effects[j]) + '</li>';
      html += '</ul><div class="earn"><small>EARNED BY &middot; ' + info.earn + '</small><div class="meter"><i style="width:' + pct(b.progress, b.goal) + '%"></i></div>' +
        '<span>' + esc(b.progressLabel) + '</span></div></div></div>';
    }
    if (chapters) {
      html += '<div class="chapters"><h4>Chronicle chapters</h4>';
      for (i = 0; i < chapters.length; i++)
        html += '<div class="chapter' + (chapters[i].done ? ' done' : '') + '"><b>' + (chapters[i].done ? '&#10003;' : '&#9671;') + '</b>' + esc(chapters[i].name) + '</div>';
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
      num(f.quests) + ' quests (one fame per ten) and ' + f.titles + ' titles (five fame each).'];
    for (i = 0; i < f.parts.length; i++) {
      p = f.parts[i];
      html += '<div class="fame-part"><div class="fame-card"><small>' + esc(p.name.toUpperCase()) + '</small><strong>' + num(p.points) + '</strong>' +
        '<p class="how">' + esc(how[i]) + '</p><div class="fame-now"><span>Step ' + p.step + ' / ' + p.steps.length + '</span>' + esc(p.bonus) + '</div>' +
        '<div class="meter"><i style="width:' + pct(p.next ? p.points : 1, p.next || 1) + '%"></i></div>' +
        '<p class="next">' + (p.next ? esc(num(p.points) + ' / ' + num(p.next) + ' to the next step') : 'Every step reached') + '</p><ol class="steps">';
      for (j = 0; j < p.steps.length; j++)
        html += '<li' + (p.steps[j].reached ? ' class="reached"' : '') + '><b>' + num(p.steps[j].at) + '</b>' + esc(p.steps[j].bonus) + '</li>';
      html += '</ol><div class="kinah"><small>DAILY KINAH</small>' + num(p.stipend) + ' <em>/ ' + num(p.stipendCap) + '</em></div></div></div>';
    }
    html += '<div class="clear"></div></div><div class="tiers"><h4>Account-wide fame &middot; ' + num(f.total) + ' fame</h4>';
    for (i = 0; i < f.tiers.length; i++) {
      t = f.tiers[i];
      html += '<div class="tier' + (f.total >= t.at ? ' reached' : '') + '"><b>' + (f.total >= t.at ? '&#10003;' : (i + 1)) + '</b><em>' + (t.at > 1 ? num(t.at) + ' fame' : 'Any fame') +
        '</em><strong>' + esc(t.name) + '</strong><span>' + esc(t.benefit) + '</span></div>';
    }
    id('fame').innerHTML = html + '</div>';
    id('fame-count').innerHTML = num(f.total);
  }

  function renderCharacters(s) {
    var html = '', i, c;
    for (i = 0; i < s.characters.length; i++) {
      c = s.characters[i];
      html += '<tr' + (c.online ? ' class="online"' : '') + '><td class="name">' + esc(c.name) + (c.online ? ' &#10022;' : '') + '</td><td>' +
        esc(CLASSES[c.playerClass] || c.playerClass) + '</td><td>' + (c.race === 'ELYOS' ? 'Elyos' : 'Asmodian') + '</td><td class="num">' + c.level +
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
      if (x.status !== 200 || !data) { status(data && data.error ? data.error : 'Account is unavailable. Refresh to try again.', 'bad'); return; }
      state = data; renderBanner(data); renderBoons(data); renderFame(data); renderCharacters(data);
      status('Up to date · boons apply to every character on this account', 'ok');
    };
    x.send(null);
  }

  var tabs = document.querySelectorAll('nav.sections button'), t;
  for (t = 0; t < tabs.length; t++) tabs[t].onclick = function () {
    for (var k = 0; k < tabs.length; k++) { tabs[k].className = tabs[k] === this ? 'active' : ''; id(tabs[k].getAttribute('data-view') + '-view').className = 'view' + (tabs[k] === this ? '' : ' hidden'); }
    id('main').scrollTop = 0;
  };
  id('refresh').onclick = load;
  load();
}());
