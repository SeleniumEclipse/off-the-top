// Check/export the selected visual prototype, not the Android application.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const url = 'http://127.0.0.1:4176/design/card-table.html';
const output = path.resolve(__dirname, '../design/previews');

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 950 }, deviceScaleFactor: 1 });
  const errors = [];
  const failed = [];
  page.on('pageerror', error => errors.push(String(error)));
  page.on('response', response => { if (response.status() >= 400 && response.url().includes('127.0.0.1')) failed.push(response.url()); });
  try {
    await page.goto(url);
    await page.evaluate(() => document.fonts.ready);
    const table = page.locator('.table');
    const controls = page.getByRole('navigation', { name: 'Preview state' });
    const regularView = () => table.getByRole('button', { name: 'Correct', exact: true }).isEnabled();
    const home = () => controls.getByRole('button', { name: 'Decks', exact: true }).click();
    assert.equal(await table.locator('.home-screen:visible').count(), 1);
    assert.equal(await page.locator('.icon-row img').count(), 3);

    for (const [key, name, mark, file] of [
      ['wild', 'Wild World', '#wild-icon', 'wild-world'],
      ['everyday', 'Everyday Things', '#everyday-icon', 'everyday-things'],
      ['actions', 'Do Your Thing', '#actions-icon', 'do-your-thing'],
    ]) {
      console.log(`Checking custom ${name} mark and deck`);
      // Exported assets and in-card vector paths must be the same original drawing.
      const inlinePaths = await page.locator(`${mark} path`).evaluateAll(paths => paths.map(p => p.getAttribute('d')));
      const response = await page.request.get(new URL(`icons/${file}.svg`, url).href);
      assert.equal(response.status(), 200);
      const svg = await response.text();
      for (const d of inlinePaths) assert(svg.includes(d), `${name}: exported SVG differs from card mark`);
      assert(!/[\u2660-\u2667\u{1F000}-\u{1FAFF}]/u.test(svg), 'No suits or emoji');
      await table.getByRole('button', { name: `Play ${name}`, exact: true }).click();
      assert.equal(await table.locator('.round-deck').innerText(), name);
      const hrefs = await table.locator('.active-icon').evaluateAll(uses => uses.map(use => use.getAttribute('href')));
      assert.deepEqual(hrefs, [mark, mark]);
      for (let i = 0; i < 4; i++) {
        await page.waitForFunction(() => !document.querySelector('.word').hidden);
        const overflow = await table.locator('.word').evaluate(el => ({ width: el.scrollWidth > el.clientWidth + 1, height: el.scrollHeight > el.parentElement.clientHeight + 1 }));
        assert.deepEqual(overflow, { width: false, height: false }, `${name}: sample clue should fit`);
        await table.getByRole('button', { name: 'Correct', exact: true }).click();
        await page.waitForFunction(() => document.querySelector('.table').dataset.feedback === undefined);
      }
      assert.equal(await table.locator('.card-score').getAttribute('aria-label'), '8 correct');
      await table.getByRole('button', { name: 'Pass', exact: true }).click();
      await page.waitForFunction(() => document.querySelector('.table').dataset.feedback === undefined);
      assert.equal(await table.locator('.card-score').getAttribute('aria-label'), '8 correct');
      await table.getByRole('button', { name: 'Decks', exact: true }).click();
    }

    for (const seconds of [30, 60, 90, 120]) {
      await table.getByRole('button', { name: `${seconds}s`, exact: true }).click();
      assert.equal(await table.getByRole('button', { name: `${seconds}s`, exact: true }).getAttribute('aria-pressed'), 'true');
    }
    await table.getByRole('button', { name: '60s', exact: true }).click();
    for (const action of ['How to play', 'Settings', 'Recent rounds']) {
      await table.getByRole('button', { name: action, exact: true }).click();
      assert.equal(await page.getByRole('dialog').count(), 1);
      await page.keyboard.press('Tab');
      assert.equal(await page.evaluate(() => document.activeElement.closest('.dialog') !== null), true);
      await page.keyboard.press('Escape');
      assert.equal(await page.getByRole('dialog').count(), 0);
    }

    await table.getByRole('button', { name: 'Play Wild World', exact: true }).click();
    await table.getByRole('button', { name: 'Correct', exact: true }).click();
    assert.equal(await table.getAttribute('data-feedback'), 'correct');
    assert.equal(await regularView(), false);
    await table.getByRole('button', { name: 'Pause', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Resume', exact: true }).click();
    assert.equal(await regularView(), true, 'Resume during feedback must not leave controls disabled');
    assert.equal(await table.locator('.card-score').getAttribute('aria-label'), '5 correct');

    for (const view of ['correct', 'pass']) {
      await controls.getByRole('button', { name: `${view === 'correct' ? 'Correct' : 'Pass'} feedback`, exact: true }).click();
      assert.equal(await table.getAttribute('data-feedback'), view);
      assert.equal(await table.locator('.feedback strong').innerText(), view === 'correct' ? 'Correct' : 'Pass');
    }

    for (const width of [390, 588, 1280]) {
      await page.setViewportSize({ width, height: 950 });
      await page.goto(url);
      await page.evaluate(() => document.fonts.ready);
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `No horizontal overflow at ${width}`);
      await table.getByRole('button', { name: 'Play Everyday Things', exact: true }).click();
      await table.getByRole('button', { name: 'Correct', exact: true }).click();
      await page.waitForFunction(() => document.querySelector('.table').dataset.feedback === undefined);
      assert.equal(await table.locator('.word').innerText(), 'WASHING MACHINE');
    }

    await page.setViewportSize({ width: 1280, height: 950 });
    for (const view of ['decks', 'round', 'correct', 'pass']) {
      await page.goto(`${url}?view=${view}&isolate=1`);
      await page.evaluate(() => document.fonts.ready);
      await page.locator('.phone-viewport').screenshot({ path: path.join(output, `casino-table-${view}.png`) });
    }
    await page.goto(url);
    await page.evaluate(() => document.fonts.ready);
    await page.locator('.icon-study').screenshot({ path: path.join(output, 'casino-category-icons.png') });

    // Contact sheet: no external assets, no game or repo mutations.
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.evaluate(() => {
      document.body.innerHTML = '<section id="contact"><header><h1>Off the Top / Card Table</h1><p>Green felt, red backs, original category marks. No card suits.</p></header><div class="contact-grid">' + [
        ['decks', 'Deck selection'], ['round', 'In a round'], ['correct', 'Correct'], ['pass', 'Pass'],
      ].map(([file, title]) => `<figure><figcaption>${title}</figcaption><img alt="${title} concept" src="previews/casino-table-${file}.png"></figure>`).join('') + '</div></section>';
      const style = document.createElement('style');
      style.textContent = '#contact{width:1440px;padding:24px 28px;background:#ebe6dc}#contact header{display:flex;justify-content:space-between;align-items:center;padding-bottom:8px}#contact h1{font-size:28px}#contact p{font-size:15px}.contact-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.contact-grid figure{margin:0}.contact-grid figcaption{font-size:17px;font-weight:700;margin-bottom:8px}.contact-grid img{display:block;width:100%;border:1px solid #173c2c}';
      document.head.append(style);
    });
    await page.locator('#contact img').evaluateAll(images => Promise.all(images.map(image => image.decode())));
    await page.locator('#contact').screenshot({ path: path.join(output, 'casino-table-comparison.png') });
    assert.deepEqual(errors, []);
    assert.deepEqual(failed, []);
    console.log('PASS: original SVGs match card marks; three decks, category changes, fitted words, scoring/pass, feedback, pause, dialogs, four durations and three viewport widths.');
    console.log('Exported four screen states, category icon sheet and comparison. No Android files modified.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });