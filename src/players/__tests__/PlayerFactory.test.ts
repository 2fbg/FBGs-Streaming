/**
 * @jest-environment jsdom
 */
import { PlayerFactory } from '../PlayerFactory';
import { TizenAVPlayPlayer } from '../players/TizenAVPlayPlayer';
import { WebOSVideoPlayer } from '../players/WebOSVideoPlayer';
import { HLSFallbackPlayer } from '../players/HLSFallbackPlayer';

jest.mock('hls.js', () => {
  const Hls = jest.fn();
  (Hls as unknown as { isSupported: () => boolean }).isSupported = jest.fn(() => true);
  return { __esModule: true, default: Hls };
});

const makeVideo = (): HTMLVideoElement => document.createElement('video');

const fakeAVPlay = (): Record<string, unknown> => ({
  open: jest.fn(),
  close: jest.fn(),
  setDisplayRect: jest.fn(),
  setListener: jest.fn(),
  prepareAsync: jest.fn((ok: () => void) => ok()),
  play: jest.fn(),
  pause: jest.fn(),
  stop: jest.fn(),
  seekTo: jest.fn(),
  getCurrentTime: jest.fn(() => 0),
  getDuration: jest.fn(() => 0),
  getState: jest.fn(() => 'IDLE'),
  setStreamingProperty: jest.fn(),
  setBufferingParam: jest.fn(),
  setSelectTrack: jest.fn(),
  getCurrentStreamInfo: jest.fn(() => []),
  getTotalTrackInfo: jest.fn(() => []),
  setVolume: jest.fn(),
});

describe('PlayerFactory.detectPlatform', () => {
  const originalUA = navigator.userAgent;

  afterEach(() => {
    delete (window as { tizen?: unknown }).tizen;
    delete (window as { webOS?: unknown }).webOS;
    delete (window as { PalmSystem?: unknown }).PalmSystem;
    delete (window as { webapis?: unknown }).webapis;
    Object.defineProperty(navigator, 'userAgent', { value: originalUA, configurable: true });
  });

  it('detecta Tizen via UA + objeto window.tizen', () => {
    Object.defineProperty(navigator, 'userAgent', {
      value: 'Mozilla/5.0 (SMART-TV; Tizen 6.0)',
      configurable: true,
    });
    (window as { tizen?: unknown }).tizen = {};
    expect(PlayerFactory.detectPlatform()).toBe('tizen');
  });

  it('detecta Tizen apenas pelo objeto window.tizen', () => {
    (window as { tizen?: unknown }).tizen = {};
    expect(PlayerFactory.detectPlatform()).toBe('tizen');
  });

  it('detecta Tizen pelo webapis.avplay mesmo sem window.tizen', () => {
    (window as { webapis?: unknown }).webapis = { avplay: fakeAVPlay() };
    expect(PlayerFactory.detectPlatform()).toBe('tizen');
  });

  it('detecta webOS via window.webOS + UA', () => {
    Object.defineProperty(navigator, 'userAgent', {
      value: 'Mozilla/5.0 (Web0S; webOS.TV-2021)',
      configurable: true,
    });
    (window as { webOS?: unknown }).webOS = {};
    expect(PlayerFactory.detectPlatform()).toBe('webos');
  });

  it('detecta webOS via PalmSystem', () => {
    (window as { PalmSystem?: unknown }).PalmSystem = {};
    expect(PlayerFactory.detectPlatform()).toBe('webos');
  });

  it('cai em browser em ambiente comum', () => {
    expect(PlayerFactory.detectPlatform()).toBe('browser');
  });
});

describe('PlayerFactory.create', () => {
  afterEach(() => {
    PlayerFactory.releaseTizen();
    delete (window as { tizen?: unknown }).tizen;
    delete (window as { webapis?: unknown }).webapis;
  });

  it('retorna TizenAVPlayPlayer quando avplay está disponível', () => {
    (window as { webapis?: unknown }).webapis = { avplay: fakeAVPlay() };
    const player = PlayerFactory.create(makeVideo(), 'tizen');
    expect(player).toBeInstanceOf(TizenAVPlayPlayer);
    expect(player.platform).toBe('tizen');
  });

  it('reaproveita a instância Tizen para o mesmo <video> (AVPlay é singleton)', () => {
    (window as { webapis?: unknown }).webapis = { avplay: fakeAVPlay() };
    const video = makeVideo();
    const first = PlayerFactory.create(video, 'tizen');
    const second = PlayerFactory.create(video, 'tizen');
    expect(second).toBe(first);
  });

  it('degrada para HLS quando avplay ausente no Tizen (simulador)', () => {
    const player = PlayerFactory.create(makeVideo(), 'tizen');
    expect(player).toBeInstanceOf(HLSFallbackPlayer);
  });

  it('retorna WebOSVideoPlayer para webOS', () => {
    const player = PlayerFactory.create(makeVideo(), 'webos');
    expect(player).toBeInstanceOf(WebOSVideoPlayer);
    expect(player.platform).toBe('webos');
  });

  it('retorna HLSFallbackPlayer para browser', () => {
    const player = PlayerFactory.create(makeVideo(), 'browser');
    expect(player).toBeInstanceOf(HLSFallbackPlayer);
  });

  it('lança exceção se elemento não for fornecido', () => {
    expect(() => PlayerFactory.create(null as unknown as HTMLVideoElement, 'browser')).toThrow(
      /elemento <video> obrigatório/,
    );
  });
});
