/**
 * Shim de hls.js para o bundle da TV.
 *
 * O app mk21-tv já carrega `libs/hls.min.js` (com fallback de CDN) antes do
 * `app.js`. Em vez de embutir ~400 KB de hls.js no bundle (a OTA baixa o arquivo
 * inteiro), o esbuild resolve `hls.js` para este módulo, que apenas reexporta o
 * global já presente.
 */
const H = typeof window !== 'undefined' ? window.Hls : undefined;

export default H;
export const Events = (H && H.Events) || {};
export const ErrorTypes = (H && H.ErrorTypes) || {};
