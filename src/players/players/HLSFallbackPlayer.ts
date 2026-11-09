import Hls, { ErrorTypes, Events } from 'hls.js';
import type { ErrorData, HlsConfig } from 'hls.js';
import type { PlayOptions, TrackInfo, TracksReadyPayload } from '../types/player';
import { BasePlayer } from './BasePlayer';
import { log } from '../utils/logger';

const HLS_CONFIG: Partial<HlsConfig> = {
  maxBufferLength: 30, // ~30s de buffer à frente
  maxMaxBufferLength: 60, // teto para redes muito instáveis
  liveSyncDurationCount: 3, // alvo de sync em live
  manifestLoadingMaxRetry: 2,
  levelLoadingMaxRetry: 2,
  fragLoadingMaxRetry: 4,
  startLevel: -1, // ABR automático desde o início
};

/**
 * Fallback MSE/HLS para browser e TVs sem engine nativa.
 * Usa hls.js quando MSE está disponível; senão tenta HLS nativo
 * (Safari, alguns firmwares).
 */
export class HLSFallbackPlayer extends BasePlayer {
  public readonly platform = 'browser' as const;

  private hls: Hls | null = null;
  private usingNativeHls = false;
  private audioTracks: TrackInfo[] = [];
  private subtitleTracks: TrackInfo[] = [];

  constructor(element: HTMLVideoElement) {
    super(element);
    element.addEventListener('waiting', this.handleWaiting);
    element.addEventListener('playing', this.handleBuffered);
    element.addEventListener('ended', this.handleEnded);
    element.addEventListener('error', this.handleVideoError);
  }

  public async play(url: string, options: PlayOptions = {}): Promise<void> {
    try {
      this.stop();
      this.setState('preparing');

      if (Hls.isSupported()) {
        this.playWithHlsJs(url, options);
        // Com MSE o <video> só ganha mídia depois do manifesto: dar play()
        // antes disso rejeita com NotSupportedError e vira erro fatal.
        await this.waitForManifest();
      } else if (this.element.canPlayType('application/vnd.apple.mpegurl')) {
        this.playNative(url, options);
      } else {
        throw new Error('[FBG2] Nenhum engine HLS disponível neste dispositivo.');
      }

      if (options.startPositionMs && options.startPositionMs > 0) {
        this.element.addEventListener(
          'loadedmetadata',
          () => {
            this.element.currentTime = (options.startPositionMs ?? 0) / 1000;
          },
          { once: true },
        );
      }

      await this.element.play();
      this.setState('playing');
      this.emit('playing');
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        log.debug('play() abortado por nova carga.');
        return;
      }
      this.setState('error');
      const message = err instanceof Error ? err.message : String(err);
      this.emit('error', { code: 'HLS_PLAY', message, fatal: true, raw: err });
      throw err;
    }
  }

  public pause(): void {
    this.element.pause();
    this.setState('paused');
    this.emit('paused');
  }

  public resume(): void {
    this.element.play().catch((err: unknown) => {
      const message = err instanceof Error ? err.message : String(err);
      this.emit('error', { code: 'HLS_RESUME', message, fatal: false, raw: err });
    });
  }

  public seek(timeMs: number): void {
    const durationSec = this.element.duration || 0;
    const target = durationSec > 0
      ? Math.max(0, Math.min(timeMs / 1000, durationSec))
      : Math.max(0, timeMs / 1000);
    this.element.currentTime = target;
  }

  public stop(): void {
    if (this.hls) {
      this.hls.destroy();
      this.hls = null;
    }
    this.usingNativeHls = false;
    this.element.pause();
    this.element.removeAttribute('src');
    try {
      this.element.load();
    } catch {
      /* ignore */
    }
    this.setState('idle');
  }

  public destroy(): void {
    this.stop();
    this.element.removeEventListener('waiting', this.handleWaiting);
    this.element.removeEventListener('playing', this.handleBuffered);
    this.element.removeEventListener('ended', this.handleEnded);
    this.element.removeEventListener('error', this.handleVideoError);
    this.finishDestroy();
  }

  public setAudioTrack(trackId: string): void {
    const track = this.audioTracks.find((t) => t.id === trackId);
    const index = track ? track.index : Number(trackId.replace('audio-', ''));
    if (this.hls && Number.isInteger(index) && index >= 0) {
      this.hls.audioTrack = index;
      log.debug('Áudio selecionado:', track?.name ?? trackId);
    } else {
      log.warn('setAudioTrack sem hls.js ativo:', trackId);
    }
  }

  public setSubtitleTrack(trackId: string | null): void {
    const tracks = this.element.textTracks;
    for (let i = 0; i < tracks.length; i += 1) {
      const t = tracks[i];
      if (!t) continue;
      t.mode = trackId && this.subtitleTracks.find((s) => s.id === trackId)?.index === i ? 'showing' : 'disabled';
    }
  }

  public setVolume(volume: number): void {
    this.element.volume = Math.max(0, Math.min(1, volume));
  }

  public getDuration(): number {
    const d = this.element.duration;
    return Number.isFinite(d) ? (d as number) * 1000 : 0;
  }

  public getCurrentTime(): number {
    return (this.element.currentTime || 0) * 1000;
  }

  /* ------------------------------------------------------------------ */

  /** Espera o manifesto (ou desiste em erro fatal não recuperável). */
  private waitForManifest(timeoutMs = 15000): Promise<void> {
    const hls = this.hls;
    if (!hls) return Promise.resolve();
    if (hls.levels && hls.levels.length > 0) return Promise.resolve();

    return new Promise<void>((resolve, reject) => {
      let timer: ReturnType<typeof setTimeout> | undefined;
      const cleanup = (): void => {
        if (timer) clearTimeout(timer);
        hls.off(Events.MANIFEST_PARSED, onParsed);
        hls.off(Events.ERROR, onError);
      };
      const onParsed = (): void => {
        cleanup();
        resolve();
      };
      const onError = (_event: unknown, data: ErrorData): void => {
        // NETWORK_ERROR tem recuperação automática no handler principal:
        // deixa ele decidir, senão o failover dispara cedo demais.
        if (!data.fatal || data.type === ErrorTypes.NETWORK_ERROR) return;
        cleanup();
        reject(new Error(`hls.js fatal: ${data.type}/${data.details}`));
      };
      timer = setTimeout(() => {
        cleanup();
        reject(new Error('timeout aguardando o manifesto HLS'));
      }, timeoutMs);
      hls.on(Events.MANIFEST_PARSED, onParsed);
      hls.on(Events.ERROR, onError);
    });
  }

  private playWithHlsJs(url: string, options: PlayOptions): void {
    const config: Partial<HlsConfig> = { ...HLS_CONFIG };
    if (options.headers && Object.keys(options.headers).length > 0) {
      config.xhrSetup = (xhr: XMLHttpRequest): void => {
        Object.entries(options.headers ?? {}).forEach(([k, v]) => xhr.setRequestHeader(k, v));
      };
    }

    this.hls = new Hls(config);
    this.hls.loadSource(url);
    this.hls.attachMedia(this.element);

    this.hls.on(Events.MANIFEST_PARSED, () => {
      log.debug('Manifesto HLS carregado.');
      this.emit('ready');
      this.emitTracks();
    });
    // Faixas de áudio são faixas, não níveis de qualidade (levels = bitrates).
    this.hls.on(Events.AUDIO_TRACKS_UPDATED, () => this.emitTracks());

    this.hls.on(Events.ERROR, (_e, data) => {
      if (!data.fatal) return;
      log.error('Erro fatal hls.js:', data.type, data.details);

      // Tentativa de recuperação antes de propagar erro (failover no app).
      switch (data.type) {
        case ErrorTypes.NETWORK_ERROR:
          try {
            this.hls?.startLoad();
            return; // recuperado — não emite 'error' fatal
          } catch {
            /* cai para o emit */
          }
          break;
        case ErrorTypes.MEDIA_ERROR:
          try {
            this.hls?.recoverMediaError();
            return;
          } catch {
            /* cai para o emit */
          }
          break;
        default:
          break;
      }

      this.setState('error');
      this.emit('error', {
        code: data.details,
        message: `hls.js fatal: ${data.type}/${data.details}`,
        fatal: true,
        raw: data,
      });
    });
  }

  private emitTracks(): void {
    const hls = this.hls;
    this.audioTracks = [];
    this.subtitleTracks = [];

    const nativeAudio = hls?.audioTracks ?? [];
    nativeAudio.forEach((t, i) => {
      this.audioTracks.push({
        id: `audio-${i}`,
        kind: 'audio',
        index: i,
        name: t.name || t.lang || `Áudio ${i + 1}`,
        language: t.lang || 'und',
      });
    });
    // Stream com uma única faixa: mantém o seletor utilizável.
    if (this.audioTracks.length === 0 && (hls?.levels?.length ?? 0) > 0) {
      this.audioTracks.push({ id: 'audio-0', kind: 'audio', index: 0, name: 'Áudio principal', language: 'und' });
    }

    const textTracks = this.element.textTracks;
    for (let i = 0; i < textTracks.length; i += 1) {
      const t = textTracks[i];
      if (!t) continue;
      if (t.kind !== 'subtitles' && t.kind !== 'captions') continue;
      this.subtitleTracks.push({
        id: `subtitle-${i}`,
        kind: 'subtitle',
        index: i,
        name: t.label || t.language || `Legenda ${i + 1}`,
        language: t.language || 'und',
      });
    }

    const payload: TracksReadyPayload = {
      audioTracks: this.audioTracks,
      subtitleTracks: this.subtitleTracks,
    };
    this.emit('tracksReady', payload);
  }

  private playNative(url: string, _options: PlayOptions): void {
    this.usingNativeHls = true;
    this.element.src = url;
    this.element.load();
    log.info('Usando HLS nativo (sem MSE).');
  }

  private handleWaiting = (): void => {
    this.setState('buffering');
    this.emit('buffering');
  };

  private handleBuffered = (): void => {
    this.setState('playing');
    this.emit('buffered');
  };

  private handleEnded = (): void => {
    this.setState('ended');
    this.emit('ended');
  };

  private handleVideoError = (): void => {
    const err = this.element.error;
    if (this.usingNativeHls && err) {
      this.setState('error');
      this.emit('error', {
        code: err.code,
        message: `HLS nativo error: code ${err.code}`,
        fatal: true,
        raw: err,
      });
    }
  };
}
