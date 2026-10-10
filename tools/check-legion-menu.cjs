// Maintained menu in a real browser, intercepted simulated requests only. Never contacts GameServer.
const fs = require('fs'), path = require('path'), assert = require('assert');
const { chromium } = require(process.env.AION_PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '..'), media = path.join(root, 'game-server/config/playerbots/media');
const output = path.resolve(process.argv[2]);
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(process.env.AION_CHECK_BROWSER ? { executablePath: process.env.AION_CHECK_BROWSER } : {}) });
  const page = await browser.newPage({ viewport: { width: 1050, height: 800 } });
  const errors = [], requests = []; let checks = 0, fixture;
  const check = (value, why) => { assert(value, why); checks++; };
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const url = new URL(route.request().url()); assert.equal(url.hostname, 'legion-check.invalid');
    if (url.pathname.endsWith('/state')) return route.fulfill({ contentType: 'application/json', body: JSON.stringify(fixture) });
    if (url.pathname.endsWith('/action')) {
      const data = Object.fromEntries(new URLSearchParams(route.request().postData())); requests.push(data);
      assert.equal(data.session_id, 'offline-fixture'); assert.equal(data.request, 'offline-request');
      assert.equal(data.name, 'LegionBot'); assert.equal(data.action, 'legiondeposit');assert.equal(data.item, '501');
      const bot = fixture.active[0];assert(bot.legionStorageAvailable && !bot.closing && !bot.world);
      const item=bot.inventory.find(i=>i.id===501);bot.inventory=bot.inventory.filter(i=>i.id!==501);bot.legionStorage.push(item);
      fixture.notice = 'Whole stack moved.';
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(fixture) });
    }
    const file = url.pathname.split('/').pop(); assert(['bots.html', 'bots.js', 'bots.css'].includes(file));
    return route.fulfill({ contentType: file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html', body: fs.readFileSync(path.join(media, file)) });
  });
  const cases = [
    { available: true, world: false, closing: false },
    { available: false, world: false, closing: false },
    { available: true, world: false, closing: true },
    { available: true, world: true, closing: false },
    { available: false, world: false, closing: false, empty: true }
  ];
  for (const sample of cases) {
    fixture = { owner: 'Offline legion check', ownerLevel: 40, enabled: true, generatedEnabled: true, generatedCount: 1, generatedLimit: 23,
      limit: 23, recruitRemaining: 22, teamKind: 'Alliance', request: 'offline-request',
      active: [{ id: 2, name: 'LegionBot', level: 40, playerClass: 'CLERIC', role: 'HEALER', order: 'FOLLOW', health: 100, mana: 100, status: 'Idle',
        generated: true, temporary: true, questions: [], quests: [], world: sample.world, closing: sample.closing,
        legionStorageAvailable: sample.available, legionStorageReason: sample.available ? '' : 'Bring both characters within talking distance of the same accessible legion warehouse keeper.', legionStorageName: 'Native Legion', legionStorageLimit: 24, legionStorage: sample.empty ? [] : [{id:502,itemId:112000002,name:'Shared armor',count:1}],
        warehouseAvailable: sample.available, warehouseReason: sample.available ? '' : 'Bring both characters within talking distance of the same accessible warehouse keeper.', warehouseLimit: 24,
        warehouse: sample.empty ? [] : [{ id: 502, itemId: 112000002, name: 'Stored armor', count: 1 }],
        inventory: [{ id: 501, itemId: 164000001, name: 'Recovery potions', count: 17, equipped: false, slots: [], bankable: true, legionStorable: true },
          { id: 503, itemId: 112000003, name: 'Equipped armor', count: 1, equipped: true, slots: ['TORSO'], bankable: false, legionStorable: false },
          { id: 504, itemId: 182400001, name: 'Kinah', count: 50, equipped: false, slots: [], bankable: false, legionStorable: false }] }],
      roster: [{ id: 2, name: 'LegionBot', generated: true, ready: true, reserved: true }],
      classes: ['CLERIC'], startingClasses: ['PRIEST'], roles: ['HEALER'], quests: [], presets: [], savedBots: [] };
    await page.goto('http://legion-check.invalid/market/companions/bots.html?session_id=offline-fixture');
    await page.locator('#owner-label').filter({ hasText: fixture.owner }).waitFor();
    await page.locator('#active [data-bot-id="2"]').click(); await page.locator('#bot-tab-equipment').click();
    const deposits=page.locator('[data-action="legiondeposit"]');
    check(await deposits.count()===(sample.world?0:1),'Only eligible owned stack offers Deposit to legion');
    check(await page.locator('[data-action="legionwithdraw"]').count()===0,'No unsupported upstream withdrawal action');
    if(sample.world){check(!(await page.locator('#bot-content').textContent()).includes('Legion storage:'),'Invited world bot shared custody hidden');continue;}
    check((await page.locator('#bot-content').textContent()).includes('Legion storage: Native Legion ('+(sample.empty?0:1)+' / 24)'),'Legion name and native capacity visible');
    check(await deposits.isDisabled()===(!sample.available || sample.closing),'Native rights/keeper/lease/closing blocker enforced by UI');
    check((await page.locator('#bot-content').textContent()).includes(sample.empty?'No legion items':'Shared armor'),'Read-only shared inventory visible');
    if(!sample.available)check((await page.locator('#bot-content').textContent()).includes('legion warehouse keeper'),'Access reason visible');
    if(sample.available && !sample.closing){
      check((await page.locator('#bot-content').textContent()).includes('Deposited items belong to the legion'),'Donation custody communicated before action');
      await deposits.click();await page.waitForFunction(()=>!document.querySelector('[data-action="legiondeposit"]'));
      check(requests.at(-1).action==='legiondeposit' && requests.at(-1).item==='501','Exact native object/request submitted');
      check((await page.locator('#bot-content').textContent()).includes('Legion storage: Native Legion ('+(sample.empty?1:2)+' / 24)'),'Acknowledged response changes shared occupancy');
      check((await page.locator('#bot-content').textContent()).includes('Recovery potions \u00d717'),'Whole stack count preserved after shared deposit');
      await page.screenshot({path:path.join(output,'legion-storage.png'),fullPage:true});
    }
  }
  check(errors.length === 0, 'No browser exceptions');
  fs.writeFileSync(path.join(output, 'legion-checks.json'), JSON.stringify({ checks, cases, requests, errors, nativeGameServerContact: false }, null, 2));
  console.log('OK: ' + checks + ' real-browser legion storage checks; simulated responses only'); await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
