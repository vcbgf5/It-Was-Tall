// Renders preview screenshots with headless Chromium.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const out = path.join(__dirname, 'shots');
  fs.mkdirSync(out, { recursive: true });
  const url = 'file://' + path.join(__dirname, 'it_was_tall_preview.html');
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 900, height: 1100 } });
  page.on('pageerror', e => console.error('pageerror', e.message));
  page.on('console', m => m.type() === 'error' && console.error('console', m.text()));
  const shots = [
    ['1_front', 'view=front'], ['2_three_quarter', 'view=three'], ['3_side', 'view=side'],
    ['4_head', 'view=head'], ['5_night', 'view=low&night=1&ref=0'], ['6_jumpscare', 'js=1'],
  ];
  for (const [name, hash] of shots) {
    await page.goto(url + '?s=' + name + '#' + hash);
    await page.waitForTimeout(700);
    await page.screenshot({ path: path.join(out, name + '.png') });
    console.log('shot', name);
  }
  await browser.close();
})();
