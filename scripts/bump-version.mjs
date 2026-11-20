#!/usr/bin/env node
/**
 * Incrementa e sincroniza a versão do MK21 Play em todas as plataformas
 * (Android, Web, Smart TV webOS/Tizen, manifestos de release e espelhos web/public).
 *
 * Uso:
 *   node scripts/bump-version.mjs 2.9.2
 *   node scripts/bump-version.mjs patch --notes "Descrição da release"
 *   node scripts/bump-version.mjs minor
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const REPO_SLUG = '2fbg/FBGs-Streaming';

function parseSemver(ver) {
  const m = String(ver || '').trim().match(/^(\d+)\.(\d+)\.(\d+)$/);
  if (!m) return null;
  return { major: Number(m[1]), minor: Number(m[2]), patch: Number(m[3]) };
}

function readJson(rel) {
  return JSON.parse(fs.readFileSync(path.join(ROOT_DIR, rel), 'utf8'));
}

function writeJson(rel, data) {
  fs.writeFileSync(path.join(ROOT_DIR, rel), `${JSON.stringify(data, null, 2)}\n`, 'utf8');
}

function resolveTargetVersion(arg, currentVersion) {
  const cur = parseSemver(currentVersion);
  if (!cur) throw new Error(`Versão atual inválida em version.json: "${currentVersion}"`);
  if (!arg || arg === 'patch') {
    return `${cur.major}.${cur.minor}.${cur.patch + 1}`;
  }
  if (arg === 'minor') {
    return `${cur.major}.${cur.minor + 1}.0`;
  }
  if (arg === 'major') {
    return `${cur.major + 1}.0.0`;
  }
  const clean = arg.replace(/^v/i, '');
  if (!parseSemver(clean)) {
    throw new Error(`Versão alvo inválida: "${arg}" (use X.Y.Z, patch, minor ou major).`);
  }
  return clean;
}

const args = process.argv.slice(2);
const notesIdx = args.indexOf('--notes');
const customNotes = notesIdx !== -1 && notesIdx + 1 < args.length ? args[notesIdx + 1] : null;
const positional = args.filter((a, i) => a !== '--notes' && i !== notesIdx + 1);

const rootManifest = readJson('version.json');
const currentVersion = String(rootManifest.version);
const currentCode = Number(rootManifest.versionCode);
const nextVersion = resolveTargetVersion(positional[0], currentVersion);
const nextCode = nextVersion === currentVersion ? currentCode : currentCode + 1;
const today = new Date().toISOString().slice(0, 10);
const releaseApkUrl = `https://github.com/${REPO_SLUG}/releases/download/v${nextVersion}/mk21.apk`;
const releaseIpkUrl = `https://github.com/${REPO_SLUG}/releases/download/v${nextVersion}/mk21play_${nextVersion}_all.ipk`;

const releaseNotes = customNotes || `MK21 Play v${nextVersion}: atualização multiplataforma (Code ${nextCode}).`;

// 1. version.json e espelhos canônicos
const versionManifests = [
  'version.json',
  'api/version.json',
  'smart-tv/version.json',
  'smart-tv/mk21-tv/version.json',
];
for (const rel of versionManifests) {
  const data = readJson(rel);
  data.version = nextVersion;
  data.versionName = nextVersion;
  data.build = nextCode;
  data.versionCode = nextCode;
  data.tagName = `v${nextVersion}`;
  data.title = `MK21 Play v${nextVersion}`;
  if (data.platforms && typeof data.platforms === 'object') {
    data.platforms.web = nextVersion;
    data.platforms.android = nextVersion;
    data.platforms.webos = nextVersion;
    data.platforms.tizen = nextVersion;
  }
  data.releaseDate = `${today}T00:00:00Z`;
  data.updatedAt = today;
  data.apkUrl = releaseApkUrl;
  data.downloadUrl = releaseApkUrl;
  data.ipkUrl = releaseIpkUrl;
  data.releaseNotes = releaseNotes;
  data.body = `${releaseNotes} (Code ${nextCode}).`;
  data.changelog = releaseNotes;
  writeJson(rel, data);
}

// 2. manifest.json, package.json, web/package.json e appinfo.json (webOS)
for (const rel of ['manifest.json', 'package.json', 'web/package.json', 'smart-tv/mk21-tv/appinfo.json', 'smart-tv/webos/appinfo.json']) {
  const data = readJson(rel);
  data.version = nextVersion;
  writeJson(rel, data);
}

// 2b. Defaults de versão/build no logger estruturado Web e Smart TV
for (const rel of ['js/observability.js', 'smart-tv/mk21-tv/observability.js']) {
  const abs = path.join(ROOT_DIR, rel);
  if (fs.existsSync(abs)) {
    let js = fs.readFileSync(abs, 'utf8');
    js = js.replace(/var version = String\(opts\.version \|\| '\d+\.\d+\.\d+'\);/, `var version = String(opts.version || '${nextVersion}');`);
    js = js.replace(/var build = Number\(opts\.build \|\| \d+\);/, `var build = Number(opts.build || ${nextCode});`);
    fs.writeFileSync(abs, js, 'utf8');
  }
}

// 3. apps.json e smart-tv/apps.json
for (const rel of ['apps.json', 'smart-tv/apps.json']) {
  const apps = readJson(rel);
  if (Array.isArray(apps.packages) && apps.packages[0]) {
    apps.packages[0].version = nextVersion;
    apps.packages[0].sourceUrl = releaseIpkUrl;
    if (apps.packages[0].manifest) {
      apps.packages[0].manifest.version = nextVersion;
      apps.packages[0].manifest.ipkUrl = releaseIpkUrl;
    }
  }
  writeJson(rel, apps);
}

// 4. app/build.gradle.kts
{
  const gradlePath = path.join(ROOT_DIR, 'app/build.gradle.kts');
  let gradle = fs.readFileSync(gradlePath, 'utf8');
  gradle = gradle.replace(/^(\s*versionCode\s*=\s*)\d+/m, `$1${nextCode}`);
  gradle = gradle.replace(/^(\s*versionName\s*=\s*")[^"]+(")/m, `$1${nextVersion}$2`);
  fs.writeFileSync(gradlePath, gradle, 'utf8');
}

// 5. smart-tv/tizen/config.xml
{
  const tizenPath = path.join(ROOT_DIR, 'smart-tv/tizen/config.xml');
  let xml = fs.readFileSync(tizenPath, 'utf8');
  xml = xml.replace(/(<widget\b[^>]*\bversion=")[^"]+(")/, `$1${nextVersion}$2`);
  fs.writeFileSync(tizenPath, xml, 'utf8');
}

// 6. index.html (labels e link do IPK)
{
  const indexPath = path.join(ROOT_DIR, 'index.html');
  let html = fs.readFileSync(indexPath, 'utf8');
  html = html.replace(/v\d+\.\d+\.\d+ by FBG2/g, `v${nextVersion} by FBG2`);
  html = html.replace(/APK Nativo v\d+\.\d+\.\d+/g, `APK Nativo v${nextVersion}`);
  html = html.replace(/Versão v\d+\.\d+\.\d+ \(Code \d+\)/g, `Versão v${nextVersion} (Code ${nextCode})`);
  html = html.replace(/(<a[^>]*href=")\/[^"]*\.ipk(")/, `$1/smart-tv/mk21play_${nextVersion}_all.ipk$2`);
  html = html.replace(/(download=")[^"]*\.ipk(")/, `$1mk21play_${nextVersion}_all.ipk$2`);
  fs.writeFileSync(indexPath, html, 'utf8');
}

// 7. smart-tv/mk21-tv/index.html e src/legacy/app-legacy.js
{
  const tvHtmlPath = path.join(ROOT_DIR, 'smart-tv/mk21-tv/index.html');
  let tvHtml = fs.readFileSync(tvHtmlPath, 'utf8');
  tvHtml = tvHtml.replace(/\?v=\d+\.\d+\.\d+/g, `?v=${nextVersion}`);
  tvHtml = tvHtml.replace(/MK21 PLAY OTT • v\d+\.\d+\.\d+/g, `MK21 PLAY OTT • v${nextVersion}`);
  tvHtml = tvHtml.replace(/anterior a \d+\.\d+\.\d+/g, `anterior a ${nextVersion}`);
  tvHtml = tvHtml.replace(/otaVer < '\d+\.\d+\.\d+'/g, `otaVer < '${nextVersion}'`);
  fs.writeFileSync(tvHtmlPath, tvHtml, 'utf8');

  const legacyPath = path.join(ROOT_DIR, 'smart-tv/mk21-tv/src/legacy/app-legacy.js');
  let legacy = fs.readFileSync(legacyPath, 'utf8');
  legacy = legacy.replace(/MK21 PLAY v\d+\.\d+\.\d+/g, `MK21 PLAY v${nextVersion}`);
  legacy = legacy.replace(/const BASE_PACKAGE_VERSION = '\d+\.\d+\.\d+';/, `const BASE_PACKAGE_VERSION = '${nextVersion}';`);
  fs.writeFileSync(legacyPath, legacy, 'utf8');
}

// 8. Sincroniza espelhos web/ e public/
execFileSync(process.execPath, [path.join(ROOT_DIR, 'scripts/sync-web.mjs')], { stdio: 'inherit' });

console.log(`[bump-version] OK: ${currentVersion} (${currentCode}) -> ${nextVersion} (${nextCode})`);
