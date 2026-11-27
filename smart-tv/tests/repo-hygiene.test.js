import test from 'node:test';
import assert from 'node:assert/strict';
import { checkRepoHygiene, listTrackedFiles, MAX_FILE_BYTES } from '../../scripts/check-repo-hygiene.mjs';

const f = (path, size = 1000) => ({ path, size });

test('checkRepoHygiene accepts a clean file list with a single IPK', () => {
  const errors = checkRepoHygiene([f('index.html'), f('mk21.apk', 4_200_000), f('smart-tv/mk21play_3.0.4_all.ipk', 354_000)]);
  assert.deepEqual(errors, []);
});

test('checkRepoHygiene rejects AI Studio assets, keystores, env files and google-services.json', () => {
  const errors = checkRepoHygiene([
    f('public/assets/aistudio/print.jpg'),
    f('keys/upload.jks'),
    f('.env'),
    f('app/secrets.properties'),
    f('app/google-services.json'),
  ]);
  assert.equal(errors.length, 5);
});

test('checkRepoHygiene allows .env.example and docs', () => {
  assert.deepEqual(checkRepoHygiene([f('.env.example'), f('docs/RELEASE_SECRETS.md')]), []);
});

test('checkRepoHygiene rejects oversized files', () => {
  const errors = checkRepoHygiene([f('video.mp4', MAX_FILE_BYTES + 1)]);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /excede o limite/);
});

test('checkRepoHygiene rejects more than one tracked IPK', () => {
  const errors = checkRepoHygiene([f('smart-tv/mk21play_3.0.3_all.ipk'), f('smart-tv/mk21play_3.0.4_all.ipk')]);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /2 IPKs/);
});

test('the real repository passes the hygiene rules', (t) => {
  const entries = listTrackedFiles();
  if (entries === null) {
    t.skip('git indisponível neste ambiente');
    return;
  }
  assert.deepEqual(checkRepoHygiene(entries), []);
});
