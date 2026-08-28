/**
 * Inlines the single-file build into one self-contained HTML document that
 * runs by double-clicking it — no server, no npm, no Android toolchain.
 *
 * Usage: npx vite build --config vite.singlefile.config.ts && node scripts/make-singlefile.mjs
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const DIST = 'dist-single';
const OUT = 'out';

const html = readFileSync(join(DIST, 'index.html'), 'utf8');
const js = readFileSync(join(DIST, 'game.js'), 'utf8');

let css = '';
try {
  css = readFileSync(join(DIST, 'game.css'), 'utf8');
} catch {
  // Vite names the stylesheet after the entry; fall back to whatever it emitted.
  const match = html.match(/href="[^"]*?([^/"]+\.css)"/);
  if (match) css = readFileSync(join(DIST, match[1]), 'utf8');
}

let out = html
  // Drop the linked stylesheet and script; they are inlined below.
  .replace(/\s*<link[^>]+rel="stylesheet"[^>]*>/g, '')
  .replace(/\s*<script[^>]*src="[^"]*"[^>]*><\/script>/g, '');

out = out.replace('</head>', `<style>\n${css}\n</style>\n</head>`);
// `</script>` inside the bundle would close the tag early.
out = out.replace(
  '</body>',
  `<script>\n${js.replace(/<\/script>/gi, '<\\/script>')}\n</script>\n</body>`
);

mkdirSync(OUT, { recursive: true });
const file = join(OUT, 'PC-Builder.html');
writeFileSync(file, out);

const kb = (Buffer.byteLength(out) / 1024).toFixed(0);
console.log(`Wrote ${file} (${kb} KB, fully self-contained)`);
