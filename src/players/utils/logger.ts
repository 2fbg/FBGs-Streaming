/**
 * Logger com gating por ambiente. Em produção os logs de debug são no-ops.
 *
 * Configure em runtime com: localStorage.setItem('FBG2_DEBUG', '1')
 * (a flag é lida a cada chamada — ligar/desligar na TV não exige reload).
 */

const TAG = '[FBG2-Player]';

const devBuild = (): boolean => {
  const proc = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process;
  return proc?.env?.NODE_ENV === 'development';
};

const debugEnabled = (): boolean => {
  if (devBuild()) return true;
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem('FBG2_DEBUG') === '1';
  } catch {
    return false;
  }
};

export const log = {
  debug(...args: unknown[]): void {
    if (debugEnabled()) console.debug(TAG, ...args);
  },
  info(...args: unknown[]): void {
    if (debugEnabled()) console.info(TAG, ...args);
  },
  warn(...args: unknown[]): void {
    console.warn(TAG, ...args);
  },
  error(...args: unknown[]): void {
    console.error(TAG, ...args);
  },
};

export const isDebugEnabled = debugEnabled;
