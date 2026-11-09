/**
 * @jest-environment jsdom
 */
import type { TracksReadyPayload } from '../types/player';

jest.mock('hls.js', () => {
  class FakeHls {
    static instances: FakeHls[] = [];
    static isSupported = (): boolean => true;
    static Events = {
      MANIFEST_PARSED: 'manifestParsed',
      AUDIO_TRACKS_UPDATED: 'audioTracksUpdated',
      ERROR: 'hlsError',
    };
    static ErrorTypes = { NETWORK_ERROR: 'networkError', MEDIA_ERROR: 'mediaError', OTHER_ERROR: 'otherError' };

    levels: Array<{ name?: string; height?: number }> = [];
    audioTracks: Array<{ name?: string; lang?: string }> = [];
    audioTrack = -1;
    loadSource = jest.fn();
    attachMedia = jest.fn();
    destroy = jest.fn();
    startLoad = jest.fn();
    recoverMediaError = jest.fn();
    private listeners: Record<string, Array<(...args: unknown[]) => void>> = {};

    constructor() {
      FakeHls.instances.push(this);
    }

    on(event: string, cb: (...args: unknown[]) => void): void {
      (this.listeners[event] ??= []).push(cb);
    }

    off(event: string, cb: (...args: unknown[]) => void): void {
      this.listeners[event] = (this.listeners[event] ?? []).filter((f) => f !== cb);
    }

    emit(event: string, ...args: unknown[]): void {
      (this.listeners[event] ?? []).forEach((f) => f(...args));
    }
  }
  const Events = {
    MANIFEST_PARSED: 'manifestParsed',
    AUDIO_TRACKS_UPDATED: 'audioTracksUpdated',
    ERROR: 'hlsError',
  };
  const ErrorTypes = { NETWORK_ERROR: 'networkError', MEDIA_ERROR: 'mediaError', OTHER_ERROR: 'otherError' };
  // O player importa os enums como named exports (hls.js 1.x), então o mock
  // precisa expor os dois formatos.
  return { __esModule: true, default: FakeHls, Events, ErrorTypes };
});

import Hls from 'hls.js';
import { HLSFallbackPlayer } from '../players/HLSFallbackPlayer';

type Fake = {
  levels: Array<{ name?: string; height?: number }>;
  audioTracks: Array<{ name?: string; lang?: string }>;
  audioTrack: number;
  emit(event: string, ...args: unknown[]): void;
};

const fakeHls = Hls as unknown as { instances: Fake[] };
const lastInstance = (): Fake => fakeHls.instances[fakeHls.instances.length - 1];

describe('HLSFallbackPlayer', () => {
  let video: HTMLVideoElement;
  let playSpy: jest.SpyInstance;

  beforeEach(() => {
    fakeHls.instances.length = 0;
    video = document.createElement('video');
    document.body.appendChild(video);
    playSpy = jest
      .spyOn(HTMLMediaElement.prototype, 'play')
      .mockImplementation(() => Promise.resolve());
    // jsdom não implementa load(): silencia o "not implemented" do console.
    jest.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
    jest.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
  });

  afterEach(() => {
    document.body.innerHTML = '';
    playSpy.mockRestore();
    jest.restoreAllMocks();
  });

  it('só chama play() depois do manifesto (MSE)', async () => {
    const player = new HLSFallbackPlayer(video);
    const pending = player.play('https://cdn.example.com/live.m3u8');
    await Promise.resolve();

    expect(playSpy).not.toHaveBeenCalled();

    lastInstance().levels = [{ height: 1080 }];
    lastInstance().emit('manifestParsed');
    await pending;

    expect(playSpy).toHaveBeenCalledTimes(1);
    expect(player.getState()).toBe('playing');
  });

  it('anuncia faixas de áudio reais (não os níveis de qualidade)', async () => {
    const player = new HLSFallbackPlayer(video);
    const onTracks = jest.fn();
    player.on('tracksReady', onTracks);

    const pending = player.play('https://cdn.example.com/live.m3u8');
    await Promise.resolve();

    const inst = lastInstance();
    inst.levels = [{ height: 1080 }, { height: 720 }];
    inst.audioTracks = [
      { name: 'Português', lang: 'pt' },
      { name: 'English', lang: 'en' },
    ];
    inst.emit('audioTracksUpdated');
    inst.emit('manifestParsed');
    await pending;

    const payload = onTracks.mock.calls[onTracks.mock.calls.length - 1][0] as TracksReadyPayload;
    expect(payload.audioTracks).toHaveLength(2);
    expect(payload.audioTracks[0]).toMatchObject({ id: 'audio-0', index: 0, name: 'Português', language: 'pt' });
    expect(payload.audioTracks[1]).toMatchObject({ id: 'audio-1', index: 1, language: 'en' });

    player.setAudioTrack('audio-1');
    expect(lastInstance().audioTrack).toBe(1);
  });

  it('rejeita o play em erro fatal de mídia', async () => {
    const player = new HLSFallbackPlayer(video);
    const onError = jest.fn();
    player.on('error', onError);

    const pending = player.play('https://cdn.example.com/live.m3u8');
    await Promise.resolve();

    lastInstance().emit('hlsError', 'hlsError', {
      fatal: true,
      type: 'mediaError',
      details: 'bufferAppendError',
    });

    await expect(pending).rejects.toThrow(/bufferAppendError/);
    expect(onError).toHaveBeenCalled();
    expect(player.getState()).toBe('error');
  });

  it('manda progressivo (.mp4) para o caminho nativo, sem hls.js', async () => {
    const player = new HLSFallbackPlayer(video);
    await player.play('https://cdn.example.com/filme.mp4');

    // hls.js só entende manifesto .m3u8; um .mp4 nele dá "no supported sources".
    expect(fakeHls.instances).toHaveLength(0);
    expect(video.getAttribute('src')).toBe('https://cdn.example.com/filme.mp4');
    expect(playSpy).toHaveBeenCalledTimes(1);
    expect(player.getState()).toBe('playing');
  });

  it('resume volta a emitir "playing" (o toggle não fica preso em paused)', async () => {
    const player = new HLSFallbackPlayer(video);
    const onPlaying = jest.fn();
    player.on('playing', onPlaying);

    const pending = player.play('https://cdn.example.com/live.m3u8');
    await Promise.resolve();
    lastInstance().emit('manifestParsed');
    await pending;
    onPlaying.mockClear();

    player.pause();
    expect(player.getState()).toBe('paused');

    player.resume();
    await Promise.resolve();
    expect(player.getState()).toBe('playing');
    expect(onPlaying).toHaveBeenCalledTimes(1);
  });

  it('destroy emite "destroyed" e derruba o hls.js', () => {
    const player = new HLSFallbackPlayer(video);
    const onDestroyed = jest.fn();
    player.on('destroyed', onDestroyed);
    void player.play('https://cdn.example.com/live.m3u8');
    const inst = lastInstance();
    player.destroy();
    expect(onDestroyed).toHaveBeenCalledTimes(1);
    expect((inst as unknown as { destroy: jest.Mock }).destroy).toHaveBeenCalled();
  });
});
