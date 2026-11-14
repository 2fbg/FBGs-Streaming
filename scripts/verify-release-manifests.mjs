#!/usr/bin/env node
/**
 * Valida a consistência entre:
 *  - app/build.gradle.kts (versionCode e versionName)
 *  - version.json e espelhos (api/, web/, public/, smart-tv/)
 *  - SHA-256 e tamanho do binário APK (e opcionalmente IPK)
 *  - URL da release do APK quando --require-release-url for informado
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DEFAULT_ROOT = path.resolve(__dirname, '..');

export function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

export function parseGradleVersions(gradlePath) {
  const text = fs.readFileSync(gradlePath, 'utf8');
  const codeMatch = text.match(/^\s*versionCode\s*=\s*(\d+)/m);
  const nameMatch = text.match(/^\s*versionName\s*=\s*"([^"]+)"/m);
  if (!codeMatch || !nameMatch) {
    throw new Error(`Não foi possível ler versionCode/versionName em ${gradlePath}`);
  }
  return {
    versionCode: Number(codeMatch[1]),
    versionName: nameMatch[1],
  };
}

export function verifyReleaseManifests({
  rootDir = DEFAULT_ROOT,
  apkPath = path.join(rootDir, 'mk21.apk'),
  ipkPath = null,
  requireApkHashMatch = true,
  requireReleaseUrl = false,
} = {}) {
  const errors = [];
  const versionJsonPath = path.join(rootDir, 'version.json');
  const gradlePath = path.join(rootDir, 'app/build.gradle.kts');

  const rootManifest = JSON.parse(fs.readFileSync(versionJsonPath, 'utf8'));
  const gradle = parseGradleVersions(gradlePath);

  const targetVersion = String(rootManifest.version || '');
  const targetCode = Number(rootManifest.versionCode ?? -1);

  if (gradle.versionName !== targetVersion) {
    errors.push(`version.json version (${targetVersion}) != build.gradle.kts versionName (${gradle.versionName})`);
  }
  if (gradle.versionCode !== targetCode) {
    errors.push(`version.json versionCode (${targetCode}) != build.gradle.kts versionCode (${gradle.versionCode})`);
  }

  const mirrorPaths = [
    'version.json',
    'api/version.json',
    'web/version.json',
    'web/api/version.json',
    'public/version.json',
    'public/api/version.json',
    'smart-tv/version.json',
  ];

  for (const rel of mirrorPaths) {
    const abs = path.join(rootDir, rel);
    if (!fs.existsSync(abs)) {
      errors.push(`Manifesto ausente: ${rel}`);
      continue;
    }
    const data = JSON.parse(fs.readFileSync(abs, 'utf8'));
    if (String(data.version || '') !== targetVersion || Number(data.versionCode ?? -1) !== targetCode) {
      errors.push(`${rel}: versão ${data.version} (${data.versionCode}) diverge de ${targetVersion} (${targetCode})`);
    }
    if (String(data.apkSha256 || '').toLowerCase() !== String(rootManifest.apkSha256 || '').toLowerCase()) {
      errors.push(`${rel}: apkSha256 diverge de version.json`);
    }
  }

  const expectedReleaseUrl = `https://github.com/2fbg/FBGs-Streaming/releases/download/v${targetVersion}/mk21.apk`;
  if (requireReleaseUrl) {
    if (rootManifest.apkUrl !== expectedReleaseUrl || rootManifest.downloadUrl !== expectedReleaseUrl) {
      errors.push(`apkUrl/downloadUrl deve apontar para ${expectedReleaseUrl} (atual: ${rootManifest.apkUrl})`);
    }
  }

  const declaredApkSha = String(rootManifest.apkSha256 || '').trim().toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(declaredApkSha)) {
    errors.push(`version.json apkSha256 inválido: "${declaredApkSha}"`);
  }

  if (requireApkHashMatch) {
    if (!apkPath || !fs.existsSync(apkPath)) {
      errors.push(`Arquivo APK não encontrado: ${apkPath}`);
    } else {
      const actualSha = sha256File(apkPath);
      const actualSize = fs.statSync(apkPath).size;
      if (actualSha !== declaredApkSha) {
        errors.push(`apkSha256 do manifesto (${declaredApkSha}) não bate com ${apkPath} (${actualSha})`);
      }
      if (Number(rootManifest.sizeBytes) !== actualSize) {
        errors.push(`sizeBytes do manifesto (${rootManifest.sizeBytes}) não bate com ${apkPath} (${actualSize})`);
      }
    }
  }

  if (ipkPath) {
    if (!fs.existsSync(ipkPath)) {
      errors.push(`Arquivo IPK não encontrado: ${ipkPath}`);
    } else {
      const actualIpkSha = sha256File(ipkPath);
      const actualIpkSize = fs.statSync(ipkPath).size;
      if (String(rootManifest.ipkSha256 || '').toLowerCase() !== actualIpkSha) {
        errors.push(`ipkSha256 do manifesto (${rootManifest.ipkSha256}) não bate com ${ipkPath} (${actualIpkSha})`);
      }
      if (Number(rootManifest.ipkSizeBytes) !== actualIpkSize) {
        errors.push(`ipkSizeBytes do manifesto (${rootManifest.ipkSizeBytes}) não bate com ${ipkPath} (${actualIpkSize})`);
      }
    }
  }

  return { ok: errors.length === 0, errors, version: targetVersion, versionCode: targetCode };
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  const args = process.argv.slice(2);
  const readFlag = (name) => {
    const idx = args.indexOf(name);
    return idx !== -1 && idx + 1 < args.length ? args[idx + 1] : null;
  };
  const apkArg = readFlag('--apk');
  const ipkArg = readFlag('--ipk');
  const rootArg = readFlag('--root') || DEFAULT_ROOT;
  const requireReleaseUrl = args.includes('--require-release-url');
  const skipApkCheck = args.includes('--skip-apk-hash');

  const result = verifyReleaseManifests({
    rootDir: path.resolve(rootArg),
    apkPath: apkArg ? path.resolve(apkArg) : path.join(path.resolve(rootArg), 'mk21.apk'),
    ipkPath: ipkArg ? path.resolve(ipkArg) : null,
    requireApkHashMatch: !skipApkCheck,
    requireReleaseUrl,
  });

  if (!result.ok) {
    console.error('[verify-release] Falha na verificação dos manifestos:');
    for (const err of result.errors) console.error(`  - ${err}`);
    process.exit(1);
  }
  console.log(`[verify-release] OK: v${result.version} (${result.versionCode}) validado.`);
}
