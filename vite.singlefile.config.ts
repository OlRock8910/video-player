import { defineConfig } from 'vite';
import { fileURLToPath, URL } from 'node:url';

/**
 * Builds the game as one self-contained bundle so it can be inlined into a
 * single HTML file and opened straight off the desktop — no server, no
 * toolchain. See scripts/make-singlefile.mjs, which does the inlining.
 *
 * IIFE rather than ESM because a file:// page cannot load module scripts.
 */
export default defineConfig({
  base: './',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: 'dist-single',
    target: 'es2020',
    sourcemap: false,
    cssCodeSplit: false,
    assetsInlineLimit: 100_000_000,
    rollupOptions: {
      output: {
        format: 'iife',
        inlineDynamicImports: true,
        entryFileNames: 'game.js',
        assetFileNames: 'game[extname]',
      },
    },
  },
});
