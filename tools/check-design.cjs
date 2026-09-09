// Uses an existing Playwright installation; no game build or package changes.
// PLAYWRIGHT_MODULE may point at an installed playwright module outside this project.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = 'http://127.0.0.1:4176/design/theme-options.html';
const output = path.resolve(__dirname, '../design/previews');

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on('pageerror', error => errors.push(String(error)));
  const badResources = [];
  page.on('response', response => { if (response.status() >= 400 && response.url().includes('127.0.0.1')) badResources.push(response.url()); });
  try {
    await page.goto(base);
    await page.evaluate(() => document.fonts.ready);
    assert.equal(await page.locator('.concept:visible').count(), 3);
    assert.equal(await page.locator('.deck-screen:visible').count(), 3);
    assert.equal(await page.locator('.round-screen:visible').count(), 0);
    await page.getByRole('button', { name: 'In a round', exact: true }).click();
    assert.equal(await page.locator('.round-screen:visible').count(), 3);
    await page.getByRole('button', { name: 'Deck selection', exact: true }).click();

    for (const [id, title, slug] of [['table', 'Card Table', 'card-table'], ['scoreboard', 'Scoreboard', 'scoreboard'], ['tickets', 'Box Office', 'box-office']]) {
      console.log(`Checking ${title}`);
      await page.getByRole('button', { name: title, exact: true }).click();
      assert.equal(await page.locator('.concept:visible').count(), 1);
      const screen = page.locator(`[data-theme="${id}"]`);
      await screen.getByRole('button', { name: '90s', exact: true }).click();
      assert.equal(await screen.getByRole('button', { name: '90s', exact: true }).getAttribute('aria-pressed'), 'true');
      await screen.getByRole('button', { name: '60s', exact: true }).click();
      await screen.getByRole('button', { name: 'How to play', exact: true }).click();
      assert.equal(await screen.getByRole('dialog').count(), 1);
      await page.keyboard.press('Escape');
      assert.equal(await screen.getByRole('dialog').count(), 0);
      await screen.getByRole('button', { name: 'Settings', exact: true }).click();
      await screen.getByRole('button', { name: 'Back', exact: true }).click();
      await screen.getByRole('button', { name: 'Recent rounds', exact: true }).click();
      await screen.getByRole('button', { name: 'Back', exact: true }).click();

      for (const deck of ['Wild World', 'Everyday Things', 'Do Your Thing']) {
        await screen.getByRole('button', { name: `Play ${deck}`, exact: true }).click();
        assert.equal(await screen.locator('.round-screen:visible .rail-deck').innerText(), deck);
        await screen.locator('.round-screen:visible [data-action=back]').click();
      }
      await screen.getByRole('button', { name: 'Play Wild World', exact: true }).click();
      await screen.getByRole('button', { name: 'Correct', exact: true }).click();
      assert.equal(await screen.locator('.word:visible').innerText(), 'WASHING MACHINE');
      if (id === 'scoreboard') assert.equal(await screen.locator('.score-digits').getAttribute('aria-label'), '5 correct');
      else assert.equal(await screen.locator('.score:visible').innerText(), '5');
      await screen.getByRole('button', { name: 'Pass', exact: true }).click();
      assert.equal(await screen.locator('.word:visible').innerText(), 'WALKING A DOG');
      if (id === 'scoreboard') assert.equal(await screen.locator('.score-digits').getAttribute('aria-label'), '5 correct');
      else assert.equal(await screen.locator('.score:visible').innerText(), '5');
      await screen.getByRole('button', { name: 'Pause', exact: true }).click();
      await screen.getByRole('button', { name: 'Resume', exact: true }).click();
      assert.equal(await screen.locator('.word:visible').innerText(), 'WALKING A DOG');
      for (let i = 0; i < 3; i++) await screen.getByRole('button', { name: 'Correct', exact: true }).click();
      const overflow = await screen.locator('.word:visible').evaluate(el => el.scrollWidth > el.clientWidth + 1 || el.scrollHeight > el.parentElement.clientHeight);
      assert.equal(overflow, false, `${title}: clue text overflows`);

      for (const mode of ['decks', 'round']) {
        await page.goto(`${base}?theme=${id}&screen=${mode}&isolate=1`);
        await page.evaluate(() => document.fonts.ready);
        await page.locator('.concept:visible .viewport').screenshot({ path: path.join(output, `${slug}-${mode}.png`) });
      }
      await page.goto(base);
    }

    for (const width of [390, 588, 1280]) {
      await page.setViewportSize({ width, height: 950 });
      await page.goto(base);
      await page.evaluate(() => document.fonts.ready);
      const fits = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth);
      assert(fits, `Horizontal page overflow at ${width}px`);
      await page.getByRole('button', { name: 'In a round', exact: true }).click();
      assert.equal(await page.locator('.round-screen:visible').count(), 3);
    }

    // A compact, original contact sheet: both states side-by-side for each direction.
    await page.setViewportSize({ width: 1440, height: 1200 });
    await page.goto(base);
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => {
      const rows = [
        ['Card Table', 'card-table', 'Dark felt + printed cards'],
        ['Scoreboard', 'scoreboard', 'Warm charcoal + mechanical numbers'],
        ['Box Office', 'box-office', 'Theater lettering + ticket stubs'],
      ];
      document.body.innerHTML = `<div id="sheet"><header><h1>Off the Top / theme options</h1><p>Original visual studies. Same Set fonts. No app changes.</p></header><div class="sheet-labels"><span>DECK SELECTION</span><span>IN A ROUND</span></div>${rows.map(([name, slug, note]) => `<section><h2>${name}<small>${note}</small></h2><div class="sheet-row"><img alt="${name} deck selection" src="previews/${slug}-decks.png"><img alt="${name} in a round" src="previews/${slug}-round.png"></div></section>`).join('')}</div>`;
      const style = document.createElement('style');
      style.textContent = '#sheet{padding:28px 32px;width:1440px;background:#eeeae3}#sheet header{display:flex;align-items:baseline;justify-content:space-between;border-bottom:1px solid #bdc1b6;padding-bottom:18px}#sheet h1{font-size:30px}#sheet p{font-size:14px;color:#586154}#sheet h2{font-size:23px;margin:16px 0 10px}#sheet small{font:400 14px Barlow;margin-left:20px;color:#586154}.sheet-row,.sheet-labels{display:grid;grid-template-columns:1fr 1fr;gap:18px}.sheet-labels{padding-top:18px;font-size:12px;letter-spacing:1.5px;font-weight:700}.sheet-row img{width:100%;display:block;border:1px solid #48554b}';
      document.head.append(style);
    });
    await page.locator('#sheet img').evaluateAll(images => Promise.all(images.map(img => img.decode())));
    await page.locator('#sheet').screenshot({ path: path.join(output, 'theme-comparison.png') });
    assert.deepEqual(errors, [], 'Browser errors');
    assert.deepEqual(badResources, [], 'Missing preview resources');
    console.log('PASS: 3 distinct themes, both screens, deck/duration selection, demo scoring, pause, overlays, 3 viewport widths, 7 exported images.');
    console.log(`Exports: ${output}`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });