import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { verifyReleaseManifests, verifyRemoteAssets } from '../../scripts/verify-release-manifests.mjs';

function setupTempReleaseFixture({ version = '2.8.0', versionCode = 280, gradleCode = 280, apkContent = 'signed-apk-bytes', manifestApkSha = null } = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'mk21-release-test-'));
  fs.mkdirSync(path.join(dir, 'app'), { recursive: true });
  fs.mkdirSync(path.join(dir, 'api'), { recursive: true });
  fs.mkdirSync(path.join(dir, 'web/api'), { recursive: true });
  fs.mkdirSync(path.join(dir, 'public/api'), { recursive: true });
  fs.mkdirSync(path.join(dir, 'smart-tv'), { recursive: true });

  const apkPath = path.join(dir, 'mk21.apk');
  fs.writeFileSync(apkPath, apkContent);
  const realSha = crypto.createHash('sha256').update(apkContent).digest('hex');
  const apkSha = manifestApkSha || realSha;

  const ipkPath = path.join(dir, `smart-tv/mk21play_${version}_all.ipk`);
  fs.writeFileSync(ipkPath, 'ipk-bytes');
  const ipkSha = crypto.createHash('sha256').update('ipk-bytes').digest('hex');

  fs.writeFileSync(
    path.join(dir, 'app/build.gradle.kts'),
    `android {\n  defaultConfig {\n    versionCode = ${gradleCode}\n    versionName = "${version}"\n  }\n}\n`
  );

  const manifest = {
    version,
    versionName: version,
    versionCode,
    apkUrl: `https://github.com/2fbg/FBGs-Streaming/releases/download/v${version}/mk21.apk`,
    downloadUrl: `https://github.com/2fbg/FBGs-Streaming/releases/download/v${version}/mk21.apk`,
    ipkUrl: `https://github.com/2fbg/FBGs-Streaming/releases/download/v${version}/mk21play_${version}_all.ipk`,
    apkSha256: apkSha,
    sha256: apkSha,
    sizeBytes: Buffer.byteLength(apkContent),
    ipkSha256: ipkSha,
    ipkSizeBytes: Buffer.byteLength('ipk-bytes'),
  };

  for (const rel of [
    'version.json',
    'api/version.json',
    'web/version.json',
    'web/api/version.json',
    'public/version.json',
    'public/api/version.json',
    'smart-tv/version.json',
  ]) {
    fs.writeFileSync(path.join(dir, rel), JSON.stringify(manifest, null, 2));
  }

  return { dir, apkPath, ipkPath };
}

test('verifyReleaseManifests passes when Gradle versionCode, release URL, and APK/IPK hashes match', () => {
  const { dir, apkPath, ipkPath } = setupTempReleaseFixture();
  try {
    const res = verifyReleaseManifests({
      rootDir: dir,
      apkPath,
      ipkPath,
      requireApkHashMatch: true,
      requireReleaseUrl: true,
    });
    assert.equal(res.ok, true, res.errors.join('; '));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('verifyReleaseManifests fails when apkSha256 does not match the release APK binary', () => {
  const { dir, apkPath, ipkPath } = setupTempReleaseFixture({
    manifestApkSha: 'f'.repeat(64),
  });
  try {
    const res = verifyReleaseManifests({
      rootDir: dir,
      apkPath,
      ipkPath,
      requireApkHashMatch: true,
      requireReleaseUrl: true,
    });
    assert.equal(res.ok, false);
    assert.ok(res.errors.some((e) => e.includes('apkSha256')));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('verifyReleaseManifests fails when versionCode diverges from app/build.gradle.kts', () => {
  const { dir, apkPath } = setupTempReleaseFixture({
    versionCode: 280,
    gradleCode: 279,
  });
  try {
    const res = verifyReleaseManifests({
      rootDir: dir,
      apkPath,
      requireApkHashMatch: true,
    });
    assert.equal(res.ok, false);
    assert.ok(res.errors.some((e) => e.includes('versionCode')));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('verifyRemoteAssets reports exact git tag command when release URL returns HTTP 404', async () => {
  const { dir } = setupTempReleaseFixture();
  try {
    const res = await verifyRemoteAssets({
      rootDir: dir,
      fetchImpl: async () => new Response('Not Found', { status: 404 }),
    });
    assert.equal(res.ok, false);
    assert.ok(res.errors.some((e) => e.includes('HTTP 404') && e.includes('git tag v2.8.0 && git push origin v2.8.0')));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('verifyRemoteAssets falls back from HEAD 405 to Range GET, catches SHA-256 mismatch, and passes on 200 match', async () => {
  const { dir } = setupTempReleaseFixture();
  try {
    const apkBytes = Buffer.from('signed-apk-bytes');
    const ipkBytes = Buffer.from('ipk-bytes');

    const badHashRes = await verifyRemoteAssets({
      rootDir: dir,
      fetchImpl: async (url, init) => {
        if (init.method === 'HEAD') return new Response(null, { status: 405 });
        if (init.headers?.Range === 'bytes=0-0') return new Response('s', { status: 206 });
        if (url.endsWith('.ipk')) return new Response(ipkBytes, { status: 200 });
        return new Response(Buffer.from('wrong-apk-bytes!'), { status: 200 });
      },
    });
    assert.equal(badHashRes.ok, false);
    assert.ok(badHashRes.errors.some((e) => e.includes('apkUrl') && e.includes('SHA-256 divergente')));

    const okRes = await verifyRemoteAssets({
      rootDir: dir,
      fetchImpl: async (url, init) => {
        if (init.method === 'HEAD') return new Response(null, { status: 200 });
        if (url.endsWith('.ipk')) return new Response(ipkBytes, { status: 200 });
        return new Response(apkBytes, { status: 200 });
      },
    });
    assert.equal(okRes.ok, true, okRes.errors.join('; '));
    assert.equal(okRes.checked.length, 2);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
