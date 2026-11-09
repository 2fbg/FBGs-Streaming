import type { DisplayRect, PlayOptions, TrackInfo, TracksReadyPayload } from '../types/player';
import { BasePlayer } from './BasePlayer';
import { log } from '../utils/logger';

declare global {
  interface Window {
    webOS?: { platform?: unknown; [key: string]: unknown };
    PalmSystem?: unknown;
  }
  // Alguns firmwares webOS expõem audioTracks no HTMLVideoElement.
  interface HTMLVideoElement {
    audioTracks?: {
      length: number;
      [index: number]: { label?: string; language?: string; enabled?: boolean };
    };
  }
}

/**
 * Player acelerado do LG webOS: usa o <video> nativo (decodificação
 * em hardware via gstreamer/ums) e expõe faixas em loadedmetadata.
 * webOS ≥ 4 suporta HLS nativo no <video> sem necessidade de MSE.
 */
export class WebOSVideoPlayer extends BasePlayer {
  public readonly platform = 'webos' as const;

  private audioTracks: TrackInfo[] = [];
  private subtitleTracks: TrackInfo[] = [];
  private lastError: MediaError | null = null;

  constructor(element: HTMLVideoElement) {
    super(element);
    element.addEventListener('loadedmetadata', this.handleLoadedMetadata);
    element.addEventListener('waiting', this.handleWaiting);
    element.addEventListener('playing', this.handlePlaying);
    element.addEventListener('ended', this.handleEnded);
    element.addEventListener('error', this.handleError);
    element.addEventListener('timeupdate', this.handleTimeUpdate);
  }

  public async play(url: string, options: PlayOptions = {}): Promise<void> {
    try {
      this.stop();
      this.lastError = null;
      this.setState('preparing');

      this.element.src = url;
      if (options.headers) {
        log.warn('webOS <video> nativo não suporta headers customizados.');
      }
      this.element.load();

      if (options.startPositionMs && options.startPositionMs > 0) {
        const onMeta = (): void => {
          const t = Math.min(options.startPositionMs ?? 0, (this.element.duration || Infinity) * 1000);
          this.element.currentTime = t / 1000;
        };
        this.element.addEventListener('loadedmetadata', onMeta, { once: true });
      }

      await this.element.play();
      this.setState('playing');
      this.emit('playing');
      log.info('webOS reproduzindo:', url);
    } catch (err) {
      // AbortError = um novo load() interrompeu este play (zapping).
      // Não é falha: o próximo play() já vai tocar.
      if (err instanceof Error && err.name === 'AbortError') {
        log.debug('play() abortado por nova carga.');
        return;
      }
      this.setState('error');
      const message = err instanceof Error ? err.message : String(err);
      this.emit('error', { code: 'WEBOS_PLAY', message, fatal: true, raw: err });
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
      this.emit('error', { code: 'WEBOS_RESUME', message, fatal: false, raw: err });
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
    this.element.pause();
    this.element.removeAttribute('src');
    try {
      this.element.load(); // reseta o pipeline de mídia do webOS
    } catch {
      /* ignore */
    }
    this.setState('idle');
  }

  public destroy(): void {
    this.stop();
    this.element.removeEventListener('loadedmetadata', this.handleLoadedMetadata);
    this.element.removeEventListener('waiting', this.handleWaiting);
    this.element.removeEventListener('playing', this.handlePlaying);
    this.element.removeEventListener('ended', this.handleEnded);
    this.element.removeEventListener('error', this.handleError);
    this.element.removeEventListener('timeupdate', this.handleTimeUpdate);
    this.finishDestroy();
  }

  public setAudioTrack(trackId: string): void {
    const track = this.audioTracks.find((t) => t.id === trackId);
    if (!track) {
      log.warn('Faixa de áudio desconhecida:', trackId);
      return;
    }
    const list = this.element.audioTracks;
    if (!list) {
      log.warn('audioTracks não exposto por este firmware webOS.');
      return;
    }
    for (let i = 0; i < list.length; i += 1) {
      const t = list[i];
      if (t) t.enabled = i === track.index;
    }
    log.debug('Áudio selecionado:', track.name);
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

  /** Reposiciona a janela de vídeo (PiP / multi-tela). */
  public setDisplay(rect: DisplayRect): void {
    Object.assign(this.element.style, {
      position: 'absolute',
      left: `${rect.left}px`,
      top: `${rect.top}px`,
      width: `${rect.width}px`,
      height: `${rect.height}px`,
    });
  }

  /* ------------------------------------------------------------------ */

  private handleLoadedMetadata = (): void => {
    this.extractTracks();
    this.emit('ready');
  };

  private extractTracks(): void {
    this.audioTracks = [];
    this.subtitleTracks = [];

    const nativeAudio = this.element.audioTracks;
    if (nativeAudio) {
      for (let i = 0; i < nativeAudio.length; i += 1) {
        const t = nativeAudio[i];
        if (!t) continue;
        this.audioTracks.push({
          id: `audio-${i}`,
          kind: 'audio',
          index: i,
          name: t.label || t.language || `Áudio ${i + 1}`,
          language: t.language || 'und',
        });
      }
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

  private handleWaiting = (): void => {
    this.setState('buffering');
    this.emit('buffering');
  };

  private handlePlaying = (): void => {
    this.setState('playing');
    this.emit('buffered');
  };

  private handleEnded = (): void => {
    this.setState('ended');
    this.emit('ended');
  };

  private handleError = (): void => {
    this.lastError = this.element.error;
    this.setState('error');
    this.emit('error', {
      code: this.lastError?.code ?? 'UNKNOWN',
      message: `webOS Video error: code ${this.lastError?.code ?? 'desconhecido'}`,
      fatal: true,
      raw: this.lastError,
    });
  };

  private handleTimeUpdate = (): void => {
    this.emit('timeupdate', {
      currentTimeMs: this.getCurrentTime(),
      durationMs: this.getDuration(),
    });
  };
}
