/**
 * Build da TV: empacota o legado + PlayerFactory num único `app.js` IIFE.
 *
 * Regras da Fase 2:
 *  - a OTA só entrega `app.js` e `styles.css` → nada de arquivo novo no cliente;
 *  - `hls.js` NÃO é embutido: resolve para o shim que reexporta o `window.Hls`
 *    carregado por `libs/hls.min.js` (o bundle fica abaixo de 350 KB);
 *  - saída sem sourcemap (a OTA não pode baixar `.map`).
 */
import { build } from 'esbuild';
import { statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const outfile = path.join(root, 'smart-tv/mk21-tv/app.js');
const LIMIT_BYTES = 350 * 1024;

await build({
  entryPoints: [path.join(root, 'smart-tv/mk21-tv/src/main.js')],
  outfile,
  bundle: true,
  format: 'iife',
  target: ['es2019'],
  minify: true,
  sourcemap: false,
  legalComments: 'none',
  charset: 'utf8',
  banner: {
    js: '/*! MK21 Play - app.js gerado por `npm run build:tv`. Nao editar: o fonte esta em smart-tv/mk21-tv/src/. */',
  },
  alias: {
    'hls.js': path.join(root, 'smart-tv/mk21-tv/src/shims/hls-global.js'),
  },
  define: {
    'process.env.NODE_ENV': '"production"',
  },
  logLevel: 'warning',
});

const size = statSync(outfile).size;
const kb = (size / 1024).toFixed(1);
console.log(`[build] app.js gerado: ${kb} KB`);

if (size > LIMIT_BYTES) {
  console.error(`[build] FALHOU: app.js acima do limite de 350 KB (${kb} KB).`);
  process.exit(1);
}
