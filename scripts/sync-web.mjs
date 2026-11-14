#!/usr/bin/env node
/**
 * Sincroniza a camada Web a partir da fonte única de verdade na raiz do repositório
 * para os espelhos `web/` e `public/`.
 *
 * Uso:
 *   node scripts/sync-web.mjs          # Copia raiz -> web/ e public/
 *   node scripts/sync-web.mjs --check  # Valida no CI se web/ e public/ estão idênticos à raiz
 *
 * Nota: `public/assets/aistudio/` nunca é tocado nem removido.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');

const MIRROR_TARGETS = ['web', 'public'];

const SINGLE_FILES = [
  'index.html',
  'sw.js',
  'manifest.json',
  'apps.json',
  'servers.json',
  'version.json',
  'vercel.json',
  'icon.svg',
  'icon-192.png',
  'icon-512.png',
  'api/version.json',
  'api/proxy.js',
  'assets/logo.png',
];

const SYNC_DIRS = ['css', 'js'];

function sha256(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function listFilesRelative(dirPath, base = dirPath) {
  if (!fs.existsSync(dirPath)) return [];
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  const results = [];
  for (const entry of entries) {
    const full = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      results.push(...listFilesRelative(full, base));
    } else if (entry.isFile()) {
      results.push(path.relative(base, full).replace(/\\/g, '/'));
    }
  }
  return results.sort();
}

function collectCanonicalFiles() {
  const files = [...SINGLE_FILES];
  for (const dir of SYNC_DIRS) {
    const absDir = path.join(ROOT_DIR, dir);
    for (const rel of listFilesRelative(absDir)) {
      files.push(`${dir}/${rel}`);
    }
  }
  return files;
}

const isCheck = process.argv.includes('--check');
const canonicalFiles = collectCanonicalFiles();
const mismatches = [];
let copiedCount = 0;

for (const rel of canonicalFiles) {
  const src = path.join(ROOT_DIR, rel);
  if (!fs.existsSync(src)) {
    mismatches.push(`Fonte canônica ausente: ${rel}`);
    continue;
  }
  const srcHash = sha256(src);

  for (const target of MIRROR_TARGETS) {
    const dst = path.join(ROOT_DIR, target, rel);
    const dstExists = fs.existsSync(dst);
    const matches = dstExists && sha256(dst) === srcHash;

    if (isCheck) {
      if (!dstExists) {
        mismatches.push(`Ausente no espelho: ${target}/${rel}`);
      } else if (!matches) {
        mismatches.push(`Divergente do arquivo canônico: ${target}/${rel} != ${rel}`);
      }
    } else if (!matches) {
      fs.mkdirSync(path.dirname(dst), { recursive: true });
      fs.copyFileSync(src, dst);
      copiedCount += 1;
      console.log(`[sync-web] ${rel} -> ${target}/${rel}`);
    }
  }
}

if (isCheck) {
  if (mismatches.length > 0) {
    console.error('[sync-web] Falha na verificação de sincronismo Web (raiz -> web/ e public/):');
    for (const item of mismatches) {
      console.error(`  - ${item}`);
    }
    console.error('Execute `node scripts/sync-web.mjs` para resincronizar.');
    process.exit(1);
  }
  console.log(`[sync-web] OK: ${canonicalFiles.length} arquivos sincronizados entre raiz, web/ e public/.`);
} else {
  if (mismatches.length > 0) {
    for (const item of mismatches) {
      console.error(`[sync-web] Erro: ${item}`);
    }
    process.exit(1);
  }
  console.log(`[sync-web] Concluído (${copiedCount} arquivos atualizados em web/ e public/).`);
}
