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
    const url = new URL(route.request().url()); assert.equal(url.hostname, 'warehouse-check.invalid');
    if (url.pathname.endsWith('/state')) return route.fulfill({ contentType: 'application/json', body: JSON.stringify(fixture) });
    if (url.pathname.endsWith('/action')) {
      const data = Object.fromEntries(new URLSearchParams(route.request().postData())); requests.push(data);
      assert.equal(data.session_id, 'offline-fixture'); assert.equal(data.request, 'offline-request');
      assert.equal(data.name, 'WarehouseBot'); assert(['bankdeposit', 'bankwithdraw'].includes(data.action));
      const bot = fixture.active[0];
      if (data.action === 'bankdeposit') {
        assert.equal(data.item, '501'); const item = bot.inventory.find(i => i.id === 501); bot.inventory = bot.inventory.filter(i => i.id !== 501); bot.warehouse.push(item);
      } else {
        assert.equal(data.item, '501'); const item = bot.warehouse.find(i => i.id === 501); bot.warehouse = bot.warehouse.filter(i => i.id !== 501); bot.inventory.push(item);
      }
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
    fixture = { owner: 'Offline warehouse check', ownerLevel: 40, enabled: true, generatedEnabled: true, generatedCount: 1, generatedLimit: 23,
      limit: 23, recruitRemaining: 22, teamKind: 'Alliance', request: 'offline-request',
      active: [{ id: 2, name: 'WarehouseBot', level: 40, playerClass: 'CLERIC', role: 'HEALER', order: 'FOLLOW', health: 100, mana: 100, status: 'Idle',
        generated: true, temporary: true, questions: [], quests: [], world: sample.world, closing: sample.closing,
        warehouseAvailable: sample.available, warehouseReason: sample.available ? '' : 'Bring both characters within talking distance of the same accessible warehouse keeper.', warehouseLimit: 24,
        warehouse: sample.empty ? [] : [{ id: 502, itemId: 112000002, name: 'Stored armor', count: 1 }],
        inventory: [{ id: 501, itemId: 164000001, name: 'Recovery potions', count: 17, equipped: false, slots: [], bankable: true },
          { id: 503, itemId: 112000003, name: 'Equipped armor', count: 1, equipped: true, slots: ['TORSO'], bankable: false },
          { id: 504, itemId: 182400001, name: 'Kinah', count: 50, equipped: false, slots: [], bankable: false }] }],
      roster: [{ id: 2, name: 'WarehouseBot', generated: true, ready: true, reserved: true }],
      classes: ['CLERIC'], startingClasses: ['PRIEST'], roles: ['HEALER'], quests: [], presets: [], savedBots: [] };
    await page.goto('http://warehouse-check.invalid/market/companions/bots.html?session_id=offline-fixture');
    await page.locator('#owner-label').filter({ hasText: fixture.owner }).waitFor();
    await page.locator('#active [data-bot-id="2"]').click(); await page.locator('#bot-tab-equipment').click();
    const deposits = page.locator('[data-action="bankdeposit"]'), withdrawals = page.locator('[data-action="bankwithdraw"]');
    check(await deposits.count() === (sample.world ? 0 : 1), 'Only owned, eligible unequipped stack has Deposit');
    check(await withdrawals.count() === (sample.world || sample.empty ? 0 : 1), 'Stored owned item has Withdraw');
    if (sample.world) { check(!(await page.locator('#bot-content').textContent()).includes('Private warehouse'), 'World bot custody hidden'); continue; }
    check((await page.locator('#bot-content').textContent()).includes('Private warehouse (' + (sample.empty ? 0 : 1) + ' / 24)'), 'Actual occupancy and native capacity visible');
    check(await deposits.isDisabled() === (!sample.available || sample.closing), 'Banker/closing gate disables Deposit');
    if (!sample.empty) check(await withdrawals.isDisabled() === (!sample.available || sample.closing), 'Banker/closing gate disables Withdraw');
    if (!sample.available) check((await page.locator('#bot-content').textContent()).includes('talking distance'), 'Precise native keeper requirement visible');
    if (sample.available && !sample.closing) {
      await deposits.click(); await page.locator('[data-action="bankwithdraw"]').nth(1).waitFor();
      check(requests.at(-1).action === 'bankdeposit' && requests.at(-1).item === '501', 'Exact deposit object/request submitted');
      check(await page.locator('[data-action="bankdeposit"]').count() === 0, 'Committed response removes cube action');
      check((await page.locator('#bot-content').textContent()).includes('Private warehouse (2 / 24)'), 'Committed response updates occupancy');
      await page.locator('[data-action="bankwithdraw"]').nth(1).click(); await page.locator('[data-action="bankdeposit"]').waitFor();
      check(requests.at(-1).action === 'bankwithdraw' && requests.at(-1).item === '501', 'Exact withdrawal object submitted');
      check((await page.locator('#bot-content').textContent()).includes('Recovery potions ×17'), 'Whole stack count survives displayed roundtrip');
      await page.screenshot({ path: path.join(output, 'private-warehouse.png'), fullPage: true });
    }
  }
  check(errors.length === 0, 'No browser exceptions');
  fs.writeFileSync(path.join(output, 'warehouse-checks.json'), JSON.stringify({ checks, cases, requests, errors, nativeGameServerContact: false }, null, 2));
  console.log('OK: ' + checks + ' real-browser warehouse checks; simulated responses only'); await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
