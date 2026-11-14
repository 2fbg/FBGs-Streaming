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

function isReleaseDownloadUrl(rawUrl) {
  return /github\.com\/[^/]+\/[^/]+\/releases\/download\/v?[^/]+\//i.test(rawUrl);
}

async function fetchWithTimeout(url, options, timeoutMs, fetchImpl) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(new Error(`Timeout após ${timeoutMs}ms`)), timeoutMs);
  try {
    return await fetchImpl(url, {
      ...options,
      redirect: 'follow',
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timer);
  }
}

export async function verifyRemoteAsset({
  label,
  url,
  expectedSha256,
  expectedSizeBytes = 0,
  targetVersion,
  timeoutMs = 20000,
  fetchImpl = globalThis.fetch,
}) {
  const errors = [];
  if (!url || !/^https:\/\//i.test(url)) {
    return {
      ok: false,
      errors: [`${label} inválido ou não HTTPS para validação remota: "${url || ''}"`],
      status: 0,
      sha256: null,
      sizeBytes: 0,
    };
  }

  let probeStatus = 0;
  try {
    let headRes = await fetchWithTimeout(
      url,
      { method: 'HEAD', headers: { 'User-Agent': 'MK21-Release-Verifier' } },
      Math.min(timeoutMs, 10000),
      fetchImpl
    );
    if (headRes.status === 405 || headRes.status === 501) {
      headRes = await fetchWithTimeout(
        url,
        {
          method: 'GET',
          headers: {
            'User-Agent': 'MK21-Release-Verifier',
            Range: 'bytes=0-0',
          },
        },
        Math.min(timeoutMs, 10000),
        fetchImpl
      );
      if (headRes.body && typeof headRes.body.cancel === 'function') {
        await headRes.body.cancel().catch(() => {});
      }
    }
    probeStatus = headRes.status;
    if (probeStatus !== 200 && probeStatus !== 206) {
      if (probeStatus === 404 && isReleaseDownloadUrl(url)) {
        errors.push(
          `${label} remoto retornou HTTP 404 (${url}). A release v${targetVersion} ainda não foi publicada — comando necessário: git tag v${targetVersion} && git push origin v${targetVersion}`
        );
      } else {
        errors.push(`${label} remoto retornou HTTP ${probeStatus} (${url}).`);
      }
      return { ok: false, errors, status: probeStatus, sha256: null, sizeBytes: 0 };
    }
  } catch (err) {
    errors.push(`${label} remoto inacessível (${url}): ${err.message}`);
    return { ok: false, errors, status: 0, sha256: null, sizeBytes: 0 };
  }

  try {
    const res = await fetchWithTimeout(
      url,
      { method: 'GET', headers: { 'User-Agent': 'MK21-Release-Verifier' } },
      timeoutMs,
      fetchImpl
    );
    if (res.status !== 200 && res.status !== 206) {
      if (res.status === 404 && isReleaseDownloadUrl(url)) {
        errors.push(
          `${label} remoto retornou HTTP 404 no download (${url}). A release v${targetVersion} ainda não existe — execute: git tag v${targetVersion} && git push origin v${targetVersion}`
        );
      } else {
        errors.push(`${label} remoto retornou HTTP ${res.status} no download (${url}).`);
      }
      return { ok: false, errors, status: res.status, sha256: null, sizeBytes: 0 };
    }

    const buffer = Buffer.from(await res.arrayBuffer());
    const actualSize = buffer.byteLength;
    const actualSha = crypto.createHash('sha256').update(buffer).digest('hex');

    if (expectedSizeBytes > 0 && actualSize !== Number(expectedSizeBytes)) {
      errors.push(
        `${label} (${url}): tamanho remoto divergente — manifesto=${expectedSizeBytes} bytes, remoto=${actualSize} bytes.`
      );
    }
    if (expectedSha256 && actualSha !== String(expectedSha256).trim().toLowerCase()) {
      errors.push(
        `${label} (${url}): SHA-256 divergente — manifesto=${expectedSha256}, remoto=${actualSha}.`
      );
    }

    return { ok: errors.length === 0, errors, status: res.status, sha256: actualSha, sizeBytes: actualSize };
  } catch (err) {
    errors.push(`${label} falhou ao baixar (${url}): ${err.message}`);
    return { ok: false, errors, status: probeStatus, sha256: null, sizeBytes: 0 };
  }
}

export async function verifyRemoteAssets({
  rootDir = DEFAULT_ROOT,
  manifest = null,
  timeoutMs = 20000,
  fetchImpl = globalThis.fetch,
} = {}) {
  const rootManifest =
    manifest || JSON.parse(fs.readFileSync(path.join(rootDir, 'version.json'), 'utf8'));
  const targetVersion = String(rootManifest.version || '');
  const errors = [];
  const checked = [];

  const apkUrl = String(rootManifest.apkUrl || '').trim();
  const downloadUrl = String(rootManifest.downloadUrl || '').trim();
  const ipkUrl = String(rootManifest.ipkUrl || '').trim();

  const targets = [
    {
      label: 'apkUrl',
      url: apkUrl,
      expectedSha256: String(rootManifest.apkSha256 || '').trim().toLowerCase(),
      expectedSizeBytes: Number(rootManifest.sizeBytes || 0),
    },
  ];
  if (downloadUrl && downloadUrl !== apkUrl) {
    targets.push({
      label: 'downloadUrl',
      url: downloadUrl,
      expectedSha256: String(rootManifest.apkSha256 || '').trim().toLowerCase(),
      expectedSizeBytes: Number(rootManifest.sizeBytes || 0),
    });
  }
  if (ipkUrl) {
    targets.push({
      label: 'ipkUrl',
      url: ipkUrl,
      expectedSha256: String(rootManifest.ipkSha256 || '').trim().toLowerCase(),
      expectedSizeBytes: Number(rootManifest.ipkSizeBytes || 0),
    });
  }

  for (const target of targets) {
    const res = await verifyRemoteAsset({
      ...target,
      targetVersion,
      timeoutMs,
      fetchImpl,
    });
    checked.push({ label: target.label, url: target.url, status: res.status, sha256: res.sha256, sizeBytes: res.sizeBytes });
    errors.push(...res.errors);
  }

  return { ok: errors.length === 0, errors, checked };
}

async function main() {
  const args = process.argv.slice(2);
  const readFlag = (name) => {
    const idx = args.indexOf(name);
    return idx !== -1 && idx + 1 < args.length ? args[idx + 1] : null;
  };
  const apkArg = readFlag('--apk');
  const ipkArg = readFlag('--ipk');
  const rootArg = readFlag('--root') || DEFAULT_ROOT;
  const timeoutArg = Number(readFlag('--timeout-ms') || 20000);
  const requireReleaseUrl = args.includes('--require-release-url');
  const skipApkCheck = args.includes('--skip-apk-hash');
  const remoteMode = args.includes('--remote');
  const resolvedRoot = path.resolve(rootArg);

  const result = verifyReleaseManifests({
    rootDir: resolvedRoot,
    apkPath: apkArg ? path.resolve(apkArg) : path.join(resolvedRoot, 'mk21.apk'),
    ipkPath: ipkArg ? path.resolve(ipkArg) : null,
    requireApkHashMatch: !skipApkCheck,
    requireReleaseUrl,
  });

  const allErrors = [...result.errors];
  let checkedRemote = [];

  if (remoteMode) {
    const remoteRes = await verifyRemoteAssets({
      rootDir: resolvedRoot,
      timeoutMs: Number.isFinite(timeoutArg) && timeoutArg > 0 ? timeoutArg : 20000,
    });
    allErrors.push(...remoteRes.errors);
    checkedRemote = remoteRes.checked;
  }

  if (allErrors.length > 0) {
    console.error('[verify-release] Falha na verificação dos manifestos:');
    for (const err of allErrors) console.error(`  - ${err}`);
    process.exit(1);
  }

  console.log(`[verify-release] OK: v${result.version} (${result.versionCode}) validado.`);
  for (const item of checkedRemote) {
    console.log(
      `[verify-release] REMOTE OK: ${item.label} -> HTTP ${item.status} | sha256=${item.sha256} | ${item.sizeBytes} bytes (${item.url})`
    );
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  await main();
}
