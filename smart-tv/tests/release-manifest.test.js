import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { verifyReleaseManifests } from '../../scripts/verify-release-manifests.mjs';

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
