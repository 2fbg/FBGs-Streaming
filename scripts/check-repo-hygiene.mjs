#!/usr/bin/env node
/**
 * Guarda de higiene do repositório (roda no CI):
 *  - nada de capturas/logs do AI Studio versionados (public/assets/aistudio/);
 *  - nada de keystores, .env, secrets.properties ou google-services.json real no git;
 *  - nenhum arquivo rastreado acima de 8 MB;
 *  - no máximo 1 IPK rastreado (o da versão atual; versões antigas ficam nas GitHub Releases).
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const DEFAULT_ROOT = path.resolve(path.dirname(__filename), '..');

export const MAX_FILE_BYTES = 8 * 1024 * 1024;
export const FORBIDDEN_PREFIXES = ['public/assets/aistudio/'];
export const FORBIDDEN_PATTERNS = [
  /\.(jks|keystore|p12|pfx)$/i,
  /(^|\/)secrets\.properties$/i,
  /(^|\/)\.env$/i,
  /(^|\/)google-services\.json$/i,
];
const IPK_PATTERN = /^smart-tv\/mk21play_.+_all\.ipk$/;

/** entries: [{ path: 'dir/arquivo', size: bytes }] */
export function checkRepoHygiene(entries) {
  const errors = [];
  let ipkCount = 0;
  for (const { path: p, size } of entries) {
    if (FORBIDDEN_PREFIXES.some((prefix) => p.startsWith(prefix))) {
      errors.push(`${p}: pasta proibida no repositório (${FORBIDDEN_PREFIXES.join(', ')})`);
      continue;
    }
    if (FORBIDDEN_PATTERNS.some((re) => re.test(p))) {
      errors.push(`${p}: arquivo sensível não pode ser versionado`);
      continue;
    }
    if (size > MAX_FILE_BYTES) {
      errors.push(`${p}: ${(size / 1048576).toFixed(1)} MB excede o limite de ${MAX_FILE_BYTES / 1048576} MB`);
    }
    if (IPK_PATTERN.test(p)) ipkCount += 1;
  }
  if (ipkCount > 1) {
    errors.push(`${ipkCount} IPKs rastreados em smart-tv/: mantenha só o da versão atual (os antigos ficam nas Releases)`);
  }
  return errors;
}

/** Lista os arquivos rastreados pelo git (null se não for um repositório git). */
export function listTrackedFiles(rootDir = DEFAULT_ROOT) {
  try {
    const out = execFileSync('git', ['ls-files', '-z'], { cwd: rootDir, maxBuffer: 64 * 1024 * 1024 });
    const entries = [];
    for (const rel of out.toString('utf8').split('\0').filter(Boolean)) {
      try {
        const st = fs.statSync(path.join(rootDir, rel));
        if (st.isFile()) entries.push({ path: rel, size: st.size });
      } catch {
        // arquivo removido na árvore de trabalho: ignora
      }
    }
    return entries;
  } catch {
    return null;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  const entries = listTrackedFiles();
  if (entries === null) {
    console.error('[repo-hygiene] não foi possível listar arquivos (git ls-files falhou).');
    process.exit(2);
  }
  const errors = checkRepoHygiene(entries);
  if (errors.length > 0) {
    console.error('[repo-hygiene] Problemas encontrados:');
    for (const e of errors) console.error(`  - ${e}`);
    process.exit(1);
  }
  console.log(`[repo-hygiene] OK: ${entries.length} arquivos rastreados dentro das regras.`);
}
