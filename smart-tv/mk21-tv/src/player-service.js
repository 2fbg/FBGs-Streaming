/**
 * PlayerService — adaptador entre o app legado do mk21-tv e a PlayerFactory (Fase 1).
 *
 * Responsabilidades:
 *  - decidir (flag tripla) se a reprodução vai pela factory ou pelo caminho legado;
 *  - criar/manter a instância do player e traduzir eventos para os hooks da UI legada;
 *  - guardar as faixas de áudio normalizadas para o menu de áudio;
 *  - falar MILISSEGUNDOS com a factory (o legado usa segundos no <video>).
 *
 * A UI legada não é importada aqui: o app registra `setHooks({...})` no boot, o que
 * mantém este módulo independente e evita import circular no bundle.
 */
import { PlayerFactory } from '../../../src/players/index.ts';

/** localStorage: força o caminho legado (rollback local imediato). */
export const FLAG_LEGACY_KEY = 'FBG2_PLAYER_LEGACY';
/** localStorage: cache do campo remoto `playerEngine` do version.json. */
export const ENGINE_CACHE_KEY = 'FBG2_PLAYER_ENGINE';
/** Default da especificação (Fase 2, regra dura 2). */
export const USE_FACTORY_PLAYER = true;

const ENGINE_MANIFEST_URL =
  'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json';
const ENGINE_MANIFEST_TIMEOUT_MS = 4000;

/**
 * A factory atende HLS, DASH e progressivo. MPEG-TS cru (.ts) continua no
 * pipeline mpegts legado — o AVPlay não abre .ts com a mesma confiabilidade.
 */
const FACTORY_URL_RE = /\.(m3u8|mpd|mp4|m4v|webm|mov)([?#]|$)/i;

/** Erros de zapping: play() anterior abortado de propósito, não é falha. */
const isPlayInterruption = (err) => {
  const msg = (err && (err.message || err.msg)) || '';
  const name = (err && err.name) || '';
  return name === 'AbortError' || /interrupt|abort/i.test(msg);
};

const readStorage = (key) => {
  try {
    return localStorage.getItem(key);
  } catch (err) {
    return null;
  }
};

export class PlayerService {
  static #instance = null;

  static get instance() {
    if (!this.#instance) this.#instance = new PlayerService();
    return this.#instance;
  }

  #player = null;
  #videoEl = null;
  #wired = false;
  #active = false;
  #playToken = 0;
  #bufferMs = 4000;
  #audioTracks = [];
  #activeAudioId = null;
  #remoteEngine = null;
  #hooks = {};

  constructor() {
    // Override LOCAL explicito (localStorage) tem prioridade sobre o remoto.
    // O valor remoto do version.json fica so em memoria: assim uma troca de
    // flag do servidor nao apaga o override de quem esta testando na TV.
    this.#remoteEngine = null;
  }

  /** O app legado registra aqui os callbacks de UI. */
  setHooks(hooks) {
    this.#hooks = hooks || {};
    return this;
  }

  #call(name, ...args) {
    const fn = this.#hooks[name];
    if (typeof fn !== 'function') return undefined;
    try {
      return fn(...args);
    } catch (err) {
      console.warn('[PlayerService] hook ' + name + ' falhou:', err);
      return undefined;
    }
  }

  /**
   * Flag tripla (regra dura 2): localStorage > campo remoto do version.json
   * > default da especificação.
   */
  get useFactory() {
    if (readStorage(FLAG_LEGACY_KEY) === '1') return false;
    const local = String(readStorage(ENGINE_CACHE_KEY) || '').toLowerCase();
    if (local === 'legacy') return false;
    if (local === 'factory') return true;
    const remote = String(this.#remoteEngine || '').toLowerCase();
    if (remote === 'legacy') return false;
    if (remote === 'factory') return true;
    return USE_FACTORY_PLAYER;
  }

  /** Motivo legível da decisão atual (vai para a tela de logs). */
  get engineReason() {
    if (readStorage(FLAG_LEGACY_KEY) === '1') return 'legado (FBG2_PLAYER_LEGACY=1)';
    const local = String(readStorage(ENGINE_CACHE_KEY) || '').toLowerCase();
    if (local === 'legacy') return 'legado (FBG2_PLAYER_ENGINE local)';
    if (local === 'factory') return 'factory (FBG2_PLAYER_ENGINE local)';
    const remote = String(this.#remoteEngine || '').toLowerCase();
    if (remote === 'legacy') return 'legado (version.json)';
    if (remote === 'factory') return 'factory (version.json)';
    return 'factory (default)';
  }

  get player() {
    return this.#player;
  }

  get platform() {
    try {
      return PlayerFactory.detectPlatform();
    } catch (err) {
      return 'browser';
    }
  }

  /** A factory é dona da reprodução atual? */
  isActive() {
    return !!(this.#active && this.#player);
  }

  /** Tenta assumir a reprodução. `false` = siga pelo caminho legado. */
  tryPlay(item, url) {
    if (!this.useFactory) return false;
    if (!url || !FACTORY_URL_RE.test(String(url))) return false;

    let player;
    try {
      player = this.#ensurePlayer();
    } catch (err) {
      console.warn('[PlayerService] factory indisponível, seguindo no legado:', err);
      return false;
    }
    if (!player) return false;

    this.#active = true;
    const token = ++this.#playToken;

    const startMs =
      item && item.currentTime > 5 &&
      (item.contentType === 'MOVIE' || item.contentType === 'SERIES')
        ? Math.round(item.currentTime * 1000)
        : 0;

    this.#call('onFactoryStart', { url, platform: player.platform, reason: this.engineReason });

    Promise.resolve(
      player.play(url, {
        bufferSizeMs: this.#bufferMs,
        startPositionMs: startMs,
      }),
    ).catch((err) => {
      if (token !== this.#playToken) return; // zapping: play antigo abortado
      if (isPlayInterruption(err)) return;
      this.#call('onError', { message: (err && err.message) || String(err), fatal: true, raw: err }, true);
    });

    return true;
  }

  pause() {
    if (!this.isActive()) return false;
    this.#player.pause();
    return true;
  }

  resume() {
    if (!this.isActive()) return false;
    this.#player.resume();
    return true;
  }

  /** Alterna play/pause pelo estado real do player. */
  toggle() {
    if (!this.isActive()) return false;
    if (this.#player.getState && this.#player.getState() === 'paused') this.#player.resume();
    else this.#player.pause();
    return true;
  }

  /** Seek em MILISSEGUNDOS (contrato da Seção 5). */
  seekMs(ms) {
    if (!this.isActive()) return false;
    const target = Math.max(0, Math.round(Number(ms) || 0));
    this.#player.seek(target);
    return true;
  }

  stop() {
    this.#playToken += 1;
    this.#active = false;
    if (!this.#player) return;
    try {
      this.#player.stop();
    } catch (err) {
      console.warn('[PlayerService] stop falhou:', err);
    }
  }

  /** Libera a engine nativa (fim do app / troca de tela). */
  release() {
    this.stop();
    if (this.#player) {
      try {
        this.#player.destroy();
      } catch (err) {
        console.warn('[PlayerService] destroy falhou:', err);
      }
    }
    this.#player = null;
    this.#videoEl = null;
    this.#wired = false;
    PlayerFactory.releaseTizen();
  }

  /** Buffer alvo em ms (a factory usa no AVPlay e no hls.js). */
  setBufferMs(ms) {
    const value = Number(ms);
    if (!Number.isFinite(value) || value <= 0) return;
    this.#bufferMs = Math.max(3000, Math.min(30000, Math.round(value)));
  }

  getAudioTracks() {
    return this.#audioTracks;
  }

  getActiveAudioId() {
    return this.#activeAudioId;
  }

  setAudioTrack(trackId) {
    if (!this.#player || !trackId) return false;
    this.#activeAudioId = trackId;
    try {
      this.#player.setAudioTrack(trackId);
      return true;
    } catch (err) {
      console.warn('[PlayerService] setAudioTrack falhou:', err);
      return false;
    }
  }

  /** Relógio do player em segundos (AVPlay não mexe no <video>). */
  getCurrentTimeSec() {
    if (!this.#player) return 0;
    try {
      return (this.#player.getCurrentTime() || 0) / 1000;
    } catch (err) {
      return 0;
    }
  }

  getDurationSec() {
    if (!this.#player) return 0;
    try {
      return (this.#player.getDuration() || 0) / 1000;
    } catch (err) {
      return 0;
    }
  }

  /**
   * Atualiza o flag remoto (`playerEngine`) uma vez por boot, sem bloquear o app.
   * Falha de rede mantém o último valor cacheado.
   */
  async hydrateRemote() {
    const controller =
      typeof AbortController === 'function' ? new AbortController() : null;
    const timer = controller
      ? setTimeout(() => controller.abort(), ENGINE_MANIFEST_TIMEOUT_MS)
      : null;
    try {
      const res = await fetch(ENGINE_MANIFEST_URL + '?t=' + Date.now(), {
        cache: 'no-store',
        signal: controller ? controller.signal : undefined,
      });
      if (!res || !res.ok) return;
      const json = await res.json();
      const engine = json && json.playerEngine ? String(json.playerEngine).toLowerCase() : '';
      if (engine === 'factory' || engine === 'legacy') {
        this.#remoteEngine = engine;
        this.#call('onEngineFlag', { engine, reason: this.engineReason });
      }
    } catch (err) {
      /* offline: mantém o cache */
    } finally {
      if (timer) clearTimeout(timer);
    }
  }

  /* ------------------------------------------------------------------ */

  #ensurePlayer() {
    const video = typeof document !== 'undefined' ? document.getElementById('tvPlayer') : null;
    if (!video) return null;
    if (this.#player && this.#videoEl === video) return this.#player;

    if (this.#player) {
      try {
        this.#player.destroy();
      } catch (err) {
        /* ignora */
      }
      this.#player = null;
      this.#wired = false;
    }

    this.#videoEl = video;
    this.#player = PlayerFactory.create(video);
    this.#wire();
    return this.#player;
  }

  #wire() {
    if (this.#wired || !this.#player) return;
    const p = this.#player;

    p.on('buffering', () => this.#call('onBuffering'));
    p.on('buffered', () => this.#call('onPlaying'));
    p.on('playing', () => this.#call('onPlaying'));
    p.on('paused', () => this.#call('onPaused'));
    p.on('ended', () => this.#call('onEnded'));
    p.on('durationchange', () => this.#call('onDuration'));
    p.on('timeupdate', (payload) => this.#call('onTimeUpdate', payload));
    p.on('tracksReady', (payload) => {
      const tracks = (payload && payload.audioTracks) || [];
      this.#audioTracks = tracks;
      if (!this.#activeAudioId && tracks.length > 0) this.#activeAudioId = tracks[0].id;
      this.#call('onTracks', payload);
    });
    p.on('error', (payload) => {
      const fatal = !payload || payload.fatal !== false;
      if (fatal) this.#active = false;
      this.#call('onError', payload, fatal);
    });
    p.on('destroyed', () => {
      this.#active = false;
    });

    this.#wired = true;
  }
}

export const playerService = PlayerService.instance;

// Instalado no escopo global ANTES do legado rodar: o bundle executa o corpo
// deste módulo antes do corpo de `src/legacy/app-legacy.js`.
if (typeof window !== 'undefined') {
  window.FBG2PlayerService = playerService;
}
