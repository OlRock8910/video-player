// Renders Mono's vinyl mark to PNG.
//
// There is no image tooling on the build machine, so this draws the record
// procedurally into an RGBA buffer and encodes the PNG with Node's zlib. Shapes
// are sampled at 4x and boxed down, which is enough anti-aliasing for concentric
// circles.
//
//   node web/tools/make-icons.mjs
//
// Re-run only when the mark changes; the PNGs it writes are committed.

import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const OUT = join(dirname(fileURLToPath(import.meta.url)), "..", "icons");
const SS = 4; // supersampling factor

const PAPER = [0xf1, 0xf1, 0xf1];
const DISC_EDGE = [0x2a, 0x2a, 0x2a];
const DISC_CORE = [0x0b, 0x0b, 0x0b];
const ACCENT = [0xff, 0x6a, 0x00];

/** Straight-alpha "over" composite of an opaque colour onto the canvas. */
function blend(px, i, rgb, alpha) {
  px[i] = Math.round(px[i] * (1 - alpha) + rgb[0] * alpha);
  px[i + 1] = Math.round(px[i + 1] * (1 - alpha) + rgb[1] * alpha);
  px[i + 2] = Math.round(px[i + 2] * (1 - alpha) + rgb[2] * alpha);
  px[i + 3] = 255;
}

/**
 * @param size    edge length in real pixels
 * @param inset   fraction of the edge left empty around the record; maskable
 *                icons need the mark inside the middle ~80% so a launcher can
 *                crop it to any shape without clipping.
 */
function render(size, inset) {
  const n = size * SS;
  const big = new Uint8Array(n * n * 4);

  // Page colour behind everything.
  for (let i = 0; i < n * n; i++) {
    big[i * 4] = PAPER[0];
    big[i * 4 + 1] = PAPER[1];
    big[i * 4 + 2] = PAPER[2];
    big[i * 4 + 3] = 255;
  }

  const cx = n / 2;
  const cy = n / 2;
  const outer = (n / 2) * (1 - inset);

  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      const d = Math.hypot(dx, dy);
      if (d > outer) continue;
      const i = (y * n + x) * 4;

      // Disc, shaded from a lighter rim toward a darker centre.
      const t = d / outer;
      const disc = [
        Math.round(DISC_CORE[0] + (DISC_EDGE[0] - DISC_CORE[0]) * t),
        Math.round(DISC_CORE[1] + (DISC_EDGE[1] - DISC_CORE[1]) * t),
        Math.round(DISC_CORE[2] + (DISC_EDGE[2] - DISC_CORE[2]) * t),
      ];
      blend(big, i, disc, 1);

      // Grooves between the label edge and the rim.
      const groove = (d - outer * 0.42) / (outer * 0.56);
      if (groove > 0 && groove < 1) {
        const ring = Math.abs(Math.sin(groove * Math.PI * 13));
        if (ring > 0.86) blend(big, i, [255, 255, 255], (ring - 0.86) * 0.5);
      }

      // Centre label and spindle hole.
      if (d < outer * 0.4) blend(big, i, ACCENT, 1);
      if (d < outer * 0.09) blend(big, i, DISC_CORE, 1);
    }
  }

  // Box-filter down to the requested size.
  const px = new Uint8Array(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let r = 0;
      let g = 0;
      let b = 0;
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const j = ((y * SS + sy) * n + (x * SS + sx)) * 4;
          r += big[j];
          g += big[j + 1];
          b += big[j + 2];
        }
      }
      const count = SS * SS;
      const i = (y * size + x) * 4;
      px[i] = Math.round(r / count);
      px[i + 1] = Math.round(g / count);
      px[i + 2] = Math.round(b / count);
      px[i + 3] = 255;
    }
  }
  return px;
}

function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "latin1"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function png(size, px) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // truecolour with alpha
  // Each scanline is prefixed with filter type 0 (none).
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0;
    Buffer.from(px.buffer, px.byteOffset + y * size * 4, size * 4).copy(
      raw,
      y * (size * 4 + 1) + 1,
    );
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

mkdirSync(OUT, { recursive: true });
for (const [name, size, inset] of [
  ["icon-192.png", 192, 0.06],
  ["icon-512.png", 512, 0.06],
  ["maskable-512.png", 512, 0.2],
  ["favicon-64.png", 64, 0.04],
]) {
  writeFileSync(join(OUT, name), png(size, render(size, inset)));
  console.log("wrote", name);
}
