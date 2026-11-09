import type {
  DisplayRect,
  IPlayer,
  PlayOptions,
  PlayerEvent,
  PlayerEventCallback,
  PlayerState,
  Platform,
} from '../types/player';
import { log } from '../utils/logger';

/**
 * Classe base: implementa o emitter de eventos e utilidades comuns,
 * para que cada player concreto só cuide da engine de mídia.
 */
export abstract class BasePlayer implements IPlayer {
  public abstract readonly platform: Platform;

  protected readonly element: HTMLVideoElement;
  private listeners = new Map<PlayerEvent, Set<PlayerEventCallback>>();
  private state: PlayerState = 'idle';

  protected constructor(element: HTMLVideoElement) {
    if (!element) throw new Error('[FBG2] BasePlayer: elemento <video> obrigatório.');
    this.element = element;
  }

  abstract play(url: string, options?: PlayOptions): Promise<void>;
  abstract pause(): void;
  abstract resume(): void;
  abstract seek(timeMs: number): void;
  abstract stop(): void;
  abstract destroy(): void;
  abstract setAudioTrack(trackId: string): void;
  abstract getDuration(): number;
  abstract getCurrentTime(): number;

  /** Sobrescreva para mover a superfície de vídeo (PiP/multi-tela). */
  public setDisplay?(rect: DisplayRect): void;
  /** Volume 0..1. Padrão usa o próprio <video>. */
  public setVolume(volume: number): void {
    try {
      this.element.volume = Math.max(0, Math.min(1, volume));
    } catch (err) {
      log.warn('setVolume falhou:', err);
    }
  }
  public setSubtitleTrack(_trackId: string | null): void {
    /* implementações com legendas sobrescrevem */
  }

  public getState(): PlayerState {
    return this.state;
  }

  /** Buffer à frente do playhead em ms (aprox. para APIs nativas). */
  public getBufferedMs(): number {
    const b = this.element.buffered;
    if (!b || b.length === 0) return 0;
    const t = this.element.currentTime || 0;
    for (let i = 0; i < b.length; i += 1) {
      if (b.start(i) <= t && t <= b.end(i)) return Math.max(0, (b.end(i) - t) * 1000);
    }
    return 0;
  }

  public on(event: PlayerEvent, callback: PlayerEventCallback): void {
    let set = this.listeners.get(event);
    if (!set) {
      set = new Set();
      this.listeners.set(event, set);
    }
    set.add(callback);
  }

  public off(event: PlayerEvent, callback: PlayerEventCallback): void {
    this.listeners.get(event)?.delete(callback);
  }

  protected setState(next: PlayerState): void {
    this.state = next;
  }

  protected emit(event: PlayerEvent, payload?: unknown): void {
    const set = this.listeners.get(event);
    if (!set) return;
    set.forEach((cb) => {
      try {
        cb(payload);
      } catch (err) {
        log.error(`Listener de "${event}" lançou exceção:`, err);
      }
    });
  }

  /**
   * Emite 'destroyed' e SÓ DEPOIS limpa os listeners.
   * Na ordem inversa (teardown -> emit) ninguém recebe o evento.
   */
  protected finishDestroy(): void {
    this.setState('idle');
    this.emit('destroyed');
    this.teardownListeners();
  }

  protected teardownListeners(): void {
    this.listeners.clear();
  }
}
