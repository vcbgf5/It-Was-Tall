// Renders frames of every animation into tools/preview/shots/anim_<name>_<i>.png
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const ANIM = JSON.parse(fs.readFileSync(path.join(__dirname, '../../src/main/resources/assets/itwastall/animations/entity/tall_one.animation.json')));
(async () => {
  const out = path.join(__dirname, 'shots');
  fs.mkdirSync(out, { recursive: true });
  const url = 'file://' + path.join(__dirname, 'it_was_tall_preview.html');
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 420, height: 620 } });
  page.on('pageerror', e => console.error('pageerror', e.message));
  for (const [key, a] of Object.entries(ANIM.animations)) {
    const name = key.split('.').pop();
    const view = ['walk', 'run', 'dig', 'knock', 'attack'].includes(name) ? 'side' : 'three';
    for (let i = 0; i < 4; i++) {
      const t = (a.animation_length * (name === 'sink' ? (i + 1) / 4 : i / 4)).toFixed(3);
      await page.goto(`${url}?n=${name}${i}#view=${view}&anim=${name}&t=${t}&pause=1&ref=0`);
      await page.addStyleTag({ content: '#ui,#anims,#hint{display:none!important}' });
      await page.waitForTimeout(400);
      await page.screenshot({ path: path.join(out, `anim_${name}_${i}.png`) });
    }
    console.log('frames', name);
  }
  await browser.close();
})();
