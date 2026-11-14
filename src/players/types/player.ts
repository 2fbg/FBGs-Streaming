/**
 * Contratos comuns do Player do FBG2-Streaming.
 * Toda implementação (Tizen/webOS/HLS) expõe exatamente esta interface.
 */

/** Plataformas suportadas pela factory. */
export type Platform = 'tizen' | 'webos' | 'browser';

/** Eventos normalizados emitidos por qualquer implementação de player. */
export type PlayerEvent =
  | 'ready'
  | 'playing'
  | 'paused'
  | 'buffering'
  | 'buffered'
  | 'ended'
  | 'error'
  | 'tracksReady'
  | 'timeupdate'
  | 'durationchange'
  | 'destroyed';

export type PlayerEventCallback = (payload?: unknown) => void;

/**
 * Estado interno normalizado. A UI usa isto para o badge de buffering e o
 * watchdog de travamento (que na 2.6.0 passou a olhar crescimento de buffer).
 */
export type PlayerState =
  | 'idle'
  | 'preparing'
  | 'playing'
  | 'buffering'
  | 'paused'
  | 'ended'
  | 'error';

export interface TrackInfo {
  id: string;
  kind: 'audio' | 'subtitle';
  index: number;
  name: string;
  language: string;
}

export interface TracksReadyPayload {
  audioTracks: TrackInfo[];
  subtitleTracks: TrackInfo[];
}

export interface ErrorPayload {
  code?: string | number;
  message: string;
  fatal: boolean;
  raw?: unknown;
}

export interface TimeUpdatePayload {
  currentTimeMs: number;
  durationMs: number;
}

/** Opções aceitas por play(). */
export interface PlayOptions {
  /** Posição inicial em milissegundos. */
  startPositionMs?: number;
  /** Headers HTTP (aplicável ao HLSFallbackPlayer via hls.js). */
  headers?: Record<string, string>;
  /** Tamanho de buffer alvo em ms (Tizen AVPlay). Default: 3000. */
  bufferSizeMs?: number;
  /** Não pausar a reprodução de fundo ao trocar de app (Tizen). */
  keepAudioOnBackground?: boolean;
}

/** Retângulo em pixels absolutos (usado em PiP / multi-tela). */
export interface DisplayRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

/** Interface comum que TODOS os players devem implementar. */
export interface IPlayer {
  /** Plataforma que esta instância atende. */
  readonly platform: Platform;

  play(url: string, options?: PlayOptions): Promise<void>;
  pause(): void;
  resume(): void;
  /** seek em milissegundos. */
  seek(timeMs: number): void;
  stop(): void;
  destroy(): void;
  setAudioTrack(trackId: string): void;
  /** Duração em ms (Infinity/0 para live). */
  getDuration(): number;
  getCurrentTime(): number;
  /** Reposiciona a superfície de vídeo (PiP/multi-tela). Opcional. */
  setDisplay?(rect: DisplayRect): void;

  /** Estado normalizado (usado por watchdog/HUD). */
  getState?(): PlayerState;
  /**
   * Buffer disponível à frente do playhead, em ms.
   * É por aqui que se detecta travamento sem depender de `waiting`/`stalled`.
   */
  getBufferedMs?(): number;
  /** Volume 0..1. */
  setVolume?(volume: number): void;
  /** `null` desliga as legendas. */
  setSubtitleTrack?(trackId: string | null): void;

  on(event: PlayerEvent, callback: PlayerEventCallback): void;
  off(event: PlayerEvent, callback: PlayerEventCallback): void;
}
