// Real browser against maintained media and simulated responses only. Never contacts GameServer.
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const { chromium } = require(process.env.AION_PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '..');
const output = path.resolve(process.argv[2]);
const media = path.join(root, 'game-server/config/playerbots/media');
const executablePath = process.env.AION_CHECK_BROWSER;

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(executablePath ? { executablePath } : {}) });
  const page = await browser.newPage({ viewport: { width: 1050, height: 800 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  let fixture;
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    assert.equal(url.hostname, 'companion-check.invalid', 'Unexpected external request');
    const name = url.pathname.split('/').pop();
    if (url.pathname.endsWith('/state')) {
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(fixture) });
    }
    assert(['bots.html', 'bots.js', 'bots.css'].includes(name), 'Unexpected route: ' + url.pathname);
    return route.fulfill({ contentType: name.endsWith('.js') ? 'application/javascript' : name.endsWith('.css') ? 'text/css' : 'text/html',
      body: fs.readFileSync(path.join(media, name)) });
  });
  const cases = [
    { count: 10, capacity: 20, active: 5, enabled: true, generatedEnabled: true, disabled: false },
    { count: 49, capacity: 50, active: 5, enabled: true, generatedEnabled: true, disabled: false },
    { count: 50, capacity: 50, active: 0, enabled: true, generatedEnabled: true, disabled: true },
    { count: 51, capacity: 20, active: 0, enabled: true, generatedEnabled: true, disabled: true },
    { count: 0, capacity: 0, active: 0, enabled: true, generatedEnabled: true, disabled: true },
    { count: 1, capacity: 20, active: 0, enabled: false, generatedEnabled: true, disabled: true },
    { count: 1, capacity: 20, active: 0, enabled: true, generatedEnabled: false, disabled: true },
    { count: 19, capacity: 20, active: 0, enabled: true, generatedEnabled: true, disabled: false },
    { count: 30, capacity: 50, active: 6, limit: 23, enabled: true, generatedEnabled: true, disabled: false },
    { count: 22, capacity: 23, active: 22, limit: 23, enabled: true, generatedEnabled: true, disabled: false },
    { count: 30, capacity: 50, active: 23, limit: 23, enabled: true, generatedEnabled: true, disabled: false },
    { count: 30, capacity: 50, active: 6, limit: 23, remaining: 0, enabled: true, generatedEnabled: true, disabled: false }
  ];
  let checks = 0;
  for (const sample of cases) {
    fixture = { owner: 'Offline UI check', ownerLevel: 40, enabled: sample.enabled, generatedEnabled: sample.generatedEnabled,
      generatedCount: sample.count, generatedLimit: sample.capacity, limit: sample.limit || 5,
      recruitRemaining: sample.remaining == null ? Math.max(0, (sample.limit || 5) - sample.active) : sample.remaining,
      teamKind: sample.limit ? 'Alliance' : 'Party', levelDifference: 10, request: 'offline-fixture',
      active: Array.from({ length: sample.active }, (_, i) => ({ id: i + 1, name: 'Companion' + (i + 1), level: 40,
        playerClass: 'CLERIC', role: 'HEALER', order: 'FOLLOW', status: 'Idle', health: 100, mana: 100, generated: true, temporary: true,
        questions: [], inventory: [], quests: [] })),
      roster: Array.from({ length: sample.count }, (_, i) => ({ id: i + 1, name: 'Companion' + (i + 1), generated: true, ready: i < sample.active || i !== sample.count - 1, reserved: i < sample.active })),
      classes: ['CLERIC', 'SORCERER'], startingClasses: ['PRIEST', 'MAGE'], roles: ['HEALER', 'CASTER'], quests: [], presets: [], savedBots: [] };
    await page.goto('http://companion-check.invalid/market/companions/bots.html?session_id=offline-fixture');
    await page.locator('#owner-label').filter({ hasText: fixture.owner }).waitFor();
    await page.locator('#tab-create').click();
    assert.equal(await page.locator('#create button[type=submit]').isDisabled(), sample.disabled); checks++;
    assert((await page.locator('#creation-capacity').textContent()).includes(sample.count + ' / ' + sample.capacity)); checks++;
    if (sample.count === 49) await page.screenshot({ path: path.join(output, 'full-party-create-49-of-50.png'), fullPage: true });
    if (sample.active === 22) await page.screenshot({ path: path.join(output, 'alliance-22-of-23.png'), fullPage: true });
    await page.locator('#model').selectOption('create');
    assert.equal(await page.locator('#create button[type=submit]').isDisabled(), !sample.enabled || fixture.recruitRemaining <= 0); checks++;
    await page.locator('#model').selectOption('generate');
    assert.equal(await page.locator('#create button[type=submit]').isDisabled(), sample.disabled); checks++;
  }
  assert.deepEqual(errors, [], 'Browser exceptions'); checks++;
  fs.writeFileSync(path.join(output, 'checks.json'), JSON.stringify({ checks, cases, errors, nativeGameServerContact: false }, null, 2));
  console.log('OK: ' + checks + ' real-browser creation/capacity/model-switch checks; simulated responses only');
  await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
