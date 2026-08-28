/**
 * Verifies the self-contained single-file build.
 *
 * The point of PC-Builder.html is that it runs by double-clicking it, so this
 * opens it over file:// with no server at all — which is the only way to prove
 * nothing in the bundle still reaches for a network origin or a sibling asset.
 *
 * Run with: node tests/verify-singlefile.mjs [path-to-html]
 */
import { chromium } from 'playwright';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

const FILE = resolve(process.argv[2] ?? 'out/PC-Builder.html');
if (!existsSync(FILE)) {
  console.error(`✗ ${FILE} not found — run "npm run build:singlefile" first.`);
  process.exit(1);
}

const errors = [];

const preinstalled = process.env.CHROME_PATH || '/opt/pw-browsers/chromium';
const browser = await chromium.launch({
  ...(existsSync(preinstalled) ? { executablePath: preinstalled } : {}),
  args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--no-sandbox'],
});
const context = await browser.newContext({
  viewport: { width: 412, height: 915 },
  deviceScaleFactor: 1,
  isMobile: true,
  hasTouch: true,
});
const page = await context.newPage();
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(m.text());
});
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
// Any request leaving the page means the bundle is not actually self-contained.
page.on('request', (r) => {
  if (!r.url().startsWith('file://') && !r.url().startsWith('data:')) {
    errors.push(`external request: ${r.url()}`);
  }
});

console.log(`\n▶ verifying ${FILE}\n`);

await page.goto(`file://${FILE}`, { waitUntil: 'load' });
await page.waitForSelector('#menu.active', { timeout: 30000 });
console.log('  ✓ boots from file:// with no server');

const title = (await page.locator('.menu-title').textContent())?.trim();
if (title !== 'PC BUILDER') throw new Error(`menu title was "${title}"`);
await page.waitForTimeout(2000);

await page.locator('.btn', { hasText: 'Free Build' }).first().click();
await page.waitForSelector('#workshop.active', { timeout: 15000 });
console.log('  ✓ workshop opens');

await page.locator('.tray-item', { hasText: 'Lumen Flow' }).first().click();
await page.waitForTimeout(1500);
const step = (await page.locator('.step-label').textContent())?.trim();
if (!/OPEN IT UP/.test(step ?? '')) throw new Error(`expected the panel step, got "${step}"`);
console.log(`  ✓ case placed — ${step}`);

await browser.close();

if (errors.length > 0) {
  console.log('\n✗ problems:\n');
  for (const e of errors) console.log(`   ${e}`);
  process.exit(1);
}
console.log('\n✓ single-file build runs fully offline with no console errors\n');
