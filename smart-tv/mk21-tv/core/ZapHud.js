/**
 * MK21 Play — HUD de zapping (overlay de troca de canal) da Smart TV.
 *
 * Portão da funcionalidade do Saimo TV (LG webOS): ao trocar de canal o app
 * mostra um cartão grande com o logo do canal, o que está "NO AR", o que vem
 * "A SEGUIR" e a contagem regressiva até o próximo programa.
 *
 * Módulo PURO: só monta payloads e decide o auto-hide. A camada de DOM fica
 * no app legado (`src/legacy/app-legacy.js`).
 */

const EpgGuide = require('./EpgGuide.js');

/** Tempo que o HUD fica visível antes de se esconder sozinho. */
const ZAP_AUTO_HIDE_MS = 7000;

/** Tempo mínimo entre dois flaps do HUD, para não piscar durante o zapping. */
const ZAP_MIN_INTERVAL_MS = 400;

/**
 * Monta o payload completo do HUD de zapping.
 *
 * @param {object} options
 * @param {object} options.channel      Item do catálogo (name, logo, group).
 * @param {number} [options.channelNumber] Número do canal (LCN), quando conhecido.
 * @param {Array}  [options.programs]   Programas do canal (XMLTV/short EPG).
 * @param {number} [options.nowMs]      Relógio de referência.
 */
function buildZapInfo(options) {
  const opts = options || {};
  const nowMs = Number.isFinite(opts.nowMs) ? opts.nowMs : Date.now();
  const channel = opts.channel || null;

  const summary = EpgGuide.summarizeChannel(channel, opts.programs, nowMs);

  const number = Number.isFinite(opts.channelNumber) && opts.channelNumber > 0
    ? opts.channelNumber
    : '';

  return {
    number,
    hasNumber: number !== '',
    name: summary.name,
    logo: summary.logo,
    group: summary.group,
    currentTitle: summary.currentTitle,
    nextTitle: summary.nextTitle,
    countdownMs: summary.countdownMs,
    countdownText: summary.countdownText,
    progressPct: summary.progressPct,
    isLive: summary.isLive,
    hasEpg: Boolean(summary.currentTitle)
  };
}

/**
 * Decide se o HUD já pode se esconder sozinho.
 * `elapsedMs` é o tempo desde que o HUD apareceu.
 */
function shouldAutoHide(elapsedMs, thresholdMs) {
  const elapsed = Number(elapsedMs);
  if (!Number.isFinite(elapsed) || elapsed < 0) return false;
  const limit = Number.isFinite(thresholdMs) && thresholdMs > 0 ? thresholdMs : ZAP_AUTO_HIDE_MS;
  return elapsed >= limit;
}

/**
 * Normaliza o número do canal digitado pelo usuário (overlay de canais).
 * Devolve null quando não há número válido.
 */
function parseChannelNumber(raw) {
  const digits = String(raw === null || raw === undefined ? '' : raw).replace(/\D+/g, '');
  if (!digits) return null;
  const value = parseInt(digits, 10);
  if (!Number.isFinite(value) || value <= 0) return null;
  return value;
}

/**
 * Monta o mapa canal -> número (LCN) a partir de uma lista de canais.
 * A numeração segue a ordem da lista, começando em 1 — é o mesmo critério
 * usado pelo "digite o número do canal" do MK21.
 */
function buildChannelNumberMap(channels, customOverrides) {
  const map = new Map();
  const overrides = (customOverrides && typeof customOverrides === 'object') ? customOverrides : null;
  if (!Array.isArray(channels)) return map;

  const usedNumbers = new Set();
  // Primeiro aplica LCNs editados pelo usuário
  if (overrides) {
    for (let i = 0; i < channels.length; i++) {
      const ch = channels[i];
      if (!ch) continue;
      const key = channelKey(ch);
      if (!key || map.has(key)) continue;
      const customNum = parseChannelNumber(overrides[key]);
      if (customNum !== null && !usedNumbers.has(customNum)) {
        map.set(key, customNum);
        usedNumbers.add(customNum);
      }
    }
  }

  let autoNum = 1;
  for (let i = 0; i < channels.length; i++) {
    const ch = channels[i];
    if (!ch) continue;
    const key = channelKey(ch);
    if (!key || map.has(key)) continue;
    while (usedNumbers.has(autoNum)) autoNum += 1;
    map.set(key, autoNum);
    usedNumbers.add(autoNum);
    autoNum += 1;
  }
  return map;
}

/** Chave estável de um canal (tvg-id quando existe, senão o nome normalizado). */
function channelKey(channel) {
  if (!channel) return '';
  const tvgId = String(channel.tvgId || channel['tvg-id'] || '').trim().toLowerCase();
  if (tvgId) return 'id:' + tvgId;
  const name = String(channel.name || '').trim().toLowerCase();
  return name ? 'name:' + name : '';
}

/** Resolve o número do canal a partir do mapa, com fallback para o índice+1. */
function resolveChannelNumber(map, channel, fallbackIndex) {
  const key = channelKey(channel);
  if (key && map && typeof map.get === 'function') {
    const found = map.get(key);
    if (Number.isFinite(found)) return found;
  }
  if (Number.isFinite(fallbackIndex) && fallbackIndex >= 0) return fallbackIndex + 1;
  return null;
}

module.exports = {
  ZAP_AUTO_HIDE_MS,
  ZAP_MIN_INTERVAL_MS,
  buildZapInfo,
  shouldAutoHide,
  parseChannelNumber,
  buildChannelNumberMap,
  channelKey,
  resolveChannelNumber
};
