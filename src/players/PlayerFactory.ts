import type { IPlayer, Platform } from './types/player';
import { TizenAVPlayPlayer } from './players/TizenAVPlayPlayer';
import { WebOSVideoPlayer } from './players/WebOSVideoPlayer';
import { HLSFallbackPlayer } from './players/HLSFallbackPlayer';
import { log } from './utils/logger';

declare global {
  interface Window {
    tizen?: { tvinputdevice?: unknown; [key: string]: unknown };
    webOS?: { platform?: unknown; [key: string]: unknown };
    PalmSystem?: unknown;
  }
}

const hasAVPlay = (): boolean =>
  typeof window !== 'undefined' && !!(window as { webapis?: { avplay?: unknown } }).webapis?.avplay;

/**
 * Factory central de players do FBG2-Streaming.
 * Detecta a plataforma (Tizen / webOS / browser) e instancia a
 * engine de mídia correta para o hardware.
 */
export class PlayerFactory {
  /** webapis.avplay é singleton no dispositivo: nunca manter duas instâncias. */
  private static tizen: { element: HTMLVideoElement; player: IPlayer } | null = null;

  /**
   * Detecção de plataforma combinando userAgent + objetos globais
   * injetados pelos sistemas das TVs.
   */
  public static detectPlatform(): Platform {
    // webapis.avplay é o sinal inequívoco de TV Samsung com AVPlay.
    if (hasAVPlay()) return 'tizen';

    const ua = typeof navigator !== 'undefined' ? navigator.userAgent : '';

    const isTizenUA = /Tizen/i.test(ua) || /SMART-TV/i.test(ua) || /Samsung.*TV/i.test(ua);
    const hasTizenObj = typeof window !== 'undefined' && typeof window.tizen !== 'undefined';

    if (isTizenUA && hasTizenObj) return 'tizen';
    // Alguns firmwares Tizen escondem o objeto mas mantêm o UA.
    if (hasTizenObj) return 'tizen';

    const hasWebOSObj =
      typeof window !== 'undefined' &&
      (typeof window.webOS !== 'undefined' || typeof window.PalmSystem !== 'undefined');
    const isWebOSUA = /Web0S/i.test(ua) || /webOS/i.test(ua) || /LG.*TV/i.test(ua);

    if (hasWebOSObj && isWebOSUA) return 'webos';
    if (hasWebOSObj) return 'webos';

    return 'browser';
  }

  /**
   * Cria o player apropriado para a plataforma detectada (ou forçada).
   *
   * @param element  Elemento <video> da tela (surface).
   * @param platform Força uma plataforma (útil em testes e web simulado).
   */
  public static create(element: HTMLVideoElement, platform?: Platform): IPlayer {
    if (!element) throw new Error('[FBG2] PlayerFactory.create: elemento <video> obrigatório.');

    const target = platform ?? PlayerFactory.detectPlatform();
    log.info('Criando player para plataforma:', target);

    switch (target) {
      case 'tizen': {
        // Em Tizen, AVPlay é obrigatório para HEVC/VP9 e baixo consumo de CPU.
        // Se indisponível (simulador/browser), degrada para MSE.
        if (!hasAVPlay()) {
          log.warn('webapis.avplay ausente — degradando para HLS/MSE no Tizen.');
          return new HLSFallbackPlayer(element);
        }
        if (PlayerFactory.tizen && PlayerFactory.tizen.element === element) {
          return PlayerFactory.tizen.player;
        }
        PlayerFactory.tizen?.player.destroy();
        const player = new TizenAVPlayPlayer(element);
        PlayerFactory.tizen = { element, player };
        return player;
      }
      case 'webos':
        return new WebOSVideoPlayer(element);
      case 'browser':
      default:
        return new HLSFallbackPlayer(element);
    }
  }

  /** Libera a instância nativa (troca de tela/desligamento do app). */
  public static releaseTizen(): void {
    PlayerFactory.tizen?.player.destroy();
    PlayerFactory.tizen = null;
  }
}
