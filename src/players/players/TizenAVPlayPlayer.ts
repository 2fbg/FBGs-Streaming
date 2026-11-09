import type {
  DisplayRect,
  PlayOptions,
  PlayerState,
  TrackInfo,
  TracksReadyPayload,
} from '../types/player';
import { BasePlayer } from './BasePlayer';
import { log } from '../utils/logger';

/**
 * Declarações mínimas das APIs nativas Tizen (webapis).
 * Evita depender de @types/tizen-tv-webapis no bundle web.
 */
export interface TizenAVPlayListener {
  onbufferingstart(): void;
  onbufferingprogress(percent: number): void;
  onbufferingcomplete(): void;
  onstreamcompleted(): void;
  oncurrentplaytime(milliseconds: number): void;
  onerror(eventType: string): void;
  onevent(eventType: string, eventData: unknown): void;
  ondrmevent?(drmEvent: unknown, drmData: unknown): void;
  onsubtitlechange?(duration: number, text: string, type: string, attriCount: number, attributes: unknown[]): void;
}

/**
 * Item de getCurrentStreamInfo()/getTotalTrackInfo().
 * A API real devolve `extra_info` como JSON *string*
 * ('{"track_num":"0","track_lang":"en"}'); alguns firmwares antigos
 * devolviam um objeto em `info`.
 */
export interface TizenStreamInfo {
  type: 'AUDIO' | 'VIDEO' | 'TEXT';
  index?: number;
  extra_info?: string | Record<string, unknown>;
  info?: { track_num?: number; track_lang?: string; fourCC?: string; [key: string]: unknown };
}

export interface TizenAVPlay {
  open(url: string): void;
  close(): void;
  /** Não existe em todos os firmwares — preferimos setDisplayRect. */
  setDisplay?(rect: DisplayRect): void;
  setDisplayRect(left: number, top: number, width: number, height: number): void;
  setListener(listener: TizenAVPlayListener): void;
  prepareAsync(success: () => void, error: (err: unknown) => void): void;
  play(): void;
  pause(): void;
  stop(): void;
  seekTo(milliseconds: number): void;
  getCurrentTime(): number;
  getDuration(): number;
  getState(): 'IDLE' | 'READY' | 'PLAYING' | 'PAUSED' | 'PLAYING_FORWARD' | 'PLAYING_REVERSE';
  setStreamingProperty(type: string, property: unknown): void;
  /** Buffer real do AVPlay: segundos, só em IDLE (antes do prepare). */
  setBufferingParam?(
    option: 'PLAYER_BUFFER_FOR_PLAY' | 'PLAYER_BUFFER_FOR_RESUME',
    unit: 'PLAYER_BUFFER_SIZE_IN_SECOND',
    amount: number,
  ): void;
  setSelectTrack(type: 'AUDIO' | 'VIDEO' | 'TEXT', index: number): void;
  getCurrentStreamInfo(): TizenStreamInfo[];
  getTotalTrackInfo(): TizenStreamInfo[];
  setVolume(volume: number): void;
}

interface TizenWebapis {
  avplay: TizenAVPlay;
}

declare global {
  interface Window {
    webapis?: TizenWebapis;
  }
}

const clamp = (v: number, min: number, max: number): number => Math.max(min, Math.min(v, max));

/** Converte extra_info (JSON string ou objeto) em um mapa de atributos. */
const parseExtra = (raw: TizenStreamInfo['extra_info'] | TizenStreamInfo['info']): Record<string, unknown> => {
  if (!raw) return {};
  if (typeof raw === 'object') return raw as Record<string, unknown>;
  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
};

/**
 * Player de hardware Tizen (Samsung) baseado em webapis.avplay.
 * Decodificação 100% em hardware — essencial para TVs antigas.
 */
export class TizenAVPlayPlayer extends BasePlayer {
  public readonly platform = 'tizen' as const;

  private avplay: TizenAVPlay;
  private audioTracks: TrackInfo[] = [];
  private destroyed = false;
  private preparing = false;
  private tracksReady = false;
  private bufferedMs = 0;

  constructor(element: HTMLVideoElement) {
    super(element);
    if (!window.webapis?.avplay) {
      throw new Error('[FBG2] webapis.avplay indisponível neste dispositivo.');
    }
    this.avplay = window.webapis.avplay;
    this.element.classList.add('fbg2-avplay-surface');
    window.addEventListener('resize', this.applyDisplayRect);
    document.addEventListener('fullscreenchange', this.applyDisplayRect);
    document.addEventListener('webkitfullscreenchange', this.applyDisplayRect);
  }

  /** Abre o stream, configura buffer/listener e prepara de forma assíncrona. */
  public async play(url: string, options: PlayOptions = {}): Promise<void> {
    // Fecha qualquer sessão anterior ANTES de abrir: AVPlay é singleton e
    // open() em estado READY/PLAYING lança InvalidStateError.
    this.safeClose();
    this.destroyed = false;
    this.tracksReady = false;
    this.preparing = true;
    this.setState('preparing');

    try {
      this.avplay.open(url);
      this.avplay.setListener(this.buildListener());

      const bufferMs = options.bufferSizeMs ?? 3000;

      // Mantido por compatibilidade com firmwares que aceitam a propriedade
      // (varia por modelo) — nunca pode derrubar o play.
      try {
        this.avplay.setStreamingProperty('BUFFER_SIZE', String(bufferMs));
        log.debug('BUFFER_SIZE =', bufferMs);
      } catch {
        log.warn('setStreamingProperty(BUFFER_SIZE) não suportado neste modelo.');
      }

      // Caminho oficial (Samsung): segundos, apenas em IDLE (antes do prepare).
      const bufferSeconds = clamp(Math.round(bufferMs / 1000), 3, 30);
      try {
        this.avplay.setBufferingParam?.('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', bufferSeconds);
        this.avplay.setBufferingParam?.(
          'PLAYER_BUFFER_FOR_RESUME',
          'PLAYER_BUFFER_SIZE_IN_SECOND',
          Math.max(bufferSeconds, 15),
        );
        log.debug('setBufferingParam =', bufferSeconds, 's');
      } catch {
        log.warn('setBufferingParam não suportado neste modelo.');
      }

      this.applyDisplayRect();
      await this.prepareAsync();

      if (this.destroyed) return;

      // READY: getTotalTrackInfo() já responde e o menu de áudio não nasce vazio.
      this.extractTracks();

      if (options.startPositionMs && options.startPositionMs > 0) {
        this.avplay.seekTo(clamp(options.startPositionMs, 0, this.getDuration() || options.startPositionMs));
      }

      this.avplay.play();
      this.preparing = false;
      this.setState('playing');
      this.emit('playing');
      log.info('AVPlay reproduzindo:', url);
    } catch (err) {
      this.preparing = false;
      this.setState('error');
      // Sem isto, uma falha de prepare deixa o AVPlay aberto e o próximo
      // open() falha para sempre ("player travado até reiniciar o app").
      this.safeClose();
      const message = err instanceof Error ? err.message : String(err);
      this.emit('error', { code: 'AVPLAY_OPEN', message, fatal: true, raw: err });
      throw err;
    }
  }

  public pause(): void {
    try {
      if (this.avplay.getState() === 'PLAYING') {
        this.avplay.pause();
        this.setState('paused');
        this.emit('paused');
      }
    } catch (err) {
      log.warn('pause() falhou:', err);
    }
  }

  public resume(): void {
    try {
      if (this.avplay.getState() === 'PAUSED') {
        this.avplay.play();
        this.setState('playing');
        this.emit('playing');
      }
    } catch (err) {
      log.warn('resume() falhou:', err);
    }
  }

  public seek(timeMs: number): void {
    try {
      const duration = this.getDuration();
      const target = duration > 0 ? clamp(timeMs, 0, duration) : Math.max(0, timeMs);
      this.avplay.seekTo(target);
    } catch (err) {
      log.warn('seek() falhou:', err);
    }
  }

  public stop(): void {
    this.safeClose();
    this.setState('idle');
  }

  public destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    this.safeClose();
    window.removeEventListener('resize', this.applyDisplayRect);
    document.removeEventListener('fullscreenchange', this.applyDisplayRect);
    document.removeEventListener('webkitfullscreenchange', this.applyDisplayRect);
    this.finishDestroy();
  }

  /** Seleciona faixa de áudio pelo id ("audio-3") gerado em extractTracks. */
  public setAudioTrack(trackId: string): void {
    const track = this.audioTracks.find((t) => t.id === trackId);
    if (!track) {
      log.warn('Faixa de áudio desconhecida:', trackId);
      return;
    }
    try {
      this.avplay.setSelectTrack('AUDIO', track.index);
      log.debug('Áudio selecionado:', track.name);
    } catch (err) {
      log.warn('setSelectTrack falhou:', err);
    }
  }

  public setVolume(volume: number): void {
    try {
      this.avplay.setVolume(clamp(volume, 0, 1));
    } catch (err) {
      log.warn('setVolume falhou:', err);
    }
  }

  public getState(): PlayerState {
    if (this.preparing) return 'preparing';
    return super.getState();
  }

  /** AVPlay só informa percentual de rebuffer; usamos isso como sinal. */
  public getBufferedMs(): number {
    return this.bufferedMs;
  }

  public getDuration(): number {
    try {
      return this.avplay.getDuration() || 0;
    } catch {
      return 0;
    }
  }

  public getCurrentTime(): number {
    try {
      return this.avplay.getCurrentTime() || 0;
    } catch {
      return 0;
    }
  }

  public setDisplay(rect: DisplayRect): void {
    try {
      // setDisplay(rect) não existe na API atual; setDisplayRect é o oficial.
      this.avplay.setDisplayRect(
        Math.round(rect.left),
        Math.round(rect.top),
        Math.round(rect.width),
        Math.round(rect.height),
      );
    } catch (err) {
      log.warn('setDisplayRect falhou:', err);
    }
  }

  /* ------------------------------------------------------------------ */

  private prepareAsync(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.avplay.prepareAsync(
        () => {
          this.preparing = false;
          resolve();
        },
        (err) => {
          this.preparing = false;
          reject(err instanceof Error ? err : new Error(String(err)));
        },
      );
    });
  }

  /**
   * Reaplica a área do vídeo. AVPlay não entende CSS: o <video> serve só de
   * régua. Container 0×0 (troca de tela) é ignorado para não gerar tela preta.
   */
  private applyDisplayRect = (): void => {
    const rect = this.element.getBoundingClientRect();
    const width = Math.round(rect.width);
    const height = Math.round(rect.height);
    if (width <= 0 || height <= 0) return;
    this.setDisplay({ left: rect.left, top: rect.top, width, height });
  };

  /** stop() e close() em try separados: falha no stop não pode pular o close. */
  private safeClose(): void {
    try {
      if (this.avplay.getState() !== 'IDLE') this.avplay.stop();
    } catch {
      /* segue para o close de qualquer forma */
    }
    try {
      this.avplay.close();
    } catch {
      /* já fechado / IDLE */
    }
    this.preparing = false;
  }

  private buildListener(): TizenAVPlayListener {
    return {
      onbufferingstart: () => {
        this.bufferedMs = 0;
        this.setState('buffering');
        this.emit('buffering');
      },
      onbufferingprogress: (percent: number) => {
        this.bufferedMs = Math.round(percent);
        log.debug('buffering:', percent + '%');
      },
      onbufferingcomplete: () => {
        this.bufferedMs = 0;
        this.setState('playing');
        this.emit('buffered');
      },
      onstreamcompleted: () => {
        this.setState('ended');
        this.emit('ended');
      },
      oncurrentplaytime: (ms: number) => {
        // As trilhas só aparecem quando o stream realmente toca.
        if (!this.tracksReady) this.extractTracks();
        this.emit('timeupdate', { currentTimeMs: ms, durationMs: this.getDuration() });
      },
      onerror: (eventType: string) => {
        this.setState('error');
        this.emit('error', { code: eventType, message: `AVPlay error: ${eventType}`, fatal: true });
      },
      onevent: (eventType: string, eventData: unknown) => {
        log.debug('onevent:', eventType, eventData);
      },
      ondrmevent: (drmEvent, drmData) => {
        log.debug('drmevent:', drmEvent, drmData);
      },
      onsubtitlechange: (duration, text) => {
        // Hook para futuro módulo de legendas (WebVTT overlay).
        log.debug('subtitle:', duration, text);
      },
    };
  }

  /**
   * Normaliza as faixas. Usa getTotalTrackInfo() quando existe (lista
   * completa) e cai para getCurrentStreamInfo(); `extra_info` é JSON string.
   */
  private extractTracks(): void {
    let infos: TizenStreamInfo[] = [];
    try {
      infos = this.avplay.getTotalTrackInfo() ?? [];
    } catch {
      /* firmware sem getTotalTrackInfo */
    }
    if (!infos || infos.length === 0) {
      try {
        infos = this.avplay.getCurrentStreamInfo() ?? [];
      } catch (err) {
        log.warn('getCurrentStreamInfo falhou:', err);
        return;
      }
    }

    this.audioTracks = (infos ?? [])
      .filter((s) => s.type === 'AUDIO')
      .map((s, i) => {
        const extra = { ...parseExtra(s.info), ...parseExtra(s.extra_info) };
        const trackNumRaw = extra.track_num ?? extra.track_index;
        const trackIndex = Number(trackNumRaw);
        const index = Number.isFinite(trackIndex) && trackIndex >= 0
          ? trackIndex
          : typeof s.index === 'number' && s.index >= 0
            ? s.index
            : i;
        const language = String(extra.track_lang ?? extra.language ?? 'und');
        const trackName = extra.track_name ? String(extra.track_name) : '';
        return {
          id: `audio-${index}`,
          kind: 'audio' as const,
          index,
          name: trackName || `Áudio (${language.toUpperCase()})`,
          language,
        };
      });

    if (this.audioTracks.length > 0) this.tracksReady = true;

    const payload: TracksReadyPayload = { audioTracks: this.audioTracks, subtitleTracks: [] };
    this.emit('tracksReady', payload);
  }
}
